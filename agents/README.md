# Jenkins Agent 容器化方案

## 为什么选择容器化 Agent？

**问题**: 在离线内网环境下，直接在虚拟机上安装 .NET SDK、Maven、Node.js 等工具链非常困难：
- ❌ 缺少依赖包
- ❌ 需要联网下载
- ❌ 配置复杂且容易出错
- ❌ 环境不一致

**解决方案**: 在外网构建好 Agent 容器镜像，打包导入内网
- ✅ 一次构建，多处部署
- ✅ 环境完全一致
- ✅ 避免在内网安装工具链
- ✅ 易于管理和扩展

---

## 架构概览

```
外网环境                                    内网环境
┌──────────────────────┐                  ┌──────────────────────┐
│  构建 Agent 镜像      │                  │  Jenkins Master      │
│  - .NET SDK 8.0      │                  │  (容器)              │
│  - Maven 3.9.6       │                  └──────────────────────┘
│  - Node.js 18        │                           │
│  - 各种工具          │      打包镜像              │
│                      │  ─────────────▶    ┌──────┴──────┐
│  导出为 tar 文件      │                   │  │  │  │  │  │
└──────────────────────┘                   ▼  ▼  ▼  ▼  ▼  ▼
                                          Agent 容器（持久运行）
                                          - dotnet-01
                                          - dotnet-02
                                          - java-01
                                          - java-02
                                          - vue-01
```

---

## 文件清单

```
agents/
├── Dockerfile.dotnet          # .NET Agent 镜像定义
├── Dockerfile.java            # Java Agent 镜像定义
├── Dockerfile.vue             # Vue Agent 镜像定义
├── build-agents.sh            # 构建脚本（外网使用）
├── import-agents.sh           # 导入脚本（内网使用）
└── README.md                  # 本文件

../
├── docker-compose-agents.yml  # Agent 容器编排配置
└── DOCKER_AGENT_GUIDE.md      # 详细部署指南
```

---

## 快速开始

### 在外网：构建镜像

```bash
cd /mnt/d/Repositories/JenkinsDeploy/agents

# 构建所有 Agent 镜像
bash build-agents.sh

# 等待构建完成（约10-15分钟）
# 生成文件（在上级目录）：
# - jenkins-agent-dotnet-1.0.tar (约1.5GB)
# - jenkins-agent-java-1.0.tar (约1.2GB)
# - jenkins-agent-vue-1.0.tar (约1.0GB)
```

### 上传到内网

```bash
# 将 tar 文件上传到内网服务器
scp ../jenkins-agent-*.tar root@internal-server:/opt/jenkins/
scp ../jenkins-agent-*.tar.md5 root@internal-server:/opt/jenkins/
```

### 在内网：导入镜像

```bash
cd /opt/jenkins

# 导入所有镜像
bash import-agents.sh

# 或者手动导入
docker load -i jenkins-agent-dotnet-1.0.tar
docker load -i jenkins-agent-java-1.0.tar
docker load -i jenkins-agent-vue-1.0.tar

# 验证
docker images | grep jenkins-agent
```

### 配置 Jenkins

详细步骤请查看 [DOCKER_AGENT_GUIDE.md](../DOCKER_AGENT_GUIDE.md)

---

## 镜像内容

### .NET Agent (jenkins-agent-dotnet:1.0)

**基础**: jenkins/inbound-agent:latest-jdk17

**包含工具**:
- .NET SDK 8.0
- .NET SDK 6.0（兼容旧项目）
- Git
- dotnet-sonarscanner

**镜像大小**: ~1.5 GB

**用途**: 构建 .NET 微服务

---

### Java Agent (jenkins-agent-java:1.0)

**基础**: jenkins/inbound-agent:latest-jdk17

**包含工具**:
- JDK 17
- Maven 3.9.6
- Gradle 8.5
- Git

**镜像大小**: ~1.2 GB

**用途**: 构建 Java/Spring Boot 微服务

---

### Vue Agent (jenkins-agent-vue:1.0)

**基础**: jenkins/inbound-agent:latest-jdk17

**包含工具**:
- Node.js 18 LTS
- npm, yarn, pnpm
- Vue CLI
- Angular CLI
- create-react-app
- Vite
- Git

**镜像大小**: ~1.0 GB

**用途**: 构建前端项目（Vue/React/Angular）

---

## 配置 Nexus 代理

如果内网有 Nexus 私服，可以在构建镜像前配置：

### .NET NuGet 配置

创建 `NuGet.Config`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="Nexus" value="http://nexus.internal.com/repository/nuget-group/" />
  </packageSources>
</configuration>
```

然后在 `Dockerfile.dotnet` 中添加：
```dockerfile
COPY NuGet.Config /home/jenkins/.nuget/NuGet/NuGet.Config
```

### Java Maven 配置

创建 `settings.xml`:
```xml
<settings>
  <mirrors>
    <mirror>
      <id>nexus</id>
      <mirrorOf>*</mirrorOf>
      <url>http://nexus.internal.com/repository/maven-public/</url>
    </mirror>
  </mirrors>
