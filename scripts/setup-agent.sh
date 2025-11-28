#!/bin/bash
# Jenkins Agent 节点自动部署脚本
# 用法: ./setup-agent.sh <dotnet|java|vue|cpp>

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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

# 检查参数
if [ $# -eq 0 ]; then
    echo "用法: $0 <dotnet|java|vue|cpp>"
    echo ""
    echo "Agent 类型："
    echo "  dotnet  - .NET 微服务构建节点"
    echo "  java    - Java 微服务构建节点"
    echo "  vue     - Vue 前端构建节点"
    echo "  cpp     - C++ 服务构建节点"
    exit 1
fi

AGENT_TYPE=$1

log_info "====== Jenkins Agent 部署脚本 ======"
log_info "Agent 类型: $AGENT_TYPE"
log_info "主机名: $(hostname)"
log_info "IP 地址: $(hostname -I | awk '{print $1}')"

# 检查是否为 root 或有 sudo 权限
if [ "$EUID" -ne 0 ]; then
    if ! sudo -n true 2>/dev/null; then
        log_error "需要 root 权限或 sudo 权限"
        exit 1
    fi
fi

# 1. 创建 jenkins 用户
log_info "步骤 1/6: 创建 jenkins 用户"
if id "jenkins" &>/dev/null; then
    log_warn "jenkins 用户已存在，跳过创建"
else
    sudo useradd -m -s /bin/bash jenkins
    log_info "jenkins 用户创建成功"
fi

sudo mkdir -p /home/jenkins/workspace
sudo chown -R jenkins:jenkins /home/jenkins
log_info "工作目录创建成功: /home/jenkins/workspace"

# 2. 配置 SSH
log_info "步骤 2/6: 配置 SSH"
sudo su - jenkins -c "mkdir -p ~/.ssh && chmod 700 ~/.ssh"

if [ ! -f /home/jenkins/.ssh/authorized_keys ]; then
    sudo su - jenkins -c "touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
    log_warn "请将 Master 的公钥添加到: /home/jenkins/.ssh/authorized_keys"
    log_warn "然后执行: sudo chmod 600 /home/jenkins/.ssh/authorized_keys"
else
    log_info "authorized_keys 已存在"
fi

# 3. 安装基础依赖
log_info "步骤 3/6: 安装基础依赖"

# 检测操作系统
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS=$ID
else
    log_error "无法检测操作系统类型"
    exit 1
fi

log_info "操作系统: $OS"

case $OS in
    centos|rhel)
        PKG_MANAGER="yum"
        ;;
    ubuntu|debian)
        PKG_MANAGER="apt"
        sudo apt update
        ;;
    *)
        log_error "不支持的操作系统: $OS"
        exit 1
        ;;
esac

# 安装 Java 17
log_info "安装 Java 17..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    log_warn "Java 已安装: $JAVA_VERSION"
else
    if [ "$PKG_MANAGER" = "yum" ]; then
        sudo yum install -y java-17-openjdk java-17-openjdk-devel
    else
        sudo apt install -y openjdk-17-jdk
    fi
    log_info "Java 17 安装成功"
fi

# 安装 Git
log_info "安装 Git..."
if command -v git &> /dev/null; then
    GIT_VERSION=$(git --version)
    log_warn "Git 已安装: $GIT_VERSION"
else
    sudo $PKG_MANAGER install -y git
    log_info "Git 安装成功"
fi

# 4. 根据类型安装构建工具
log_info "步骤 4/6: 安装构建工具（$AGENT_TYPE）"

