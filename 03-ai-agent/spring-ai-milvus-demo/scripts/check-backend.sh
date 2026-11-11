#!/usr/bin/env bash
#
# 后端检查（门禁子步骤，由 verify.sh 调用）
#
# 用途     ：编译 + 测试 + 静态规则扫描（分层、SQL、日志、时间基准、占位符）
# 用法     ：scripts/check-backend.sh [--fast]
# 参数     ：--fast  只做编译与静态扫描，跳过测试（本地快速反馈用；CI 必须不带该参数）
# 环境变量 ：JAVA_HOME  主版本必须在 17~22 之间；未设置或版本不符时脚本自动探测
#            SKIP_TESTS=1  等价于 --fast
# 执行前提 ：Maven 可用；依赖已下载（首次运行需联网）
# 副作用   ：读写 target/；不修改源码
# 失败处理 ：任一检查失败即汇总并以 1 退出；全部通过以 0 退出
#
# 设计说明：本脚本刻意不使用 set -e，需要跑完所有检查再汇总。
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT_DIR"

FAILED=0
FAST=0
for arg in "$@"; do
  case "$arg" in
    --fast) FAST=1 ;;
    *) echo "[FAIL] 未知参数：${arg}"; echo "用法：$0 [--fast]"; exit 2 ;;
  esac
done
[ "${SKIP_TESTS:-0}" = "1" ] && FAST=1

info() { echo "[INFO] $*"; }
ok()   { echo "[ OK ] $*"; }
fail() { echo "[FAIL] $*"; FAILED=$((FAILED + 1)); }

# ---------- 1. 定位 JDK 17~22 ----------
# JDK 23+ 默认关闭隐式注解处理，Lombok 会静默失效并导致数百处「找不到符号」。
java_major() {
  "$1/bin/java" -version 2>&1 | awk -F'"' '/version/ {split($2, a, "."); print (a[1] == "1" ? a[2] : a[1]); exit}'
}

resolve_java_home() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    local m
    m="$(java_major "$JAVA_HOME")"
    if [ -n "$m" ] && [ "$m" -ge 17 ] && [ "$m" -le 22 ]; then
      echo "$JAVA_HOME"; return 0
    fi
    echo "[WARN] 已设置的 JAVA_HOME 主版本为 ${m}，不满足 17~22，将自动探测" >&2
  fi
  if /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    /usr/libexec/java_home -v 17; return 0
  fi
  for d in "$HOME"/.sdkman/candidates/java/* /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17; do
    if [ -x "$d/bin/java" ]; then
      local m
      m="$(java_major "$d")"
      if [ -n "$m" ] && [ "$m" -ge 17 ] && [ "$m" -le 22 ]; then echo "$d"; return 0; fi
    fi
  done
  return 1
}

if ! JH="$(resolve_java_home)"; then
  fail "未找到主版本 17~22 的 JDK。请设置 JAVA_HOME 或安装 JDK 17"
  echo; echo "===== 后端检查失败：环境缺失 ====="; exit 1
fi
export JAVA_HOME="$JH"
export PATH="${JAVA_HOME}/bin:${PATH}"
info "JAVA_HOME=${JAVA_HOME}（主版本 $(java_major "$JAVA_HOME")）"

if ! command -v mvn >/dev/null 2>&1; then
  fail "未找到 mvn，请安装 Maven 或将其加入 PATH"
  echo; echo "===== 后端检查失败：环境缺失 ====="; exit 1
fi

# ---------- 2. 静态规则扫描 ----------
# 每个规则：命中即失败。检测统一用 grep -E 且不用 \b（BSD grep 不支持，会静默漏匹配）。
# 末尾过滤掉整行注释，避免把「禁止 xxx」这类说明文字当成违规。
scan_rule() {
  local label="$1" pattern="$2"
  shift 2
  local hits
  hits="$(grep -rnE "$pattern" "$@" 2>/dev/null | grep -vE '(^|:)[0-9]+:[[:space:]]*(//|/\*|\*)')"
  if [ -n "$hits" ]; then
    fail "$label"
    echo "$hits" | head -20 | sed 's/^/       /'
    local n
    n="$(echo "$hits" | wc -l | tr -d ' ')"
    [ "$n" -gt 20 ] && echo "       ...（共 ${n} 处，仅显示前 20）"
  else
    ok "${label}：0 处"
  fi
}

echo
info "静态规则扫描（源码：src/main/java）"

scan_rule "禁止 @Autowired 字段注入"            '@Autowired'                       src/main/java
scan_rule "禁止 System.out / printStackTrace"   'System\.(out|err)\.|printStackTrace' src/main/java
scan_rule "禁止 TODO / FIXME 占位"              '(^|[^A-Za-z])(TODO|FIXME|XXX)([^A-Za-z]|$)' src/main/java
scan_rule "禁止裸 LocalDate(Time).now()（须用注入的 Clock）" 'LocalDate(Time)?\.now\(\)' src/main/java
scan_rule "禁止 Controller 直接依赖 Mapper"      'import com\.example\.ai\..*\.mapper\.' src/main/java/com/example/ai/*/controller

