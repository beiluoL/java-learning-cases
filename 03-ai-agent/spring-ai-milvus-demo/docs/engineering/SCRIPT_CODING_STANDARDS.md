# 脚本编码规范（Shell / Docker）

> 文档编号：ENG-SC-001  
> 版本：1.0  
> 生效日期：2026-10-26  
> 适用：`start.command` `stop.command` `restart.command` `start.bat` `stop.bat` `restart.bat`（根目录）+ `samples/*.sh` + `scripts/*.sh`  
> 强制力：**违反「安全红线」的脚本一律不得合并**  
> 上位文档：[`CODE_STANDARDS.md`](./CODE_STANDARDS.md) · 相关：[`DIRECTORY_STRUCTURE.md`](./DIRECTORY_STRUCTURE.md) §6

---

## 0. 安全红线（不可协商）

这五条不是风格问题，是「会不会误删别人的东西」的问题。

| 编号 | 红线 | 原因 | 检测 |
|---|---|---|---|
| **SR-01** | **禁止 `kill -9` / `killall` / `pkill -f`** | 会杀掉其他项目甚至系统进程；`-9` 让应用没机会 flush 日志与释放连接 | 见 10.2 `check_kill` |
| **SR-02** | **禁止 `rm -rf` 作用于变量路径** | `rm -rf "$DIR"/` 在 `DIR` 为空时变成 `rm -rf /` | 见 10.2 `check_rmrf` |
| **SR-03** | **禁止硬编码用户绝对路径** | `/Users/xxx/...` 换机即失效，且泄漏用户名 | `grep -rn "/Users/" *.command *.bat samples/*.sh` → 0 行 |
| **SR-04** | **禁止在脚本里写密钥** | 会进 git 历史 | `grep -rniE "api[_-]?key\|password=" ` → 0 行 |
| **SR-05** | **破坏性操作必须 `trap` 兜底** | 中途失败会把环境留在脏状态（本项目 `verify-alert-recovery.sh` 曾把系统留在紧急停用态） | 人工评审 |

> ⚠️ **检测命令本身必须可信**。本文档所有 grep 命令都遵循两条铁律，详见 §10.1：
> ① **必须用 `grep -E`**，BSD grep 的 BRE 不支持 `\|`；② **禁止用 `\b`**，BSD grep 不支持词边界。
> 这两点在 macOS 上都会导致**静默漏匹配 = 假的「0 违规」**，比没有检查更危险。

---

## 1. 文件类型与命名

| 类型 | 位置 | 命名 | 双击运行 |
|---|---|---|---|
| macOS 启停 | 根目录 | `start.command` / `stop.command` / `restart.command` | ✅ `.command` 关联 Terminal |
| Windows 启停 | 根目录 | `start.bat` / `stop.bat` / `restart.bat` | ✅ `.bat` 关联 cmd |
| 质量门禁 | `scripts/` | `verify.sh` / `check-*.sh` | ❌ 命令行 |
| 功能验证 | `samples/` | `verify-*.sh` | ❌ 命令行 |

**硬约束**

| 约束 | 内容 |
|---|---|
| 数量固定 | 启停脚本**只能有 6 个**。不要再加 `start.sh`、`run.command`、`dev.sh` |
| 必须可执行 | `chmod +x`，权限位 `-rwxr-xr-x` |
| 扩展名不可改 | 双击可用性依赖 `.command` / `.bat` 扩展名 |
| 平台独立 | `.command` 与 `.bat` **各自自包含**，互不调用 |

---

## 2. 脚本头部注释（强制）

每个脚本**必须有**下列 7 个字段的头部注释块。这是本项目已形成的约定，新脚本沿用同样格式与对齐：

```bash
#!/usr/bin/env bash
#
# 一键启动（macOS 双击 / 命令行均可）
#
# 用途     ：构建后端 jar 并启动后端（可选启动前端 dev server）
# 用法     ：start.command [all|backend]     双击等价于 all
# 参数     ：all      启动后端 + 前端（前端依赖缺失时明确告警并跳过）
#            backend  只启动后端
# 环境变量 ：BACKEND_PORT（默认 8091）、FRONTEND_PORT（默认 5174）
#            JAVA_HOME   若已设置且主版本在 17~22 之间则优先使用
# 执行前提 ：项目根目录可写；MySQL/Redis 由 infra/docker 与外部服务提供，本脚本不拉起
# 副作用   ：1) 会先精确停止本项目上一次启动的进程（见 stop.command）
#            2) 会在 .run/ 下写入 *.pid 与 backend.log / frontend.log
#            3) 会执行 mvn package，覆盖 target/ 下产物
# 失败处理 ：任一步失败即打印失败行号并非零退出；已启动的进程会保留并打印 PID
#
```

