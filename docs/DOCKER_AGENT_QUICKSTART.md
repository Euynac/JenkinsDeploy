# Docker Agent 快速开始指南

## 一分钟了解

**问题**: 内网离线环境难以安装 .NET SDK、Maven、Node.js 等工具

**解决**: 在外网构建好 Agent 容器镜像，打包导入内网

**优势**:
- ✅ 30分钟完成部署（vs 虚拟机的5小时）
- ✅ 环境完全一致
- ✅ 5分钟快速扩容

---

## 三步部署

### 步骤 1: 外网构建镜像（15分钟）

```bash
cd /mnt/d/Repositories/JenkinsDeploy

# 构建 Agent Base 镜像
docker build -f agents/base/Dockerfile.agent-base -t jenkins-agent-base:1.0 agents/base

# 构建 Docker Agent 镜像
docker build -f agents/base/Dockerfile.agent-docker -t jenkins-agent-docker:1.0 agents/base

# 构建 .NET Agent 镜像
docker build -f agents/dotnet/Dockerfile.dotnet -t jenkins-agent-dotnet:2.0 agents/dotnet
```

导出镜像文件：
```bash
docker save -o jenkins-agent-dotnet-2.0.tar jenkins-agent-dotnet:2.0
```

### 步骤 2: 内网导入镜像（5分钟）

```bash
# 上传文件到内网
scp jenkins-agent-*.tar root@internal-server:/opt/jenkins/

# 在内网执行
cd /opt/jenkins
docker load -i jenkins-agent-dotnet-2.0.tar
```

### 步骤 3: 配置启动（10分钟）

```bash
# 1. 在 Jenkins Web UI 中添加 Agent 节点，记录 Secret

# 2. 修改 docker-compose-agents.yml，填入 Secret

# 3. 启动容器
docker-compose -f docker-compose-agents.yml up -d

# 4. 查看状态
docker-compose -f docker-compose-agents.yml ps
```

**完成！** 🎉

---

## 常用命令

### 管理命令

```bash
# 查看状态
docker-compose -f docker-compose-agents.yml ps

# 查看日志
docker-compose -f docker-compose-agents.yml logs -f agent-dotnet-01

# 重启 Agent
docker-compose -f docker-compose-agents.yml restart agent-dotnet-01

# 停止所有 Agent
docker-compose -f docker-compose-agents.yml stop

# 启动所有 Agent
docker-compose -f docker-compose-agents.yml start
```

### 调试命令

```bash
# 进入容器
docker exec -it jenkins-agent-dotnet-01 bash

# 验证工具
docker exec jenkins-agent-dotnet-01 dotnet --version
docker exec jenkins-agent-java-01 mvn -version
docker exec jenkins-agent-vue-01 node -v

# 测试网络
docker exec jenkins-agent-dotnet-01 ping jenkins-master
```

---

## 文件结构

```
JenkinsDeploy/
├── agents/                          # Agent 镜像源文件
│   ├── base/                        # 基础 Agent 镜像
│   │   ├── Dockerfile.agent-base        # Jenkins Agent 基础
│   │   ├── Dockerfile.agent-docker      # Docker Agent (DooD)
│   │   ├── entrypoint-agent-base.sh     # 基础入口脚本
│   │   └── entrypoint-agent-docker.sh   # Docker 入口脚本
│   ├── dotnet/                      # .NET Agent
│   │   ├── Dockerfile.dotnet            # .NET Agent 镜像
│   │   ├── entrypoint-dotnet.sh         # .NET 入口脚本
│   │   └── docker-compose-dotnet.yml  # 部署配置
│   └── doc/                         # 文档
│       ├── DOCKER_SOCKET_CONFIG.md
│       └── README.md
│
├── docs/
│   ├── DOCKER_AGENT_GUIDE.md        # 详细指南
│   └── AGENT_DEPLOYMENT_COMPARISON.md   # 方案对比
```

---

## 测试 Pipeline

```groovy
pipeline {
    agent none

    stages {
        stage('测试 .NET Agent') {
            agent { label 'dotnet' }
            steps {
                sh 'dotnet --version'
            }
        }

        stage('测试 Java Agent') {
            agent { label 'java' }
            steps {
                sh 'mvn -version'
            }
        }

        stage('测试 Vue Agent') {
            agent { label 'vue' }
            steps {
                sh 'node -v && npm -v'
            }
        }
    }
}
```

---

## 资源配置

### 推荐配置（24个微服务）

| Agent | 数量 | CPU | 内存 | 并发 |
|-------|------|-----|------|------|
| .NET  | 2    | 4核 | 8GB  | 4    |
| Java  | 2    | 4核 | 8GB  | 4    |
| Vue   | 1    | 2核 | 4GB  | 4    |
| **总计** | **5** | **22核** | **40GB** | **20** |

---

## 故障排查

### 问题 1: Agent 无法连接

```bash
# 查看日志
docker logs jenkins-agent-dotnet-01

# 检查网络
docker exec jenkins-agent-dotnet-01 ping jenkins-master

# 检查环境变量
docker exec jenkins-agent-dotnet-01 env | grep JENKINS
```

### 问题 2: 构建失败

```bash
# 进入容器检查
docker exec -it jenkins-agent-dotnet-01 bash

# 验证工具安装
dotnet --version
mvn --version
node --version
```

---

## 下一步

- 📖 详细指南: [DOCKER_AGENT_GUIDE.md](DOCKER_AGENT_GUIDE.md)
- 📊 方案对比: [AGENT_DEPLOYMENT_COMPARISON.md](AGENT_DEPLOYMENT_COMPARISON.md)
- 🏗️ 架构对比: [ARCHITECTURE_COMPARISON.md](ARCHITECTURE_COMPARISON.md)

---

**部署时间**: 30分钟
**推荐指数**: ⭐⭐⭐⭐⭐（离线环境）
