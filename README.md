# JenkinsDeploy

**企业级 Jenkins CI/CD 完整解决方案**

基于 Docker Compose 的 Jenkins 主从架构部署方案，支持 .NET、Java、Vue 等多技术栈，内置完整的 E2E 测试环境。

[![Jenkins](https://img.shields.io/badge/Jenkins-LTS-D24939?logo=jenkins)](https://www.jenkins.io/)
[![Docker](https://img.shields.io/badge/Docker-20.10+-2496ED?logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## 📋 目录

- [特性](#-特性)
- [架构概述](#-架构概述)
- [快速开始](#-快速开始)
- [详细部署](#-详细部署)
- [Jenkins Agent 配置](#-jenkins-agent-配置)
- [E2E 测试集成](#-e2e-测试集成)
- [故障排查](#-故障排查)
- [最佳实践](#-最佳实践)
- [项目结构](#-项目结构)

---

## ✨ 特性

### 🎯 核心功能
- ✅ **一键部署** - Docker Compose 自动化部署 Jenkins 主从架构
- ✅ **配置即代码** - Jenkins Configuration as Code (JCasC) 零手动配置
- ✅ **分层镜像** - 基于分层架构的多技术栈 Agent 镜像
- ✅ **E2E 测试** - 内置 Python + Docker Compose 的端到端测试环境
- ✅ **离线部署** - 支持内网环境的完整离线部署方案

### 🛠️ 技术栈支持
- **后端**: .NET 8.0 SDK + SonarScanner
- **前端**: Node.js 18/20 LTS + Vue CLI
- **数据库**: PostgreSQL 16 (E2E 测试)
- **测试**: Python 3.13 + pytest + pytest-bdd
- **容器**: Docker-outside-of-Docker (DooD) 架构

### 🔒 安全与性能
- ✅ Docker socket 权限管理 (`group_add` 方案)
- ✅ 资源限制配置 (CPU/Memory limits)
- ✅ 内网 Nexus 源配置支持
- ✅ 网络隔离与容器间通信优化

---

## 🏗️ 架构概述

```
┌─────────────────────────────────────────────────────────────┐
│                      Jenkins Master                          │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐    │
│  │   Web UI     │   │   JCasC      │   │   Plugins    │    │
│  │  (Port 8080) │   │  (自动配置)   │   │   (80+ 个)   │    │
│  └──────────────┘   └──────────────┘   └──────────────┘    │
└─────────────────────────────────────────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            │               │               │
    ┌───────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐
    │  Agent-Base  │ │Agent-Docker│ │Agent-Dotnet│
    │  (Jenkins)   │ │ (+Docker)  │ │(+.NET SDK) │
    │              │ │            │ │  +Python   │
    └──────────────┘ └────────────┘ └─────┬──────┘
                                           │
                                    ┌──────▼──────┐
                                    │  Docker     │
                                    │  Socket     │
                                    │  (DooD)     │
                                    └─────────────┘
```

### 分层镜像架构

**第一层 - Agent Base (jenkins-agent-base)**
- Jenkins Agent 基础环境
- Git, curl, wget 等基础工具

**第二层 - Agent Docker (jenkins-agent-docker)**
- 继承 Agent Base
- 添加 Docker CLI + Docker Compose
- 配置 Docker socket 访问权限

**第三层 - Agent .NET (jenkins-agent-dotnet)**
- 继承 Agent Docker
- .NET 8.0 SDK + SonarScanner
- Python 3.13 + pytest (E2E 测试)
- NuGet 缓存优化

---

## 🚀 快速开始

### 前置条件

```bash
# 检查环境
docker --version      # 需要 20.10+
docker compose version  # 需要 v2.0+
```

### 10 分钟部署

```bash
# 1. 克隆仓库
git clone https://github.com/Euynac/JenkinsDeploy.git
cd JenkinsDeploy

# 2. 启动 Jenkins Master
cd master
docker compose up -d

# 3. 构建 Jenkins Agent 镜像（分层构建）
cd ..
docker build -f agents/base/Dockerfile.agent-base -t jenkins-agent-base:1.0 agents/base
docker build -f agents/base/Dockerfile.agent-docker -t jenkins-agent-docker:1.0 agents/base
docker build -f agents/dotnet/Dockerfile.dotnet -t jenkins-agent-dotnet:2.0 agents/dotnet

# 4. 启动 .NET Agent
cd agents/dotnet
docker compose -f docker-compose-dotnet.yml up -d

# 5. 访问 Jenkins
open http://localhost:8080
# 用户名: admin
# 密码: admin (请在生产环境中修改)
```

### 验证部署

```bash
# 检查容器状态
docker ps | grep jenkins

# 应该看到:
# jenkins-master       Up (healthy)
# jenkins-agent-dotnet Up

# 检查 Agent 连接状态
docker logs jenkins-agent-dotnet | grep "Connected"
```

---

## 📦 详细部署

### 步骤 1: 部署 Jenkins Master

```bash
cd master
cp .env.example .env  # 根据需要修改配置

docker compose up -d
```

**环境变量说明** (`.env`):
```bash
JENKINS_ADMIN_USER=admin
JENKINS_ADMIN_PASSWORD=admin        # ⚠️ 生产环境请修改
JENKINS_OPTS=                       # JVM 参数
JENKINS_SLAVE_AGENT_PORT=50000      # Agent 通信端口
TZ=Asia/Shanghai                    # 时区设置
```

### 步骤 2: 构建 Agent 镜像

#### 分层构建步骤

```bash
cd /mnt/d/Repositories/JenkinsDeploy

# 构建基础镜像
docker build -f agents/base/Dockerfile.agent-base -t jenkins-agent-base:1.0 agents/base

# 构建 Docker Agent
docker build -f agents/base/Dockerfile.agent-docker -t jenkins-agent-docker:1.0 agents/base

# 构建 .NET Agent
docker build -f agents/dotnet/Dockerfile.dotnet -t jenkins-agent-dotnet:2.0 agents/dotnet
```

**构建顺序**:
1. `jenkins-agent-base:1.0` (基础层)
2. `jenkins-agent-docker:1.0` (Docker 层)
3. `jenkins-agent-dotnet:2.0` (.NET + Python 层)

**构建时间**: 约 5-10 分钟（首次构建）

### 步骤 3: 配置 Docker Socket 权限

**重要**: 确保 `group_add` 的 GID 与宿主机 Docker socket 的 GID 匹配！

```bash
# 1. 检查宿主机 Docker socket GID
stat -c '%g' /var/run/docker.sock
# 输出示例: 1001

# 2. 更新 docker-compose-dotnet.yml
# 找到 group_add 配置，确保 GID 匹配:
group_add:
  - "1001"  # 替换为你的 GID
```

**详细说明**: 参考 [`agents/DOCKER_SOCKET_CONFIG.md`](agents/DOCKER_SOCKET_CONFIG.md)

### 步骤 4: 启动 Agent

```bash
cd agents/dotnet
docker compose -f docker-compose-dotnet.yml up -d
```

**Agent 配置说明**:
- **JENKINS_URL**: `http://jenkins-master:8080`
- **JENKINS_AGENT_NAME**: `agent-dotnet-8`
- **JENKINS_SECRET**: 从 Jenkins Web UI 复制
  - 路径: Jenkins > 系统管理 > 节点管理 > agent-dotnet-8 > Secret

### 步骤 5: 在 Jenkins 中注册 Agent

1. 登录 Jenkins: http://localhost:8080
2. 进入 **系统管理** > **节点管理**
3. 点击 **新建节点**
   - 节点名称: `agent-dotnet-8`
   - 类型: **永久代理**
4. 配置节点:
   - **远程工作目录**: `/home/jenkins/agent`
   - **启动方式**: **通过 Java Web Start 代理程序启动**
   - **标签**: `dotnet docker e2e-test`
5. 保存后，复制 **Secret** 到 `docker-compose-dotnet.yml`
6. 重启 Agent 容器:
   ```bash
   cd agents/dotnet
   docker compose -f docker-compose-dotnet.yml restart
   ```

---

## 🤖 Jenkins Agent 配置

### Agent 类型选择

| Agent 类型 | 技术栈 | 用途 | 镜像名称 |
|-----------|--------|------|----------|
| **Base** | Jenkins Agent | 基础构建 | `jenkins-agent-base:1.0` |
| **Docker** | + Docker CLI | 容器构建 | `jenkins-agent-docker:1.0` |
| **.NET** | + .NET SDK + Python | .NET 项目 + E2E 测试 | `jenkins-agent-dotnet:2.0` |

### 资源配置

**生产环境推荐配置** (`docker-compose-dotnet.yml`):

```yaml
deploy:
  resources:
    limits:
      cpus: '2'         # 最大 2 核
      memory: 4G        # 最大 4GB
    reservations:
      cpus: '1'         # 保留 1 核
      memory: 2G        # 保留 2GB
```

### 网络配置

所有容器共享 `jenkinsdeploy_default` 网络，支持容器间直接通信：

```yaml
networks:
  jenkinsdeploy_default:
    external: true
```

---

## 🧪 E2E 测试集成

### 架构说明

E2E 测试环境完全运行在 Jenkins Agent 容器内：

```
┌─────────────────────────────────────────────────────┐
│         Jenkins Agent Container                      │
│  ┌────────────────────────────────────────────┐    │
│  │  Pipeline 执行环境                          │    │
│  │  ┌──────────────┐  ┌──────────────┐        │    │
│  │  │ .NET API     │  │ Python       │        │    │
│  │  │ (Kestrel)    │  │ pytest-bdd   │        │    │
│  │  │ Port: 5085   │  │              │        │    │
│  │  └──────┬───────┘  └──────┬───────┘        │    │
│  └─────────┼──────────────────┼────────────────┘    │
│            │                  │                      │
│            └──────────┬───────┘                      │
│                       │                              │
└───────────────────────┼──────────────────────────────┘
                        │
        ┌───────────────▼───────────────┐
        │ PostgreSQL Test Container     │
        │ (Docker-outside-of-Docker)    │
        │ Name: todoapp-postgres-test   │
        │ Network: jenkinsdeploy_default│
        └───────────────────────────────┘
```

### Pipeline 示例

完整示例: [`examples/quick-test-pipeline.groovy`](examples/quick-test-pipeline.groovy)

**关键步骤**:

```groovy
stage('Run E2E Tests') {
    steps {
        sh '''
            # 1. 启动测试数据库容器（DooD）
            cd todoapp-backend-api-e2etest-main
            docker compose -f docker-compose.test.yml up -d

            # 2. 等待 DNS 注册（关键！）
            MAX_DNS_RETRIES=30
            while [ $DNS_RETRY_COUNT -lt $MAX_DNS_RETRIES ]; do
                if getent hosts todoapp-postgres-test > /dev/null 2>&1; then
                    echo "✅ DNS 解析成功"
                    break
                fi
                sleep 1
            done

            # 3. 后台启动 API 服务（使用 nohup env 传递环境变量）
            cd ../todoapp-backend-api-main
            nohup env \
                ConnectionStrings__DefaultConnection="Host=todoapp-postgres-test;..." \
                ASPNETCORE_ENVIRONMENT=Test \
                dotnet run --urls http://localhost:5085 > ../api.log 2>&1 &

            # 4. 等待 API 启动（检查端口监听）
            while [ $RETRY_COUNT -lt 60 ]; do
                if curl -s -f http://localhost:5085/swagger/index.html > /dev/null 2>&1; then
                    echo "✅ API 服务已启动"
                    break
                fi
                sleep 1
            done

            # 5. 运行 E2E 测试
            cd ../todoapp-backend-api-e2etest
            . venv/bin/activate
            pytest --alluredir=test-results/allure-results -v
        '''
    }
}
```

### 环境变量传递要点

**问题**: `export` 的环境变量在 `nohup` 子进程中丢失

**解决方案**: 使用 `nohup env VAR=value command` 直接传递

```bash
# ❌ 错误写法（环境变量会丢失）
export ConnectionStrings__DefaultConnection="..."
nohup dotnet run &

# ✅ 正确写法
nohup env ConnectionStrings__DefaultConnection="..." dotnet run &
```

### 数据库容器配置

[`examples/todoapp-backend-api-e2etest-main/docker-compose.test.yml`](examples/todoapp-backend-api-e2etest-main/docker-compose.test.yml):

```yaml
services:
  postgres-test:
    image: postgres:16-alpine
    container_name: todoapp-postgres-test
    environment:
      POSTGRES_DB: todoapp_test
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5433:5432"
    networks:
      - jenkinsdeploy_default  # 关键！共享网络
    tmpfs:
      - /var/lib/postgresql/data  # 测试后自动清理
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 10
```

---

## 🔧 故障排查

### 常见问题 1: Docker Socket 权限错误

**错误信息**:
```
permission denied while trying to connect to Docker socket
```

**解决方案**:

1. 检查宿主机 Docker socket GID:
   ```bash
   stat -c '%g' /var/run/docker.sock
   ```

2. 更新 `docker-compose-dotnet.yml`:
   ```yaml
   group_add:
     - "YOUR_GID_HERE"  # 替换为步骤 1 的结果
   ```

3. 重启容器:
   ```bash
   docker compose -f docker-compose-dotnet.yml restart
   ```

**验证方法**:
```bash
# 正确的验证方式（不是 groups 命令）
docker exec jenkins-agent-dotnet cat /proc/1/status | grep Groups
# 应该输出: Groups: 1000 1001
```

**参考文档**: [`agents/doc/DOCKER_SOCKET_CONFIG.md`](agents/doc/DOCKER_SOCKET_CONFIG.md)

---

### 常见问题 2: `groups` 命令警告

**警告信息**:
```
jenkins groups: cannot find name for group ID 1001
```

**说明**: 这**不是错误**！`group_add` 只添加 GID，不在 `/etc/group` 中创建组名，这是正常行为。

**验证 Docker 功能**:
```bash
docker exec jenkins-agent-dotnet docker ps
# 如果能正常输出，说明权限配置正确
```

**如果想消除警告**，可以在 Dockerfile 中添加:
```dockerfile
RUN groupadd -g 1001 docker 2>/dev/null || true
```

然后重新构建镜像。

---

### 常见问题 3: API 启动后立即退出

**症状**: Pipeline 中 API 进程 PID 检查失败

**原因**: `dotnet run` 编译后会替换进程，原 PID 失效

**解决方案**: 不检查 PID，改为检查端口监听:

```bash
# ❌ 错误写法
if ! ps -p $API_PID > /dev/null 2>&1; then
    echo "API 进程已退出"
    exit 1
fi

# ✅ 正确写法
if curl -s -f http://localhost:5085/swagger/index.html > /dev/null 2>&1; then
    echo "✅ API 已就绪"
fi
```

---

### 常见问题 4: DNS 解析失败

**错误信息**:
```
System.Net.Sockets.SocketException: Name or service not known
```

**原因**: 容器名注册到 Docker DNS 需要时间

**解决方案**: 添加 DNS 等待逻辑:

```bash
# 等待 Docker DNS 注册
MAX_DNS_RETRIES=30
DNS_RETRY_COUNT=0
while [ $DNS_RETRY_COUNT -lt $MAX_DNS_RETRIES ]; do
    if getent hosts todoapp-postgres-test > /dev/null 2>&1; then
        echo "✅ DNS 解析成功"
        break
    fi
    DNS_RETRY_COUNT=$((DNS_RETRY_COUNT + 1))
    sleep 1
done
```

---

### 常见问题 5: Agent 无法连接到 Master

**症状**: Agent 日志显示连接超时

**排查步骤**:

1. 检查网络连通性:
   ```bash
   docker exec jenkins-agent-dotnet ping jenkins-master
   ```

2. 检查 JENKINS_SECRET:
   ```bash
   docker exec jenkins-agent-dotnet env | grep JENKINS_SECRET
   ```

3. 检查 Master 容器状态:
   ```bash
   docker ps | grep jenkins-master
   # 应该显示 (healthy)
   ```

4. 查看 Master 日志:
   ```bash
   docker logs jenkins-master | grep agent
   ```

---

### ⚠️ 常见问题 6: HTTP 代理导致 SonarQube 连接失败

**症状**:
- SonarQube Analysis 阶段失败
- 错误信息: `Http status code is BadGateway`
- 日志显示: `Downloading from http://sonarqube:9000/api/server/version failed`

**根本原因**:
Jenkins Agent 配置了 HTTP 代理（如 `HTTP_PROXY=http://host.docker.internal:6666`），但 `NO_PROXY` 环境变量中**没有包含 SonarQube 服务器**，导致：
1. 对 SonarQube 的请求被发送到代理服务器
2. 代理服务器无法解析 Docker 内部的 `sonarqube` 域名
3. 返回 502 Bad Gateway 错误

**诊断方法**:

```bash
# 1. 检查 Agent 是否使用代理
docker exec jenkins-agent-dotnet env | grep -i proxy

# 2. 测试 SonarQube 连接（带详细输出）
docker exec jenkins-agent-dotnet curl -v http://sonarqube:9000/api/server/version

# 如果看到以下输出，说明请求被发送到代理了：
# * Uses proxy env variable http_proxy == 'http://...'
# < HTTP/1.1 502 Bad Gateway
```

**解决方案**:

1. **编辑 Agent 配置文件**（`agents/dotnet/docker-compose-dotnet.yml`）:

   ```yaml
   environment:
     # 代理设置：排除内部 Docker 网络和 SonarQube
     NO_PROXY: "localhost,127.0.0.1,jenkins-master,sonarqube,sonarqube-db,172.16.0.0/12,192.168.0.0/16,172.19.0.0/16"
     no_proxy: "localhost,127.0.0.1,jenkins-master,sonarqube,sonarqube-db,172.16.0.0/12,192.168.0.0/16,172.19.0.0/16"
   ```

   **必须添加**：
   - `sonarqube` - SonarQube 服务器主机名
   - `sonarqube-db` - SonarQube 数据库主机名（可选）
   - `172.19.0.0/16` - SonarQube 网络的 CIDR（使用 `docker network inspect sonarqube-network` 查看）

2. **重启 Agent 容器**:

   ```bash
   cd agents/dotnet
   docker compose -f docker-compose-dotnet.yml restart
   ```

3. **验证修复**:

   ```bash
   # 应该看到 "no_proxy" 包含 sonarqube
   docker exec jenkins-agent-dotnet env | grep NO_PROXY

   # 应该看到直接连接（不经过代理），返回 HTTP/1.1 200
   docker exec jenkins-agent-dotnet curl -v http://sonarqube:9000/api/server/version

   # 应该返回 SonarQube 版本号（如 25.11.0.114957）
   docker exec jenkins-agent-dotnet curl -s http://sonarqube:9000/api/server/version
   ```

**预防措施**:
- 在任何使用 HTTP 代理的环境中，务必将内部 Docker 服务添加到 `NO_PROXY`
- 使用 Docker 网络时，建议添加常用的内网 CIDR：
  - `10.0.0.0/8`
  - `172.16.0.0/12`
  - `192.168.0.0/16`
- 对于其他内部服务（如 Nexus、GitLab），也要添加到 `NO_PROXY`

**详细文档**: 参考 [`components/sonarqube/README.md`](components/sonarqube/README.md) 中的"问题 0"

---

## 💡 最佳实践

### 1. 镜像管理

**镜像标签策略**:
```bash
# 开发环境
jenkins-agent-dotnet:dev-20241128

# 测试环境
jenkins-agent-dotnet:test-20241128

# 生产环境
jenkins-agent-dotnet:2.0
```

**镜像清理**:
```bash
# 删除悬空镜像
docker image prune -f

# 查看镜像大小
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
```

---

### 2. 数据备份

**Jenkins Master 数据**:
```bash
# 备份 Jenkins 配置和作业
docker exec jenkins-master tar czf - /var/jenkins_home > jenkins-backup-$(date +%Y%m%d).tar.gz

# 恢复备份
docker compose -f master/docker-compose.yml down
docker volume rm jenkins-data
docker volume create jenkins-data
docker run --rm -v jenkins-data:/restore -v $(pwd):/backup alpine sh -c "cd /restore && tar xzf /backup/jenkins-backup-20241128.tar.gz --strip 1"
docker compose -f master/docker-compose.yml up -d
```

**Agent 工作空间**:
```bash
# Agent 工作空间会在容器销毁时丢失，建议配置持久化卷
volumes:
  - jenkins-agent-workspace:/home/jenkins/agent
```

---

### 3. 安全加固

**修改默认密码**:
```bash
# 在 master/.env 中修改
JENKINS_ADMIN_PASSWORD=YourStrongPassword123!
```

**禁用不必要的插件**:
```bash
# 编辑 master/plugins.txt，注释掉不需要的插件
# locale:1.2
```

**配置防火墙**:
```bash
# 仅允许内网访问
sudo ufw allow from 192.168.0.0/16 to any port 8080
```

---

### 4. 性能优化

**JVM 参数调优** (`.env`):
```bash
JENKINS_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC
```

**Docker 构建缓存**:
```bash
# 使用 BuildKit 加速构建
export DOCKER_BUILDKIT=1
docker build --cache-from jenkins-agent-dotnet:2.0 -t jenkins-agent-dotnet:2.1 .
```

**NuGet/npm 缓存优化**:
```yaml
volumes:
  - jenkins-agent-dotnet-nuget:/home/jenkins/.nuget  # 持久化 NuGet 缓存
```

---

### 5. 监控与日志

**容器健康检查**:
```bash
# 查看容器健康状态
docker ps --format "table {{.Names}}\t{{.Status}}"

# 查看实时日志
docker logs -f jenkins-master
```

**资源使用监控**:
```bash
# 查看容器资源使用
docker stats jenkins-master jenkins-agent-dotnet
```

**日志收集**:
```bash
# 导出最近 1000 行日志
docker logs --tail 1000 jenkins-master > jenkins-master.log
```

---

## 📂 项目结构

```
JenkinsDeploy/
├── master/                           # Jenkins Master 配置
│   ├── docker-compose.yml           # Master 容器编排
│   ├── .env.example                 # 环境变量模板
│   └── config/
│       └── jenkins-casc.yaml        # JCasC 自动配置
│
├── agents/                          # Jenkins Agent 镜像
│   ├── base/                        # 基础 Agent 镜像
│   │   ├── Dockerfile.agent-base        # Jenkins Agent 基础镜像
│   │   ├── Dockerfile.agent-docker      # Docker Agent 镜像 (DooD)
│   │   ├── entrypoint-agent-base.sh     # 基础 Agent 入口脚本
│   │   └── entrypoint-agent-docker.sh   # Docker Agent 入口脚本
│   ├── dotnet/                      # .NET Agent 镜像
│   │   ├── Dockerfile.dotnet            # .NET Agent 镜像 (含 Python)
│   │   ├── entrypoint-dotnet.sh         # .NET Agent 入口脚本
│   │   └── docker-compose-dotnet.yml  # .NET Agent 部署配置
│   └── doc/                         # 文档目录
│       ├── DOCKER_SOCKET_CONFIG.md      # Docker Socket 权限配置
│       └── README.md                    # Agent 文档
│
├── examples/                        # 示例项目
│   ├── quick-test-pipeline.groovy  # E2E 测试 Pipeline
│   └── todoapp-backend-api-e2etest-main/
│       ├── docker-compose.test.yml  # 测试数据库配置
│       └── requirements.txt     # Python 测试依赖
│
├── docs/                            # 项目文档
│   └── ...
│
├── README.md                        # 本文件
├── PROJECT_STRUCTURE.md             # 项目结构说明
└── .gitignore
```

---

## 📚 相关文档

- [Docker Socket 权限配置指南](agents/doc/DOCKER_SOCKET_CONFIG.md)
- [项目结构详解](PROJECT_STRUCTURE.md)
- [E2E 测试 Pipeline 完整示例](examples/quick-test-pipeline.groovy)

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

**开发流程**:
1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📝 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

---

## 🙏 致谢

- [Jenkins](https://www.jenkins.io/) - 开源 CI/CD 平台
- [Docker](https://www.docker.com/) - 容器化技术
- [Jenkins Configuration as Code Plugin](https://github.com/jenkinsci/configuration-as-code-plugin)

---

## 📧 联系方式

- **GitHub**: [Euynac/JenkinsDeploy](https://github.com/Euynac/JenkinsDeploy)
- **Issues**: [提交问题](https://github.com/Euynac/JenkinsDeploy/issues)

---

**⭐ 如果这个项目对你有帮助，请给个 Star！**