| 字段 | 必须回答 | 反面例子 |
|---|---|---|
| 用途 | 这个脚本干什么 | 「启动脚本」（等于没说） |
| 用法 | 完整调用形式 | 缺参数说明 |
| 参数 | 每个参数的取值与含义 | 「可选参数」 |
| 环境变量 | 全部可覆盖项 + 默认值 | 只写「见代码」 |
| 执行前提 | 需要什么已在运行 | 不写，用户跑失败了才猜 |
| **副作用** | **会改什么、会删什么、会写哪些文件** | **不写 → 用户不知道会覆盖数据** |
| 失败处理 | 失败时留下什么状态、退出码 | 不写 → 用户不知道要不要手工清理 |

> **「副作用」是这 7 项里最重要的**。脚本误删数据的投诉，99% 源于文档没写清会动哪里。

---

## 3. Shell 严格模式

### 3.1 长驻 / 破坏性脚本：`set -Eeuo pipefail`

```bash
#!/usr/bin/env bash
set -Eeuo pipefail
trap 'echo "[FAIL] 第 $LINENO 行执行失败（退出码 $?）"; exit 1' ERR
```

| 选项 | 作用 | 没有它会怎样 |
|---|---|---|
| `-e` | 命令失败立即退出 | 前半段失败，后半段继续跑，产出半成品 |
| `-u` | 引用未定义变量报错 | `rm -rf "$TYPO_DIR/"` 变成 `rm -rf /` |
| `-o pipefail` | 管道中任一环失败即失败 | `mvn ... \| tail` 吞掉 mvn 的失败码（**本项目踩过**） |
| `-E` | `ERR` trap 对子函数也生效 | 函数内失败不触发 trap |
| `trap ... ERR` | 打印失败行号 | 只看到「命令失败」，不知道哪一行 |

**本项目已落实**：3 个 `.command`（长驻/破坏性）均为 `set -Eeuo pipefail` + `#!/usr/bin/env bash`；
3 个 `samples/*.sh`（探测型）为 `set -uo pipefail` + 显式注释说明为何不用 `-e`。

### 3.2 探测型脚本：不适用 `-e`

**验证脚本**（`samples/verify-*.sh`）需要「单步失败仍继续收集证据」，因此**不使用 `-e`**，
但必须显式注释说明为什么，并用 `set -uo pipefail`：

```bash
#!/usr/bin/env bash
set -uo pipefail
# 说明：本脚本是探测型，刻意不使用 set -e。
#       单个接口探测失败需要继续跑完其余接口，最后统一汇总失败清单，
#       否则「第一个接口挂了」会掩盖「后面 5 个也挂了」。
```

同时必须用 `trap ... EXIT` 兜底恢复被改动的状态。

---

## 4. 进程管理

### 4.1 只杀自己的进程

**唯一允许的关停方式：读取自己写的 PID 文件。**

```bash
stop_one() {
  local name="$1" pidfile="$2" port="$3"
  [ -f "$pidfile" ] || { echo "[SKIP] $name 未在运行（无 PID 文件）"; return 0; }
  local pid
  pid="$(cat "$pidfile" 2>/dev/null || true)"

  # ① PID 必须是纯数字，否则视为脏文件，直接清理
  case "$pid" in
    ''|*[!0-9]*) echo "[WARN] $name PID 文件内容非法（$pid），已清理"; rm -f "$pidfile"; return 0 ;;
  esac

  # ② 进程已不存在（上次非正常退出），清理残留 PID 文件
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "[WARN] $name 进程 $pid 已不存在，清理过期 PID 文件"; rm -f "$pidfile"; return 0
  fi

  # ③ 精确关停：SIGTERM，等待优雅退出
  kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 20); do
    kill -0 "$pid" 2>/dev/null || { echo "[ OK ] $name 已停止（PID $pid，端口 $port）"; rm -f "$pidfile"; return 0; }
    sleep 0.5
  done

  # ④ 超时只告警，不升级 -9
  echo "[WARN] $name（PID $pid）10 秒内未退出，保留 PID 文件，请人工处理"
  return 1
}
```

