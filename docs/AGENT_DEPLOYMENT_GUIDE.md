# Jenkins Agent 节点部署指南

## 架构说明

```
┌─────────────────────────────────────────────────────────┐
│  Jenkins Master (Docker 容器)                           │
│  - 调度任务                                             │
│  - 管理配置                                             │
│  - 149个插件                                            │
│  - 不执行构建任务                                        │
└─────────────────────────────────────────────────────────┘
                         │
                         │ SSH 连接
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Agent-DotNet │  │ Agent-Java   │  │ Agent-Vue    │
│ (虚拟机)      │  │ (虚拟机)      │  │ (虚拟机)      │
│              │  │              │  │              │
│ 直接安装：    │  │ 直接安装：    │  │ 直接安装：    │
│ - .NET SDK   │  │ - JDK 17     │  │ - Node.js    │
│ - MSBuild    │  │ - Maven      │  │ - npm/yarn   │
│ - SonarScan  │  │ - Gradle     │  │ - nginx      │
└──────────────┘  └──────────────┘  └──────────────┘
```

**优势**：
- ✅ Master 容器化：易于升级、回滚、迁移
- ✅ Agent 传统部署：性能好、工具链完整、运维简单
- ✅ SSH 连接：安全、稳定、配置简单

---

## 前置准备

### 网络规划

假设你有以下虚拟机：

| 主机名 | IP 地址 | 角色 | 用途 |
|--------|---------|------|------|
| jenkins-master | 192.168.1.100 | Master | 调度和管理 |
| jenkins-agent-dotnet | 192.168.1.101 | Agent | .NET 微服务构建 |
| jenkins-agent-java | 192.168.1.102 | Agent | Java 微服务构建 |
| jenkins-agent-vue | 192.168.1.103 | Agent | Vue 前端构建 |
| jenkins-agent-cpp | 192.168.1.104 | Agent | C++ 服务构建（可选） |

### 所有 Agent 节点都需要

- **操作系统**: CentOS 7/8, Ubuntu 20.04/22.04, 或 RHEL 7/8
- **最低配置**: 4核CPU、8GB内存、50GB磁盘
- **推荐配置**: 8核CPU、16GB内存、100GB磁盘
- **网络**: 能够被 Master SSH 连接

---

## 步骤 1: Agent 基础环境准备

### 1.1 创建 Jenkins 用户

在**每台 Agent 虚拟机**上执行：

```bash
# 创建 jenkins 用户
sudo useradd -m -s /bin/bash jenkins

# 设置密码（可选，SSH 密钥登录可不设置）
sudo passwd jenkins

# 创建工作目录
sudo mkdir -p /home/jenkins/workspace
sudo chown -R jenkins:jenkins /home/jenkins
```

### 1.2 安装 Java（必须）

Jenkins Agent 需要 Java 运行时：

```bash
# CentOS/RHEL
sudo yum install -y java-17-openjdk java-17-openjdk-devel

# Ubuntu/Debian
sudo apt update
sudo apt install -y openjdk-17-jdk

# 验证安装
java -version
```

### 1.3 配置 SSH 密钥认证

#### 在 Master 容器中生成密钥对

```bash
# 进入 Master 容器
docker exec -it jenkins-master-test bash

# 切换到 jenkins 用户
su - jenkins

# 生成 SSH 密钥对（不设置密码，直接回车）
ssh-keygen -t rsa -b 4096 -f ~/.ssh/id_rsa -N ""

# 查看公钥（后面要用）
cat ~/.ssh/id_rsa

# 查看私钥（复制保存）
cat ~/.ssh/id_rsa
```

#### 将公钥部署到 Agent 节点

在**每台 Agent 虚拟机**上执行：

```bash
# 切换到 jenkins 用户
sudo su - jenkins

# 创建 .ssh 目录
mkdir -p ~/.ssh
chmod 700 ~/.ssh

# 将 Master 的公钥添加到 authorized_keys
cat >> ~/.ssh/authorized_keys << 'EOF'
# 这里粘贴 Master 的公钥内容（id_rsa.pub）
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQD... jenkins@master
EOF

chmod 600 ~/.ssh/authorized_keys
```

