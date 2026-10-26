# finpay-business 业务示例模块

聚合所有能力，提供两个完整示例与基础设施实现，是项目的**主启动工程**。

## 示例系统
1. **支付网关示例**：`web.gateway.PaymentGatewayController` + `PaymentOrderService` + `CallbackController`
   - 统一下单、退款、查询、交易列表。
   - 异步回调接收（`/callback/{channel}/{merchantNo}`），内部验签 + 幂等更新。
2. **银行交易系统示例**：`web.bank.*` + `web.tcc.TccTransferService`
   - 开户、余额查询、转账流水。
   - 转账采用 **TCC**（Try 冻结 → Confirm 提交 / Cancel 回滚），保证资金最终一致。

## 基础设施实现（注入为 Bean）
- `infrastructure.impl.DbChannelConfigProvider`：从 `t_channel_config` 提供渠道配置。
- `infrastructure.impl.DbAuditLogRecorder`：审计日志落库 `t_audit_log`。
- `infrastructure.impl.DbReconcileSource`：对账本地/渠道数据源（渠道侧待接入）。
- `auth.*`：登录签发 JWT（内置 admin/admin123，演示用）。

## 启动
```bash
mvn -pl finpay-business -am spring-boot:run   # 端口 8080
```
主类：`FinpayBusinessApplication`（`@MapperScan` + `@EnableScheduling`）。
