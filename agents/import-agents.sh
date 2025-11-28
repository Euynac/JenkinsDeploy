#!/bin/bash
# Jenkins Agent 镜像导入脚本（内网使用）

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

VERSION="1.0"

log_info "====== Jenkins Agent 镜像导入脚本 ======"
log_info "版本: ${VERSION}"
log_info "导入日期: $(date +'%Y-%m-%d %H:%M:%S')"
echo ""

# 检查 tar 文件是否存在
TAR_FILES=(
    "jenkins-agent-dotnet-${VERSION}.tar"
    "jenkins-agent-java-${VERSION}.tar"
    "jenkins-agent-vue-${VERSION}.tar"
)

log_step "检查镜像文件..."
for tar_file in "${TAR_FILES[@]}"; do
    if [ ! -f "$tar_file" ]; then
        log_error "找不到文件: $tar_file"
        log_error "请确保所有 tar 文件都在当前目录"
        exit 1
    fi
    log_info "✓ $tar_file"
done
echo ""

# 验证 MD5
log_step "验证 MD5 校验和..."
for tar_file in "${TAR_FILES[@]}"; do
    if [ -f "${tar_file}.md5" ]; then
        log_info "验证 ${tar_file}..."
        if md5sum -c "${tar_file}.md5" > /dev/null 2>&1; then
            log_info "✅ MD5 验证通过"
        else
            log_error "❌ MD5 验证失败：${tar_file}"
            log_error "文件可能损坏，请重新上传"
            exit 1
        fi
    else
        log_warn "未找到 MD5 文件：${tar_file}.md5，跳过验证"
    fi
done
echo ""

# 导入镜像
log_step "导入镜像..."

import_image() {
    local tar_file=$1
    local image_name=$2

    log_info "导入 ${tar_file}..."

    if docker load -i "${tar_file}"; then
        log_info "✅ ${image_name} 导入成功"
    else
        log_error "❌ ${image_name} 导入失败"
        exit 1
    fi
    echo ""
}

import_image "jenkins-agent-dotnet-${VERSION}.tar" "jenkins-agent-dotnet:${VERSION}"
import_image "jenkins-agent-java-${VERSION}.tar" "jenkins-agent-java:${VERSION}"
import_image "jenkins-agent-vue-${VERSION}.tar" "jenkins-agent-vue:${VERSION}"

# 验证镜像
log_step "验证导入的镜像..."
echo ""

docker images | grep jenkins-agent

echo ""
log_info "====== 导入完成 ======"
echo ""

# 显示镜像信息
log_info "已导入的镜像："
for image in "jenkins-agent-dotnet:${VERSION}" "jenkins-agent-java:${VERSION}" "jenkins-agent-vue:${VERSION}"; do
    SIZE=$(docker images "${image}" --format "{{.Size}}")
    log_info "  - ${image} (${SIZE})"
done

echo ""
log_info "📋 下一步："
echo ""
echo "1️⃣  在 Jenkins Web UI 中创建 Agent 节点："
echo "   Manage Jenkins → Nodes → New Node"
echo ""
echo "2️⃣  配置 Agent："
echo "   - Node name: agent-dotnet-01 (或其他名称)"
echo "   - Labels: dotnet"
echo "   - Launch method: Launch agent by connecting it to the controller"
echo "   - 记录 Secret"
echo ""
echo "3️⃣  修改 docker-compose-agents.yml："
echo "   - 填入 JENKINS_SECRET"
echo "   - 填入 JENKINS_URL（Master 地址）"
echo ""
echo "4️⃣  启动 Agent 容器："
echo "   docker-compose -f docker-compose-agents.yml up -d"
echo ""
echo "5️⃣  验证 Agent 连接："
echo "   在 Jenkins Web UI 中查看节点状态"
echo ""

log_info "详细配置指南请查看: DOCKER_AGENT_GUIDE.md"
