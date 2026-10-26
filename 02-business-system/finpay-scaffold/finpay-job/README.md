# finpay-job 对账与任务模块

负责日终对账、差错处理与补偿重试调度。接口驱动，数据来源由业务实现。

## 关键类
- `model`：`ReconciliationBill`（账单行）、`Mismatch`（差错）、`ReconcileReport`（对账报告）。
- `spi.BillSource` / `spi.LocalOrderSource`：渠道账单与本地订单数据源（业务实现）。
- `service.ReconciliationService` / `CompensationService`：对账与补偿契约。
- `impl.DefaultReconciliationService`：按 `outTradeNo` 比对，产出 LOCAL_ONLY / CHANNEL_ONLY / AMOUNT_DIFF / STATUS_DIFF 四类差错。
- `impl.DefaultCompensationService`：内存补偿队列，最多重试 3 次，耗尽升级人工。
- `task.ReconciliationScheduler`：`@Scheduled` 每日 02:30 对账、每 5 分钟重试补偿（需业务主类 `@EnableScheduling`）。

## 扩展
- 生产将 `DefaultCompensationService` 的补偿队列换为数据库表 + 调度器。
- `BillSource.fetchBill` 接入支付宝/微信账单下载并解析。
