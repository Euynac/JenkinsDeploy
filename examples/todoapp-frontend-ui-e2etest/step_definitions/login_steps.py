"""
用户登录功能的步骤定义（Selenium UI 测试）
"""
import os
import time
import json
import psycopg2
from psycopg2.extensions import ISOLATION_LEVEL_AUTOCOMMIT
import bcrypt
import requests
from pytest_bdd import given, when, then, parsers, scenarios
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException
import pytest
from conftest import get_db_connection, API_BASE_URL, FRONTEND_BASE_URL

# 使用 scenarios() 加载 feature 文件
# 注意：由于 pytest.ini 中配置了 bdd_features_base_dir = features
# 所以这里只需要提供相对于 features 目录的路径
scenarios("login.feature")


@pytest.fixture(scope="function")
def test_context():
    """测试上下文，用于在步骤之间共享数据"""
    return {}


@given(parsers.parse('数据库中已存在用户 "{username}"，密码为 "{password}"'))
def create_user_in_database(username, password, api_client):
    """在数据库中创建用户"""
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
            cursor.execute('DELETE FROM "Users" WHERE "Username" = %s', (username,))
        
        cursor.close()
    except Exception as e:
        print(f"清理用户失败: {e}")
    finally:
        if conn:
            conn.close()
    
    # 使用 API 注册接口创建用户
    email = f"{username}@example.com"
    register_data = {
        "username": username,
        "email": email,
        "password": password
    }
    
    try:
        response = api_client.post("/api/auth/register", json=register_data)
        if response.status_code not in [200, 201]:
            # 如果注册失败，尝试直接插入数据库
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
    except Exception as e:
        print(f"通过 API 创建用户失败，尝试直接插入: {e}")
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


@when('我访问登录页面')
def visit_login_page(driver, test_context):
    """访问登录页面"""
    driver.get(f"{FRONTEND_BASE_URL}/login")
    # 等待页面加载 - Element UI 的登录容器
    WebDriverWait(driver, 10).until(
        EC.presence_of_element_located((By.CSS_SELECTOR, ".login-container"))
    )
    test_context["current_url"] = driver.current_url


@when(parsers.parse('我输入用户名 "{username}" 和密码 "{password}"'))
def enter_credentials(driver, test_context, username, password):
    """输入用户名和密码"""
    # Element UI 的输入框结构：el-input > input
    # 查找用户名输入框
    username_input = WebDriverWait(driver, 10).until(
        EC.presence_of_element_located((By.CSS_SELECTOR, "input[placeholder='用户名']"))
    )
    username_input.clear()
    username_input.send_keys(username)
    
    # 查找密码输入框
    password_input = driver.find_element(By.CSS_SELECTOR, "input[type='password']")
    password_input.clear()
    password_input.send_keys(password)
    
    test_context["username"] = username
    test_context["password"] = password


@when('我点击登录按钮')
def click_login_button(driver, test_context):
    """点击登录按钮"""
    # Element UI 的按钮：el-button.login-button
    login_button = WebDriverWait(driver, 10).until(
        EC.element_to_be_clickable((By.CSS_SELECTOR, ".login-button"))
    )
    login_button.click()
    
    # 等待页面响应（可能是成功跳转或错误消息）
    time.sleep(2)  # 给前端一些时间处理


@then('我应该被重定向到项目列表页面')
def should_be_redirected_to_projects(driver, test_context):
    """验证是否重定向到项目列表页面"""
    # 等待 URL 变化
    try:
        WebDriverWait(driver, 10).until(
            lambda d: "/login" not in d.current_url
        )
        # 检查是否在项目页面（通过检查页面元素）
        # 等待页面加载完成
        time.sleep(1)
        # 检查是否不在登录页面
        current_url = driver.current_url
        assert "/login" not in current_url, f"仍然在登录页面，当前 URL: {current_url}"
    except TimeoutException:
        # 如果超时，检查当前 URL
        current_url = driver.current_url
        assert "/login" not in current_url, f"未重定向，当前 URL: {current_url}"


@then(parsers.parse('页面应该显示 "{username}" 的用户信息'))
def should_display_user_info(driver, test_context, username):
    """验证页面显示用户信息"""
    # 检查 localStorage（通过 JavaScript）
    user_info = driver.execute_script("return localStorage.getItem('user');")
    assert user_info is not None, "localStorage 中未找到用户信息"
    
    user_data = json.loads(user_info)
    assert user_data["username"] == username, \
        f"用户名不匹配，期望: {username}, 实际: {user_data.get('username')}"


@then(parsers.parse('我应该看到错误消息 "{error_message}"'))
def should_see_error_message(driver, test_context, error_message):
    """验证错误消息"""
    # Element UI 的错误消息通常显示在 el-message 中
    # 等待错误消息出现（Element UI 的 message 组件）
    try:
        # Element UI 的 message 会动态添加到 body，查找 .el-message
        error_element = WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, ".el-message, .el-message__content"))
        )
        error_text = error_element.text
        assert error_message in error_text, \
            f"错误消息不匹配，期望包含: {error_message}, 实际: {error_text}"
    except TimeoutException:
        # 如果找不到错误元素，检查页面文本
        page_text = driver.find_element(By.TAG_NAME, "body").text
        assert error_message in page_text, \
            f"页面中未找到错误消息: {error_message}，页面文本: {page_text[:200]}"


@then('我应该仍然在登录页面')
def should_still_be_on_login_page(driver, test_context):
    """验证仍然在登录页面"""
    current_url = driver.current_url
    assert "/login" in current_url or current_url.endswith("/login"), \
        f"不在登录页面，当前 URL: {current_url}"


@then(parsers.parse('我应该看到验证错误消息 "{error_message}"'))
def should_see_validation_error(driver, test_context, error_message):
    """验证表单验证错误消息"""
    # Element UI 的表单验证错误通常显示在 el-form-item__error 中
    try:
        # 等待验证错误消息出现
        error_element = WebDriverWait(driver, 5).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, ".el-form-item__error"))
        )
        error_text = error_element.text
        assert error_message in error_text, \
            f"验证错误消息不匹配，期望包含: {error_message}, 实际: {error_text}"
    except TimeoutException:
        # 如果找不到验证错误，可能需要触发验证
        # 尝试点击登录按钮来触发验证
        try:
            login_button = driver.find_element(By.CSS_SELECTOR, ".login-button")
            login_button.click()
            time.sleep(1)
            # 再次查找错误消息
            error_element = driver.find_element(By.CSS_SELECTOR, ".el-form-item__error")
            error_text = error_element.text
            assert error_message in error_text, \
                f"验证错误消息不匹配，期望包含: {error_message}, 实际: {error_text}"
        except:
            # 如果还是找不到，检查页面文本
            page_text = driver.find_element(By.TAG_NAME, "body").text
            assert error_message in page_text, \
                f"页面中未找到验证错误消息: {error_message}"

