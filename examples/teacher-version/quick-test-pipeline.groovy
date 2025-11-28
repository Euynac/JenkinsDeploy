// 快速测试 Pipeline - 包含代码复制
// 适用于本地测试，无需 Git 仓库

pipeline {
    agent {
        label 'dotnet'
    }

    environment {
        PROJECT_NAME = 'TodoApp-backend'
        PROJECT_PATH = 'todoapp-backend-api-main'
        DOTNET_CLI_TELEMETRY_OPTOUT = '1'
        DOTNET_SKIP_FIRST_TIME_EXPERIENCE = '1'

        // Docker 镜像配置
        DOCKER_IMAGE_NAME = 'todoapp-backend'
        DOCKER_IMAGE_TAG = "${BUILD_NUMBER}"
    }

    stages {
        stage('Copy Project to Workspace') {
            steps {
                script {
                    echo "=========================================="
                    echo "复制测试项目到工作空间..."
                    echo "=========================================="

                    sh """
                        # 源目录（Docker 容器中挂载的路径）
                        SOURCE_DIR="/test-projects/teacher-version/todoapp-backend-api-main"

                        # 检查源目录是否存在
                        if [ -d "\$SOURCE_DIR" ]; then
                            echo "✅ 源目录存在: \$SOURCE_DIR"
                            # 复制整个项目
                            cp -r "\$SOURCE_DIR" ${WORKSPACE}/
                            echo "✅ 项目已复制到: ${WORKSPACE}/${PROJECT_PATH}"
                        else
                            echo "❌ 源目录不存在: \$SOURCE_DIR"
                            echo "请确保 docker-compose 配置中挂载了测试项目目录"
                            echo "检查 docker-compose-test-dotnet.yml 中的 volumes 配置"
                            exit 1
                        fi

                        # 验证复制结果
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
                script {
                    echo "=========================================="
                    echo "检查构建环境..."
                    echo "=========================================="

                    sh """
                        echo ".NET SDK 版本:"
                        dotnet --version
                        echo ""
                        echo "当前 Agent: ${NODE_NAME}"
                        echo "构建号: ${BUILD_NUMBER}"
                        echo ""
                        echo "当前用户: \$(whoami)"
                        echo "HOME 目录: \$HOME"

                        # 确保 NuGet 目录权限正确
                        if [ ! -d "\$HOME/.nuget" ]; then
                            echo "创建 NuGet 目录..."
                            mkdir -p "\$HOME/.nuget/NuGet"
                        fi

                        # 验证权限
                        if [ -w "\$HOME/.nuget" ]; then
                            echo "✅ NuGet 目录权限正常"
                        else
                            echo "❌ NuGet 目录权限异常，需要修复"
                            ls -la "\$HOME/.nuget" || true
                        fi
                    """
                }
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

                        mkdir -p ../test-results

                        dotnet test TodoApp-backend.Tests/TodoApp-backend.Tests.csproj \\
                            --configuration Release \\
                            --verbosity normal \\
                            --results-directory ../test-results \\
                            --logger 'trx;LogFileName=test-results.trx'

                        echo ""
                        echo "=========================================="
                        echo "✅ 测试完成！"
                        echo "=========================================="
                    """
                }
            }

            post {
                always {
                    // 发布测试结果（允许为空）
                    junit testResults: 'test-results/**/*.trx', allowEmptyResults: true

                    // 归档所有测试产物
                    archiveArtifacts artifacts: 'test-results/**/*', allowEmptyArchive: true
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                dir("${WORKSPACE}/${PROJECT_PATH}") {
                    script {
                        echo "=========================================="
                        echo "构建 Docker 镜像..."
                        echo "=========================================="

                        sh """
                            echo "Docker 版本:"
                            docker --version
                            echo ""

                            echo "当前用户: \$(whoami)"
                            echo "Docker 组成员:"
                            groups
                            echo ""

                            echo "构建镜像: ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}"
                            docker build -t ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG} .

                            # 同时打上 latest 标签
                            docker tag ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG} ${DOCKER_IMAGE_NAME}:latest

                            echo ""
                            echo "验证镜像:"
                            docker images | grep ${DOCKER_IMAGE_NAME}

                            echo ""
                            echo "=========================================="
                            echo "✅ 镜像构建完成！"
                            echo "=========================================="
                        """
                    }
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
- Docker 镜像: ✅

镜像信息:
- 镜像名称: ${DOCKER_IMAGE_NAME}
- 镜像标签: ${DOCKER_IMAGE_TAG}, latest

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