| 规则 | 内容 |
|---|---|
| PID 文件位置 | `.run/backend.pid`、`.run/frontend.pid`（`.run/` 在 `.gitignore` 中） |
| 校验 PID 合法性 | 必须是纯数字 |
| 校验进程存活 | `kill -0` |
| 信号 | **只用 `SIGTERM`**，超时不升级 `-9`，改为告警 + 保留 PID 文件 |
| 幂等 | 未运行时执行 stop 必须 `exit 0` |
| 不按端口查杀 | `lsof -ti:8091 \| xargs kill` ❌ —— 端口可能是别的项目 |

> **为什么端口不能作为关停依据**：本项目 8080 / 5173 / 8091 / 8092 在不同时期都被其他项目占用过。
> 按端口杀进程，等于「谁占着这个端口我杀谁」，这是真实发生过的事故模式。

### 4.2 启动前的冲突检查

```bash
# 启动前：若本项目进程已在运行 → 先停后起（自动重启语义）
if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
  echo "[INFO] 检测到本项目后端已在运行，先停止再启动"
  stop_one backend "$PIDFILE" "$BACKEND_PORT"
fi

# 端口被「非本项目」进程占用 → 明确报错，绝不杀
if lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN -t >/dev/null 2>&1; then
  echo "[FAIL] 端口 $BACKEND_PORT 已被其他进程占用（非本项目）："
  lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN
  exit 1
fi
```

### 4.3 后台进程与日志

| 规则 | 内容 |
|---|---|
| 日志 | `.run/backend.log` / `.run/frontend.log` |
| PID | 启动后立即写 `.run/*.pid` |
| 后台启动 | `nohup ... &` 在脚本内可以（脚本本身是长驻的）；但**不要**在交互式工具里 `&` 后脱离 |
| 日志跟随 | 只在 `[ -t 1 ]`（标准输出是终端）时 `tail -f`，重定向到文件时不跟 |

```bash
if [ -t 1 ]; then
  tail -f "$LOG"      # 双击/终端：实时看日志
else
  echo "[INFO] 日志：$LOG"   # 被重定向：只打印路径
fi
```

---

## 5. 路径与变量

### 5.1 定位仓库根

```bash
# ✅ 不依赖调用者的当前目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
```

**禁止**：

```bash
cd /Users/beiluo/Documents/alProject/...   # 硬编码绝对路径
cd ../../                                    # 依赖调用位置
```

### 5.2 变量引用必须加引号

```bash
cp "$src" "$dst"          # ✅
cp $src $dst              # ❌ 路径含空格即失败
rm -rf "${TARGET_DIR:?}"/  # ✅ :? 在为空时报错退出
rm -rf "$TARGET_DIR"/      # ❌ 为空时变成 rm -rf /
```

### 5.3 ⚠️ macOS bash 3.2 的多字节陷阱（本项目真实踩过）

macOS 自带 `/bin/bash` 是 **3.2**。当变量后**紧跟中文**时，bash 3.2 会把中文字符的
首字节并入变量名，导致 `unbound variable`：

```bash
echo "[INFO] JAVA_HOME=$JAVA_HOME（主版本 17）"
# bash: JAVA_HOME\xef\xbc\x88: unbound variable      ← 报错位置在第 111 行，看起来毫不相关

echo "[INFO] JAVA_HOME=${JAVA_HOME}（主版本 17）"     # ✅ 必须写成 ${VAR}
```

| 规则 | 内容 |
|---|---|
| 变量后紧跟非 ASCII | **必须写 `${VAR}`** |
| 检测方式 | 见下方 `check_multibyte`（**不能用 `grep -P`，BSD grep 不支持**） |
| 本项目状态 | start 8 处 / stop 2 处 / restart 1 处已全部改为 `${VAR}`，复查 0 处 |

