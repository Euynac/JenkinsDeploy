# 🚀 快速开始 - 测试 Docker Agent

## 当前状态

✅ Jenkins Master 运行中
✅ .NET Agent 已连接 (agent-dotnet-8)
✅ 测试项目已挂载到容器
✅ 一切准备就绪！

---

## 1分钟快速测试

### 步骤 1: 创建 Pipeline 任务

1. 访问 Jenkins: http://localhost:8080
2. 首页 > `新建任务`
3. 任务名称: `Test-DotNet-Agent-Quick`
4. 类型: `Pipeline`
5. 点击 `确定`

### 步骤 2: 配置 Pipeline

在 **Pipeline** 配置中：

1. Definition: `Pipeline script`
2. 粘贴以下脚本：

```groovy
// 复制这个文件的全部内容:
// examples/teacher-version/quick-test-pipeline.groovy
```

或者直接复制粘贴：

```groovy
pipeline {
    agent {
        label 'dotnet'
    }

    environment {
        PROJECT_NAME = 'TodoApp-backend'
        PROJECT_PATH = 'todoapp-backend-api-main'
        DOTNET_CLI_TELEMETRY_OPTOUT = '1'
        DOTNET_SKIP_FIRST_TIME_EXPERIENCE = '1'
    }

    stages {
        stage('Copy Project to Workspace') {
            steps {
                script {
                    echo "=========================================="
                    echo "复制测试项目到工作空间..."
                    echo "=========================================="

                    sh """
                        SOURCE_DIR="/test-projects/teacher-version/todoapp-backend-api-main"

                        if [ -d "\$SOURCE_DIR" ]; then
                            echo "✅ 源目录存在: \$SOURCE_DIR"
                            cp -r "\$SOURCE_DIR" ${WORKSPACE}/
                            echo "✅ 项目已复制到: ${WORKSPACE}/${PROJECT_PATH}"
                        else
                            echo "❌ 源目录不存在: \$SOURCE_DIR"
                            ls -la /test-projects/
                            exit 1
                        fi

                        if [ -d "${WORKSPACE}/${PROJECT_PATH}" ]; then
                            echo "✅ 验证成功"
                            ls -la ${WORKSPACE}/${PROJECT_PATH}
                        else
                            echo "❌ 复制失败"
                            exit 1
                        fi
                    """
                }
            }
        }

        stage('Environment Check') {
            steps {
                sh """
                    echo "=========================================="
                    echo "检查构建环境..."
                    echo "=========================================="
                    echo ".NET SDK 版本:"
                    dotnet --version
                    echo ""
                    echo "当前 Agent: ${NODE_NAME}"
                    echo "构建号: ${BUILD_NUMBER}"
                """
            }
        }

        stage('Restore Dependencies') {
            steps {
                dir("${WORKSPACE}/${PROJECT_PATH}") {
                    sh """
                        echo "=========================================="
                        echo "还原 NuGet 包..."
                        echo "=========================================="
                        dotnet restore --verbosity normal
                    """
                }
            }
        }

        stage('Build') {
            steps {
                dir("${WORKSPACE}/${PROJECT_PATH}") {
                    sh """
                        echo "=========================================="
                        echo "编译项目..."
                        echo "=========================================="
                        dotnet build --configuration Release --no-restore
                    """
                }
            }
        }

        stage('Unit Test') {
            steps {
                dir("${WORKSPACE}/${PROJECT_PATH}") {
                    sh """
                        echo "=========================================="
                        echo "运行单元测试..."
                        echo "=========================================="

                        mkdir -p ${WORKSPACE}/test-results

                        dotnet test TodoApp-backend.Tests/TodoApp-backend.Tests.csproj \\
                            --configuration Release \\
                            --no-build \\
                            --verbosity normal \\
                            --results-directory ${WORKSPACE}/test-results \\
                            --logger "trx;LogFileName=test-results.trx" \\
                            --logger "console;verbosity=detailed"

                        echo ""
                        echo "=========================================="
                        echo "✅ 测试完成！"
                        echo "=========================================="
                    """
                }
            }

            post {
                always {
                    junit testResults: 'test-results/**/*.trx',
                          allowEmptyResults: false

                    archiveArtifacts artifacts: 'test-results/**/*.trx',
                                     allowEmptyArchive: true
                }
            }
        }
    }

    post {
        success {
            echo """
========================================
✅ Docker Agent 测试成功！
========================================
项目: ${PROJECT_NAME}
Agent: ${NODE_NAME}
构建号: ${BUILD_NUMBER}

测试结果:
- 环境检查: ✅
- 依赖还原: ✅
- 项目编译: ✅
- 单元测试: ✅

Docker Agent 工作正常！
========================================
"""
        }

        failure {
            echo """
========================================
❌ 测试失败
========================================
请检查控制台输出获取详细信息
========================================
"""
        }
    }
}
```

### 步骤 3: 运行构建

1. 保存配置
2. 点击 `立即构建`
3. 点击构建号 `#1`
4. 点击 `控制台输出` 查看实时日志

---

## 预期结果

成功的构建应该显示：

```
✅ 源目录存在: /test-projects/teacher-version/todoapp-backend-api-main
✅ 项目已复制
========================================
检查构建环境...
========================================
.NET SDK 版本:
8.0.416

========================================
还原 NuGet 包...
========================================
Restore succeeded.

========================================
编译项目...
========================================
Build succeeded.

========================================
运行单元测试...
========================================
Passed!  - Failed:     0, Passed:     X, Total:     X

========================================
✅ Docker Agent 测试成功！
========================================
```

---

## 验证清单

- [ ] Agent 显示 "在线"
- [ ] 构建在 agent-dotnet-8 上执行
- [ ] 测试项目成功复制
- [ ] .NET SDK 8.0.416 可用
- [ ] 依赖还原成功
- [ ] 项目编译成功
- [ ] 单元测试全部通过
- [ ] 测试报告已发布

---

## 问题排查

### 问题: 找不到 dotnet 标签的 Agent

**检查**: Jenkins > 系统管理 > 节点管理 > agent-dotnet-8

确认标签包含：`dotnet`

如果没有，添加标签后保存。

### 问题: 源目录不存在

**检查**:

```bash
docker exec jenkins-agent-dotnet-test ls -la /test-projects/teacher-version/
```

应该显示 `todoapp-backend-api-main` 目录。

---

## 下一步

测试成功后：

1. ✅ 查看测试报告（Test Result 页面）
2. ✅ 检查构建产物（Build Artifacts）
3. ✅ 尝试完整的 Pipeline（`Jenkinsfile-simple`）
4. ✅ 配置你的真实项目

---

## 文件说明

```
examples/teacher-version/
├── START_HERE.md                    # 本文件 - 快速开始
├── AGENT_TEST_GUIDE.md             # 详细测试指南
├── quick-test-pipeline.groovy      # 快速测试脚本（含代码复制）
├── Jenkinsfile-simple              # 简化的 Jenkinsfile（需要 Git）
├── backend.groovy                  # 完整的生产 Pipeline
├── backend.groovy.bak              # 原始备份
└── todoapp-backend-api-main/       # .NET 测试项目
```

---

**预计测试时间**: 2-3 分钟（首次构建 5-8 分钟）
**成功率**: 99%（如果 Agent 在线）

🎉 **开始测试吧！**
