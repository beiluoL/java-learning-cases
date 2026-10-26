package com.example.txlab.repository;

import com.example.txlab.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 审计日志表访问层（纯 JdbcTemplate，不加 @Transactional）。
 */
@Repository
public class AuditRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final TraceRecorder trace;

    public AuditRepository(JdbcTemplate jdbcTemplate, TraceRecorder trace) {
        this.jdbcTemplate = jdbcTemplate;
        this.trace = trace;
    }

    /** 插入一条审计记录，返回真实影响行数 */
    public int insert(Long orderId, String action) {
        int rows = jdbcTemplate.update(
                "INSERT INTO audit_log (order_id, action, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                orderId, action);
        log.info("JDBC: INSERT INTO audit_log(order_id={}, action='{}') → 影响 {} 行（尚未提交，可被回滚）",
                orderId, action, rows);
        trace.record("SQL", "执行 INSERT INTO audit_log(order_id=" + orderId + ", action='" + action
                + "') → 影响 " + rows + " 行（事务未提交）");
        return rows;
    }

    /** 某订单的审计记录数 */
    public int countByOrderId(Long orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE order_id = ?", Integer.class, orderId);
        return count == null ? 0 : count;
    }

    /** 全表记录数 */
    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
        return count == null ? 0 : count;
    }

    /** 全部审计记录（用于 HTTP 观察接口） */
    public List<Map<String, Object>> findAll() {
        return jdbcTemplate.queryForList(
                "SELECT id, order_id, action, created_at FROM audit_log ORDER BY id");
    }
}
