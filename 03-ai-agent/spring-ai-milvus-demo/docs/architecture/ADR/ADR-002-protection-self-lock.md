# ADR-002：保护等级判定排除自身告警，避免自锁

> 状态：已接受（缺陷修复）
> 记录日期：2026-11-11（治理平台 V1.0 联调阶段）
>
> 对应代码路径：
> - `src/main/java/com/example/ai/token/cost/CostProtectionEngine.java`（`currentLevel()`、`resume()`）
> - `src/main/java/com/example/ai/token/cost/CostAlertService.java`（`countOpenByLevelExcluding()`、`resolveOpenOf()`）
> - `src/main/java/com/example/ai/token/cost/mapper/CostAlertMapper.java` + `resources/mapper/CostAlertMapper.xml`（`countOpenByLevelExcluding`、`listOpenByTypeAndTarget`）
> - 回归测试：`src/test/java/com/example/ai/token/cost/CostProtectionEngineTest.java`

## 背景

自动保护引擎的保护等级由三个输入取最大值决定：

1. 紧急开关是否打开；
2. 预算使用率命中了哪条 `BUDGET_USAGE_RATE` 规则；
3. 是否存在**未恢复的 CRITICAL 告警**。

第 3 条的本意是「有严重告警时宁可保守一点」。但第 1 条动作在打开紧急开关时，引擎自身会写一条审计性质的告警：

```java
alertService.alert(AlertType.PROTECTION_ACTION, AlertLevel.CRITICAL, "GLOBAL", "__ALL__", ...)
```

于是形成闭环：

```
紧急停用 → 写 PROTECTION_ACTION(CRITICAL, OPEN) → 等级判定看到未恢复 CRITICAL → 等级至少 SEVERE
管理员 resume → 开关关了，但那条告警还是 OPEN → 等级仍是 SEVERE
```

实测现象（修复前）：

```
停用后 level= BLOCKED
恢复后 level= SEVERE      ← 期望 NORMAL
```

后果不是「报错了」，而是**静默的功能降级**：SEVERE 下强制走低成本模型、Agent 一律暂停，管理员以为已经恢复，实际系统仍在自保状态，直到有人手工去预警中心把那条告警点成「已恢复」。

## 决策

两处同时改，缺一不可：

### 1. 等级判定排除自身类型

`currentLevel()` 不再用 `countOpenByLevel(CRITICAL)`，改用 `countOpenByLevelExcluding(CRITICAL, "PROTECTION_ACTION")`。

理由：`PROTECTION_ACTION` 是「引擎已经动手了」的**执行记录**，不是独立的**风险信号**。用它来抬升等级属于自我引用——尤其当它自己那条记录的等级就是从动作推导出来的时候。

其它类型的 CRITICAL 告警（如 `ANOMALY_AGENT_LOOP`、`SETTLE_OVERRUN`）仍然正常参与判定，保守策略不变。

### 2. 恢复时一并关闭该告警

`resume()` 在写完恢复日志后调用 `alertService.resolveOpenOf(PROTECTION_ACTION, "GLOBAL", "__ALL__")`。

理由：事件已经结束，预警中心不应该留一条永远等着人点的 CRITICAL。审计追溯由 `ai_cost_protection_log` 承担（保留 `EMERGENCY_STOP` / `RESUME` 两条带操作人与原因的记录），不依赖告警表。

`countOpenByLevelExcluding` 是防御性的第二道保险：即使某次 `resume` 因故没成功清理（进程崩溃、告警被 ACKNOWLEDGED 而非 RESOLVED），等级也不会被永久钉死。

## 备选方案与放弃原因

| 方案 | 放弃原因 |
|---|---|
| resume 时只清理告警，不改判定逻辑 | 清理失败或存在历史遗留 OPEN 记录时依旧自锁；没有兜底 |
| 只改判定逻辑，resume 不清理告警 | 预警中心会长期挂着一条无法自动恢复的 CRITICAL，误导运维 |
| 把 PROTECTION_ACTION 的等级从 CRITICAL 降到 HIGH | 掩盖问题：紧急停用确实是最高级别事件，降级会让它淹没在普通告警里；且未来别的高等级动作会再犯同样错 |
| 用独立字段（如 `source='ENGINE'`）过滤 | 需要改表结构，收益与「按 alert_type 排除」相同，成本更高 |

## 影响

- 保护等级在恢复后能真正回到 `NORMAL`（或按实际预算使用率应处的等级）。
- 预警中心不再残留无法自动恢复的 CRITICAL。
- 审计链完整：动作记录在 `ai_cost_protection_log`，告警可被关闭但不消失（`status=RESOLVED`，`resolved_at` 有值）。

## 验证

单元测试 `CostProtectionEngineTest` 4 个用例，其中一个专门回归本缺陷：

```
openProtectionActionAlertDoesNotKeepEngineAtSevere   // 存在自身 CRITICAL 未恢复 → 等级仍是 NORMAL
otherOpenCriticalAlertStillEscalatesToSevere         // 其它类型 CRITICAL → 仍升到 SEVERE（不能过度放宽）
emergencyStopBlocksThenResumeRestoresNormal          // 停用 BLOCKED → 恢复 NORMAL，并 verify resolveOpenOf 被调用
budgetThresholdRuleDrivesLevelAndAgentIsStoppedAtSevere
```

端到端实测（8091 实例）：

```
当前等级（历史 PROTECTION_ACTION 仍在库）  level= NORMAL  openCriticalAlerts= 1   ← 不参与判定，故仍 NORMAL
停用后                                    level= BLOCKED
恢复后                                    level= NORMAL
```