echo
info "静态规则扫描（Mapper XML：src/main/resources/mapper）"
scan_rule "禁止 SELECT *"                        '[Ss][Ee][Ll][Ee][Cc][Tt][[:space:]]+\*' src/main/resources/mapper

# ${} 插值：唯一允许的位置是 ORDER BY，且必须由 DTO 的 setter 白名单兜住。
# 白名单例外数量锁定为 1（TokenUsageMapper 的 ORDER BY ${q.sort}）。
# 数量变化说明有人新增了插值点，必须评审，不能静默放过。
INTERP_ALL="$(grep -rnE '\$\{' src/main/resources/mapper 2>/dev/null)"
INTERP_ORDER="$(echo "$INTERP_ALL" | grep -E 'ORDER[[:space:]]+BY' | grep -v '^$')"
INTERP_OTHER="$(echo "$INTERP_ALL" | grep -vE 'ORDER[[:space:]]+BY' | grep -v '^$')"

if [ -n "$INTERP_OTHER" ]; then
  fail "禁止 ORDER BY 之外的 SQL 字符串插值 \${}"
  echo "$INTERP_OTHER" | sed 's/^/       /'
else
  ok "非 ORDER BY 的 SQL 字符串插值：0 处"
fi

INTERP_ORDER_N=0
[ -n "$INTERP_ORDER" ] && INTERP_ORDER_N="$(echo "$INTERP_ORDER" | wc -l | tr -d ' ')"
if [ "$INTERP_ORDER_N" -eq 1 ]; then
  ok "ORDER BY 白名单插值：1 处（已登记例外）"
  echo "$INTERP_ORDER" | sed 's/^/       /'
else
  fail "ORDER BY 白名单插值数量变化（期望 1，实际 ${INTERP_ORDER_N}）：新增插值点必须评审"
  [ -n "$INTERP_ORDER" ] && echo "$INTERP_ORDER" | sed 's/^/       /'
fi

# ---------- 3. Checkstyle ----------
# 依赖可解析 → 违规即失败；不可解析 → 降级 WARN（本机曾出现 3 个仓库 artifact TLS 握手失败）。
# 刻意不把 Checkstyle 绑到 mvn verify：插件依赖缺失时会让整个构建不可用。
#
# 失败必须区分三种成因，不能一律报「存在违规」：
#   A. 依赖缺失/网络失败  → 降级 WARN（门禁能力受限，但已明确告知）
#   B. 规则配置本身写错    → 立刻 FAIL（此时检查从未真正执行，属最危险的静默失效）
#   C. 真实代码违规        → FAIL 并打印违规明细
# 历史教训：checkstyle.xml 曾把 TodoComment 挂在 Checker 下（应为 TreeWalker 子节点），
# 报错信息是「Failed during checkstyle configuration」，当时被误判为「存在违规」。
echo
info "Checkstyle 静态规范检查（config/checkstyle/checkstyle.xml）"
CS_LOG="$(mktemp -t checkstyle-XXXXXX.log)"
CS_CFG="config/checkstyle/checkstyle.xml"
CS_SUP="config/checkstyle/suppressions.xml"

