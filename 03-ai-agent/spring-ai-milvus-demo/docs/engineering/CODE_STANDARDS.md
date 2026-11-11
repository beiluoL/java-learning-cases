# 项目统一代码规范（入口文档）

> 适用项目：`03-ai-agent/spring-ai-milvus-demo`（企业级 AI 成本治理平台）
> 版本：V1.0 · 2026-11-11
> **本文件是规范体系的唯一入口。任何人在本项目写代码前，先读完本文件，再按链接进入对应子规范。**

---

## 0. 这份文档解决什么问题

此前本项目**没有任何自动化质量门禁**，规范只存在于口头约定中，导致：

- 99 个类里 87 个没有日志、210 个实体字段只有 6 个有注释；
- 59 个文件堆在一个 `ai/token/` 目录下，8 个业务域混居；
- 一个方法长到 184 行，18 个构造依赖；
- 前端没有 Prettier、没有 Stylelint、没有一行测试。

结论很直接：**写在 Markdown 里的规范，如果没有工具强制执行，就等于没有。**
所以本规范体系的设计原则是「**能被门禁检查的才写进去，不能被检查的必须写清判断依据**」。

---

## 1. 规范体系地图

| 文档 | 管什么 | 什么时候读 |
|---|---|---|
| **CODE_STANDARDS.md**（本文） | 总入口、强制原则、决策表 | 任何时候，先读这个 |
| [DIRECTORY_STRUCTURE.md](./DIRECTORY_STRUCTURE.md) | 目录与包结构、每个目录的职责、新增代码放哪 | 新增文件前 |
| [BACKEND_CODING_STANDARDS.md](./BACKEND_CODING_STANDARDS.md) | Java / Spring Boot / 分层 / 命名 / 异常 / 日志 / 事务 | 写后端代码时 |
| [FRONTEND_CODING_STANDARDS.md](./FRONTEND_CODING_STANDARDS.md) | Vue3 / TS / 组件 / API / 状态 / CSS | 写前端代码时 |
| [DATABASE_CODING_STANDARDS.md](./DATABASE_CODING_STANDARDS.md) | 建表 / 字段 / 索引 / 迁移 / SQL | 写 SQL 或迁移时 |
| [SCRIPT_CODING_STANDARDS.md](./SCRIPT_CODING_STANDARDS.md) | Shell / bat / Docker Compose | 写脚本时 |
| [COMMENT_STANDARDS.md](./COMMENT_STANDARDS.md) | 类 / 字段 / 方法 / 复杂逻辑 / SQL / 脚本注释 | 写注释时 |
| [CODE_REVIEW_CHECKLIST.md](./CODE_REVIEW_CHECKLIST.md) | 人工审查清单（10 个维度） | 提交前 / 审查他人代码时 |
| [QUALITY_GATE.md](./QUALITY_GATE.md) | 自动化门禁说明与命令 | 交付前 |
| [AI_AGENT_DEVELOPMENT_RULES.md](./AI_AGENT_DEVELOPMENT_RULES.md) | 给 AI 智能体的硬约束与豁免清单 | **AI 智能体每次开工前必读** |
| [PROJECT_CODE_AUDIT.md](./PROJECT_CODE_AUDIT.md) | 本次治理的问题台账与依据 | 想了解「为什么这么规定」时 |

---

## 2. 五条不可协商的强制原则

这五条优先于任何其他考虑。冲突时以本节为准。

### 原则 1：不改变业务行为

规范治理、重构、目录调整，**一律不得改变对外行为**。
判定标准：`mvn -B -o -Pintegration verify` 的**全部测试全绿**（2026-10-26 基线为 97 项：73 单元 + 24 集成）+ 真实接口返回结构不变。
达不到这条，改动一律回滚。

### 原则 2：口径不可混用

本项目是成本系统，**口径混用等于数据造假**。三条红线：

| 口径 | 含义 | 允许进哪里 | **禁止** |
|---|---|---|---|
| `ACTUAL` | 供应商真实账单 | `apiCost` 合计 | 与分摊混算 |
| `ALLOCATED` | 基础设施分摊 | 单独表与单独指标 | **禁止**进 `apiCost` |
| `SAVED` | 节省（未发生） | 优化收益 | **禁止**进成本合计 |

