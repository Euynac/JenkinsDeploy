# Jenkins CI 集成指南

本文档专门说明如何在 Jenkins CI 环境中运行端到端测试。

## 前置要求：Jenkins 节点环境配置

### Python 环境配置

E2E 测试需要 **Python 3.8 或更高版本**。在 Jenkins 构建节点上需要安装和配置 Python 环境。

#### 1. 安装 Python 3

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install -y python3 python3-pip python3-venv
# 如果上面的命令安装失败，可以尝试安装特定版本的 venv 模块
# 例如 Python 3.10:
sudo apt install -y python3.10-venv
# 或者 Python 3.11:
# sudo apt install -y python3.11-venv
```

**CentOS/RHEL:**
```bash
sudo yum install -y python3 python3-pip
# 或使用 dnf (CentOS 8+)
sudo dnf install -y python3 python3-pip
```

**macOS (使用 Homebrew):**
```bash
brew install python3
```

**验证安装:**
```bash
python3 --version  # 应该显示 Python 3.8 或更高版本
pip3 --version
```

#### 2. 安装 Docker 和 Docker Compose

**Docker:**
```bash
# Ubuntu/Debian
sudo apt install -y docker.io docker-compose

# CentOS/RHEL
sudo yum install -y docker docker-compose

# 启动 Docker 服务
sudo systemctl start docker
sudo systemctl enable docker

# 将 Jenkins 用户添加到 docker 组（避免使用 sudo）
sudo usermod -aG docker jenkins
```

**验证安装:**
```bash
docker --version
docker-compose --version  # 或 docker compose version
```

#### 3. Jenkins 节点配置检查清单

在 Jenkins 节点上执行以下检查：

```bash
# 1. 检查 Python
python3 --version
which python3

# 2. 检查 pip
pip3 --version
which pip3

# 3. 检查 Docker
docker --version
docker ps

# 4. 检查 Docker Compose
docker-compose --version || docker compose version

# 5. 检查网络连接（访问 GitLab）
curl -I https://ci-pilot.hohistar.com.cn/gitlab
```

#### 4. Jenkins 全局环境变量配置（可选）

在 Jenkins 管理界面配置全局环境变量（Manage Jenkins → Configure System → Global properties）：

```bash
# Python 相关
PYTHONUNBUFFERED=1  # 确保 Python 输出实时显示

# 测试相关（可选，代码中已有默认值）
TEST_DB_HOST=localhost
TEST_DB_PORT=5433
TEST_DB_NAME=todoapp_test
TEST_DB_USER=postgres
TEST_DB_PASSWORD=postgres
API_BASE_URL=http://localhost:5085
```

#### 5. 权限配置

确保 Jenkins 用户有足够权限：

```bash
# 检查 Jenkins 用户
id jenkins

# 确保 Jenkins 用户可以执行 Docker 命令（无需 sudo）
groups jenkins | grep docker

# 如果不在 docker 组，添加用户
sudo usermod -aG docker jenkins

# 重新登录或重启 Jenkins 服务使权限生效
sudo systemctl restart jenkins
```

**重要提示：** 如果使用 `jenkins-agent` 用户（而不是 `jenkins`），请将 `jenkins-agent` 替换为实际使用的用户名。

#### 6. Docker 权限问题排查和解决

这是最常见的问题。即使将用户添加到 docker 组，仍然可能出现权限错误。

##### 问题症状

```
permission denied while trying to connect to the Docker daemon socket at unix:///var/run/docker.sock
```

##### 完整排查步骤

**步骤 1: 确认用户和组信息**

```bash
# 1. 确认当前运行 Jenkins agent 的用户名
whoami
# 应该显示: jenkins-agent 或 jenkins

# 2. 检查用户是否在 docker 组中
id jenkins-agent
# 应该看到: ... groups=1001(jenkins-agent),999(docker) ...

# 或者使用
groups jenkins-agent
# 应该包含: docker

