# Docker Agent 测试项目

这个目录包含用于测试 Jenkins Docker Agent 的完整测试环境和脚本。

## 📁 文件结构

```
examples/
├── AGENT_TEST_GUIDE.md             # 📖 详细测试指南
├── quick-test-pipeline.groovy      # 🔧 快速测试脚本（推荐）
├── Jenkinsfile-simple              # 📋 简化的 Jenkinsfile
├── backend.groovy                  # 🏭 完整的生产 Pipeline（已简化）
├── frontend.groovy                 # 🎨 前端 Pipeline
├── teacher-version.sln             # 📋 解决方案文件
├── README.md                       # 本文件
├── todoapp-backend-api-main/       # 📦 .NET 测试项目
├── todoapp-backend-api-e2etest-main/ # 🧪 后端 E2E 测试项目
├── todoapp-frontend-vue2-main/     # 🎨 前端测试项目
└── todoapp-frontend-ui-e2etest/    # 🧪 前端 E2E 测试项目
```

## 🎯 快速开始

1. **阅读**: 查看下方的测试脚本说明
2. **创建**: 在 Jenkins 中创建 Pipeline 任务
3. **粘贴**: 使用 `quick-test-pipeline.groovy` 的内容
4. **运行**: 立即构建并查看结果

**总耗时**: 2-5 分钟

---

## 📖 测试脚本说明

### 1. quick-test-pipeline.groovy (推荐) ⭐

**用途**: 快速测试 Docker Agent 基本功能

**特点**:
- ✅ 自动复制测试项目到 workspace
- ✅ 无需 Git 仓库
- ✅ 包含完整的构建和测试流程
- ✅ 适合本地快速测试

**阶段**:
1. 复制项目到工作空间
2. 环境检查（.NET SDK）
3. 还原依赖（dotnet restore）
4. 构建项目（dotnet build）
5. 运行单元测试（dotnet test）

**使用方式**: 直接复制粘贴到 Jenkins Pipeline 脚本

---

### 2. Jenkinsfile-simple

**用途**: 标准的 Jenkinsfile 测试

**特点**:
- ✅ 标准 Jenkinsfile 格式
- ✅ 适合 Git 仓库使用
- ✅ 包含测试报告发布
- ⚠️ 需要项目代码在 workspace 中

**阶段**:
1. 环境检查
2. 准备环境
3. 还原依赖
4. 构建项目
5. 运行单元测试

**使用方式**: Pipeline script from SCM

---

### 3. backend.groovy

**用途**: 完整的生产级 Pipeline

**特点**:
- 原始的完整 Pipeline
- 已简化为仅包含构建和测试
- 包含注释掉的其他阶段（E2E、SonarQube、Nexus 等）
- 适合学习和参考

**原始阶段**:
1. ✅ Checkout（已简化）
2. ✅ Setup Environment
3. ✅ Restore
4. ✅ Build
5. ✅ Unit Test
6. ❌ E2E Tests（已注释）
7. ❌ SonarQube Analysis（已注释）
8. ❌ Publish（已注释）
9. ❌ Package（已注释）
10. ❌ Archive Artifacts（已注释）
11. ❌ Deploy to Nexus（已注释）

---

## 🔧 环境配置

### 当前 Agent 状态

```bash
# 检查 Agent 连接
docker logs jenkins-agent-dotnet-test --tail=5

# 检查挂载的测试项目
docker exec jenkins-agent-dotnet-test ls -la /test-projects/

# 检查 .NET SDK
docker exec jenkins-agent-dotnet-test dotnet --version
```

### Docker Compose 配置

文件: `/mnt/d/Repositories/JenkinsDeploy/docker-compose-test-dotnet.yml`

**关键配置**:
- Agent 名称: `agent-dotnet-8`
- 标签: `dotnet`
- 挂载: `./examples:/test-projects:ro`
- .NET SDK: 8.0.416

---

## 📊 测试项目说明

### todoapp-backend-api-main

**类型**: ASP.NET Core Web API

**技术栈**:
- .NET 8.0
- Entity Framework Core
- PostgreSQL（生产环境）
- xUnit（单元测试）

**项目结构**:
```
todoapp-backend-api-main/
├── TodoApp-backend.csproj          # 主项目
├── Program.cs                      # 入口
├── Controllers/                    # API 控制器
├── Models/                         # 数据模型
├── Services/                       # 业务逻辑
├── Data/                           # 数据访问
└── TodoApp-backend.Tests/         # 单元测试
    └── TodoApp-backend.Tests.csproj
```

**测试覆盖**:
- 单元测试
- 控制器测试
- 服务层测试

---

## ✅ 验证清单

完成测试后，确认以下内容：

- [ ] Agent 节点 `agent-dotnet-8` 在线
- [ ] 构建在 Docker Agent 上执行
- [ ] .NET SDK 8.0.416 可用
- [ ] 测试项目成功挂载
- [ ] 依赖还原成功（NuGet 包）
- [ ] 项目编译成功（无错误）
- [ ] 单元测试全部通过（0 失败）
- [ ] 测试报告正确发布
- [ ] 构建产物已归档

---

## 🐛 常见问题

### 1. Agent 无法连接

**症状**: 日志显示连接失败

**解决**:
```bash
# 检查容器状态
docker ps | grep jenkins-agent

# 查看日志
docker logs jenkins-agent-dotnet-test

# 重启容器
docker-compose -f docker-compose-test-dotnet.yml restart
```

### 2. 找不到 dotnet 标签

**症状**: "There are no nodes with the label 'dotnet'"

**解决**: Jenkins > 节点管理 > agent-dotnet-8 > 配置 > 添加标签 `dotnet`

### 3. 源目录不存在

**症状**: "❌ 源目录不存在: /test-projects"

**解决**: 检查 docker-compose 配置中的 volumes 挂载

### 4. 单元测试失败

**症状**: 测试执行失败

**解决**:
1. 查看详细日志
2. 在本地运行测试验证
3. 检查数据库连接配置

---

## 📚 参考文档

- [详细测试指南](AGENT_TEST_GUIDE.md) - 完整测试流程
- [Docker Socket 权限配置](../agents/DOCKER_SOCKET_CONFIG.md) - Docker 配置

---

## 📝 测试记录

```
测试日期: _______________
测试人员: _______________

环境信息:
- Agent 名称: agent-dotnet-8
- .NET SDK: 8.0.416
- 项目: TodoApp-backend

测试结果:
- [ ] Agent 连接成功
- [ ] 环境检查通过
- [ ] 依赖还原成功
- [ ] 编译成功
- [ ] 单元测试通过（___ 个）
- [ ] 测试报告发布

构建时间: _____ 秒
测试数量: _____ 个
覆盖率: _____

备注:
____________________________________
____________________________________
```

---

## 🎉 成功标准

测试通过的标准：

1. ✅ 构建在 Docker Agent 上成功执行
2. ✅ 所有阶段无错误完成
3. ✅ 单元测试 100% 通过
4. ✅ 测试报告正确发布
5. ✅ 构建时间合理（< 5 分钟）

---

**推荐指数**: ⭐⭐⭐⭐⭐
**难度等级**: ⭐⭐（简单）
**预计时间**: 5-10 分钟