同理，收益度量必须标注来源：`MEASURED`（真实实测）/ `COUNTERFACTUAL`（反推假设）/ `ESTIMATED`（规则估算）。
把 `ESTIMATED` 写成 `MEASURED` 属于**严重缺陷**，不是「小问题」。

### 原则 3：规范靠工具执行，不靠自觉

- 新增代码必须通过 `scripts/verify.sh`（见 QUALITY_GATE.md）。
- 门禁失败即不可交付。**不接受「这次先放过」**。
- 如果某条规范无法被工具检查，必须在对应子规范里写明「为什么保留人工判断」以及判断依据。

### 原则 4：删除能力前先问「为什么存在」

本项目有大量看似冗余的设计，每一个都有原因，**不要「顺手优化」**：

| 看似冗余 | 真实原因 | 破坏后果 |
|---|---|---|
| `settle()` 先写 Redis 再写 MySQL | ADR-003 明确选择「最终一致」，靠对账兜底 | 改成事务内写 Redis → Redis 已扣、DB 回滚 |
| `@MapperScan` 用 `@Mapper` 注解过滤 | 包移动后硬编码包名会静默失效 | 退回硬编码 → 启动报 `Invalid bound statement` |
| `AiChatService` 里 15 个编号阶段注释 | 对应真实治理链路顺序，顺序错则计费错 | 重排顺序 → 预扣与结算错位 |
| `CostProtectionEngine` 排除自身告警类型 | 修复「保护自锁」缺陷（ADR-002） | 去掉排除 → 恢复后永久卡 SEVERE |
| `BUDGET_THRESHOLD` 告警带判别位 | 50% 与 70% 同等级，不带判别位会被去重合并 | 去掉判别位 → 只报一条，漏掉 4 档 |

### 原则 5：遵守豁免清单

见 [AI_AGENT_DEVELOPMENT_RULES.md](./AI_AGENT_DEVELOPMENT_RULES.md) 第 3 节。
**豁免范围内的代码不是「待修的脏代码」，是明确定义的遗留上下文，禁止改动。**

---

## 3. 技术栈基线（不得随意升级）

| 组件 | 锁定版本 | 升级条件 |
|---|---|---|
| Java | **17** | 需同步验证 Lombok 注解处理，不得直接用系统默认 JDK |
| Spring Boot | 3.4.5 | 走 ADR |
| Spring AI | 1.0.0 GA | 走 ADR（1.x → 2.x 有破坏性变更） |
| MyBatis Starter | 3.0.4 | 走 ADR |
| MySQL | 8.0.36（容器） | 走 ADR |
| Vue | 3.5.13 | 走 ADR |
| TypeScript | 5.7.x | 走 ADR |
| Vite | 6.x | 走 ADR |
| Node | 支持 Vite 6 的 LTS | 跟随 Vite 要求 |

> **绝对禁止**：因为「有新版」而升级。升级必须有理由、有验证、有回滚方案，并记录 ADR。

### ⚠️ JDK 必须显式指定 17

本机默认 JDK 可能是 23+。**JDK 23 起 javac 默认关闭隐式注解处理**，会让 Lombok **静默失效**，报出数百处「找不到符号 `getXxx()`」——错误信息完全不提 Lombok，极易误判为业务代码问题。

```bash
export JAVA_HOME=/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home
java -version   # 必须显示 17.x
```

`scripts/verify.sh` 已内置版本校验，不满足直接失败退出。

---

## 4. 快速决策表

遇到判断不清的情况，查这张表。

