# 压测脚本与性能监控报告

零第三方依赖（仅用 Python 标准库），对 `redis-fullstack-lab` 的高并发/核心场景做压力测试，
并自动生成**自包含 HTML 监控报告**（内联 SVG 图表，离线可看，不依赖任何 CDN）。

## 支持的压测场景

| 场景 | 接口 | 看点 |
| --- | --- | --- |
| `ratelimit` | `GET /api/ratelimit/order`（QPS=5） | 限流削峰：报告里能看到明显的 QPS 封顶与 ~99% 拒绝率 |
| `lock` | `POST /api/lock/deduct`（手写 Redis 锁） | 锁竞争：并发越高，响应时间抬升越明显（对比其它场景） |
| `cache` | `GET /api/cache/product/1` | 缓存读路径：低延迟、高 QPS |
| `rank` | `POST /api/rank/add` | ZSET 写密集：吞吐与延迟 |
| `penetration` | `GET /api/penetration/product/{id}` | 布隆过滤器拦截：绝大多数请求不落 DB |
| `avalanche` | `GET /api/avalanche/product/1?protect=true` | 多级缓存扛雪崩：延迟平稳 |

## 用法

```bash
# 1) 先起后端（默认 8080）与 Redis
cd .. && docker compose up -d          # 起 Redis
# 另开终端启动后端（或双击 start.command / start.bat）
cd backend && mvn -q spring-boot:run

# 2) 运行压测（Python 3.8+，无需 pip install）
python3 run_benchmark.py --scenario all --concurrency 50 --requests 600

# 单场景示例
python3 run_benchmark.py --scenario ratelimit --concurrency 200 --requests 1000
python3 run_benchmark.py --scenario lock      --concurrency 50  --requests 1500 --rps 100

# 指定后端地址（也可指向前端代理 5173）
python3 run_benchmark.py --scenario cache --host http://localhost:8080
```

参数说明：
- `--scenario`：上述场景之一，或 `all`（跑全部并出一份汇总对比报告）
- `--concurrency`：并发线程数（模拟突发流量）
- `--requests`：总请求数
- `--rps`：限速（令牌桶），>0 时把流量平滑到指定 QPS，模拟平稳真实流量

## 生成的报告

写入 `benchmark/report/`：
- 每个场景一个 `report/<场景>_<时间戳>.html`，含 4 张图：
  1. **QPS 随时间**（吞吐）
  2. **响应时间 p50/p95/p99 随时间**
  3. **错误率 / 拒绝率 随时间**
  4. **响应时间分布直方图**
  以及核心指标卡（成功率、错误率、P95/P99、最大 RT 等）
- `--scenario all` 额外生成 `report/summary_<时间戳>.html`，含多场景 QPS 对比图与汇总表。

直接用浏览器打开 HTML 即可查看，无需联网。

## 指标含义（面试常考）

- **QPS**：每秒成功完成的请求数（吞吐），越高越好
- **响应时间**：发出请求到收到完整响应的耗时，关注 P95/P99（长尾），越低越好
- **错误率**：(异常 + 非预期状态码) / 总请求数，生产要求远低于 1%
- **拒绝率**：被限流拦截(429) 的请求占比，仅限流场景有意义
