# 质量门禁

> 文档编号：ENG-QG-001  
> 版本：1.0  
> 生效日期：2026-10-26  
> 入口：**`scripts/verify.sh`（唯一）**  
> 强制力：**`verify.sh` 未通过 = 代码不得合并**  
> 上位文档：[`CODE_STANDARDS.md`](./CODE_STANDARDS.md)

---

## 0. 为什么需要门禁

本项目的审计结论（`PROJECT_CODE_AUDIT.md`）第一条根因是：

> **零自动化质量门禁** —— 没有任何 Checkstyle / Prettier / Stylelint / ShellCheck 配置，
> 规范完全依赖人（和 AI）自觉。

写成 Markdown 的规范会被遗忘、被跳过、被"这次先例外"。**只有会失败的检查才会被执行。**

门禁的设计目标不是"证明代码对"，而是**"证明代码错在哪、错在第几行"**。
因此每条检查都必须：可复现、有明确退出码、失败时打印文件与行号。

---

## 1. 一条命令

```bash
./scripts/verify.sh            # 完整门禁（提交前 / CI 必须跑这个）
./scripts/verify.sh --fast     # 快速模式：跳过测试与构建，本地改代码时用
./scripts/verify.sh --backend  # 只跑后端
./scripts/verify.sh --frontend # 只跑前端
./scripts/verify.sh --scripts  # 只跑脚本检查
```

**退出码**

| 码 | 含义 |
|---:|---|
| 0 | 全部通过 |
| 1 | 存在失败项 |
| 2 | 参数错误 |

**实测结果（2026-10-26）**

```
==============================================================
 门禁汇总
==============================================================
  [ OK ] 脚本检查
  [ OK ] 后端检查
  [ OK ] 前端检查

  结论：全部通过 ✅
==============================================================
```

总耗时约 40 秒（后端编译+97 项测试约 15s，前端 6 项检查约 25s）。

---

## 2. 门禁全景

| 域 | 检查项 | 工具 | 失败含义 |
|---|---|---|---|
| **脚本** | 语法 | `bash -n` | 脚本跑不起来 |
| | shebang | `#!/usr/bin/env bash` | 换环境不可用 |
| | 严格模式 | `set -Eeuo pipefail` | 失败被吞、路径为空导致误删 |
| | 安全红线 | `kill -9` / `pkill` / `rm -rf` / 硬编码路径 / 明文密钥 | 会误杀进程、误删数据、泄漏密钥 |
| | bash 3.2 兼容 | perl 多字节检测 | macOS 双击时 `unbound variable` |
| | Windows | `chcp 65001` / 不用 `taskkill /IM` | 中文乱码、误杀进程 |
| | 可执行位 | `-rwxr-xr-x` | 双击无法运行 |
| **后端** | 编译 | `mvn test-compile` | 编译不过 |
| | 单元 + 集成测试 | `mvn -Pintegration verify` | 行为回归 |
| | Checkstyle | `mvn checkstyle:check` | 命名/空 catch/方法长度等 19 条规则（依赖不可下载时降级 WARN，见 §8） |
| | 禁止字段注入 | grep `@Autowired` | 不可测、循环依赖 |
| | 禁止调试输出 | grep `System.out` / `printStackTrace` | 日志缺失 |
| | 禁止占位符 | grep `TODO` / `FIXME` | 半成品混入 |
| | 时间基准 | grep 裸 `LocalDate(Time).now()` | 查询窗口偏移时区 |
| | 分层 | grep controller→mapper | 绕过 Service，契约双真相源 |
| | SQL | grep `SELECT *` | 加列后结果集静默变化 |
| | SQL 插值 | grep `${}`（锁定 1 处 ORDER BY 白名单） | 注入面 |
| **前端** | 格式 | Prettier `--check` | 格式不统一 |
| | ESLint | `--max-warnings=0` | 代码缺陷 / 未用变量 / `any` |
| | Stylelint | 标准规则 | CSS 结构问题 |
| | 类型 | `vue-tsc --noEmit` | 类型不匹配 |
| | 单测 | Vitest（不允许零测试） | 逻辑回归 |
| | 构建 | `vite build` | 打包失败 |

---

## 3. 目录与职责

```
scripts/
├── verify.sh            ← 唯一入口，编排三域，汇总结论
├── check-scripts.sh     ← 33 项脚本检查
├── check-backend.sh     ← 7 条静态规则 + 编译 + 97 项测试
└── check-frontend.sh    ← 6 道前端检查
```

