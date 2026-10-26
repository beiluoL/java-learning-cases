# finpay-common 公共模块

提供跨模块复用的基础能力，不依赖任何业务模块。

## 关键类
- `result.Result` / `result.ErrorCode`：统一响应体与业务错误码。
- `exception.BizException` / `GlobalExceptionHandler`：业务异常与全局异常处理。
- `util.SnowflakeId`：分布式 ID / 订单号生成。
- `util.AESUtil` / `RSAUtil` / `SignUtil`：AES 对称加密、RSA 非对称签名/验签、Hmac/MD5 摘要拼接。
- `util.JsonUtils`：基于 Fastjson2 的 JSON 工具。

## 约定
- 所有 Controller 返回统一包裹 `Result<T>`。
- 业务校验失败抛 `BizException(ErrorCode)`，由全局处理器转响应，禁止吞异常。
- 金额一律用 `BigDecimal`，禁止 `double` 参与金额运算。