# 3. 检查 docker 组是否存在
getent group docker
# 应该显示: docker:x:999:jenkins-agent
```

**步骤 2: 检查 Docker Socket 权限**

```bash
# 检查 Docker socket 的权限和所有者
ls -la /var/run/docker.sock
# 应该显示类似: srw-rw---- 1 root docker ...

# 如果所有者不是 root 或组不是 docker，需要修复
sudo chown root:docker /var/run/docker.sock
sudo chmod 660 /var/run/docker.sock
```

**步骤 3: 将用户添加到 docker 组（如果还没有）**

```bash
# 将 jenkins-agent 用户添加到 docker 组
sudo usermod -aG docker jenkins-agent

# 验证用户已添加到组
getent group docker | grep jenkins-agent
```

**步骤 4: 使组权限生效（关键步骤）**

这是最容易被忽略的步骤！添加用户到组后，**必须**执行以下操作之一：

**方法 A: 重新登录用户（推荐）**

```bash
# 如果可以通过 SSH 登录，先退出当前会话，然后重新登录
exit
# 然后重新 SSH 登录

# 验证组权限是否生效
id jenkins-agent
groups jenkins-agent
```

**方法 B: 使用 newgrp 命令（临时生效）**

```bash
# 切换到 docker 组（当前会话有效）
newgrp docker

# 验证
id
docker ps
```

**方法 C: 重启 Jenkins agent 服务**

```bash
# 如果 Jenkins agent 作为系统服务运行
sudo systemctl restart jenkins-agent
# 或
sudo systemctl restart jenkins

# 如果 Jenkins agent 通过 SSH 启动，需要：
# 1. 停止当前 agent
# 2. 重新启动 agent（会重新加载用户组信息）
```

**方法 D: 重启 Docker 服务（有时需要）**

```bash
sudo systemctl restart docker
```

**步骤 5: 验证 Docker 权限**

```bash
# 切换到 jenkins-agent 用户（如果当前不是）
sudo su - jenkins-agent

# 测试 Docker 命令（应该不需要 sudo）
docker ps
docker version
docker compose version

# 如果仍然失败，检查 Docker socket
ls -la /var/run/docker.sock
```

##### 常见问题和解决方案

**问题 1: 用户已在 docker 组，但仍然权限不足**

**原因：** 组权限未生效，用户会话未刷新组信息。

**解决方案：**
```bash
# 方法 1: 使用 newgrp（推荐用于测试）
newgrp docker
docker ps

# 方法 2: 重启相关服务
sudo systemctl restart docker
sudo systemctl restart jenkins-agent  # 或 jenkins

# 方法 3: 重新登录用户
# 退出当前会话，重新 SSH 登录
```

**问题 2: Docker socket 权限不正确**

**原因：** `/var/run/docker.sock` 的权限或所有者不正确。

**解决方案：**
```bash
# 检查当前权限
ls -la /var/run/docker.sock

# 修复权限（所有者应该是 root，组应该是 docker）
sudo chown root:docker /var/run/docker.sock
sudo chmod 660 /var/run/docker.sock

# 验证
ls -la /var/run/docker.sock
# 应该显示: srw-rw---- 1 root docker ...
```

**问题 3: Jenkins agent 以不同用户运行**

**原因：** Jenkins agent 可能以 `jenkins` 用户运行，但你配置的是 `jenkins-agent`。

**解决方案：**
```bash
# 检查 Jenkins agent 实际运行的用户
ps aux | grep jenkins
# 或
ps aux | grep java | grep jenkins

# 将实际运行的用户添加到 docker 组
sudo usermod -aG docker <实际用户名>

# 重启 Jenkins agent
sudo systemctl restart jenkins-agent
```

**问题 4: SELinux 阻止访问（CentOS/RHEL）**

**原因：** SELinux 可能阻止用户访问 Docker socket。

**解决方案：**
```bash
# 检查 SELinux 状态
getenforce

# 如果启用，可以临时设置为宽松模式（不推荐生产环境）
sudo setenforce 0

# 或者配置 SELinux 允许访问（推荐）
sudo setsebool -P container_manage_cgroup on
```

##### 快速验证脚本

创建一个测试脚本来验证 Docker 权限：

```bash
#!/bin/bash
# 保存为 test-docker-permissions.sh