| 规则 | 内容 |
|---|---|
| 入口唯一 | 任何人和任何智能体**只允许**通过 `verify.sh` 判定质量 |
| 子脚本可单独跑 | 排障时可以用 `--backend` 等参数缩小范围 |
| 不改源码 | 全部检查只读源码（除 `target/` `dist/` 产物） |
| 无硬编码路径 | 用 `$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)` 定位仓库根 |

---

## 4. 关键设计决策（都有踩坑依据）

### 4.1 失败不中断：跑完全部再汇总

```bash
# ❌ 第一个失败就退出，后面 5 个失败看不到
set -e

# ✅ 逐个执行，收集退出码，最后汇总
"$script" "$@"
code=$?
RESULT_CODES+=("$code")
```

**依据**：一次只暴露一个错误，意味着要跑 6 遍才能修完 6 个问题。

### 4.2 不用管道包裹被测命令

```bash
# ❌ 退出码是 tail 的 0，mvn 失败被吞掉（本项目真实踩过）
mvn -B verify | tail -30

# ✅ 重定向到临时文件，单独判断退出码，再按需打印
mvn -B verify > "$LOG" 2>&1
code=$?
grep -E "Tests run:|ERROR" "$LOG" | tail -30
```

**依据**：`set -o pipefail` 能解决一部分，但只要中间还有 `grep`/`head` 提前退出（SIGPIPE），
退出码依然不可信。**唯一可靠做法是把被测命令的退出码单独取出来。**

### 4.3 检测命令自身的三个陷阱（最重要）

这份文档里最容易出错的地方不是被测代码，而是**检测命令**。
本项目已经因为这个原因把「31 处 `SELECT *`」误报为「0 处」。

| 陷阱 | 错误写法 | 现象 | 正确写法 |
|---|---|---|---|
| BSD grep 的 BRE 不支持 `\|` | `grep "a\|b"` | **静默 0 命中** | `grep -E 'a\|b'` |
| BSD grep 不支持 `\b` / `\<` / `\>` | `grep -E '\bpkill\b'` | **静默 0 命中** | `[[:<:]]pkill[[:>:]]` 或 `(^\|[^A-Za-z0-9_])pkill` |
| BSD grep 不支持 `-P` | `grep -nP '...'` | `invalid option -- P` | `perl -ne` / `awk` |

**实测证据**

```bash
printf 'kill -9 123\n' > /tmp/_p.sh
grep -n "kill -9\|killall\|pkill" /tmp/_p.sh   # 无输出，exit=1  ← 假阴性
grep -nE "kill -9|killall|pkill"  /tmp/_p.sh   # 命中：1:kill -9 123
```

**强制规则**：任何「0 违规 / 无问题」的结论，都必须在同一个文件上
用一个**必然命中**的样本来验证检测命令本身有效。做不到就不许写"0"。

### 4.4 排除注释，但保留误报提示

```bash
grep -rnE "(^|[^A-Za-z0-9_])($pattern)([^A-Za-z0-9_]|$)" "$@" \
  | grep -vE '(^|:)[0-9]+:[[:space:]]*#'
```

排除整行注释（避免把「禁止使用 kill -9」的说明文字当成违规）。
但**消息字符串里的关键词仍会命中**，例如：

```
stop.command:59: echo "[FAIL] ... （未使用 kill -9，PID 文件已保留）"
```

处理方式：**如实打印为 `[WARN] 疑似提示文案（需人工确认）`，不计失败，但也绝不隐藏。**

> **为什么不把命令改到恰好返回 0？**
> 那会需要排除 `echo` 行，代价是漏掉 `eval "kill -9 $pid"` 这类真违规。
> **宁可误报，不可漏报。**

### 4.5 `check-scripts.sh` 扫描时排除自身

检测脚本必然包含 `pkill` / `kill -9` / `rm -rf` 这些字符串（作为模式定义与提示文案），
不排除就会自我误报。因此：

```bash
SCAN_SH=()
for f in "${SH_FILES[@]}"; do
  [ "$f" = "./scripts/check-scripts.sh" ] && continue
  SCAN_SH+=("$f")
done
```

被排除的只有**模式扫描**这一项；语法、严格模式、可执行位检查**仍然包含它**。

