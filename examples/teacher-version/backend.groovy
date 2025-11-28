pipeline {

     agent {
        label 'dotnet'  // 修改为使用 dotnet 标签
    }
    
    // 环境变量配置
    environment {
        // 项目配置
        PROJECT_NAME = 'TodoApp-backend'
        PROJECT_PATH = 'todoapp-backend-api'
        TARGET_FRAMEWORK = 'net8.0'
        
        // 构建输出目录
        BUILD_DIR = "${WORKSPACE}/${PROJECT_PATH}"
        PUBLISH_DIR = "${WORKSPACE}/publish"
        ARTIFACTS_DIR = "${WORKSPACE}/artifacts"
        
        // GitLab 配置（根据实际情况修改）
        GITLAB_URL = 'https://ci-pilot.hohistar.com.cn/gitlab'
        GITLAB_CREDENTIALS_ID = 'ci-pilot-checkout-id'
        GITLAB_REPO = '/root/todoapp-backend-api'
        GIT_BRANCH = 'main'
        
        // SonarQube 配置
        // SCANNER_HOME = tool 'ms-scanner-8'  // 使用已配置的 MSBuild Scanner（作为备用）
    }
    

    stages {
        // 阶段 1: 代码检出（简化：使用本地代码）
        stage('Checkout') {
            steps {
                script {
                    echo "=========================================="
                    echo "使用本地代码进行测试..."
                    echo "项目路径: ${PROJECT_PATH}"
                    echo "=========================================="

                    // 检查项目目录是否存在
                    sh """
                        if [ -d "${PROJECT_PATH}" ]; then
                            echo "✅ 项目目录存在"
                            ls -la ${PROJECT_PATH}
                        else
                            echo "❌ 项目目录不存在: ${PROJECT_PATH}"
                            echo "当前目录内容:"
                            ls -la
                            exit 1
                        fi
                    """
                }
            }
        }
        
        // 阶段 2: 环境准备
        stage('Setup Environment') {
            steps {
                script {
                    echo "=========================================="
                    echo "准备构建环境..."
                    echo "=========================================="
                    
                    // 检查 .NET SDK 是否安装
                    def dotnetVersion = sh(
                        script: 'dotnet --version || echo "NOT_INSTALLED"',
                        returnStdout: true
                    ).trim()
                    
                    if (dotnetVersion == 'NOT_INSTALLED') {
                        error(".NET SDK 未安装，请确保 Jenkins 节点已安装 .NET 8.0 SDK")
                    }
                    
                    echo ".NET SDK 版本: ${dotnetVersion}"
                    
                    // 清理之前的构建产物
                    sh """
                        rm -rf ${PUBLISH_DIR}
                        rm -rf ${ARTIFACTS_DIR}
                        mkdir -p ${PUBLISH_DIR}
                        mkdir -p ${ARTIFACTS_DIR}
                    """
                }
            }
        }
        
        // 阶段 3: 还原依赖
        stage('Restore') {
            steps {
                script {
                    echo "=========================================="
                    echo "还原 NuGet 包依赖..."
                    echo "=========================================="
                }
                
                dir("${BUILD_DIR}") {
                    sh """
                        dotnet restore --verbosity normal
                    """
                }
            }
        }
        
        // 阶段 4: 构建项目
        stage('Build') {
            steps {
                script {
                    echo "=========================================="
                    echo "构建项目（包括测试项目）..."
                    echo "=========================================="
                }
                
                dir("${BUILD_DIR}") {
                    sh """
                        # 构建所有项目（包括测试项目）
                        dotnet build --configuration Release --no-restore --verbosity normal
                        
                        # 创建测试结果目录
                        mkdir -p ${WORKSPACE}/test-results
                    """
                }
            }
        }
        
        // 阶段 5: 运行测试
        stage('Test') {
            steps {
                dir("${BUILD_DIR}") {
                    script {
                        def testProj = "TodoApp-backend.Tests/TodoApp-backend.Tests.csproj"
                        if (!fileExists(testProj)) {
                            echo "未找到测试项目，跳过测试阶段"
                            return
                        }

                        sh """
                            set -e

                            mkdir -p ${WORKSPACE}/test-results
                            mkdir -p ${WORKSPACE}/test-results/coverage

                            echo "=== 运行单元测试 + Coverlet 覆盖率收集 ==="

                            # 使用 coverlet.collector 数据收集器方式收集覆盖率
                            # 同时生成 Cobertura（用于 Jenkins Coverage 插件）和 OpenCover（用于 SonarQube）格式
                            dotnet test "${testProj}" \\
                                --configuration Release \\
                                --verbosity normal \\
                                --results-directory "${WORKSPACE}/test-results" \\
                                --logger "trx;LogFileName=test-results.trx" \\
                                --logger "junit;LogFilePath=${WORKSPACE}/test-results/junit-test-results.xml" \\
                                --collect:"XPlat Code Coverage" \\
                                -- DataCollectionRunSettings.DataCollectors.DataCollector.Configuration.Format=cobertura,opencover \\
                                -- DataCollectionRunSettings.DataCollectors.DataCollector.Configuration.Exclude="[*.Tests]*" \\
                                -- DataCollectionRunSettings.DataCollectors.DataCollector.Configuration.ExcludeByFile="**/Migrations/**"

                            # coverlet.collector 会将覆盖率文件生成在 TestResults 目录下
                            # 查找并复制覆盖率文件到指定位置
                            echo "查找覆盖率文件..."
                            
                            # 查找 Cobertura 格式文件（用于 Jenkins Coverage 插件）
                            COBERTURA_FILE=\$(find ${WORKSPACE}/test-results -name "coverage.cobertura.xml" -type f | head -1)
                            if [ -n "\$COBERTURA_FILE" ]; then
                                echo "找到 Cobertura 覆盖率文件: \$COBERTURA_FILE"
                                cp "\$COBERTURA_FILE" "${WORKSPACE}/test-results/coverage/coverage.cobertura.xml"
                                echo "Cobertura 文件已复制到: ${WORKSPACE}/test-results/coverage/coverage.cobertura.xml"
                                ls -lh "${WORKSPACE}/test-results/coverage/coverage.cobertura.xml"
                            else
                                echo "警告: 未找到 Cobertura 覆盖率文件"
                            fi
                            
                            # 查找 OpenCover 格式文件（用于 SonarQube）
                            OPENCOVER_FILE=\$(find ${WORKSPACE}/test-results -name "coverage.opencover.xml" -type f | head -1)
                            if [ -n "\$OPENCOVER_FILE" ]; then
                                echo "找到 OpenCover 覆盖率文件: \$OPENCOVER_FILE"
                                cp "\$OPENCOVER_FILE" "${WORKSPACE}/test-results/coverage/coverage.opencover.xml"
                                echo "OpenCover 文件已复制到: ${WORKSPACE}/test-results/coverage/coverage.opencover.xml"
                                ls -lh "${WORKSPACE}/test-results/coverage/coverage.opencover.xml"
                            else
                                echo "警告: 未找到 OpenCover 覆盖率文件"
                                echo "检查 TestResults 目录:"
                                find ${WORKSPACE}/test-results -name "*.xml" -type f | head -10 || true
                            fi
                        """
                    }
                }
            }

            post {
                always {
                    // 如果 JunitXml.TestLogger 没生成（某些情况下会失败），就用 trx2junit 兜底
                    script {
                        if (!fileExists("${WORKSPACE}/test-results/junit-test-results.xml")) {
                            def trxFiles = findFiles(glob: 'test-results/**/*.trx')
                            if (trxFiles) {
                                def trxPath = trxFiles[0].path
                                echo "JUnit 文件未生成，尝试从 TRX 转换: ${trxPath}"
                                sh """
                                    set +e
                                    if ! command -v trx2junit > /dev/null 2>&1; then
                                        echo "安装 trx2junit 工具..."
                                        dotnet tool install -g trx2junit --version 3.1.0 --ignore-failed-sources
                                        export PATH="\$HOME/.dotnet/tools:\$PATH"
                                    fi

                                    if command -v trx2junit > /dev/null 2>&1; then
                                        trx2junit '${trxPath}'
                                        # trx2junit 会生成同名 .xml 文件
                                        xml_file="\$(dirname '${trxPath}')/\$(basename '${trxPath}' .trx).xml"
                                        if [ -f "\$xml_file" ]; then
                                            mv "\$xml_file" '${WORKSPACE}/test-results/junit-test-results.xml'
                                            echo "已成功转换为 JUnit 格式"
                                        fi
                                    fi
                                """
                            }
                        }
                    }

                    // 发布 JUnit 测试报告（Jenkins 原生支持）
                    junit testResults: 'test-results/junit-test-results.xml',
                          allowEmptyResults: true,
                          keepLongStdio: true

                    // 归档所有测试产物
                    archiveArtifacts artifacts: 'test-results/**/*.trx, test-results/**/*.xml',
                                     allowEmptyArchive: true

                    // 发布代码覆盖率报告（需安装 Coverage 插件）
                    // 注意：Cobertura 插件已废弃，请使用 Coverage 插件
                    // 文档：https://plugins.jenkins.io/coverage/
                    script {
                        try {
                            recordCoverage(
                                tools: [[parser: 'COBERTURA', pattern: 'test-results/coverage/coverage.cobertura.xml']],
                                sourceCodeRetention: 'LAST_BUILD'  // 存储最后一次构建的源代码
                            )
                            echo "✅ 代码覆盖率报告已发布"
                        } catch (MissingMethodException e) {
                            echo "⚠️  警告: Coverage 插件未安装"
                            echo "请安装 'Coverage' 插件以查看覆盖率趋势图"
                            echo "Manage Jenkins → Manage Plugins → Available → 搜索 'Coverage'"
                            echo "插件页面: https://plugins.jenkins.io/coverage/"
                        } catch (Exception e) {
                            echo "⚠️  警告: 覆盖率报告发布失败"
                            echo "错误信息: ${e.getMessage()}"
                        }
                    }
                }
            }
        }

        // ===== 以下阶段已移除，仅用于本地测试 =====
        // - E2E Tests
        // - SonarQube Analysis
        // - Publish
        // - Package
        // - Archive Artifacts
        // - Deploy to Nexus

        /* 已注释
        stage('E2E Tests') {
            steps {
                script {
                    echo "=========================================="
                    echo "开始端到端测试..."
                    echo "=========================================="
                    
                    // 检出 E2E 测试项目
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: '*/main']],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [
                            [$class: 'CleanBeforeCheckout'],
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: 'todoapp-backend-api-e2etest']
                        ],
                        submoduleCfg: [],
                        userRemoteConfigs: [[
                            credentialsId: "${GITLAB_CREDENTIALS_ID}",
                            url: "${GITLAB_URL}/root/todoapp-backend-api-e2etest.git"
                        ]]
                    ])
                    
                    // 检查 Python 环境
                    def pythonVersion = sh(
                        script: 'python3 --version || echo "NOT_INSTALLED"',
                        returnStdout: true
                    ).trim()
                    
                    if (pythonVersion.contains('NOT_INSTALLED')) {
                        error("Python 3 未安装，请确保 Jenkins 节点已安装 Python 3.8+。参考 todoapp-backend-api-e2etest/JENKINS.md 进行配置。")
                    }
                    
                    echo "Python 版本: ${pythonVersion}"
                    
                    // 检查 Docker
                    def dockerVersion = sh(
                        script: 'docker --version || echo "NOT_INSTALLED"',
                        returnStdout: true
                    ).trim()
                    
                    if (dockerVersion.contains('NOT_INSTALLED')) {
                        error("Docker 未安装，请确保 Jenkins 节点已安装 Docker。参考 todoapp-backend-api-e2etest/JENKINS.md 进行配置。")
                    }
                    
                    echo "Docker 版本: ${dockerVersion}"
                    
                    dir('todoapp-backend-api-e2etest') {
                        sh '''
                            set -e
                            
                            echo "=== 准备 Python 虚拟环境 ==="
                            
                            # 创建虚拟环境（如果不存在）
                            if [ ! -d "venv" ]; then
                                echo "创建新的虚拟环境..."
                                python3 -m venv venv
                            else
                                echo "使用现有虚拟环境..."
                            fi
                            
                            # 激活虚拟环境并安装依赖
                            # 使用 . 代替 source（兼容 /bin/sh）
                            . venv/bin/activate
                            pip install --upgrade pip
                            pip install -r requirements.txt
                            
                            echo "=== 启动测试数据库 ==="
                            # 输出当前用户名
                            echo "当前用户名: $(whoami)"
                            
                            # 检测 Docker Compose 命令
                            if docker compose version &> /dev/null 2>&1; then
                                DOCKER_COMPOSE_CMD="docker compose"
                            elif command -v docker-compose &> /dev/null 2>&1; then
                                DOCKER_COMPOSE_CMD="docker-compose"
                            else
                                echo "错误: 未找到 docker-compose 或 docker compose 命令"
                                exit 1
                            fi
                            
                            echo "使用 Docker Compose 命令: ${DOCKER_COMPOSE_CMD}"
                            
                            # 启动测试数据库
                            ${DOCKER_COMPOSE_CMD} -f docker-compose.test.yml up -d
                            
                            # 等待数据库就绪
                            echo "等待数据库就绪..."
                            sleep 10
                            
                            # 验证数据库是否运行
                            if ! docker ps | grep -q todoapp-postgres-test; then
                                echo "错误: 测试数据库启动失败"
                                docker logs todoapp-postgres-test || true
                                exit 1
                            fi
                            
                            echo "测试数据库已启动"
                            
                            echo "=== 启动后端 API 服务 ==="
                            
                            # 检查端口是否被占用
                            if lsof -i :5085 > /dev/null 2>&1 || netstat -tuln 2>/dev/null | grep -q ":5085 " || ss -tuln 2>/dev/null | grep -q ":5085 "; then
                                echo "警告: 端口 5085 已被占用，尝试停止现有进程..."
                                lsof -ti :5085 | xargs kill -9 2>/dev/null || true
                                sleep 2
                            fi
                            
                            # 设置测试环境变量
                            export ConnectionStrings__DefaultConnection="Host=localhost;Port=5433;Database=todoapp_test;Username=postgres;Password=postgres"
                            export ASPNETCORE_ENVIRONMENT=Test
                            
                            echo "环境变量已设置:"
                            echo "  ConnectionStrings__DefaultConnection: $ConnectionStrings__DefaultConnection"
                            echo "  ASPNETCORE_ENVIRONMENT: $ASPNETCORE_ENVIRONMENT"
                            
                            # 后台启动 API 服务
                            cd ../todoapp-backend-api
                            echo "当前目录: $(pwd)"
                            echo "检查 .NET SDK:"
                            dotnet --version || echo "错误: .NET SDK 未找到"
                            
                            echo "开始启动 API 服务..."
                            # 清空之前的日志
                            > ../api.log
                            nohup dotnet run --urls http://localhost:5085 >> ../api.log 2>&1 &
                            API_PID=$!
                            echo $API_PID > ../api.pid
                            echo "API 进程 PID: $API_PID"
                            
                            # 等待几秒让进程启动
                            sleep 3
                            
                            # 检查进程是否还在运行
                            if ! ps -p $API_PID > /dev/null 2>&1; then
                                echo "错误: API 进程已退出"
                                echo "API 日志内容:"
                                cat ../api.log || true
                                exit 1
                            fi
                            
                            # 等待 API 启动（使用兼容 /bin/sh 的循环）
                            echo "等待 API 服务启动..."
                            API_STARTED=false
                            MAX_RETRIES=60
                            RETRY_COUNT=0
                            
                            while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
                                # 检查进程是否还在运行
                                if ! ps -p $API_PID > /dev/null 2>&1; then
                                    echo "错误: API 进程已退出"
                                    echo "API 日志内容:"
                                    cat ../api.log || true
                                    exit 1
                                fi
                                
                                # 检查 API 是否响应
                                if curl -s -f http://localhost:5085/swagger/index.html > /dev/null 2>&1; then
                                    echo "API 服务已启动 (PID: $API_PID)"
                                    API_STARTED=true
                                    break
                                fi
                                
                                RETRY_COUNT=$((RETRY_COUNT + 1))
                                echo "等待 API 启动... ($RETRY_COUNT/$MAX_RETRIES)"
                                
                                # 每 5 次重试显示一次日志
                                if [ $((RETRY_COUNT % 5)) -eq 0 ]; then
                                    echo "当前 API 日志（最后 20 行）:"
                                    tail -20 ../api.log 2>/dev/null || echo "日志文件为空或不存在"
                                fi
                                
                                sleep 2
                            done
                            
                            if [ "$API_STARTED" = false ]; then
                                echo "错误: API 服务启动超时"
                                echo "进程状态:"
                                ps -p $API_PID > /dev/null 2>&1 && echo "进程仍在运行" || echo "进程已退出"
                                echo ""
                                echo "端口状态:"
                                lsof -i :5085 2>/dev/null || netstat -tuln 2>/dev/null | grep 5085 || ss -tuln 2>/dev/null | grep 5085 || echo "端口未监听"
                                echo ""
                                echo "完整 API 日志:"
                                cat ../api.log || true
                                echo ""
                                echo "尝试直接访问 API:"
                                curl -v http://localhost:5085/swagger/index.html 2>&1 | head -20 || true
                                exit 1
                            fi
                            
                            echo "=== 运行 E2E 测试 ==="
                            
                            # 运行测试
                            cd ../todoapp-backend-api-e2etest
                            # 使用 . 代替 source（兼容 /bin/sh）
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
                                    # 如果进程仍在运行，强制杀死
                                    if ps -p $PID > /dev/null 2>&1; then
                                        echo "强制停止 API 服务..."
                                        kill -9 $PID || true
                                    fi
                                fi
                                rm -f api.pid
                            fi
                            
                            # 清理 API 日志
                            rm -f api.log || true
                        '''
                        
                        // 清理测试数据库
                        dir('todoapp-backend-api-e2etest') {
                            sh '''
                                # 检测 Docker Compose 命令
                                if docker compose version &> /dev/null 2>&1; then
                                    DOCKER_COMPOSE_CMD="docker compose"
                                elif command -v docker-compose &> /dev/null 2>&1; then
                                    DOCKER_COMPOSE_CMD="docker-compose"
                                else
                                    DOCKER_COMPOSE_CMD="docker-compose"
                                fi
                                
                                ${DOCKER_COMPOSE_CMD} -f docker-compose.test.yml down -v || true
                            '''
                        }
                        
                        // 发布测试报告
                        dir('todoapp-backend-api-e2etest') {
                            // 检查 Allure 结果目录是否存在
                            def allureResultsDir = 'test-results/allure-results'
                            def allureResultsExist = fileExists(allureResultsDir)
                            
                            echo "检查 Allure 结果目录: ${allureResultsDir}"
                            echo "Allure 结果目录存在: ${allureResultsExist}"
                            
                            if (allureResultsExist) {
                                // 列出结果文件
                                sh """
                                    echo "Allure 结果文件列表:"
                                    find ${allureResultsDir} -type f | head -10 || true
                                    echo "结果文件数量:"
                                    find ${allureResultsDir} -type f | wc -l || echo "0"
                                """
                            }
                            
                            // 尝试发布 Allure 报告（如果插件已安装）
                            def allurePluginAvailable = false
                            try {
                                // 检查插件是否可用（通过尝试调用方法）
                                echo "尝试检测 Allure 插件..."
                                
                                // 方法 1: 直接调用（如果插件已加载）
                                allure([
                                    includeProperties: false,
                                    jdk: '',
                                    properties: [],
                                    reportBuildPolicy: 'ALWAYS',
                                    results: [[path: allureResultsDir]]
                                ])
                                allurePluginAvailable = true
                                echo "✅ Allure 报告已成功发布"
                            } catch (MissingMethodException e) {
                                echo "❌ Allure 插件方法不可用 (MissingMethodException)"
                                echo "错误信息: ${e.getMessage()}"
                                echo "可能原因:"
                                echo "  1. Allure Plugin 未安装"
                                echo "  2. Jenkins 未重启（安装插件后需要重启）"
                                echo "  3. 插件版本不兼容"
                                echo ""
                                echo "检查步骤:"
                                echo "  1. 访问: Manage Jenkins → Manage Plugins → Installed"
                                echo "  2. 搜索 'Allure' 确认插件已安装且启用"
                                echo "  3. 如果已安装，请重启 Jenkins: sudo systemctl restart jenkins"
                            } catch (NoSuchMethodError e) {
                                echo "❌ Allure 插件方法不可用 (NoSuchMethodError)"
                                echo "错误信息: ${e.getMessage()}"
                                echo "这通常意味着插件未正确加载，需要重启 Jenkins"
                            } catch (ru.yandex.qatools.allure.jenkins.exception.AllurePluginException e) {
                                echo "❌ Allure 报告发布失败: AllurePluginException"
                                echo "错误信息: ${e.getMessage()}"
                                if (e.getMessage().contains('allure commandline')) {
                                    echo ""
                                    echo "⚠️  问题: 未找到 Allure 命令行工具"
                                    echo "解决方案:"
                                    echo "  1. 在 Jenkins 管理界面配置 Allure 工具路径"
                                    echo "     Manage Jenkins → Global Tool Configuration → Allure Commandline"
                                    echo "  2. 或者在构建机上安装 Allure 命令行工具"
                                    echo "     参考: todoapp-backend-api-e2etest/JENKINS.md"
                                }
                            } catch (Exception e) {
                                echo "❌ Allure 报告发布失败"
                                echo "异常类型: ${e.getClass().getName()}"
                                echo "错误信息: ${e.getMessage()}"
                            }
                            
                            if (!allurePluginAvailable && allureResultsExist) {
                                echo ""
                                echo "⚠️  警告: Allure 结果已生成，但无法发布报告"
                                echo "结果文件已归档，可以在构建产物中下载查看"
                            }
                            
                            // 归档测试结果（包括 HTML 报告和 Allure 结果）
                            archiveArtifacts artifacts: 'test-results/**/*',
                                             allowEmptyArchive: true
                            
                            // 发布 HTML 报告（pytest-html 生成，不需要额外插件）
                            if (fileExists('test-results/report.html')) {
                                publishHTML([
                                    reportName: 'E2E Test Report',
                                    reportDir: 'test-results',
                                    reportFiles: 'report.html',
                                    keepAll: true,
                                    alwaysLinkToLastBuild: true
                                ])
                                echo "HTML 测试报告已发布"
                            }
                        }
                    }
                }
            }
        }

        // 阶段 7: SonarQube 代码扫描
        stage('SonarQube Analysis') {
            steps {
                script {
                    echo "=========================================="
                    echo "开始 SonarQube 代码扫描..."
                    echo "=========================================="
                }
                
                dir("${BUILD_DIR}") {
                    withSonarQubeEnv('sonarqube-server') {  // 使用 Jenkins 中配置的 SonarQube 服务器名称
                        sh """
                            # 使用 dotnet sonarscanner（.NET 8.0 推荐方式）
                            # 确保 PATH 包含 dotnet tools 目录
                            export PATH="\$HOME/.dotnet/tools:\$PATH"
                            
                            # 显示当前用户和 PATH
                            echo "当前用户: \$(whoami)"
                            echo "HOME 目录: \$HOME"
                            echo "PATH: \$PATH"
                            
                            # 检查工具是否已安装
                            # 注意：dotnet sonarscanner /version 会输出版本信息，但可能因为提示需要 begin/end 而返回非零退出码
                            # 所以通过检查输出中是否包含版本号来判断，而不是依赖退出码
                            echo "检查 dotnet-sonarscanner 是否已安装..."
                            VERSION_OUTPUT=\$(dotnet sonarscanner /version 2>&1 || true)
                            
                            if echo "\$VERSION_OUTPUT" | grep -q "SonarScanner for .NET"; then
                                echo "✅ dotnet-sonarscanner 已安装"
                                echo "\$VERSION_OUTPUT" | head -3
                            else
                                echo "dotnet-sonarscanner 未安装，开始安装..."
                                dotnet tool install --global dotnet-sonarscanner
                                export PATH="\$HOME/.dotnet/tools:\$PATH"
                                
                                # 验证安装是否成功
                                VERSION_OUTPUT=\$(dotnet sonarscanner /version 2>&1 || true)
                                if echo "\$VERSION_OUTPUT" | grep -q "SonarScanner for .NET"; then
                                    echo "✅ dotnet-sonarscanner 安装成功"
                                    echo "\$VERSION_OUTPUT" | head -3
                                else
                                    echo "❌ 错误: dotnet-sonarscanner 安装失败"
                                    echo "输出信息: \$VERSION_OUTPUT"
                                    echo "请检查 .NET SDK 是否正确安装"
                                    exit 1
                                fi
                            fi
                            
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
                            
                            # 构建 SonarQube 参数
                            SONAR_PARAMS="/k:\"${PROJECT_NAME}\" /n:\"${PROJECT_NAME}\" /v:\"${env.BUILD_NUMBER}\" /d:sonar.projectBaseDir=\"${BUILD_DIR}\" /d:sonar.coverage.exclusions=\"**/Migrations/**,**/*Tests/**\" /d:sonar.tests=\"TodoApp-backend.Tests\""
                            
                            # 如果找到覆盖率文件，添加覆盖率路径参数
                            if [ -n "\$COVERAGE_FILE" ]; then
                                if echo "\$COVERAGE_FILE" | grep -q "opencover"; then
                                    SONAR_PARAMS="\$SONAR_PARAMS /d:sonar.cs.opencover.reportsPaths=\"\$COVERAGE_FILE\""
                                else
                                    # 如果只有 Cobertura 格式，尝试使用（可能不被支持）
                                    SONAR_PARAMS="\$SONAR_PARAMS /d:sonar.cs.opencover.reportsPaths=\"\$COVERAGE_FILE\""
                                    echo "警告: 使用 Cobertura 格式，SonarQube C# 插件可能无法正确解析"
                                fi
                            fi
                            
                            dotnet sonarscanner begin \$SONAR_PARAMS
                            
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
                            elif [ -f "${BUILD_DIR}/.sonar/report-task.txt" ]; then
                                REPORT_TASK_FILE="${BUILD_DIR}/.sonar/report-task.txt"
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

        // 阶段 8: 发布/打包
        stage('Publish') {
            steps {
                script {
                    echo "=========================================="
                    echo "发布项目（打包）..."
                    echo "=========================================="
                }
                
                dir("${BUILD_DIR}") {
                    // 发布为框架依赖部署（需要目标服务器安装 .NET 8.0 Runtime）
                    sh """
                        dotnet publish \\
                            --configuration Release \\
                            --framework ${TARGET_FRAMEWORK} \\
                            --output ${PUBLISH_DIR}/${PROJECT_NAME} \\
                            --no-build \\
                            --verbosity normal
                    """
                }
                
                script {
                    echo "发布完成，输出目录: ${PUBLISH_DIR}/${PROJECT_NAME}"
                }
            }
        }
        
        // 阶段 9: 创建压缩包
        stage('Package') {
            steps {
                script {
                    echo "=========================================="
                    echo "创建发布包..."
                    echo "=========================================="
                    
                    def timestamp = sh(
                        script: 'date +%Y%m%d_%H%M%S',
                        returnStdout: true
                    ).trim()
                    
                    def buildNumber = env.BUILD_NUMBER ?: '0'
                    def packageName = "${PROJECT_NAME}_${timestamp}_${buildNumber}.tar.gz"
                    
                    sh """
                        cd ${PUBLISH_DIR}
                        tar -czf ${ARTIFACTS_DIR}/${packageName} ${PROJECT_NAME}
                        
                        # 计算文件大小和 MD5
                        cd ${ARTIFACTS_DIR}
                        ls -lh ${packageName}
                        md5sum ${packageName} > ${packageName}.md5 || md5 ${packageName} > ${packageName}.md5 || echo "MD5 calculation failed"
                    """
                    
                    env.PACKAGE_NAME = packageName
                    env.PACKAGE_PATH = "${ARTIFACTS_DIR}/${packageName}"
                    
                    echo "打包完成: ${packageName}"
                }
            }
        }
        
        // 阶段 10: 归档构建产物
        stage('Archive Artifacts') {
            steps {
                script {
                    echo "=========================================="
                    echo "归档构建产物..."
                    echo "=========================================="
                }
                
                // 归档发布包
                archiveArtifacts artifacts: "artifacts/**/*.tar.gz", allowEmptyArchive: false
                archiveArtifacts artifacts: "artifacts/**/*.md5", allowEmptyArchive: true
                
                // 归档测试结果和代码覆盖率报告
                archiveArtifacts artifacts: "test-results/**/*.trx", allowEmptyArchive: true
                archiveArtifacts artifacts: "test-results/**/coverage.cobertura.xml", allowEmptyArchive: true
                
                // 可选：归档发布目录
                // archiveArtifacts artifacts: "publish/**/*", allowEmptyArchive: false
            }
        }

        // 阶段 11: 上传到 Nexus 仓库
        stage('Deploy to Nexus') {
            steps {
                script {
                    echo "=========================================="
                    echo "上传构建产物到 Nexus 仓库..."
                    echo "=========================================="
                    
                    // 检查构建产物是否存在
                    def packageName = env.PACKAGE_NAME
                    def packagePath = env.PACKAGE_PATH
                    
                    if (!packageName || !packagePath) {
                        error("构建产物信息不存在，无法上传到 Nexus")
                    }
                    
                    if (!fileExists(packagePath)) {
                        error("构建产物文件不存在: ${packagePath}")
                    }
                    
                    echo "准备上传文件: ${packageName}"
                    echo "文件路径: ${packagePath}"
                    
                    // Nexus 仓库配置
                    def nexusUrl = 'https://nexus.hohistar.com.cn/repository/todoapp-backend-api-snapshots'
                    def nexusCredentialsId = 'Nexus-Repository-User-Pwd'
                    
                    // 使用凭证上传文件到 Nexus
                    withCredentials([usernamePassword(credentialsId: nexusCredentialsId, usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD')]) {
                        // 上传构建产物
                        def uploadExitCode = sh(
                            script: """
                                echo "开始上传到 Nexus..."
                                echo "仓库地址: ${nexusUrl}"
                                echo "文件名: ${packageName}"
                                
                                # 使用 curl 上传文件到 Nexus raw repository
                                curl -f -u "\${NEXUS_USER}:\${NEXUS_PASSWORD}" \\
                                    --upload-file "${packagePath}" \\
                                    "${nexusUrl}/${packageName}" || {
                                    UPLOAD_EXIT_CODE=\$?
                                    echo "上传失败，退出码: \$UPLOAD_EXIT_CODE"
                                    exit \$UPLOAD_EXIT_CODE
                                }
                                
                                echo "✅ 文件上传成功"
                                echo "访问地址: ${nexusUrl}/${packageName}"
                            """,
                            returnStatus: true
                        )
                        
                        if (uploadExitCode != 0) {
                            error("上传到 Nexus 失败，退出码: ${uploadExitCode}")
                        }
                        
                        // 上传 MD5 文件（如果存在）
                        def md5Path = "${packagePath}.md5"
                        if (fileExists(md5Path)) {
                            def md5FileName = "${packageName}.md5"
                            echo "上传 MD5 文件: ${md5FileName}"
                            
                            def md5UploadExitCode = sh(
                                script: """
                                    curl -f -u "\${NEXUS_USER}:\${NEXUS_PASSWORD}" \\
                                        --upload-file "${md5Path}" \\
                                        "${nexusUrl}/${md5FileName}" || {
                                        MD5_UPLOAD_EXIT_CODE=\$?
                                        echo "MD5 文件上传失败，退出码: \$MD5_UPLOAD_EXIT_CODE"
                                        exit \$MD5_UPLOAD_EXIT_CODE
                                    }
                                    echo "✅ MD5 文件上传成功"
                                """,
                                returnStatus: true
                            )
                            
                            if (md5UploadExitCode != 0) {
                                echo "⚠️  警告: MD5 文件上传失败，但主文件已上传成功"
                            }
                        }
                        
                        echo "=========================================="
                        echo "✅ 构建产物已成功上传到 Nexus 仓库"
                        echo "文件: ${packageName}"
                        echo "访问地址: ${nexusUrl}/${packageName}"
                        echo "=========================================="
                    }
                }
            }
        }
        */
    }

    post {
        // 构建成功后的操作
        success {
            script {
                echo "=========================================="
                echo "构建成功！"
                echo "构建号: ${env.BUILD_NUMBER}"
                echo "包名: ${env.PACKAGE_NAME ?: 'N/A'}"
                
                // 显示测试结果摘要
                def testResultFiles = sh(
                    script: "find ${WORKSPACE}/test-results -name '*.trx' -type f 2>/dev/null || true",
                    returnStdout: true
                ).trim()
                
                if (testResultFiles) {
                    echo "测试结果文件已生成并发布"
                    echo "可在 Jenkins 的 'Test Result' 页面查看详细测试报告"
                }
                
                echo "=========================================="
            }
        }
        
        // 构建失败后的操作
        failure {
            script {
                echo "=========================================="
                echo "构建失败！"
                echo "请检查构建日志以获取详细信息"
                echo "=========================================="
            }
        }
        
        // 无论成功或失败都执行
        always {
            script {
                // 清理工作空间（可选，根据需要决定是否保留）
                // cleanWs()
                
                echo "构建流程完成"
            }
        }
    }
}

