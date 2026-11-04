#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
redis-fullstack-lab 压测脚本（零第三方依赖，仅用 Python 标准库）

用法示例：
  # 对「限流」场景做 200 并发、共 2000 次请求
  python3 run_benchmark.py --scenario ratelimit --concurrency 200 --requests 2000

  # 对「分布式锁扣减」做突发压测，并限制到 100 QPS 模拟平稳流量
  python3 run_benchmark.py --scenario lock --concurrency 50 --requests 1500 --rps 100

  # 一次性压测全部核心场景，每个场景出一张独立报告 + 一份汇总对比报告
  python3 run_benchmark.py --scenario all

  # 指定后端地址（默认 http://localhost:8080，也可指向前端代理 5173）
  python3 run_benchmark.py --scenario cache --host http://localhost:8080

特点：
  - 仅用标准库（urllib + concurrent.futures），拷走即用，无需 pip install。
  - 模拟真实流量：支持并发突发(--concurrency) 与平稳流量(--rps 令牌桶限速)。
  - 采集指标：QPS、平均/p50/p90/p95/p99 响应时间、错误率、拒绝率(限流场景)。
  - 自动生成「自包含 HTML 监控报告」(内联 SVG 图表，离线可看，不依赖任何 CDN)。

指标含义（面试常考）：
  QPS   = 每秒成功完成的请求数（吞吐）
  响应时间 = 从发出请求到收到完整响应的耗时（含网络与排队），越低越好
  错误率 = (异常 + 非预期状态码) / 总请求数，生产要求远低于 1%
  拒绝率 = 被限流拦截(429)的请求占比，仅限流场景有意义