echo "=== Docker 权限检查 ==="
echo ""

echo "1. 当前用户:"
whoami
echo ""

echo "2. 用户组信息:"
id
echo ""

echo "3. Docker socket 权限:"
ls -la /var/run/docker.sock
echo ""

echo "4. Docker 组信息:"
getent group docker
echo ""

echo "5. 测试 Docker 命令:"
if docker ps &>/dev/null; then
    echo "✅ Docker 权限正常"
    docker ps
else
    echo "❌ Docker 权限不足"
    echo "错误信息:"
    docker ps 2>&1
fi
```

运行脚本：
```bash
chmod +x test-docker-permissions.sh
./test-docker-permissions.sh
```

##### 在 Jenkins Pipeline 中添加权限检查

可以在 Jenkinsfile 中添加权限检查步骤：

```groovy
// 在 E2E Tests 阶段开始时添加
script {
    // 检查 Docker 权限
    def dockerCheck = sh(
        script: 'docker ps 2>&1 || true',
        returnStdout: true
    ).trim()
    
    if (dockerCheck.contains('permission denied')) {
        error("""
            Docker 权限不足！请执行以下步骤：
            1. 将 jenkins-agent 用户添加到 docker 组: sudo usermod -aG docker jenkins-agent
            2. 重启 Jenkins agent 服务: sudo systemctl restart jenkins-agent
            3. 或重新登录 jenkins-agent 用户
            4. 验证: docker ps
            详细说明请参考: todoapp-backend-api-e2etest/JENKINS.md
        """)
    }
}
```

#### 7. 其他故障排查

**问题：Python 命令未找到**
```bash
# 检查 Python 安装位置
which python3
ls -la /usr/bin/python*

# 如果未安装，参考上面的安装步骤
```

**问题：Docker Compose 命令未找到**
```bash
# 检查是否安装了 docker-compose
which docker-compose

# 或者检查 docker compose (V2)
docker compose version