```bash
# 唯一可靠的实现：perl（macOS 自带）。grep -P 在 BSD grep 上直接报 invalid option
check_multibyte() {
  perl -ne 'print "$ARGV:$.: $_" if /\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7F]/' "$@"
}
# 正例：echo "x=$A（中文"   → 命中
# 反例：echo "y=${B}（中文"  → 不命中
```

> 同样原因，**禁止使用 bash 4+ 特性**：关联数组（`declare -A`）、`mapfile`、`${var,,}` 小写转换、
> `${var^^}` 大写转换。需要键值对时用「索引数组 + `|` 分隔」：
> ```bash
> SERVICES_ENTRIES=("redis|6379|redis://127.0.0.1:6379" "mysql|13307|jdbc:mysql://...")
> ```

---

## 6. 输出与退出码

### 6.1 统一状态前缀

| 前缀 | 含义 | 是否阻断 |
|---|---|---|
| `[INFO]` | 进度信息 | 否 |
| `[ OK ]` | 成功（注意 `OK` 两侧有空格，保持 5 字符对齐） | 否 |
| `[WARN]` | 可继续的异常 | 否 |
| `[FAIL]` | 失败，即将退出 | 是 |
| `[SKIP]` | 有意跳过 | 否 |

```bash
echo "[INFO] JAVA_HOME=${JAVA_HOME}"
echo "[ OK ] 后端已就绪（PID ${PID}，健康检查 200）"
echo "[WARN] 前端依赖缺失，跳过前端启动"
echo "[FAIL] 端口 ${PORT} 被其他进程占用"
```

### 6.2 退出码约定

| 码 | 含义 |
|---:|---|
| `0` | 成功（含「已无事可做」的幂等场景） |
| `1` | 一般失败 |
| `2` | 参数错误（用法不正确） |

```bash
case "${1:-all}" in
  all|backend) MODE="$1" ;;
  *) echo "[FAIL] 未知参数：$1"; echo "用法：$0 [all|backend]"; exit 2 ;;
esac
```

### 6.3 双击场景：窗口必须留住

双击运行时窗口关闭 = 用户看不到任何输出。

| 平台 | 做法 |
|---|---|
| macOS `.command` | Terminal 默认保持；**删除逻辑结束时不要 `exit`** 之外无需额外处理；若脚本会结束，末尾 `read -n 1 -s -r -p "按任意键关闭"` 且只在 `[ -t 0 ]` 时执行 |
| Windows `.bat` | 末尾 `pause`（长驻服务场景则保持 `cmd` 不退出） |

```bash
# macOS：只在交互式终端等待，被脚本调用时不等待（供 restart.command 复用）
if [ -t 0 ]; then
  read -n 1 -s -r -p "按任意键关闭窗口"
  echo
fi
```

---

## 7. 兜底恢复（`trap`）

任何**修改了系统状态**的脚本（改数据库额度、改 Redis key、停服务）必须 `trap` 恢复：

```bash
# 先读原值，再注册 trap，最后才改
ORIGINAL_LIMIT="$(SQL "SELECT daily_limit FROM ai_cost.ai_budget_policy WHERE id=${POLICY_ID};" | tr -d '[:space:]')"

restore_limit() {
  [ -n "${ORIGINAL_LIMIT:-}" ] && SETLIMIT "$ORIGINAL_LIMIT" >/dev/null 2>&1 || true
}
trap 'restore_limit; echo "[INFO] 脚本结束，额度已恢复为 ${ORIGINAL_LIMIT}"' EXIT
```

**关键点**

| 点 | 说明 |
|---|---|
| 顺序 | **先读原值 → 再 trap → 最后才改**。顺序错了等于没兜底 |
| `EXIT` 而非 `ERR` | 正常结束、`Ctrl-C`、`set -e` 退出都应恢复 |
| `\|\| true` | 恢复动作本身失败不能让 trap 中断 |
| 幂等 | 恢复函数重复执行结果一致 |
| 日志 | trap 里打印「恢复成什么」，让用户知道当前状态 |

> **本项目真实案例**：`verify-alert-recovery.sh` 早期版本会在中途失败时把系统
> 留在「紧急停用（BLOCKED）」状态——后续所有请求都被拒，而脚本已经退出。
> 加 `trap cleanup EXIT` 后，无论怎么退出都会 `UPDATE` 回原额度并调 `resume`。