</settings>
```

然后在 `Dockerfile.java` 中添加：
```dockerfile
COPY settings.xml /home/jenkins/.m2/settings.xml
```

### Vue npm 配置

在 `Dockerfile.vue` 中添加：
```dockerfile
RUN npm config set registry http://nexus.internal.com/repository/npm-group/
RUN yarn config set registry http://nexus.internal.com/repository/npm-group/
```

---

## 资源配置建议

### 24个微服务场景

推荐配置：

| Agent 类型 | 容器数量 | 单容器资源 | 总资源 |
|-----------|---------|-----------|--------|
| .NET Agent | 2 | 4核8GB | 8核16GB |
| Java Agent | 2 | 4核8GB | 8核16GB |
| Vue Agent | 1 | 2核4GB | 2核4GB |
| **总计** | **5** | - | **18核36GB** |

**宿主机配置**: 至少 24核48GB

**并发构建能力**: 同时构建 16-20 个项目

---

## 与虚拟机 Agent 对比

| 特性 | 容器 Agent | 虚拟机 Agent |
|------|-----------|-------------|
| **离线部署难度** | ⭐⭐⭐⭐⭐ 简单<br>（镜像导入） | ⭐⭐ 困难<br>（需安装工具链） |
| **环境一致性** | ⭐⭐⭐⭐⭐ 完全一致 | ⭐⭐⭐ 手动保证 |
| **部署速度** | ⭐⭐⭐⭐⭐ 5分钟 | ⭐⭐ 30-60分钟 |
| **资源利用率** | ⭐⭐⭐⭐ 较好 | ⭐⭐⭐⭐⭐ 最好 |
| **构建性能** | ⭐⭐⭐⭐ 较快 | ⭐⭐⭐⭐⭐ 最快 |
| **扩容难度** | ⭐⭐⭐⭐⭐ 简单 | ⭐⭐⭐ 需配置 |
| **推荐度（离线）** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |

**结论**: 对于离线内网环境，容器化 Agent 明显更优！

---

## 常见问题

### 1. 镜像文件太大怎么办？

**问题**: 3个镜像文件总计约 3.7GB

**解决**:
- 使用移动硬盘或 U盘 传输
- 如果有内网文件服务器，先上传到文件服务器
- 考虑压缩传输（虽然 tar 已经压缩过了）

### 2. 能否在内网修改镜像配置？

**可以**: 导入镜像后，可以基于现有镜像创建新镜像

```bash
# 导入基础镜像
docker load -i jenkins-agent-dotnet-1.0.tar

# 基于它创建自定义镜像
cat > Dockerfile.custom <<EOF
FROM jenkins-agent-dotnet:1.0
USER root
# 添加自定义配置
RUN apt-get update && apt-get install -y xxx
USER jenkins
EOF

docker build -f Dockerfile.custom -t jenkins-agent-dotnet:1.0-custom .
```

### 3. 如何更新 Agent 镜像？

**步骤**:
1. 在外网重新构建镜像（修改版本号，如 v1.1）
2. 导出新镜像
3. 上传到内网并导入
4. 修改 `docker-compose-agents.yml` 中的镜像版本
5. 重启容器：`docker-compose -f docker-compose-agents.yml up -d`

### 4. 容器和虚拟机 Agent 能否混用？

**可以**: Jenkins 支持混合使用

```groovy
pipeline {
    stages {
        stage('构建 .NET') {
            agent { label 'dotnet' }  // 容器 Agent
            steps { ... }
        }

        stage('构建 Java') {
            agent { label 'java-vm' }  // 虚拟机 Agent
            steps { ... }
        }
    }
}
```

---

## 故障排查

### Agent 无法连接 Master

```bash
# 检查容器日志
docker logs jenkins-agent-dotnet-01

# 检查网络连通性
docker exec jenkins-agent-dotnet-01 ping jenkins-master

# 检查环境变量
docker exec jenkins-agent-dotnet-01 env | grep JENKINS
```

### 构建失败：命令找不到

```bash
# 进入容器验证
docker exec -it jenkins-agent-dotnet-01 bash

# 验证工具是否安装
dotnet --version
mvn --version
node --version
```

---

## 技术支持

详细配置指南: [DOCKER_AGENT_GUIDE.md](../DOCKER_AGENT_GUIDE.md)

架构对比分析: [ARCHITECTURE_COMPARISON.md](../ARCHITECTURE_COMPARISON.md)

---

## 总结

### ✅ 容器化 Agent 的优势（离线环境）

1. **部署简单**: 镜像导入即可，无需在内网安装工具链
2. **环境一致**: 所有 Agent 使用相同的镜像，避免环境差异
3. **快速扩容**: 复制配置即可增加 Agent 数量
4. **易于管理**: 容器化配置，统一管理
5. **完美适配**: 非常适合离线内网环境

### 🎯 推荐指数

对于你的场景（离线内网 + 24个微服务）:

**⭐⭐⭐⭐⭐ 强烈推荐！**

---

**构建时间**: 约15分钟（外网构建镜像）
**导入时间**: 约5分钟（内网导入镜像）
**总部署时间**: 约30分钟（包括配置Jenkins）
