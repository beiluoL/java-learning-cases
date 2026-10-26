# Spring Vue Learn — Spring Boot + Vue3 最小可执行学习框架

面向**学习目的**的前后端分离最小骨架：后端 Spring Boot + MyBatis-Plus + MySQL，前端 Vue 3 + Vite + TypeScript。代码简洁、无复杂业务逻辑，开箱即可运行。

---

## 技术选型

| 层 | 选型 | 说明 |
|---|---|---|
| 后端框架 | Spring Boot 3.4.1 | 当前稳定线，要求 JDK 17+ |
| JDK | 17（LTS） | |
| 构建工具 | Maven 3.8+ | 约定优于配置 |
| 持久化 | MyBatis-Plus 3.5.7 + MySQL | 默认 MySQL；另含 `h2` 零配置 profile |
| 数据库驱动 | mysql-connector-j 8.0.33 | 兼容 MySQL 5.7 / 8.0 |
| 前端框架 | Vue 3.5 + Composition API | `<script setup>` 写法 |
| 构建工具 | Vite 6 | 冷启动快、配置少 |
| 语言 | TypeScript | 类型安全 |
| 路由 | Vue Router 4 | |
| 状态管理 | Pinia 2 | 官方推荐 |
| HTTP 客户端 | axios | 调后端接口 |

---

## 目录结构

```
spring-vue-learn/
├── backend/                 # Spring Boot 后端（Maven 工程）
│   ├── pom.xml
│   └── src/main/java/com/example/springvuelearn/
│       ├── SpringVueLearnApplication.java   # 启动入口
│       ├── config/                          # MyBatis-Plus / 自动填充 / CORS
│       ├── entity/User.java                 # 实体（对应表 sys_user）
│       ├── mapper/UserMapper.java           # BaseMapper
│       ├── service/ + impl/                 # 业务层
│       ├── dto/UserCreateRequest.java       # 新增入参（record）
│       └── controller/                      # SysUserController + HealthController
│   └── src/main/resources/
│       ├── application.yml                  # 公共配置（激活 mysql profile）
│       ├── application-mysql.yml            # MySQL 数据源 + 自动建表
│       ├── application-h2.yml               # H2 零配置 profile
│       ├── schema.sql / schema-h2.sql       # 建表脚本
├── frontend/                # Vue3 前端（Vite 工程）
│   ├── package.json / vite.config.ts / tsconfig*.json
│   └── src/
│       ├── main.ts / App.vue
│       ├── router/        # 路由
│       ├── store/user.ts  # Pinia 状态
│       ├── api/user.ts    # axios 封装
│       ├── types/user.ts  # 类型
│       └── views/HomeView.vue  # 示例页：展示/新增用户
├── start.command / start.bat    # 一键启动（macOS / Windows 双击）
├── stop.command  / stop.bat     # 一键关闭
├── restart.command/ restart.bat # 一键重启
├── logs/   # 运行日志（自动生成）
└── .run/   # PID 文件（自动生成）
```

---

## 快速开始（一键脚本）

- **macOS**：双击 `start.command`（若从网络下载，先解除隔离：`xattr -d com.apple.quarantine start.command`）
- **Windows**：双击 `start.bat`
- 启动后访问 **http://localhost:5173**
- 关闭：双击 `stop.command` / `stop.bat`（或 `restart.command` / `restart.bat` 重启）

脚本会：检查 java/mvn/node → 拉起后端（:8080）→ 首次自动 `npm install` 并拉起前端（:5173）→ 打印状态。已运行则自动重启；关闭精准按 PID/端口，无误杀。

---

## 手动启动

### 后端（MySQL，默认）
1. 准备本地 MySQL（5.7 或 8.0），创建数据库：
   ```sql
   CREATE DATABASE spring_vue_learn DEFAULT CHARACTER SET utf8mb4;
   ```
2. 若账号密码不是 `root/root`，修改 `backend/src/main/resources/application-mysql.yml`。
3. 启动：
   ```bash
   mvn -f backend/pom.xml spring-boot:run
   ```

### 前端
```bash
cd frontend
npm install
npm run dev
```

> 不想装 MySQL？用 **H2 零配置 profile**（已端到端验证可用）：
> ```bash
> SPRING_PROFILES_ACTIVE=h2 mvn -f backend/pom.xml spring-boot:run
> ```
> 等价写法：`mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=h2`
> （注意：`spring-boot:run` 不直接转发 `-Dspring.profiles.active`，需用环境变量或 `spring-boot.run.profiles`）
> 前端启动方式不变。H2 控制台：http://localhost:8080/h2-console （JDBC URL 见 `application-h2.yml`）。

---

## 示例接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/users` | 用户列表 |
| POST | `/api/users` | 新增用户，Body：`{"username":"张三","email":"a@b.com"}` |
| GET | `/api/users/{id}` | 用户详情 |
| GET | `/api/health` | 健康检查（供启动脚本探测） |

前端首页即调用 `/api/users` 展示列表，并可通过表单新增用户（Vite 代理把 `/api` 转发到 8080）。

---

## 约定与说明

- 遵循《阿里巴巴 Java 开发手册》强制项：表名小写 `sys_user`、必备 `id/create_time/update_time`、逻辑删除 `deleted`、唯一索引 `uk_username`；未使用物理外键。
- 后端已配置 CORS，放行 `http://localhost:5173`。
- 生产环境应将 DO 与接口返回分离（当前为学习演示，直接返回 `User`）。
- 一键启停脚本按本项目交付约定生成（macOS `.command` + Windows `.bat`，双击运行、端口自校验、精准关停）。
