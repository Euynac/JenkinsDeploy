# Docker 容器化 Agent 部署指南

## 方案概述

**适用场景**：离线内网环境，难以安装构建工具链

**解决方案**：在外网构建好 Agent 镜像，打包导入内网

```
外网：构建镜像          内网：导入并使用
┌──────────────┐       ┌──────────────┐
│ .NET SDK     │       │ 导入镜像      │
│ Maven        │ ───▶  │ 启动容器      │
│ Node.js      │  tar  │ 连接 Master   │
└──────────────┘       └──────────────┘
```

---

## 方式对比

### 方式 1: 静态 Agent 容器（推荐离线环境）⭐⭐⭐⭐⭐

```
┌─────────────┐
│   Master    │
└─────────────┘
       │
    ┌──┼──┐
    │  │  │
    ▼  ▼  ▼
  ┌───┐┌───┐┌───┐
  │容器││容器││容器│  ← 长期运行的 Agent 容器
  └───┘└───┘└───┘
```

**优势**：
- ✅ 容器长期运行，连接稳定
- ✅ 适合内网离线环境
- ✅ 配置简单，类似虚拟机 Agent
- ✅ 资源可控（docker-compose 限制）

**劣势**：
- ❌ 不能动态扩缩容
- ❌ 需要提前规划 Agent 数量

**推荐场景**：内网环境、固定并发数、离线部署 ⭐⭐⭐⭐⭐

---

### 方式 2: 动态 Agent 容器（Docker Cloud Plugin）

```
┌─────────────┐
│   Master    │  ← 按需启动容器
└─────────────┘
       │
    需要构建时
       │
    ┌──▼──┐
    │ 容器 │  ← 临时创建
    └─────┘
    构建完销毁
```

**优势**：
- ✅ 按需创建，资源利用率高
- ✅ 每次构建环境干净
- ✅ 可以根据负载自动扩容

**劣势**：
- ❌ 启动有延迟（拉取镜像、启动容器）
- ❌ 离线环境配置复杂（需要本地镜像仓库）

**推荐场景**：云环境、需要弹性扩容、有网络环境 ⭐⭐⭐

---

## 部署步骤（方式 1：静态容器 - 推荐）

### 步骤 1: 在外网构建 Agent 镜像

```bash
cd /mnt/d/Repositories/JenkinsDeploy/agents

# 构建所有 Agent 镜像
bash build-agents.sh

# 等待构建完成（约10-15分钟）
# 生成文件：
# - jenkins-agent-dotnet-1.0.tar
# - jenkins-agent-java-1.0.tar
# - jenkins-agent-vue-1.0.tar
```

### 步骤 2: 上传到内网

```bash
# 将以下文件上传到内网服务器
scp jenkins-agent-*.tar root@internal-server:/opt/jenkins/
scp jenkins-agent-*.tar.md5 root@internal-server:/opt/jenkins/
```

### 步骤 3: 在内网导入镜像

```bash
# 在内网服务器上
cd /opt/jenkins

# 验证 MD5
md5sum -c jenkins-agent-dotnet-1.0.tar.md5
md5sum -c jenkins-agent-java-1.0.tar.md5
md5sum -c jenkins-agent-vue-1.0.tar.md5

# 导入镜像
docker load -i jenkins-agent-dotnet-1.0.tar
docker load -i jenkins-agent-java-1.0.tar
docker load -i jenkins-agent-vue-1.0.tar

# 验证镜像
docker images | grep jenkins-agent
```

应该看到：
```
jenkins-agent-dotnet    1.0    ...    ...
jenkins-agent-java      1.0    ...    ...
jenkins-agent-vue       1.0    ...    ...
```

### 步骤 4: 在 Jenkins Master 中配置 Agent

#### 4.1 创建 Agent 节点

1. 登录 Jenkins: `http://localhost:8080`

2. **Manage Jenkins** → **Nodes** → **New Node**

