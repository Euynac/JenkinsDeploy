# 使用 Jenkinsfile 管理流水线（推荐方案）

## 项目结构

```
your-microservice/              # 你的项目仓库
├── src/                        # 源代码
├── tests/                      # 测试代码
├── Jenkinsfile                 # Pipeline定义 ⭐
├── Jenkinsfile.dev             # 开发环境Pipeline（可选）
├── Jenkinsfile.prod            # 生产环境Pipeline（可选）
└── README.md
```

## Jenkinsfile 示例

### 基础版本
```groovy
// Jenkinsfile
pipeline {
    agent { label 'dotnet' }

    stages {
        stage('构建') {
            steps {
                sh 'dotnet build'
            }
        }

        stage('测试') {
            steps {
                sh 'dotnet test'
            }
        }

        stage('打包') {
            steps {
                sh 'dotnet publish -o ./publish'
            }
        }
    }
}
```

### 完整版本（多环境）
```groovy
// Jenkinsfile
pipeline {
    agent { label 'dotnet' }

    parameters {
        choice(
            name: 'ENVIRONMENT',
            choices: ['dev', 'test', 'prod'],
            description: '部署环境'
        )
    }

    environment {
        PROJECT_NAME = 'microservice-user'
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
                sh 'dotnet restore'
            }
        }

        stage('代码质量') {
            when { branch 'develop' }
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh 'dotnet sonarscanner begin /k:"${PROJECT_NAME}"'
                    sh 'dotnet build'
                    sh 'dotnet sonarscanner end'
                }
            }
        }

        stage('编译') {
            steps {
                sh 'dotnet build --configuration Release'
            }
        }

        stage('单元测试') {
            steps {
                sh 'dotnet test --logger "trx"'
            }
            post {
                always {
                    mstest testResultsFile: '**/test-results.trx'
                }
            }
        }

        stage('打包') {
            steps {
                sh 'dotnet publish --configuration Release -o ./publish'
                sh 'cd publish && zip -r ../${PROJECT_NAME}-${BUILD_NUMBER}.zip .'
            }
        }

        stage('上传制品') {
            steps {
                archiveArtifacts artifacts: '*.zip', fingerprint: true
            }
        }

        stage('部署') {
            when {
                expression { params.ENVIRONMENT != '' }
            }
            steps {
                script {
                    if (params.ENVIRONMENT == 'prod') {
                        timeout(time: 1, unit: 'HOURS') {
                            input message: '确认部署到生产环境？',
                                  ok: '确认',
                                  submitter: 'admin,ops-team'
                        }
                    }

                    sh "./deploy.sh ${params.ENVIRONMENT}"
                }
            }
        }
    }

    post {
        success {
            echo '✅ 构建成功'
        }
        failure {
            echo '❌ 构建失败'
        }
        always {
            cleanWs()
        }
    }
}
```

## 在 Jenkins 中配置

### 1. 创建多分支流水线

**Dashboard** → **新建任务**

- 任务名称: `microservice-user`
- 类型: **多分支流水线**

### 2. 配置分支源

**Branch Sources** → **Add source** → **Git**

- Project Repository: `http://gitlab.internal.com/microservices/user.git`
- Credentials: 选择 GitLab 凭证

### 3. 构建配置

**Build Configuration**:
- Mode: **by Jenkinsfile**
- Script Path: `Jenkinsfile`

### 4. 扫描分支

**Scan Multibranch Pipeline Triggers**:
- ☑ Periodically if not otherwise run
- Interval: **1 hour**

### 5. 保存

点击"保存"，Jenkins 会自动扫描所有分支并创建对应的 Pipeline。

## 优势

### ✅ 版本控制
- Pipeline 和代码一起管理
- 每次提交都有对应的构建定义
- 可以回滚到历史版本

### ✅ 代码审查
- Pipeline 修改需要经过 Code Review
- 提交历史清晰可追溯

### ✅ 分支隔离
- 每个分支可以有不同的 Pipeline
- feature 分支可以测试新的构建流程

### ✅ 可移植性
- 项目换到新 Jenkins 实例，Pipeline 自动迁移
- 团队成员都能看到和修改 Pipeline

## 最佳实践

### 1. 一个项目一个 Jenkinsfile
```
microservice-user/
├── Jenkinsfile        # 主流水线
└── src/
```

### 2. 多环境使用参数
```groovy
parameters {
    choice(name: 'ENV', choices: ['dev', 'test', 'prod'])
}
```

### 3. 敏感信息使用凭证
```groovy
withCredentials([string(credentialsId: 'api-key', variable: 'API_KEY')]) {
    sh "curl -H 'Authorization: ${API_KEY}' ..."
}
```

### 4. 使用共享库（高级）
```groovy
@Library('my-shared-library') _

pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                buildMicroservice()  // 来自共享库
            }
        }
    }
}
```

## 示例：24个微服务的组织方式

### 方式 1: 每个微服务独立仓库
```
gitlab.com/microservices/
├── user-service/
│   ├── Jenkinsfile
│   └── src/
├── order-service/
│   ├── Jenkinsfile
│   └── src/
└── payment-service/
    ├── Jenkinsfile
    └── src/
```

Jenkins 中创建 24 个多分支流水线任务。

### 方式 2: 单体仓库（Monorepo）
```
gitlab.com/microservices/all-services/
├── services/
│   ├── user/
│   │   ├── Jenkinsfile
│   │   └── src/
│   ├── order/
│   │   ├── Jenkinsfile
│   │   └── src/
│   └── payment/
│       ├── Jenkinsfile
│       └── src/
└── shared/
```

Jenkins 中使用 Job DSL 自动扫描并创建流水线。

## 团队协作

### Git 工作流
```bash
# 开发人员修改 Pipeline
git checkout -b feature/improve-pipeline
vim Jenkinsfile
git add Jenkinsfile
git commit -m "优化构建流程：添加并行测试"
git push origin feature/improve-pipeline

# 提交 Merge Request
# Code Review 通过后合并
# Jenkins 自动使用新的 Pipeline
```

### Pipeline 权限控制
- **开发人员**: 可以修改 Jenkinsfile（通过 MR）
- **运维人员**: 审核 Jenkinsfile 变更
- **Jenkins 管理员**: 管理 Jenkins 系统配置

## 故障排查

### 问题：Jenkinsfile 语法错误

**解决**：
1. 使用 Blue Ocean 编辑器（可视化）
2. 本地验证：`jenkins-cli declarative-linter < Jenkinsfile`
3. 在测试分支先试验

### 问题：权限不足

**解决**：
```groovy
// 在 Jenkinsfile 中指定凭证
checkout([$class: 'GitSCM',
    branches: [[name: 'master']],
    userRemoteConfigs: [[
        url: 'http://gitlab.com/repo.git',
        credentialsId: 'gitlab-credentials'
    ]]
])
```

## 总结

✅ **推荐做法**:
- 每个项目仓库包含 Jenkinsfile
- Pipeline 代码和项目代码一起管理
- Jenkins 只存储系统配置和凭证

❌ **不推荐做法**:
- 将 Pipeline 定义存储在 Jenkins 中
- 通过 Web UI 手动配置
- 上传整个 jenkins_home 到 Git
