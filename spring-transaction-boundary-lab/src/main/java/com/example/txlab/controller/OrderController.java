package com.example.txlab.controller;

import com.example.txlab.service.PaymentComparisonService;
import com.example.txlab.service.PaymentService;
import com.example.txlab.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实验入口控制器。
 *
 * <p>三个接口对应三个独立场景：</p>
 * <ul>
 *   <li>{@code POST /api/lab/pay}：核心场景（内层 REQUIRED 抛异常 → 外层吞掉 → UnexpectedRollbackException）</li>
 *   <li>{@code POST /api/lab/pay/self-caught}：Test04 对照（内层自己 catch → 外层正常提交）</li>
 *   <li>{@code POST /api/lab/pay/requires-new}：Test05 对照（内层 REQUIRES_NEW → 外层正常提交、审计不落库）</li>
 * </ul>
 *
 * <p>注意日志 C 与日志 D 是互斥的：
 * 日志 C 只在「事务代理把返回值正常交回给调用方」时才打印；
 * 事务代理在完成后阶段抛出 UnexpectedRollbackException 时，走的是 catch 分支，
 * 所以日志 C 一定不会出现。</p>
 */
@RestController
@RequestMapping("/api/lab")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final PaymentService paymentService;
    private final PaymentComparisonService paymentComparisonService;
    private final TraceRecorder trace;

    public OrderController(PaymentService paymentService,
                           PaymentComparisonService paymentComparisonService,
                           TraceRecorder trace) {
        this.paymentService = paymentService;
        this.paymentComparisonService = paymentComparisonService;
        this.trace = trace;
    }

    /**
     * 核心场景：业务方法 return "SUCCESS"，但 Controller 可能收到 UnexpectedRollbackException。
     */
    @PostMapping("/pay")
    public ResponseEntity<String> pay(@RequestParam(name = "orderId", defaultValue = "1") Long orderId) {
        trace.startRun("核心场景 POST /api/lab/pay?orderId=" + orderId);
        trace.record("CONTROLLER",
                "【步骤1】Controller 调用 paymentService.pay(" + orderId + ")：注入进来的是 Spring 事务代理，不是裸对象");

        try {
            String result = paymentService.pay(orderId);

            // 日志 C：只有事务代理正常返回时才执行
            log.info("日志C：{}", result);
            trace.record("CONTROLLER", "【步骤15】日志C 执行：Controller 正常拿到返回值 " + result);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            // 日志 D：记录实际异常类型
            log.error("日志D：{}", e.getClass().getSimpleName());
            trace.record("CONTROLLER",
                    "【步骤14】日志D 执行：Controller 捕获 " + e.getClass().getName()
                            + "，message=" + e.getMessage());

            return ResponseEntity
                    .internalServerError()
                    .body(e.getClass().getSimpleName());
        } finally {
            // 把真实执行轨迹打到控制台，便于从日志直接取证
            trace.dump();
        }
    }

    /**
     * Test04 对照场景：内层自己 catch 异常，不传播到内层事务代理。
     */
    @PostMapping("/pay/self-caught")
    public ResponseEntity<String> payWithSelfCaughtAudit(
            @RequestParam(name = "orderId", defaultValue = "2") Long orderId) {
        trace.startRun("对照场景 Test04 POST /api/lab/pay/self-caught?orderId=" + orderId);
        trace.record("CONTROLLER", "Controller 调用 paymentComparisonService.payWithSelfCaughtAudit(" + orderId + ")");
        try {
            String result = paymentComparisonService.payWithSelfCaughtAudit(orderId);
            log.info("日志C：{}", result);
            trace.record("CONTROLLER", "日志C 执行：Controller 正常拿到返回值 " + result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("日志D：{}", e.getClass().getSimpleName());
            trace.record("CONTROLLER", "日志D 执行：Controller 捕获 " + e.getClass().getName());
            return ResponseEntity.internalServerError().body(e.getClass().getSimpleName());
        } finally {
            trace.dump();
        }
    }

    /**
     * Test05 对照场景：内层 REQUIRES_NEW 独立事务回滚。
     */
    @PostMapping("/pay/requires-new")
    public ResponseEntity<String> payWithRequiresNewAudit(
            @RequestParam(name = "orderId", defaultValue = "3") Long orderId) {
        trace.startRun("对照场景 Test05 POST /api/lab/pay/requires-new?orderId=" + orderId);
        trace.record("CONTROLLER", "Controller 调用 paymentComparisonService.payWithRequiresNewAudit(" + orderId + ")");
        try {
            String result = paymentComparisonService.payWithRequiresNewAudit(orderId);
            log.info("日志C：{}", result);
            trace.record("CONTROLLER", "日志C 执行：Controller 正常拿到返回值 " + result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("日志D：{}", e.getClass().getSimpleName());
            trace.record("CONTROLLER", "日志D 执行：Controller 捕获 " + e.getClass().getName());
            return ResponseEntity.internalServerError().body(e.getClass().getSimpleName());
        } finally {
            trace.dump();
        }
    }
}
