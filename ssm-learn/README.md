# SSM-Learn 企业级 SSM 框架学习模板

> **技术栈**：Spring 5.3 + Spring MVC 5.3 + MyBatis 3.5 + Druid + PageHelper + Layui 2.9
> **定位**：一套规范、可扩展的 SSM 项目骨架，用于 SSM 框架学习与二次开发。

---

## 一、目录结构（企业级分层）

```
ssm-learn
├── pom.xml                          # Maven 依赖与构建（版本统一在 <properties> 管理；含 jetty 插件）
├── start.sh                         # 一键启动（前端 8081 + 后端 8080）
├── stop.sh                          # 一键关闭
├── scripts/
│   └── frontend-dev-server.js       # 前端预览服务（Node 零依赖 + API 反代）
├── sql/
│   └── ssm_learn.sql                # 建库建表 + 演示数据脚本
└── src/main/
    ├── java/com/example/ssm/
    │   ├── controller/               # 控制层：接收请求、返回 JSON（@RestController）
    │   │   ├── UserController.java
    │   │   └── DashboardController.java
    │   ├── service/                  # 业务接口层
    │   │   ├── UserService.java
    │   │   └── impl/                 # 业务实现层（@Service + @Transactional）
    │   │       └── UserServiceImpl.java
    │   ├── mapper/                   # 数据访问层（MyBatis Mapper 接口，对应 dao 层）
    │   │   └── UserMapper.java
    │   ├── entity/                   # 实体类（与数据表字段一一对应，mapUnderscoreToCamelCase）
    │   │   └── User.java
    │   └── common/                   # 公共模块（跨业务复用）
    │       ├── result/               # 统一返回结构
    │       │   ├── Result.java       #   通用返回 {code,msg,data}
    │       │   └── PageResult.java   #   分页返回（对齐 Layui table）
    │       ├── exception/            # 异常处理
    │       │   ├── BusinessException.java     # 业务异常
    │       │   └── GlobalExceptionHandler.java # 全局异常捕获（@RestControllerAdvice）
    │       ├── aspect/               # AOP 切面（日志模块）
    │       │   └── WebLogAspect.java #   Service 层环绕日志
    │       ├── constant/             # 统一常量（魔法值集中管理）
    │       │   └── Constants.java
    │       └── util/                 # 通用工具类
    │           ├── SnowflakeUtil.java # 分布式 ID 生成
    │           └── DateUtils.java     # 日期格式化/解析
    ├── resources/
    │   ├── spring/
    │   │   ├── applicationContext.xml # 根容器：数据源/MyBatis/事务/AOP/组件扫描
    │   │   └── spring-mvc.xml         # Web 子容器：Controller 扫描/静态资源/视图
    │   ├── mybatis/
    │   │   ├── mybatis-config.xml     # MyBatis 全局设置 + 分页插件
    │   │   └── mapper/                # Mapper XML（SQL 映射）
    │   │       └── UserMapper.xml
    │   ├── jdbc.properties            # 数据库连接配置
    │   ├── log4j2.xml                 # 日志配置
    │   └── generatorConfig.xml        # MyBatis Generator 代码生成配置
    └── webapp/
        ├── WEB-INF/web.xml           # 容器入口：编码过滤/Druid/Spring/SpringMVC
        ├── index.html                # 后台框架页（左侧菜单 + iframe）
        ├── dashboard.html            # 工作台首页
        ├── page/user/list.html       # 用户管理 CRUD 页面（Layui）
        └── static/
            ├── js/common.js          # 公共脚本（上下文路径 ctx + fetch 封装）
            └── css/style.css
```

---

## 二、分层职责说明

| 层 | 包 | 职责 | 关键注解 |
|---|---|---|---|
| 控制层 | `controller` | 接收 HTTP 请求、参数校验、调用 Service、返回 JSON | `@RestController` `@RequestMapping` |
| 业务接口 | `service` | 定义业务契约 | — |
| 业务实现 | `service.impl` | 业务逻辑、事务控制 | `@Service` `@Transactional` |
| 数据访问 | `mapper` | 数据库 CRUD 定义（MyBatis 接口） | 由 `MapperScannerConfigurer` 代理 |
| 实体 | `entity` | 与表对应的 POJO | `@Data`(Lombok) |
| 公共 | `common` | 返回结构 / 异常 / 切面 / 常量 / 工具 | 见各子包 |

**请求链路**：`页面 AJAX → Controller → Service → Mapper → DB`，返回统一 `Result/PageResult` JSON，前端 Layui 渲染。

---

## 三、统一规范与基础设施

