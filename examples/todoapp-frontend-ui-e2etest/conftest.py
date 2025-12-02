"""
pytest 配置文件
负责测试环境的启动、关闭和数据库重置
"""
import os
import pathlib
import subprocess
import time
import logging
import sys
import threading
import psycopg2
from psycopg2.extensions import ISOLATION_LEVEL_AUTOCOMMIT
import pytest
import requests
from dotenv import load_dotenv
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from webdriver_manager.chrome import ChromeDriverManager

# 配置日志 - 确保输出可见（即使 pytest 捕获了标准输出）
logger = logging.getLogger(__name__)

# 创建一个同时输出到控制台和日志的辅助函数
def log_print(message, level=logging.INFO):
    """同时使用 print 和 logging 输出，确保消息可见"""
    # 使用 print（如果 pytest 使用 -s 参数会显示）
    print(message, flush=True)
    # 使用 logging（pytest 的 log_cli 会显示）
    logger.log(level, message)


def read_output(pipe, prefix, log_func):
    """在单独线程中读取进程输出"""
    try:
        for line in iter(pipe.readline, ''):
            if line:
                log_func(f"[{prefix}] {line.rstrip()}")
        pipe.close()
    except Exception as e:
        log_func(f"[{prefix}] 读取输出时出错: {e}", logging.ERROR)

# 加载环境变量
load_dotenv()

# 测试配置
TEST_DB_HOST = os.getenv("TEST_DB_HOST", "localhost")
TEST_DB_PORT = os.getenv("TEST_DB_PORT", "5433")
TEST_DB_NAME = os.getenv("TEST_DB_NAME", "todoapp_test")
TEST_DB_USER = os.getenv("TEST_DB_USER", "postgres")
TEST_DB_PASSWORD = os.getenv("TEST_DB_PASSWORD", "postgres")
API_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:5085")
FRONTEND_BASE_URL = os.getenv("FRONTEND_BASE_URL", "http://localhost:8080")
API_STARTUP_TIMEOUT = int(os.getenv("API_STARTUP_TIMEOUT", "60"))
FRONTEND_STARTUP_TIMEOUT = int(os.getenv("FRONTEND_STARTUP_TIMEOUT", "60"))


def get_db_connection():
    """获取数据库连接"""
    return psycopg2.connect(
        host=TEST_DB_HOST,
        port=TEST_DB_PORT,
        database=TEST_DB_NAME,
        user=TEST_DB_USER,
        password=TEST_DB_PASSWORD
    )


def reset_database():
    """重置数据库：删除所有表并重新创建"""
    conn = None
    try:
        conn = get_db_connection()
        conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
        cursor = conn.cursor()
        
        # 删除所有表（按依赖顺序）
        cursor.execute("""
            DO $$ DECLARE
                r RECORD;
            BEGIN
                FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
                    EXECUTE 'DROP TABLE IF EXISTS ' || quote_ident(r.tablename) || ' CASCADE';
                END LOOP;
            END $$;
        """)
        
        # 重置序列
        cursor.execute("""
            DO $$ DECLARE
                r RECORD;
            BEGIN
                FOR r IN (SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public') LOOP
                    EXECUTE 'DROP SEQUENCE IF EXISTS ' || quote_ident(r.sequence_name) || ' CASCADE';
                END LOOP;
            END $$;
        """)
        
        # 创建表结构（基于 EF Core 模型）
        # Users 表
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS "Users" (
                "Id" SERIAL PRIMARY KEY,
                "Username" VARCHAR(50) NOT NULL UNIQUE,
                "Email" VARCHAR(100) NOT NULL UNIQUE,
                "PasswordHash" TEXT NOT NULL,
                "CreatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """)
        
        # Projects 表
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS "Projects" (
                "Id" SERIAL PRIMARY KEY,
                "Name" VARCHAR(200) NOT NULL,
                "Description" TEXT,
                "UserId" INTEGER NOT NULL,
                "CreatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                "UpdatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT "FK_Projects_Users_UserId" FOREIGN KEY ("UserId") 
                    REFERENCES "Users" ("Id") ON DELETE CASCADE
            )
        """)
        
        # Todos 表
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS "Todos" (
                "Id" SERIAL PRIMARY KEY,
                "Title" VARCHAR(200) NOT NULL,
                "Description" TEXT,
                "IsCompleted" BOOLEAN NOT NULL DEFAULT FALSE,
                "ProjectId" INTEGER NOT NULL,
                "CreatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                "UpdatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT "FK_Todos_Projects_ProjectId" FOREIGN KEY ("ProjectId") 
                    REFERENCES "Projects" ("Id") ON DELETE CASCADE
            )
        """)
        
        # 创建索引
        cursor.execute('CREATE INDEX IF NOT EXISTS "IX_Users_Username" ON "Users" ("Username")')
        cursor.execute('CREATE INDEX IF NOT EXISTS "IX_Users_Email" ON "Users" ("Email")')
        cursor.execute('CREATE INDEX IF NOT EXISTS "IX_Projects_UserId" ON "Projects" ("UserId")')
        cursor.execute('CREATE INDEX IF NOT EXISTS "IX_Todos_ProjectId" ON "Todos" ("ProjectId")')
        
        cursor.close()
        log_print("数据库已重置并创建表结构")
    except Exception as e:
        log_print(f"重置数据库时出错: {e}", logging.ERROR)
        raise
    finally:
        if conn:
            conn.close()


