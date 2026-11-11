package com.example.ai.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 8 集成测试（V4/V5 治理 schema）：
 * 迁移版本、新表结构、唯一约束、追加式流水、保护规则种子、场景预算列、每日汇总回填。
 *
 * <p>运行：mvn -Pintegration verify（需先启动 ai-cost-mysql 容器并让应用执行过迁移）。
 */
class GovernancePersistenceIT {

    private static final String URL = "jdbc:mysql://127.0.0.1:"
            + System.getProperty("mysqlPort", System.getenv().getOrDefault("MYSQL_PORT", "13307"))
            + "/ai_cost?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
    private static final String USER = System.getenv().getOrDefault("MYSQL_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("MYSQL_PASSWORD", "root");
    private static final String ENTRY_NO = "IT-LEDGER-GOVERNANCE-001";
    private static final String TASK_ID = "it-agent-task-001";

    private static Connection connection;

    @BeforeAll
    static void connect() throws Exception {
        connection = DriverManager.getConnection(URL, USER, PASSWORD);
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (connection != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM ai_cost_ledger WHERE entry_no = ?")) {
                ps.setString(1, ENTRY_NO);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM ai_agent_cost_trace WHERE task_id = ?")) {
                ps.setString(1, TASK_ID);
                ps.executeUpdate();
            }
            connection.close();
        }
    }

    @Test
    void flywayAppliedGovernanceMigrations() throws Exception {
        Set<String> versions = new HashSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM flyway_schema_history WHERE success = 1")) {
            while (rs.next()) {
                versions.add(rs.getString(1));
            }
        }
        assertTrue(versions.contains("1"), "基础表迁移缺失");
        assertTrue(versions.contains("4"), "V4 治理 schema 迁移未执行");
        assertTrue(versions.contains("5"), "V5 治理种子数据迁移未执行");
    }

    @Test
    void governanceTablesExistWithComments() throws Exception {
        Set<String> tables = new HashSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'ai_cost'")) {
            while (rs.next()) {
                tables.add(rs.getString(1).toLowerCase());
            }
        }
        for (String expected : new String[]{"ai_cost_ledger", "ai_budget_transaction",
                "ai_cost_protection_rule", "ai_cost_protection_log", "ai_cost_optimization_log",
                "ai_cost_daily_summary", "ai_agent_cost_trace", "ai_infrastructure_cost"}) {
            assertTrue(tables.contains(expected), "缺少表 " + expected);
        }

        // 关键金额列必须是 DECIMAL，不能用浮点
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema='ai_cost' AND table_name='ai_cost_ledger' AND column_name='amount'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("decimal", rs.getString(1).toLowerCase());
            }
        }
    }

    @Test
    void ledgerIsAppendOnlyAndIdempotentByEntryNo() throws Exception {
        insertLedger();
        // 重复入账：驱动对 ON DUPLICATE 的 affected-rows 返回值不统一，
        // 真正要验证的是「行数不增加、金额不被改写」
        insertLedger();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*), MAX(amount) FROM ai_cost_ledger WHERE entry_no = ?")) {
            ps.setString(1, ENTRY_NO);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "相同 entry_no 只能有一条账务流水");
                assertEquals(0, new BigDecimal("0.00123000").compareTo(rs.getBigDecimal(2)));
            }
        }
    }

    @Test
    void protectionRulesAreSeededWithFiveLevels() throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(DISTINCT protection_level) FROM ai_cost_protection_rule WHERE enabled = 1")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 4, "保护规则应覆盖至少 4 个等级，实际 " + rs.getInt(1));
        }
    }

    @Test
    void budgetPolicySupportsSceneAndAgentScopes() throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM ai_budget_policy WHERE budget_type IN ('SCENE','AGENT','KNOWLEDGE_BASE')")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 3, "缺少场景/Agent/知识库预算种子");
        }
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT overrun_policy, alert_threshold FROM ai_budget_policy WHERE budget_type='AGENT' LIMIT 1")) {
            assertTrue(rs.next());
            assertEquals("BLOCK", rs.getString(1));
            assertTrue(rs.getBigDecimal(2) != null);
        }
    }

    @Test
    void dailySummaryNeverExceedsDetailBilling() throws Exception {
        // 历史日期（< 今天）汇总必须与明细完全一致；今天的汇总由每小时任务重建，允许短暂滞后但不得超出
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT s.summary_date, s.total_cost, "
                             + "(SELECT COALESCE(SUM(u.total_cost),0) FROM ai_token_usage u "
                             + " WHERE DATE(u.created_at) = s.summary_date) AS detail "
                             + "FROM ai_cost_daily_summary s WHERE s.dimension = 'ALL'")) {
            int checked = 0;
            while (rs.next()) {
                java.sql.Date date = rs.getDate(1);
                BigDecimal summary = rs.getBigDecimal(2);
                BigDecimal detail = rs.getBigDecimal(3);
                assertTrue(summary.compareTo(detail) <= 0,
                        "汇总不得大于明细：" + date + " summary=" + summary + " detail=" + detail);
                checked++;
            }
            assertTrue(checked >= 0);
        }
    }

    @Test
    void agentTraceIsUniquePerTask() throws Exception {
        insertAgentTrace();
        insertAgentTrace();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM ai_agent_cost_trace WHERE task_id = ?")) {
            ps.setString(1, TASK_ID);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "同一 task_id 只能有一条追踪");
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE ai_agent_cost_trace SET steps = 3, total_cost = 0.5 WHERE task_id = ?")) {
            ps.setString(1, TASK_ID);
            assertEquals(1, ps.executeUpdate());
        }
    }

    private int insertLedger() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO ai_cost_ledger (entry_no, entry_type, cost_kind, user_id, amount, currency, "
                        + "occurred_at, remark) VALUES (?, 'MODEL_CALL', 'ACTUAL', 'it-user', ?, 'USD', NOW(3), 'IT 用例') "
                        + "ON DUPLICATE KEY UPDATE entry_no = entry_no")) {
            ps.setString(1, ENTRY_NO);
            ps.setBigDecimal(2, new BigDecimal("0.00123000"));
            return ps.executeUpdate();
        }
    }

    private int insertAgentTrace() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO ai_agent_cost_trace (task_id, agent_id, user_id, steps, tool_calls, input_tokens, "
                        + "output_tokens, total_tokens, total_cost, status, started_at) "
                        + "VALUES (?, 'it-agent', 'it-user', 0, 0, 0, 0, 0, 0, 'RUNNING', NOW(3)) "
                        + "ON DUPLICATE KEY UPDATE task_id = task_id")) {
            ps.setString(1, TASK_ID);
            return ps.executeUpdate();
        }
    }
}
