# Job DSL 批量管理 Job

## 用途

Job DSL 用于**批量创建和管理 Jenkins Job**，特别适合：
- 24个微服务需要相似的 Pipeline
- 多个项目具有相同的构建模式
- 需要动态生成大量 Job

---

## Git 仓库结构

```
jenkins-job-dsl/
├── microservices.groovy        # 微服务 Job 定义
├── frontend-projects.groovy    # 前端项目 Job 定义
├── shared/
│   └── common-pipeline.groovy  # 通用 Pipeline 模板
├── README.md
└── .gitignore
```

---

## 示例：为24个微服务创建 Job

### microservices.groovy

```groovy
// 定义所有微服务
def microservices = [
    'user-service',
    'order-service',
    'payment-service',
    'inventory-service',
    'notification-service',
    // ... 共24个
]

def gitlabBaseUrl = 'http://gitlab.internal.com/microservices'

// 为每个微服务创建多分支流水线
microservices.each { serviceName ->
    multibranchPipelineJob("microservices/${serviceName}") {
        displayName(serviceName.capitalize())
        description("${serviceName} 的多分支流水线")

        // 分支源
        branchSources {
            git {
                id(serviceName)
                remote("${gitlabBaseUrl}/${serviceName}.git")
                credentialsId('gitlab-credentials')

                // 包含的分支
                includes('master develop release/*')
            }
        }

        // 扫描触发器
        triggers {
            periodic(15) // 每15分钟扫描一次
        }

        // 孤儿项目策略
        orphanedItemStrategy {
            discardOldItems {
                numToKeep(10)
            }
        }

        // Pipeline 配置
        factory {
            workflowBranchProjectFactory {
                scriptPath('Jenkinsfile')
            }
        }
    }
}

// 创建全量构建 Job
pipelineJob('microservices/build-all') {
    displayName('全量构建 - 所有微服务')
    description('并行构建所有24个微服务')

    parameters {
        choiceParam('BRANCH', ['develop', 'master', 'release'], '构建分支')
    }

    definition {
        cps {
            script("""
                pipeline {
                    agent any
                    stages {
                        stage('并行构建') {
                            steps {
                                script {
                                    def jobs = ${microservices.collect { "'microservices/${it}'" }}
                                    jobs.each { job ->
                                        build job: job,
                                              parameters: [string(name: 'BRANCH', value: params.BRANCH)],
                                              wait: false
                                    }
                                }
                            }
                        }
                    }
                }
            """)
            sandbox()
        }
    }
}

// 创建文件夹
folder('microservices') {
    displayName('微服务项目')
    description('所有微服务的构建任务')
}
```

### 不同类型项目的模板

```groovy
// frontend-projects.groovy

def frontendProjects = [
    'admin-portal',
    'user-portal',
    'mobile-app'
]

frontendProjects.each { projectName ->
    pipelineJob("frontend/${projectName}") {
        displayName(projectName.capitalize())

        parameters {
            choiceParam('BUILD_ENV', ['dev', 'test', 'prod'], '构建环境')
        }

        triggers {
            scm('H/5 * * * *')  // 每5分钟检查 SCM 变化
        }

        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url("http://gitlab.internal.com/frontend/${projectName}.git")
                            credentials('gitlab-credentials')
                        }
                        branch('*/master')
                    }
                }
                scriptPath('Jenkinsfile')
            }
        }
    }
}

folder('frontend') {
    displayName('前端项目')
}
```

---

## 在 Git 中管理

### 1. 创建仓库

```bash
mkdir jenkins-job-dsl
cd jenkins-job-dsl

git init
git remote add origin http://gitlab.internal.com/devops/jenkins-job-dsl.git
```

### 2. 添加 DSL 脚本

```bash
# 创建微服务 Job 定义
vim microservices.groovy

# 提交
git add microservices.groovy
git commit -m "添加微服务 Job DSL"
git push origin master
```

---

## 在 Jenkins 中应用

### 方式 1: 创建种子 Job（Seed Job）

1. **创建新任务**: Dashboard → 新建任务
2. **任务名称**: `job-dsl-seed`
3. **类型**: **自由风格项目**

#### 配置种子 Job:

**源代码管理**:
- Git
- Repository URL: `http://gitlab.internal.com/devops/jenkins-job-dsl.git`
- Credentials: gitlab-credentials
- Branch: `*/master`

**构建触发器**:
- ☑ Poll SCM: `H/15 * * * *` (每15分钟检查一次)

**构建步骤**:
- 添加构建步骤 → **Process Job DSLs**
- Look on Filesystem
- DSL Scripts: `*.groovy`

**保存并立即构建**

### 方式 2: Pipeline 方式（推荐）

```groovy
// Jenkinsfile for job-dsl-seed
pipeline {
    agent any

    triggers {
        pollSCM('H/15 * * * *')
    }

    stages {
        stage('Checkout') {
            steps {
                git url: 'http://gitlab.internal.com/devops/jenkins-job-dsl.git',
                    branch: 'master',
                    credentialsId: 'gitlab-credentials'
            }
        }

        stage('Process DSL') {
            steps {
                jobDsl targets: '*.groovy',
                       removedJobAction: 'DELETE',
                       removedViewAction: 'DELETE',
                       lookupStrategy: 'SEED_JOB'
            }
        }
    }

    post {
        success {
            echo '✅ Job DSL 处理成功'
        }
        failure {
            emailext subject: 'Job DSL 处理失败',
                     body: '请检查 DSL 脚本',
                     to: 'devops-team@example.com'
        }
    }
}
```