---

## 8. Windows `.bat` 专项

| 规则 | 写法 |
|---|---|
| 首行 | `@echo off` |
| **UTF-8 中文** | 第二行 `chcp 65001 >nul`，否则中文输出乱码 |
| 路径含空格 | 必须 `"%VAR%"` 双引号 |
| 变量延迟展开 | 循环内用 `setlocal enabledelayedexpansion` + `!VAR!` |
| 结束不闪退 | 末尾 `pause` |
| 不写 `.ps1` 中转 | 非 ASCII 路径会被编码破坏成乱码文件名 |
| 精确关停 | `taskkill /PID %PID% /T`（**不带 `/F`**），与 macOS 的 `SIGTERM` 语义对齐 |

```bat
@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set "ROOT=%~dp0"
set "PIDFILE=%ROOT%.run\backend.pid"

if not exist "%PIDFILE%" (
    echo [SKIP] 后端未在运行
    goto :eof
)
set /p PID=<"%PIDFILE%"
taskkill /PID !PID! /T >nul 2>&1
echo [ OK ] 后端已停止（PID !PID!）
```

**禁止**：`taskkill /IM node.exe` / `taskkill /IM java.exe`（按镜像名杀，会误杀所有 Node/Java 进程）。

---

## 9. Docker / Compose

| 规则 | 内容 |
|---|---|
| 镜像必须锁版本 | `mysql:8.0.36`，**禁止 `mysql:latest`** |
| 端口映射写全 | `0.0.0.0:13307->3306/tcp` 显式声明，避免与其他项目撞端口 |
| 数据卷 | 具名 volume，不用 bind mount 到用户目录 |
| **默认密码仅本地可用** | `MYSQL_ROOT_PASSWORD: root` 必须注释「生产必须覆盖」 |
| 健康检查 | 配 `healthcheck`，依赖方用 `depends_on: condition: service_healthy` |
| 时区 | `TZ: UTC`，与全链路 UTC 一致 |
| 字符集 | `--character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci` |

```yaml
services:
  mysql:
    image: mysql:8.0.36                    # ✅ 锁版本
    # image: mysql:latest                  # ❌ 不可复现
    environment:
      MYSQL_ROOT_PASSWORD: root            # 仅本地；生产必须用 secret 覆盖
      TZ: UTC
    ports:
      - "0.0.0.0:13307:3306"               # 显式绑定，避开 3306 默认端口冲突
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1"]
      interval: 5s
      retries: 20
```

---

## 10. 静态检查

### 10.1 ⚠️ 检测命令本身的三个陷阱（macOS BSD grep）

本节是整份文档里**最容易出错**的部分。踩过三次，每次都是「工具报 0 → 以为没问题 → 实际有 31 处」。

| 陷阱 | 错误写法 | 现象 | 正确写法 |
|---|---|---|---|
| **BRE 不支持 `\|`** | `grep "a\|b"` | 静默 0 命中。实测：含 `kill -9` 的文件用 `grep "kill -9\|pkill"` 返回 **无匹配** | `grep -E "a\|b"` 或 `grep -E "a\|b"` 的 ERE 形式 `grep -E 'a|b'` |
| **不支持 `\b` / `\<` / `\>`** | `grep -E '\bpkill\b'` | 静默 0 命中。实测：`grep -E 'pkill'` 命中，`grep -E '\bpkill\b'` 不命中 | `[[:<:]]pkill[[:>:]]`（BSD）或 `(^|[^A-Za-z0-9_])pkill([^A-Za-z0-9_]|$)`（GNU/BSD 通用） |
| **不支持 `-P`（PCRE）** | `grep -nP '...'` | `grep: invalid option -- P` | 用 `perl -ne` 或 `awk` |

**验证方法（必须做）**：写检测命令时，同时构造**正例**（一个必然命中的假文件）与
**反例**（一个只在注释里提到关键词的假文件），两者都对才算命令可用。

```bash
# 正例
printf '#!/bin/bash\npkill -f node\nkill -9 123\nkill -9x\n' > /tmp/_pos.sh
# 反例
printf '#!/bin/bash\n# 注释里提到 pkill 与 kill -9\necho ok\n' > /tmp/_neg.sh
```

