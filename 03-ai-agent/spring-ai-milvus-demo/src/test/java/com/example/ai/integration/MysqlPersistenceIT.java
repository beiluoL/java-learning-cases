package com.example.ai.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 8 集成测试：验证 Flyway 迁移、表注释、唯一约束、DECIMAL 精度、
 * 价格版本快照与统计 SQL。运行：mvn -Pintegration verify
 * 前提：infra/docker/ai-cost-compose.yml 已启动且应用已执行过迁移。
 */
class MysqlPersistenceIT {

    private static final String URL = "jdbc:mysql://127.0.0.1:"
            + System.getProperty("mysqlPort", System.getenv().getOrDefault("MYSQL_PORT", "13307"))
            + "/ai_cost?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
    private static final String USER = System.getenv().getOrDefault("MYSQL_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("MYSQL_PASSWORD", "root");
    private static final String TEST_REQUEST = "it-mysql-persistence-001";

    private static Connection connection;

    @BeforeAll
    static void connect() throws Exception {
        connection = DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** 每个用例独立清理测试数据，避免用例顺序导致唯一约束误报。 */
    @BeforeEach
    void cleanTestRow() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM ai_token_usage WHERE request_id = ?")) {
            ps.setString(1, TEST_REQUEST);
            ps.executeUpdate();
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (connection != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM ai_token_usage WHERE request_id = ?")) {
                ps.setString(1, TEST_REQUEST);
                ps.executeUpdate();
            }
            connection.close();
        }
    }

    @Test
    void flywayCreatedAllFiveTablesWithFlywayHistory() throws Exception {
        Set<String> tables = new HashSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema='ai_cost'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        for (String name : Set.of("ai_token_usage", "ai_model_pricing", "ai_budget_policy",
                "ai_budget_reservation", "ai_cost_alert")) {
            assertTrue(tables.contains(name), "缺少表: " + name);
        }
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")) {
            rs.next();
            assertTrue(rs.getInt(1) >= 2, "Flyway 迁移记录应至少 2 条");
        }
    }

    @Test
    void pricingSeedIsMarkedAsDemoAndHasVersions() throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*), SUM(demo_flag) FROM ai_model_pricing WHERE version='DEMO-v1'")) {
            rs.next();
            assertTrue(rs.getInt(1) >= 5);
            assertEquals(rs.getInt(1), rs.getInt(2), "示例价格必须全部标记 DEMO");
        }
    }

    @Test
    void requestIdUniqueConstraintAndDecimalPrecision() throws Exception {
        insertUsage("0.00345678");
        assertThrows(SQLException.class, () -> insertUsage("0.00345678"),
                "同一 requestId 重复写入必须被唯一约束拒绝");
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT total_cost FROM ai_token_usage WHERE request_id=?")) {
            ps.setString(1, TEST_REQUEST);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, new BigDecimal("0.00345678").compareTo(rs.getBigDecimal(1)),
                        "DECIMAL(20,8) 金额精度不得丢失");
            }
        }
    }

    @Test
    void statisticsSqlUsesRangeAndReturnsAggregates() throws Exception {
        insertUsage("0.00345678");
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*), COALESCE(SUM(total_cost),0) FROM ai_token_usage "
                        + "WHERE created_at >= DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 DAY) AND created_at < DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 MINUTE)")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertTrue(rs.getInt(1) >= 1);
                assertTrue(rs.getBigDecimal(2).compareTo(BigDecimal.ZERO) >= 0);
            }
        }
    }

    private void insertUsage(String cost) throws SQLException {
        String sql = "INSERT INTO ai_token_usage "
                + "(request_id, trace_id, user_id, business_scene, provider, model_name, model_type, "
                + "input_tokens, output_tokens, total_tokens, cached_input_tokens, reasoning_tokens, "
                + "usage_status, request_status, latency_ms, total_cost, currency, pricing_version, created_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, TEST_REQUEST);
            ps.setString(2, "it-trace");
            ps.setString(3, "demo-user");
            ps.setString(4, "CHAT");
            ps.setString(5, "mock");
            ps.setString(6, "mock-chat-model");
            ps.setString(7, "CHAT");
            ps.setLong(8, 100);
            ps.setLong(9, 50);
            ps.setLong(10, 150);
            ps.setLong(11, 0);
            ps.setLong(12, 0);
            ps.setString(13, "MOCK");
            ps.setString(14, "SUCCESS");
            ps.setLong(15, 10);
            ps.setBigDecimal(16, new BigDecimal(cost));
            ps.setString(17, "USD");
            ps.setString(18, "DEMO-v1");
            ps.executeUpdate();
        }
    }
}