### 4.6 前端缺失工具 = 失败，不是跳过

```bash
REQUIRED="format:check lint lint:style type-check test build"
for s in $REQUIRED; do
  has_script "$s" || fail "缺少 npm script：${s}"
done
```

**依据**：门禁如果对缺失的工具报"跳过"，那它会在没人配置的仓库里显示全绿——比没有门禁更糟。

### 4.7 Vitest 关闭 `passWithNoTests`

```ts
test: { passWithNoTests: false }
```

**依据**：默认行为下「0 个测试」也算通过，门禁会静默变绿。关闭后，新增模块必须带测试。

### 4.8 JDK 必须锁在 17~22

```bash
is_usable_jdk() { [ "$major" -ge 17 ] && [ "$major" -le 22 ]; }
```

**依据**：**JDK 23+ 默认关闭隐式注解处理**，Lombok 静默失效，编译期出现数百处
「找不到符号 getXxx()」，而错误信息完全指向不到根因。
门禁必须在入口就把不合格的 JDK 拒掉，并给出明确提示。

---

## 5. 典型失败与修复

| 输出 | 原因 | 修复 |
|---|---|---|
| `[FAIL] 未找到主版本 17~22 的 JDK` | 装的是 JDK 23+ 或未设 `JAVA_HOME` | 装 JDK 17 或 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` |
| `[FAIL] 缺少 pipefail` | 新脚本忘了严格模式 | 加 `set -Eeuo pipefail` |
| `[FAIL] 变量后紧跟非 ASCII 字符` | 写了 `$VAR（中文` | 改成 `${VAR}（中文` |
| `[FAIL] 禁止 Controller 直接依赖 Mapper` | 分层违规 | 抽 Service，见 `DIRECTORY_STRUCTURE.md` §2.4 |
| `[FAIL] 禁止裸 LocalDate(Time).now()` | 时间基准不一致 | 注入 `Clock`，用 `LocalDateTime.now(clock)` |
| `[FAIL] 禁止 SELECT *` | XML 里写了 `SELECT *` | 按真实列序展开 |
| `[FAIL] ORDER BY 白名单插值数量变化` | 新增了 `${}` 插值点 | 放白名单 DTO 或改 `#{}` |
| `[FAIL] 缺少 npm script：format:check` | 前端工具链未接入 | 见 §6 |
| `Prettier --check` 失败 | 未格式化 | `cd frontend && npm run format` |
| `ESLint` 失败 | 未用变量 / `any` / Options API | 按提示修，**不要加 disable 注释** |
| Vitest 失败 | 逻辑回归或被新测试抓到缺陷 | 先判断是代码错还是测试错，禁止改测试迁就代码 |

---

## 6. 前端工具链配置清单

已交付（`frontend/`）：

| 文件 | 作用 |
|---|---|
| `.prettierrc.json` | 无分号、单引号、`printWidth: 100`、LF |
| `.prettierignore` | 排除 `node_modules` `dist` `*.d.ts` |
| `stylelint.config.js` | 标准规则 + Vue + 关闭纯书写风格规则 |
| `vitest.config.ts` | jsdom、`passWithNoTests: false` |
| `eslint.config.js` | 追加 `eslint-config-prettier`、规则升级为 error |

`package.json` scripts：

```json
"lint": "eslint . --max-warnings=0",
"lint:style": "stylelint \"src/**/*.{vue,css}\"",
"format": "prettier --write \"src/**/*.{ts,vue,css}\"",
"format:check": "prettier --check \"src/**/*.{ts,vue,css}\"",
"test": "vitest run",
"test:watch": "vitest",
"verify": "npm run format:check && npm run lint && npm run lint:style && npm run type-check && npm run test && npm run build"
```

### 6.1 Stylelint 的分级策略

| 级别 | 规则 | 当前状态 |
|---|---|---|
| **error** | 重复属性、非法色值、未知属性、未知伪元素 | 0 违规 ✅ |
| **warning** | `declaration-property-value-disallowed-list`（硬编码色值） | 12 处（FE-05 待收敛） |
| **关闭** | `color-function-notation` 等 7 条纯书写风格规则 | 见下方说明 |

**为什么关闭纯书写风格规则**：它们只影响「`rgba(0,0,0,.1)` 还是 `rgb(0 0 0 / 10%)`」，
不影响渲染。开启会产生数十处无意义的 CSS 改写，与「不改变视觉表现、最小 diff」冲突。
**这是有意识的取舍，不是忽略。**

