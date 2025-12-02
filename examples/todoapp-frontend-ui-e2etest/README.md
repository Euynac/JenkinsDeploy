# TodoApp Frontend UI E2E 测试

Vue2 前端应用的端到端 UI 测试项目。

## 技术栈

- **测试框架**: pytest
- **BDD**: pytest-bdd
- **UI 自动化**: Selenium 4
- **WebDriver**: ChromeDriver (通过 webdriver-manager 自动管理)
- **并行测试**: pytest-xdist
- **测试报告**: Allure Pytest Adapter / pytest-html
- **数据库**: PostgreSQL (Docker)

## 项目结构

```
todoapp-frontend-ui-e2etest/
├── docker-compose.test.yml          # 测试数据库配置
├── conftest.py                      # 测试环境管理
├── requirements.txt                 # Python 依赖
├── pytest.ini                       # pytest 配置
├── features/
│   └── login.feature                # 登录功能测试用例（BDD）
├── step_definitions/
│   └── login_steps.py               # 登录步骤定义（Selenium）
├── run_tests.sh                     # 一键执行测试脚本
└── setup_venv.sh                    # 虚拟环境设置脚本
```

## 快速开始

### 1. 设置虚拟环境

```bash
chmod +x setup_venv.sh
./setup_venv.sh
```

### 2. 运行测试

```bash
chmod +x run_tests.sh
./run_tests.sh
```

### 3. 查看测试报告

#### Allure 报告（推荐）

```bash
./run_tests.sh --allure
allure serve test-results/allure-results
```

#### HTML 报告

```bash
./run_tests.sh --html
# 打开 test-results/report.html
```

## 环境变量

可以通过 `.env` 文件或环境变量配置：

```bash
TEST_DB_HOST=localhost
TEST_DB_PORT=5433
TEST_DB_NAME=todoapp_test
TEST_DB_USER=postgres
TEST_DB_PASSWORD=postgres
API_BASE_URL=http://localhost:5085
FRONTEND_BASE_URL=http://localhost:8080
HEADLESS=true  # 是否使用无头浏览器模式
```

## 测试流程

1. **启动数据库**: 通过 Docker Compose 启动 PostgreSQL
2. **启动后端 API**: 自动启动 .NET 后端服务
3. **启动前端**: 自动启动 Vue2 前端服务
4. **重置数据库**: 每个测试前自动重置数据库
5. **执行测试**: 使用 Selenium 进行 UI 测试
6. **生成报告**: 生成 Allure 或 HTML 报告

## 在 Jenkins 中使用

### 1. 安装依赖

```groovy
sh '''
    cd todoapp-frontend-ui-e2etest
    ./setup_venv.sh
'''
```

### 2. 运行测试

```groovy
sh '''
    cd todoapp-frontend-ui-e2etest
    source venv/bin/activate
    pytest --alluredir=test-results/allure-results
'''
```

### 3. 发布 Allure 报告

```groovy
allure([
    includeProperties: false,
    jdk: '',
    properties: [],
    reportBuildPolicy: 'ALWAYS',
    results: [[path: 'todoapp-frontend-ui-e2etest/test-results/allure-results']]
])
```

## 注意事项

1. 确保已安装 Chrome 浏览器（Selenium 使用 ChromeDriver）
2. 确保已安装 Docker 和 Docker Compose
3. 确保已安装 .NET SDK（用于启动后端）
4. 确保已安装 Node.js 和 npm（用于启动前端）
5. 在 CI 环境中，建议设置 `HEADLESS=true` 使用无头浏览器模式

## 测试用例

当前实现了用户登录功能的测试，包括：

- ✅ 成功登录
- ✅ 登录失败 - 错误的密码
- ✅ 登录失败 - 不存在的用户
- ✅ 登录失败 - 空用户名
- ✅ 登录失败 - 空密码

## 故障排除

### 数据库连接失败

确保 Docker 正在运行，并且端口 5433 未被占用。

### 后端 API 启动失败

确保已安装 .NET SDK，并且 `todoapp-backend-api` 项目可以正常编译运行。

### 前端启动失败

确保已安装 Node.js 和 npm，并且 `todoapp-frontend-vue2` 项目的依赖已安装。

### ChromeDriver 问题

webdriver-manager 会自动下载和管理 ChromeDriver，如果遇到问题，可以手动下载 ChromeDriver 并设置 PATH。