def wait_for_database(max_retries=30, retry_interval=1):
    """等待数据库就绪"""
    for i in range(max_retries):
        try:
            conn = get_db_connection()
            conn.close()
            log_print("数据库已就绪")
            return True
        except Exception as e:
            if i < max_retries - 1:
                log_print(f"等待数据库就绪... ({i+1}/{max_retries})")
                time.sleep(retry_interval)
            else:
                log_print(f"数据库连接失败: {e}", logging.ERROR)
                raise
    return False


def wait_for_api(max_retries=60, retry_interval=1):
    """等待 API 服务就绪"""
    for i in range(max_retries):
        try:
            response = requests.get(f"{API_BASE_URL}/swagger/index.html", timeout=2)
            if response.status_code in [200, 404]:
                log_print("API 服务已就绪")
                return True
        except requests.exceptions.RequestException:
            pass
        
        if i < max_retries - 1:
            log_print(f"等待 API 服务就绪... ({i+1}/{max_retries})")
            time.sleep(retry_interval)
        else:
            log_print("API 服务启动超时", logging.ERROR)
            raise TimeoutError("API 服务启动超时")
    return False


def wait_for_frontend(max_retries=60, retry_interval=1):
    """等待前端服务就绪"""
    for i in range(max_retries):
        try:
            response = requests.get(f"{FRONTEND_BASE_URL}", timeout=2)
            if response.status_code == 200:
                log_print("前端服务已就绪")
                return True
        except requests.exceptions.RequestException:
            pass
        
        if i < max_retries - 1:
            log_print(f"等待前端服务就绪... ({i+1}/{max_retries})")
            time.sleep(retry_interval)
        else:
            log_print("前端服务启动超时", logging.ERROR)
            raise TimeoutError("前端服务启动超时")
    return False


def get_docker_compose_cmd():
    """根据操作系统检测 Docker Compose 命令"""
    import platform
    system = platform.system()
    
    if system == "Darwin":  # macOS
        try:
            subprocess.run(
                ["docker", "compose", "version"],
                check=True,
                capture_output=True,
                text=True
            )
            return ["docker", "compose"]
        except (subprocess.CalledProcessError, FileNotFoundError):
            raise RuntimeError("未找到 docker compose 命令（macOS 需要 Docker Desktop）")
    else:
        try:
            subprocess.run(
                ["docker-compose", "--version"],
                check=True,
                capture_output=True,
                text=True
            )
            return ["docker-compose"]
        except (subprocess.CalledProcessError, FileNotFoundError):
            try:
                subprocess.run(
                    ["docker", "compose", "version"],
                    check=True,
                    capture_output=True,
                    text=True
                )
                return ["docker", "compose"]
            except (subprocess.CalledProcessError, FileNotFoundError):
                raise RuntimeError("未找到 docker-compose 或 docker compose 命令")


