# 企业级 IoT 物联网 + 大数据学习平台

基于 **Spring Boot 3 + Vue 3** 的企业级物联网（IoT）与大数据场景学习项目，采用标准多模块 Maven 工程与分层、模块化前端设计，内置 **MQTT 设备通信、实时数据采集与分析、舆情监控、数据可视化、实时数据看板** 等核心能力。代码清晰、模块分明，适合作为企业级开发与学习参考模板，开箱即可二次开发。

> 项目后端内置 **嵌入式 MQTT Broker（moquette）**，前端通过 **WebSocket(STOMP)** 接收实时推送，无需额外部署中间件即可一键运行。

---

## 一、技术栈

| 层 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 3.2 / Java 17 |
| 设备通信 | Eclipse Paho(MQTT 客户端) + moquette(内嵌 Broker) |
| 数据持久化 | Spring Data JPA + H2（默认，可切 MySQL） |
| 实时推送 | Spring WebSocket + STOMP + SockJS |
| 前端框架 | Vue 3 + TypeScript + Vite |
| UI / 图表 | Element Plus + ECharts 5 |
| 状态 / 请求 | Pinia + Axios |

---

## 二、工程结构（分层 + 模块化）

```
iot-platform/
├── backend/                 # Spring Boot 多模块后端（聚合工程）
│   ├── iot-common/          # 公共层：统一响应、异常、常量、工具、基础实体
│   ├── iot-mqtt/            # 设备通信层：内嵌 Broker + 订阅客户端 + 消息路由 + 设备注册中心
│   ├── iot-collector/       # 数据采集层：设备/数据点建模、持久化、采集与查询服务
│   ├── iot-analysis/        # 实时分析层：滚动窗口、统计、异常检测、告警
│   ├── iot-opinion/         # 舆情监控层：舆情采集、情感分析、热点话题
│   ├── iot-visualization/   # 可视化聚合层：面向前端图表的跨模块聚合
│   └── iot-web/             # 接口层：REST 控制器、WebSocket 推送、主启动类、模拟调度
├── frontend/                # Vue 3 前端（可视化看板）
├── start.command            # macOS 一键启动（双击）
├── stop.command             # macOS 一键关闭（双击）
├── start.bat                # Windows 一键启动（双击）
└── stop.bat                 # Windows 一键关闭（双击）
```

**调用链路（以设备遥测为例）：**
```
设备/模拟器 --MQTT--> 内嵌Broker --> Paho订阅 --> DeviceDataMessageHandler
   --> 发布 DeviceDataReceivedEvent
        ├--> iot-collector：自动纳管设备 + 落库数据点
        └--> iot-analysis：更新实时快照 + 异常检测 --> 命中则发布 AnalysisAlertEvent
                              └--> iot-web WebSocket 推送 /topic/alert
前端 <--WebSocket(STOMP)-- 实时指标 / 告警 / 舆情
```

---

## 三、环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+（建议使用 20+）

---

## 四、快速启动

### 方式一：一键脚本（推荐）

项目根目录提供跨平台双击脚本：

| 系统 | 启动 | 关闭 |
| --- | --- | --- |
| macOS | 双击 `start.command` | 双击 `stop.command` |
| Windows | 双击 `start.bat` | 双击 `stop.bat` |

脚本会自动编译后端（首次）、安装前端依赖并拉起前后端，**保持窗口可查看实时日志**。启动后访问：

- 前端看板：<http://localhost:5173>
- 后端接口：<http://localhost:8080>
- H2 控制台：<http://localhost:8080/h2-console>（JDBC URL：`jdbc:h2:./data/iotdb`）

### 方式二：手动启动

```bash
# 1) 后端（在 backend 目录）
mvn -pl iot-web -am package -DskipTests
java -jar iot-web/target/iot-web-1.0.0.jar

# 2) 前端（另开终端，在 frontend 目录）
npm install
npm run dev
```

---

## 五、核心功能

1. **MQTT 设备通信**：内嵌 Broker 在 `1883` 端口，设备向 `iot/device/{deviceId}/data` 发布遥测；后端订阅后路由到采集与分析模块。支持通过 `MqttGateway` 下发命令与推送模拟数据。
2. **数据采集与实时分析**：设备数据自动落库（时序数据点），分析层维护滚动窗口实时统计（均值/最值），并基于阈值规则进行异常检测，命中即产生分级告警（WARN / CRITICAL）。
3. **舆情监控系统**：内置情感词典分析器对舆情文本打标（正面/负面/中性），提供情感分布、趋势、热点话题排行；可模拟生成舆情数据。
4. **数据可视化系统**：可视化聚合层把采集/分析/舆情数据编排为图表友好结构（饼图、趋势线、对比线、TOP 榜）。
5. **实时数据看板**：前端通过 STOMP 订阅 `/topic/device-metrics`、`/topic/alert`、`/topic/opinion` 接收实时推送，结合 REST 轮询呈现总览、设备、分析、舆情、可视化五大页面。

---

## 六、主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/dashboard/overview` | 平台总览 |
| GET | `/api/dashboard/device-status` | 设备状态分布 |
| GET | `/api/dashboard/metric-trend` | 单设备指标趋势 |
| GET | `/api/devices` | 设备分页 |
| POST | `/api/devices` | 新增/编辑设备 |
| GET | `/api/analysis/realtime` | 实时指标快照 |
| GET | `/api/analysis/alerts` | 告警列表 |
| GET | `/api/opinion/list` | 舆情列表 |
| GET | `/api/opinion/stat` `trend` `hot` | 舆情统计/趋势/热点 |
| POST | `/api/simulator/device-data` | 触发设备数据模拟 |
| POST | `/api/simulator/opinion` | 触发舆情模拟 |
| WS | `/ws` (STOMP) | 订阅 `/topic/*` 实时推送 |

> 统一响应结构：`{ "code": 200, "message": "操作成功", "data": ..., "timestamp": ... }`
> 分页结构：`{ "records": [...], "total": ..., "pageNum": ..., "pageSize": ..., "pages": ... }`

---

## 七、二次开发指南

- **新增设备类型 / 指标**：在 `iot-collector` 的 `Device` / `DeviceDataPoint` 实体与 `iot-analysis` 的 `DefaultRuleProvider` 阈值规则中扩展即可。
- **替换外部 MQTT Broker**：移除 `EmbeddedMqttBroker`，在 `application.yml` 的 `iot.mqtt.host/port` 指向 EMQX / Mosquitto。
- **接入真实舆情源**：替换 `iot-opinion` 的 `OpinionSimulator`，改为爬虫 / 开放平台 API / MQ 订阅，调用 `OpinionService.analyzeAndSave` 即可复用分析链路。
- **生产数据库**：将 `application.yml` 中 `spring.datasource` 改为 MySQL，并新建对应表（实体已遵循阿里建表规约：无外键、逻辑外键建索引、必备四字段）。
- **前端扩展**：在 `frontend/src/api` 增加接口封装，在 `frontend/src/views` 增加页面并在 `router/index.ts` 注册。

---

## 八、代码规范

后端遵循《阿里巴巴 Java 开发手册》核心规约（数据库无外键、逻辑外键建索引、命名小写、禁用魔法值、线程池显式创建等）；前端遵循 Airbnb JavaScript 规范 + Vue 3 官方风格指南（`<script setup>`、Prop 详定义、组合式 API、`===`、单引号、分号等）。
