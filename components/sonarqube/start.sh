#!/bin/bash

# SonarQube 启动脚本
# 用于快速启动 SonarQube 服务并进行初始配置检查

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "启动 SonarQube 代码质量分析平台"
echo "=========================================="

# 检查是否已经在运行
if docker ps | grep -q sonarqube; then
    echo "⚠️  SonarQube 已经在运行"
    echo ""
    docker ps | grep sonarqube
    echo ""
    echo "访问地址: http://localhost:9000"
    echo "如需重启，请先运行: docker compose down"
    exit 0
fi

# 启动服务
echo "启动 SonarQube 服务..."
docker compose up -d

echo ""
echo "等待服务启动（SonarQube 首次启动需要 2-3 分钟）..."
echo ""

# 等待 SonarQube 就绪
MAX_RETRIES=60
RETRY_COUNT=0
SONARQUBE_READY=false

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    # 检查容器是否在运行
    if ! docker ps | grep -q sonarqube; then
        echo "❌ 错误: SonarQube 容器未运行"
        echo "查看日志:"
        docker compose logs --tail=50 sonarqube
        exit 1
    fi

    # 检查健康状态
    HEALTH_STATUS=$(docker inspect --format='{{.State.Health.Status}}' sonarqube 2>/dev/null || echo "none")

    if [ "$HEALTH_STATUS" = "healthy" ]; then
        SONARQUBE_READY=true
        break
    fi

    RETRY_COUNT=$((RETRY_COUNT + 1))

    # 每 10 秒显示一次进度
    if [ $((RETRY_COUNT % 5)) -eq 0 ]; then
        echo "等待中... ($RETRY_COUNT/$MAX_RETRIES) - 健康状态: $HEALTH_STATUS"
        echo "提示: 首次启动需要初始化数据库和 Elasticsearch"
    fi

    sleep 2
done

echo ""

if [ "$SONARQUBE_READY" = true ]; then
    echo "=========================================="
    echo "✅ SonarQube 启动成功！"
    echo "=========================================="
    echo ""
    echo "服务信息:"
    docker compose ps
    echo ""
    echo "访问地址: http://localhost:9000"
    echo "默认账号: admin"
    echo "默认密码: admin"
    echo ""
    echo "首次登录后请立即修改默认密码！"
    echo ""
    echo "=========================================="
    echo "下一步操作:"
    echo "=========================================="
    echo "1. 访问 http://localhost:9000 并登录"
    echo "2. 修改默认密码"
    echo "3. 创建项目分析 Token:"
    echo "   - 点击右上角头像 → My Account → Security"
    echo "   - Token Name: jenkins-todoapp-backend"
    echo "   - Type: Project Analysis Token"
    echo "   - 点击 Generate 并保存 Token"
    echo "4. 在 Jenkins 中配置 SonarQube:"
    echo "   - Manage Jenkins → Configure System → SonarQube servers"
    echo "   - Name: sonarqube-server"
    echo "   - Server URL: http://sonarqube:9000"
    echo "   - 添加 Token 凭证"
    echo ""
    echo "查看详细文档: components/sonarqube/README.md"
    echo "=========================================="
else
    echo "=========================================="
    echo "❌ SonarQube 启动超时"
    echo "=========================================="
    echo ""
    echo "请检查日志:"
    docker compose logs --tail=100 sonarqube
    echo ""
    echo "常见问题:"
    echo "1. 内存不足 - SonarQube 需要至少 2GB RAM"
    echo "2. vm.max_map_count 设置过低（Linux）"
    echo "   解决方案: sudo sysctl -w vm.max_map_count=262144"
    echo ""
    exit 1
fi
