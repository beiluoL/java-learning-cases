# finpay-payment-alipay 支付宝渠道实现

基于 `alipay-sdk-java`，实现 `PaymentChannel` 接口。支持沙箱（`sandbox=true` 切换 `alipaydev` 网关）。

## 关键实现
- `impl.AlipayChannel`：
  - `createOrder`：NATIVE 扫码（`AlipayTradePrecreateRequest`，返回 `qrCode`）；PC 网站支付（`AlipayTradePagePayRequest`，返回跳转表单）。
  - `parseAndVerifyCallback`：用**支付宝公钥** `AlipaySignature.rsaCheckV1` 验签，再解析 `trade_status`。
  - `refund` / `queryOrder`：退款与订单查询。
- 配置来自 `ChannelConfig.params`：`privateKey`、`alipayPublicKey`、`appId` 等。

## 接入要点
- 平台保存「应用私钥」与「支付宝公钥」；回调必须先验签再入账。
- 金额单位为「元」（与微信不同）。
- 退款查询为进阶能力，本实现返回处理中占位。