### 6.2 硬编码色值为什么是 warning 而不是 error

存量分布在 10 个视图的 scoped 样式里（`PROJECT_CODE_AUDIT.md` FE-05）。
一次性全改属于大规模样式迁移，风险远大于收益。

**收敛路径**：warning 清单 → 分批替换为 `var(--color-*)` → 清零后把 severity 改为 `error`。

---

## 7. 门禁强度分级

不是所有改动都需要跑完整门禁（`CODE_STANDARDS.md` §5）：

| 变更类型 | 必跑 | 附加要求 |
|---|---|---|
| 纯文档 / 注释 | `--scripts`（若改了脚本）+ 不需要 | — |
| 单文件 bugfix | `--fast` | 说明为何不需要全量测试 |
| 前端改动 | `--frontend` | 视觉变化需要人工看页面 |
| 后端单模块 | `--backend --fast` | 触及 Mapper/事务必须全量 |
| **后端跨模块** | **完整 `verify.sh`** | **+ 启动并调一次真实接口** |
| 包结构 / 数据库 / 依赖 | **完整 `verify.sh`** | **+ 启动验证 + 数据比对** |

> **「编译通过 ≠ 装配成功」**：Mapper 绑定错误、`@Transactional` 失效、
> Bean 循环依赖都只在**运行期**暴露。跨模块变更必须启动服务并调真实接口。

---

## 8. 待补齐（B8 批次）

| 项 | 状态 | 说明 |
|---|---|---|
| **Checkstyle** | ✅ 已接入，**0 违规** | 规则集 `config/checkstyle/checkstyle.xml`（19 条规则）+ 存量抑制台账 `config/checkstyle/suppressions.xml`（14 处已登记技术债）+ 插件已在 `pom.xml` 声明。**刻意不绑定 `verify` 阶段**：本机曾无法下载 `javax.annotation-api` / `cdi-api` / `xbean-reflect`（TLS 握手失败），绑定会让 `mvn verify` 整体不可用、连带门禁失效。改为 `check-backend.sh` 显式调用，并按 §8.1 的三分类判定 |
| Spotless | ❌ 未接入 | 统一 Java 格式（4 空格、import 顺序）。与 Checkstyle 分工：Spotless 管格式，Checkstyle 管规范 |
| P3C / p3c-pmd | ❌ 未接入 | 阿里规约；当前用 Checkstyle + 7 条自定义 grep 规则覆盖主要风险点 |
| JaCoCo 覆盖率 | ❌ 未接入 | 先设软阈值（行覆盖 30%），逐步提升 |
| ShellCheck | ❌ 未安装 | 装好后 `check-scripts.sh` 自动启用。降级方案：`bash -n` + 33 项手工规则（已覆盖 SC2086/SC2181 的常见后果） |
| 前端覆盖率阈值 | ❌ 未设 | 待 `utils`/`composables` 补齐后设 60% |

**降级不等于静默通过**：`check-backend.sh` 在 Checkstyle 依赖缺失时打印
`[WARN] ... 本项降级为跳过`，并列出缺失的 artifact 名称。
**门禁整体仍以「降级项已明确告知」为通过条件**，不会假装检查过。

> 接入新工具时以「新增一个检查项」的方式加入，失败即阻断。
> 若某个工具的依赖在当前网络下不可获取，必须按 Checkstyle 的方式处理：
> **声明清楚 + 明确 WARN + 不改判定为 PASS**。

---

## 8.1 Checkstyle 失败的三分类判定（2026-11-11）

**背景（真实踩坑）**：`checkstyle.xml` 最初把 `TodoComment` 挂在 `<module name="Checker">` 下，
而它是 `TreeWalker` 的子节点。报错为：

```
[ERROR] Failed to execute goal ...: Failed during checkstyle configuration:
        TodoComment is not allowed as a child in Checker
```

当时的 `check-backend.sh` 只有「依赖缺失 → WARN」与「否则 → 存在违规」两个分支，
于是**配置错误被误报成「存在违规」**。后果不只是文案不准：修好配置后才发现，
这项检查从建立起就**一次都没有真正执行过**，29 项违规（含 3 处真实无用导入）全部被漏掉。

