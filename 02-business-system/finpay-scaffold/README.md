# FinPay · 金融支付企业级脚手架（Spring Boot 3 + Vue 3）

面向**金融与支付领域**、对**安全性与稳定性**要求极高的业务场景的脚手架框架。提供统一支付渠道抽象、敏感数据加密、签名验签、操作审计、TCC 分布式事务与日终对账能力，并内置「支付网关」「银行交易系统」两个完整示例，配套 Vue 管理后台与零基础友好文档。

## 特性一览
- **多模块分层**：common / security / payment-core / payment-alipay / payment-wechat / job / business / gateway，职责清晰、解耦、易二次开发。
- **统一支付渠道抽象**：`PaymentChannel` 接口 + `ChannelManager` 工厂，新增渠道 = 实现接口 + 登记配置，业务层零改造。
- **第三方支付全流程**：支付宝、微信支付的**下单 / 回调验签 / 退款 / 查询**。
- **安全能力**：AES 敏感数据落库加密、RSA 签名验签、密钥管理（KMS 友好）、JWT 权限控制、完整操作审计日志（`@AuditLog`）。
- **一致性保障**：基于 **TCC** 模式的分布式事务（Try/Confirm/Cancel）；**日终对账**（差错处理 + 补偿重试调度）。
- **Vue 管理后台**：交易查询、对账管理、审计日志、渠道配置四大页面（Ant Design Vue）。
- **详尽文档**：`docs/支付场景详细文档.md` 含 SVG 图解，覆盖环境准备 → 商户/沙箱申请 → 密钥配置 → 时序流程 → 逐步操作 → 验签幂等 → 联调测试 → 错误排查。

## 工程结构
```
finpay-scaffold/
├── finpay-common/          公共：统一响应、异常、雪花ID、AES/RSA/签名工具
├── finpay-security/        安全：密钥管理、敏感加密、审计切面、JWT 鉴权、验签
├── finpay-payment-core/    统一支付抽象：渠道接口、工厂、DTO、门面
├── finpay-payment-alipay/  支付宝渠道实现（沙箱就绪）
├── finpay-payment-wechat/  微信支付 V3 渠道实现
├── finpay-job/             对账与任务：日终对账、差错处理、补偿重试
├── finpay-business/        业务示例：支付网关 + 银行交易系统 + TCC + 主启动类
├── finpay-gateway/         网关：Spring Cloud Gateway 路由/鉴权/限流
├── finpay-web/             Vue 管理后台
├── db/schema.sql           建库建表脚本
├── docs/                   支付场景文档、数据库设计、接口文档、SVG 图解
└── scripts/                一键启动/停止脚本（macOS .command + Windows .bat）
```

## 快速开始
```bash
# 1. 初始化数据库
mysql -uroot -p < db/schema.sql

# 2. 编译
mvn -q clean install -DskipTests

# 3. 启动后端（另开终端启网关）
mvn -pl finpay-business -am spring-boot:run
mvn -pl finpay-gateway -am spring-boot:run

# 4. 启动前端
cd finpay-web && npm install && npm run dev   # http://localhost:5173
```
登录：admin / admin123。接口文档：http://localhost:8080/doc.html

> 未配置 `finpay.crypto.*` 时框架自动生成开发用测试密钥；**生产务必通过环境变量/KMS 注入**，禁止明文入库。

## 文档索引
- [支付场景详细文档（零基础）](docs/支付场景详细文档.md)
- [数据库设计](docs/数据库设计.md)
- [接口文档](docs/接口文档.md)
- 各模块 README：`finpay-*/README.md`

## 技术栈
Java 17 · Spring Boot 3.2 · Spring Cloud 2023 · MyBatis-Plus · MySQL 8 · Redis · JWT ·
alipay-sdk-java · wechatpay-apache-httpclient · Vue 3 · Vite · TypeScript · Ant Design Vue · Knife4j