def start_docker_compose():
    """启动 Docker Compose 服务"""
    compose_file = os.path.join(os.path.dirname(__file__), "docker-compose.test.yml")
    try:
        docker_compose_cmd = get_docker_compose_cmd()
        subprocess.run(
            docker_compose_cmd + ["-f", compose_file, "up", "-d"],
            check=True,
            capture_output=True,
            text=True
        )
        log_print("Docker Compose 服务已启动")
    except subprocess.CalledProcessError as e:
        log_print(f"启动 Docker Compose 失败: {e.stderr}", logging.ERROR)
        raise
    except RuntimeError as e:
        log_print(f"检测 Docker Compose 命令失败: {e}", logging.ERROR)
        raise


def stop_docker_compose():
    """停止 Docker Compose 服务"""
    compose_file = os.path.join(os.path.dirname(__file__), "docker-compose.test.yml")
    try:
        docker_compose_cmd = get_docker_compose_cmd()
        subprocess.run(
            docker_compose_cmd + ["-f", compose_file, "down", "-v"],
            check=True,
            capture_output=True,
            text=True
        )
        log_print("Docker Compose 服务已停止")
    except subprocess.CalledProcessError as e:
        log_print(f"停止 Docker Compose 失败: {e.stderr}", logging.ERROR)
    except RuntimeError as e:
        log_print(f"检测 Docker Compose 命令失败: {e}", logging.ERROR)


def start_backend_api():
    """启动后端 API 服务"""
    log_print("检查后端 API 服务状态...")
    backend_dir = os.path.join(os.path.dirname(__file__), "..", "todoapp-backend-api")
    backend_dir = os.path.abspath(backend_dir)
    
    if not os.path.exists(backend_dir):
        error_msg = f"后端项目目录不存在: {backend_dir}"
        log_print(f"错误: {error_msg}", logging.ERROR)
        raise FileNotFoundError(error_msg)
    
    log_print(f"后端项目目录: {backend_dir}")
    log_print(f"API 基础 URL: {API_BASE_URL}")
    
    # 检查是否已经运行
    log_print(f"检查后端服务是否已在运行 ({API_BASE_URL})...")
    try:
        response = requests.get(f"{API_BASE_URL}/swagger/index.html", timeout=2)
        if response.status_code in [200, 404]:
            log_print("后端 API 服务已在运行，跳过启动")
            return None
    except requests.exceptions.RequestException as e:
        log_print(f"后端服务未运行或无法连接: {e}")
    
    # 构建数据库连接字符串
    # .NET Core 使用 PostgreSQL 连接字符串格式
    connection_string = f"Host={TEST_DB_HOST};Port={TEST_DB_PORT};Database={TEST_DB_NAME};Username={TEST_DB_USER};Password={TEST_DB_PASSWORD}"
    log_print(f"数据库连接字符串: Host={TEST_DB_HOST};Port={TEST_DB_PORT};Database={TEST_DB_NAME};Username={TEST_DB_USER};Password=***")
    
    # 设置后端环境变量
    env = os.environ.copy()
    # .NET Core 使用双下划线或冒号作为配置键分隔符
    env["ConnectionStrings__DefaultConnection"] = connection_string
    env["ConnectionStrings:DefaultConnection"] = connection_string
    # 也设置一些常见的环境变量名
    env["DATABASE_URL"] = f"postgresql://{TEST_DB_USER}:{TEST_DB_PASSWORD}@{TEST_DB_HOST}:{TEST_DB_PORT}/{TEST_DB_NAME}"
    env["DB_HOST"] = TEST_DB_HOST
    env["DB_PORT"] = str(TEST_DB_PORT)
    env["DB_NAME"] = TEST_DB_NAME
    env["DB_USER"] = TEST_DB_USER
    env["DB_PASSWORD"] = TEST_DB_PASSWORD
    
    # 启动后端服务
    log_print("启动后端 API 服务...")
    try:
        process = subprocess.Popen(
            ["dotnet", "run", "--urls", API_BASE_URL],
            cwd=backend_dir,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            env=env,
            bufsize=1  # 行缓冲
        )
        log_print(f"后端 API 服务进程已启动 (PID: {process.pid})")
        
        # 启动线程实时读取输出
        stdout_thread = threading.Thread(
            target=read_output,
            args=(process.stdout, "后端-STDOUT", log_print),
            daemon=True
        )
        stderr_thread = threading.Thread(
            target=read_output,
            args=(process.stderr, "后端-STDERR", lambda msg, level=logging.ERROR: log_print(msg, level)),
            daemon=True
        )
        stdout_thread.start()
        stderr_thread.start()
        
    except FileNotFoundError as e:
        error_msg = f"未找到 dotnet 命令，请确保已安装 .NET SDK: {e}"
        log_print(f"错误: {error_msg}", logging.ERROR)
        raise RuntimeError(error_msg) from e
    except Exception as e:
        error_msg = f"启动后端服务时出错: {e}"
        log_print(f"错误: {error_msg}", logging.ERROR)
        raise
    
    # 等待服务就绪，同时检查进程状态
    log_print("等待后端 API 服务就绪...")
    try:
        wait_for_api_with_process_check(process)
        log_print("后端 API 服务已就绪")
    except Exception as e:
        # 如果进程已退出，尝试读取最后的错误信息
        if process.poll() is not None:
            log_print(f"后端进程已退出，退出码: {process.returncode}", logging.ERROR)
            # 尝试读取剩余的输出
            try:
                remaining_stdout = process.stdout.read()
                remaining_stderr = process.stderr.read()
                if remaining_stdout:
                    log_print(f"后端最后输出 (STDOUT): {remaining_stdout}", logging.ERROR)
                if remaining_stderr:
                    log_print(f"后端最后输出 (STDERR): {remaining_stderr}", logging.ERROR)
            except:
                pass
        raise
    return process


