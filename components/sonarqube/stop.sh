#!/bin/bash

# SonarQube 停止脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "��止 SonarQube 服务"
echo "=========================================="

# 检查是否在运行
if ! docker ps | grep -q sonarqube; then
    echo "ℹ️  SonarQube 未在运行"
    exit 0
fi

# 停止服务
echo "停止 SonarQube 和数据库..."
docker compose down

echo ""
echo "✅ SonarQube 已停止"
echo ""
echo "注意: 数据已保存在 Docker volumes 中"
echo "如需完全删除数据，请运行:"
echo "  docker compose down -v"
echo ""
