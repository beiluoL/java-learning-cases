package com.learn.mybatismini.demo;

import com.learn.mybatismini.builder.XMLConfigBuilder;
import com.learn.mybatismini.demo.entity.Order;
import com.learn.mybatismini.demo.entity.User;
import com.learn.mybatismini.demo.mapper.OrderMapper;
import com.learn.mybatismini.demo.mapper.UserAnnoMapper;
import com.learn.mybatismini.demo.mapper.UserMapper;
import com.learn.mybatismini.io.Resources;
import com.learn.mybatismini.logging.MiniLog;
import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.session.SqlSession;
import com.learn.mybatismini.session.SqlSessionFactory;
import com.learn.mybatismini.session.defaults.DefaultSqlSessionFactory;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 迷你 MyBatis 端到端演示入口（main 方法）。
 *
 * <h3>这个类的职责</h3>
 * 它不属于框架，而是<b>框架的使用方</b>——就像你平时写的业务代码。
 * 通过一步步调用手写的迷你 MyBatis，把所有核心模块串起来跑一遍：
 * <pre>
 *   Step01  配置解析（XML -> Configuration -> SqlSessionFactory）
 *   Step02  基础查询 selectById / selectAll
 *   Step03  动态 SQL：&lt;where&gt; + &lt;if&gt;
 *   Step04  动态 SQL：&lt;foreach&gt; IN 查询
 *   Step05  动态 SQL：&lt;choose&gt;/&lt;when&gt;/&lt;otherwise&gt;
 *   Step06  插入 + 自增主键回填 useGeneratedKeys
 *   Step07  动态 &lt;set&gt; 更新（只改非 null 字段）
 *   Step08  @Param 多参 + 计数
 *   Step09  ${} 字符串拼接排序（含 SQL 注入风险说明）
 *   Step10  一级缓存（同一会话内重复查询）
 *   Step11  二级缓存（跨会话共享，需 commit）
 *   Step12  注解式 Mapper（@Select/@Insert/@CacheNamespace）
 *   Step13  插件机制（慢 SQL 耗时统计，全程生效）
 *   Step14  SQL 执行生命周期总结
 * </pre>
 *
 * <p>运行方式（项目根目录）：
 * <pre>
 *   mvn -q compile exec:java
 * </pre>
 */
public class MainApplication {

    public static void main(String[] args) throws Exception {
        MiniLog.title("迷你 MyBatis 源码学习 · 端到端 Demo（Step01 ~ Step14）");

        // ===== Step01：配置解析 =====
        MiniLog.section("Step01 配置解析：XML -> Configuration -> SqlSessionFactory");
        Configuration configuration;
        try (InputStream in = Resources.getResourceAsStream("mini-mybatis-config.xml")) {
            configuration = new XMLConfigBuilder(in).parse();
        }
        SqlSessionFactory factory = new DefaultSqlSessionFactory(configuration);
        MiniLog.info("已解析 MappedStatement 数量：" + configuration.getMappedStatementCount());
        MiniLog.info("已注册插件数量：" + configuration.getInterceptorChain().getInterceptors().size());

        // 初始化内存库表结构 + 种子数据
        initSchema(configuration);

        // ===== Step02 ~ Step14 =====
        step02_basicQuery(factory);
        step03_dynamicWhere(factory);
        step04_foreach(factory);
        step05_choose(factory);
        step06_insertAndKeyBackfill(factory);
        step07_updateSelective(factory);
        step08_paramAndCount(factory);
        step09_dollarOrder(factory);
        step10_firstLevelCache(factory);
        step11_secondLevelCache(factory);
        step12_annotationMapper(factory);
        step13_pluginAlreadyWired();
        step14_lifecycleSummary();

        MiniLog.title("全部 Demo 执行完毕，谢谢观看 \uD83C\uDF89");
    }

    // ==================================================================
    // 各步骤
    // ==================================================================

