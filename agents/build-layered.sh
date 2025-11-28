#!/bin/bash
# 三层构建 Jenkins Agent 镜像
# 1. agent-base: 基础镜像（Python + 基础工具）
# 2. agent-docker: Docker 层（基于 base，添加 Docker CLI）
# 3. agent-dotnet: .NET 层（基于 docker，添加 .NET SDK）

set -e

echo "=========================================="
echo "开始三层构建 Jenkins Agent 镜像"
echo "=========================================="

# 进入脚本所在目录
cd "$(dirname "$0")"

echo ""
echo "Step 1/3: 构建 agent-base 基础镜像（Python + 基础工具）..."
echo "=========================================="
docker build -f Dockerfile.agent-base -t jenkins-agent-base:1.0 .

if [ $? -eq 0 ]; then
    echo "✅ agent-base 镜像构建成功"
    docker images | grep jenkins-agent-base | head -1
else
    echo "❌ agent-base 镜像构建失败"
    exit 1
fi

echo ""
echo "Step 2/3: 构建 agent-docker 镜像（基于 agent-base，添加 Docker CLI）..."
echo "=========================================="
docker build -f Dockerfile.agent-docker -t jenkins-agent-docker:1.0 .

if [ $? -eq 0 ]; then
    echo "✅ agent-docker 镜像构建成功"
    docker images | grep jenkins-agent-docker | head -1
else
    echo "❌ agent-docker 镜像构建失败"
    exit 1
fi

echo ""
echo "Step 3/3: 构建 agent-dotnet 镜像（基于 agent-docker，添加 .NET SDK）..."
echo "=========================================="
docker build -f Dockerfile.dotnet -t jenkins-agent-dotnet:2.0 .

if [ $? -eq 0 ]; then
    echo "✅ agent-dotnet 镜像构建成功"
    docker images | grep jenkins-agent-dotnet | head -1
else
    echo "❌ agent-dotnet 镜像构建失败"
    exit 1
fi

echo ""
echo "=========================================="
echo "✅ 所有镜像构建完成！"
echo "=========================================="
echo "镜像列表:"
docker images | grep -E "jenkins-agent-(base|docker|dotnet)" | head -10

echo ""
echo "镜像层级关系:"
echo "  jenkins/inbound-agent:latest-jdk17"
echo "  └─ jenkins-agent-base:1.0 (Python + 基础工具)"
echo "     └─ jenkins-agent-docker:1.0 (Docker CLI DooD)"
echo "        └─ jenkins-agent-dotnet:2.0 (.NET SDK 8.0)"
echo ""
echo "使用场景:"
echo "  - agent-base:     仅需要 Python 环境（如 Python 项目构建）"
echo "  - agent-docker:   需要 Python + Docker（如镜像构建、docker-compose）"
echo "  - agent-dotnet:   需要 Python + Docker + .NET（如 .NET 项目 + E2E 测试 + 镜像打包）"
echo ""
echo "验证 agent-dotnet 环境:"
docker run --rm jenkins-agent-dotnet:2.0 bash -c "python3 --version && docker --version && dotnet --version"
