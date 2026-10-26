# 电商学习项目 · 前端（Vue 3）

基于 **Vue 3 + Vite 5 + Pinia + Vue Router + Element Plus + Axios** 的电商前台与管理后台。

## 运行
```bash
npm install
npm run dev      # http://localhost:5173
```

Vite 已配置 `/api` 代理到后端 `http://localhost:8080`，无需处理跨域。

## 功能
- 商品检索 / 详情（SPU + SKU 规格选择）
- 购物车（加入 / 改数量 / 结算）
- 下单 + 模拟支付（前端回调 `/api/pay/notify` 标记已支付）
- 我的订单（支付 / 取消 / 确认收货）
- 秒杀页（调用 Redis+Lua 原子抢购）
- 个人中心（资料编辑）
- 后台（看板 / 商品 / 订单 / 秒杀，需 ADMIN 角色）

## 目录
```
src/
├── api/         # 各模块接口封装
├── store/       # Pinia（user 登录态）
├── router/      # 路由 + 登录/管理员守卫
├── utils/       # request.js（Axios + JWT 拦截）
└── views/       # 页面（普通页 + admin/ 后台）
```

详见后端说明：`../ecommerce-learn/README.md`