#### 测试 SSH 连接

回到 Master 容器，测试连接：

```bash
# 在 Master 容器中测试
ssh jenkins@192.168.1.101
# 第一次连接会提示 yes/no，输入 yes
# 如果能免密登录，说明配置成功
```

---

## 步骤 2: 安装构建工具

根据 Agent 节点的用途，安装相应的构建工具。

### 2.1 .NET Agent 配置（agent-dotnet）

```bash
# 在 agent-dotnet 虚拟机上执行

# 1. 安装 .NET SDK 8.0
wget https://dot.net/v1/dotnet-install.sh
chmod +x dotnet-install.sh
sudo ./dotnet-install.sh --channel 8.0 --install-dir /usr/share/dotnet

# 添加到 PATH
echo 'export DOTNET_ROOT=/usr/share/dotnet' | sudo tee -a /etc/profile
echo 'export PATH=$PATH:/usr/share/dotnet' | sudo tee -a /etc/profile
source /etc/profile

# 验证
dotnet --version

# 2. 安装 Git（如果没有）
sudo yum install -y git   # CentOS/RHEL
# 或
sudo apt install -y git    # Ubuntu/Debian

# 3. 安装 SonarQube Scanner（可选）
wget https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-5.0.1.3006-linux.zip
unzip sonar-scanner-cli-5.0.1.3006-linux.zip
sudo mv sonar-scanner-5.0.1.3006-linux /opt/sonar-scanner
echo 'export PATH=$PATH:/opt/sonar-scanner/bin' | sudo tee -a /etc/profile
source /etc/profile

# 4. 安装 MSBuild（如果需要构建 .NET Framework 项目）
# 这个比较复杂，建议使用 dotnet build 替代
```

### 2.2 Java Agent 配置（agent-java）

```bash
# 在 agent-java 虚拟机上执行

# 1. 安装 JDK 17（前面已装）
# 如果需要多版本，可以安装 JDK 11 和 JDK 17

# 2. 安装 Maven
wget https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz
tar -xzf apache-maven-3.9.6-bin.tar.gz
sudo mv apache-maven-3.9.6 /opt/maven

# 配置环境变量
echo 'export MAVEN_HOME=/opt/maven' | sudo tee -a /etc/profile
echo 'export PATH=$PATH:$MAVEN_HOME/bin' | sudo tee -a /etc/profile
source /etc/profile

# 验证
mvn -version

# 3. 安装 Gradle（可选）
wget https://services.gradle.org/distributions/gradle-8.5-bin.zip
unzip gradle-8.5-bin.zip
sudo mv gradle-8.5 /opt/gradle

echo 'export GRADLE_HOME=/opt/gradle' | sudo tee -a /etc/profile
echo 'export PATH=$PATH:$GRADLE_HOME/bin' | sudo tee -a /etc/profile
source /etc/profile

# 验证
gradle -version

# 4. 安装 Git
sudo yum install -y git
```

### 2.3 Vue/Node.js Agent 配置（agent-vue）

```bash
# 在 agent-vue 虚拟机上执行

# 1. 安装 Node.js 18 LTS
curl -fsSL https://rpm.nodesource.com/setup_18.x | sudo bash -
sudo yum install -y nodejs

# 或者使用 nvm（推荐）
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
source ~/.bashrc
nvm install 18
nvm use 18

# 验证
node -v
npm -v

# 2. 安装 Yarn（可选）
npm install -g yarn

# 3. 安装 pnpm（可选）
npm install -g pnpm

# 4. 安装 Git
sudo yum install -y git

# 5. 安装 nginx（用于前端部署测试）
sudo yum install -y nginx
```

### 2.4 C++ Agent 配置（agent-cpp，可选）

```bash
# 在 agent-cpp 虚拟机上执行

# 安装 GCC 编译器
sudo yum groupinstall -y "Development Tools"
sudo yum install -y gcc-c++ make cmake

# 验证
gcc --version
g++ --version
cmake --version
```

---

## 步骤 3: 在 Jenkins Master 中配置 Agent 节点

### 3.1 添加 SSH 凭证

1. 登录 Jenkins Web UI: `http://192.168.1.100:8080`