3. 配置 `.NET Agent`:
```
Node name: agent-dotnet-01
Type: Permanent Agent

Remote root directory: /home/jenkins/agent
Labels: dotnet dotnet-8 microservice
Usage: Use this node as much as possible
Launch method: Launch agent by connecting it to the controller

✓ Use WebSocket
```

4. 点击 **Save**，记录下显示的命令，例如：
```
jenkins-agent-dotnet-01
Secret: a1b2c3d4e5f6g7h8i9j0
```

#### 4.2 重复创建其他 Agent

按照相同步骤创建：
- `agent-dotnet-02` (可选)
- `agent-java-01`
- `agent-java-02` (可选)
- `agent-vue-01`

记录每个 Agent 的 **Secret**。

### 步骤 5: 修改 docker-compose-agents.yml

编辑 `docker-compose-agents.yml`，填入每个 Agent 的 Secret：

```yaml
services:
  agent-dotnet-01:
    environment:
      - JENKINS_URL=http://jenkins-master:8080
      - JENKINS_AGENT_NAME=agent-dotnet-01
      - JENKINS_SECRET=a1b2c3d4e5f6g7h8i9j0  # ← 填入实际 Secret
```

**注意**：
- `JENKINS_URL` 必须是 Master 容器的网络地址
- 如果 Master 和 Agent 在同一台机器，使用容器名称
- 如果在不同机器，使用 Master 的 IP 地址

### 步骤 6: 创建 Docker 网络

如果 Master 和 Agent 在同一台机器：

```bash
# 创建共享网络
docker network create jenkins-network

# 将 Master 连接到网络
docker network connect jenkins-network jenkins-master
```

如果在不同机器，修改 `docker-compose-agents.yml`：

```yaml
services:
  agent-dotnet-01:
    environment:
      - JENKINS_URL=http://192.168.1.100:8080  # ← Master 的实际 IP
```

### 步骤 7: 启动 Agent 容器

```bash
# 启动所有 Agent
docker-compose -f docker-compose-agents.yml up -d

# 查看日志
docker-compose -f docker-compose-agents.yml logs -f

# 查看状态
docker-compose -f docker-compose-agents.yml ps
```

### 步骤 8: 验证 Agent 连接

1. 回到 Jenkins Web UI: **Manage Jenkins** → **Nodes**

2. 应该看到所有 Agent 显示为 **在线**（绿色图标）

3. 如果显示离线，查看日志：
```bash
docker logs jenkins-agent-dotnet-01
```

---

## 测试 Agent

### 创建测试 Pipeline

```groovy
pipeline {
    agent none  // 不使用默认 Agent

    stages {
        stage('测试 .NET Agent') {
            agent { label 'dotnet' }
            steps {
                sh '''
                    echo "====== .NET Agent 测试 ======"
                    hostname
                    dotnet --version
                    dotnet --list-sdks
                    git --version
                '''
            }
        }

        stage('测试 Java Agent') {
            agent { label 'java' }
            steps {
                sh '''
                    echo "====== Java Agent 测试 ======"
                    hostname
                    java -version
                    mvn -version
                    gradle -version
                '''
            }
        }

        stage('测试 Vue Agent') {
            agent { label 'vue' }
            steps {
                sh '''
                    echo "====== Vue Agent 测试 ======"
                    hostname
                    node -v
                    npm -v
                    yarn -v
                    pnpm -v
                '''
            }
        }
    }

    post {
        success {
            echo '✅ 所有 Agent 工作正常！'
        }
    }
}
```

保存并运行，如果成功，说明 Agent 配置正确。

---

## 配置 Nexus 代理（可选）

### .NET NuGet 配置

创建 `agents/NuGet.Config`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="Nexus" value="http://nexus.internal.com/repository/nuget-group/" />
  </packageSources>
</configuration>
```

修改 `Dockerfile.dotnet`：

```dockerfile
# 添加 NuGet 配置
COPY NuGet.Config /home/jenkins/.nuget/NuGet/NuGet.Config
RUN chown jenkins:jenkins /home/jenkins/.nuget/NuGet/NuGet.Config
```

重新构建镜像。

### Java Maven 配置

创建 `agents/settings.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors>
    <mirror>
      <id>nexus</id>
      <mirrorOf>*</mirrorOf>
      <url>http://nexus.internal.com/repository/maven-public/</url>
    </mirror>
  </mirrors>
