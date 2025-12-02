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

        // SonarQube 项目配置（项目键必须小写，只能包含字母数字、'-'、'_'、'.'、':'）
        SONAR_PROJECT_KEY = 'todoapp-backend'
        SONAR_PROJECT_NAME = 'TodoApp Backend API'

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
                        SOURCE_DIR="/test-projects/todoapp-backend-api-main"

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
                        echo "运行单元测试 + 代码覆盖率收集..."
                        echo "=========================================="

                        mkdir -p ../test-results
                        mkdir -p ../test-results/coverage

                        # 使用 coverlet.collector 数据收集器方式收集覆盖率
                        # 同时生成 Cobertura（用于 Jenkins Coverage 插件）和 OpenCover（用于 SonarQube）格式
                        dotnet test TodoApp-backend.Tests/TodoApp-backend.Tests.csproj \\
                            --configuration Release \\
                            --verbosity normal \\
                            --results-directory ../test-results \\
                            --logger 'trx;LogFileName=test-results.trx' \\
                            --collect:"XPlat Code Coverage" \\
                            -- DataCollectionRunSettings.DataCollectors.DataCollector.Configuration.Format=cobertura,opencover \\
                            -- DataCollectionRunSettings.DataCollectors.DataCollector.Configuration.Exclude="[*.Tests]*" \\
                            -- DataCollectionRunSettings.DataCollectors.DataCollector.Configuration.ExcludeByFile="**/Migrations/**"

                        # coverlet.collector 会将覆盖率文件生成在 TestResults 目录下
                        # 查找并复制覆盖率文件到指定位置
                        echo "查找覆盖率文件..."

                        # 查找 Cobertura 格式文件（用于 Jenkins Coverage 插件）
                        COBERTURA_FILE=\$(find ../test-results -name "coverage.cobertura.xml" -type f | head -1)
                        if [ -n "\$COBERTURA_FILE" ]; then
                            echo "找到 Cobertura 覆盖率文件: \$COBERTURA_FILE"
                            cp "\$COBERTURA_FILE" "../test-results/coverage/coverage.cobertura.xml"
                            echo "Cobertura 文件已复制到: ${WORKSPACE}/test-results/coverage/coverage.cobertura.xml"
                            ls -lh "../test-results/coverage/coverage.cobertura.xml"
                        else
                            echo "警告: 未找到 Cobertura 覆盖率文件"
                        fi

                        # 查找 OpenCover 格式文件（用于 SonarQube）
                        OPENCOVER_FILE=\$(find ../test-results -name "coverage.opencover.xml" -type f | head -1)
                        if [ -n "\$OPENCOVER_FILE" ]; then
                            echo "找到 OpenCover 覆盖率文件: \$OPENCOVER_FILE"
                            cp "\$OPENCOVER_FILE" "../test-results/coverage/coverage.opencover.xml"
                            echo "OpenCover 文件已复制到: ${WORKSPACE}/test-results/coverage/coverage.opencover.xml"
                            ls -lh "../test-results/coverage/coverage.opencover.xml"
                        else
                            echo "警告: 未找到 OpenCover 覆盖率文件"
                            echo "检查 TestResults 目录:"
                            find ../test-results -name "*.xml" -type f | head -10 || true
                        fi

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

        stage('E2E Tests') {
            steps {
                script {
                    echo "=========================================="
                    echo "复制 E2E 测试项目..."
                    echo "=========================================="

                    sh """
                        # 复制 E2E 测试项目
                        E2E_SOURCE="/test-projects/todoapp-backend-api-e2etest-main"
                        E2E_DEST="${WORKSPACE}/todoapp-backend-api-e2etest"

                        if [ -d "\$E2E_SOURCE" ]; then
                            echo "✅ E2E 源目录存在: \$E2E_SOURCE"

                            # 删除旧目录（如果存在）确保全新复制
                            if [ -d "\$E2E_DEST" ]; then
                                echo "删除旧的 E2E 测试目录..."
                                rm -rf "\$E2E_DEST"
                            fi

                            # 复制新目录
                            cp -r "\$E2E_SOURCE" "\$E2E_DEST"
                            echo "✅ E2E 项目已复制到: \$E2E_DEST"
                        else
                            echo "❌ E2E 源目录不存在: \$E2E_SOURCE"
                            exit 1
                        fi
                    """

                    echo "=========================================="
                    echo "检查环境..."
                    echo "=========================================="

                    sh """
                        echo "Python 版本:"
                        python3 --version
                        echo ""
                        echo "Docker 版本:"
                        docker --version
                        echo ""
                    """

                    dir('todoapp-backend-api-e2etest') {
                        sh '''
                            set -e

                            echo "=== 设置 E2E 测试环境变量 ==="
                            export TEST_DB_HOST="todoapp-postgres-test"
                            export TEST_DB_PORT="5432"
                            export TEST_DB_NAME="todoapp_test"
                            export TEST_DB_USER="postgres"
                            export TEST_DB_PASSWORD="postgres"
                            export API_BASE_URL="http://localhost:5085"

                            echo "数据库: ${TEST_DB_HOST}:${TEST_DB_PORT}"
                            echo "API: ${API_BASE_URL}"

                            echo "=== 准备 Python 虚拟环境 ==="
                            if [ ! -d "venv" ]; then
                                echo "创建新的虚拟环境..."
                                python3 -m venv venv
                            else
                                echo "使用现有虚拟环境..."
                            fi

                            . venv/bin/activate
                            pip install --upgrade pip
                            pip install -r requirements.txt

                            echo "=== 启动测试数据库 ==="

                            # 检测 Docker Compose 命令
                            if docker compose version > /dev/null 2>&1; then
                                DOCKER_COMPOSE_CMD="docker compose"
                            elif command -v docker-compose > /dev/null 2>&1; then
                                DOCKER_COMPOSE_CMD="docker-compose"
                            else
                                echo "错误: 未找到 docker-compose 或 docker compose 命令"
                                exit 1
                            fi

                            echo "使用 Docker Compose 命令: ${DOCKER_COMPOSE_CMD}"

                            # 启动测试数据库
                            ${DOCKER_COMPOSE_CMD} -f docker-compose.test.yml up -d

                            # 等待数据库容器运行
                            echo "等待数据库容器启动..."
                            sleep 5

                            # 验证数据库容器是否运行
                            if ! docker ps | grep -q todoapp-postgres-test; then
                                echo "错误: 测试数据库启动失败"
                                docker logs todoapp-postgres-test || true
                                exit 1
                            fi

                            # 等待 Docker DNS 注册容器名（关键！）
                            echo "等待 Docker DNS 注册..."
                            MAX_DNS_RETRIES=30
                            DNS_RETRY_COUNT=0
                            while [ $DNS_RETRY_COUNT -lt $MAX_DNS_RETRIES ]; do
                                if getent hosts todoapp-postgres-test > /dev/null 2>&1; then
                                    echo "✅ DNS 解析成功: todoapp-postgres-test"
                                    break
                                fi
                                DNS_RETRY_COUNT=$((DNS_RETRY_COUNT + 1))
                                echo "等待 DNS 注册... ($DNS_RETRY_COUNT/$MAX_DNS_RETRIES)"
                                sleep 1
                            done

                            if [ $DNS_RETRY_COUNT -ge $MAX_DNS_RETRIES ]; then
                                echo "错误: DNS 注册超时"
                                exit 1
                            fi

                            # 等待数据库健康检查通过
                            echo "等待数据库健康检查..."
                            MAX_DB_RETRIES=30
                            DB_RETRY_COUNT=0
                            while [ $DB_RETRY_COUNT -lt $MAX_DB_RETRIES ]; do
                                if docker exec todoapp-postgres-test pg_isready -U postgres > /dev/null 2>&1; then
                                    echo "✅ 数据库就绪"
                                    break
                                fi
                                DB_RETRY_COUNT=$((DB_RETRY_COUNT + 1))
                                echo "等待数据库就绪... ($DB_RETRY_COUNT/$MAX_DB_RETRIES)"
                                sleep 1
                            done

                            if [ $DB_RETRY_COUNT -ge $MAX_DB_RETRIES ]; then
                                echo "错误: 数据库健康检查超时"
                                exit 1
                            fi

                            echo "✅ 测试数据库已完全就绪"

                            echo "=== 启动后端 API 服务 ==="

                            # 检查并清理端口 5085
                            echo "检查端口 5085..."
                            if lsof -i :5085 > /dev/null 2>&1; then
                                echo "警告: 端口 5085 已被占用，停止现有进程..."
                                # 使用循环逐个杀死进程，更可靠
                                lsof -ti :5085 | while read pid; do
                                    echo "终止进程: $pid"
                                    kill -9 $pid 2>/dev/null || true
                                done
                                sleep 2

                                # 再次检查
                                if lsof -i :5085 > /dev/null 2>&1; then
                                    echo "错误: 端口 5085 仍被占用，无法启动 API"
                                    lsof -i :5085
                                    exit 1
                                fi
                                echo "端口 5085 已释放"
                            else
                                echo "端口 5085 空闲"
                            fi

                            # 后台启动 API 服务
                            cd ../todoapp-backend-api-main

                            # 使用环境变量启动 API（直接在 nohup 命令中设置）
                            # 使用容器名和内部端口（PostgreSQL 容器在同一个 Docker 网络中）
                            nohup env \
                                ConnectionStrings__DefaultConnection="Host=todoapp-postgres-test;Port=5432;Database=todoapp_test;Username=postgres;Password=postgres" \
                                ASPNETCORE_ENVIRONMENT=Test \
                                dotnet run --urls http://localhost:5085 > ../api.log 2>&1 &
                            API_PID=$!
                            echo $API_PID > ../api.pid
                            echo "API 进程 PID: $API_PID"

                            # 等待 API 启动
                            echo "等待 API 服务启动..."
                            API_STARTED=false
                            MAX_RETRIES=60
                            RETRY_COUNT=0

                            while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
                                # 检查端口是否在监听（不检查 PID，因为 dotnet run 会在编译后替换进程）
                                if curl -s -f http://localhost:5085/swagger/index.html > /dev/null 2>&1; then
                                    echo "✅ API 服务已启动并就绪"
                                    API_STARTED=true
                                    break
                                fi

                                RETRY_COUNT=$((RETRY_COUNT + 1))
                                echo "等待 API 启动... ($RETRY_COUNT/$MAX_RETRIES)"

                                if [ $((RETRY_COUNT % 5)) -eq 0 ]; then
                                    echo "当前 API 日志（最后 20 行）:"
                                    tail -20 ../api.log 2>/dev/null || echo "日志文件为空或不存在"
                                fi

                                sleep 2
                            done

                            if [ "$API_STARTED" = false ]; then
                                echo "错误: API 服务启动超时"
                                cat ../api.log || true
                                exit 1
                            fi

                            echo "=== 运行 E2E 测试 ==="
                            cd ../todoapp-backend-api-e2etest
                            . venv/bin/activate
                            pytest --alluredir=test-results/allure-results -v
                        '''
                    }
                }
            }

            post {
                always {
                    script {
                        // 清理 API 进程
                        sh '''
                            if [ -f api.pid ]; then
                                PID=$(cat api.pid)
                                if ps -p $PID > /dev/null 2>&1; then
                                    echo "停止 API 服务 (PID: $PID)"
                                    kill $PID || true
                                    sleep 2
                                    if ps -p $PID > /dev/null 2>&1; then
                                        kill -9 $PID || true
                                    fi
                                fi
                                rm -f api.pid
                            fi
                            rm -f api.log || true
                        '''

                        // 清理测试数据库
                        dir('todoapp-backend-api-e2etest') {
                            sh '''
                                if docker compose version > /dev/null 2>&1; then
                                    DOCKER_COMPOSE_CMD="docker compose"
                                elif command -v docker-compose > /dev/null 2>&1; then
                                    DOCKER_COMPOSE_CMD="docker-compose"
                                else
                                    DOCKER_COMPOSE_CMD="docker-compose"
                                fi

                                ${DOCKER_COMPOSE_CMD} -f docker-compose.test.yml down -v || true
                            '''
                        }

                        // 归档测试结果
                        archiveArtifacts artifacts: 'todoapp-backend-api-e2etest/test-results/**/*',
                                         allowEmptyArchive: true
                    }
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                script {
                    echo "=========================================="
                    echo "开始 SonarQube 代码扫描..."
                    echo "=========================================="
                }

                dir("${WORKSPACE}/${PROJECT_PATH}") {
                    withSonarQubeEnv('sonarqube-server') {  // 使用 Jenkins 中配置的 SonarQube 服务器名称
                        sh """
                            # 使用 dotnet sonarscanner（.NET 8.0 推荐方式）
                            # dotnet-sonarscanner 已预装在 Agent 镜像中

                            echo "验证 dotnet-sonarscanner 是否可用..."
                            VERSION_OUTPUT=\$(dotnet sonarscanner --version 2>&1 || true)
                            echo "\$VERSION_OUTPUT" | head -3

                            if ! echo "\$VERSION_OUTPUT" | grep -q "SonarScanner for .NET"; then
                                echo "❌ 错误: dotnet-sonarscanner 未找到"
                                echo "请确保 Agent 镜像已包含此工具"
                                exit 1
                            fi

                            echo "✅ dotnet-sonarscanner 已就绪"
                            echo ""

                            # 开始 SonarQube 分析
                            echo "开始 SonarQube 分析..."

                            # 确定覆盖率文件路径（优先使用 OpenCover 格式）
                            if [ -f "${WORKSPACE}/test-results/coverage/coverage.opencover.xml" ]; then
                                COVERAGE_FILE="${WORKSPACE}/test-results/coverage/coverage.opencover.xml"
                                echo "使用 OpenCover 格式覆盖率文件: \$COVERAGE_FILE"
                            elif [ -f "${WORKSPACE}/test-results/coverage/coverage.cobertura.xml" ]; then
                                COVERAGE_FILE="${WORKSPACE}/test-results/coverage/coverage.cobertura.xml"
                                echo "使用 Cobertura 格式覆盖率文件: \$COVERAGE_FILE（注意：SonarQube C# 插件更推荐 OpenCover 格式）"
                            else
                                echo "警告: 未找到覆盖率文件，SonarQube 将无法导入覆盖率数据"
                                COVERAGE_FILE=""
                            fi

                            # 开始 SonarQube 分析（直接使用参数，避免引号嵌套问题）
                            # 注意：项目键必须小写，sonar.tests 参数在 .NET Scanner 中不支持
                            if [ -n "\$COVERAGE_FILE" ] && echo "\$COVERAGE_FILE" | grep -q "opencover"; then
                                # 包含覆盖率文件
                                dotnet sonarscanner begin \\
                                    /k:"${SONAR_PROJECT_KEY}" \\
                                    /n:"${SONAR_PROJECT_NAME}" \\
                                    /v:"${BUILD_NUMBER}" \\
                                    /d:sonar.projectBaseDir="${WORKSPACE}/${PROJECT_PATH}" \\
                                    /d:sonar.exclusions="**/Migrations/**" \\
                                    /d:sonar.test.exclusions="**/*Tests/**" \\
                                    /d:sonar.test.inclusions="**/*Tests/**/*.cs" \\
                                    /d:sonar.cs.opencover.reportsPaths="\$COVERAGE_FILE"
                            else
                                # 无覆盖率文件或格式不正确
                                if [ -n "\$COVERAGE_FILE" ]; then
                                    echo "警告: 覆盖率文件格式不是 OpenCover，SonarQube 可能无法正确解析"
                                fi
                                dotnet sonarscanner begin \\
                                    /k:"${SONAR_PROJECT_KEY}" \\
                                    /n:"${SONAR_PROJECT_NAME}" \\
                                    /v:"${BUILD_NUMBER}" \\
                                    /d:sonar.projectBaseDir="${WORKSPACE}/${PROJECT_PATH}" \\
                                    /d:sonar.exclusions="**/Migrations/**" \\
                                    /d:sonar.test.exclusions="**/*Tests/**" \\
                                    /d:sonar.test.inclusions="**/*Tests/**/*.cs"
                            fi

                            # 构建项目（SonarQube 需要分析构建后的代码）
                            echo "构建项目以供 SonarQube 分析..."
                            dotnet build --configuration Release --no-restore --verbosity normal

                            # 结束 SonarQube 分析
                            echo "结束 SonarQube 分析并上传结果..."
                            dotnet sonarscanner end

                            # 检查是否生成了 report-task.txt（验证扫描是否成功）
                            # report-task.txt 可能在多个位置：.sonar/ 或 .sonarqube/out/.sonar/
                            REPORT_TASK_FILE=""
                            if [ -f ".sonar/report-task.txt" ]; then
                                REPORT_TASK_FILE=".sonar/report-task.txt"
                            elif [ -f ".sonarqube/out/.sonar/report-task.txt" ]; then
                                REPORT_TASK_FILE=".sonarqube/out/.sonar/report-task.txt"
                            elif [ -f "${WORKSPACE}/${PROJECT_PATH}/.sonar/report-task.txt" ]; then
                                REPORT_TASK_FILE="${WORKSPACE}/${PROJECT_PATH}/.sonar/report-task.txt"
                            fi

                            if [ -n "\$REPORT_TASK_FILE" ] && [ -f "\$REPORT_TASK_FILE" ]; then
                                echo "✅ SonarQube 分析完成"
                                echo "报告文件位置: \$REPORT_TASK_FILE"
                                cat "\$REPORT_TASK_FILE"
                            else
                                echo "⚠️  警告: 未找到 report-task.txt 文件"
                                echo "扫描可能已成功，但报告文件未在预期位置"
                                echo "检查可能的路径:"
                                find . -name "report-task.txt" -type f 2>/dev/null | head -5 || echo "未找到"
                            fi
                        """
                    }
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
                            groups 2>/dev/null || echo "组 ID: \$(id -G)"
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
- E2E 测试: ✅
- SonarQube 代码扫描: ✅
- Docker 镜像: ✅

镜像信息:
- 镜像名称: ${DOCKER_IMAGE_NAME}
- 镜像标签: ${DOCKER_IMAGE_TAG}, latest

SonarQube 报告:
- 访问地址: http://localhost:9000
- 项目键: ${SONAR_PROJECT_KEY}
- 项目名称: ${SONAR_PROJECT_NAME}

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