# 如果都没有，安装 docker-compose
sudo apt install docker-compose  # Ubuntu/Debian
# 或
sudo pip3 install docker-compose  # 使用 pip 安装
```

## 为什么使用虚拟环境？

在 Jenkins CI 中使用 Python 虚拟环境是**强烈推荐**的最佳实践，原因如下：

### ✅ 优势

1. **依赖隔离**
   - 避免与系统 Python 或其他项目的依赖冲突
   - 每个项目有独立的依赖环境
   - 防止全局 Python 包污染

2. **可重现性**
   - 确保在不同 Jenkins agent 上使用相同的依赖版本
   - 便于问题排查和调试
   - 符合"基础设施即代码"原则

3. **CI/CD 最佳实践**
   - 行业标准做法（类似 Node.js 的 node_modules，Java 的 Maven/Gradle）
   - 易于清理和重置
   - 支持并行构建

4. **安全性**
   - 避免安装系统级包带来的安全风险
   - 限制依赖的作用域

## Jenkins Pipeline 配置

### 在现有 Pipeline 中集成

E2E 测试已经集成到 `todoapp-ci/dotnet/Jenkinsfile` 中，作为第 6 个阶段（在单元测试之后）。

**流程说明：**
1. 自动检出 E2E 测试项目代码
2. 检查 Python 和 Docker 环境
3. 创建 Python 虚拟环境并安装依赖
4. 启动测试数据库（Docker Compose）
5. 后台启动后端 API 服务
6. 运行 E2E 测试
7. 自动清理资源（API 进程、Docker 容器）
8. 发布 Allure 测试报告

### 基础配置示例

如果需要单独运行 E2E 测试，可以使用以下配置：

```groovy
stage('E2E Tests') {
    steps {
        dir('todoapp-backend-api-e2etest') {
            script {
                // 1. 创建虚拟环境
                sh '''
                    if [ ! -d "venv" ]; then
                        python3 -m venv venv
                    fi
                '''
                
                // 2. 激活虚拟环境并安装依赖
                sh '''
                    source venv/bin/activate
                    pip install --upgrade pip
                    pip install -r requirements.txt
                '''
                
                // 3. 启动测试数据库
                sh 'docker-compose -f docker-compose.test.yml up -d'
                sh 'sleep 10'
                
                // 4. 运行测试
                sh '''
                    source venv/bin/activate
                    pytest --alluredir=test-results/allure-results -v
                '''
            }
        }
    }
    post {
        always {
            dir('todoapp-backend-api-e2etest') {
                // 发布报告
                allure([
                    includeProperties: false,
                    jdk: '',
                    properties: [],
                    reportBuildPolicy: 'ALWAYS',
                    results: [[path: 'test-results/allure-results']]
                ])
                
                // 清理
                sh 'docker-compose -f docker-compose.test.yml down -v'
            }
        }
    }
}
```

### 优化配置（使用缓存）

如果 Jenkins agent 支持文件系统缓存，可以缓存虚拟环境以提高构建速度：

```groovy
stage('E2E Tests') {
    steps {
        dir('todoapp-backend-api-e2etest') {
            script {
                // 使用 Jenkins 缓存插件或文件系统缓存
                def cachePath = "${WORKSPACE}/.cache/venv"
                
                // 检查缓存
                if (fileExists("${cachePath}/bin/activate")) {
                    echo "使用缓存的虚拟环境..."
                    sh "cp -r ${cachePath} venv"
                } else {
                    echo "创建新的虚拟环境..."
                    sh 'python3 -m venv venv'
                }
                
                // 安装依赖（每次都需要，因为 requirements.txt 可能更新）
                sh '''
                    source venv/bin/activate
                    pip install --upgrade pip
                    pip install -r requirements.txt
                '''
                
                // 更新缓存
                sh "mkdir -p ${WORKSPACE}/.cache && cp -r venv ${cachePath}"
                
                // 运行测试
                sh '''
                    source venv/bin/activate
                    docker-compose -f docker-compose.test.yml up -d
                    sleep 10
                    pytest --alluredir=test-results/allure-results -v
                '''
            }
        }
    }
    post {
        always {
            dir('todoapp-backend-api-e2etest') {
                allure([...])
                sh 'docker-compose -f docker-compose.test.yml down -v'
            }
        }
    }
}
```

### 使用 Docker Agent（推荐）

如果使用 Docker agent，可以在 Dockerfile 中预装依赖：

```groovy
pipeline {
    agent {
        docker {
            image 'python:3.11-slim'
            args '-v /var/run/docker.sock:/var/run/docker.sock'  // 允许在容器内运行 Docker
        }
    }
    stages {
        stage('E2E Tests') {
            steps {
                dir('todoapp-backend-api-e2etest') {
                    sh '''
                        python3 -m venv venv
                        source venv/bin/activate
                        pip install --upgrade pip
                        pip install -r requirements.txt
                        docker-compose -f docker-compose.test.yml up -d
                        sleep 10
                        pytest --alluredir=test-results/allure-results -v
                    '''
                }
            }
        }
    }
}
```

## 环境变量配置

在 Jenkins 中配置环境变量（Manage Jenkins → Configure System → Global properties）：

```bash
# Python 相关
PYTHONUNBUFFERED=1  # 确保 Python 输出实时显示