### 10.2 可用的检测函数

```bash
# 通用：匹配关键词，排除整行注释，并用可移植词边界避免 pkillx / killall2 误命中
_check() {
  local pattern="$1"; shift
  grep -rnE "(^|[^A-Za-z0-9_])(${pattern})([^A-Za-z0-9_]|$)" "$@" 2>/dev/null \
    | grep -vE '(^|:)[0-9]+:[[:space:]]*#'
}

check_kill()    { _check 'pkill|killall|kill[[:space:]]+-9' "$@"; }
check_rmrf()    { _check 'rm[[:space:]]+-rf([[:space:]]|$)' "$@"; }
check_abs_path(){ _check '/Users/' "$@"; }
check_secret()  { _check 'api[_-]?key|password[[:space:]]*=' "$@"; }

# 多字节变量界定（必须 perl，grep -P 不可用）
check_multibyte() {
  perl -ne 'print "$ARGV:$.: $_" if /\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7F]/' "$@"
}
```

**验证结果（2026-10-26 实测）**

| 函数 | 正例 | 负例 | 本项目 |
|---|---|---|---|
| `check_kill` | `/tmp/_pos.sh` → 2 行（`pkill`、`kill -9`；正确排除 `kill -9x`） | `/tmp/_neg.sh` → 0 行 | **1 行**（见下方说明） |
| `check_rmrf` | — | 注释含 `rm -rf` → 0 行 | **0 行**（4 处 `rm -f` 不命中，符合预期） |
| `check_abs_path` | — | — | **0 行** ✅ |
| `check_multibyte` | `$A（中文` → 命中 | `${B}（中文` → 不命中 | **0 行** ✅ |

> **`check_kill` 的 1 行是已知误报，不是违规**：
> `stop.command:59: echo "[FAIL] ... （未使用 kill -9，PID 文件已保留）"`
> 这是一条**提示文案**，声明脚本没有用 `kill -9`。检测命令无法区分「在代码里调用」与
> 「在消息里提到」，因此**这类命中必须人工确认**——本文档选择如实列出，而不是把命令改到
> 恰好返回 0（那会让检查失去意义）。
>
> 若确实需要零误报，可再加一层过滤 `| grep -vE '[[:space:]]*echo '`，
> 但代价是漏掉 `eval "kill -9 $pid"` 这类真违规。**宁可误报，不可漏报。**

### 10.3 工具链

| 工具 | 状态 | 说明 |
|---|---|---|
| `bash -n`（语法） | ✅ 已在本机执行通过 | 最低要求，进 `scripts/verify.sh` |
| **ShellCheck** | ❌ 未安装 | B8 接入；`shellcheck --severity=warning` 必须 0 |
| `shfmt`（格式化） | ❌ 未安装 | 可选 |

`shellcheck` 未安装时的降级检查（`scripts/check-scripts.sh` 已实现）：

```bash
for f in *.command samples/*.sh scripts/*.sh; do
  bash -n "$f" || { echo "[FAIL] 语法错误：$f"; exit 1; }
done
```

**ShellCheck 重点关注的规则**

| 规则 | 含义 |
|---|---|
| SC2086 | 变量未加引号 |
| SC2181 | 用 `$?` 而非直接判断命令 |
| SC2046 | 命令替换未加引号导致分词 |
| SC2155 | `local x=$(cmd)` 会吞掉返回码 |
| SC2164 | `cd` 失败未处理 |
| SC2317 | 不可达代码（trap 函数常被误报，可 `# shellcheck disable=` 说明） |

### 10.4 多字节变量检测的完整说明

`grep -P` 在 macOS BSD grep 上**不可用**（`invalid option -- P`）。可用替代：

```bash
# ✅ perl（推荐，macOS 自带且行为稳定）
perl -ne 'print "$ARGV:$.: $_" if /\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7F]/' start.command

# ✅ awk 亦可行，但转义更啰嗦
LC_ALL=C awk '/\$[A-Za-z_][A-Za-z0-9_]*[^ -~]/ {print FILENAME":"FNR": "$0}'
```

---

## 11. 拒绝清单

