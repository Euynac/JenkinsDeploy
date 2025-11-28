"""
pytest 配置文件
负责测试环境的启动、关闭和数据库重置
"""
import os
import pathlib
import subprocess
import time
import psycopg2
from psycopg2.extensions import ISOLATION_LEVEL_AUTOCOMMIT
import pytest
import requests
from dotenv import load_dotenv
from pytest_bdd import scenarios

# 加载环境变量
load_dotenv()


# 移除 pytest_configure，让 pytest-bdd 自动发现 feature 文件

# 测试配置
TEST_DB_HOST = os.getenv("TEST_DB_HOST", "localhost")
TEST_DB_PORT = os.getenv("TEST_DB_PORT", "5433")
TEST_DB_NAME = os.getenv("TEST_DB_NAME", "todoapp_test")
TEST_DB_USER = os.getenv("TEST_DB_USER", "postgres")
TEST_DB_PASSWORD = os.getenv("TEST_DB_PASSWORD", "postgres")
API_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:5085")
API_STARTUP_TIMEOUT = int(os.getenv("API_STARTUP_TIMEOUT", "30"))


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
        print("数据库已重置并创建表结构")
    except Exception as e:
        print(f"重置数据库时出错: {e}")
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
            print("数据库已就绪")
            return True
        except Exception as e:
            if i < max_retries - 1:
                print(f"等待数据库就绪... ({i+1}/{max_retries})")
                time.sleep(retry_interval)
            else:
                print(f"数据库连接失败: {e}")
                raise
    return False


def wait_for_api(max_retries=30, retry_interval=1):
    """等待 API 服务就绪"""
    for i in range(max_retries):
        try:
            response = requests.get(f"{API_BASE_URL}/swagger/index.html", timeout=2)
            if response.status_code in [200, 404]:  # 404 也可以，说明服务已启动
                print("API 服务已就绪")
                return True
        except requests.exceptions.RequestException:
            pass
        
        if i < max_retries - 1:
            print(f"等待 API 服务就绪... ({i+1}/{max_retries})")
            time.sleep(retry_interval)
        else:
            print("API 服务启动超时")
            raise TimeoutError("API 服务启动超时")
    return False


def get_docker_compose_cmd():
    """根据操作系统检测 Docker Compose 命令"""
    import platform
    system = platform.system()
    
    if system == "Darwin":  # macOS
        # macOS: 使用 docker compose (V2, 无连字符)
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
        # Linux/Windows: 优先使用 docker-compose (V1, 有连字符)
        try:
            subprocess.run(
                ["docker-compose", "--version"],
                check=True,
                capture_output=True,
                text=True
            )
            return ["docker-compose"]
        except (subprocess.CalledProcessError, FileNotFoundError):
            # 如果 docker-compose 不存在，尝试 docker compose (V2)
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
        print("Docker Compose 服务已启动")
    except subprocess.CalledProcessError as e:
        print(f"启动 Docker Compose 失败: {e.stderr}")
        raise
    except RuntimeError as e:
        print(f"检测 Docker Compose 命令失败: {e}")
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
        print("Docker Compose 服务已停止")
    except subprocess.CalledProcessError as e:
        print(f"停止 Docker Compose 失败: {e.stderr}")
    except RuntimeError as e:
        print(f"检测 Docker Compose 命令失败: {e}")


@pytest.fixture(scope="session")
def test_environment():
    """测试环境启动和关闭（会话级别）"""
    print("\n=== 启动测试环境 ===")
    
    # 启动 Docker Compose
    start_docker_compose()
    
    # 等待数据库就绪
    wait_for_database()
    
    # 注意：API 服务需要手动启动，这里只检查是否就绪
    # 在实际使用中，需要在运行测试前手动启动 API 服务
    # 或者使用 subprocess 启动 dotnet run
    try:
        wait_for_api()
    except TimeoutError:
        print("警告: API 服务未启动，请确保在运行测试前启动 API 服务")
        print(f"启动命令: cd ../todoapp-backend-api && dotnet run --urls {API_BASE_URL}")
    
    yield
    
    print("\n=== 关闭测试环境 ===")
    # 可以选择是否在测试后停止 Docker Compose
    # stop_docker_compose()  # 取消注释以在测试后停止服务


@pytest.fixture(scope="function")
def reset_db(test_environment):
    """每个测试前重置数据库（函数级别）"""
    reset_database()
    yield
    # 测试后可以选择清理或保留数据


@pytest.fixture(scope="function")
def api_client():
    """提供 API 客户端"""
    class APIClient:
        def __init__(self, base_url):
            self.base_url = base_url
            self.session = requests.Session()
            self.token = None
        
        def set_token(self, token):
            """设置认证 token"""
            self.token = token
            self.session.headers.update({
                "Authorization": f"Bearer {token}"
            })
        
        def clear_token(self):
            """清除认证 token"""
            self.token = None
            if "Authorization" in self.session.headers:
                del self.session.headers["Authorization"]
        
        def post(self, endpoint, json=None, **kwargs):
            """POST 请求"""
            url = f"{self.base_url}{endpoint}"
            return self.session.post(url, json=json, **kwargs)
        
        def get(self, endpoint, **kwargs):
            """GET 请求"""
            url = f"{self.base_url}{endpoint}"
            return self.session.get(url, **kwargs)
        
        def put(self, endpoint, json=None, **kwargs):
            """PUT 请求"""
            url = f"{self.base_url}{endpoint}"
            return self.session.put(url, json=json, **kwargs)
        
        def delete(self, endpoint, **kwargs):
            """DELETE 请求"""
            url = f"{self.base_url}{endpoint}"
            return self.session.delete(url, **kwargs)
        
        def patch(self, endpoint, json=None, **kwargs):
            """PATCH 请求"""
            url = f"{self.base_url}{endpoint}"
            return self.session.patch(url, json=json, **kwargs)
    
    return APIClient(API_BASE_URL)

