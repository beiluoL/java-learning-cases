#!/usr/bin/env bash
#
# 脚本静态检查（门禁子步骤，由 verify.sh 调用）
#
# 用途     ：检查全部 Shell 脚本的语法、安全红线、bash 3.2 兼容性
# 用法     ：scripts/check-scripts.sh
# 参数     ：无
# 环境变量 ：无
# 执行前提 ：在仓库根目录或有 bash/perl/grep 的任意目录（脚本自行定位仓库根）
# 副作用   ：只读。不修改任何文件
# 失败处理 ：任一检查命中即打印明细并以 1 退出；全部通过以 0 退出
#
# 设计说明：本脚本刻意不使用 set -e。
#   门禁需要「跑完所有检查再汇总」，第一个失败就退出会掩盖其余问题。
#   改为一律执行到末尾，用 FAILED 计数决定退出码。
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT_DIR"

FAILED=0
CHECKED=0

# ---------- 输出工具 ----------
info() { echo "[INFO] $*"; }
ok()   { echo "[ OK ] $*"; }
fail() { echo "[FAIL] $*"; FAILED=$((FAILED + 1)); }

# ---------- 检测工具 ----------
# 匹配关键词并排除整行注释；用可移植词边界避免 pkillx / killall2 误命中。
# 注意：macOS BSD grep 不支持 \b，也不支持 BRE 的 \|，必须用 -E 且不用 \b。
_match_code() {
  local pattern="$1"
  shift
  grep -rnE "(^|[^A-Za-z0-9_])(${pattern})([^A-Za-z0-9_]|$)" "$@" 2>/dev/null \
    | grep -vE '(^|:)[0-9]+:[[:space:]]*#'
}

