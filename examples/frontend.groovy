pipeline {

     agent {
        label 'build-node'
    }

    // 工具配置
    tools {
        nodejs 'node-20'
    }

    // 环境变量配置
    environment {
        // 项目配置
        PROJECT_NAME = 'TodoApp-frontend'
        PROJECT_PATH = 'todoapp-frontend-vue2'

        // 构建输出目录
        BUILD_DIR = "${WORKSPACE}/${PROJECT_PATH}"
        DIST_DIR = "${WORKSPACE}/dist"
        ARTIFACTS_DIR = "${WORKSPACE}/artifacts"

        // GitLab 配置
        GITLAB_URL = 'https://ci-pilot.hohistar.com.cn/gitlab'
        GITLAB_CREDENTIALS_ID = 'ci-pilot-checkout-id'
        GITLAB_REPO = '/root/todoapp-frontend-vue2'
        GIT_BRANCH = 'main'
    }


    stages {
        // 阶段 1: 代码检出
        stage('Checkout') {
            steps {
                script {
                    echo "=========================================="
                    echo "开始从 GitLab 拉取代码..."
                    echo "仓库: ${GITLAB_REPO}"
                    echo "分支: ${GIT_BRANCH}"
                    echo "=========================================="
                }

                git(
                    url: "${GITLAB_URL}/${GITLAB_REPO}.git",
                    branch: "${GIT_BRANCH}",
                    credentialsId: "${GITLAB_CREDENTIALS_ID}",
                    changelog: true,
                    poll: true
                )

                script {
                    sh """
                        echo "代码检出完成"
                        cd ${PROJECT_PATH}
                        git log -1 --oneline
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

                    // 验证 Node.js 工具配置
                    def nodeVersion = sh(
                        script: 'node --version',
                        returnStdout: true
                    ).trim()

                    def npmVersion = sh(
                        script: 'npm --version',
                        returnStdout: true
                    ).trim()

                    echo "Node.js 版本: ${nodeVersion}"
                    echo "npm 版本: ${npmVersion}"

                    // 清理之前的构建产物
                    sh """
                        rm -rf ${DIST_DIR}
                        rm -rf ${ARTIFACTS_DIR}
                        mkdir -p ${DIST_DIR}
                        mkdir -p ${ARTIFACTS_DIR}
                    """
                }
            }
        }

        // 阶段 3: 安装依赖
        stage('Install Dependencies') {
            steps {
                script {
                    echo "=========================================="
                    echo "安装 npm 依赖..."
                    echo "=========================================="
                }

                dir("${BUILD_DIR}") {
                    sh """
                        npm install --verbose
                    """
                }
            }
        }


        // 阶段 5: 运行测试
        stage('Test') {
            steps {
                dir("${BUILD_DIR}") {
                    script {
                        try {
                            // 检查是否有测试脚本
                            if (fileExists('package.json')) {
                                echo "使用 Node.js 检查 package.json 中的测试脚本..."
                                
                                // 使用 Node.js 检查是否有 test 脚本
                                def hasTestScript = sh(
                                    script: '''
                                        node -e "
                                            const fs = require('fs');
                                            const pkg = JSON.parse(fs.readFileSync('package.json', 'utf8'));
                                            if (pkg.scripts && pkg.scripts.test) {
                                                console.log('HAS_TEST:' + pkg.scripts.test);
                                            } else if (pkg.scripts && pkg.scripts['test:unit']) {
                                                console.log('HAS_TEST_UNIT:' + pkg.scripts['test:unit']);
                                            } else {
                                                console.log('NO_TEST');
                                            }
                                        "
                                    ''',
                                    returnStdout: true
                                ).trim()
                                
                                echo "检查结果: ${hasTestScript}"
                                
                                if (hasTestScript.startsWith('HAS_TEST:') || hasTestScript.startsWith('HAS_TEST_UNIT:')) {
                                    echo "=== 运行单元测试 ==="

                                    // 创建测试结果目录和覆盖率目录
                                    sh """
                                        mkdir -p ${WORKSPACE}/test-results
                                        mkdir -p coverage
                                    """

                                    // 检查并安装 jest-junit（如果未安装）
                                    echo "检查 jest-junit 是否已安装..."
                                    def jestJunitInstalled = sh(
                                        script: '''
                                            if npm list jest-junit > /dev/null 2>&1; then
                                                echo "INSTALLED"
                                            else
                                                echo "NOT_INSTALLED"
                                            fi
                                        ''',
                                        returnStdout: true
                                    ).trim()
                                    
                                    if (jestJunitInstalled == "NOT_INSTALLED") {
                                        echo "安装 jest-junit..."
                                        sh """
                                            npm install --save-dev jest-junit || echo "安装 jest-junit 失败，将尝试其他方式生成报告"
                                        """
                                    }
                                    
                                    // 运行测试并生成 JUnit 报告和覆盖率报告
                                    // 使用环境变量和命令行参数配置 jest-junit 报告器
                                    // 同时生成覆盖率报告（lcov 格式用于 SonarQube）
                                    echo "运行测试并生成覆盖率报告..."
                                    def testExitCode = sh(
                                        script: """
                                            export JEST_JUNIT_OUTPUT_DIR="${WORKSPACE}/test-results"
                                            export JEST_JUNIT_OUTPUT_NAME="junit.xml"
                                            # 如果 jest-junit 已安装，使用它作为 reporter
                                            if npm list jest-junit > /dev/null 2>&1; then
                                                echo "使用 jest-junit 生成 JUnit 报告，同时生成覆盖率报告..."
                                                npx jest --reporters=default --reporters=jest-junit \\
                                                    --coverage \\
                                                    --coverageReporters=text \\
                                                    --coverageReporters=text-summary \\
                                                    --coverageReporters=lcov \\
                                                    --coverageDirectory=coverage || {
                                                    TEST_EXIT_CODE=\$?
                                                    echo "测试执行完成，退出码: \$TEST_EXIT_CODE"
                                                    exit \$TEST_EXIT_CODE
                                                }
                                            else
                                                echo "jest-junit 未安装，使用默认方式运行测试并生成覆盖率..."
                                                npx jest --coverage \\
                                                    --coverageReporters=text \\
                                                    --coverageReporters=text-summary \\
                                                    --coverageReporters=lcov \\
                                                    --coverageDirectory=coverage || {
                                                    TEST_EXIT_CODE=\$?
                                                    echo "测试执行完成，退出码: \$TEST_EXIT_CODE"
                                                    exit \$TEST_EXIT_CODE
                                                }
                                            fi
                                        """,
                                        returnStatus: true
                                    )
                                    
                                    // 检查测试报告是否生成
                                    echo "检查测试报告是否生成..."
                                    def reportExists = fileExists("${WORKSPACE}/test-results/junit.xml")
                                    
                                    if (reportExists) {
                                        echo "JUnit 报告已生成: ${WORKSPACE}/test-results/junit.xml"
                                    } else {
                                        echo "警告: 未找到 JUnit 报告文件"
                                        sh """
                                            echo "检查 test-results 目录内容:"
                                            ls -la ${WORKSPACE}/test-results/ || echo "test-results 目录为空"
                                        """
                                    }
                                    
                                    // 检查覆盖率报告是否生成
                                    echo "检查覆盖率报告是否生成..."
                                    def coverageReportExists = fileExists("coverage/lcov.info")
                                    
                                    if (coverageReportExists) {
                                        echo "✅ 覆盖率报告已生成: coverage/lcov.info"
                                        // 显示覆盖率摘要
                                        sh """
                                            echo "覆盖率报告摘要:"
                                            if [ -f coverage/lcov.info ]; then
                                                echo "覆盖率文件大小:"
                                                ls -lh coverage/lcov.info
                                                echo ""
                                                echo "覆盖率文件前几行:"
                                                head -20 coverage/lcov.info || true
                                            fi
                                        """
                                        
                                        // 将 lcov 格式转换为 Cobertura 格式（用于 Jenkins Coverage 插件）
                                        echo "将 lcov 格式转换为 Cobertura 格式..."
                                        def coberturaConverted = false
                                        try {
                                            // 检查并安装 lcov-to-cobertura-xml 转换工具
                                            def converterInstalled = sh(
                                                script: '''
                                                    if npm list lcov-to-cobertura-xml > /dev/null 2>&1; then
                                                        echo "INSTALLED"
                                                    else
                                                        echo "NOT_INSTALLED"
                                                    fi
                                                ''',
                                                returnStdout: true
                                            ).trim()
                                            
                                            if (converterInstalled == "NOT_INSTALLED") {
                                                echo "安装 lcov-to-cobertura-xml 转换工具..."
                                                sh """
                                                    npm install --save-dev lcov-to-cobertura-xml || echo "安装失败，将尝试其他方式"
                                                """
                                            }
                                            
                                            // 执行转换
                                            def convertExitCode = sh(
                                                script: """
                                                    if npm list lcov-to-cobertura-xml > /dev/null 2>&1; then
                                                        echo "使用 lcov-to-cobertura-xml 转换..."
                                                        npx lcov-to-cobertura-xml coverage/lcov.info -o coverage/coverage.cobertura.xml
                                                        CONVERT_EXIT_CODE=\$?
                                                        if [ \$CONVERT_EXIT_CODE -eq 0 ] && [ -f coverage/coverage.cobertura.xml ]; then
                                                            echo "✅ Cobertura 格式转换成功"
                                                            ls -lh coverage/coverage.cobertura.xml
                                                            echo "CONVERTED"
                                                        else
                                                            echo "转换失败，退出码: \$CONVERT_EXIT_CODE"
                                                            echo "NOT_CONVERTED"
                                                        fi
                                                    else
                                                        echo "转换工具未安装，跳过转换"
                                                        echo "NOT_CONVERTED"
                                                    fi
                                                """,
                                                returnStdout: true
                                            ).trim()
                                            
                                            if (convertExitCode.contains("CONVERTED")) {
                                                coberturaConverted = true
                                                echo "✅ Cobertura 格式覆盖率报告已生成: coverage/coverage.cobertura.xml"
                                            } else {
                                                echo "⚠️  警告: lcov 转 Cobertura 失败，将仅使用 lcov 格式（用于 SonarQube）"
                                            }
                                        } catch (Exception e) {
                                            echo "⚠️  警告: 转换过程中发生错误: ${e.getMessage()}"
                                            echo "将仅使用 lcov 格式（用于 SonarQube）"
                                        }
                                    } else {
                                        echo "⚠️  警告: 未找到覆盖率报告文件 coverage/lcov.info"
                                        sh """
                                            echo "检查 coverage 目录内容:"
                                            ls -la coverage/ 2>/dev/null || echo "coverage 目录为空或不存在"
                                        """
                                    }
                                    
                                    // 如果测试失败，标记构建为不稳定（但继续执行）
                                    if (testExitCode != 0) {
                                        echo "=========================================="
                                        echo "测试执行失败，退出码: ${testExitCode}"
                                        echo "构建将标记为不稳定，但继续执行后续步骤"
                                        echo "=========================================="
                                        unstable("测试失败: 退出码 ${testExitCode}")
                                    } else {
                                        echo "所有测试通过"
                                        if (coverageReportExists) {
                                            echo "✅ 测试和覆盖率报告生成完成"
                                        }
                                    }
                                } else {
                                    echo "未找到测试脚本，跳过测试阶段"
                                    
                                    // 列出所有可用的 scripts
                                    def allScripts = sh(
                                        script: '''
                                            node -e "
                                                const fs = require('fs');
                                                const pkg = JSON.parse(fs.readFileSync('package.json', 'utf8'));
                                                if (pkg.scripts) {
                                                    console.log(Object.keys(pkg.scripts).join(', '));
                                                } else {
                                                    console.log('N/A');
                                                }
                                            "
                                        ''',
                                        returnStdout: true
                                    ).trim()
                                    echo "可用的 scripts: ${allScripts}"
                                }
                            } else {
                                echo "package.json 不存在，跳过测试阶段"
                            }
                        } catch (Exception e) {
                            echo "=========================================="
                            echo "测试阶段读取 package.json 时发生错误:"
                            echo "错误类型: ${e.getClass().getName()}"
                            echo "错误消息: ${e.getMessage()}"
                            echo "=========================================="
                            error("测试阶段失败: ${e.getMessage()}")
                        }
                    }
                }
            }

            post {
                always {
                    // 发布 JUnit 测试报告
                    junit testResults: 'test-results/*.xml',
                          allowEmptyResults: true,
                          keepLongStdio: true

                    // 归档测试结果
                    archiveArtifacts artifacts: 'test-results/**/*.xml',
                                     allowEmptyArchive: true
                    
                    // 归档覆盖率报告并发布覆盖率报告
                    dir("${BUILD_DIR}") {
                        archiveArtifacts artifacts: 'coverage/**/*',
                                         allowEmptyArchive: true
                        
                        // 发布代码覆盖率报告（需安装 Coverage 插件）
                        // 注意：Jenkins Coverage 插件支持 COBERTURA 格式，不支持 LCOV
                        // 文档：https://plugins.jenkins.io/coverage/
                        script {
                            // 检查 Cobertura 格式文件是否存在
                            def coberturaExists = fileExists('coverage/coverage.cobertura.xml')
                            
                            if (coberturaExists) {
                                try {
                                    recordCoverage(
                                        tools: [[parser: 'COBERTURA', pattern: 'coverage/coverage.cobertura.xml']],
                                        sourceCodeRetention: 'LAST_BUILD'  // 存储最后一次构建的源代码
                                    )
                                    echo "✅ 代码覆盖率报告已发布（Cobertura 格式）"
                                } catch (MissingMethodException e) {
                                    echo "⚠️  警告: Coverage 插件未安装"
                                    echo "请安装 'Coverage' 插件以查看覆盖率趋势图"
                                    echo "Manage Jenkins → Manage Plugins → Available → 搜索 'Coverage'"
                                    echo "插件页面: https://plugins.jenkins.io/coverage/"
                                } catch (Exception e) {
                                    echo "⚠️  警告: 覆盖率报告发布失败"
                                    echo "错误信息: ${e.getMessage()}"
                                }
                            } else {
                                echo "⚠️  警告: 未找到 Cobertura 格式覆盖率文件"
                                echo "文件路径: coverage/coverage.cobertura.xml"
                                echo "可能原因: lcov 转 Cobertura 转换失败"
                                echo "覆盖率数据仍可在 SonarQube 中查看（使用 lcov 格式）"
                            }
                        }
                    }
                }
            }
        }

        // 阶段 5.5: UI 端到端测试
        stage('UI E2E Tests') {
            steps {
                script {
                    echo "=========================================="
                    echo "开始 UI 端到端测试..."
                    echo "=========================================="
                }

                script {
                    // 1. 检出后端 API 项目（端到端测试需要）
                    echo "----------------------------------------"
                    echo "步骤 1: 检出后端 API 项目..."
                    echo "----------------------------------------"
                    
                    def backendPath = 'todoapp-backend-api'
                    def backendRepo = '/root/todoapp-backend-api'
                    
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/main"]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [
                            [$class: 'CleanBeforeCheckout'],
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: backendPath]
                        ],
                        submoduleCfg: [],
                        userRemoteConfigs: [[
                            credentialsId: "${GITLAB_CREDENTIALS_ID}",
                            url: "${GITLAB_URL}${backendRepo}.git"
                        ]]
                    ])
                    
                    sh """
                        echo "后端 API 项目检出完成"
                        cd ${backendPath}
                        git log -1 --oneline
                    """
                    
                    // 2. 检出 UI 端到端测试项目
                    echo "----------------------------------------"
                    echo "步骤 2: 检出 UI 端到端测试项目..."
                    echo "----------------------------------------"
                    
                    def e2eTestPath = 'todoapp-frontend-ui-e2etest'
                    def e2eTestRepo = '/root/todoapp-frontend-ui-e2etest'
                    
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/main"]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [
                            [$class: 'CleanBeforeCheckout'],
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: e2eTestPath]
                        ],
                        submoduleCfg: [],
                        userRemoteConfigs: [[
                            credentialsId: "${GITLAB_CREDENTIALS_ID}",
                            url: "${GITLAB_URL}${e2eTestRepo}.git"
                        ]]
                    ])

                    echo "UI 端到端测试项目检出完成"

                    // 设置 Python 环境
                    dir(e2eTestPath) {
                        // 检查 Python 是否可用
                        def pythonVersion = sh(
                            script: 'python3 --version || echo "NOT_INSTALLED"',
                            returnStdout: true
                        ).trim()
                        
                        if (pythonVersion.contains('NOT_INSTALLED')) {
                            error("Python 3 未安装，请确保 Jenkins 节点已安装 Python 3")
                        }
                        
                        echo "Python 版本: ${pythonVersion}"
                        
                        // 检查虚拟环境是否存在，如果不存在则创建
                        def venvExists = fileExists('venv')
                        // 使用国内镜像源加速下载
                        def pipIndexUrl = 'https://pypi.tuna.tsinghua.edu.cn/simple'
                        
                        if (!venvExists) {
                            echo "创建 Python 虚拟环境..."
                            sh """
                                python3 -m venv venv
                                . venv/bin/activate
                                pip install --upgrade pip -i ${pipIndexUrl}
                                pip install -r requirements.txt -i ${pipIndexUrl}
                            """
                        } else {
                            echo "虚拟环境已存在，激活并更新依赖..."
                            sh """
                                . venv/bin/activate
                                pip install --upgrade pip -i ${pipIndexUrl}
                                pip install -r requirements.txt -i ${pipIndexUrl}
                            """
                        }
                        
                        // 检查 Docker 是否可用
                        def dockerVersion = sh(
                            script: 'docker --version || echo "NOT_INSTALLED"',
                            returnStdout: true
                        ).trim()
                        
                        if (dockerVersion.contains('NOT_INSTALLED')) {
                            error("Docker 未安装，请确保 Jenkins 节点已安装 Docker")
                        }
                        
                        echo "Docker 版本: ${dockerVersion}"
                        
                        // 检查 .NET SDK 是否可用（用于启动后端 API）
                        def dotnetVersion = sh(
                            script: 'dotnet --version || echo "NOT_INSTALLED"',
                            returnStdout: true
                        ).trim()
                        
                        if (dotnetVersion.contains('NOT_INSTALLED')) {
                            error(".NET SDK 未安装，请确保 Jenkins 节点已安装 .NET SDK")
                        }
                        
                        echo ".NET SDK 版本: ${dotnetVersion}"
                        
                        // 设置环境变量（无头模式，适合 CI 环境）
                        // 注意：conftest.py 会自动启动后端 API 和前端服务
                        // 前端项目路径：${WORKSPACE}/${PROJECT_PATH}
                        // 后端项目路径：${WORKSPACE}/todoapp-backend-api（需要先检出）
                        sh """
                            export HEADLESS=true
                            export API_BASE_URL=http://localhost:5085
                            export FRONTEND_BASE_URL=http://localhost:8080
                            
                            echo "环境变量设置:"
                            echo "  HEADLESS=\$HEADLESS"
                            echo "  API_BASE_URL=\$API_BASE_URL"
                            echo "  FRONTEND_BASE_URL=\$FRONTEND_BASE_URL"
                            echo ""
                            echo "注意: conftest.py 会自动启动以下服务:"
                            echo "  1. Docker 数据库 (PostgreSQL)"
                            echo "  2. 后端 API (dotnet run)"
                            echo "  3. 前端服务 (npm run serve)"
                            echo ""
                            echo "前端项目路径: ${WORKSPACE}/${PROJECT_PATH}"
                            echo "后端项目路径: ${WORKSPACE}/todoapp-backend-api"
                        """
                        
                        // 检查前端项目是否存在（conftest.py 需要它）
                        def frontendPath = "${WORKSPACE}/${PROJECT_PATH}"
                        if (!fileExists(frontendPath)) {
                            error("前端项目不存在: ${frontendPath}，请确保前端项目已检出")
                        }
                        echo "✅ 前端项目已就绪: ${frontendPath}"
                        
                        // 检查后端项目是否存在（conftest.py 需要它）
                        def backendFullPath = "${WORKSPACE}/todoapp-backend-api"
                        if (!fileExists(backendFullPath)) {
                            error("后端项目不存在: ${backendFullPath}，请确保后端项目已检出")
                        }
                        echo "✅ 后端项目已就绪: ${backendFullPath}"
                        
                        // 运行测试
                        echo "开始运行 UI 端到端测试..."
                        echo "测试将自动启动数据库、后端 API 和前端服务..."
                        
                        // 确保测试结果目录存在
                        sh """
                            mkdir -p test-results/allure-results
                            echo "测试结果目录已创建: test-results/allure-results"
                        """
                        
                        // 运行测试并捕获退出码（使用 returnStatus 允许测试失败时继续执行）
                        def testExitCode = sh(
                            script: """
                                set +e  # 允许命令失败而不中断脚本
                                . venv/bin/activate
                                
                                # 设置环境变量（确保 pytest 和 conftest.py 可以访问）
                                export HEADLESS=true
                                export API_BASE_URL=http://localhost:5085
                                export FRONTEND_BASE_URL=http://localhost:8080
                                
                                echo "开始执行 pytest..."
                                echo "环境变量:"
                                echo "  HEADLESS=\$HEADLESS"
                                echo "  API_BASE_URL=\$API_BASE_URL"
                                echo "  FRONTEND_BASE_URL=\$FRONTEND_BASE_URL"
                                
                                pytest --alluredir=test-results/allure-results -v
                                TEST_EXIT_CODE=\$?
                                echo "pytest 执行完成，退出码: \$TEST_EXIT_CODE"
                                exit \$TEST_EXIT_CODE
                            """,
                            returnStatus: true
                        )
                        
                        // 检查测试结果是否生成
                        echo "检查测试结果..."
                        sh """
                            echo "当前工作目录:"
                            pwd
                            echo ""
                            echo "测试结果目录内容:"
                            ls -la test-results/allure-results/ 2>&1 || echo "测试结果目录为空或不存在"
                            echo ""
                            echo "测试结果文件数量:"
                            find test-results/allure-results -type f 2>/dev/null | wc -l || echo "0"
                            echo ""
                            echo "测试结果文件列表:"
                            find test-results/allure-results -type f 2>/dev/null | head -10 || echo "无结果文件"
                        """
                        
                        // 如果测试失败，标记为不稳定但继续执行（以便生成报告）
                        if (testExitCode != 0) {
                            echo "=========================================="
                            echo "⚠️  UI 端到端测试执行失败，退出码: ${testExitCode}"
                            echo "构建将标记为不稳定，但继续生成测试报告"
                            echo "=========================================="
                            // 注意：使用 unstable 而不是 error，这样后续阶段可以继续执行
                            currentBuild.result = 'UNSTABLE'
                        } else {
                            echo "✅ 所有 UI 端到端测试通过"
                        }
                    }
                }
            }

            post {
                always {
                    script {
                        def e2eTestPath = 'todoapp-frontend-ui-e2etest'
                        
                        dir(e2eTestPath) {
                            // 清理测试环境（停止服务、数据库等）
                            sh '''
                                # 清理可能运行的进程
                                pkill -f "dotnet run" || true
                                pkill -f "npm run serve" || true
                                
                                # 清理测试数据库
                                if docker compose version &> /dev/null 2>&1; then
                                    DOCKER_COMPOSE_CMD="docker compose"
                                elif command -v docker-compose &> /dev/null 2>&1; then
                                    DOCKER_COMPOSE_CMD="docker-compose"
                                else
                                    DOCKER_COMPOSE_CMD="docker-compose"
                                fi
                                
                                ${DOCKER_COMPOSE_CMD} -f docker-compose.test.yml down -v || true
                            '''
                            
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
                                    reportName: 'UI E2E Test Report',
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

        // 阶段 6: SonarQube 代码质量分析
        stage('SonarQube Analysis') {
            steps {
                script {
                    echo "=========================================="
                    echo "开始 SonarQube 代码质量分析..."
                    echo "=========================================="
                }
                
                dir("${BUILD_DIR}") {
                    withSonarQubeEnv('sonarqube-server') {
                        script {
                            // 检查并安装 sonar-scanner（如果未安装）
                            echo "检查 sonar-scanner 是否可用..."
                            def scannerInstalled = sh(
                                script: '''
                                    if command -v sonar-scanner > /dev/null 2>&1; then
                                        echo "INSTALLED"
                                    else
                                        echo "NOT_INSTALLED"
                                    fi
                                ''',
                                returnStdout: true
                            ).trim()
                            
                            if (scannerInstalled == "NOT_INSTALLED") {
                                echo "检查是否可以通过 npm 安装 sonarqube-scanner..."
                                def npmScannerInstalled = sh(
                                    script: '''
                                        if npm list -g sonarqube-scanner > /dev/null 2>&1; then
                                            echo "INSTALLED"
                                        else
                                            echo "NOT_INSTALLED"
                                        fi
                                    ''',
                                    returnStdout: true
                                ).trim()
                                
                                if (npmScannerInstalled == "NOT_INSTALLED") {
                                    echo "安装 sonarqube-scanner (npm 全局包)..."
                                    sh """
                                        npm install -g sonarqube-scanner || echo "安装失败，将尝试使用本地安装"
                                    """
                                }
                            }
                            
                            // 创建或更新 sonar-project.properties
                            echo "配置 SonarQube 项目属性..."
                            def buildNumber = env.BUILD_NUMBER ?: '0'
                            
                            // 检查测试报告是否存在且有效
                            def testReportExists = false
                            def testReportPath = "${WORKSPACE}/test-results/junit.xml"
                            if (fileExists(testReportPath)) {
                                echo "检查测试报告格式..."
                                def testReportValid = sh(
                                    script: """
                                        if [ -s "${testReportPath}" ]; then
                                            # 检查 XML 格式是否有效（至少包含 testsuites 或 testsuite 标签）
                                            if grep -q '<testsuites\\|testsuite' "${testReportPath}" 2>/dev/null; then
                                                echo "VALID"
                                            else
                                                echo "INVALID_FORMAT"
                                            fi
                                        else
                                            echo "EMPTY"
                                        fi
                                    """,
                                    returnStdout: true
                                ).trim()
                                
                                if (testReportValid == "VALID") {
                                    testReportExists = true
                                    echo "测试报告有效，将包含在 SonarQube 分析中"
                                } else {
                                    echo "测试报告无效或格式不正确，将跳过: ${testReportValid}"
                                }
                            } else {
                                echo "测试报告不存在，将跳过"
                            }
                            
                            // 检查覆盖率报告是否存在
                            def coverageReportExists = false
                            def coverageReportPath = "${BUILD_DIR}/coverage/lcov.info"
                            if (fileExists(coverageReportPath)) {
                                def coverageReportValid = sh(
                                    script: """
                                        if [ -s "${coverageReportPath}" ]; then
                                            echo "VALID"
                                        else
                                            echo "EMPTY"
                                        fi
                                    """,
                                    returnStdout: true
                                ).trim()
                                
                                if (coverageReportValid == "VALID") {
                                    coverageReportExists = true
                                    echo "覆盖率报告存在，将包含在 SonarQube 分析中"
                                } else {
                                    echo "覆盖率报告为空，将跳过"
                                }
                            } else {
                                echo "覆盖率报告不存在，将跳过"
                            }
                            
                            // 动态构建 sonar-project.properties
                            def testReportFlag = testReportExists ? "true" : "false"
                            def coverageReportFlag = coverageReportExists ? "true" : "false"
                            
                            sh """
                                cat > sonar-project.properties << EOF
# SonarQube 项目配置
sonar.projectKey=${PROJECT_NAME}
sonar.projectName=${PROJECT_NAME}
sonar.projectVersion=${buildNumber}
sonar.sourceEncoding=UTF-8

# 源代码目录
sonar.sources=src
sonar.tests=tests

# 排除文件
sonar.exclusions=**/node_modules/**,**/dist/**,**/coverage/**,**/*.spec.js,**/*.test.js

# 项目基础目录
sonar.projectBaseDir=${BUILD_DIR}

# GitLab 集成（如果配置了）
sonar.gitlab.project_id=${GITLAB_REPO}
EOF
                                
                                # 如果覆盖率报告存在，添加配置
                                if [ "${coverageReportFlag}" == "true" ]; then
                                    echo "sonar.javascript.lcov.reportPaths=coverage/lcov.info" >> sonar-project.properties
                                    echo "已添加覆盖率报告配置"
                                fi
                                
                                # 如果测试报告存在且有效，添加配置
                                if [ "${testReportFlag}" == "true" ]; then
                                    echo "sonar.testExecutionReportPaths=${testReportPath}" >> sonar-project.properties
                                    echo "已添加测试报告配置"
                                fi
                                
                                echo ""
                                echo "sonar-project.properties 内容:"
                                cat sonar-project.properties
                            """
                            
                            // 运行 SonarQube 扫描
                            echo "运行 SonarQube 扫描..."
                            sh """
                                # 使用 sonar-scanner 或 sonarqube-scanner
                                if command -v sonar-scanner > /dev/null 2>&1; then
                                    echo "使用系统 sonar-scanner..."
                                    sonar-scanner
                                elif command -v sonarqube-scanner > /dev/null 2>&1; then
                                    echo "使用全局 sonarqube-scanner..."
                                    sonarqube-scanner
                                elif [ -f node_modules/.bin/sonar-scanner ]; then
                                    echo "使用本地 sonar-scanner..."
                                    ./node_modules/.bin/sonar-scanner
                                else
                                    echo "安装 sonarqube-scanner 作为项目依赖..."
                                    npm install --save-dev sonarqube-scanner
                                    ./node_modules/.bin/sonar-scanner
                                fi
                            """
                            
                            // 检查扫描结果
                            echo "检查 SonarQube 扫描结果..."
                            sh """
                                if [ -f ".scannerwork/report-task.txt" ]; then
                                    echo "✅ SonarQube 分析完成"
                                    echo "报告文件内容:"
                                    cat .scannerwork/report-task.txt
                                else
                                    echo "⚠️  警告: 未找到 report-task.txt 文件"
                                    echo "检查可能的路径:"
                                    find . -name "report-task.txt" -type f 2>/dev/null | head -5 || echo "未找到"
                                fi
                            """
                        }
                    }
                }
            }
        }

        // 阶段 7: 等待 SonarQube 质量门检查
        stage('SonarQube Quality Gate') {
            steps {
                script {
                    echo "=========================================="
                    echo "等待 SonarQube 质量门检查结果..."
                    echo "=========================================="
                    
                    // 确保在正确的目录下执行 waitForQualityGate
                    // report-task.txt 文件在 BUILD_DIR/.scannerwork/ 目录下
                    dir("${BUILD_DIR}") {
                        // 检查 report-task.txt 是否存在
                        def reportTaskExists = fileExists('.scannerwork/report-task.txt')
                        echo "检查 report-task.txt 文件: ${reportTaskExists}"
                        
                        if (reportTaskExists) {
                            sh """
                                echo "report-task.txt 文件内容:"
                                cat .scannerwork/report-task.txt
                            """
                        } else {
                            echo "⚠️  警告: 未找到 report-task.txt 文件"
                            echo "尝试在工作空间根目录查找..."
                            sh """
                                find ${WORKSPACE} -name "report-task.txt" -type f 2>/dev/null | head -5 || echo "未找到"
                            """
                        }
                        
                        // 增加超时时间到 10 分钟，因为 SonarQube 服务器可能需要更长时间处理
                        timeout(time: 10, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: false
                        }
                    }
                }
            }
        }

        // 阶段 8: 构建项目
        stage('Build') {
            steps {
                script {
                    echo "=========================================="
                    echo "构建项目..."
                    echo "=========================================="
                }

                dir("${BUILD_DIR}") {
                    sh """
                        npm run build
                    """
                }

                script {
                    // 检查构建产物是否存在
                    if (fileExists("${BUILD_DIR}/dist")) {
                        sh """
                            cp -r ${BUILD_DIR}/dist/* ${DIST_DIR}/
                            echo "构建完成，输出目录: ${DIST_DIR}"
                        """
                    } else {
                        error("构建失败：未找到 dist 目录")
                    }
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
                        cd ${DIST_DIR}
                        tar -czf ${ARTIFACTS_DIR}/${packageName} .

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

                // 归档测试结果
                archiveArtifacts artifacts: "test-results/**/*.xml", allowEmptyArchive: true
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
                    def nexusUrl = 'https://nexus.hohistar.com.cn/repository/todoapp-frontend-snapshots'
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
                    script: "find ${WORKSPACE}/test-results -name '*.xml' -type f 2>/dev/null || true",
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