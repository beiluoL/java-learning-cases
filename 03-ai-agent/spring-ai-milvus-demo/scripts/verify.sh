#!/usr/bin/env bash
#
# 统一质量门禁（唯一入口）
#
# 用途     ：一次跑完后端 / 前端 / 脚本三域的全部检查，给出统一的通过/失败结论
# 用法     ：scripts/verify.sh [选项]
# 参数     ：--fast            跳过耗时的测试与构建（本地快速反馈；CI 必须不带）
#            --backend         只跑后端
#            --frontend        只跑前端
#            --scripts         只跑脚本检查
#            -h | --help       显示帮助
#            （不指定范围时三域全跑）
# 环境变量 ：SKIP_TESTS=1      等价于 --fast
#            FRONTEND_DIR      前端目录，默认 <仓库根>/frontend
# 执行前提 ：JDK 17~22、Maven、Node 18+、npm（前端依赖已 npm install）
# 副作用   ：只读源码；会写 target/、frontend/dist、临时日志
# 失败处理 ：任一域失败即整体失败（退出码 1）；参数错误退出码 2
#
# 设计约定（重要）：
#   1. 任何检查失败都不中断后续检查——必须跑完全部再汇总，
#      否则「第一个失败」会掩盖后面 5 个失败，返工次数翻倍。
#   2. 每个子检查的退出码被显式收集，不依赖管道最后的命令。
#      历史教训：`mvn ... | tail -30` 会让退出码变成 tail 的 0，失败被吞掉。
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT_DIR"

FAST=0
RUN_BACKEND=0
RUN_FRONTEND=0
RUN_SCRIPTS=0
EXPLICIT_SCOPE=0

usage() {
  sed -n '4,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

for arg in "$@"; do
  case "$arg" in
    --fast)     FAST=1 ;;
    --backend)  RUN_BACKEND=1;  EXPLICIT_SCOPE=1 ;;
    --frontend) RUN_FRONTEND=1; EXPLICIT_SCOPE=1 ;;
    --scripts)  RUN_SCRIPTS=1;  EXPLICIT_SCOPE=1 ;;
    -h|--help)  usage; exit 0 ;;
    *) echo "[FAIL] 未知参数：${arg}"; echo; usage; exit 2 ;;
  esac
done

[ "${SKIP_TESTS:-0}" = "1" ] && FAST=1

# 未指定范围 → 三域全跑
if [ "$EXPLICIT_SCOPE" -eq 0 ]; then
  RUN_BACKEND=1; RUN_FRONTEND=1; RUN_SCRIPTS=1
fi

RESULT_NAMES=()
RESULT_CODES=()
OVERALL=0

run_step() {
  local name="$1" script="$2"
  shift 2
  echo
  echo "──────────────────────────────────────────────────────────────"
  echo "  ${name}"
  echo "──────────────────────────────────────────────────────────────"
  if [ ! -x "$script" ]; then
    echo "[FAIL] 脚本不存在或不可执行：${script}"
    RESULT_NAMES+=("$name"); RESULT_CODES+=(1); OVERALL=1
    return
  fi
  # 直接执行，不接管道：保留子脚本真实退出码
  "$script" "$@"
  local code=$?
  RESULT_NAMES+=("$name")
  RESULT_CODES+=("$code")
  [ "$code" -ne 0 ] && OVERALL=1
  return 0
}

echo "=============================================================="
echo " 质量门禁  verify.sh"
echo " 仓库：${ROOT_DIR}"
echo " 模式：$([ "$FAST" -eq 1 ] && echo '快速（跳过测试/构建）' || echo '完整')"
echo "=============================================================="

[ "$RUN_SCRIPTS" -eq 1 ] && run_step "脚本检查" "${SCRIPT_DIR}/check-scripts.sh"

if [ "$RUN_BACKEND" -eq 1 ]; then
  if [ "$FAST" -eq 1 ]; then
    run_step "后端检查" "${SCRIPT_DIR}/check-backend.sh" --fast
  else
    run_step "后端检查" "${SCRIPT_DIR}/check-backend.sh"
  fi
fi

if [ "$RUN_FRONTEND" -eq 1 ]; then
  if [ "$FAST" -eq 1 ]; then
    run_step "前端检查" "${SCRIPT_DIR}/check-frontend.sh" --fast
  else
    run_step "前端检查" "${SCRIPT_DIR}/check-frontend.sh"
  fi
fi

# ---------- 汇总 ----------
echo
echo "=============================================================="
echo " 门禁汇总"
echo "=============================================================="
i=0
while [ "$i" -lt "${#RESULT_NAMES[@]}" ]; do
  name="${RESULT_NAMES[$i]}"
  code="${RESULT_CODES[$i]}"
  if [ "$code" -eq 0 ]; then
    printf '  [ OK ] %s\n' "$name"
  else
    printf '  [FAIL] %s（退出码 %s）\n' "$name" "$code"
  fi
  i=$((i + 1))
done

if [ "$OVERALL" -eq 0 ]; then
  echo
  echo "  结论：全部通过 ✅"
  echo "=============================================================="
  exit 0
fi

echo
echo "  结论：存在失败项，禁止合并 ❌"
echo "  排查顺序：脚本检查 → 后端 → 前端（依赖关系自下而上）"
echo "  规范说明：docs/engineering/QUALITY_GATE.md"
echo "=============================================================="
exit 1
