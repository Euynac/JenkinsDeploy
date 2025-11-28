#!/bin/bash
set -e

# 获取 Docker socket 的组 ID
DOCKER_SOCK_GID=$(stat -c '%g' /var/run/docker.sock 2>/dev/null || echo "")

if [ -n "$DOCKER_SOCK_GID" ]; then
    echo "检测到 Docker socket GID: $DOCKER_SOCK_GID"

    # 检查是否已存在该 GID 的组
    if ! getent group $DOCKER_SOCK_GID > /dev/null; then
        echo "创建 docker-host 组 (GID: $DOCKER_SOCK_GID)"
        groupadd -g $DOCKER_SOCK_GID docker-host
    fi

    # 将 jenkins 用户添加到该组
    echo "将 jenkins 用户添加到 GID $DOCKER_SOCK_GID 组"
    usermod -aG $DOCKER_SOCK_GID jenkins

    echo "✅ Docker 权限配置完成"
else
    echo "⚠️  未检测到 Docker socket，跳过 Docker 配置"
fi

# 验证环境
echo "=========================================="
echo "Agent Docker 环境信息"
echo "=========================================="
echo "Python 版本: $(python3 --version 2>&1)"
echo "Docker 版本: $(docker --version 2>&1)"
echo "Git 版本: $(git --version 2>&1)"
echo "当前用户组: $(groups jenkins 2>&1)"
echo "=========================================="

# 执行原始的 jenkins-agent 入口点
exec /usr/local/bin/jenkins-agent "$@"
