package com.example.txlab;

import com.example.txlab.repository.AuditRepository;
import com.example.txlab.repository.OrderRepository;
import com.example.txlab.service.AuditService;
import com.example.txlab.service.PaymentService;
import com.example.txlab.support.LabDataResetter;
import com.example.txlab.trace.TraceRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 事务代理返回边界 集成测试。
 *
 * <p><b>测试类刻意不加 {@code @Transactional}，测试方法也没有事务注解。</b>
 * 原因很关键：Spring Test 的 {@code @Transactional} 会让测试方法自己开一个事务、
 * 结束后自动回滚，从而掩盖被测代码"到底有没有真的提交/回滚"。
 * 本类所有数据库断言，都是请求结束后用一条新查询读出来的真实结果。</p>
 *
 * <p>每个测试前都会 {@link LabDataResetter#reset()}：清空审计表、三个订单恢复 PENDING，
 * 保证五个场景彼此独立、互不干扰。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private LabDataResetter resetter;

    @Autowired
    private TraceRecorder trace;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        // 真实落库的重置（不在测试事务里，不会被自动回滚）
        resetter.reset();
    }

    // ==================================================================================
    // Test01：真实代理行为
    // ==================================================================================
    @Test
    @DisplayName("Test01 注入的是 Spring 事务代理；内层 REQUIRED 也是通过代理加入外层事务")
    void test01_injectedBeansAreRealTransactionProxies() throws Exception {
        // 1) 注入进来的是代理对象，不是裸目标对象
        assertThat(AopUtils.isAopProxy(paymentService)).isTrue();
        assertThat(AopUtils.isAopProxy(auditService)).isTrue();
        assertThat(paymentService.getClass()).isNotEqualTo(PaymentService.class);
        assertThat(auditService.getClass()).isNotEqualTo(AuditService.class);

        Object rawPaymentTarget = AopProxyUtils.getSingletonTarget(paymentService);
        assertThat(rawPaymentTarget).isInstanceOf(PaymentService.class);
        assertThat(rawPaymentTarget).isNotSameAs(paymentService);

        // 2) 代理链上确实挂着 TransactionInterceptor（事务能力是被 AOP 织入的）
        assertThat(adviceClassNames(paymentService))
                .as("PaymentService 代理上应存在 TransactionInterceptor")
                .anyMatch(name -> name.contains("TransactionInterceptor"));
        assertThat(adviceClassNames(auditService))
                .as("AuditService 代理上应存在 TransactionInterceptor")
                .anyMatch(name -> name.contains("TransactionInterceptor"));

        // 3) 注解语义：外层 REQUIRED，内层也 REQUIRED
        assertThat(propagationOf(PaymentService.class, "pay")).isEqualTo(Propagation.REQUIRED);
        assertThat(propagationOf(AuditService.class, "saveAudit")).isEqualTo(Propagation.REQUIRED);

        // 4) 真实跑一次核心场景，用轨迹证明"内层确实在事务里，而且是加入外层事务"
        mockMvc.perform(post("/api/lab/pay").param("orderId", "1"))
                .andExpect(status().isInternalServerError());

        TraceRecorder.Event outerEntry = trace.lastEventContaining("【步骤2】进入 PaymentService.pay()");
        assertThat(outerEntry).as("应记录外层方法进入事件").isNotNull();
        assertThat(outerEntry.txState())
                .as("外层方法体里应处于真实事务中，且是这条物理事务的拥有者")
                .contains("active=true")
                .contains("newTx=true");

        TraceRecorder.Event innerEntry = trace.lastEventContaining("进入 AuditService.saveAudit()");
        assertThat(innerEntry).as("应记录内层方法进入事件").isNotNull();
        assertThat(innerEntry.txState())
                .as("内层方法体里也在事务中，但 newTx=false —— 说明它是通过代理加入了外层事务，没有新开物理事务")
                .contains("active=true")
                .contains("newTx=false");
    }

    // ==================================================================================
    // Test02：日志执行顺序 + 真实回滚的位置
    // ==================================================================================
    @Test
    @DisplayName("Test02 日志A 出现、日志C 不出现、日志D 是 UnexpectedRollbackException，真实回滚在 A 之后 D 之前")
    void test02_logOrderAndRealRollbackPosition() throws Exception {
        mockMvc.perform(post("/api/lab/pay").param("orderId", "1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("UnexpectedRollbackException"));

        // 三个日志的出现/不出现
        assertThat(trace.containsText("日志A")).as("业务方法体内的日志A 必须打印").isTrue();
        assertThat(trace.containsText("日志C")).as("事务代理抛出异常后，Controller 的日志C 绝不能执行").isFalse();
        assertThat(trace.containsText("日志D")).as("Controller 的 catch 分支必须执行").isTrue();
        assertThat(trace.lastEventContaining("日志D").message())
                .as("日志D 记录的异常类型必须是 UnexpectedRollbackException")
                .contains("UnexpectedRollbackException");

        // 顺序断言：日志A → beforeCompletion → afterCompletion(ROLLED_BACK) → 日志D
        // 注意用「【步骤11】/【步骤12】」前缀定位，避免撞上"已注册事务完成同步回调"这条说明事件
        int indexA = trace.indexOfText("日志A");
        int indexBeforeCompletion = trace.indexOfText("【步骤11】beforeCompletion");
        int indexAfterCompletion = trace.indexOfText("【步骤12】afterCompletion(status=ROLLED_BACK(1))");
        int indexD = trace.indexOfText("日志D");

        assertThat(indexA).as("日志A 必须存在").isGreaterThanOrEqualTo(0);
        assertThat(indexBeforeCompletion).as("外层事务进入完成阶段").isGreaterThan(indexA);
        assertThat(indexAfterCompletion).as("真实回滚发生在 beforeCompletion 之后").isGreaterThan(indexBeforeCompletion);
        assertThat(indexD).as("异常到达 Controller 一定晚于真实回滚").isGreaterThan(indexAfterCompletion);

        // 事务拦截器没有走 commit 分支，所以 beforeCommit 一次都不会被回调
        assertThat(trace.containsText("【步骤11】beforeCommit"))
                .as("被标记 rollback-only 的事务不会进入 commit 分支，beforeCommit 不应出现")
                .isFalse();

        // afterCompletion 时刻已经是回滚之后：用同一条连接回读，订单仍是 PENDING
        assertThat(trace.snapshot().get(indexAfterCompletion).message())
                .as("afterCompletion(ROLLED_BACK) 时数据库层回滚已完成")
                .contains("orders.status=PENDING");

        // rollback-only 在外层 catch 时就已经是 true（由内层事务拦截器标记）
        TraceRecorder.Event rollbackOnlyEvent = trace.lastEventContaining("【步骤8验证】");
        assertThat(rollbackOnlyEvent).isNotNull();
        assertThat(rollbackOnlyEvent.txState())
                .as("此刻共享事务已被标记 rollback-only，但数据库尚未回滚")
                .contains("rollbackOnly=true");
    }

    // ==================================================================================
    // Test03：数据库真实结果
    // ==================================================================================
    @Test
    @DisplayName("Test03 请求后数据库仍为 PENDING 且审计表无新增记录（真实查询，非 Mock）")
    void test03_databaseStateIsReallyRolledBack() throws Exception {
        // 请求前：真实查询
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = 1", String.class))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class))
                .isZero();

        mockMvc.perform(post("/api/lab/pay").param("orderId", "1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("UnexpectedRollbackException"));

        // 请求后：用全新查询验证，不依赖任何内存变量
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = 1", String.class))
                .as("订单 UPDATE 被真实回滚了")
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class))
                .as("审计 INSERT 也被同一次回滚撤销了")
                .isZero();
        assertThat(auditRepository.countByOrderId(1L)).isZero();
    }

    // ==================================================================================
    // Test04：内层自己捕获异常 → 外层可以正常提交
    // ==================================================================================
    @Test
    @DisplayName("Test04 异常在 AuditService 内部被 catch（不传播到事务代理）→ 外层事务正常提交")
    void test04_exceptionCaughtInsideInnerMethod() throws Exception {
        mockMvc.perform(post("/api/lab/pay/self-caught").param("orderId", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        // 外层真的提交了
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = 2", String.class))
                .isEqualTo("PAID");
        assertThat(auditRepository.countByOrderId(2L)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT action FROM audit_log WHERE order_id = 2", String.class))
                .isEqualTo("PAY_AUDIT_SELF_CAUGHT");

        // 日志 A/C 都有，D 没有
        assertThat(trace.containsText("日志A")).isTrue();
        assertThat(trace.containsText("日志C")).isTrue();
        assertThat(trace.containsText("日志D")).isFalse();

        // 事务走的是 commit 分支
        assertThat(trace.containsText("beforeCommit")).isTrue();
        assertThat(trace.containsText("afterCompletion(status=COMMITTED(0))")).isTrue();

        TraceRecorder.Event rollbackOnlyEvent = trace.lastEventContaining("【Test04】内层正常返回后");
        assertThat(rollbackOnlyEvent).isNotNull();
        assertThat(rollbackOnlyEvent.txState()).contains("rollbackOnly=false");
    }

    // ==================================================================================
    // Test05：REQUIRES_NEW 对照
    // ==================================================================================
    @Test
    @DisplayName("Test05 内层 REQUIRES_NEW 独立事务回滚 → 外层提交、审计不落库")
    void test05_requiresNewInnerTransaction() throws Exception {
        mockMvc.perform(post("/api/lab/pay/requires-new").param("orderId", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        // 外层提交成功
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = 3", String.class))
                .isEqualTo("PAID");
        // 内层独立事务回滚，审计记录不落库
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class)).isZero();
        assertThat(auditRepository.countByOrderId(3L)).isZero();

        // 日志 A/C 都有，D 没有
        assertThat(trace.containsText("日志A")).isTrue();
        assertThat(trace.containsText("日志B")).isTrue();
        assertThat(trace.containsText("日志C")).isTrue();
        assertThat(trace.containsText("日志D")).isFalse();

        // 外层事务正常提交
        assertThat(trace.containsText("afterCompletion(status=COMMITTED(0))")).isTrue();

        // 内层是独立物理事务：newTx=true（说明它没有加入外层事务）
        TraceRecorder.Event innerEntry = trace.lastEventContaining("REQUIRES_NEW 挂起外层事务");
        assertThat(innerEntry).isNotNull();
        assertThat(innerEntry.txState())
                .as("内层是独立物理事务")
                .contains("active=true")
                .contains("newTx=true");

        TraceRecorder.Event rollbackOnlyEvent = trace.lastEventContaining("【Test05】内层异常返回后");
        assertThat(rollbackOnlyEvent).isNotNull();
        assertThat(rollbackOnlyEvent.txState())
                .as("内层独立事务的回滚不会污染外层事务的 rollback-only 标记")
                .contains("rollbackOnly=false");
    }

    // ==================================================================================
    // 辅助方法
    // ==================================================================================

    /** 读取代理对象上的所有 advice 类名 */
    private List<String> adviceClassNames(Object proxy) {
        assertThat(proxy).isInstanceOf(Advised.class);
        Advisor[] advisors = ((Advised) proxy).getAdvisors();
        return Arrays.stream(advisors).map(advisor -> advisor.getAdvice().getClass().getName()).toList();
    }

    /** 读取目标方法上的 @Transactional 传播行为 */
    private Propagation propagationOf(Class<?> type, String methodName) {
        Method method = Arrays.stream(type.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("找不到方法：" + type.getSimpleName() + "#" + methodName));
        Transactional transactional = AnnotationUtils.findAnnotation(method, Transactional.class);
        assertThat(transactional)
                .as("%s#%s 上必须存在 @Transactional", type.getSimpleName(), methodName)
                .isNotNull();
        return transactional.propagation();
    }
}