if [ ! -f "$CS_CFG" ]; then
  fail "规则文件缺失：${CS_CFG}"
elif [ ! -f "$CS_SUP" ]; then
  fail "存量抑制台账缺失：${CS_SUP}（缺失会导致 14 处已登记债务立刻阻断门禁）"
elif mvn -B checkstyle:check > "$CS_LOG" 2>&1; then
  ok "Checkstyle 通过（0 违规；存量债务见 ${CS_SUP}）"
else
  # 核心判据：只有 Checkstyle 真正跑完才会打印 "You have N Checkstyle violations"。
  # 因此「有这一行」= 真实违规；「没这一行」= 检查根本没执行成功（依赖或配置问题）。
  # 不依赖具体报错文案，避免 Maven/插件升级后分类失效。
  # 历史教训：曾靠匹配 "Could not transfer artifact" 分类，结果 Guava 冲突
  # 换了一种报错文案（NoSuchMethodError）就被误判为「存在违规」。
  CS_VIOL="$(grep -oE "You have [0-9]+ Checkstyle violations" "$CS_LOG" | head -1)"

  if [ -n "$CS_VIOL" ]; then
    # ---- C. 真实代码违规 ----
    fail "Checkstyle 存在违规（${CS_VIOL}）"
    grep -E "\[ERROR\].*\.java" "$CS_LOG" | head -30 | sed 's/^/       /'
    echo "       处置：改代码，不要放宽规则或扩 suppressions.xml（R-04）"
    echo "       完整日志：${CS_LOG}"
  elif grep -qE "Could not (transfer|resolve) artifact|Could not resolve dependencies|in offline mode|UnknownHostException|Connection (reset|refused|timed out)" "$CS_LOG"; then
    # ---- A. 依赖不可达：降级，但明确告知未真正检查 ----
    echo "[WARN] Checkstyle 依赖无法下载（网络受限），本项降级为跳过"
    echo "       规则集与插件配置已就绪；依赖可下载时本项会自动生效并阻断违规"
    grep -oE "Could not (transfer|resolve) artifact [^ ]+" "$CS_LOG" | head -3 | sed 's/^/       /'
  else
    # ---- B. 规则配置或插件环境错误：必须阻断，否则门禁静默失效 ----
    fail "Checkstyle 未能执行（既无违规记录，也非依赖缺失）—— 检查等同于未做"
    grep -E "^\[ERROR\]" "$CS_LOG" | grep -vE "^\[ERROR\] *$" | head -4 | sed 's/^/       /'
    echo "       常见原因：checkstyle.xml 模块层级写错（如 TodoComment 挂在 Checker 下）、"
    echo "                 插件 Guava/依赖版本冲突（NoSuchMethodError）"
    echo "       完整日志：${CS_LOG}"
  fi
fi

# ---------- 4. 编译 ----------
echo
info "编译主代码与测试代码"
if mvn -B -q clean test-compile 2>&1 | tail -30; then
  ok "编译通过"
else
  fail "编译失败（完整日志见上方输出）"
fi

# ---------- 5. 测试 ----------
if [ "$FAST" -eq 1 ]; then
  echo
  echo "[SKIP] 已指定 --fast / SKIP_TESTS=1，跳过测试"
else
  echo
  info "执行全量测试（单元 + 集成）"
  TEST_LOG="$(mktemp -t backend-test-XXXXXX.log)"
  if mvn -B -Pintegration verify > "$TEST_LOG" 2>&1; then
    ok "$(grep -E 'Tests run:.*Failures.*Errors' "$TEST_LOG" | tail -2 | tr '\n' ' ')"
  else
    fail "测试未通过"
    grep -E "Tests run:|ERROR|FAIL" "$TEST_LOG" | head -40 | sed 's/^/       /'
    echo "       完整日志：${TEST_LOG}"
  fi
fi

# ---------- 汇总 ----------
echo
if [ "$FAILED" -eq 0 ]; then
  echo "===== 后端检查通过 ====="
  exit 0
fi
echo "===== 后端检查失败：${FAILED} 项未通过 ====="
exit 1
