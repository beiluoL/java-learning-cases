# 01-spring-core · 框架原理与源码级学习

> **这一组放什么**：不依赖 Spring Boot 自动配置、纯手写或最小整合的 Java / Spring 工程，目的是**看懂框架内部在做什么**。
>
> **为什么单独成组**：Spring 的抽象层数很深（`BeanFactory` → `BeanDefinition` → `BeanPostProcessor` → AOP 代理链），只看文档很难建立直觉。先把这些机制自己写一遍，后面看任何 Spring Boot 工程都是"自动配置把地基包起来了"，而不是一堆魔法注解。

---

## 组内工程

| 工程 | 学习目标 | 技术栈 | 启动方式 |
|---|---|---|---|
| [spring-ioc-aop-learn](./spring-ioc-aop-learn) | 手写迷你 IoC 容器 + AOP 框架：`BeanFactory` / `BeanDefinition` / `BeanPostProcessor` / `AdvisedSupport` / `MethodInterceptor`；JDK 动态代理 vs CGLIB 对比 | Java 17 · Maven · CGLIB 3.3 | `./start.command` 或 `mvn compile exec:java` |
| [mybatis-source-learn](./mybatis-source-learn) | 手写简化版 MyBatis：Mapper 接口 → 动态代理 → SQL 映射 → 结果集封装全链路 | Java · Maven · H2 | `./start.command`（macOS）/ `start.bat`（Windows） |
| [springboot-mybatis-learn](./springboot-mybatis-learn) | Spring Boot + MyBatis 最小整合：数据源、Mapper 扫描、事务的基础配置 | Spring Boot 2.6.13 · MyBatis · MySQL | `mvn spring-boot:run`（:8080） |
| [ssm-learn](./ssm-learn) | 传统 SSM（Spring + SpringMVC + MyBatis）企业级分层模板，对照 Spring Boot 看差异 | Spring 5 · SpringMVC · MyBatis · Maven | `./start.command` / `start.bat` |
| [spring-transaction-boundary-lab](./spring-transaction-boundary-lab) | 事务边界实验：7 种传播行为的实际表现、事务失效的典型场景（自调用 / 异常被吞 / 非 public 等）可复现验证 | Spring Boot 3.5.16 · JPA | `./start.command` / `start.bat`（:8095） |

---

## 建议阅读顺序

```text
spring-ioc-aop-learn  →  mybatis-source-learn  →  springboot-mybatis-learn
        (IoC/AOP 原理)          (ORM 与代理)             (自动配置整合)
                                                        ↓
                                              ssm-learn  →  spring-transaction-boundary-lab
                                              (传统分层)      (事务这类高频面试点)
```

---

## 注意事项

- 本组**全部是纯后端工程，没有前端**，启动后靠控制台输出或 HTTP 接口验证，没有网页界面。
- `springboot-mybatis-learn` 是 Spring Boot **2.6**（JDK 8/11 即可跑），其余需要 **JDK 17+**。
- `spring-transaction-boundary-lab/docs/logs/` 下是事务实验的**交付证据日志**，被根 `.gitignore` 的 `!**/docs/logs/*` 例外规则保留跟踪，**不要清理**。
- 双击脚本：macOS 用 `.command`，Windows 用 `.bat`，两者互相独立；脚本内部用 `dirname $0` 定位自身，移动目录后仍可用。

返回 [仓库总览](../README.md)
