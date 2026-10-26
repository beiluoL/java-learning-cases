# 可参考的开源项目（对标实现）

以下项目可用于对标本方案各模块的设计思路与核心实现。标注「★ Java 相关」的与本实现语言栈最贴近。

| # | 项目 | 仓库地址 | 语言 / 协议 | Stars(约) | 可借鉴的核心模块 / 设计思路 |
|---|---|---|---|---|---|
| 1 | **Ant Media Server** ★ | https://github.com/ant-media/Ant-Media-Server | Java / WebRTC, RTMP, HLS, CMAF, LL-DASH | 4.7k | **最贴近本栈的 Java 媒体服务器**。借鉴：WebRTC 超低延迟（~0.5s）、自适应码率（ABR）、SFU、集群与自动扩缩、Rest API 设计。转码与分发可直接参考其架构。 |
| 2 | **Red5** ★ | https://github.com/Red5/red5-server | Java / RTMP, RTMPT, WebSocket | 1.5k+ | 老牌 Java 媒体服务器。借鉴：RTMP  ingest/边缘节点模型、流生命周期管理、插件化扩展点。 |
| 3 | **SRS (Simple Realtime Server)** | https://github.com/ossrs/srs | C++ / RTMP, WebRTC, HLS, HTTP-FLV, SRT, DASH | 29k | 全球最流行的开源直播服务器之一。借鉴：**ingest→转协议→分发** 的清晰分层、LL-HLS/WebRTC 低延迟方案、HTTP API + 回调鉴权、边缘-源站集群部署。文档极详尽，强烈推荐通读其 Wiki。 |
| 4 | **ZLMediaKit** | https://github.com/ZLMediaKit/ZLMediaKit | C++11 / RTSP, RTMP, HLS, WebRTC, GB28181 | 15k+ | 高性能运营级框架。借鉴：**协议互转**、按需转协议（有人观看才转，降 CPU）、边缘回源集群、WebHook 事件驱动业务、单机 10w 级并发的设计。 |
| 5 | **MediaCMS** | https://github.com/mediacms-io/mediacms | Python/Django + React / HLS 自适应 | 4.9k | 现代化媒资 CMS。借鉴：**产品层设计**——多分辨率播放器（video.js）、RBAC 权限、频道/播放列表/字幕、缩略图、审核流。适合对标「媒资管理 + 播放体验」。 |
| 6 | **Owncast** | https://github.com/owncast/owncast | Go / RTMP ingest + HLS output | 11k+ | 单用户自托管直播。借鉴：**极简直播管线**（RTMP 推流 → 转 HLS → 播放 + 聊天），以及单人/小团队场景的轻量架构。 |
| 7 | **OvenMediaEngine** | https://github.com/AirenSoft/OvenMediaEngine | C++ / Sub-Second Latency (LL-HLS/WebRTC) | 3k+ | 亚秒级延迟流媒体引擎。借鉴：超低延迟直播的**边缘吞吐模型**、OVT/WebRTC 传输优化。 |
| 8 | **androidx/media (ExoPlayer)** ★ | https://github.com/androidx/media | Kotlin/Java / HLS, DASH, SmoothStreaming | 2k+ | Android 播放器事实标准（Java/Kotlin）。借鉴：**ABR 自适应算法实现**、TrackSelection、缓冲模型——直接对标本方案「播放端自适应」逻辑。 |
| 9 | **FFmpeg** | https://github.com/FFmpeg/FFmpeg | C / 转码引擎 | 50k+ | 转码事实标准。借鉴：`FfmpegWrapper` 的命令行参数构造（scale / codec / bitrate / hls_time）、硬件编码（NVENC）、滤镜链。所有转码方案的底层依赖。 |
| 10 | **PeerTube** | https://github.com/Chocobozzz/PeerTube | Node.js / 联邦化 + P2P(BitTorrent) | 13k+ | 去中心化视频平台。借鉴：**P2P/WebRTC 辅助分发**思路，用于降低 CDN 带宽成本（尤其热点内容）。 |
| 11 | **Livepeer** | https://github.com/livepeer/go-livepeer | Go / 去中心化视频转码网络 | 1.5k+ | 视频转码网络 + AI。借鉴：转码任务**竞价/调度**模型、将转码作为可编排服务的思路。 |

## 对标映射（本方案 → 开源参考）

| 本方案模块 | 最佳对标项目 | 说明 |
|---|---|---|
| 转码 Worker（FFmpeg 编排） | FFmpeg + SRS/ZLMediaKit 转码模块 | 命令构造与硬件加速直接参考 FFmpeg；分布式调度参考 SRS/ZLMediaKit 的 worker 模型 |
| 低延迟直播分发 | SRS / Ant Media Server / OvenMediaEngine | 实现 LL-HLS / WebRTC 低延迟分发时优先参考这三者的配置与协议转换 |
| Java 媒体服务骨架 | Ant Media Server / Red5 | 若需在 JVM 内自研媒体能力（而非纯控制面），以这两者为主要参考 |
| 播放端 ABR | androidx/media (ExoPlayer) | Web/hls.js、iOS AVPlayer 的 ABR 策略可借鉴 ExoPlayer 的 TrackSelection 思想 |
| 媒资/产品层 | MediaCMS | 权限、播放列表、封面、审核等围绕视频的业务功能参考 |
| 降本（带宽） | PeerTube (P2P) | 热点内容叠加 P2P 分发降低 CDN 成本 |