</settings>
```

修改 `Dockerfile.java`：

```dockerfile
# 添加 Maven 配置
COPY settings.xml /home/jenkins/.m2/settings.xml
RUN chown jenkins:jenkins /home/jenkins/.m2/settings.xml
```

重新构建镜像。

### Vue npm 配置

修改 `Dockerfile.vue`：

```dockerfile
# 配置 npm 镜像
RUN npm config set registry http://nexus.internal.com/repository/npm-group/
RUN yarn config set registry http://nexus.internal.com/repository/npm-group/
```

重新构建镜像。

---

## 高级配置：动态 Agent（方式 2）

### 安装 Docker Plugin

1. **Manage Jenkins** → **Plugins** → **Available plugins**
2. 搜索 **Docker**
3. 安装 **Docker plugin**

### 配置 Docker Cloud

1. **Manage Jenkins** → **Clouds** → **New cloud** → **Docker**

2. 配置：
```
Name: docker-agents
Docker Host URI: unix:///var/run/docker.sock  # 如果 Master 在 Docker 中
或
Docker Host URI: tcp://192.168.1.100:2375    # 远程 Docker 主机
```

3. 添加 Docker Agent Template:

**.NET Agent Template**:
```
Labels: dotnet-dynamic
Docker Image: jenkins-agent-dotnet:1.0
Remote File System Root: /home/jenkins/agent
Volumes: agent-workspace:/home/jenkins/agent
```

**Java Agent Template**:
```
Labels: java-dynamic
Docker Image: jenkins-agent-java:1.0
Remote File System Root: /home/jenkins/agent
```

**Vue Agent Template**:
```
Labels: vue-dynamic
Docker Image: jenkins-agent-vue:1.0
Remote File System Root: /home/jenkins/agent
```

4. 保存配置

### 使用动态 Agent

```groovy
pipeline {
    agent {
        label 'dotnet-dynamic'  // 使用动态 Agent
    }

    stages {
        stage('构建') {
            steps {
                sh 'dotnet build'
            }
        }
    }
}
```

Jenkins 会自动：
1. 创建 Agent 容器
2. 运行构建
3. 销毁容器

---

## 资源配置建议

### 24个微服务场景

| Agent 类型 | 数量 | 资源配置 | 并发构建数 |
|-----------|------|---------|-----------|
| .NET Agent | 2个 | 8核16GB | 每台4个 |
| Java Agent | 2个 | 8核16GB | 每台4个 |
| Vue Agent | 1个 | 4核8GB | 4个 |

**总资源**: 44核88GB

**并发能力**: 同时构建 20 个项目

### 宿主机配置建议

**方案 1**: 单台大服务器
- **配置**: 48核96GB、1TB SSD
- **部署**: 所有 Agent 容器在一台机器

**方案 2**: 多台服务器（推荐）
- **Server 1**: 16核32GB → 运行 Master + 2个 .NET Agent
- **Server 2**: 16核32GB → 运行 2个 Java Agent
- **Server 3**: 8核16GB → 运行 1个 Vue Agent

---

## 监控和维护

### 查看 Agent 状态

```bash
# 查看容器状态
docker-compose -f docker-compose-agents.yml ps

# 查看资源使用
docker stats
```

### 查看 Agent 日志

```bash
# 实时日志
docker logs -f jenkins-agent-dotnet-01

# 最近100行
docker logs --tail 100 jenkins-agent-dotnet-01
```

### 重启 Agent

```bash
# 重启单个 Agent
docker-compose -f docker-compose-agents.yml restart agent-dotnet-01

# 重启所有 Agent
docker-compose -f docker-compose-agents.yml restart
```

### 清理旧的构建缓存

```bash
# 进入容器
docker exec -it jenkins-agent-dotnet-01 bash

