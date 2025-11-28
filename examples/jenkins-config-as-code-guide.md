# Configuration as Code (JCasC) 配置管理

## 用途

JCasC 用于管理 **Jenkins 系统配置**，不是管理 Pipeline！

**管理内容**:
- Jenkins 系统设置
- 插件配置
- 凭证（不含密码明文）
- 用户和权限
- 全局工具配置

**不管理内容**:
- ❌ Pipeline 定义（用 Jenkinsfile）
- ❌ Job 配置（用 Job DSL）
- ❌ 构建历史
- ❌ 工作空间文件

---

## Git 仓库结构

```
jenkins-config/                 # 独立的配置仓库
├── jenkins.yaml                # 主配置文件
├── credentials.yaml            # 凭证配置
├── plugins.txt                 # 插件列表
├── README.md
└── .gitignore                  # 忽略敏感文件
```

---

## jenkins.yaml 示例

```yaml
jenkins:
  systemMessage: "企业 CI/CD 平台 - 生产环境"

  # Master 不执行构建
  numExecutors: 0

  # 安全域配置
  securityRealm:
    local:
      allowsSignup: false
      users:
        - id: "admin"
          password: "${JENKINS_ADMIN_PASSWORD}"  # 从环境变量读取
          name: "系统管理员"

  # 授权策略
  authorizationStrategy:
    roleBased:
      roles:
        global:
          - name: "admin"
            description: "管理员"
            permissions:
              - "Overall/Administer"
            assignments:
              - "admin"

          - name: "developer"
            description: "开发人员"
            permissions:
              - "Overall/Read"
              - "Job/Read"
              - "Job/Build"
              - "Job/Cancel"
            assignments:
              - "dev-team"

        items:
          - name: "team1-jobs"
            pattern: "team1-.*"
            permissions:
              - "Job/Read"
              - "Job/Build"
              - "Job/Configure"
            assignments:
              - "team1-members"

  # 节点配置
  nodes:
    - permanent:
        name: "agent-dotnet-01"
        remoteFS: "/home/jenkins"
        labelString: "dotnet"
        launcher:
          ssh:
            host: "192.168.1.101"
            credentialsId: "ssh-agent-key"
            sshHostKeyVerificationStrategy: "nonVerifyingKeyVerificationStrategy"

    - permanent:
        name: "agent-java-01"
        remoteFS: "/home/jenkins"
        labelString: "java"
        launcher:
          ssh:
            host: "192.168.1.102"
            credentialsId: "ssh-agent-key"

# 全局配置
unclassified:
  # 位置配置
  location:
    url: "http://jenkins.internal.com:8080/"
    adminAddress: "jenkins-admin@example.com"

  # GitLab 连接
  gitLabConnectionConfig:
    connections:
      - name: "GitLab Internal"
        url: "http://gitlab.internal.com"
        apiTokenId: "gitlab-api-token"
        ignoreCertificateErrors: false

  # SonarQube 配置
  sonarGlobalConfiguration:
    installations:
      - name: "SonarQube"
        serverUrl: "http://sonarqube.internal.com"
        serverAuthenticationToken: "${SONAR_TOKEN}"

  # Nexus 配置（如果有插件支持）
  # 可以添加 Nexus 相关配置

# 工具配置
tools:
  # Maven 配置
  maven:
    installations:
      - name: "Maven-3.9"
        properties:
          - installSource:
              installers:
                - maven:
                    id: "3.9.6"

  # JDK 配置
  jdk:
    installations:
      - name: "JDK-17"
        home: "/usr/lib/jvm/java-17-openjdk-amd64"

  # NodeJS 配置
  nodejs:
    installations:
      - name: "NodeJS-18"
        properties:
          - installSource:
              installers:
                - nodeJSInstaller:
                    id: "18.19.0"
                    npmPackagesRefreshHours: 72

# 凭证配置
credentials:
  system:
    domainCredentials:
      - credentials:
          # GitLab 用户名密码
          - usernamePassword:
              scope: GLOBAL
              id: "gitlab-user"
              username: "${GITLAB_USERNAME}"
              password: "${GITLAB_PASSWORD}"
              description: "GitLab 用户凭证"

          # SSH 密钥
          - basicSSHUserPrivateKey:
              scope: GLOBAL
              id: "ssh-agent-key"
              username: "jenkins"
              privateKeySource:
                directEntry:
                  privateKey: "${SSH_PRIVATE_KEY}"
              description: "Agent 节点 SSH 密钥"

          # API Token
          - string:
              scope: GLOBAL
              id: "gitlab-api-token"
              secret: "${GITLAB_API_TOKEN}"
              description: "GitLab API Token"

          - string:
              scope: GLOBAL
              id: "sonar-token"
              secret: "${SONAR_TOKEN}"
              description: "SonarQube Token"
```

---

## 在 Git 中管理

### 1. 创建配置仓库

```bash
# 创建独立的配置仓库
mkdir jenkins-config
cd jenkins-config

git init
git remote add origin http://gitlab.internal.com/devops/jenkins-config.git
```

### 2. 添加配置文件

```bash
# 创建主配置
vim jenkins.yaml

# 创建 .gitignore
cat > .gitignore <<EOF
# 不提交敏感信息
.env
secrets/
*.key
*.pem
EOF

# 创建环境变量模板
cat > .env.example <<EOF
JENKINS_ADMIN_PASSWORD=change-me
GITLAB_USERNAME=jenkins
GITLAB_PASSWORD=change-me
GITLAB_API_TOKEN=change-me
SONAR_TOKEN=change-me
SSH_PRIVATE_KEY=change-me
EOF
```

