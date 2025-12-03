# Jenkins Agent - Vue.js 构建环境

基于 `jenkins-agent-docker:1.0` 镜像，添加 Node.js 20 LTS 和 Vue.js 工具链，用于前端项目的 CI/CD 构建。

## 镜像特性

- **基础层**: jenkins-agent-docker:1.0 (包含 Docker CLI 和基础工具)
- **Node.js**: 20.x LTS
- **包管理器**: npm (随 Node.js 安装)
- **全局工具**:
  - `@vue/cli` - Vue CLI 脚手架工具
  - `sonarqube-scanner` - SonarQube 代码扫描工具
- **DooD 支持**: Docker-outside-of-Docker，可在构建中使用 Docker 命令

## 快速开始

### 1. 前置条件

确保已完成以下步骤：

```bash
# 1. 检查 Jenkins Master 是否运行
docker ps | grep jenkins-master-test

# 2. 检查 SonarQube 是否运行（用于代码质量分析）
docker ps | grep sonarqube

# 3. 检查 Docker 网络是否存在
docker network ls | grep jenkinsdeploy_default
docker network ls | grep sonarqube-network

# 4. 检查宿主机 Docker socket GID
stat -c '%g' /var/run/docker.sock
# 记下这个 GID，需要在 docker-compose 中配置
```

### 2. 构建 Agent 镜像（分层构建）

**重要**: 必须按顺序构建，因为存在镜像依赖关系。

```bash
cd /mnt/d/Repositories/JenkinsDeploy

# Layer 1: Base agent (如果还没有构建)
docker build -f agents/base/Dockerfile.agent-base -t jenkins-agent-base:1.0 agents/base

# Layer 2: Docker agent (如果还没有构建)
docker build -f agents/base/Dockerfile.agent-docker -t jenkins-agent-docker:1.0 agents/base

# Layer 3: Vue agent
docker build -f agents/vue/Dockerfile.vue -t jenkins-agent-vue:1.0 agents/vue
```

### 3. 验证镜像

```bash
# 查看镜像
docker images | grep jenkins-agent

# 预期输出:
# jenkins-agent-vue       1.0
# jenkins-agent-docker    1.0
# jenkins-agent-base      1.0
```

### 4. 配置 Agent 连接信息

在启动 Agent 前，需要在 Jenkins UI 中创建节点并获取 Secret：

1. 访问 Jenkins: http://localhost:8080
2. 进入 "Manage Jenkins" → "Nodes"
3. 点击 "New Node"
4. 配置节点：
   - **Name**: `agent-vue`
   - **Type**: Permanent Agent
   - **Remote root directory**: `/home/jenkins/agent`
   - **Labels**: `vue`
   - **Launch method**: Launch agent by connecting it to the controller
5. 保存后，在节点页面复制 **Secret** 值

编辑 `agents/vue/docker-compose-test-vue.yml`，替换 Secret：

```yaml
environment:
  JENKINS_SECRET: "your_secret_here_replace_after_creating_node"  # 替换为实际的 Secret
```

**同时检查 `group_add` GID**，确保与宿主机 Docker socket GID 匹配：

```yaml
group_add:
  - "1001"  # 替换为实际的 Docker socket GID
```

### 5. 启动 Agent

```bash
cd agents/vue
docker compose -f docker-compose-test-vue.yml up -d

# 查看日志
docker logs -f jenkins-agent-vue-test

# 预期日志:
# INFO: Connected
```

### 6. 验证 Agent 连接

在 Jenkins UI 中检查节点状态：
- "Manage Jenkins" → "Nodes"
- `agent-vue` 应显示为 "Connected"

## 运行测试 Pipeline

### 方法 1: 使用 Jenkins UI

1. 访问 Jenkins: http://localhost:8080
2. 新建任务 → Pipeline
3. 配置 Pipeline:
   - **Definition**: Pipeline script
   - 复制 `examples/test-frontend.groovy` 的内容
4. 保存并构建

### 方法 2: 使用 Blue Ocean (推荐)

1. 安装 Blue Ocean 插件 (如果未安装)
2. 访问 Blue Ocean UI
3. 创建新 Pipeline，粘贴 `test-frontend.groovy` 内容

