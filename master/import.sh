#!/bin/bash
# ==========================================
# Jenkins Master 镜像导入脚本（内网环境）
# ==========================================

set -e  # 遇到错误立即退出

# 配置变量
IMAGE_NAME="jenkins-master-offline"
IMAGE_VERSION="1.0"
IMAGE_TAG="${IMAGE_NAME}:${IMAGE_VERSION}"
IMPORT_FILE="${IMAGE_NAME}-${IMAGE_VERSION}.tar"

echo "=========================================="
echo "开始导入 Jenkins Master 离线镜像"
echo "镜像名称: ${IMAGE_TAG}"
echo "=========================================="

# 检查镜像文件
echo ""
echo "[1/3] 检查镜像文件..."
if [ ! -f "${IMPORT_FILE}" ]; then
    echo "错误: 镜像文件 ${IMPORT_FILE} 不存在"
    echo "请确保已将镜像文件传输到当前目录"
    exit 1
fi

# 校验文件完整性（如果存在MD5文件）
if [ -f "${IMPORT_FILE}.md5" ]; then
    echo "校验文件完整性..."
    md5sum -c ${IMPORT_FILE}.md5
    if [ $? -ne 0 ]; then
        echo "错误: 文件校验失败，文件可能已损坏"
        exit 1
    fi
    echo "✓ 文件校验通过"
else
    echo "警告: 未找到 MD5 校验文件，跳过完整性校验"
fi

# 导入镜像
echo ""
echo "[2/3] 导入 Docker 镜像..."
echo "提示: 导入可能需要几分钟，请耐心等待..."
docker load -i ${IMPORT_FILE}

if [ $? -ne 0 ]; then
    echo "错误: 镜像导入失败"
    exit 1
fi
echo "✓ 镜像导入成功"

# 验证镜像
echo ""
echo "[3/3] 验证镜像..."
docker images | grep ${IMAGE_NAME}

if docker images | grep -q ${IMAGE_NAME}; then
    IMAGE_SIZE=$(docker images ${IMAGE_TAG} --format "{{.Size}}")
    echo "✓ 镜像验证成功"
    echo "  镜像大小: ${IMAGE_SIZE}"
else
    echo "错误: 未找到导入的镜像"
    exit 1
fi

# 导入完成
echo ""
echo "=========================================="
echo "✓ 导入完成！"
echo "=========================================="
echo ""
echo "下一步操作："
echo "1. 启动 Jenkins Master："
echo "   docker-compose up -d"
echo ""
echo "2. 查看启动日志："
echo "   docker-compose logs -f"
echo ""
echo "3. 访问 Jenkins Web UI："
echo "   http://服务器IP:8080"
echo ""
echo "4. 获取初始管理员密码："
echo "   docker-compose exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword"
echo ""
echo "=========================================="
