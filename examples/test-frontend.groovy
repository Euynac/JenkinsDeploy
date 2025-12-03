// Vue.js 前端快速测试 Pipeline
// 适用于本地测试，无需 Git 仓库
// 包含单元测试、代码覆盖率和 SonarQube 分析

pipeline {
    agent {
        label 'vue'  // 使用 Vue.js agent
    }

    environment {
        // 项目配置
        PROJECT_NAME = 'TodoApp-frontend'
        PROJECT_PATH = 'todoapp-frontend-vue2-main'

        // 构建输出目录
        BUILD_DIR = "${WORKSPACE}/${PROJECT_PATH}"
        DIST_DIR = "${WORKSPACE}/dist"
        ARTIFACTS_DIR = "${WORKSPACE}/artifacts"

        // SonarQube 项目配置
        SONAR_PROJECT_KEY = 'todoapp-frontend'
        SONAR_PROJECT_NAME = 'TodoApp Frontend Vue2'
    }

    stages {
        // 阶段 1: 复制项目到工作空间
        stage('Copy Project to Workspace') {
            steps {
                script {
                    echo "=========================================="
                    echo "复制测试项目到工作空间..."
                    echo "=========================================="

                    sh """
                        # 源目录（Docker 容器中挂载的路径）
                        SOURCE_DIR="/test-projects/todoapp-frontend-vue2-main"

                        # 检查源目录是否存在
                        if [ -d "\$SOURCE_DIR" ]; then
                            echo "✅ 源目录存在: \$SOURCE_DIR"
                            # 复制整个项目
                            cp -r "\$SOURCE_DIR" ${WORKSPACE}/
                            echo "✅ 项目已复制到: ${WORKSPACE}/${PROJECT_PATH}"
                        else
                            echo "❌ 源目录不存在: \$SOURCE_DIR"
                            echo "请确保 docker-compose 配置中挂载了测试项目目录"
                            echo "检查 docker-compose-test-vue.yml 中的 volumes 配置"
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
                    echo "当前 Agent: ${NODE_NAME}"
                    echo "构建号: ${BUILD_NUMBER}"

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
                    echo "安装 npm 依赖（包括开发依赖）..."
                    echo "=========================================="
                }

                dir("${BUILD_DIR}") {
                    sh """
                        # 清理可能存在的 node_modules 和 package-lock.json
                        # rm -rf node_modules package-lock.json

                        # 确保安装所有依赖（包括 devDependencies）
                        # NODE_ENV 不设置为 production，确保安装开发依赖
                        npm install --include=dev --verbose

                        # 验证关键依赖是否安装
                        echo ""
                        echo "验证关键依赖安装情况："
                        npm list jest jest-environment-jsdom babel-jest vue-jest 2>&1 | head -20 || echo "部分依赖未在根级别，但可能在嵌套依赖中"
                    """
                }
            }
        }

        // 阶段 4: 运行单元测试
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

                                    // 运行测试并生成 JUnit 报告和覆盖率报告
                                    // 注意：jest-junit 已在 package.json 中定义，无需动态安装
                                    echo "运行测试并生成覆盖率报告..."
                                    def testExitCode = sh(
                                        script: """
                                            # 设置测试环境变量
                                            export NODE_ENV=test
                                            export JEST_JUNIT_OUTPUT_DIR="${WORKSPACE}/test-results"
                                            export JEST_JUNIT_OUTPUT_NAME="junit.xml"

                                            # 清除 Jest 缓存
                                            npx jest --clearCache

                                            # 运行测试：使用 jest-junit reporter + 生成覆盖率
                                            echo "运行测试（jest-junit + 覆盖率）..."
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
                                }
                            } else {
                                echo "package.json 不存在，跳过测试阶段"
                            }
                        } catch (Exception e) {
                            echo "=========================================="
                            echo "测试阶段发生错误:"
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

                    // 归档覆盖率报告
                    dir("${BUILD_DIR}") {
                        archiveArtifacts artifacts: 'coverage/**/*',
                                         allowEmptyArchive: true
                    }
                }
            }
        }

        // 阶段 5: SonarQube 代码质量分析
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
                                echo "sonar-scanner 未安装，将使用全局安装的 sonarqube-scanner..."
                            }

                            // 检查测试报告是否存在
                            def testReportExists = fileExists("${WORKSPACE}/test-results/junit.xml")
                            def coverageReportExists = fileExists("coverage/lcov.info")

                            // 动态构建 sonar-project.properties
                            def buildNumber = env.BUILD_NUMBER ?: '0'

                            sh """
                                cat > sonar-project.properties << EOF
# SonarQube 项目配置
sonar.projectKey=${SONAR_PROJECT_KEY}
sonar.projectName=${SONAR_PROJECT_NAME}
sonar.projectVersion=${buildNumber}
sonar.sourceEncoding=UTF-8

# 源代码目录
sonar.sources=src
sonar.tests=tests

# 排除文件
sonar.exclusions=**/node_modules/**,**/dist/**,**/coverage/**,**/*.spec.js,**/*.test.js

# 项目基础目录
sonar.projectBaseDir=${BUILD_DIR}
EOF

                                # 如果覆盖率报告存在，添加配置
                                if [ "${coverageReportExists}" == "true" ]; then
                                    echo "sonar.javascript.lcov.reportPaths=coverage/lcov.info" >> sonar-project.properties
                                    echo "已添加覆盖率报告配置"
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

        // 阶段 6: 等待 SonarQube 质量门检查
        stage('SonarQube Quality Gate') {
            steps {
                script {
                    echo "=========================================="
                    echo "等待 SonarQube 质量门检查结果..."
                    echo "=========================================="

                    // 确保在正确的目录下执行 waitForQualityGate
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
                        }

                        // 增加超时时间到 10 分钟
                        timeout(time: 10, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: false
                        }
                    }
                }
            }
        }

        // 阶段 7: 构建项目
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

        // 阶段 8: 创建压缩包
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

        // 阶段 9: 归档构建产物
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
    }

    post {
        // 构建成功后的操作
        success {
            script {
                echo "=========================================="
                echo "✅ 构建成功！"
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
                echo "❌ 构建失败！"
                echo "请检查构建日志以获取详细信息"
                echo "=========================================="
            }
        }

        // 无论成功或失败都执行
        always {
            script {
                echo "构建流程完成"
            }
        }
    }
}