# 测试相关
TEST_DB_HOST=localhost
TEST_DB_PORT=5433
TEST_DB_NAME=todoapp_test
TEST_DB_USER=postgres
TEST_DB_PASSWORD=postgres
API_BASE_URL=http://localhost:5085
```

## 常见问题

### Q: 虚拟环境应该提交到 Git 吗？

**A: 不应该。** `venv/` 目录已在 `.gitignore` 中，不应该提交到版本控制。

### Q: 每次构建都要重新创建虚拟环境吗？

**A: 不一定。** 
- 如果 Jenkins agent 是持久化的，可以保留虚拟环境
- 如果使用 Docker agent，每次都是新环境，需要重新创建
- 可以使用缓存机制提高速度

### Q: 虚拟环境会影响构建速度吗？

**A: 会有轻微影响，但可以优化：**
- 首次创建：~5-10 秒
- 安装依赖：~30-60 秒（取决于网络）
- 使用缓存可以显著减少时间

### Q: 可以在多个项目间共享虚拟环境吗？

**A: 不推荐。** 每个项目应该有独立的虚拟环境，避免依赖冲突。

### Q: API 服务启动失败怎么办？

**A: 检查以下几点：**
1. 确保端口 5085 未被占用
2. 检查 API 日志文件 `api.log`
3. 确保数据库连接字符串正确
4. 检查 .NET SDK 是否已安装

### Q: 测试数据库启动失败怎么办？

**A: 检查以下几点：**
1. 确保端口 5433 未被占用
2. 检查 Docker 服务是否运行：`docker ps`
3. 查看数据库日志：`docker logs todoapp-postgres-test`
4. 确保 Jenkins 用户在 docker 组中

## 最佳实践总结

1. ✅ **总是使用虚拟环境** - 在 CI 和本地开发中都应该使用
2. ✅ **在 requirements.txt 中固定版本** - 确保可重现性
3. ✅ **每次构建时升级 pip** - 确保使用最新的包管理器
4. ✅ **清理测试资源** - 在 `post { always }` 中清理 Docker 容器和 API 进程
5. ✅ **使用缓存** - 如果可能，缓存虚拟环境以提高速度
6. ✅ **记录 Python 版本** - 在文档中明确支持的 Python 版本
7. ✅ **环境检查** - 在 Pipeline 开始时检查 Python 和 Docker 是否安装
8. ✅ **错误处理** - 确保所有资源在测试失败时也能被正确清理

## Allure 插件检查和故障排查

### 检查 Allure 插件是否已安装

#### 方法 1: 通过 Jenkins Web 界面检查

1. 登录 Jenkins 管理界面
2. 访问：**Manage Jenkins** → **Manage Plugins** → **Installed**
3. 搜索 "Allure"
4. 确认以下插件已安装且启用：
   - **Allure Plugin** (ID: `allure-jenkins-plugin`)

#### 方法 2: 通过 Jenkins 脚本控制台检查

1. 访问：**Manage Jenkins** → **Script Console**
2. 执行以下 Groovy 脚本：

```groovy
import jenkins.model.Jenkins

def pluginManager = Jenkins.instance.pluginManager
def allurePlugin = pluginManager.getPlugin('allure-jenkins-plugin')

