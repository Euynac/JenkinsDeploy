#!/bin/bash
# ==========================================
# Jenkins Master 镜像构建脚本（外网环境）
# ==========================================

set -e  # 遇到错误立即退出

# 配置变量
IMAGE_NAME="jenkins-master-offline"
IMAGE_VERSION="1.0"
IMAGE_TAG="${IMAGE_NAME}:${IMAGE_VERSION}"
EXPORT_FILE="${IMAGE_NAME}-${IMAGE_VERSION}.tar"

echo "=========================================="
echo "开始构建 Jenkins Master 离线镜像"
echo "镜像名称: ${IMAGE_TAG}"
echo "=========================================="

# 检查必需文件
echo ""
echo "[1/5] 检查构建文件..."
if [ ! -f "Dockerfile" ]; then
    echo "错误: Dockerfile 文件不存在"
    exit 1
fi

if [ ! -f "plugins.txt" ]; then
    echo "错误: plugins.txt 文件不存在"
    exit 1
fi
echo "✓ 构建文件检查完成"

# 构建镜像
echo ""
echo "[2/5] 开始构建 Docker 镜像..."
echo "提示: 插件下载可能需要 10-30 分钟，请耐心等待..."
docker build -t ${IMAGE_TAG} .

if [ $? -ne 0 ]; then
    echo "错误: 镜像构建失败"
    exit 1
fi
echo "✓ 镜像构建成功"

# 验证镜像
echo ""
echo "[3/5] 验证镜像..."
docker images | grep ${IMAGE_NAME}
IMAGE_SIZE=$(docker images ${IMAGE_TAG} --format "{{.Size}}")
echo "✓ 镜像大小: ${IMAGE_SIZE}"

# 测试运行
echo ""
echo "[4/5] 测试镜像启动..."
docker run --rm -d --name jenkins-test ${IMAGE_TAG}
sleep 10

# 检查容器状态
if docker ps | grep -q jenkins-test; then
    echo "✓ 容器启动成功"
    docker logs jenkins-test | head -n 20
    docker stop jenkins-test
else
    echo "错误: 容器启动失败"
    docker logs jenkins-test
    exit 1
fi

# 导出镜像
echo ""
echo "[5/5] 导出镜像为 tar 文件..."
docker save -o ${EXPORT_FILE} ${IMAGE_TAG}

if [ -f ${EXPORT_FILE} ]; then
    FILE_SIZE=$(du -h ${EXPORT_FILE} | cut -f1)
    echo "✓ 镜像已导出"
    echo "  文件名: ${EXPORT_FILE}"
    echo "  文件大小: ${FILE_SIZE}"
else
    echo "错误: 镜像导出失败"
    exit 1
fi

# 生成校验文件
echo ""
echo "生成 MD5 校验文件..."
md5sum ${EXPORT_FILE} > ${EXPORT_FILE}.md5
echo "✓ 校验文件已生成: ${EXPORT_FILE}.md5"

# 构建完成
echo ""
echo "=========================================="
echo "✓ 构建完成！"
echo "=========================================="
echo ""
echo "下一步操作："
echo "1. 将以下文件传输到内网环境："
echo "   - ${EXPORT_FILE}"
echo "   - ${EXPORT_FILE}.md5"
echo ""
echo "2. 在内网环境执行："
echo "   bash import.sh"
echo ""
echo "3. 运行 Jenkins Master："
echo "   docker-compose up -d"
echo ""
echo "=========================================="
