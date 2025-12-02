# language: zh-CN
Feature: 用户登录
  As a user
  I want to login to TodoApp
  So that I can manage my projects and todos

  Scenario: 成功登录
    假设 数据库中已存在用户 "testuser"，密码为 "password123"
    当 我访问登录页面
    并且 我输入用户名 "testuser" 和密码 "password123"
    并且 我点击登录按钮
    那么 我应该被重定向到项目列表页面
    并且 页面应该显示 "testuser" 的用户信息

  Scenario: 登录失败 - 错误的密码
    假设 数据库中已存在用户 "testuser"，密码为 "password123"
    当 我访问登录页面
    并且 我输入用户名 "testuser" 和密码 "wrongpassword"
    并且 我点击登录按钮
    那么 我应该看到错误消息 "登录失败，请检查用户名和密码"
    并且 我应该仍然在登录页面

  Scenario: 登录失败 - 不存在的用户
    当 我访问登录页面
    并且 我输入用户名 "nonexistent" 和密码 "password123"
    并且 我点击登录按钮
    那么 我应该看到错误消息 "登录失败，请检查用户名和密码"
    并且 我应该仍然在登录页面

  Scenario: 登录失败 - 空用户名
    当 我访问登录页面
    并且 我输入用户名 "" 和密码 "password123"
    并且 我点击登录按钮
    那么 我应该看到验证错误消息 "请输入用户名"
    并且 我应该仍然在登录页面

  Scenario: 登录失败 - 空密码
    假设 数据库中已存在用户 "testuser"，密码为 "password123"
    当 我访问登录页面
    并且 我输入用户名 "testuser" 和密码 ""
    并且 我点击登录按钮
    那么 我应该看到验证错误消息 "请输入密码"
    并且 我应该仍然在登录页面

