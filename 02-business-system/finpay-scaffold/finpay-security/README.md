# finpay-security 安全模块

支付安全的基石：密钥管理、敏感数据加密、审计日志、JWT 鉴权、签名验签。

## 关键类
- `key.KeyManager`：持有平台 AES/RSA 密钥，按「渠道+商户」缓存渠道公钥，用于回调验签。
- `config.CryptoProperties`：加密相关配置（`finpay.crypto.*`），生产经 KMS 注入。
- `crypto.SensitiveCryptoService` / `SensitiveConverter`：敏感字段 AES 落库加密、读取解密（JPA 转换器）。
- `audit.AuditLog` / `AuditLogAspect` / `AuditLogRecorder`：操作审计注解+切面；`AuditLogRecorder` 由业务实现落库。
- `auth.JwtTokenProvider` / `JwtAuthFilter` / `SecurityConfig`：无状态 JWT 鉴权与 RBAC。
- `service.SignVerifyService`：平台私钥签名、渠道公钥验签。

## 扩展
- 新增审计落库方式：实现 `AuditLogRecorder` 并注册为 Bean。
- 替换密钥来源：修改 `KeyManager`，从 KMS/配置中心加载。
