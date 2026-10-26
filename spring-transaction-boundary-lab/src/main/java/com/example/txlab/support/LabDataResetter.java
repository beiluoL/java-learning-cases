package com.example.txlab.support;

import com.example.txlab.repository.AuditRepository;
import com.example.txlab.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 实验数据重置器：把两个表恢复到"干净起点"，保证每个场景互不干扰。
 *
 * <p>刻意<b>不加</b> {@code @Transactional}：重置动作必须真正落库（自动提交），
 * 否则测试里"我刚刚重置好的数据"会被测试框架自己的事务回滚掉，
 * 那就会掩盖被测代码的真实提交结果。</p>
 */
@Component
public class LabDataResetter {

    private static final Logger log = LoggerFactory.getLogger(LabDataResetter.class);

    private final JdbcTemplate jdbcTemplate;
    private final AuditRepository auditRepository;
    private final TraceRecorder trace;

    public LabDataResetter(JdbcTemplate jdbcTemplate, AuditRepository auditRepository, TraceRecorder trace) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditRepository = auditRepository;
        this.trace = trace;
    }

    /**
     * 清空审计表、把 3 个订单全部恢复成 PENDING。
     *
     * @return 重置后两个表的真实行数摘要
     */
    public String reset() {
        jdbcTemplate.update("DELETE FROM audit_log");
        for (long orderId = 1; orderId <= 3; orderId++) {
            jdbcTemplate.update("MERGE INTO orders (id, status, created_at) KEY (id) VALUES (?, 'PENDING', CURRENT_TIMESTAMP)", orderId);
        }
        String summary = "重置完成：orders=" + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)
                + " 行（全部 PENDING），audit_log=" + auditRepository.countAll() + " 行";
        log.info("{}", summary);
        trace.record("DB_RESET", summary);
        return summary;
    }
}
