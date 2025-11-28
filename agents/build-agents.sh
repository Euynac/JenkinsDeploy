#!/bin/bash
# 构建所有 Jenkins Agent 镜像并导出

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

# 版本号
VERSION="1.0"

# 镜像名称
IMAGES=(
    "jenkins-agent-dotnet:${VERSION}"
    "jenkins-agent-java:${VERSION}"
    "jenkins-agent-vue:${VERSION}"
)

log_info "====== Jenkins Agent 镜像构建脚本 ======"
log_info "版本: ${VERSION}"
log_info "构建日期: $(date +'%Y-%m-%d %H:%M:%S')"
echo ""

# 检查 Dockerfile 是否存在
log_step "检查 Dockerfile..."
for dockerfile in Dockerfile.dotnet Dockerfile.java Dockerfile.vue; do
    if [ ! -f "$dockerfile" ]; then
        log_error "找不到 $dockerfile"
        exit 1
    fi
    log_info "✓ $dockerfile"
done
echo ""

# 构建镜像
build_image() {
    local dockerfile=$1
    local image_name=$2
    local agent_type=$3

    log_step "构建 ${agent_type} Agent 镜像..."
    log_info "Dockerfile: ${dockerfile}"
    log_info "镜像名称: ${image_name}"

    if docker build -f "${dockerfile}" -t "${image_name}" . ; then
        log_info "✅ ${agent_type} Agent 镜像构建成功"

        # 显示镜像信息
        IMAGE_SIZE=$(docker images "${image_name}" --format "{{.Size}}")
        log_info "镜像大小: ${IMAGE_SIZE}"
    else
        log_error "❌ ${agent_type} Agent 镜像构建失败"
        exit 1
    fi
    echo ""
}

# 构建所有镜像
log_info "开始构建镜像..."
echo ""

build_image "Dockerfile.dotnet" "jenkins-agent-dotnet:${VERSION}" ".NET"
build_image "Dockerfile.java" "jenkins-agent-java:${VERSION}" "Java"
build_image "Dockerfile.vue" "jenkins-agent-vue:${VERSION}" "Vue"

# 导出镜像
log_step "导出镜像为 tar 文件..."

export_image() {
    local image_name=$1
    local tar_file="${image_name/:/-}.tar"

    log_info "导出 ${image_name}..."
    if docker save "${image_name}" -o "../${tar_file}"; then
        # 生成 MD5
        cd ..
        md5sum "${tar_file}" > "${tar_file}.md5"
        cd agents

        TAR_SIZE=$(du -h "../${tar_file}" | cut -f1)
        log_info "✅ 导出成功: ${tar_file} (${TAR_SIZE})"
    else
        log_error "❌ 导出失败: ${image_name}"
        exit 1
    fi
    echo ""
}

export_image "jenkins-agent-dotnet:${VERSION}"
export_image "jenkins-agent-java:${VERSION}"
export_image "jenkins-agent-vue:${VERSION}"

# 生成摘要报告
log_step "生成构建报告..."

cat > ../AGENT_BUILD_REPORT.md << EOF
# Jenkins Agent 镜像构建报告

**构建时间**: $(date +'%Y-%m-%d %H:%M:%S')
**版本**: ${VERSION}
**构建主机**: $(hostname)

---

## 镜像列表

| 镜像名称 | 大小 | 导出文件 | MD5 |
|---------|------|----------|-----|
EOF

for image in "${IMAGES[@]}"; do
    IMAGE_SIZE=$(docker images "${image}" --format "{{.Size}}")
    TAR_FILE="${image/:/-}.tar"
    MD5=$(cd .. && md5sum "${TAR_FILE}" | cut -d' ' -f1 && cd agents)
    TAR_SIZE=$(cd .. && du -h "${TAR_FILE}" | cut -f1 && cd agents)

    echo "| ${image} | ${IMAGE_SIZE} | ${TAR_FILE} | \`${MD5}\` |" >> ../AGENT_BUILD_REPORT.md
done

cat >> ../AGENT_BUILD_REPORT.md << 'EOF'

---

## 镜像内容

### .NET Agent
- **基础镜像**: jenkins/inbound-agent:latest-jdk17
- **.NET SDK**: 6.0 + 8.0
- **工具**: git, curl, wget, dotnet-sonarscanner

### Java Agent
- **基础镜像**: jenkins/inbound-agent:latest-jdk17
- **JDK**: 17
- **Maven**: 3.9.6
- **Gradle**: 8.5
- **工具**: git, curl, wget

### Vue Agent
- **基础镜像**: jenkins/inbound-agent:latest-jdk17
- **Node.js**: 18 LTS
- **包管理器**: npm, yarn, pnpm
- **CLI**: @vue/cli, @angular/cli, vite
- **工具**: git, curl, wget

---

## 导入到内网

### 1. 上传文件

将以下文件上传到内网服务器：
```
jenkins-agent-dotnet-1.0.tar
jenkins-agent-dotnet-1.0.tar.md5
jenkins-agent-java-1.0.tar
jenkins-agent-java-1.0.tar.md5
jenkins-agent-vue-1.0.tar
jenkins-agent-vue-1.0.tar.md5
```

### 2. 验证 MD5

```bash
md5sum -c jenkins-agent-dotnet-1.0.tar.md5
md5sum -c jenkins-agent-java-1.0.tar.md5
md5sum -c jenkins-agent-vue-1.0.tar.md5
```

### 3. 导入镜像

```bash
docker load -i jenkins-agent-dotnet-1.0.tar
docker load -i jenkins-agent-java-1.0.tar
docker load -i jenkins-agent-vue-1.0.tar
```

### 4. 验证镜像

```bash
docker images | grep jenkins-agent
```

应该看到：
```
jenkins-agent-dotnet    1.0    ...    ...    ...
jenkins-agent-java      1.0    ...    ...    ...
jenkins-agent-vue       1.0    ...    ...    ...
```

---

## 使用方式

### 方式 1: Docker Compose（静态 Agent）

参考 `docker-compose-agents.yml` 启动持久化的 Agent 容器。

### 方式 2: Docker Cloud Plugin（动态 Agent）

在 Jenkins 中配置 Docker Cloud，按需启动 Agent 容器。

详细配置请查看 `DOCKER_AGENT_GUIDE.md`。

---

## 更新日志

### v1.0 ($(date +'%Y-%m-%d'))
- 初始版本
- 支持 .NET、Java、Vue 构建环境
- 基于 jenkins/inbound-agent:latest-jdk17

---

**构建成功！** ✅
EOF

log_info "✅ 构建报告已生成: ../AGENT_BUILD_REPORT.md"
echo ""

# 显示镜像列表
log_step "本地镜像列表："
docker images | grep jenkins-agent

echo ""
log_info "====== 构建完成 ======"
log_info "导出的镜像文件："
cd ..
ls -lh jenkins-agent-*.tar
cd agents

echo ""
log_info "下一步："
log_info "1. 将 jenkins-agent-*.tar 文件上传到内网"
log_info "2. 在内网执行: docker load -i jenkins-agent-xxx.tar"
log_info "3. 参考 DOCKER_AGENT_GUIDE.md 配置 Jenkins"
echo ""
