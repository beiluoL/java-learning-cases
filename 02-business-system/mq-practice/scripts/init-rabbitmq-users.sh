#!/usr/bin/env bash
# =====================================================================================
#  RabbitMQ 最小权限账号初始化（幂等，可重复执行）
#
#  为什么需要它：
#    默认 guest/guest 拥有全部权限，只适合本机开发。生产/多人环境应拆成
#      mq_app      业务账号：能读写业务 vhost（tag: management）
#      mq_monitor  监控账号：只能看（tag: monitoring），给运维台用
#    并禁用 guest 远程登录。
#
#  用法：
#    bash scripts/init-rabbitmq-users.sh                 # 使用默认口令（不推荐）
#    MQ_APP_PASSWORD='...' MQ_MONITOR_PASSWORD='...' bash scripts/init-rabbitmq-users.sh
#
#  执行后如何切到新账号：
#    RABBITMQ_USERNAME=mq_app RABBITMQ_PASSWORD='...' ./start.command
#    MQ_ADMIN_USERNAME=mq_monitor MQ_ADMIN_PASSWORD='...' ./start.command
# =====================================================================================
set -euo pipefail

CONTAINER="${RABBITMQ_CONTAINER:-mq-practice-rabbitmq}"
VHOST="${RABBITMQ_VHOST:-/}"
APP_USER="${MQ_APP_USER:-mq_app}"
MONITOR_USER="${MQ_MONITOR_USER:-mq_monitor}"
APP_PASSWORD="${MQ_APP_PASSWORD:-mq_app_ChangeMe123}"
MONITOR_PASSWORD="${MQ_MONITOR_PASSWORD:-mq_monitor_ChangeMe123}"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "[FAIL] 未找到运行中的容器 $CONTAINER，请先 docker compose up -d rabbitmq"
  exit 1
fi

ctl() { docker exec "$CONTAINER" rabbitmqctl "$@"; }

echo ">>> 创建/更新业务账号 $APP_USER"
ctl add_user "$APP_USER" "$APP_PASSWORD" >/dev/null 2>&1 || ctl change_password "$APP_USER" "$APP_PASSWORD" >/dev/null
ctl set_user_tags "$APP_USER" management >/dev/null
ctl set_permissions -p "$VHOST" "$APP_USER" ".*" ".*" ".*" >/dev/null
echo "[ OK ] $APP_USER → tag=management，vhost=${VHOST} 可配置/写/读"

echo ">>> 创建/更新只读账号 $MONITOR_USER"
ctl add_user "$MONITOR_USER" "$MONITOR_PASSWORD" >/dev/null 2>&1 || ctl change_password "$MONITOR_USER" "$MONITOR_PASSWORD" >/dev/null
ctl set_user_tags "$MONITOR_USER" monitoring >/dev/null
# 只给 read：能看队列消息，不能建队列、不能发消息、不能 purge
ctl set_permissions -p "$VHOST" "$MONITOR_USER" "^$" "^$" ".*" >/dev/null
echo "[ OK ] $MONITOR_USER → tag=monitoring，vhost=${VHOST} 只读"

echo ""
echo "当前用户："
ctl list_users
echo ""
echo "提示：确认业务能用新账号连上后，再删除 guest："
echo "  docker exec $CONTAINER rabbitmqctl delete_user guest"