"""

import argparse
import concurrent.futures as cf
import json
import queue
import random
import string
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime

# ----------------------------------------------------------------------------
# 场景定义：每个场景映射到后端的一个真实接口
# accept      : 视为「成功」的状态码集合
# expect_reject: 视为「被限流拒绝」的状态码（非系统错误，单独统计）
# note        : 报告里的业务说明
# ----------------------------------------------------------------------------
SCENARIOS = {
    "ratelimit": {
        "method": "GET",
        "path": "/api/ratelimit/order",
        "accept": {200},
        "expect_reject": {429},
        "note": "滑动窗口限流接口。大量并发打到同一 key 时，超出阈值的请求会返回 429，"
                "用于演示「限流把流量削峰」的效果——报告里能看到明显的 QPS 封顶与拒绝率。",
    },
    "lock": {
        "method": "POST",
        "path": "/api/lock/deduct?productId=1&impl=redis",
        "accept": {200},
        "expect_reject": set(),
        "note": "分布式锁扣减库存。高并发下多个线程竞争同一把锁，会出现锁等待，"
                "响应时间会随并发升高而抬升；最终库存不会超卖（可用 /api/lock/reset 复位）。",
        "after": {"method": "POST", "path": "/api/lock/reset?productId=1&count=100"},
    },
    "cache": {
        "method": "GET",
        "path": "/api/cache/product/1",
        "accept": {200},
        "expect_reject": set(),
        "note": "缓存读取（Cache-Aside 命中）。纯内存读取，期望低延迟、高 QPS，"
                "是缓存加速场景的典型读路径。",
    },
    "rank": {
        "method": "POST",
        "path": "/api/rank/add?member={member}&score={score}",
        "accept": {200},
        "expect_reject": set(),
        "note": "排行榜写入（ZSET ZADD）。每个请求写入一个随机成员与分数，"
                "用于观察写密集场景下的吞吐与延迟。",
        "dynamic": True,
    },
    "penetration": {
        "method": "GET",
        "path": "/api/penetration/product/{id}",
        "accept": {200},
        "expect_reject": set(),
        "note": "缓存穿透场景。大量请求打不存在的 id（>100），会被布隆过滤器直接拦截，"
                "几乎不落 DB；少数命中区间的请求才回源，整体延迟低且平稳。",
        "dynamic": "penetration",
    },
    "avalanche": {
        "method": "GET",
        "path": "/api/avalanche/product/1?protect=true",
        "accept": {200},
        "expect_reject": set(),
        "note": "缓存雪崩场景（开启 Caffeine L1 + Redis L2 + 熔断保护）。多级缓存扛住瞬时洪峰，"
                "即使 Redis 抖动也能降级，延迟稳定。",
    },
}


def build_url(host, scenario_name, seq):
    """根据场景构造请求 URL（处理动态参数）"""
    cfg = SCENARIOS[scenario_name]
    path = cfg["path"]
    if cfg.get("dynamic") is True:
        member = "worker-" + "".join(random.choices(string.digits, k=6))
        score = round(random.uniform(1, 1000), 2)
        path = path.format(member=member, score=score)
    elif cfg.get("dynamic") == "penetration":
        # 90% 命中不存在的 id（被布隆过滤器挡掉），10% 命中真实区间
        rid = random.randint(1000, 9999) if random.random() < 0.9 else random.randint(1, 100)
        path = path.format(id=rid)
    return host.rstrip("/") + path


def do_request(method, url, timeout):
    """执行一次 HTTP 请求，返回 (ok, rejected, latency_ms, status, err)"""
    t0 = time.perf_counter()
    try:
        req = urllib.request.Request(url, method=method, data=None)
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            _ = resp.read()
            status = resp.getcode()
    except urllib.error.HTTPError as e:
        status = e.code
    except Exception as e:  # 连接失败 / 超时 / 读超时 等
        return False, False, (time.perf_counter() - t0) * 1000.0, 0, str(e)
    latency = (time.perf_counter() - t0) * 1000.0
    cfg_accept = SCENARIOS_current_accept
    cfg_reject = SCENARIOS_current_reject
    if status in cfg_reject:
        return True, True, latency, status, ""
    if status in cfg_accept:
        return True, False, latency, status, ""
    return False, False, latency, status, "status=%s" % status


# 这两个全局变量由 run_scenario 在每次压测前设置，避免在每个请求里查表
SCENARIOS_current_accept = {200}
SCENARIOS_current_reject = set()


def worker(task_q, result_q, method, host, scenario_name, bucket_ms, rps_bucket, stop):
    """工作线程：从任务队列取任务，执行请求，把结果放进结果队列。
    rps_bucket 为可选令牌桶，用于限速（模拟平稳流量）。"""
    while not stop.is_set():
        try:
            seq = task_q.get_nowait()
        except queue.Empty:
            break
        if seq is None:
            break
        if rps_bucket is not None:
            rps_bucket.acquire()
        url = build_url(host, scenario_name, seq)
        ok, rejected, latency, status, err = do_request(method, url, timeout=10)
        t_rel = (time.perf_counter() - START_TS) * 1000.0
        result_q.put((t_rel, latency, ok, rejected, status, err))


class TokenBucket:
    """简单令牌桶，用于把压测流量限制在指定 QPS（模拟平稳真实流量）"""

    def __init__(self, rate):
        self.rate = float(rate)
        self.lock = threading.Lock()
        self.tokens = rate
        self.last = time.perf_counter()

    def acquire(self):
        with self.lock:
            now = time.perf_counter()
            elapsed = now - self.last
            self.tokens = min(self.rate, self.tokens + elapsed * self.rate)
            self.last = now
            if self.tokens >= 1:
                self.tokens -= 1
                return
        # 令牌不足：等一个周期再试
        time.sleep(1.0 / self.rate)
        self.acquire()


def run_scenario(name, host, concurrency, requests, rps):
    global SCENARIOS_current_accept, SCENARIOS_current_reject, START_TS
    cfg = SCENARIOS[name]
    SCENARIOS_current_accept = cfg["accept"]
    SCENARIOS_current_reject = cfg["expect_reject"]
    method = cfg["method"]

    task_q = queue.Queue()
    for i in range(requests):
        task_q.put(i)
    result_q = queue.Queue()
    stop = threading.Event()
    rps_bucket = TokenBucket(rps) if rps else None

    START_TS = time.perf_counter()
    t_start = START_TS
    with cf.ThreadPoolExecutor(max_workers=concurrency) as ex:
        futures = [ex.submit(worker, task_q, result_q, method, host, name, 1000, rps_bucket, stop)
                   for _ in range(concurrency)]
        # 主线程负责把结果从队列取出，避免队列无限增长
        collected = []
        while len(collected) < requests:
            try:
                collected.append(result_q.get(timeout=30))
            except queue.Empty:
                break
        stop.set()
        cf.wait(futures)
    t_end = time.perf_counter()

    # 跑完若定义了 after（如锁场景复位库存），顺手复位
    if "after" in cfg:
        a = cfg["after"]
        try:
            req = urllib.request.Request(host.rstrip("/") + a["path"], method=a["method"])
            urllib.request.urlopen(req, timeout=10).read()
        except Exception:
            pass

    return {
        "name": name,
        "method": method,
        "path": cfg["path"],
        "note": cfg["note"],
        "concurrency": concurrency,
        "requests": requests,
        "rps_limit": rps,
        "duration_s": round(t_end - t_start, 3),
        "results": collected,
    }


# ----------------------------------------------------------------------------
# 统计与分桶
# ----------------------------------------------------------------------------
def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0.0
    k = (len(sorted_vals) - 1) * p / 100.0
    import math
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return sorted_vals[int(k)]
    return sorted_vals[f] * (c - k) + sorted_vals[c] * (k - f)


def summarize(raw):
    results = raw["results"]
    latencies = [r[1] for r in results]
    total = len(results)
    ok = sum(1 for r in results if r[2])
    rejected = sum(1 for r in results if r[3])
    errors = total - ok
    err_rate = (errors / total * 100.0) if total else 0.0
    rej_rate = (rejected / total * 100.0) if total else 0.0
    dur = raw["duration_s"]
    overall_qps = total / dur if dur else 0.0
    lat_sorted = sorted(latencies)
    stats = {
        "total": total,
        "ok": ok,
        "rejected": rejected,
        "errors": errors,
        "err_rate": round(err_rate, 2),
        "rej_rate": round(rej_rate, 2),
        "duration_s": dur,
        "overall_qps": round(overall_qps, 1),
        "lat_min": round(min(latencies), 2) if latencies else 0,
        "lat_mean": round(sum(latencies) / total, 2) if total else 0,
        "lat_p50": round(percentile(lat_sorted, 50), 2),
        "lat_p90": round(percentile(lat_sorted, 90), 2),
        "lat_p95": round(percentile(lat_sorted, 95), 2),
        "lat_p99": round(percentile(lat_sorted, 99), 2),
        "lat_max": round(max(latencies), 2) if latencies else 0,
    }

    # 时间分桶（1 秒一桶），用于画时间序列
    buckets = {}
    for t_rel, lat, r_ok, r_rej, status, err in results:
        b = int(t_rel // 1000)
        buckets.setdefault(b, []).append((lat, r_ok, r_rej))
    bucket_keys = sorted(buckets.keys())
    series = {"t": [], "qps": [], "p50": [], "p95": [], "p99": [], "err": [], "rej": []}
    for b in bucket_keys:
        items = buckets[b]
        lats = sorted(x[0] for x in items)
        n = len(items)
        ok_n = sum(1 for x in items if x[1])
        rej_n = sum(1 for x in items if x[2])
        err_n = n - ok_n
        series["t"].append(b)
        series["qps"].append(round(n, 1))  # 1s 桶，桶内计数即 QPS
        series["p50"].append(round(percentile(lats, 50), 1))
        series["p95"].append(round(percentile(lats, 95), 1))
        series["p99"].append(round(percentile(lats, 99), 1))
        series["err"].append(round(err_n / n * 100.0, 2) if n else 0)
        series["rej"].append(round(rej_n / n * 100.0, 2) if n else 0)

    # 延迟直方图（10 个区间）
    if latencies:
        mx = max(latencies)
        step = max(1.0, mx / 10.0)
        hist = [0] * 10
        for l in latencies:
            idx = min(9, int(l // step))
            hist[idx] += 1
        hist_labels = ["%.0f-%.0f" % (i * step, (i + 1) * step) for i in range(10)]
    else:
        hist, hist_labels = [0] * 10, []

    return stats, series, hist, hist_labels


# ----------------------------------------------------------------------------
# 纯 SVG 图表（零依赖、离线可看）
# ----------------------------------------------------------------------------
def _esc(s):
    return str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def svg_line(title, series_list, x_labels, y_label="", height=300, y_max=None):
    """series_list: [{"name","color","data":[...]}]，x 轴用 x_labels（或索引）"""
    W, H = 900, height
    pad_l, pad_r, pad_t, pad_b = 56, 20, 30, 40
    plot_w, plot_h = W - pad_l - pad_r, H - pad_t - pad_b
    n = len(x_labels) if x_labels else (len(series_list[0]["data"]) if series_list else 0)
    if n == 0:
        return "<p>无数据</p>"
    # y 轴最大值
    all_vals = [v for s in series_list for v in s["data"] if v is not None]
    if y_max is None:
        y_max = max(all_vals) if all_vals else 1
    y_max = max(y_max, 1e-6)
    # 网格线（5 条）
    grid = ""
    for i in range(6):
        yv = y_max * (5 - i) / 5.0
        yy = pad_t + plot_h * i / 5.0
        grid += ('<line x1="%d" y1="%.1f" x2="%d" y2="%.1f" stroke="#eee"/>'
                 % (pad_l, yy, W - pad_r, yy))
        grid += ('<text x="%d" y="%.1f" fill="#888" font-size="11" text-anchor="end">%.1f</text>'
                 % (pad_l - 6, yy + 4, yv))

    def xpos(i):
        return pad_l + (plot_w * i / max(1, n - 1)) if n > 1 else pad_l + plot_w / 2

    def ypos(v):
        return pad_t + plot_h * (1 - min(v, y_max) / y_max)

    polylines = ""
    for s in series_list:
        pts = " ".join("%.1f,%.1f" % (xpos(i), ypos(s["data"][i])) for i in range(n))
        polylines += ('<polyline fill="none" stroke="%s" stroke-width="2" points="%s"/>'
                      % (s["color"], pts))
    # x 轴标签（最多 8 个）
    xticks = ""
    step_label = max(1, n // 8)
    for i in range(0, n, step_label):
        lbl = x_labels[i] if x_labels and i < len(x_labels) else str(i)
        xticks += ('<text x="%.1f" y="%d" fill="#888" font-size="11" text-anchor="middle">%s</text>'
                   % (xpos(i), H - pad_b + 18, _esc(lbl)))
    legend = ""
    lx = pad_l + 10
    for s in series_list:
        legend += ('<rect x="%d" y="%d" width="12" height="12" fill="%s"/>'
                   % (lx, 8, s["color"]))
        legend += ('<text x="%d" y="%d" fill="#333" font-size="12">%s</text>'
                   % (lx + 16, 18, _esc(s["name"])))
        lx += 24 + len(_esc(s["name"])) * 8

    return ('<svg viewBox="0 0 %d %d" width="100%%" xmlns="http://www.w3.org/2000/svg">'
            '<text x="%d" y="%d" fill="#222" font-size="14" font-weight="bold">%s</text>'
            '%s%s%s%s</svg>'
            % (W, H, pad_l, pad_t - 10, _esc(title), grid, polylines, xticks, legend))


def svg_bar(title, labels, values, color="#5b8ff9", height=260):
    W, H = 900, height
    pad_l, pad_r, pad_t, pad_b = 56, 20, 30, 50
    plot_w, plot_h = W - pad_l - pad_r, H - pad_t - pad_b
    n = len(values)
    if n == 0:
        return "<p>无数据</p>"
    y_max = max(values) if values else 1
    y_max = max(y_max, 1)
    grid = ""
    for i in range(6):
        yv = y_max * (5 - i) / 5.0
        yy = pad_t + plot_h * i / 5.0
        grid += '<line x1="%d" y1="%.1f" x2="%d" y2="%.1f" stroke="#eee"/>' % (pad_l, yy, W - pad_r, yy)
        grid += '<text x="%d" y="%.1f" fill="#888" font-size="11" text-anchor="end">%.0f</text>' % (pad_l - 6, yy + 4, yv)
    bw = plot_w / n
    bars = ""
    for i, v in enumerate(values):
        x = pad_l + i * bw + bw * 0.15
        h = plot_h * (v / y_max)
        y = pad_t + plot_h - h
        bars += '<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="%s"/>' % (x, y, bw * 0.7, h, color)
        bars += '<text x="%.1f" y="%.1f" fill="#888" font-size="9" text-anchor="middle">%d</text>' % (x + bw * 0.35, pad_t + plot_h + 14, v)
        bars += '<text x="%.1f" y="%.1f" fill="#555" font-size="10" text-anchor="middle">%s</text>' % (x + bw * 0.35, y - 4, v)
    # x 标签（区间）
    xt = ""
    for i, lbl in enumerate(labels):
        xt += '<text x="%.1f" y="%d" fill="#666" font-size="9" text-anchor="middle">%s</text>' % (pad_l + i * bw + bw / 2, H - 6, _esc(lbl))
    return ('<svg viewBox="0 0 %d %d" width="100%%" xmlns="http://www.w3.org/2000/svg">'
            '<text x="%d" y="%d" fill="#222" font-size="14" font-weight="bold">%s</text>%s%s%s</svg>'
            % (W, H, pad_l, pad_t - 10, _esc(title), grid, bars, xt))


# ----------------------------------------------------------------------------
# 生成自包含 HTML 报告
# ----------------------------------------------------------------------------
def build_html(raw, stats, series, hist, hist_labels, host):
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    name = raw["name"]
    c1 = svg_line("%s · QPS 随时间" % name, [{"name": "QPS", "color": "#5b8ff9", "data": series["qps"]}],
                  series["t"], y_label="QPS")
    c2 = svg_line("%s · 响应时间(p50/p95/p99) ms" % name,
                  [{"name": "p50", "color": "#5ad8a6", "data": series["p50"]},
                   {"name": "p95", "color": "#f6bd16", "data": series["p95"]},
                   {"name": "p99", "color": "#e8684a", "data": series["p99"]}],
                  series["t"], y_label="ms")
    c3 = svg_line("%s · 错误率/拒绝率 %%" % name,
                  [{"name": "错误率", "color": "#e8684a", "data": series["err"]},
                   {"name": "拒绝率", "color": "#9270ca", "data": series["rej"]}],
                  series["t"], y_label="%")
    c4 = svg_bar("%s · 响应时间分布(ms 区间)" % name, hist_labels, hist, color="#5b8ff9")

    cards = (
        card("总请求", stats["total"]) + card("成功率", "%.1f%%" % (100 - stats["err_rate"])) +
        card("错误率", "%.2f%%" % stats["err_rate"]) + card("拒绝率", "%.2f%%" % stats["rej_rate"]) +
        card("总耗时(s)", stats["duration_s"]) + card("整体QPS", stats["overall_qps"]) +
        card("均值RT(ms)", stats["lat_mean"]) + card("P95(ms)", stats["lat_p95"]) +
        card("P99(ms)", stats["lat_p99"]) + card("最大RT(ms)", stats["lat_max"])
    )

    lat_rows = (
        "<tr><td>最小</td><td>%s</td><td>P50</td><td>%s</td></tr>"
        "<tr><td>均值</td><td>%s</td><td>P90</td><td>%s</td></tr>"
        "<tr><td>最大</td><td>%s</td><td>P95</td><td>%s</td></tr>"
        "<tr><td>错误数</td><td>%s</td><td>P99</td><td>%s</td></tr>"
        % (stats["lat_min"], stats["lat_p50"], stats["lat_mean"], stats["lat_p90"],
           stats["lat_max"], stats["errors"], stats["lat_p95"], stats["lat_p99"])
    )

    return """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>压测报告 · {name}</title>
