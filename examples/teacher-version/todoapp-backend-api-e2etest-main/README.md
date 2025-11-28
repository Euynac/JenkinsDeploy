# TodoApp Backend API 端到端测试

基于 Python + pytest + pytest-bdd 的端到端 API 测试项目，用于测试 TodoApp 后端 API。

## 项目结构

```
todoapp-backend-api-e2etest/
├── docker-compose.test.yml      # 测试数据库 Docker Compose 配置
├── features/                     # BDD 测试用例（Gherkin 格式）
│   └── 用户登录.feature
├── step_definitions/            # 步骤定义（Python 实现）
│   └── login_steps.py
├── conftest.py                  # pytest 配置和 fixtures
├── requirements.txt             # Python 依赖
├── pytest.ini                   # pytest 配置文件
└── README.md                    # 本文件
```

## 前置要求

1. **Python 3.8+**
2. **Docker 和 Docker Compose**（用于运行测试数据库）
3. **.NET 8 SDK**（用于运行后端 API）
4. **PostgreSQL 客户端工具**（可选，用于调试）

## 安装步骤

### 1. 创建并激活虚拟环境（强烈推荐）

**为什么使用虚拟环境？**
- ✅ **依赖隔离**：避免与系统 Python 或其他项目的依赖冲突
- ✅ **可重现性**：确保在不同环境中使用相同的依赖版本
- ✅ **CI/CD 最佳实践**：在 Jenkins 等 CI 环境中是标准做法
- ✅ **易于清理**：删除虚拟环境即可完全清理项目依赖

**创建虚拟环境：**

```bash
# 创建虚拟环境
python3 -m venv venv

# 激活虚拟环境
# macOS/Linux:
source venv/bin/activate
# Windows:
# venv\Scripts\activate
```

**验证虚拟环境已激活：**

```bash
which python  # 应该显示 venv/bin/python
```

### 2. 安装 Python 依赖

```bash
pip install --upgrade pip
pip install -r requirements.txt
```

### 2. 配置环境变量（可选）

创建 `.env` 文件（可选，使用默认值也可以）：

```env
TEST_DB_HOST=localhost
TEST_DB_PORT=5433
TEST_DB_NAME=todoapp_test
TEST_DB_USER=postgres
TEST_DB_PASSWORD=postgres
API_BASE_URL=http://localhost:5085
API_STARTUP_TIMEOUT=30
```

### 3. 启动测试数据库

```bash
docker-compose -f docker-compose.test.yml up -d
```

验证数据库是否运行：

```bash
docker ps | grep todoapp-postgres-test
```

### 4. 启动后端 API

在另一个终端中，启动后端 API：

```bash
cd ../todoapp-backend-api
dotnet run --urls http://localhost:5085
```

**注意**：确保后端 API 使用测试数据库连接字符串。可以创建 `appsettings.Test.json` 或在启动时设置环境变量：

```bash
export ConnectionStrings__DefaultConnection="Host=localhost;Port=5433;Database=todoapp_test;Username=postgres;Password=postgres"
cd ../todoapp-backend-api
dotnet run --urls http://localhost:5085
```

## 运行测试

### 运行所有测试

```bash
pytest
```

### 运行特定功能测试

```bash
pytest features/用户登录.feature
```

### 运行测试并生成 HTML 报告

```bash
pytest --html=test-results/report.html --self-contained-html
```

报告将保存在 `test-results/report.html`

### 运行测试并生成 Allure 报告

```bash
# 运行测试
pytest

# 生成并打开 Allure 报告
allure serve test-results/allure-results
```

### 查看详细输出

```bash
pytest -v -s
```

## 测试流程说明

1. **测试环境启动**：`conftest.py` 中的 `test_environment` fixture 会在测试会话开始时启动 Docker Compose 数据库
2. **数据库重置**：每个测试函数执行前，`reset_db` fixture 会重置数据库（删除所有表并重新创建）
3. **测试执行**：pytest-bdd 执行 `.feature` 文件中定义的测试场景
4. **测试清理**：测试完成后，可以选择保留或清理测试数据

## 编写新的测试用例

### 1. 创建 Feature 文件

在 `features/` 目录下创建新的 `.feature` 文件，例如 `项目管理.feature`：

```gherkin
# language: zh-CN
功能: 项目管理
  作为系统用户
  我想要管理我的项目
  以便组织我的待办事项

  场景: 创建新项目
    假设 我已经登录系统
    当 我创建一个名为 "新项目" 的项目
    那么 响应状态码应该是 200
    而且 响应应该包含项目信息
```

### 2. 创建步骤定义

