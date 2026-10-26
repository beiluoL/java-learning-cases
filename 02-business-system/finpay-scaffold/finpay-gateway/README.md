# finpay-gateway 网关模块

Spring Cloud Gateway 统一入口：路由转发、JWT 鉴权、CORS。

## 关键类
- `FinpayGatewayApplication`：网关启动类。
- `filter.GatewayAuthFilter`：全局过滤器，对 `/api/**` 校验 JWT，放行 `/auth/**`、`/callback/**`、文档与健康检查；校验通过后将用户名写入 `X-User` 头透传下游。

## 路由（application.yml）
- `/auth/**`、`/callback/**` → 直连业务（免鉴权）。
- `/api/**` → 业务服务（经网关统一鉴权）。
- 全局 CORS 放行预检。

## 启动
```bash
mvn -pl finpay-gateway -am spring-boot:run   # 端口 8088
```
前端既可直连 8080，也可统一经网关 8088（二者 JWT 密钥需一致，已通过环境变量对齐）。