    private static void step02_basicQuery(SqlSessionFactory factory) {
        MiniLog.section("Step02 基础查询：selectById / selectAll");
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            User u = mapper.selectById(1L);
            MiniLog.info("selectById(1) => " + u);
            List<User> all = mapper.selectAll();
            MiniLog.info("selectAll 共 " + all.size() + " 条");
        }
    }

    private static void step03_dynamicWhere(SqlSessionFactory factory) {
        MiniLog.section("Step03 动态 SQL：<where> + <if> 条件任意组合");
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            MiniLog.info("条件1 userName='张' => " + mapper.selectByCondition("张", null, null));
            MiniLog.info("条件2 age=25       => " + mapper.selectByCondition(null, 25, null));
            MiniLog.info("条件3 全 null（查全表）=> " + mapper.selectByCondition(null, null, null));
        }
    }

    private static void step04_foreach(SqlSessionFactory factory) {
        MiniLog.section("Step04 动态 SQL：<foreach> 展开成 IN (?, ?, ?)");
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            List<User> users = mapper.selectByIds(Arrays.asList(1L, 2L, 3L));
            MiniLog.info("selectByIds([1,2,3]) => " + users);
        }
    }

    private static void step05_choose(SqlSessionFactory factory) {
        MiniLog.section("Step05 动态 SQL：<choose>/<when>/<otherwise>");
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            MiniLog.info("when 命中 userName='李' => " + mapper.selectByChoose("李", null));
            MiniLog.info("otherwise 命中 email => " + mapper.selectByChoose(null, "wangwu@example.com"));
        }
    }

    private static void step06_insertAndKeyBackfill(SqlSessionFactory factory) {
        MiniLog.section("Step06 插入 + 自增主键回填（useGeneratedKeys=true keyProperty=id）");
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            User u = new User("钱七", 28, "qianqi@example.com");
            u.setCreateTime(LocalDateTime.now());
            MiniLog.info("插入前 id = " + u.getId());
            int rows = mapper.insertUser(u);
            MiniLog.info("影响行数 = " + rows + "，插入后 id 被自动回填 = " + u.getId());
            session.commit();
            MiniLog.info("commit 后按回填的 id 再查回 => " + mapper.selectById(u.getId()));
        }
    }

    private static void step07_updateSelective(SqlSessionFactory factory) {
        MiniLog.section("Step07 动态 <set> 更新：只改非 null 字段");
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            User u = new User();
            u.setId(2L);
            u.setUserName("李四改");
            // age、email 故意留 null，验证不会被更新
            int rows = mapper.updateUserSelective(u);
            MiniLog.info("影响行数 = " + rows + "（应只更新了 user_name）");
            session.commit();
            MiniLog.info("更新后 => " + mapper.selectById(2L));
        }
    }

    private static void step08_paramAndCount(SqlSessionFactory factory) {
        MiniLog.section("Step08 @Param 多参 + 计数");
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            int count = mapper.countByAge(20, 30);
            MiniLog.info("年龄 20~30 的人数 = " + count);
        }
    }

    private static void step09_dollarOrder(SqlSessionFactory factory) {
        MiniLog.section("Step09 ${} 字符串拼接排序（★ SQL 注入高风险，仅演示原理）");
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            MiniLog.info("ORDER BY id DESC => " + mapper.selectByOrder(1L, "id DESC"));
            MiniLog.warn("注意：${} 是文本直接拼接，绝不可拼接用户输入！请用 #{} 或白名单枚举。");
        }
    }

    private static void step10_firstLevelCache(SqlSessionFactory factory) {
        MiniLog.section("Step10 一级缓存：同一 SqlSession 内重复查询命中缓存");
        try (SqlSession session = factory.openSession()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            MiniLog.info("第 1 次 selectAll（应未命中一级缓存，查库）...");
            mapper.selectAll();
            MiniLog.info("第 2 次 selectAll（应命中一级缓存，不再查库）...");
            mapper.selectAll();
        }
    }

    private static void step11_secondLevelCache(SqlSessionFactory factory) {
        MiniLog.section("Step11 二级缓存：跨 SqlSession 共享（必须 commit 后才生效）");
        // 会话一：查询并 commit -> 结果刷入共享二级缓存
        try (SqlSession s1 = factory.openSession()) {
            OrderMapper m1 = s1.getMapper(OrderMapper.class);
            MiniLog.info("会话1 第一次 selectAll（查库）...");
            m1.selectAll();
            MiniLog.info("会话1 commit -> 结果刷入二级缓存");
            s1.commit();
        }
        // 会话二：再查同一条件，应命中二级缓存
        try (SqlSession s2 = factory.openSession()) {
            OrderMapper m2 = s2.getMapper(OrderMapper.class);
            MiniLog.info("会话2 查询 selectAll（应命中二级缓存，不再查库）...");
            List<Order> orders = m2.selectAll();
            MiniLog.info("会话2 拿到 " + orders.size() + " 条（来自二级缓存）");
            s2.commit();
        }
    }

    private static void step12_annotationMapper(SqlSessionFactory factory) {
        MiniLog.section("Step12 注解式 Mapper：@Select / @Insert / @CacheNamespace");
        try (SqlSession session = factory.openSession()) {
            UserAnnoMapper mapper = session.getMapper(UserAnnoMapper.class);
            MiniLog.info("selectById(1) => " + mapper.selectById(1L));
            MiniLog.info("countByAge(18, 40) = " + mapper.countByAge(18, 40));
            User u = new User("注解插入", 33, "anno@example.com");
            u.setCreateTime(LocalDateTime.now());
            int rows = mapper.insertUser(u);
            MiniLog.info("注解 insert 影响行数 = " + rows + "，回填 id = " + u.getId());
            session.commit();
        }
    }

    private static void step13_pluginAlreadyWired() {
        MiniLog.section("Step13 插件机制：已在 Step02~Step12 全程生效");
        MiniLog.info("所有 query/update 调用前后都打印了 [插件 耗时统计] 日志，");
        MiniLog.info("这正是 Interceptor 用 JDK 动态代理包裹 Executor 实现的 AOP 环绕通知。");
    }

    private static void step14_lifecycleSummary() {
        MiniLog.section("Step14 SQL 执行生命周期（一次查询的完整链路）");
        MiniLog.info("SqlSession.selectList");
        MiniLog.info("  -> MapperProxy（JDK 动态代理，statementId = 接口全限定名.方法名）");
        MiniLog.info("  -> MapperMethod.execute（按 SQL 类型分发到 select/insert/...）");
        MiniLog.info("  -> CachingExecutor.query（二级缓存：命中直接返回）");
        MiniLog.info("  -> BaseExecutor.query（一级缓存：命中直接返回）");
        MiniLog.info("  -> SimpleExecutor.doQuery（模板方法，真正干活）");
        MiniLog.info("  -> Configuration.newStatementHandler -> PreparedStatementHandler");
        MiniLog.info("  -> DefaultParameterHandler（#{xx} -> ?，TypeHandler 设参）");
        MiniLog.info("  -> JDBC PreparedStatement 执行");
        MiniLog.info("  -> DefaultResultSetHandler（结果集 -> 实体，反射 + TypeHandler）");
        MiniLog.info("  -> 结果逐层返回并写入两级缓存");
    }

    // ==================================================================
    // 工具：初始化内存库
    // ==================================================================

    /**
     * 读取 classpath 下的 db/schema.sql，按 ";" 拆分后逐条执行，
     * 完成建表 + 灌入种子数据。使用的是配置里的数据源连接。
     */
    private static void initSchema(Configuration configuration) throws Exception {
        MiniLog.section("初始化内存库（执行 db/schema.sql）");
        DataSource ds = configuration.getEnvironment().getDataSource();
        String sql = Resources.getResourceAsString("db/schema.sql");
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String part : sql.split(";")) {
                String s = part.trim();
                if (!s.isEmpty()) {
                    stmt.execute(s);
                }
            }
        }
        MiniLog.info("表结构 + 种子数据已就绪（t_user / t_order）");
    }
}