---

## 高级用法

### 1. 使用变量和配置文件

```groovy
// config.groovy
class Config {
    static final GITLAB_URL = 'http://gitlab.internal.com'
    static final MICROSERVICES = [
        'user-service',
        'order-service',
        // ...
    ]
}

// microservices.groovy
def services = Config.MICROSERVICES

services.each { service ->
    multibranchPipelineJob("ms/${service}") {
        // ...
    }
}
```

### 2. 动态读取配置

```groovy
// 从 YAML 文件读取配置
import groovy.yaml.YamlSlurper

def yaml = new YamlSlurper()
def config = yaml.parse(readFileFromWorkspace('services.yaml'))

config.microservices.each { service ->
    multibranchPipelineJob("ms/${service.name}") {
        displayName(service.displayName)
        description(service.description)
        // ...
    }
}
```

**services.yaml**:
```yaml
microservices:
  - name: user-service
    displayName: 用户服务
    description: 用户管理微服务
    gitUrl: http://gitlab.internal.com/ms/user-service.git

  - name: order-service
    displayName: 订单服务
    description: 订单管理微服务
    gitUrl: http://gitlab.internal.com/ms/order-service.git
```

### 3. 条件创建

```groovy
def environment = System.getenv('JENKINS_ENV') ?: 'production'

if (environment == 'production') {
    // 生产环境：创建所有 Job
    microservices.each { service ->
        multibranchPipelineJob("ms/${service}") {
            // 完整配置
        }
    }
} else {
    // 测试环境：只创建部分 Job
    ['user-service', 'order-service'].each { service ->
        multibranchPipelineJob("ms/${service}") {
            // 简化配置
        }
    }
}
```

---

## 团队协作

### 工作流程

```bash
# 1. 创建新分支
git checkout -b feature/add-payment-service

# 2. 修改 DSL 脚本
vim microservices.groovy
# 添加 'payment-service' 到列表

# 3. 提交并推送
git add microservices.groovy
git commit -m "添加 payment-service 的 Job 定义"
git push origin feature/add-payment-service

# 4. 提交 MR，经过 Code Review

# 5. 合并后，种子 Job 自动触发
# Jenkins 自动创建新的 payment-service Job
```

---

## 最佳实践

### 1. 命名规范

```groovy
// 好的命名
multibranchPipelineJob('microservices/user-service')
multibranchPipelineJob('frontend/admin-portal')
multibranchPipelineJob('backend/api-gateway')

// 不好的命名
multibranchPipelineJob('job1')
multibranchPipelineJob('test')
```

### 2. 使用文件夹组织

```groovy
folder('microservices') {
    displayName('微服务')
    description('所有微服务项目')
}

folder('frontend') {
    displayName('前端')
}

folder('devops') {
    displayName('DevOps 工具')
}
```

### 3. 参数化

```groovy
// 将配置提取为参数
def createPipelineJob(String name, String gitUrl, String branch = 'master') {
    pipelineJob(name) {
        definition {
            cpsScm {
                scm {
                    git {
                        remote { url(gitUrl) }
                        branch(branch)
                    }
                }
            }
        }
    }
}

// 使用
createPipelineJob('ms/user-service', 'http://gitlab.com/ms/user.git')
createPipelineJob('ms/order-service', 'http://gitlab.com/ms/order.git')
```

### 4. 版本控制

```bash
# 打标签
git tag -a v1.0 -m "初始版本：24个微服务 Job"
git push --tags

# 回滚到之前版本
git checkout v1.0
# 触发种子 Job 重新生成
```

---

## 与 Jenkinsfile 的配合

Job DSL 创建 Job 结构，Jenkinsfile 定义构建逻辑：

**Job DSL** (jenkins-job-dsl 仓库):
```groovy
// 创建 Job
multibranchPipelineJob('ms/user-service') {
    branchSources {
        git {
            remote('http://gitlab.com/ms/user-service.git')
        }
    }
}
```

**Jenkinsfile** (user-service 项目仓库):
```groovy
// 定义构建流程
pipeline {
    agent { label 'dotnet' }
    stages {
        stage('Build') {
            steps {
                sh 'dotnet build'
            }
        }
    }
}
```

---

## 故障排查

### 问题：DSL 脚本错误

**错误信息**:
```
ERROR: Failed to execute DSL script
```

**解决**:
1. 查看种子 Job 的控制台输出
2. 使用 Job DSL Playground 测试语法
3. 检查 DSL 脚本权限

### 问题：Job 没有自动创建

**检查**:
1. 种子 Job 是否成功运行
2. SCM 轮询是否正常
3. Job DSL 插件是否安装

---

## 总结

### ✅ Job DSL 适用于:
- 批量创建相似的 Job
- 24个微服务的 Job 管理
- 自动化 Job 配置
- Job 结构版本控制

### 最佳实践组合:
1. **Job DSL**: 在 Git 仓库管理 Job 创建脚本
2. **Jenkinsfile**: 在项目仓库管理具体的构建流程
3. **种子 Job**: 自动应用 Job DSL 变更

这样实现了**完全的基础设施即代码（IaC）**！