def wait_for_api_with_process_check(process, max_retries=60, retry_interval=1):
    """等待 API 服务就绪，同时检查进程是否还在运行"""
    for i in range(max_retries):
        # 检查进程是否还在运行
        if process.poll() is not None:
            error_msg = f"后端进程已退出，退出码: {process.returncode}"
            log_print(error_msg, logging.ERROR)
            raise RuntimeError(error_msg)
        
        # 检查 API 是否就绪
        try:
            response = requests.get(f"{API_BASE_URL}/swagger/index.html", timeout=2)
            if response.status_code in [200, 404]:
                log_print("API 服务已就绪")
                return True
        except requests.exceptions.RequestException:
            pass
        
        if i < max_retries - 1:
            log_print(f"等待 API 服务就绪... ({i+1}/{max_retries})")
            time.sleep(retry_interval)
        else:
            log_print("API 服务启动超时", logging.ERROR)
            raise TimeoutError("API 服务启动超时")
    return False


def start_frontend():
    """启动前端服务"""
    log_print("检查前端服务状态...")
    frontend_dir = os.path.join(os.path.dirname(__file__), "..", "todoapp-frontend-vue2")
    frontend_dir = os.path.abspath(frontend_dir)
    
    if not os.path.exists(frontend_dir):
        error_msg = f"前端项目目录不存在: {frontend_dir}"
        log_print(f"错误: {error_msg}", logging.ERROR)
        raise FileNotFoundError(error_msg)
    
    log_print(f"前端项目目录: {frontend_dir}")
    log_print(f"前端基础 URL: {FRONTEND_BASE_URL}")
    
    # 检查是否已经运行
    log_print(f"检查前端服务是否已在运行 ({FRONTEND_BASE_URL})...")
    try:
        response = requests.get(f"{FRONTEND_BASE_URL}", timeout=2)
        if response.status_code == 200:
            log_print("前端服务已在运行，跳过启动")
            return None
    except requests.exceptions.RequestException as e:
        log_print(f"前端服务未运行或无法连接: {e}")
    
    # 设置环境变量，确保使用真实 API
    env = os.environ.copy()
    env["VUE_APP_USE_MOCK"] = "false"
    env["VUE_APP_API_BASE_URL"] = API_BASE_URL
    log_print(f"设置前端环境变量: VUE_APP_USE_MOCK=false, VUE_APP_API_BASE_URL={API_BASE_URL}")
    
    # 启动前端服务
    log_print("启动前端服务...")
    try:
        process = subprocess.Popen(
            ["npm", "run", "serve"],
            cwd=frontend_dir,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            env=env
        )
        log_print(f"前端服务进程已启动 (PID: {process.pid})")
    except FileNotFoundError as e:
        error_msg = f"未找到 npm 命令，请确保已安装 Node.js: {e}"
        log_print(f"错误: {error_msg}", logging.ERROR)
        raise RuntimeError(error_msg) from e
    except Exception as e:
        error_msg = f"启动前端服务时出错: {e}"
        log_print(f"错误: {error_msg}", logging.ERROR)
        raise
    
    # 等待服务就绪
    log_print("等待前端服务就绪...")
    wait_for_frontend()
    log_print("前端服务已就绪")
    return process


