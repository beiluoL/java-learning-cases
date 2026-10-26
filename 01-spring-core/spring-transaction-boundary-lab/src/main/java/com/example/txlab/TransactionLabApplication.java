package com.example.txlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Q021-EXT 事务代理返回边界实验室 启动类。
 *
 * <p>实验目标：搞清楚
 * 「业务方法 return "SUCCESS" → 内层事务代理 → rollback-only → 外层事务完成 → 真正 ROLLBACK
 * → UnexpectedRollbackException → Controller 处理」这条执行链的真实先后顺序。</p>
 *
 * <p>{@code @EnableTransactionManagement} 显式开启注解事务（Spring Boot 已自动配置，
 * 这里显式声明是为了让学习者在启动类上就能看到"事务能力是靠代理织入的"）。
 * 默认 {@code proxyTargetClass=true}，即使用 CGLIB 子类代理，因此本工程的 Bean 都是真实代理对象。</p>
 */
@SpringBootApplication
@EnableTransactionManagement
public class TransactionLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionLabApplication.class, args);
    }
}