| 场景 | 决策 |
|---|---|
| 新功能放哪个包？ | 按**业务域**建顶层包，不按「控制器/服务/工具」建全局目录 → DIRECTORY_STRUCTURE |
| Service 要不要写 interface？ | **不要**。单一实现不建接口。只有真需要多实现（如策略可切换）才建 → BACKEND §4.2 |
| Controller 能返回 Entity 吗？ | **不能**。必须返回 VO/record → BACKEND §3.1 |
| DTO 能用 Map 接收吗？ | **不能**。必须显式类型 + Bean Validation → BACKEND §3.2 |
| 金额用什么类型？ | `BigDecimal` + `DECIMAL(20,8)` + `MoneyScale` 归一。**禁止 double** → BACKEND §8 |
| 时间怎么存？ | 全链路 UTC。DB 用 `DATETIME(3)`，Java 用 `LocalDateTime` + `Clock` → DATABASE §4 |
| 需要写日志吗？ | 跨模块决策点、失败降级、外部调用结果**必须**记，且带 `requestId` → BACKEND §7 |
| 加一个迁移还是改旧的？ | **只能新增** `V{n+1}__xxx.sql`，已执行的 V1~V5 禁止修改 → DATABASE §6 |
| 前端能直接用 axios 吗？ | **不能**。走 `api/*.ts` → `request.ts` → FRONTEND §5 |
| 前端能用 any 吗？ | **不能**。用 interface / 联合类型 / 泛型 → FRONTEND §6 |
| 脚本能用 `kill -9` 吗？ | **不能**。PID 文件 + `kill -TERM` → SCRIPT §2 |
| 脚本能用 `pkill -f` 吗？ | **不能**。会误杀同机其他项目 → SCRIPT §2 |
| 能改 `milvusdemo` 包吗？ | **不能**。属豁免遗留上下文 → AI_AGENT_DEVELOPMENT_RULES §3 |
| 能删 RAG / Milvus 代码吗？ | **不能**。任何情况下都保留 → 同上 |
| 想「顺手重构」看不过去的代码？ | **停**。先提 issue 记入审计台账，不要混在功能改动里 |

---

## 5. 变更类型与门禁强度

不是所有改动都需要全量门禁。按变更范围选择：

| 变更类型 | 示例 | 必需门禁 |
|---|---|---|
| **纯注释** | 补 Javadoc、补字段注释 | `mvn -q -o compile` |
| **纯文档** | 改 `docs/**` | 无（但需自查交叉引用一致性） |
| **前端样式** | 改 `.vue` 的 `<style>` | `npm run lint` + `npm run build` |
| **前端逻辑** | 改 composable / api / 组件逻辑 | `npm run lint` + `npm run type-check` + `npm run build` |
| **后端单类** | 改一个 Service 内部实现 | `mvn -B -o -Pintegration verify` |
| **后端跨模块** | 改包结构、改公共类型、改 Mapper | `mvn -B -o -Pintegration verify` + **启动并调一次真实接口** |
| **数据库** | 新增迁移 | 上述全部 + Flyway 从空库重建验证 |
| **脚本** | 改 `*.command` / `*.bat` | ShellCheck 0 告警 + 实跑一次 |

> **跨模块改动的额外要求**：包结构变更后，`mvn compile` 通过**不代表**正确。MyBatis 的 mapper 绑定是运行期检查的，必须启动应用并真实调用一次接口，否则会漏掉 `Invalid bound statement`。

---

## 6. 交付时必须提供什么

每次交付（尤其是 AI 智能体交付）必须包含：

1. **本批目标**：一句话说清这批要解决什么。
2. **实际修改文件清单**：路径 + 改了什么。
3. **修改原因**：为什么这么改（对应对哪条规范/哪个审计问题）。
4. **规范应用情况**：本批落实了哪些规范条目。
5. **执行的命令与真实输出**：不是「应该通过」，是**实际输出**。
6. **遗留问题**：如实列出没做的、有风险的。
7. **下一批任务**。

**禁止**：用「已完成」「已优化」这类无证据的措辞替代真实输出。

---

## 7. 违规处理

| 违规类型 | 处理 |
|---|---|
| 门禁失败仍提交 | 回滚 |
| 改动业务行为未说明 | 回滚 |
| 混用成本口径 | 回滚 + 补 ADR 说明口径边界 |
| 修改豁免清单内代码 | 回滚 |
| 修改已执行的 Flyway 迁移 | 回滚 + 新增修正迁移 |
| 引入硬编码密钥 / Key | 立即移除 + 轮换密钥 |
| 用假数据 / 空实现冒充功能 | 回滚 + 记入审计台账 |