- **统一返回**：所有后端接口返回 `{code, msg, data}`；分页接口返回 `{code:0, count, data}`（对齐 Layui）。**成功码统一为 `0`**（`Result.success` 与 `PageResult` 均使用 `Constants.CODE_SUCCESS=0`），失败码用 `500` 或业务自定义码，前端按 `res.code === 0` 判定成功。
- **统一异常处理**：`@RestControllerAdvice` 拦截异常，业务异常返回明确提示，系统异常返回通用提示并记录日志。
- **日志模块**：`WebLogAspect` 通过 AOP 对 Service 层统一打印「方法/入参/耗时/异常」；底层用 slf4j + log4j2。
- **统一配置管理**：数据库连接集中在 `jdbc.properties`；Spring/SpringMVC/MyBatis 配置按职责拆分在 `resources/spring`、`resources/mybatis`；版本号集中在 `pom.xml` 的 `<properties>`。
- **事务**：`@Transactional(rollbackFor = Exception.class)` 声明式事务，遇异常自动回滚。
- **分页**：PageHelper 插件，调用 `PageHelper.startPage()` 即可自动分页。

---

## 四、一键启动 / 关闭脚本（推荐方式）

项目已内置一键脚本，**无需手动安装 Tomcat**：后端用 Maven 嵌入式 Jetty（`mvn jetty:run`）直接启动，前端用 Node 零依赖静态预览服务 + API 反代（端口 `8081`）一并拉起。

```
ssm-learn
├── start.sh                        # 一键启动：先后台拉起前端(8081)与后端(8080)
├── stop.sh                         # 一键关闭：精准关停两个服务进程
└── scripts/
    └── frontend-dev-server.js      # 前端预览服务（Node 原生 http，零第三方依赖）
```

**使用**：

```bash
cd ssm-learn
chmod +x start.sh stop.sh          # 首次需赋予执行权限（已默认加好）
./start.sh                          # 启动，终端展示两个服务的状态与日志
./stop.sh                           # 关闭
```

- 后端访问：`http://localhost:8080/`（工作台、Druid 监控同此端口）
- 前端访问：`http://localhost:8081/`（页面走同源 `ctx`，`/api/*` 自动反代到后端 8080）
- 脚本特性：自动识别项目根目录；后端/前端分别记录 PID 与日志（`logs/backend.log`、`logs/frontend.log`）；任一服务启动失败会及时提示；`stop.sh` 按 PID 精准关停，不误杀其他进程。
- 前置：JDK 8+、Maven 3.x、Node.js（双击 `start.sh` 或终端执行均可；Maven 首次运行会自动下载依赖）。

> 想走传统 Tomcat 部署？见下一节。

## 五、运行步骤

1. **准备环境**：JDK 1.8+、MySQL 5.7+、Tomcat 8.5/9、Maven 3.x。
2. **建库**：执行 `sql/ssm_learn.sql`。
3. **改配置**：修改 `src/main/resources/jdbc.properties` 中的 `jdbc.url/username/password`。
4. **导入 IDE**：以 Maven 项目导入（IntelliJ IDEA / Eclipse），**安装 Lombok 插件**（否则实体类报红）。
5. **配置 Tomcat**：Deployment 添加本项目，`Application context` 建议设为 `/`（便于前端 `ctx` 识别）；启动访问 `http://localhost:8080/`。
6. **Druid 监控**：访问 `http://localhost:8080/druid`，账号 `admin` / 密码 `admin123`。

> 前端 Layui 通过 CDN 引入；若要离线运行，把 Layui 下载到 `webapp/static/layui` 并修改页面 `<link>/<script>` 路径即可。

---

## 六、如何扩展一个新模块（二次开发）

以新增「角色管理 Role」为例，只需 5 步，无需改动任何现有配置：

1. **建表**：在 `sql/ssm_learn.sql` 追加 `sys_role` 表，执行。
2. **实体**：`entity/Role.java`（`@Data`，字段对应表）。
3. **Mapper**：`mapper/RoleMapper.java` 接口 + `resources/mybatis/mapper/RoleMapper.xml`（SQL）。
4. **Service**：`service/RoleService.java` 接口 + `service/impl/RoleServiceImpl.java`（标注 `@Service`）。
5. **Controller**：`controller/RoleController.java`（`@RestController` + `@RequestMapping("/api/role")`），返回 `Result/PageResult`。
6. **页面**：复制 `page/user/list.html` 改为角色管理页，左侧菜单加一项即可。

> 想更快？用 `resources/generatorConfig.xml` + MyBatis Generator 一键生成 entity / mapper / mapper.xml，再补 Service + Controller。

---

## 七、接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/user/list?page=1&limit=10&username=&status=` | 分页查询 |
| GET | `/api/user/{id}` | 详情 |
| POST | `/api/user` | 新增 |
| PUT | `/api/user` | 更新 |
| DELETE | `/api/user/{id}` | 删除单条 |
| DELETE | `/api/user/batch` | 批量删除（body 为 id 数组） |
| GET | `/api/dashboard/stats` | 工作台统计 |
