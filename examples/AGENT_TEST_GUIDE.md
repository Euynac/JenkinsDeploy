# Docker Agent 测试指南

## 测试目标

使用真实的 .NET 项目测试 Docker Agent 的构建和单元测试功能。

---

## 前提条件

- ✅ Jenkins Master 运行中
- ✅ .NET Agent 已连接（agent-dotnet-8）
- ✅ Agent 节点配置标签包含 `dotnet`

---

## 快速测试流程（5分钟）

### 步骤 1: 确认 Agent 在线

1. 访问 Jenkins: http://localhost:8080
2. 进入 `系统管理` > `节点管理`
3. 确认 `agent-dotnet-8` 状态为 **在线** ✅

### 步骤 2: 确认节点标签

在节点配置中，确认标签包含：
- `dotnet`
- `dotnet-8`
- 或其他你配置的标签

**重要**: Jenkinsfile 中使用 `label 'dotnet'`，确保节点包含此标签！

---

### 步骤 3: 创建测试 Pipeline 任务

#### 3.1 创建任务

1. Jenkins 首页 > `新建任务`
2. 任务名称: `Test-DotNet-Agent`
3. 选择: `Pipeline`
4. 点击 `确定`

#### 3.2 配置 Pipeline

在 **Pipeline** 部分：

**选项 A: 使用 SCM（推荐）**

如果代码在 Git 仓库中：

```
Definition: Pipeline script from SCM
SCM: Git
Repository URL: <你的仓库地址>
Branch: */main
Script Path: examples/Jenkinsfile-simple
```

**注意**: 需要确保代码已提交到 Git 仓库！

**选项 B: 直接粘贴脚本（快速测试）**

1. Definition: `Pipeline script`
2. 粘贴 `Jenkinsfile-simple` 的全部内容
3. **但是**: 这种方式不会自动拷贝项目代码到 workspace

---

### 步骤 4: 准备项目代码

#### 方式 1: 使用 Git 仓库（推荐）

```bash
cd /mnt/d/Repositories/JenkinsDeploy

# 提交代码到 Git
git add examples/
git commit -m "Add test project for Docker Agent"
git push
```

然后在 Jenkins Pipeline 配置中使用 SCM 方式。

#### 方式 2: 手动复制到 workspace（仅测试）

在 Pipeline 脚本开头添加复制命令：

```groovy
stage('Copy Project') {
    steps {
        sh """
            cp -r /mnt/d/Repositories/JenkinsDeploy/examples/todoapp-backend-api-main ${WORKSPACE}/
        """
    }
}
```

**注意**: 这种方式仅适用于本地测试，生产环境应使用 Git。

---

### 步骤 5: 运行构建

1. 保存 Pipeline 配置
2. 点击 `立即构建`
3. 点击构建号（如 `#1`）
4. 点击 `控制台输出`

---

## 预期输出

### 成功的构建应该显示：

```
========================================
检查构建环境...
========================================
.NET SDK 版本:
8.0.416

========================================
还原 NuGet 包依赖...
========================================
Determining projects to restore...

========================================
构建项目（包括测试项目）...
========================================
Build succeeded.

========================================
运行单元测试...
========================================
Passed!  - Failed:     0, Passed:    XX, Skipped:     0, Total:    XX

========================================
测试完成！
========================================

========================================
✅ 构建和测试全部成功！
========================================
```

---

## 验证清单

完成测试后，确认以下内容：

- [ ] Agent 节点显示 "在线"
- [ ] 构建在 Agent 上执行（检查构建日志中的 NODE_NAME）
- [ ] .NET SDK 版本正确（8.0.416）
- [ ] 依赖还原成功
- [ ] 项目编译成功
- [ ] 单元测试全部通过
- [ ] 测试报告已发布（查看 "Test Result" 页面）
- [ ] 测试产物已归档（查看 "Build Artifacts"）

---

## 常见问题

### 问题 1: Agent 无法执行构建

**症状**: 构建卡在 "Waiting for next available executor"

**解决方案**:
1. 检查 Agent 节点是否在线
2. 检查节点标签是否包含 `dotnet`
3. 检查节点是否有可用的 executor（并发构建数）

---

### 问题 2: 找不到项目目录

**症状**:
```
❌ 项目目录不存在: /home/jenkins/agent/workspace/.../todoapp-backend-api-main
```

**解决方案**:

**方案 A**: 使用 Git SCM 检出代码
- 在 Pipeline 配置中使用 "Pipeline script from SCM"
- 配置 Git 仓库地址

**方案 B**: 添加代码复制阶段
- 在 Jenkinsfile 开头添加复制项目代码的 stage（见上文）

---

### 问题 3: dotnet 命令找不到

**症状**:
```
dotnet: command not found
```

**解决方案**:

检查 Agent 容器中的 .NET SDK：

```bash
docker exec jenkins-agent-dotnet dotnet --version
```

如果输出 `8.0.416`，说明 SDK 已安装，可能是路径问题。

---

### 问题 4: 测试失败

**症状**: 单元测试执行失败

**排查步骤**:

1. 查看控制台输出的详细测试日志
2. 检查是否缺少测试数据库连接
3. 在本地运行测试验证：

```bash
cd /mnt/d/Repositories/JenkinsDeploy/examples/todoapp-backend-api-main
dotnet test
```

---

## 文件清单

测试相关文件：

```
examples/
├── todoapp-backend-api-main/          # .NET 项目源码
│   ├── TodoApp-backend.csproj         # 主项目
│   ├── TodoApp-backend.Tests/         # 测试项目
│   └── ...
├── Jenkinsfile-simple                 # 简化的测试 Pipeline
├── backend.groovy                     # 完整的生产 Pipeline（已修改）
└── AGENT_TEST_GUIDE.md               # 本文件
```

---

## 成功标准

测试成功的标准：

1. ✅ 构建在 Docker Agent 上执行
2. ✅ 所有阶段（环境检查、还原、构建、测试）全部成功
3. ✅ 单元测试全部通过（0 失败）
4. ✅ 测试报告正确发布
5. ✅ 代码覆盖率报告生成（如果配置了 Coverage 插件）

---

## 下一步

测试成功后，你可以：

1. **扩展 Pipeline**: 添加更多阶段（发布、打包、上传 Nexus 等）
2. **部署更多 Agent**: 根据 `docker-compose-agents.yml` 部署生产 Agent
3. **配置真实项目**: 使用此 Pipeline 模板构建你的 24 个微服务
4. **集成工具**: 添加 SonarQube、Allure 等质量检查工具

---

## 测试记录

完成测试后，记录以下信息：

```
测试日期: _______________
Agent 名称: agent-dotnet-8
Agent 容器: jenkins-agent-dotnet
.NET SDK: 8.0.416

✅ Agent 连接成功
✅ 环境检查通过
✅ 依赖还原成功
✅ 编译成功
✅ 单元测试通过（___ 个测试）
✅ 测试报告发布成功

构建时间: _____ 秒
测试执行时间: _____ 秒

测试人员: _______________
```

---

## 参考文档

- [Docker Socket 权限配置](../agents/DOCKER_SOCKET_CONFIG.md)
- [项目主文档](../README.md)

---

**测试时间**: 约 5-10 分钟
**推荐指数**: ⭐⭐⭐⭐⭐（验证 Docker Agent 功能）