在 `step_definitions/` 目录下创建对应的步骤定义文件，例如 `project_steps.py`：

```python
from pytest_bdd import given, when, then, parsers
import pytest

@given('我已经登录系统')
def user_logged_in(api_client):
    # 实现登录逻辑
    pass

@when(parsers.parse('我创建一个名为 "{name}" 的项目'))
def create_project(api_client, name):
    # 实现创建项目逻辑
    pass
```

## 集成到 Jenkins CI

### 1. 在 Jenkinsfile 中添加测试步骤（使用虚拟环境）

**推荐方式：使用虚拟环境**

```groovy
stage('E2E Tests') {
    steps {
        dir('todoapp-backend-api-e2etest') {
            script {
                // 创建虚拟环境（如果不存在）
                sh '''
                    if [ ! -d "venv" ]; then
                        python3 -m venv venv
                    fi
                '''
                
                // 激活虚拟环境并安装依赖
                sh '''
                    source venv/bin/activate
                    pip install --upgrade pip
                    pip install -r requirements.txt
                '''
                
                // 启动测试数据库
                sh 'docker-compose -f docker-compose.test.yml up -d'
                sh 'sleep 10'  // 等待数据库就绪
                
                // 运行测试（在虚拟环境中）
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
                // 发布 Allure 报告
                allure([
                    includeProperties: false,
                    jdk: '',
                    properties: [],
                    reportBuildPolicy: 'ALWAYS',
                    results: [[path: 'test-results/allure-results']]
                ])
                
                // 清理测试环境
                sh 'docker-compose -f docker-compose.test.yml down -v'
                
                // 可选：清理虚拟环境（如果需要完全清理）
                // sh 'rm -rf venv'
            }
        }
    }
}
```

**优化版本：使用虚拟环境缓存（提高构建速度）**

```groovy
stage('E2E Tests') {
    steps {
        dir('todoapp-backend-api-e2etest') {
            script {
                // 检查虚拟环境是否存在，如果存在则直接使用
                def venvExists = sh(
                    script: 'test -d venv',
                    returnStatus: true
                ) == 0
                
                if (!venvExists) {
                    echo "创建新的虚拟环境..."
                    sh 'python3 -m venv venv'
                } else {
                    echo "使用现有的虚拟环境..."
                }
                
                // 激活虚拟环境并安装/更新依赖
                sh '''
                    source venv/bin/activate
                    pip install --upgrade pip
                    pip install -r requirements.txt
                '''
                
                // 启动测试数据库
                sh 'docker-compose -f docker-compose.test.yml up -d'
                sh 'sleep 10'
                
                // 运行测试
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
                allure([
                    includeProperties: false,
                    jdk: '',
                    properties: [],
                    reportBuildPolicy: 'ALWAYS',
                    results: [[path: 'test-results/allure-results']]
                ])
                sh 'docker-compose -f docker-compose.test.yml down -v'
            }
        }
    }
}
```

### 2. 安装 Allure Jenkins 插件

在 Jenkins 中安装 "Allure Jenkins Plugin" 插件以显示测试报告。

### 3. CI 环境最佳实践

**✅ 推荐做法：**
- 使用虚拟环境隔离依赖
- 在每次构建时升级 pip 确保使用最新版本
- 缓存虚拟环境目录以提高构建速度（如果 Jenkins agent 支持）
- 在 `post { always }` 中清理测试资源（Docker 容器等）

**❌ 不推荐：**
- 在系统级别安装 Python 包（`pip install --user` 或全局安装）
- 在多个项目间共享虚拟环境
- 跳过虚拟环境直接安装依赖

## 故障排查

### 数据库连接失败

- 检查 Docker Compose 服务是否运行：`docker ps`
- 检查端口是否被占用：`lsof -i :5433`
- 检查数据库日志：`docker logs todoapp-postgres-test`

### API 连接失败

- 确认后端 API 正在运行：访问 `http://localhost:5085/swagger`
- 检查 API 是否使用正确的数据库连接字符串
- 查看 API 日志确认是否有错误

### 测试失败

- 使用 `-v -s` 参数查看详细输出：`pytest -v -s`
- 检查测试数据库是否正确重置
- 确认测试数据是否正确创建

## 技术栈

- **pytest**: Python 测试框架
- **pytest-bdd**: BDD 测试支持（Gherkin 语法）
- **pytest-html**: HTML 测试报告
- **allure-pytest**: Allure 测试报告
- **requests**: HTTP 客户端
- **psycopg2**: PostgreSQL 数据库驱动
- **bcrypt**: 密码哈希（用于创建测试用户）

## 许可证

本项目遵循项目主仓库的许可证。