2. **Manage Jenkins** → **Credentials** → **System** → **Global credentials**

3. 点击 **Add Credentials**

4. 配置：
   - **Kind**: `SSH Username with private key`
   - **Scope**: `Global`
   - **ID**: `jenkins-ssh-key`
   - **Description**: `Jenkins Agent SSH密钥`
   - **Username**: `jenkins`
   - **Private Key**:
     - 选择 **Enter directly**
     - 粘贴前面生成的 **私钥**（id_rsa 文件内容）
   - **Passphrase**: 留空（如果密钥没有密码）

5. 点击 **Create**

### 3.2 添加 .NET Agent 节点

1. **Manage Jenkins** → **Nodes** → **New Node**

2. 配置：
   - **Node name**: `agent-dotnet-01`
   - 选择 **Permanent Agent**
   - 点击 **Create**

3. 详细配置：

```
Name: agent-dotnet-01
Description: .NET微服务构建节点
Number of executors: 4                          # 并发构建数量
Remote root directory: /home/jenkins/workspace
Labels: dotnet dotnet-8 microservice            # 标签（重要！）
Usage: Use this node as much as possible
Launch method: Launch agents via SSH

Launch agents via SSH:
  Host: 192.168.1.101
  Credentials: jenkins-ssh-key（选择前面创建的）
  Host Key Verification Strategy: Non verifying Verification Strategy

Advanced:
  JavaPath: /usr/bin/java
  JVM Options: -Xmx2g
```

4. 点击 **Save**

5. 查看节点状态，应该显示 **Agent successfully connected and online**

### 3.3 添加其他 Agent 节点

重复上述步骤，添加其他节点：

**Java Agent**:
```
Node name: agent-java-01
Remote root directory: /home/jenkins/workspace
Labels: java java-17 maven gradle
Host: 192.168.1.102
Credentials: jenkins-ssh-key
```

**Vue Agent**:
```
Node name: agent-vue-01
Remote root directory: /home/jenkins/workspace
Labels: vue nodejs frontend npm
Host: 192.168.1.103
Credentials: jenkins-ssh-key
```

**C++ Agent** (可选):
```
Node name: agent-cpp-01
Remote root directory: /home/jenkins/workspace
Labels: cpp gcc cmake
Host: 192.168.1.104
Credentials: jenkins-ssh-key
```

---

## 步骤 4: 测试 Agent 节点

### 4.1 创建测试 Pipeline

1. **Dashboard** → **新建任务**
2. 名称: `test-agent-dotnet`
3. 类型: **Pipeline**
4. Pipeline 脚本：

```groovy
pipeline {
    agent { label 'dotnet' }  // 使用标签选择 Agent

    stages {
        stage('环境信息') {
            steps {
                sh '''
                    echo "====== 主机信息 ======"
                    hostname
                    uname -a

                    echo "====== Java 版本 ======"
                    java -version

                    echo "====== .NET 版本 ======"
                    dotnet --version

                    echo "====== Git 版本 ======"
                    git --version

                    echo "====== 磁盘空间 ======"
                    df -h

                    echo "====== 内存信息 ======"
                    free -h
                '''
            }
        }

        stage('构建测试') {
            steps {
                sh '''
                    # 创建一个简单的 .NET 项目
                    dotnet new console -n TestApp -o ./test-app
                    cd test-app
                    dotnet build
                    dotnet run
                '''
            }
        }
    }

    post {
        success {
            echo '✅ Agent 节点工作正常！'
        }
        failure {
            echo '❌ Agent 节点测试失败！'
        }
        always {
            cleanWs()  // 清理工作空间
        }
    }
}
```

5. 保存并**立即构建**

6. 查看构建日志，应该看到：
   - 任务被调度到 `agent-dotnet-01` 节点
   - 显示 .NET 版本信息
   - 成功构建并运行测试项目

---

## 步骤 5: 配置 Master 不执行构建

为了确保 Master 只做调度，不执行构建任务：

1. **Manage Jenkins** → **Nodes** → **Built-In Node**

2. 点击 **Configure**

3. **Number of executors**: 改为 `0`

4. **Save**

现在所有构建任务都会被调度到 Agent 节点执行。

