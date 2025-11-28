# .NET 8 Sample Project

这是一个用于测试 Jenkins .NET Agent 的示例项目。

## 项目结构

```
dotnet-sample/
├── Controllers/
│   └── HealthController.cs    # 健康检查 API
├── DotNetSample.csproj         # 项目文件
├── Program.cs                  # 应用程序入口
├── appsettings.json            # 配置文件
├── Jenkinsfile                 # Jenkins Pipeline 脚本
└── README.md                   # 本文件
```

## 本地测试

### 1. 还原依赖

```bash
dotnet restore
```

### 2. 编译项目

```bash
dotnet build --configuration Release
```

### 3. 运行项目

```bash
dotnet run
```

### 4. 测试 API

```bash
# 健康检查
curl http://localhost:5000/api/health

# 系统信息
curl http://localhost:5000/api/health/info
```

## Jenkins 部署步骤

### 步骤 1: 启动 .NET Agent

```bash
cd /mnt/d/Repositories/JenkinsDeploy

# 1. 在 Jenkins Web UI 中创建节点
#    - 节点名称: agent-dotnet-test
#    - 标签: dotnet-test
#    - 启动方式: Launch agent by connecting it to the controller

# 2. 复制 Secret 并修改 docker-compose-test-dotnet.yml

# 3. 启动 Agent
docker-compose -f docker-compose-test-dotnet.yml up -d

# 4. 查看日志，确认连接成功
docker-compose -f docker-compose-test-dotnet.yml logs -f
```

### 步骤 2: 在 Jenkins 中创建 Pipeline 任务

1. 进入 Jenkins 首页
2. 点击 "新建任务"
3. 输入任务名称: `DotNet-Sample-Test`
4. 选择 "Pipeline"
5. 点击 "确定"

### 步骤 3: 配置 Pipeline

在 Pipeline 配置中：

**方式 1: Pipeline script from SCM**（推荐）

如果你的代码在 Git 仓库中：
- Definition: `Pipeline script from SCM`
- SCM: `Git`
- Repository URL: 你的 Git 仓库地址
- Script Path: `test-projects/dotnet-sample/Jenkinsfile`

**方式 2: Pipeline script**（快速测试）

直接粘贴 `Jenkinsfile` 的内容到 Pipeline 脚本框中。

### 步骤 4: 运行构建

1. 点击 "立即构建"
2. 查看控制台输出
3. 验证构建是否成功

## 预期输出

构建成功后，你会看到：

```
========== 环境信息 ==========
8.0.416
8.0.416 [/usr/share/dotnet/sdk]
git version 2.47.3

========== 还原 NuGet 包 ==========
...

========== 编译项目 ==========
...

========== 发布应用程序 ==========
...

========== 验证发布产物 ==========
✅ 主程序文件存在

✅ 构建成功！
```

## 故障排查

### 问题 1: Agent 无法连接

```bash
# 查看 Agent 日志
docker logs jenkins-agent-dotnet-test

# 检查网络
docker exec jenkins-agent-dotnet-test ping jenkins-master

# 检查环境变量
docker exec jenkins-agent-dotnet-test env | grep JENKINS
```

### 问题 2: 构建失败 - dotnet 命令找不到

检查 Agent 镜像是否正确：

```bash
docker exec jenkins-agent-dotnet-test dotnet --version
```

### 问题 3: NuGet 还原失败

可能是网络问题或需要配置 Nexus：

```bash
# 在 Agent 容器中测试网络
docker exec jenkins-agent-dotnet-test curl -I https://api.nuget.org/v3/index.json
```

## API 端点

项目包含以下 API 端点：

### 1. 健康检查

```bash
GET /api/health

返回:
{
  "status": "healthy",
  "timestamp": "2025-11-26T09:00:00Z",
  "version": "1.0.0",
  "framework": ".NET 8.0"
}
```

### 2. 系统信息

```bash
GET /api/health/info

返回:
{
  "application": "DotNet Sample API",
  "environment": "Production",
  "machineName": "jenkins-agent-dotnet-test",
  "osVersion": "Unix 6.6.87.2",
  "processorCount": 4,
  "dotnetVersion": "8.0.11"
}
```

## 下一步

如果测试成功，你可以：

1. ✅ 部署更多 .NET Agent 容器
2. ✅ 继续构建 Java Agent（修复 Maven 下载问题）
3. ✅ 构建 Vue Agent
4. ✅ 配置生产环境的 Pipeline
5. ✅ 集成 SonarQube 代码质量检查

## 参考文档

- [Docker Agent 快速指南](../../DOCKER_AGENT_QUICKSTART.md)
- [Docker Agent 详细指南](../../DOCKER_AGENT_GUIDE.md)
- [方案对比分析](../../AGENT_DEPLOYMENT_COMPARISON.md)