@pytest.fixture(scope="session", autouse=True)
def test_environment():
    """测试环境启动和关闭（会话级别）- 自动运行以确保后端和前端服务启动"""
    log_print("\n" + "="*60)
    log_print("=== 启动测试环境 ===")
    log_print("="*60)
    
    # 启动 Docker Compose（数据库）
    log_print("\n[1/3] 启动测试数据库...")
    start_docker_compose()
    wait_for_database()
    log_print("✓ 测试数据库已就绪\n")
    
    # 启动后端 API
    log_print("[2/3] 启动后端 API 服务...")
    api_process = start_backend_api()
    log_print("✓ 后端 API 服务已就绪\n")
    
    # 启动前端
    log_print("[3/3] 启动前端服务...")
    frontend_process = start_frontend()
    log_print("✓ 前端服务已就绪\n")
    
    log_print("="*60)
    log_print("=== 测试环境启动完成，开始执行测试 ===")
    log_print("="*60 + "\n")
    
    yield
    
    log_print("\n" + "="*60)
    log_print("=== 关闭测试环境 ===")
    log_print("="*60)
    
    # 停止前端
    if frontend_process:
        log_print("\n停止前端服务...")
        frontend_process.terminate()
        try:
            frontend_process.wait(timeout=10)
            log_print("✓ 前端服务已停止")
        except subprocess.TimeoutExpired:
            frontend_process.kill()
            log_print("⚠ 前端服务强制终止")
    else:
        log_print("\n前端服务未由测试环境启动，跳过停止")
    
    # 停止后端 API
    if api_process:
        log_print("\n停止后端 API 服务...")
        api_process.terminate()
        try:
            api_process.wait(timeout=10)
            log_print("✓ 后端 API 服务已停止")
        except subprocess.TimeoutExpired:
            api_process.kill()
            log_print("⚠ 后端 API 服务强制终止")
    else:
        log_print("\n后端 API 服务未由测试环境启动，跳过停止")
    
    log_print("\n" + "="*60)
    log_print("=== 测试环境已关闭 ===")
    log_print("="*60)
    
    # 可以选择是否在测试后停止 Docker Compose
    # stop_docker_compose()


@pytest.fixture(scope="function")
def reset_db(test_environment):
    """每个测试前重置数据库（函数级别）"""
    reset_database()
    yield
    # 测试后可以选择清理或保留数据


@pytest.fixture(scope="function")
def driver():
    """提供 Selenium WebDriver 实例"""
    chrome_options = Options()
    
    # 可选：无头模式（在 CI 环境中使用）
    if os.getenv("HEADLESS", "false").lower() == "true":
        chrome_options.add_argument("--headless")
    
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--disable-gpu")
    chrome_options.add_argument("--window-size=1920,1080")
    
    # 使用 webdriver-manager 自动管理 ChromeDriver
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=chrome_options)
    
    yield driver
    
    # 清理
    driver.quit()


@pytest.fixture(scope="function")
def api_client():
    """提供 API 客户端（用于准备测试数据）"""
    class APIClient:
        def __init__(self, base_url):
            self.base_url = base_url
            self.session = requests.Session()
            self.token = None
        
        def set_token(self, token):
            self.token = token
            self.session.headers.update({
                "Authorization": f"Bearer {token}"
            })
        
        def post(self, endpoint, json=None, **kwargs):
            url = f"{self.base_url}{endpoint}"
            return self.session.post(url, json=json, **kwargs)
        
        def get(self, endpoint, **kwargs):
            url = f"{self.base_url}{endpoint}"
            return self.session.get(url, **kwargs)
    
    return APIClient(API_BASE_URL)