---

## 实际项目 Pipeline 示例

### .NET 微服务 Pipeline

```groovy
// 在项目的 Jenkinsfile 中
pipeline {
    agent { label 'dotnet' }  // 使用 .NET Agent

    environment {
        PROJECT_NAME = 'UserService'
        NEXUS_URL = 'http://nexus.internal.com'
    }

    stages {
        stage('代码检出') {
            steps {
                checkout scm
            }
        }

        stage('恢复依赖') {
            steps {
                sh 'dotnet restore ${PROJECT_NAME}.sln'
            }
        }

        stage('编译') {
            steps {
                sh 'dotnet build ${PROJECT_NAME}.sln --configuration Release'
            }
        }

        stage('单元测试') {
            steps {
                sh 'dotnet test ${PROJECT_NAME}.Tests/*.csproj --logger "trx"'
            }
        }

        stage('发布') {
            steps {
                sh 'dotnet publish ${PROJECT_NAME}/*.csproj -c Release -o ./publish'
            }
        }
    }
}
```

### Java 微服务 Pipeline

```groovy
pipeline {
    agent { label 'java' }  // 使用 Java Agent

    tools {
        maven 'Maven-3.9'
        jdk 'JDK-17'
    }

    stages {
        stage('编译') {
            steps {
                sh 'mvn clean compile'
            }
        }

        stage('测试') {
            steps {
                sh 'mvn test'
            }
        }

        stage('打包') {
            steps {
                sh 'mvn package -DskipTests'
            }
        }
    }
}
```

### Vue 前端 Pipeline

```groovy
pipeline {
    agent { label 'vue' }  // 使用 Vue Agent

    stages {
        stage('安装依赖') {
            steps {
                sh 'npm install'
            }
        }

        stage('构建') {
            steps {
                sh 'npm run build'
            }
        }

        stage('打包') {
            steps {
                sh 'tar -czf dist.tar.gz dist/'
                archiveArtifacts artifacts: 'dist.tar.gz'
            }
        }
    }
}
```

---

## 自动化部署脚本

### Agent 自动部署脚本

创建 `agent-setup.sh`（在离线环境需要提前准备安装包）：

```bash
#!/bin/bash
# Agent 节点自动部署脚本

set -e

echo "====== Jenkins Agent 节点部署 ======"

# 配置变量
AGENT_TYPE=$1  # dotnet, java, vue, cpp
MASTER_IP="192.168.1.100"

if [ -z "$AGENT_TYPE" ]; then
    echo "用法: $0 <dotnet|java|vue|cpp>"
    exit 1
fi

# 1. 创建 jenkins 用户
echo "创建 jenkins 用户..."
sudo useradd -m -s /bin/bash jenkins || true
sudo mkdir -p /home/jenkins/workspace
sudo chown -R jenkins:jenkins /home/jenkins

# 2. 安装 Java 17
echo "安装 Java 17..."
sudo yum install -y java-17-openjdk java-17-openjdk-devel

# 3. 安装 Git
echo "安装 Git..."
sudo yum install -y git

# 4. 配置 SSH（需要手动添加公钥）
echo "配置 SSH..."
sudo su - jenkins -c "mkdir -p ~/.ssh && chmod 700 ~/.ssh"

echo "请将 Master 的公钥添加到 /home/jenkins/.ssh/authorized_keys"
echo "然后执行: chmod 600 /home/jenkins/.ssh/authorized_keys"

# 5. 根据类型安装构建工具
case $AGENT_TYPE in
    dotnet)
        echo "安装 .NET SDK..."
        # 这里添加 .NET SDK 安装逻辑
        ;;
    java)
        echo "安装 Maven..."
        # 这里添加 Maven 安装逻辑
        ;;
    vue)
        echo "安装 Node.js..."
        # 这里添加 Node.js 安装逻辑
        ;;
    cpp)
        echo "安装 GCC..."
        sudo yum groupinstall -y "Development Tools"
        ;;
    *)
        echo "未知类型: $AGENT_TYPE"
        exit 1
        ;;
esac

echo "====== 部署完成 ======"
echo "下一步："
echo "1. 将 Master 公钥添加到 /home/jenkins/.ssh/authorized_keys"
echo "2. 在 Jenkins Web UI 中添加此节点"
```

