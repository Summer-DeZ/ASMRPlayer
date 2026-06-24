#!/usr/bin/env bash
#
# ui-probe.sh — ASMRPlayer 内部 UI Probe 辅助脚本
#
# 把 uiProbe 构建变体的常用点选取证命令固化成子命令，配合应用内右上角
# “UI 探针”按钮，把一次可见点击映射成 selected.id / label / sourceHint /
# bounds / metadata，便于定位 Compose 源文件和业务对象。
#
# 详细工作流见 DOCS/README.md「UI Probe 工作流」。
#
# 用法:
#   scripts/ui-probe.sh [子命令] [参数]
#
# 子命令:
#   read            读取并美化最近一次点选结果 latest-selection.json（默认）
#   field <key>     只取某个字段，如 selected.id / selected.sourceHint
#   watch           等待下一次点选写入后自动打印（轮询文件 mtime）
#   shot [路径]     截图到指定路径（默认 /tmp/asrm-ui-probe-selection.png）
#   log             实时跟随 ASRM_UI_PROBE Logcat（Ctrl-C 退出）
#   open            在设备上拉起 UI Probe 应用
#   install         构建并安装 uiProbe 包，然后拉起（不覆盖正式包）
#   help            显示本帮助
#
# 环境变量:
#   ADB             adb 可执行路径（默认 adb）
#   ANDROID_SERIAL  目标设备序列号（多设备时使用，等同 adb -s）
#   GRADLE_USER_HOME 透传给 gradlew（install 子命令）

set -euo pipefail

PKG="io.github.summerdez.asmrplayer.uiprobe"
LOG_TAG="ASRM_UI_PROBE"
SELECTION_PATH="files/ui-probe/latest-selection.json"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ADB="${ADB:-adb}"

die() {
  echo "ui-probe: $*" >&2
  exit 1
}

require_device() {
  command -v "$ADB" >/dev/null 2>&1 || die "找不到 adb（可用 ADB=路径 指定）"
  local count
  count="$("$ADB" devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')"
  [ "$count" -ge 1 ] || die "没有处于 device 状态的设备，请先启动模拟器或连接真机"
  if [ "$count" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
    die "检测到多台设备，请用 ANDROID_SERIAL=序列号 指定目标"
  fi
}

require_installed() {
  "$ADB" shell pm list packages | grep -q "^package:${PKG}$" \
    || die "未安装 $PKG，请先执行: scripts/ui-probe.sh install"
}

# 把 JSON 通过 jq / python3 美化，二者都没有就原样输出
pretty_json() {
  if command -v jq >/dev/null 2>&1; then
    jq .
  elif command -v python3 >/dev/null 2>&1; then
    python3 -m json.tool
  else
    cat
  fi
}

cat_selection() {
  "$ADB" shell run-as "$PKG" cat "$SELECTION_PATH" 2>/dev/null
}

cmd_read() {
  require_device
  require_installed
  local json
  json="$(cat_selection || true)"
  if [ -z "${json//[$'\t\r\n ']/}" ]; then
    die "还没有点选结果。请在应用内点右上角“UI 探针”，再点目标区域后重试。"
  fi
  printf '%s\n' "$json" | pretty_json
}

cmd_field() {
  local key="${1:-}"
  [ -n "$key" ] || die "用法: scripts/ui-probe.sh field <字段，如 selected.id>"
  command -v jq >/dev/null 2>&1 || die "field 子命令需要安装 jq"
  require_device
  require_installed
  cat_selection | jq -r ".${key}"
}

cmd_watch() {
  require_device
  require_installed
  echo "等待下一次点选（在应用内点“UI 探针”后点目标区域）… Ctrl-C 退出" >&2
  local prev cur
  prev="$("$ADB" shell run-as "$PKG" stat -c %Y "$SELECTION_PATH" 2>/dev/null || echo 0)"
  while true; do
    cur="$("$ADB" shell run-as "$PKG" stat -c %Y "$SELECTION_PATH" 2>/dev/null || echo 0)"
    if [ "$cur" != "$prev" ] && [ "$cur" != "0" ]; then
      echo "── 新点选 $(date '+%H:%M:%S') ──" >&2
      cat_selection | pretty_json
      prev="$cur"
    fi
    sleep 1
  done
}

cmd_shot() {
  require_device
  local out="${1:-/tmp/asrm-ui-probe-selection.png}"
  "$ADB" exec-out screencap -p > "$out"
  echo "已保存截图: $out"
}

cmd_log() {
  require_device
  "$ADB" logcat -s "$LOG_TAG"
}

cmd_open() {
  require_device
  require_installed
  "$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
  echo "已拉起 $PKG"
}

cmd_install() {
  require_device
  ( cd "$REPO_ROOT" && ./gradlew :app:installUiProbe )
  cmd_open
}

main() {
  local sub="${1:-read}"
  [ $# -gt 0 ] && shift || true
  case "$sub" in
    read)    cmd_read "$@" ;;
    field)   cmd_field "$@" ;;
    watch)   cmd_watch "$@" ;;
    shot)    cmd_shot "$@" ;;
    log)     cmd_log "$@" ;;
    open)    cmd_open "$@" ;;
    install) cmd_install "$@" ;;
    help|-h|--help) awk 'NR>1 { if ($0 !~ /^#/) exit; sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}" ;;
    *) die "未知子命令: $sub（用 help 查看用法）" ;;
  esac
}

main "$@"
