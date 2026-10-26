package com.example.txlab.repository;

import com.example.txlab.trace.TraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 订单表访问层（纯 JdbcTemplate，不加 @Transactional）。
 *
 * <p>刻意不加事务注解：它必须"附着"在调用方开启的事务里执行，
 * 这样才能观察到"UPDATE 已执行但还未提交"的中间状态。</p>
 */
@Repository
public class OrderRepository {

    private static final Logger log = LoggerFactory.getLogger(OrderRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final TraceRecorder trace;

    public OrderRepository(JdbcTemplate jdbcTemplate, TraceRecorder trace) {
        this.jdbcTemplate = jdbcTemplate;
        this.trace = trace;
    }

    /**
     * 修改订单状态。
     *
     * @return 真实影响行数（由数据库返回，不是写死的）
     */
    public int updateStatus(Long orderId, String status) {
        int rows = jdbcTemplate.update("UPDATE orders SET status = ? WHERE id = ?", status, orderId);
        log.info("JDBC: UPDATE orders SET status='{}' WHERE id={} → 影响 {} 行（尚未提交，可被回滚）",
                status, orderId, rows);
        trace.record("SQL", "执行 UPDATE orders SET status='" + status + "' WHERE id=" + orderId
                + " → 影响 " + rows + " 行（事务未提交）");
        return rows;
    }

    /** 读取订单当前状态（走当前线程绑定的事务连接；无事务时自动提交连接） */
    public String findStatus(Long orderId) {
        List<String> list = jdbcTemplate.queryForList(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId);
        return list.isEmpty() ? "NOT_FOUND" : list.get(0);
    }

    /** 全部订单（用于 HTTP 观察接口） */
    public List<Map<String, Object>> findAll() {
        return jdbcTemplate.queryForList("SELECT id, status, created_at FROM orders ORDER BY id");
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        return count == null ? 0 : count;
    }
}