| 编号 | 拒绝项 | 检测 |
|---|---|---|
| S-01 | `kill -9` / `killall` / `pkill -f` | `check_kill`（见 10.2；命中含提示文案需人工确认） |
| S-02 | 按端口/镜像名杀进程 | `_check 'lsof.*xargs.*kill|taskkill[[:space:]]+/IM'` |
| S-03 | `rm -rf`（未用 `:?`） | `check_rmrf` |
| S-04 | 硬编码 `/Users/...` | `check_abs_path` |
| S-05 | 脚本内明文密钥 | `check_secret` |
| S-06 | 缺 `set -Eeuo pipefail`（非探测型） | `grep -L "pipefail" *.command` |
| S-07 | `$VAR` 后紧跟中文（bash 3.2） | `check_multibyte`（perl，**不可用 `grep -P`**） |
| S-08 | 缺 7 字段头部注释 | 人工评审 |
| S-09 | 无 `trap` 却改系统状态 | 人工评审 |
| S-10 | `.bat` 缺 `chcp 65001` | `grep -L "chcp 65001" *.bat` |
| S-11 | `docker` 镜像用 `latest` | `grep -n ":latest"` |
| S-12 | 脚本不可执行（`-rw-r--r--`） | `ls -l *.command` |

---

## 12. 现状核对（2026-10-26）

| 文件 | 行数 | 严格模式 | trap | 头部 7 字段 | 可执行 |
|---|---:|---|---|---|---|
| `start.command` | 214 | ✅ `-Eeuo pipefail` | ✅ ERR | ✅ | ✅ |
| `stop.command` | 78 | ✅ `-Eeuo pipefail` | — | ✅ | ✅ |
| `restart.command` | 30 | ✅ `-Eeuo pipefail` | — | ✅ | ✅ |
| `start.bat` | 74 | `chcp 65001` | — | ✅ | ✅ |
| `stop.bat` | 43 | `chcp 65001` | — | ✅ | ✅ |
| `restart.bat` | 10 | `chcp 65001` | — | ✅ | ✅ |
| `samples/verify-admin-endpoints.sh` | 105 | ✅ `-uo pipefail`（探测型，无 `-e`） | — | ✅ | ✅ |
| `samples/verify-alert-levels.sh` | 90 | ✅ 探测型 | ✅ EXIT 恢复额度 | ✅ | ✅ |
| `samples/verify-alert-recovery.sh` | 92 | ✅ 探测型 | ✅ EXIT 恢复额度 + resume | ✅ | ✅ |
| `scripts/verify.sh` | 待建 | — | — | — | B8 |

| 安全检查 | 结果 |
|---|---|
| `kill -9` / `pkill` / `killall` | **实际调用 0 处**；`check_kill` 命中 1 行，为 `stop.command:59` 的提示文案「未使用 kill -9」 |
| 硬编码 `/Users/` | **0 处** ✅ |
| `rm -rf` | **0 处** ✅（4 处为 `rm -f`，不命中） |
| `$VAR` 后跟中文 | **0 处** ✅（perl 检测） |
| 全部 `#!/usr/bin/env bash` | 6 / 6 ✅ |
| 可执行位 | 9 / 9 ✅ |

复现命令（**必须用 -E，禁止 `\b` 与 `-P`**）：

```bash
_check() { grep -rnE "(^|[^A-Za-z0-9_])($1)([^A-Za-z0-9_]|\$)" "${@:2}" 2>/dev/null \
             | grep -vE '(^|:)[0-9]+:[[:space:]]*#'; }

_check 'pkill|killall|kill[[:space:]]+-9' *.command *.bat samples/*.sh   # 1 行提示文案
_check '/Users/'                          *.command *.bat samples/*.sh   # 0 行
_check 'rm[[:space:]]+-rf([[:space:]]|$)' *.command *.bat samples/*.sh   # 0 行

perl -ne 'print "$ARGV:$.: $_" if /\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7F]/' *.command samples/*.sh  # 0 行

for f in *.command samples/*.sh; do bash -n "$f" || echo "语法错误：$f"; done                    # 无输出
```

---

## 13. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| 1.0 | 2026-10-26 | 首版。含 macOS bash 3.2 多字节陷阱、`pipefail` 吞退出码教训、兜底恢复真实事故 |