## Pipeline 功能说明

`test-frontend.groovy` 包含以下阶段：

1. **Copy Project to Workspace** - 从挂载目录复制项目
2. **Setup Environment** - 验证 Node.js 环境
3. **Install Dependencies** - npm install
4. **Test** - 运行单元测试 (Jest) + 代码覆盖率
5. **SonarQube Analysis** - 代码质量分析
6. **SonarQube Quality Gate** - 质量门检查
7. **Build** - npm run build
8. **Package** - 打包 tar.gz
9. **Archive Artifacts** - 归档构建产物

**注意**: 此 Pipeline 已去除 E2E 测试阶段，不依赖后端服务。

## 常见问题

### 1. Agent 无法连接到 Jenkins Master

**症状**: 日志显示连接超时或拒绝

**解决**:
```bash
# 检查网络连通性
docker exec jenkins-agent-vue-test ping jenkins-master-test

# 检查 Secret 是否正确
docker exec jenkins-agent-vue-test env | grep JENKINS_SECRET

# 重启 Agent
docker compose -f docker-compose-test-vue.yml restart
```

### 2. SonarQube 连接失败 (502 Bad Gateway)

**症状**: SonarQube Analysis 阶段报错 `Http status code is BadGateway`

**原因**: Agent 的 HTTP 代理拦截了内部请求

**解决**: 确保 `NO_PROXY` 环境变量包含 `sonarqube`:

```yaml
environment:
  NO_PROXY: "localhost,127.0.0.1,jenkins-master-test,sonarqube,sonarqube-db,..."
```

### 3. npm install 很慢

**解决方案 1**: 使用国内镜像

编辑 `Dockerfile.vue`，取消注释：

```dockerfile
RUN npm config set registry https://registry.npmmirror.com
```

重新构建镜像。

**解决方案 2**: 使用 npm 缓存卷

`docker-compose-test-vue.yml` 已配置缓存卷：

```yaml
volumes:
  - jenkins-agent-vue-test-npm:/home/jenkins/.npm
```

### 4. 测试失败但想继续构建

Pipeline 已配置为测试失败时标记为 UNSTABLE 但继续执行：

```groovy
unstable("测试失败: 退出码 ${testExitCode}")
```

如需改为测试失败即中止，修改为：

```groovy
error("测试失败: 退出码 ${testExitCode}")
```

## 目录结构

```
agents/vue/
├── Dockerfile.vue                    # Vue agent 镜像定义
├── docker-compose-test-vue.yml       # Agent 部署配置
└── README.md                         # 本文档

examples/
├── test-frontend.groovy              # 简化版前端测试 Pipeline
├── frontend.groovy                   # 完整前端 Pipeline (含 E2E)
└── todoapp-frontend-vue2-main/       # Vue2 示例项目
```

## 镜像维护

### 更新 Node.js 版本

编辑 `Dockerfile.vue`:

```dockerfile
# 将 setup_20.x 改为其他版本
RUN curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
```

### 添加全局 npm 包

```dockerfile
RUN npm install -g @vue/cli \
    && npm install -g sonarqube-scanner \
    && npm install -g your-package-name  # 添加新包
```

### 重新构建

```bash
docker build -f agents/vue/Dockerfile.vue -t jenkins-agent-vue:1.0 agents/vue --no-cache
```

## 资源配置

默认资源限制（可在 `docker-compose-test-vue.yml` 中调整）：

```yaml
deploy:
  resources:
    limits:
      cpus: '2'
      memory: 4G
    reservations:
      cpus: '1'
      memory: 2G
```

## 下一步

- 集成到 GitLab CI/CD
- 配置 Nexus 仓库上传
- 添加更多前端工具链 (TypeScript, ESLint, etc.)
- 配置多节点 Agent 集群

## 参考文档

- [CLAUDE.md](../../CLAUDE.md) - 项目架构说明
- [agents/doc/DOCKER_SOCKET_CONFIG.md](../doc/DOCKER_SOCKET_CONFIG.md) - DooD 权限配置
- [examples/README.md](../../examples/README.md) - 示例项目说明
