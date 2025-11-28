#!/bin/bash

# 测试运行脚本
# 用法: ./run_tests.sh [选项]

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== TodoApp Backend API E2E 测试 ===${NC}\n"

# 检查 Python
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}错误: 未找到 python3${NC}"
    exit 1
fi

# 检查虚拟环境
if [ ! -d "venv" ]; then
    echo -e "${YELLOW}未找到虚拟环境，正在创建...${NC}"
    ./setup_venv.sh
fi

# 激活虚拟环境
echo -e "${YELLOW}激活虚拟环境...${NC}"
source venv/bin/activate

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: 未找到 docker${NC}"
    exit 1
fi

# 检测操作系统并确定 Docker Compose 命令
detect_docker_compose_cmd() {
    local os_type=$(uname -s)
    if [ "$os_type" = "Darwin" ]; then
        # macOS: 使用 docker compose (V2, 无连字符)
        if docker compose version &> /dev/null 2>&1; then
            echo "docker compose"
            return 0
        else
            echo -e "${RED}错误: 未找到 docker compose 命令${NC}" >&2
            echo -e "${YELLOW}提示: macOS 需要安装 Docker Desktop${NC}" >&2
            return 1
        fi
    else
        # Linux/Windows: 优先使用 docker-compose (V1, 有连字符)
        if command -v docker-compose &> /dev/null 2>&1; then
            echo "docker-compose"
            return 0
        elif docker compose version &> /dev/null 2>&1; then
            # 如果 docker-compose 不存在，尝试 docker compose (V2)
            echo "docker compose"
            return 0
        else
            echo -e "${RED}错误: 未找到 docker-compose 或 docker compose 命令${NC}" >&2
            return 1
        fi
    fi
}

# 检测并设置 Docker Compose 命令
if ! DOCKER_COMPOSE_CMD=$(detect_docker_compose_cmd); then
    exit 1
fi
echo -e "${GREEN}检测到 Docker Compose 命令: ${DOCKER_COMPOSE_CMD}${NC}"

# 启动测试数据库
echo -e "${YELLOW}启动测试数据库...${NC}"
${DOCKER_COMPOSE_CMD} -f docker-compose.test.yml up -d

# 等待数据库就绪
echo -e "${YELLOW}等待数据库就绪...${NC}"
sleep 5

# 检查数据库是否运行
if ! docker ps | grep -q todoapp-postgres-test; then
    echo -e "${RED}错误: 测试数据库启动失败${NC}"
    exit 1
fi

echo -e "${GREEN}测试数据库已启动${NC}\n"

# 检查 API 是否运行
echo -e "${YELLOW}检查 API 服务...${NC}"
API_URL="${API_BASE_URL:-http://localhost:5085}"
if curl -s -f "${API_URL}/swagger/index.html" > /dev/null 2>&1; then
    echo -e "${GREEN}API 服务正在运行${NC}\n"
else
    echo -e "${YELLOW}警告: API 服务未运行，请确保在另一个终端中启动 API${NC}"
    echo -e "${YELLOW}启动命令: cd ../todoapp-backend-api && dotnet run --urls ${API_URL}${NC}\n"
fi

# 运行测试（在虚拟环境中）
echo -e "${GREEN}运行测试...${NC}\n"

# 解析参数
if [ "$1" == "--allure" ]; then
    pytest --alluredir=test-results/allure-results
    echo -e "\n${GREEN}测试完成！生成 Allure 报告:${NC}"
    echo -e "${YELLOW}allure serve test-results/allure-results${NC}"
elif [ "$1" == "--html" ]; then
    pytest --html=test-results/report.html --self-contained-html
    echo -e "\n${GREEN}测试完成！查看报告: test-results/report.html${NC}"
else
    pytest "$@"
fi

# 停用虚拟环境
deactivate

# 询问是否停止数据库
echo ""
read -p "是否停止测试数据库? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}停止测试数据库...${NC}"
    ${DOCKER_COMPOSE_CMD} -f docker-compose.test.yml down -v
    echo -e "${GREEN}测试数据库已停止${NC}"
fi