**教训**：门禁最危险的失效形式不是「误报失败」，而是**「从未执行却显示通过/报错在其他地方」**。

### 判定规则

不再依赖具体报错文案（Maven/插件一升级文案就变，实测同一错误能报出
`Failed during checkstyle configuration` 与 `NoSuchMethodError` 两种形态），
改用 **Checkstyle 自身的产出作为唯一硬判据**：

> 只有规则引擎真正跑完，Maven 才会打印 `You have N Checkstyle violations.`

| 条件 | 分类 | 处置 |
|---|---|---|
| 退出码 0 | 通过 | `[ OK ] Checkstyle 通过（0 违规）` |
| 退出码≠0 **且有** `You have N Checkstyle violations` | C 真实违规 | `[FAIL]` + 违规明细 + 提示「改代码，不要放宽规则」 |
| 退出码≠0 无违规数行，且匹配到网络/仓库错误关键词 | A 依赖不可达 | `[WARN]` 降级 + 列出缺失 artifact |
| 退出码≠0 无违规数行，且无网络错误特征 | B 配置/环境错误 | `[FAIL] Checkstyle 未能执行 —— 检查等同于未做` |

### 三种场景的实测验证（元测试）

门禁的判定逻辑本身必须有测试，否则「分类器写错」会退化成新的静默失效：

| 场景 | 构造方式 | 实测输出 |
|---|---|---|
| 1 正常 | 当前仓库 | `[ OK ] Checkstyle 通过（0 违规；存量债务见 config/checkstyle/suppressions.xml）` |
| 2 配置错误 | 向 `Checker` 注入不存在的 `<module name="BadModuleForMetaTest"/>` | `[FAIL] Checkstyle 未能执行（既无违规记录，也非依赖缺失）—— 检查等同于未做` |
| 3 真实违规 | 新建 `_gateprobe/GateProbe.java`，方法带 9 个参数 + 包名非法 | `[FAIL] Checkstyle 存在违规（You have 2 Checkstyle violations）` + 逐条明细 |

> 场景 2、3 的探针文件在验证后即删除，仓库内不保留。

---

## 8.2 存量技术债抑制台账（suppressions.xml）

首次真正跑通 Checkstyle 后暴露 **29 项违规**。分类处置，而非一键放宽阈值：

| 类型 | 数量 | 性质 | 处置 |
|---|---:|---|---|
| `ConstantName` 报 `log` | 12 | **规则错配**：`private static final Logger log` 是 SLF4J/Lombok 生态通行命名 | 规则改 `applyToPrivate=false`（只约束 public/protected/package 常量） |
| `UnusedImports` / `RedundantImport` | 3 | **真实缺陷** | 直接删导入（`JacksonConfig`、`CostAnalyticsService`、`InfrastructureCostService`） |
| `ParameterNumber` > 7 | 14 | **技术债**（最高 18 个参数） | `suppressions.xml` 按文件登记，B6 批次改用 record 聚合参数 |

**为什么用抑制文件而不是把 `max` 从 7 调到 18**：

- 调阈值 = 同时放过所有**新**代码，门禁形同虚设；
- 抑制文件按**文件**登记，规则本身保持 `max=7`，**新增文件超 7 个参数立即阻断**（已反向验证）；
- 债务在版本控制里可见、可审计、只能删不能加。

`suppressions.xml` 维护铁律：**只能删，不能加**。新增违规一律改代码（`AI_AGENT_DEVELOPMENT_RULES.md` R-04）。
每批治理删掉一条后必须重跑 `./scripts/verify.sh` 确认无回归。

---

## 9. 实测基线（2026-10-26）

| 指标 | 数值 |
|---|---:|
| 门禁域 | 3 |
| 脚本检查项 | 33 |
| 后端静态规则 | 7 条（全部 0 违规） |
| 后端测试 | 97（73 单元 + 24 集成） |
| 前端检查项 | 6 |
| 前端测试 | 36 |
| **测试合计** | **133** |
| 门禁总耗时 | ≈ 40s |
| 当前结论 | **全部通过，退出码 0** |

复现：

```bash
cd 03-ai-agent/spring-ai-milvus-demo
./scripts/verify.sh; echo "exit=$?"
```

---

## 10. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| 1.0 | 2026-10-26 | 首版。含 BSD grep 三个陷阱的实测证据、5 个设计决策的踩坑依据、7 项待接入工具清单 |
