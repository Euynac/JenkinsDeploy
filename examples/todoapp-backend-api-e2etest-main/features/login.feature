# language: zh-CN
Feature: 用户登录
  As a system user
  I want to login to the system
  So that I can access my todos

  Scenario: 用户使用正确的用户名和密码登录 (admin)
    Given 数据库中已存在用户 "admin"，密码为 "admin123"
    When 我使用用户名 "admin" 和密码 "admin123" 发送登录请求
    Then 响应状态码应该是 200
    And 响应应该包含有效的 token
    And 响应应该包含用户信息

  Scenario: 用户使用正确的用户名和密码登录 (test)
    Given 数据库中已存在用户 "test"，密码为 "test123"
    When 我使用用户名 "test" 和密码 "test123" 发送登录请求
    Then 响应状态码应该是 200
    And 响应应该包含有效的 token
    And 响应应该包含用户信息

  Scenario: 用户使用错误的用户名登录
    Given 数据库中已存在用户 "admin"，密码为 "admin123"
    When 我使用用户名 "wronguser" 和密码 "admin123" 发送登录请求
    Then 响应状态码应该是 401
    And 响应应该包含错误消息 "用户名或密码错误"

  Scenario: 用户使用错误的密码登录
    Given 数据库中已存在用户 "admin"，密码为 "admin123"
    When 我使用用户名 "admin" 和密码 "wrongpassword" 发送登录请求
    Then 响应状态码应该是 401
    And 响应应该包含错误消息 "用户名或密码错误"

  Scenario: 用户使用空的用户名登录
    When 我使用用户名 "" 和密码 "admin123" 发送登录请求
    Then 响应状态码应该是 400 或 401

  Scenario: 用户使用空的密码登录
    When 我使用用户名 "admin" 和密码 "" 发送登录请求
    Then 响应状态码应该是 400 或 401