if (allurePlugin != null) {
    println "✅ Allure Plugin 已安装"
    println "   版本: ${allurePlugin.version}"
    println "   状态: ${allurePlugin.isEnabled() ? '已启用' : '已禁用'}"
    println "   已激活: ${allurePlugin.isActive() ? '是' : '否'}"
} else {
    println "❌ Allure Plugin 未安装"
}
```

#### 方法 3: 通过 Pipeline 脚本检查

在 Jenkinsfile 中添加以下代码来检查插件：

```groovy
script {
    try {
        def pluginManager = Jenkins.instance.pluginManager
        def allurePlugin = pluginManager.getPlugin('allure-jenkins-plugin')
        
        if (allurePlugin != null && allurePlugin.isActive()) {
            echo "✅ Allure Plugin 已安装并激活"
            echo "   版本: ${allurePlugin.version}"
        } else {
            echo "❌ Allure Plugin 未安装或未激活"
        }
    } catch (Exception e) {
        echo "检查插件时出错: ${e.getMessage()}"
    }
}
```

### 常见问题和解决方案

#### 问题 0: Can not find any allure commandline installation

**错误信息：**
```
ru.yandex.qatools.allure.jenkins.exception.AllurePluginException: Can not find any allure commandline installation.
```

**原因：** Allure 插件已安装，但系统上未安装 Allure 命令行工具。

**解决方案：**

1. **在 Jenkins 中配置 Allure 工具（推荐）**
   - 访问：Manage Jenkins → Global Tool Configuration
   - 找到 Allure Commandline 部分
   - 添加 Allure 工具，选择 "Install automatically"
   - 保存配置

2. **手动在构建机上安装 Allure**
   ```bash
   # 下载并安装
   wget https://github.com/allure-framework/allure2/releases/download/2.24.0/allure-2.24.0.tgz
   tar -xzf allure-2.24.0.tgz
   sudo mv allure-2.24.0 /opt/allure
   sudo ln -s /opt/allure/bin/allure /usr/local/bin/allure
   
   # 验证
   allure --version
   ```

3. **在 Jenkinsfile 中指定路径**
   ```groovy
   allure([
       results: [[path: 'test-results/allure-results']],
       commandline: '/opt/allure/bin/allure'  // 指定路径
   ])
   ```

#### 问题 1: 插件已安装但仍显示"未安装"

**可能原因：**
- Jenkins 未重启（安装插件后必须重启）
- 插件未正确加载
- 插件版本不兼容

**解决步骤：**

1. **重启 Jenkins**
   ```bash
   # 如果使用 systemd
   sudo systemctl restart jenkins
   
   # 或者通过 Web 界面
   # Manage Jenkins → Prepare for Shutdown → 等待重启
   ```

2. **检查插件状态**
   - 访问：Manage Jenkins → Manage Plugins → Installed
   - 确认 Allure Plugin 显示为 "Enabled" 和 "Active"

3. **重新安装插件**
   - 卸载 Allure Plugin
   - 重启 Jenkins
   - 重新安装 Allure Plugin
   - 再次重启 Jenkins

#### 问题 2: 插件版本不兼容

**检查 Jenkins 版本兼容性：**
- Allure Plugin 2.30.0+ 需要 Jenkins 2.277.1+
- 如果 Jenkins 版本较旧，需要安装兼容的 Allure Plugin 版本

**查看兼容性：**
- 访问插件页面：https://plugins.jenkins.io/allure-jenkins-plugin/
- 查看 "Dependencies" 部分

#### 问题 3: 插件方法不可用

如果看到 `NoSuchMethodError` 或 `MissingMethodException`：

1. **检查插件是否正确加载**
   ```groovy
   // 在 Script Console 中执行
   Jenkins.instance.pluginManager.activePlugins.each {
       if (it.shortName.contains('allure')) {
           println "${it.shortName}: ${it.version} - Active: ${it.isActive()}"
       }
   }
   ```

2. **检查 Pipeline 语法**
   - 确保使用正确的 `allure()` 方法调用
   - 参考插件文档：https://plugins.jenkins.io/allure-jenkins-plugin/

3. **尝试使用不同的调用方式**
   ```groovy
   // 方式 1: 标准调用
   allure([
       includeProperties: false,
       jdk: '',
       properties: [],
       reportBuildPolicy: 'ALWAYS',
       results: [[path: 'test-results/allure-results']]
   ])
   
   // 方式 2: 简化调用
   allure([
       results: [[path: 'test-results/allure-results']]
   ])
   ```

#### 问题 4: Allure 结果目录不存在

**检查步骤：**

```bash
# 在 Pipeline 中添加
sh '''
    echo "检查 Allure 结果目录:"
    ls -la test-results/allure-results/ || echo "目录不存在"
    
    if [ -d "test-results/allure-results" ]; then
        echo "结果文件数量:"
        find test-results/allure-results -type f | wc -l
        echo "结果文件列表:"
        find test-results/allure-results -type f | head -10
    fi
'''
```

**确保 pytest 正确生成结果：**
```bash
# 在 pytest 命令中确保包含 --alluredir
pytest --alluredir=test-results/allure-results -v
```

### 安装 Allure 命令行工具

**重要：** Allure Jenkins Plugin 需要系统上安装 Allure 命令行工具才能工作。

#### 方法 1: 在 Jenkins 中配置 Allure 工具（推荐）

1. 访问：**Manage Jenkins** → **Global Tool Configuration**
2. 找到 **Allure Commandline** 部分
3. 点击 **Add Allure Commandline**
4. 配置：
   - **Name**: `Allure` (或自定义名称)
   - **Install automatically**: 勾选
   - **Version**: 选择最新版本（如 `2.24.0`）
5. 点击 **Save**

Jenkins 会自动下载并安装 Allure 工具。

#### 方法 2: 手动在构建机上安装 Allure

如果 Jenkins 自动安装失败，可以手动安装：

**Ubuntu/Debian:**
```bash
# 方法 1: 使用 apt（如果可用）
sudo apt update
sudo apt install -y allure

