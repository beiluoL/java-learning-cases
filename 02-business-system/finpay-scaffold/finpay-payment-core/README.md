# finpay-payment-core 统一支付渠道抽象层

屏蔽支付宝/微信等渠道差异的核心抽象，是接入新渠道的扩展点。

## 核心契约
- `api.PaymentChannel`：渠道统一接口，定义 `createOrder / parseAndVerifyCallback / refund / queryOrder / queryRefund`。**新增渠道只需实现此接口。**
- `api.ChannelConfigProvider`：按「商户+渠道」提供运行配置（业务实现，数据源解耦）。
- `api.UnifiedPayService`：业务层唯一入口，按编码路由到具体渠道。
- `factory.ChannelManager`：Spring 自动收集所有 `PaymentChannel` Bean 注册为工厂。
- `model.dto.*`：标准化请求/结果/回调（`ChannelCallback` 已抹平渠道字段差异）。
- `model.enums`：`PayChannel` / `PayStatus` / `RefundStatus` 状态机。
- `exception.ChannelException`：渠道异常，携带渠道原始错误码。

## 新增一个渠道的步骤
1. 新建模块 `finpay-payment-xxx`，实现 `PaymentChannel`。
2. 在 `ChannelConfigProvider` 中提供该渠道配置。
3. 业务层用 `channel=xxx` 调用 `UnifiedPayService` 即可，零改造。
