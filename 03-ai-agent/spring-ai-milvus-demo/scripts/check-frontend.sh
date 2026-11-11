#!/usr/bin/env bash
#
# 前端检查（门禁子步骤，由 verify.sh 调用）
#
# 用途     ：格式 + ESLint + Stylelint + 类型 + 单测 + 构建，六道检查依次执行
# 用法     ：scripts/check-frontend.sh [--fast]
# 参数     ：--fast  跳过 build 与 test（本地快速反馈用；CI 必须不带该参数）
# 环境变量 ：FRONTEND_DIR  前端目录，默认 <仓库根>/frontend
# 执行前提 ：Node 18+ 与 npm；frontend/node_modules 已安装
# 副作用   ：只读源码；会写入 frontend/dist（build）与 node_modules/.vite 缓存
# 失败处理 ：任一检查失败即汇总并以 1 退出；全部通过以 0 退出
#
# 设计说明：本脚本刻意不使用 set -e，需要跑完所有检查再汇总。
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
FRONTEND_DIR="${FRONTEND_DIR:-${ROOT_DIR}/frontend}"

FAILED=0
FAST=0
for arg in "$@"; do
  case "$arg" in
    --fast) FAST=1 ;;
    *) echo "[FAIL] 未知参数：${arg}"; echo "用法：$0 [--fast]"; exit 2 ;;
  esac
done

info() { echo "[INFO] $*"; }
ok()   { echo "[ OK ] $*"; }
fail() { echo "[FAIL] $*"; FAILED=$((FAILED + 1)); }

if [ ! -d "$FRONTEND_DIR" ]; then
  fail "前端目录不存在：${FRONTEND_DIR}"
  echo; echo "===== 前端检查失败：环境缺失 ====="; exit 1
fi
cd "$FRONTEND_DIR"

if ! command -v node >/dev/null 2>&1; then
  fail "未找到 node，请安装 Node 18+"
  echo; echo "===== 前端检查失败：环境缺失 ====="; exit 1
fi
info "node=$(node -v) npm=$(npm -v) 目录=${FRONTEND_DIR}"

if [ ! -d node_modules ]; then
  fail "node_modules 不存在，请先在 ${FRONTEND_DIR} 执行 npm install"
  echo; echo "===== 前端检查失败：依赖未安装 ====="; exit 1
fi

# 读取 package.json 中已定义的 scripts，用于判断某项检查是否已接入
AVAILABLE_SCRIPTS="$(node -e "console.log(Object.keys(require('./package.json').scripts||{}).join(' '))" 2>/dev/null)"
has_script() {
  case " ${AVAILABLE_SCRIPTS} " in
    *" $1 "*) return 0 ;;
    *) return 1 ;;
  esac
}

run_script() {
  local name="$1" label="$2" log
  log="$(mktemp -t fe-${name//[:]/_}-XXXXXX.log)"
  if npm run --silent "$name" > "$log" 2>&1; then
    ok "${label}"
    tail -5 "$log" | grep -v '^$' | sed 's/^/       /'
  else
    fail "${label}"
    tail -40 "$log" | sed 's/^/       /'
    echo "       完整日志：${log}"
  fi
}

# 必查项：缺失即失败（这是门禁的存在意义——不允许静默跳过）
REQUIRED="format:check lint lint:style type-check"
if [ "$FAST" -eq 0 ]; then
  REQUIRED="${REQUIRED} test build"
fi

echo
info "检查 package.json scripts 完整性"
MISSING=""
for s in $REQUIRED; do
  if ! has_script "$s"; then
    fail "缺少 npm script：${s}（按 QUALITY_GATE 要求必须提供）"
    MISSING="${MISSING} ${s}"
  fi
done
[ -z "$MISSING" ] && ok "全部必需 script 已定义：${REQUIRED}"

# 若有关键 script 缺失，后续逐项执行会产生噪音，直接给出指引后退出
if [ -n "$MISSING" ]; then
  echo
  echo "       缺失项：${MISSING}"
  echo "       修复方式见 docs/engineering/QUALITY_GATE.md 第 3 节「前端工具链」"
  echo
  echo "===== 前端检查失败：工具链未接入 ====="
  exit 1
fi

echo
info "依次执行前端检查"
run_script "format:check" "Prettier 格式检查"
run_script "lint"         "ESLint 静态检查"
run_script "lint:style"   "Stylelint 样式检查"
run_script "type-check"   "TypeScript 类型检查"

if [ "$FAST" -eq 1 ]; then
  echo
  echo "[SKIP] 已指定 --fast，跳过 test 与 build"
else
  run_script "test"  "Vitest 单元测试"
  run_script "build" "Vite 生产构建"
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "===== 前端检查通过 ====="
  exit 0
fi
echo "===== 前端检查失败：${FAILED} 项未通过 ====="
exit 1