---

## 监控和维护

### 查看 Agent 状态

1. **Web UI**: Manage Jenkins → Nodes
2. 查看每个节点的状态、磁盘空间、响应时间

### Agent 日志

在 Agent 虚拟机上：

```bash
# 查看 Agent 日志
sudo tail -f /home/jenkins/workspace/remoting/logs/remoting.log
```

### 重启 Agent

```bash
# 在 Web UI 中：
# Nodes → 选择节点 → Disconnect
# 然后点击 Launch agent
```

---

## 常见问题

### 1. SSH 连接失败

**症状**: Agent 显示 "Connection refused" 或 "Permission denied"

**排查**:
```bash
# 在 Master 容器中测试
ssh -v jenkins@192.168.1.101

# 检查 Agent 的 SSH 服务
sudo systemctl status sshd

# 检查防火墙
sudo firewall-cmd --list-all
sudo firewall-cmd --permanent --add-service=ssh
sudo firewall-cmd --reload
```

### 2. 构建工具找不到

**症状**: "dotnet: command not found"

**解决**:
```bash
# 在 Agent 节点配置环境变量
echo 'export PATH=$PATH:/usr/share/dotnet' | sudo tee -a /etc/profile
source /etc/profile

# 或者在 Jenkinsfile 中指定路径
environment {
    PATH = "/usr/share/dotnet:${env.PATH}"
}
```

### 3. 磁盘空间不足

**解决**:
```bash
# 清理旧的工作空间
sudo du -sh /home/jenkins/workspace/*
sudo rm -rf /home/jenkins/workspace/old-jobs

# 在 Jenkins 中配置自动清理
# Manage Jenkins → Configure System → Workspace Cleanup
```

---

## 性能优化建议

### 1. Agent 节点规格

对于 24 个微服务的场景：

| Agent 类型 | 推荐配置 | 数量 | 并发构建数 |
|-----------|---------|------|-----------|
| .NET Agent | 8核16GB | 2-3台 | 每台4个 |
| Java Agent | 8核16GB | 2-3台 | 每台4个 |
| Vue Agent | 4核8GB | 1-2台 | 每台4个 |

### 2. 启用 Agent 缓存

```groovy
// 在 Jenkinsfile 中启用依赖缓存
pipeline {
    agent { label 'dotnet' }

    options {
        // 保留构建缓存
        skipDefaultCheckout()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps {
                // 增量检出（保留 .git 目录）
                checkout scm
            }
        }
    }
}
```

### 3. 配置 Maven/NuGet 本地缓存

```bash
# 在 Agent 节点配置
sudo mkdir -p /home/jenkins/.m2
sudo mkdir -p /home/jenkins/.nuget

# Maven settings.xml 指向 Nexus
# NuGet.config 指向 Nexus
```

---

## 总结

### ✅ 这个架构的优势

1. **Master 容器化**：易于升级、回滚、迁移
2. **Agent 虚拟机**：性能好、工具链完整、运维简单
3. **SSH 连接**：安全、稳定、企业级标准
4. **标签调度**：灵活分配构建任务到对应的 Agent

### 📋 部署检查清单

- [ ] 所有 Agent 节点创建 jenkins 用户
- [ ] 所有 Agent 节点安装 Java 17
- [ ] SSH 密钥配置完成，免密登录测试成功
- [ ] 各 Agent 节点安装对应的构建工具
- [ ] Jenkins Web UI 中添加所有 Agent 节点
- [ ] Agent 节点状态显示 "online"
- [ ] 创建测试 Pipeline 验证 Agent 工作正常
- [ ] Master 节点 executors 设置为 0

### 🎯 下一步

1. 为你的 24 个微服务创建 Pipeline（使用正确的 Agent 标签）
2. 配置 JCasC 管理 Agent 节点配置
3. 设置监控和告警
4. 配置自动备份

---

**部署时间估计**: 每个 Agent 节点约 30-60 分钟
**推荐 Agent 数量**: 至少 4-5 台（根据 24 个微服务的并发构建需求）