<style>
 body{{font-family:-apple-system,Segoe UI,Roboto,'PingFang SC','Microsoft YaHei',sans-serif;margin:0;background:#f5f7fa;color:#333}}
 .wrap{{max-width:980px;margin:0 auto;padding:24px}}
 h1{{font-size:22px;margin:0 0 4px}} .sub{{color:#888;font-size:13px;margin-bottom:18px}}
 .cards{{display:flex;flex-wrap:wrap;gap:12px;margin-bottom:20px}}
 .card{{background:#fff;border-radius:10px;padding:12px 16px;min-width:120px;box-shadow:0 1px 3px rgba(0,0,0,.06)}}
 .card .k{{color:#888;font-size:12px}} .card .v{{font-size:22px;font-weight:700;margin-top:4px}}
 .panel{{background:#fff;border-radius:10px;padding:16px;margin-bottom:16px;box-shadow:0 1px 3px rgba(0,0,0,.06)}}
 .panel h2{{font-size:15px;margin:0 0 10px}}
 table{{border-collapse:collapse;width:100%;font-size:13px}}
 td{{border:1px solid #eee;padding:6px 10px}} tr:nth-child(odd){{background:#fafafa}}
 .note{{background:#eef4ff;border-left:4px solid #5b8ff9;padding:10px 14px;border-radius:6px;font-size:13px;color:#335;margin-bottom:16px}}
 code{{background:#f0f0f0;padding:1px 5px;border-radius:4px}}
</style></head>
<body><div class="wrap">
<h1>压测报告 · {name}</h1>
<div class="sub">生成时间：{now} ｜ 目标：{host} ｜ 接口：<code>{method} {path}</code> ｜ 并发：{conc} ｜ 请求数：{req}{rps}</div>
<div class="note">{note}</div>
<div class="cards">{cards}</div>
<div class="panel"><h2>核心延迟指标 (ms)</h2><table>{latrows}</table></div>
<div class="panel"><h2>吞吐随时间变化</h2>{c1}</div>
<div class="panel"><h2>响应时间分位随时间变化</h2>{c2}</div>
<div class="panel"><h2>错误率 / 拒绝率随时间变化</h2>{c3}</div>
<div class="panel"><h2>响应时间分布</h2>{c4}</div>
</div></body></html>""".format(
        name=_esc(name), now=now, host=_esc(host), method=raw["method"], path=_esc(raw["path"]),
        conc=raw["concurrency"], req=raw["requests"],
        rps=(" ｜ 限速：%d QPS" % raw["rps_limit"]) if raw["rps_limit"] else "",
        note=_esc(raw["note"]), cards=cards, latrows=lat_rows, c1=c1, c2=c2, c3=c3, c4=c4)


def card(k, v):
    return '<div class="card"><div class="k">%s</div><div class="v">%s</div></div>' % (_esc(k), _esc(v))


def save_report(name, html):
    fname = "report/%s_%s.html" % (name, datetime.now().strftime("%Y%m%d_%H%M%S"))
    with open(fname, "w", encoding="utf-8") as f:
        f.write(html)
    return fname


def main(argv=None):
    p = argparse.ArgumentParser(description="Redis 全栈实战项目 · 可复用压测脚本")
    p.add_argument("--scenario", default="cache",
                   choices=list(SCENARIOS.keys()) + ["all"],
                   help="压测场景：ratelimit/lock/cache/rank/penetration/avalanche/all")
    p.add_argument("--host", default="http://localhost:8080", help="后端地址，默认 http://localhost:8080")
    p.add_argument("--concurrency", type=int, default=50, help="并发线程数（突发流量）")
    p.add_argument("--requests", type=int, default=1000, help="总请求数")
    p.add_argument("--rps", type=int, default=0, help="限速 QPS（0=不限速，模拟平稳流量）")
    args = p.parse_args(argv)

    scenarios = list(SCENARIOS.keys()) if args.scenario == "all" else [args.scenario]
    summary_rows = []
    for name in scenarios:
        print(">> 压测场景 [%s] 并发=%d 请求=%d%s ..." % (
            name, args.concurrency, args.requests, (" rps=%d" % args.rps) if args.rps else ""))
        raw = run_scenario(name, args.host, args.concurrency, args.requests, args.rps or None)
        stats, series, hist, hist_labels = summarize(raw)
        html = build_html(raw, stats, series, hist, hist_labels, args.host)
        fname = save_report(name, html)
        print("   完成：整体QPS=%.1f 错误率=%.2f%% P95=%.1fms P99=%.1fms -> %s"
              % (stats["overall_qps"], stats["err_rate"], stats["lat_p95"], stats["lat_p99"], fname))
        summary_rows.append((name, stats, fname))

    if args.scenario == "all" and len(summary_rows) > 1:
        # 汇总对比报告：各场景整体 QPS / 错误率 / P95
        rows = "".join(
            "<tr><td>%s</td><td>%s</td><td>%.1f</td><td>%.2f%%</td><td>%.1f</td><td>%.1f</td><td><a href='%s'>报告</a></td></tr>"
            % (_esc(n), s["total"], s["overall_qps"], s["err_rate"], s["lat_p95"], s["lat_p99"], _esc(f))
            for n, s, f in summary_rows)
        comp = """<div class="panel"><h2>多场景汇总对比</h2>
        <table><tr><th>场景</th><th>请求数</th><th>整体QPS</th><th>错误率</th><th>P95(ms)</th><th>P99(ms)</th><th>详情</th></tr>%s</table></div>""" % rows
        # 简单对比图（各场景 QPS 柱子）
        names = [n for n, _, _ in summary_rows]
        qpss = [s["overall_qps"] for _, s, _ in summary_rows]
        cmp_chart = svg_bar("各场景整体 QPS 对比", names, qpss, color="#5b8ff9")
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        html = """<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<title>压测汇总</title><style>body{font-family:-apple-system,Segoe UI,Roboto,'PingFang SC',sans-serif;background:#f5f7fa}
.wrap{max-width:980px;margin:0 auto;padding:24px} .panel{background:#fff;border-radius:10px;padding:16px;margin-bottom:16px;box-shadow:0 1px 3px rgba(0,0,0,.06)}
h1{font-size:22px} table{border-collapse:collapse;width:100%%;font-size:13px} td,th{border:1px solid #eee;padding:6px 10px;text-align:left} a{color:#5b8ff9}</style></head>
<body><div class="wrap"><h1>压测汇总对比</h1><div class="sub">生成时间：%s</div>%s<div class="panel">%s</div></div></body></html>""" % (now, comp, cmp_chart)
        fname = save_report("summary", html)
        print(">> 汇总对比报告：%s" % fname)

    print("全部完成。")


if __name__ == "__main__":
    main()
