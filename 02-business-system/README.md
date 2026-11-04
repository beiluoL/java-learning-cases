# 02-business-system · 可跑的业务系统（含全栈）

> **这一组放什么**：能真正跑起来、有完整业务闭环的系统 —— 权限、审批流、订单、支付、设备接入。既有纯后端，也有前后端分离的全栈工程。
>
> **为什么单独成组**：框架原理（`01-spring-core`）会了以后，真正的差距在**业务复杂度**：多表事务、状态机、并发扣减、权限模型、模块拆分。这一组就是练这个的。

---

## 组内工程

| 工程 | 业务域 | 技术栈 | 前端 | 启动方式 |
|---|---|---|---|---|
| [erp-crm-system](./erp-crm-system) | 企业 ERP/CRM：RBAC 权限、审批流引擎、进销存 / HRM / CRM 模块 | Spring Boot 3.4.1 · MyBatis-Plus 3.5.5 · H2 / MySQL | 否 | `mvn spring-boot:run`（:8090） |
| [video-stream-system](./video-stream-system) | 全球视频流：上传 → 转码 → 存储 → 分发 → 播放链路 | Spring Boot 3.4.1 · Java 17 | 否 | `mvn spring-boot:run`（:8080） |
| [spring-vue-learn](./spring-vue-learn) | 最小可执行的前后端分离骨架，用来先跑通联调 | Spring Boot 3.4.1 · Vue 3 · Vite | 是 | `./start.command` / `start.bat` |
| [ecommerce-learn-all](./ecommerce-learn-all) | 电商全栈（合并式目录：后端 `ecommerce-learn` + 前端 `ecommerce-learn-vue`） | Spring Boot · MyBatis · Vue 3 · Vite | 是 | `./start.command` |
| [iot-platform](./iot-platform) | 企业级 IoT 物联网 + 大数据学习平台 | Spring Boot 3.2.12 · Vue 3 | 是 | `./start.command` / `start.bat` |
| [finpay-scaffold](./finpay-scaffold) | 金融支付多模块脚手架：网关 + 业务 + Web 前端 | Spring Boot 3.2.5 · Gateway · Vue 3 | 是 | 网关 `mvn -pl finpay-gateway spring-boot:run`（:8088）；业务（:8080）；前端 `cd finpay-web && npm run dev` |
| [mq-practice](./mq-practice) | MQ 实战三场景：秒杀削峰填谷、延迟消息超时关单、商品变更发布订阅（含确认/ACK/幂等/重试/死信回放 + 可视化控制台） | Spring Boot 3.4.1 · RabbitMQ · MyBatis-Plus · Redis · Vue 3 | 是 | `./start.command` / `start.bat`（自动拉起 RabbitMQ） |

---

## 建议阅读顺序

```text
spring-vue-learn        ← 先跑通"前端能调到后端"这件事，成本最低
      ↓
erp-crm-system          ← 权限模型 + 审批流引擎，单一系统里的业务复杂度上限
      ↓
video-stream-system     ← 无前端，专注链路设计（上传/转码/播放）
      ↓
iot-platform            ← 设备接入 + 多模块
      ↓
finpay-scaffold         ← 多模块 + 网关 + 支付，最接近企业工程形态
```

---

## 目录形态说明

本组两种前后端组织形态并存，都是仓库约定允许的：

- **分离式**：`spring-vue-learn/backend` + `spring-vue-learn/frontend`、`iot-platform/backend` + `iot-platform/frontend`
- **合并式**：`ecommerce-learn-all/ecommerce-learn`（后端）+ `ecommerce-learn-all/ecommerce-learn-vue`（前端）

`finpay-scaffold` 是 Maven 多模块（`finpay-gateway` / `finpay-business` / `finpay-web` …），根目录没有一键脚本，需按下表分别启动。

---

## 注意事项

- **端口冲突是最常见问题**：`erp-crm-system` 特意用 `8090`（避开本机常被占的 8080/8081）；同时跑多个工程时注意错开（`--server.port=xxxx`）。
- 前端工程首次运行需 `npm install`，`node_modules` 体积大且**不入库**（已被忽略），删了重装即可。
- `ecommerce-learn-all` 的一键脚本只有 macOS 版（`.command`），Windows 需手动起前后端。
- 各工程密钥（如支付、短信）统一走环境变量，不写进配置文件。

返回 [仓库总览](../README.md)
