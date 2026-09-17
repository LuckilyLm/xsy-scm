#!/usr/bin/env bash
# =============================================================================
# XSY-SCM 本地 Docker 一键部署脚本（Git Bash / macOS / Linux）
#
#   ./deploy.sh build     仅构建镜像（后端 Maven、前端 Node，均在容器内完成）
#   ./deploy.sh up        构建并后台启动，等待健康检查通过
#   ./deploy.sh down      停止并移除容器
#   ./deploy.sh restart   重启
#   ./deploy.sh logs      实时查看日志（Ctrl+C 退出）
#   ./deploy.sh status    查看容器状态与健康检查
#   ./deploy.sh clean     停止容器并清理本项目镜像（危险：会删除本地镜像）
#   ./deploy.sh db        额外启动容器版 MySQL/Redis（docker-compose.db.yml）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}/.."

COMPOSE_FILE="docker-compose.yml"
DB_FILE="docker-compose.db.yml"

# 自动识别 docker compose（插件）或 docker-compose（独立二进制）
if docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  echo "[错误] 未找到 docker compose，请先安装/启动 Docker Desktop。"
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "[错误] Docker 守护进程未运行，请先启动 Docker Desktop。"
  exit 1
fi

CMD="${1:-up}"
EXTRA_ARGS="${@:2}"

# 读取 .env 中的端口用于提示
WEB_PORT="$(grep -E '^WEB_PORT=' .env 2>/dev/null | cut -d= -f2 || echo 8080)"
SERVER_PORT="$(grep -E '^SERVER_PORT=' .env 2>/dev/null | cut -d= -f2 || echo 1024)"
WEB_PORT="${WEB_PORT:-8080}"
SERVER_PORT="${SERVER_PORT:-1024}"

wait_healthy() {
  echo ">> 等待服务健康检查通过（最长 180s）..."
  for i in $(seq 1 60); do
    backend_state="$(docker inspect -f '{{.State.Health.Status}}' xsy-scm-server 2>/dev/null || echo missing)"
    web_state="$(docker inspect -f '{{.State.Health.Status}}' xsy-scm-web 2>/dev/null || echo missing)"
    if [ "$backend_state" = "healthy" ] && [ "$web_state" = "healthy" ]; then
      echo ">> 全部服务健康 ✔"
      return 0
    fi
    if [ "$backend_state" = "unhealthy" ] || [ "$web_state" = "unhealthy" ]; then
      echo "[警告] 服务健康检查未通过，请查看日志：./deploy.sh logs"
      return 1
    fi
    sleep 3
  done
  echo "[警告] 等待超时，请自行确认：./deploy.sh status"
}

case "$CMD" in
  build)
    $DC -f "$COMPOSE_FILE" build $EXTRA_ARGS
    ;;
  up)
    $DC -f "$COMPOSE_FILE" up -d --build $EXTRA_ARGS
    wait_healthy || true
    echo
    echo "管理后台：http://localhost:${WEB_PORT}/admin/"
    echo "移动端H5：http://localhost:${WEB_PORT}/h5/"
    echo "后端接口：http://localhost:${SERVER_PORT}"
    ;;
  down)
    $DC -f "$COMPOSE_FILE" down $EXTRA_ARGS
    ;;
  restart)
    $DC -f "$COMPOSE_FILE" restart $EXTRA_ARGS
    ;;
  logs)
    $DC -f "$COMPOSE_FILE" logs -f --tail=200 $EXTRA_ARGS
    ;;
  status)
    $DC -f "$COMPOSE_FILE" ps
    ;;
  clean)
    $DC -f "$COMPOSE_FILE" down --rmi local -v
    ;;
  db)
    $DC -f "$COMPOSE_FILE" -f "$DB_FILE" up -d --build $EXTRA_ARGS
    echo "容器版 MySQL/Redis 已启动，记得把 .env 中 MYSQL/REDIS 地址改为容器名 mysql / redis"
    ;;
  *)
    sed -n '2,16p' "$0"
    ;;
esac