### 3. 提交到 Git

```bash
git add jenkins.yaml .gitignore .env.example
git commit -m "初始化 Jenkins 配置"
git push origin master
```

---

## 在 Jenkins 中应用

### 方式 1: 容器启动时加载

修改 `docker-compose.yml`:

```yaml
services:
  jenkins:
    image: jenkins-master-offline:1.0
    volumes:
      - ./jenkins_home:/var/jenkins_home
      - ./jenkins-config:/var/jenkins_config:ro  # 挂载配置

    environment:
      # 指定 JCasC 配置文件
      - CASC_JENKINS_CONFIG=/var/jenkins_config/jenkins.yaml

      # 传入敏感信息（从 .env 文件）
      - JENKINS_ADMIN_PASSWORD=${JENKINS_ADMIN_PASSWORD}
      - GITLAB_USERNAME=${GITLAB_USERNAME}
      - GITLAB_PASSWORD=${GITLAB_PASSWORD}
      - GITLAB_API_TOKEN=${GITLAB_API_TOKEN}
      - SONAR_TOKEN=${SONAR_TOKEN}
```

### 方式 2: Web UI 手动应用

1. 访问: **Manage Jenkins** → **Configuration as Code**
2. 点击 **"Reload existing configuration"**
3. 或上传新的配置文件

### 方式 3: 从 Git 仓库自动拉取（推荐）

创建一个 Job 自动同步配置：

```groovy
// config-sync-job
pipeline {
    agent any

    triggers {
        cron('H/15 * * * *')  // 每15分钟检查一次
    }

    stages {
        stage('拉取配置') {
            steps {
                git url: 'http://gitlab.internal.com/devops/jenkins-config.git',
                    branch: 'master',
                    credentialsId: 'gitlab-user'
            }
        }

        stage('应用配置') {
            steps {
                script {
                    // 复制配置到 Jenkins
                    sh 'cp jenkins.yaml /var/jenkins_home/casc_configs/'

                    // 重新加载配置（需要安装 JCasC 插件）
                    // 可以通过 API 触发重载
                    sh '''
                        curl -X POST \
                          http://localhost:8080/configuration-as-code/reload \
                          --user admin:${JENKINS_API_TOKEN}
                    '''
                }
            }
        }
    }
}
```

---

## 最佳实践

### 1. 敏感信息处理

**❌ 不要这样做**:
```yaml
credentials:
  - usernamePassword:
      password: "my-password-123"  # 明文密码
```

**✅ 应该这样做**:
```yaml
credentials:
  - usernamePassword:
      password: "${GITLAB_PASSWORD}"  # 从环境变量读取
```

### 2. 环境分离

```
jenkins-config/
├── production/
│   └── jenkins.yaml
├── staging/
│   └── jenkins.yaml
└── development/
    └── jenkins.yaml
```

### 3. 版本控制

```bash
# 每次修改都提交
git add jenkins.yaml
git commit -m "添加 agent-dotnet-02 节点"
git push

# 打标签
git tag -a v1.0 -m "生产环境配置 v1.0"
git push --tags
```

### 4. 配置验证

在应用前验证配置：

```bash
# 使用 Jenkins CLI 验证
jenkins-cli -s http://localhost:8080/ \
    reload-jcasc-configuration \
    --validate-only
```

---

## 团队协作流程

### 1. 修改配置

```bash
git checkout -b feature/add-new-agent
vim jenkins.yaml
# 添加新的 Agent 节点配置
git add jenkins.yaml
git commit -m "添加 agent-cpp-01 节点"
git push origin feature/add-new-agent
```

### 2. Code Review

提交 Merge Request，团队审核配置变更。

### 3. 合并到主分支

```bash
git checkout master
git merge feature/add-new-agent
git push origin master
```

### 4. 应用到 Jenkins

Jenkins 自动拉取并应用新配置（如果配置了自动同步）。

---

## 迁移到新 Jenkins 实例

### 场景：灾难恢复或环境迁移

```bash
# 1. 在新 Jenkins 实例上克隆配置仓库
git clone http://gitlab.internal.com/devops/jenkins-config.git

# 2. 设置环境变量
cp .env.example .env
vim .env  # 填入实际密码

# 3. 启动 Jenkins 并应用配置
docker-compose up -d

# 4. 配置自动生效，无需手动点击
```

---

## 与其他方案的对比

| 方案 | 用途 | Git 管理 |
|------|------|---------|
| **JCasC** | Jenkins 系统配置 | ✅ 推荐 |
| **Jenkinsfile** | Pipeline 定义 | ✅ 必须 |
| **Job DSL** | Job 批量创建 | ✅ 推荐 |
| **手动配置** | - | ❌ 不推荐 |

---

## 总结

### ✅ JCasC 适用于:
- Jenkins 系统配置
- 插件配置
- 用户和权限
- Agent 节点
- 全局工具
- 外部服务集成

### ❌ JCasC 不适用于:
- Pipeline 定义（用 Jenkinsfile）
- 单个 Job 配置（用 Jenkinsfile）
- 构建历史（不需要管理）
- 临时数据

### 最佳组合:
- **JCasC**: 管理 Jenkins 系统配置 → Git 仓库 `jenkins-config`
- **Jenkinsfile**: 管理 Pipeline 定义 → 项目代码仓库
- **Job DSL**: 批量创建 Job → Git 仓库 `jenkins-job-dsl`
- **共享库**: 可重用 Pipeline 代码 → Git 仓库 `jenkins-shared-library`