# ---------- 待检查文件清单 ----------
SH_FILES=()
for f in ./*.command; do [ -f "$f" ] && SH_FILES+=("$f"); done
for f in ./samples/*.sh; do [ -f "$f" ] && SH_FILES+=("$f"); done
for f in ./scripts/*.sh;  do [ -f "$f" ] && SH_FILES+=("$f"); done

BAT_FILES=()
for f in ./*.bat; do [ -f "$f" ] && BAT_FILES+=("$f"); done

# 模式扫描必须排除本脚本自身：它必然包含 pkill / kill -9 / rm -rf 等字符串
# （既作为检测模式，也作为提示文案），否则会自我误报。
# 语法/严格模式/可执行位检查仍包含它。
SCAN_SH=()
for f in "${SH_FILES[@]}"; do
  [ "$f" = "./scripts/check-scripts.sh" ] && continue
  SCAN_SH+=("$f")
done

if [ "${#SH_FILES[@]}" -eq 0 ] && [ "${#BAT_FILES[@]}" -eq 0 ]; then
  fail "未找到任何脚本文件（当前目录：${ROOT_DIR}）"
  echo
  echo "===== 脚本检查汇总：未找到脚本，视为失败 ====="
  exit 1
fi

info "待检查：${#SH_FILES[@]} 个 Shell 脚本，${#BAT_FILES[@]} 个 Windows 批处理"

# ---------- 1. 语法（bash -n） ----------
for f in "${SH_FILES[@]}"; do
  CHECKED=$((CHECKED + 1))
  if bash -n "$f" 2>/dev/null; then
    ok "语法通过：$f"
  else
    fail "语法错误：$f"
    bash -n "$f" 2>&1 | sed 's/^/       /'
  fi
done

# ---------- 2. shebang 与严格模式 ----------
for f in "${SH_FILES[@]}"; do
  CHECKED=$((CHECKED + 1))
  head1="$(head -1 "$f")"
  if [ "$head1" != "#!/usr/bin/env bash" ]; then
    fail "shebang 应为 #!/usr/bin/env bash：${f}（当前：${head1}）"
  else
    ok "shebang 正确：$f"
  fi

  # 探测型脚本（samples/verify-*）允许无 -e，但必须有 pipefail 且写明原因
  if grep -q "pipefail" "$f"; then
    ok "严格模式：$f"
  else
    fail "缺少 pipefail：$f"
  fi
done

# ---------- 3. 安全红线 ----------
echo
info "检查安全红线（kill -9 / pkill / killall / rm -rf / 硬编码路径 / 明文密钥）"

HITS="$(_match_code 'pkill|killall|kill[[:space:]]+-9' "${SCAN_SH[@]}" "${BAT_FILES[@]}")"
if [ -n "$HITS" ]; then
  # 提示文案（echo 中声明「未使用 kill -9」）属已知误报，单独提示而非判失败
  REAL_VIOLATION=0
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    case "$line" in
      *echo*) echo "[WARN] 疑似提示文案（需人工确认，不计失败）：$line" ;;
      *)      fail "禁止使用 kill -9 / pkill / killall：$line"; REAL_VIOLATION=1 ;;
    esac
  done <<< "$HITS"
  [ "$REAL_VIOLATION" -eq 0 ] && ok "kill 类：无实际调用（命中项均为提示文案）"
else
  ok "kill 类：0 处"
fi

HITS="$(_match_code 'rm[[:space:]]+-rf([[:space:]]|$)' "${SCAN_SH[@]}" "${BAT_FILES[@]}")"
if [ -n "$HITS" ]; then
  fail "禁止使用 rm -rf："; echo "$HITS" | sed 's/^/       /'
else
  ok "rm -rf：0 处"
fi

HITS="$(_match_code '/Users/' "${SCAN_SH[@]}" "${BAT_FILES[@]}")"
if [ -n "$HITS" ]; then
  fail "禁止硬编码用户绝对路径："; echo "$HITS" | sed 's/^/       /'
else
  ok "硬编码 /Users/ 路径：0 处"
fi

HITS="$(_match_code 'api[_-]?key|password[[:space:]]*=' "${SCAN_SH[@]}" "${BAT_FILES[@]}")"
if [ -n "$HITS" ]; then
  fail "疑似明文密钥："; echo "$HITS" | sed 's/^/       /'
else
  ok "明文密钥：0 处"
fi

# ---------- 4. bash 3.2 兼容性：$VAR 后紧跟非 ASCII ----------
# macOS 自带 bash 3.2 会把中文字符首字节并入变量名，导致 unbound variable。
# grep -P 在 BSD grep 上不可用（invalid option -- P），只能用 perl。
echo
info "检查 bash 3.2 多字节变量界定（\$VAR 后紧跟中文）"
if ! command -v perl >/dev/null 2>&1; then
  echo "[WARN] 未安装 perl，跳过该项检查"
else
  # perl 的 $. 在多个文件之间不会自动重置，必须 close ARGV if eof，
  # 否则第 2 个文件起行号会累加（实测把 186 行的文件报成 660 行）。
  MB_HITS="$(perl -ne 'print "$ARGV:$.: $_" if /\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7F]/; close ARGV if eof' "${SCAN_SH[@]}" 2>/dev/null)"
  if [ -n "$MB_HITS" ]; then
    fail "变量后紧跟非 ASCII 字符（请改为 \${VAR}）："; echo "$MB_HITS" | sed 's/^/       /'
  else
    ok "多字节变量界定：0 处"
  fi
fi

# ---------- 5. Windows 批处理专项 ----------
if [ "${#BAT_FILES[@]}" -gt 0 ]; then
  echo
  info "检查 .bat（chcp 65001 / taskkill 用法）"
  for f in "${BAT_FILES[@]}"; do
    CHECKED=$((CHECKED + 1))
    if grep -q "chcp 65001" "$f"; then
      ok "UTF-8 代码页：$f"
    else
      fail "缺少 chcp 65001，中文会乱码：$f"
    fi
  done

  HITS="$(_match_code 'taskkill[[:space:]]+/IM' "${BAT_FILES[@]}")"
  if [ -n "$HITS" ]; then
    fail "禁止按镜像名杀进程（会误杀其他项目）："; echo "$HITS" | sed 's/^/       /'
  else
    ok "taskkill：未按镜像名查杀"
  fi
fi

# ---------- 6. 可执行位 ----------
echo
info "检查可执行位"
for f in ./*.command ./samples/*.sh ./scripts/*.sh; do
  [ -f "$f" ] || continue
  CHECKED=$((CHECKED + 1))
  if [ -x "$f" ]; then
    ok "可执行：$f"
  else
    fail "缺少可执行位（chmod +x 后提交）：$f"
  fi
done

# ---------- 汇总 ----------
echo
if [ "$FAILED" -eq 0 ]; then
  echo "===== 脚本检查通过：${CHECKED} 项检查全绿 ====="
  exit 0
fi
echo "===== 脚本检查失败：${FAILED} 项未通过（共 ${CHECKED} 项）====="
exit 1
