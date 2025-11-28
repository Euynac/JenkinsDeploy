#!/bin/bash
# Docker 权限检查脚本
# 用于验证 Jenkins agent 用户是否有权限访问 Docker

echo "=========================================="
echo "Docker 权限检查脚本"
echo "=========================================="
echo ""

echo "1. 当前用户:"
whoami
echo ""

echo "2. 用户组信息:"
id
echo ""

echo "3. 用户所属组:"
groups
echo ""

echo "4. Docker socket 权限:"
if [ -e /var/run/docker.sock ]; then
    ls -la /var/run/docker.sock
    echo ""
    echo "   Socket 所有者: $(stat -c '%U:%G' /var/run/docker.sock)"
    echo "   Socket 权限: $(stat -c '%a' /var/run/docker.sock)"
else
    echo "   ❌ Docker socket 不存在: /var/run/docker.sock"
fi
echo ""

echo "5. Docker 组信息:"
if getent group docker > /dev/null 2>&1; then
    getent group docker
    echo ""
    echo "   Docker 组成员:"
    getent group docker | cut -d: -f4 | tr ',' '\n' | sed 's/^/     - /'
else
    echo "   ❌ Docker 组不存在"
fi
echo ""

echo "6. 检查用户是否在 docker 组中:"
if groups | grep -q docker; then
    echo "   ✅ 当前用户在 docker 组中"
else
    echo "   ❌ 当前用户不在 docker 组中"
    echo "   解决方案: sudo usermod -aG docker $(whoami)"
    echo "   然后需要重新登录或执行: newgrp docker"
fi
echo ""

echo "7. 测试 Docker 命令:"
if docker ps &>/dev/null; then
    echo "   ✅ Docker 权限正常"
    echo ""
    echo "   容器列表:"
    docker ps
else
    echo "   ❌ Docker 权限不足"
    echo ""
    echo "   错误信息:"
    docker ps 2>&1 | head -5
    echo ""
    echo "   可能的原因:"
    echo "   1. 用户不在 docker 组中"
    echo "   2. 组权限未生效（需要重新登录或执行 newgrp docker）"
    echo "   3. Docker socket 权限不正确"
    echo ""
    echo "   解决步骤:"
    echo "   1. sudo usermod -aG docker $(whoami)"
    echo "   2. newgrp docker  # 或重新登录"
    echo "   3. sudo chown root:docker /var/run/docker.sock"
    echo "   4. sudo chmod 660 /var/run/docker.sock"
fi
echo ""

echo "8. 测试 Docker Compose 命令:"
if docker compose version &>/dev/null 2>&1; then
    echo "   ✅ Docker Compose V2 可用"
    docker compose version
elif command -v docker-compose &>/dev/null 2>&1; then
    echo "   ✅ Docker Compose V1 可用"
    docker-compose --version
else
    echo "   ❌ Docker Compose 未找到"
    echo "   解决方案: sudo apt install docker-compose"
fi
echo ""

echo "=========================================="
echo "检查完成"
echo "=========================================="

