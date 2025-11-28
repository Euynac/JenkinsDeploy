"""
用户登录功能的步骤定义
"""
import json
import os
import psycopg2
from psycopg2.extensions import ISOLATION_LEVEL_AUTOCOMMIT
import bcrypt
from pytest_bdd import given, when, then, parsers, scenarios
import pytest
from conftest import get_db_connection, API_BASE_URL

# 使用 scenarios() 加载 feature 文件
# 注意：由于 pytest.ini 中配置了 bdd_features_base_dir = features
# 所以这里只需要提供相对于 features 目录的路径
scenarios("login.feature")


# 使用 pytest 的 stash 来存储测试上下文
@pytest.fixture(scope="function")
def test_context():
    """测试上下文，用于在步骤之间共享数据"""
    return {}


@given(parsers.parse('数据库中已存在用户 "{username}"，密码为 "{password}"'))
def create_user_in_database(username, password, api_client):
    """在数据库中创建用户（使用 API 注册接口确保密码哈希格式正确）"""
    import requests
    
    # 先检查用户是否已存在，如果存在则删除
    conn = None
    try:
        conn = get_db_connection()
        conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
        cursor = conn.cursor()
        
        # 检查表是否存在，如果不存在则创建
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS "Users" (
                "Id" SERIAL PRIMARY KEY,
                "Username" VARCHAR(50) NOT NULL UNIQUE,
                "Email" VARCHAR(100) NOT NULL UNIQUE,
                "PasswordHash" TEXT NOT NULL,
                "CreatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """)
        
        # 检查用户是否已存在
        cursor.execute('SELECT "Id" FROM "Users" WHERE "Username" = %s', (username,))
        existing_user = cursor.fetchone()
        
        if existing_user:
            # 删除现有用户
            cursor.execute('DELETE FROM "Users" WHERE "Username" = %s', (username,))
        
        cursor.close()
    except Exception as e:
        print(f"清理用户失败: {e}")
    finally:
        if conn:
            conn.close()
    
    # 使用 API 注册接口创建用户（确保密码哈希格式与后端一致）
    email = f"{username}@example.com"
    register_data = {
        "username": username,
        "email": email,
        "password": password
    }
    
    try:
        response = api_client.post("/api/auth/register", json=register_data)
        # 如果用户已存在（409或其他错误），尝试直接更新密码
        if response.status_code not in [200, 201]:
            # 如果注册失败，尝试直接更新数据库中的密码哈希
            # 这种情况下，我们使用 Python bcrypt 生成哈希
            conn = get_db_connection()
            conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
            cursor = conn.cursor()
            password_hash = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt(rounds=10)).decode('utf-8')
            cursor.execute(
                'UPDATE "Users" SET "PasswordHash" = %s WHERE "Username" = %s',
                (password_hash, username)
            )
            cursor.close()
            conn.close()
    except Exception as e:
        print(f"通过 API 创建用户失败，尝试直接插入: {e}")
        # 如果 API 调用失败，回退到直接插入数据库
        conn = get_db_connection()
        conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
        cursor = conn.cursor()
        password_hash = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt(rounds=10)).decode('utf-8')
        cursor.execute(
            'INSERT INTO "Users" ("Username", "Email", "PasswordHash", "CreatedAt") VALUES (%s, %s, %s, CURRENT_TIMESTAMP) ON CONFLICT ("Username") DO UPDATE SET "PasswordHash" = EXCLUDED."PasswordHash"',
            (username, email, password_hash)
        )
        cursor.close()
        conn.close()


@when(parsers.parse('我使用用户名 "{username}" 和密码 "{password}" 发送登录请求'))
def send_login_request(api_client, test_context, username, password):
    """发送登录请求"""
    login_data = {
        "username": username,
        "password": password
    }
    response = api_client.post("/api/auth/login", json=login_data)
    # 将响应保存到 context 中，以便后续步骤使用
    test_context["login_response"] = response
    return response


@when('我使用用户名 "" 和密码 "admin123" 发送登录请求')
def send_login_request_empty_username(api_client, test_context):
    """发送登录请求（空用户名）"""
    login_data = {
        "username": "",
        "password": "admin123"
    }
    response = api_client.post("/api/auth/login", json=login_data)
    test_context["login_response"] = response
    return response


@when('我使用用户名 "admin" 和密码 "" 发送登录请求')
def send_login_request_empty_password(api_client, test_context):
    """发送登录请求（空密码）"""
    login_data = {
        "username": "admin",
        "password": ""
    }
    response = api_client.post("/api/auth/login", json=login_data)
    test_context["login_response"] = response
    return response


@then(parsers.parse('响应状态码应该是 {status_code:d}'))
def check_response_status_code(test_context, status_code):
    """检查响应状态码"""
    response = test_context.get("login_response")
    assert response is not None, "未找到登录响应，请先执行登录请求"
    assert response.status_code == status_code, \
        f"期望状态码 {status_code}，实际得到 {response.status_code}"


@then('响应应该包含有效的 token')
def check_response_contains_token(test_context):
    """检查响应是否包含有效的 token"""
    response = test_context.get("login_response")
    assert response is not None, "未找到登录响应，请先执行登录请求"
    assert response.status_code == 200, \
        f"登录失败，状态码: {response.status_code}"
    
    response_data = response.json()
    assert "token" in response_data, "响应中缺少 token 字段"
    assert response_data["token"], "token 不能为空"
    
    # 验证 token 格式（JWT token 通常包含三个部分，用点分隔）
    token = response_data["token"]
    parts = token.split(".")
    assert len(parts) == 3, f"token 格式不正确，应该有3个部分，实际有 {len(parts)} 个"


@then('响应应该包含用户信息')
def check_response_contains_user_info(test_context):
    """检查响应是否包含用户信息"""
    response = test_context.get("login_response")
    assert response is not None, "未找到登录响应，请先执行登录请求"
    response_data = response.json()
    assert "userId" in response_data, "响应中缺少 userId 字段"
    assert "username" in response_data, "响应中缺少 username 字段"
    assert response_data["userId"] > 0, "userId 应该大于 0"
    assert response_data["username"], "username 不能为空"


@then(parsers.parse('响应应该包含错误消息 "{error_message}"'))
def check_error_message(test_context, error_message):
    """检查错误消息"""
    response = test_context.get("login_response")
    assert response is not None, "未找到登录响应，请先执行登录请求"
    response_data = response.json()
    assert "message" in response_data, "响应中缺少 message 字段"
    assert error_message in response_data["message"], \
        f"期望错误消息包含 '{error_message}'，实际得到 '{response_data['message']}'"


@then(parsers.parse('响应状态码应该是 {status_code:d} 或 {status_code2:d}'))
def check_response_status_code_or(test_context, status_code, status_code2):
    """检查响应状态码（允许两个值）"""
    response = test_context.get("login_response")
    assert response is not None, "未找到登录响应，请先执行登录请求"
    actual_status = response.status_code
    assert actual_status in [status_code, status_code2], \
        f"期望状态码 {status_code} 或 {status_code2}，实际得到 {actual_status}"