# 清理工作空间
rm -rf /home/jenkins/agent/workspace/*

# 清理 NuGet 缓存
dotnet nuget locals all --clear
```

---

## 常见问题

### 1. Agent 连接不上 Master

**症状**: Agent 容器日志显示连接失败

**排查**:
```bash
# 检查网络连通性
docker exec jenkins-agent-dotnet-01 ping jenkins-master

# 检查 JENKINS_URL 是否正确
docker exec jenkins-agent-dotnet-01 env | grep JENKINS

# 检查 Secret 是否正确
# 在 Jenkins Web UI 中重新查看 Secret
```

**解决**:
- 确保 `JENKINS_URL` 可达
- 确保 `JENKINS_SECRET` 正确
- 确保网络配置正确（同一网络或防火墙规则）

### 2. 构建失败：找不到命令

**症状**: `dotnet: command not found`

**原因**: 镜像构建不完整

**解决**:
```bash
# 验证镜像内容
docker run --rm jenkins-agent-dotnet:1.0 dotnet --version

# 如果失败，重新构建镜像
cd agents
bash build-agents.sh
```

### 3. 磁盘空间不足

**症状**: 构建失败，提示磁盘空间不足

**解决**:
```bash
# 清理 Docker 卷
docker volume prune

# 清理未使用的镜像
docker image prune -a

# 查看磁盘使用
df -h
du -sh /var/lib/docker/*
```

### 4. 容器性能差

**症状**: 构建时间比虚拟机慢很多

**排查**:
```bash
# 查看容器资源限制
docker inspect jenkins-agent-dotnet-01 | grep -A 10 Resources

# 查看实际资源使用
docker stats jenkins-agent-dotnet-01
```

**解决**:
- 增加 CPU 和内存限制（修改 docker-compose.yml）
- 检查宿主机资源是否充足
- 考虑使用 SSD 存储

---

## 优势总结

### ✅ 容器化 Agent 的优势（内网环境）

1. **易于部署**：
   - 外网构建好镜像，内网直接导入
   - 避免在内网安装复杂的工具链
   - 一次构建，多处部署

2. **环境一致**：
   - 所有 Agent 使用相同的镜像
   - .NET、Maven、Node.js 版本固定
   - 避免"在我机器上能跑"的问题

3. **易于管理**：
   - 容器化配置，一键启动
   - 易于扩容（复制配置即可）
   - 易于回滚（使用旧版本镜像）

4. **资源隔离**：
   - 每个 Agent 独立运行
   - 资源限制可控
   - 互不影响

### 📊 与虚拟机 Agent 对比

| 特性 | 容器 Agent | 虚拟机 Agent |
|------|-----------|-------------|
| 部署难度（内网） | ⭐⭐⭐⭐⭐ 简单 | ⭐⭐ 困难 |
| 环境一致性 | ⭐⭐⭐⭐⭐ 完全一致 | ⭐⭐⭐ 手动配置 |
| 资源利用率 | ⭐⭐⭐⭐ 较好 | ⭐⭐⭐⭐⭐ 最好 |
| 构建性能 | ⭐⭐⭐⭐ 较快 | ⭐⭐⭐⭐⭐ 最快 |
| 扩容难度 | ⭐⭐⭐⭐⭐ 简单 | ⭐⭐⭐ 需要配置 |

---

## 总结

### 推荐方案：静态容器 Agent

对于你的场景（内网离线环境、24个微服务），推荐使用**静态容器 Agent**：

1. ✅ 在外网构建好 Agent 镜像
2. ✅ 打包导入内网
3. ✅ 使用 docker-compose 启动长期运行的 Agent 容器
4. ✅ 配置简单，类似虚拟机 Agent
5. ✅ 避免了在内网安装工具链的麻烦

### 下一步

1. 运行 `agents/build-agents.sh` 构建镜像
2. 上传到内网并导入
3. 配置 Jenkins 添加 Agent 节点
4. 修改 `docker-compose-agents.yml` 填入 Secret
5. 启动 Agent 容器
6. 测试构建

---

**构建时间估计**: 约 30 分钟
**适用场景**: ⭐⭐⭐⭐⭐ 完美适合离线内网环境
