# finpay-payment-wechat 微信支付 V3 渠道实现

基于 `wechatpay-apache-httpclient`，实现 `PaymentChannel` 接口（V3 API）。

## 关键实现
- `impl.WechatChannel`：
  - `createOrder`：NATIVE 下单 `/v3/pay/transactions/native`，返回 `code_url`。
  - `parseAndVerifyCallback`：用**平台证书**验证 Header 签名，再用 **APIv3Key** 对 `resource` 做 AES-GCM 解密得到明文。
  - `refund` / `queryOrder`：退款与订单查询（金额单位为「分」）。
- 平台证书：通过 `AutoUpdateCertificatesVerifier` 凭商户私钥 + APIv3Key **自动下载缓存**，无需手动配置。

## 接入要点
- 配置来自 `ChannelConfig.params`：`privateKey`(PEM)、`serialNo`、`apiV3Key`、`appId`。
- 微信签名在 **HTTP Header**，与支付宝（表单内）不同——因此核心接口 `parseAndVerifyCallback(body, headers, cfg)` 额外接收 headers。
- 金额单位为「分」，下单/退款时 `元×100` 转换。