case $AGENT_TYPE in
    dotnet)
        log_info "安装 .NET SDK 8.0..."

        # 检查是否已安装
        if command -v dotnet &> /dev/null; then
            DOTNET_VERSION=$(dotnet --version)
            log_warn ".NET SDK 已安装: $DOTNET_VERSION"
        else
            # 下载并安装 .NET SDK
            wget https://dot.net/v1/dotnet-install.sh -O /tmp/dotnet-install.sh
            chmod +x /tmp/dotnet-install.sh
            sudo /tmp/dotnet-install.sh --channel 8.0 --install-dir /usr/share/dotnet

            # 添加到 PATH
            if ! grep -q "DOTNET_ROOT" /etc/profile; then
                echo 'export DOTNET_ROOT=/usr/share/dotnet' | sudo tee -a /etc/profile
                echo 'export PATH=$PATH:/usr/share/dotnet' | sudo tee -a /etc/profile
            fi

            # 当前会话生效
            export DOTNET_ROOT=/usr/share/dotnet
            export PATH=$PATH:/usr/share/dotnet

            log_info ".NET SDK 安装成功"
        fi

        # 验证
        /usr/share/dotnet/dotnet --version
        ;;

    java)
        log_info "安装 Maven..."

        if command -v mvn &> /dev/null; then
            MVN_VERSION=$(mvn -version | head -n 1)
            log_warn "Maven 已安装: $MVN_VERSION"
        else
            # 下载并安装 Maven
            MAVEN_VERSION="3.9.6"
            wget https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz -O /tmp/maven.tar.gz
            tar -xzf /tmp/maven.tar.gz -C /tmp
            sudo mv /tmp/apache-maven-${MAVEN_VERSION} /opt/maven

            # 添加到 PATH
            if ! grep -q "MAVEN_HOME" /etc/profile; then
                echo 'export MAVEN_HOME=/opt/maven' | sudo tee -a /etc/profile
                echo 'export PATH=$PATH:$MAVEN_HOME/bin' | sudo tee -a /etc/profile
            fi

            # 当前会话生效
            export MAVEN_HOME=/opt/maven
            export PATH=$PATH:$MAVEN_HOME/bin

            log_info "Maven 安装成功"
        fi

        # 验证
        /opt/maven/bin/mvn -version

        # 可选：安装 Gradle
        log_info "可选：安装 Gradle？(y/n)"
        read -r -n 1 INSTALL_GRADLE
        echo
        if [ "$INSTALL_GRADLE" = "y" ]; then
            if command -v gradle &> /dev/null; then
                log_warn "Gradle 已安装"
            else
                GRADLE_VERSION="8.5"
                wget https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O /tmp/gradle.zip
                sudo unzip /tmp/gradle.zip -d /opt
                sudo mv /opt/gradle-${GRADLE_VERSION} /opt/gradle

                if ! grep -q "GRADLE_HOME" /etc/profile; then
                    echo 'export GRADLE_HOME=/opt/gradle' | sudo tee -a /etc/profile
                    echo 'export PATH=$PATH:$GRADLE_HOME/bin' | sudo tee -a /etc/profile
                fi

                log_info "Gradle 安装成功"
            fi
        fi
        ;;

    vue)
        log_info "安装 Node.js..."

        if command -v node &> /dev/null; then
            NODE_VERSION=$(node -v)
            log_warn "Node.js 已安装: $NODE_VERSION"
        else
            # 安装 Node.js 18 LTS
            if [ "$PKG_MANAGER" = "yum" ]; then
                curl -fsSL https://rpm.nodesource.com/setup_18.x | sudo bash -
                sudo yum install -y nodejs
            else
                curl -fsSL https://deb.nodesource.com/setup_18.x | sudo bash -
                sudo apt install -y nodejs
            fi

            log_info "Node.js 安装成功"
        fi

        # 验证
        node -v
        npm -v

        # 安装 Yarn
        log_info "安装 Yarn..."
        if command -v yarn &> /dev/null; then
            log_warn "Yarn 已安装"
        else
            sudo npm install -g yarn
            log_info "Yarn 安装成功"
        fi
        ;;

    cpp)
        log_info "安装 GCC 和开发工具..."

        if command -v gcc &> /dev/null; then
            GCC_VERSION=$(gcc --version | head -n 1)
            log_warn "GCC 已安装: $GCC_VERSION"
        else
            if [ "$PKG_MANAGER" = "yum" ]; then
                sudo yum groupinstall -y "Development Tools"
                sudo yum install -y gcc-c++ make cmake
            else
                sudo apt install -y build-essential gcc g++ make cmake
            fi

            log_info "GCC 和开发工具安装成功"
        fi

        # 验证
        gcc --version
        g++ --version
        cmake --version
        ;;

    *)
        log_error "未知的 Agent 类型: $AGENT_TYPE"
        exit 1
        ;;
esac

# 5. 配置防火墙（如果需要）
log_info "步骤 5/6: 配置防火墙"

if command -v firewall-cmd &> /dev/null; then
    log_info "检测到 firewalld，确保 SSH 端口开放..."
    sudo firewall-cmd --permanent --add-service=ssh
    sudo firewall-cmd --reload
    log_info "防火墙配置完成"
else
    log_warn "未检测到 firewalld，跳过防火墙配置"
fi

# 6. 显示环境信息
log_info "步骤 6/6: 环境验证"

echo ""
echo "====== 环境信息 ======"
echo "主机名: $(hostname)"
echo "IP 地址: $(hostname -I | awk '{print $1}')"
echo "操作系统: $(cat /etc/os-release | grep PRETTY_NAME | cut -d'"' -f2)"
echo "Java 版本: $(java -version 2>&1 | head -n 1)"

case $AGENT_TYPE in
    dotnet)
        echo ".NET 版本: $(/usr/share/dotnet/dotnet --version)"
        ;;
    java)
        echo "Maven 版本: $(/opt/maven/bin/mvn -version | head -n 1)"
        ;;
    vue)
        echo "Node.js 版本: $(node -v)"
        echo "npm 版本: $(npm -v)"
        ;;
    cpp)
        echo "GCC 版本: $(gcc --version | head -n 1)"
        ;;
esac

echo "Git 版本: $(git --version)"
echo ""

# 7. 后续步骤提示
log_info "====== 部署完成 ======"
echo ""
echo "✅ Jenkins Agent 基础环境部署成功！"
echo ""
echo "📋 后续步骤："
echo ""
echo "1️⃣  在 Master 容器中生成 SSH 密钥对："
echo "   docker exec -it jenkins-master bash"
echo "   su - jenkins"
echo "   ssh-keygen -t rsa -b 4096 -N \"\""
echo "   cat ~/.ssh/id_rsa.pub"
echo ""
echo "2️⃣  将 Master 的公钥添加到此节点："
echo "   sudo su - jenkins"
echo "   echo 'ssh-rsa AAAAB...' >> ~/.ssh/authorized_keys"
echo "   chmod 600 ~/.ssh/authorized_keys"
echo ""
echo "3️⃣  在 Master 上测试 SSH 连接："
echo "   ssh jenkins@$(hostname -I | awk '{print $1}')"
echo ""
echo "4️⃣  在 Jenkins Web UI 中添加此节点："
echo "   Manage Jenkins → Nodes → New Node"
echo "   - Node name: agent-${AGENT_TYPE}-01"
echo "   - Remote root directory: /home/jenkins/workspace"
echo "   - Labels: ${AGENT_TYPE}"
echo "   - Launch method: Launch agents via SSH"
echo "   - Host: $(hostname -I | awk '{print $1}')"
echo ""
echo "5️⃣  验证节点状态："
echo "   在 Nodes 页面查看节点是否在线"
echo ""

log_info "如需帮助，请查看: AGENT_DEPLOYMENT_GUIDE.md"