# 方法 2: 手动下载安装
# 下载 Allure
wget https://github.com/allure-framework/allure2/releases/download/2.24.0/allure-2.24.0.tgz

# 解压
tar -xzf allure-2.24.0.tgz

# 移动到系统目录
sudo mv allure-2.24.0 /opt/allure

# 创建符号链接
sudo ln -s /opt/allure/bin/allure /usr/local/bin/allure

# 验证安装
allure --version
```

**CentOS/RHEL:**
```bash
# 下载 Allure
wget https://github.com/allure-framework/allure2/releases/download/2.24.0/allure-2.24.0.tgz

# 解压
tar -xzf allure-2.24.0.tgz

# 移动到系统目录
sudo mv allure-2.24.0 /opt/allure

# 创建符号链接
sudo ln -s /opt/allure/bin/allure /usr/local/bin/allure

# 验证安装
allure --version
```

**macOS:**
```bash
# 使用 Homebrew
brew install allure
```

#### 方法 3: 在 Jenkinsfile 中指定 Allure 路径

如果 Allure 安装在非标准位置，可以在 Jenkinsfile 中指定：

```groovy
allure([
    includeProperties: false,
    jdk: '',
    properties: [],
    reportBuildPolicy: 'ALWAYS',
    results: [[path: 'test-results/allure-results']],
    commandline: '/opt/allure/bin/allure'  // 指定 Allure 路径
])
```

#### 验证 Allure 安装

在构建机上执行：

```bash
# 检查 Allure 是否在 PATH 中
which allure

# 检查版本
allure --version

# 如果不在 PATH 中，检查常见安装位置
ls -la /opt/allure/bin/allure
ls -la /usr/local/bin/allure
```

### 手动安装 Allure Plugin

如果插件未安装，可以通过以下方式安装：

#### 方法 1: 通过 Web 界面安装（推荐）

1. 访问：**Manage Jenkins** → **Manage Plugins** → **Available**
2. 搜索 "Allure"
3. 勾选 "Allure Plugin"
4. 点击 "Install without restart" 或 "Download now and install after restart"
5. 等待安装完成
6. **重要：重启 Jenkins**

#### 方法 2: 通过命令行安装

```bash
# 下载插件（替换版本号）
wget https://updates.jenkins.io/download/plugins/allure-jenkins-plugin/2.30.0/allure-jenkins-plugin.hpi

# 复制到 Jenkins 插件目录
sudo cp allure-jenkins-plugin.hpi /var/lib/jenkins/plugins/

# 设置权限
sudo chown jenkins:jenkins /var/lib/jenkins/plugins/allure-jenkins-plugin.hpi

# 重启 Jenkins
sudo systemctl restart jenkins
```

### 验证 Allure 报告发布

安装并重启后，运行一次构建，检查：

1. **构建日志中应该显示：**
   ```
   ✅ Allure 报告已成功发布
   ```

2. **构建页面应该显示：**
   - 左侧菜单有 "Allure Report" 链接
   - 点击可以查看详细的测试报告

3. **如果仍然失败，查看详细错误信息：**
   - 构建日志会显示具体的异常类型和错误信息
   - 根据错误信息进行相应的修复

## 参考资源

- [Python 虚拟环境官方文档](https://docs.python.org/3/tutorial/venv.html)
- [Jenkins Pipeline 最佳实践](https://www.jenkins.io/doc/book/pipeline/pipeline-best-practices/)
- [pytest 文档](https://docs.pytest.org/)
- [Docker Compose 文档](https://docs.docker.com/compose/)
- [Allure Jenkins Plugin 文档](https://plugins.jenkins.io/allure-jenkins-plugin/)
