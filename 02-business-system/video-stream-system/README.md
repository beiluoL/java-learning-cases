# 全球视频流系统（Java 后端实现）

面向「全球用户」的视频流系统**控制面 / 业务后端**参考实现，技术栈 **Spring Boot 3.4.1 + Java 17**，
覆盖文档《全球视频流系统方案设计》中的核心链路：**上传 → 转码编排 → 存储 → CDN 分发 → 播放**。

> 算力密集的 FFmpeg 转码在独立 **Worker 节点**执行（通过 Kafka 解耦），本进程无状态、可水平扩展。

## 1. 目录结构

```
video-stream-system/
├── pom.xml
├── src/main/java/com/example/videostream/
│   ├── common/                 # 错误码、业务异常、统一响应、全局异常处理、常量
│   ├── properties/             # 对象存储 / FFmpeg / CDN / 转码档位 配置绑定
│   ├── config/                 # S3 客户端、转码档位 Bean、Kafka 配置
│   ├── domain/                 # JPA 实体：MediaAsset / TranscodeTask / UploadSession
│   ├── repository/             # Spring Data JPA 仓库
│   ├── dto/                    # request / response / message（MQ 消息）
│   ├── service/
│   │   ├── storage/            # 对象存储抽象 + S3 兼容实现（上传/下载/分片/预签名）
│   │   ├── upload/             # 上传会话、分片直传预签名、秒传去重
│   │   ├── transcode/          # 档位查询、FFmpeg 封装、转码编排
│   │   ├── cdn/                # 播放鉴权签名、多区域 CDN 调度
│   │   └── media/              # 媒资查询、播放信息组装
│   ├── mq/                     # Kafka 生产者 + 转码 Worker 消费者
│   └── controller/             # 上传 / 媒资播放 / 云转码回调 接口
└── src/main/resources/application.yml
```

## 2. 核心流程

1. **上传**：`POST /api/v1/upload/sessions` 创建会话，返回各分片**预签名直传 URL**；客户端直传对象存储；
   `POST /api/v1/upload/complete` 校验分片完整 → 合并（multipart）→ **秒传去重**（同 hash 复用）→ 触发转码。
2. **转码**：`TranscodeService` 为一个媒资创建全部清晰度档位任务并投递 Kafka；Worker（`TranscodeTaskConsumer`）
   消费后下载源片 → `FfmpegWrapper` 转单档位 HLS → 回传切片 → 生成 master 播放列表 → 回告状态；全部完成则媒资置 `READY`。
3. **分发/播放**：`GET /api/v1/media/{mediaId}/playback?region=AP` 返回主播放列表与各档位清单，
   均带**就近 CDN 域名 + HMAC 防盗链签名**；播放器按 ABR 自适应切换清晰度。

## 3. 运行前置

- JDK 17、Maven 3.8+
- MySQL（库 `video_stream`，`spring.jpa.hibernate.ddl-auto=update` 自动建表；生产请改用 Flyway/Liquibase）
- Kafka（bootstrap 见 `application.yml`）
- 对象存储（AWS S3 / 阿里云 OSS S3 接口 / 腾讯云 COS / MinIO），配置 `object-storage.*`
- 转码 Worker 节点需安装 `ffmpeg`（GPU 编码需 NVIDIA NVENC 驱动）

```bash
mvn spring-boot:run
```

## 4. 生产部署建议

- **控制面与 Worker 分离**：Worker 节点启用 `@KafkaListener`（转码），控制面关闭监听，仅做编排；
  可通过 Spring Profile 切换（如 `-Dspring.profiles.active=worker`）。
- **多 CDN 调度**：`CdnDispatchService` 当前按区域静态选路，可扩展为按实时质量（RTT/丢包/命中率）动态选路。
- **转码策略**：热门内容「预转码」、冷内容「即时转码（JIT）」；规模上来后将云托管转码迁至自建 GPU 集群。
- **存储**：对象存储开启生命周期「热→冷→归档」分层，长尾切片沉降以降本。
- **安全**：播放 URL 签名密钥（HMAC）务必通过环境变量注入，勿硬编码；叠加 DRM（Widevine/ FairPlay/PlayReady）。

## 5. 关键设计取舍

| 决策点 | 选择 | 理由 |
|---|---|---|
| 持久层 | Spring Data JPA | 元数据读写简单，专注业务；可平滑替换为 MyBatis-Plus |
| 对象存储抽象 | `ObjectStorageService` 接口 | 解耦厂商，S3/OSS/COS/MinIO 可切换 |
| 转码解耦 | Kafka + 无状态 Worker | 算力弹性伸缩、失败重试、手动提交防丢 |
| 大文件上传 | S3 Multipart + 预签名分片 | 断点续传、绕过业务服务器、省带宽 |
| 播放鉴权 | HMAC-SHA256 签名 URL | 轻量防盗链，CDN 边缘可校验 |
| ABR | 服务端生成多档 HLS + master | 播放器原生自适应，CDN 缓存友好 |
