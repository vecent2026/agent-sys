#!/usr/bin/env bash
set -euo pipefail

# 发布顺序脚本（面向 docker-compose 本地/测试环境）
# 用法:
#   bash scripts/release/publish_main_tenant.sh

ROOT_DIR="/Users/wenpeilin/trae/智能体管理"
DB_NAME="admin_system"
DB_USER="root"
DB_PASS="root"
MYSQL_CONTAINER="mysql"

ts="$(date +%Y%m%d_%H%M%S)"
backup_file="${ROOT_DIR}/scripts/release/backup_${DB_NAME}_${ts}.sql"

echo "[1/8] 检查 main 分支状态"
git -C "${ROOT_DIR}" rev-parse --abbrev-ref HEAD
git -C "${ROOT_DIR}" status --short

echo "[2/8] 备份数据库 -> ${backup_file}"
docker exec -i "${MYSQL_CONTAINER}" mysqldump -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" > "${backup_file}"

echo "[3/8] 执行数据库对齐 SQL"
docker exec -i "${MYSQL_CONTAINER}" mysql -u"${DB_USER}" -p"${DB_PASS}" -D "${DB_NAME}" < "${ROOT_DIR}/scripts/release/align_super_admin_model.sql"

echo "[4/8] 执行发布前检查 SQL"
docker exec -i "${MYSQL_CONTAINER}" mysql -u"${DB_USER}" -p"${DB_PASS}" -D "${DB_NAME}" < "${ROOT_DIR}/scripts/release/tenant_alignment_check.sql"

echo "[5/8] 构建 admin-service 与 frontend"
docker compose -f "${ROOT_DIR}/docker-compose.yml" build admin-service frontend

echo "[6/8] 滚动重启 admin-service（先后端）"
docker compose -f "${ROOT_DIR}/docker-compose.yml" up -d admin-service

echo "[7/8] 重启 frontend 与 nginx-gateway（后前端/网关）"
docker compose -f "${ROOT_DIR}/docker-compose.yml" up -d frontend nginx-gateway

echo "[8/8] 健康检查"
curl -fsS http://localhost:8081/actuator/health >/dev/null && echo "admin-service: OK"
curl -fsS http://localhost:8082/actuator/health >/dev/null && echo "user-service: OK"
curl -fsS http://localhost/ >/dev/null && echo "nginx-gateway: OK"

echo "发布完成。"
