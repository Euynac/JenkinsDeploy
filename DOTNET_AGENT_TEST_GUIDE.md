# .NET Agent 测试指南

## 快速测试流程（10分钟）

### 前提条件

- ✅ Jenkins Master 已运行
- ✅ .NET Agent 镜像已构建（jenkins-agent-dotnet:1.0）
- ✅ Docker 和 Docker Compose 已安装

---

## 步骤 1: 在 Jenkins 中创建 Agent 节点（3分钟）

### 1.1 创建节点

1. 登录 Jenkins Web UI
2. 进入 `系统管理` > `节点管理`
3. 点击 `新建节点`

### 1.2 配置节点

```
节点名称: agent-dotnet-test
类型: 永久代理

配置:
- 并发构建数: 2
- 远程工作目录: /home/jenkins/agent
- 标签: dotnet-test dotnet net8
- 用法: 尽可能使用这个节点
- 启动方式: Launch agent by connecting it to the controller
```

### 1.3 获取 Secret

保存后，Jenkins 会显示连接命令，其中包含 Secret：

```bash
# 示例（注意：这不是真实 Secret）
java -jar agent.jar -url http://jenkins-master:8080/ -secret abc123def456... -name agent-dotnet-test
```

复制 `-secret` 后面的值（例如：`abc123def456...`）

---

## 步骤 2: 启动 .NET Agent 容器（2分钟）

### 2.1 修改配置文件

编辑 `docker-compose-test-dotnet.yml`:

```yaml
# 找到这一行：
JENKINS_SECRET: "YOUR_SECRET_HERE"

# 替换为你的 Secret：
JENKINS_SECRET: "abc123def456..."
```

### 2.2 启动容器

```bash
cd /mnt/d/Repositories/JenkinsDeploy

# 启动 Agent
docker-compose -f docker-compose-test-dotnet.yml up -d

# 查看日志（等待连接成功）
docker-compose -f docker-compose-test-dotnet.yml logs -f
```

### 2.3 验证连接

预期日志输出：

```
jenkins-agent-dotnet-test | INFO: Connected
jenkins-agent-dotnet-test | INFO: Agent is connected
```

在 Jenkins Web UI 中，节点状态应该显示为 "在线"。

---

## 步骤 3: 创建测试 Pipeline（2分钟）

### 3.1 创建任务

1. 回到 Jenkins 首页
2. 点击 `新建任务`
3. 输入名称: `DotNet-Agent-Test`
4. 选择 `Pipeline`
5. 点击 `确定`

### 3.2 配置 Pipeline

在 Pipeline 配置页面：

**方式 1: 使用 Git 仓库（推荐）**

```
Definition: Pipeline script from SCM
SCM: Git
Repository URL: <你的Git仓库地址>
Credentials: <如需要>
Branch: */main
Script Path: test-projects/dotnet-sample/Jenkinsfile
```

**方式 2: 直接粘贴脚本（快速测试）**

1. Definition: `Pipeline script`
2. 粘贴 `test-projects/dotnet-sample/Jenkinsfile` 的内容
3. 注意：需要将项目文件也上传到 Git 仓库或手动放到 Agent 工作目录

---

## 步骤 4: 运行构建（3分钟）

### 4.1 开始构建

1. 点击 `立即构建`
2. 点击构建号（例如 `#1`）
3. 点击 `控制台输出`

### 4.2 观察构建过程

你会看到以下阶段：

```
========== 环境信息 ==========
8.0.416

========== 清理旧的构建产物 ==========

========== 还原 NuGet 包 ==========
Determining projects to restore...
Restored /home/jenkins/agent/workspace/...

========== 编译项目 ==========
Build succeeded.

========== 发布应用程序 ==========
DotNetSample -> /home/jenkins/agent/workspace/.../publish/

========== 验证发布产物 ==========
✅ 主程序文件存在

========================================
✅ 构建成功！
========================================
```

### 4.3 检查产物

在任务页面，点击 `最近构建` > `构建产物`，应该能看到：

```
publish/
├── DotNetSample.dll
├── DotNetSample.pdb
├── appsettings.json
├── ...
```

---

## 验证检查清单

完成以上步骤后，确认以下内容：

- [ ] Jenkins 节点 `agent-dotnet-test` 状态为 "在线"
- [ ] Agent 容器日志显示 "Connected"
- [ ] Pipeline 任务创建成功
- [ ] 构建执行成功（绿色勾）
- [ ] 控制台输出显示 "✅ 构建成功！"
- [ ] 构建产物已归档
- [ ] 能看到 `publish/DotNetSample.dll` 文件

---

## 常见问题

### 问题 1: Agent 无法连接

**症状**: 容器日志显示连接失败

**排查**:

```bash
# 检查网络连通性
docker exec jenkins-agent-dotnet-test ping jenkins-master

# 检查环境变量
docker exec jenkins-agent-dotnet-test env | grep JENKINS

# 重启容器
docker-compose -f docker-compose-test-dotnet.yml restart
```

**解决方案**:
- 确认 Secret 正确
- 确认网络 `jenkins-network` 存在
- 确认 Master 和 Agent 在同一网络

---

### 问题 2: Pipeline 找不到 Agent

**症状**: 构建卡在 "等待可用的 executor"

**排查**:

```
# 检查节点标签
Jenkins > 系统管理 > 节点管理 > agent-dotnet-test > 配置

# 确认标签包含: dotnet-test
```

**解决方案**:

在 Jenkinsfile 中使用正确的 label：

```groovy
agent {
    label 'dotnet-test'  // 确保与节点标签匹配
}
```

---

### 问题 3: dotnet 命令找不到

**症状**: 构建失败，提示 `dotnet: command not found`

**排查**:

```bash
# 进入容器验证
docker exec -it jenkins-agent-dotnet-test bash

# 检查 .NET SDK
dotnet --version
```

**解决方案**:
- 确认使用正确的镜像 `jenkins-agent-dotnet:1.0`
- 重新构建镜像（如果之前构建失败）

---

### 问题 4: NuGet 还原失败

**症状**: 还原依赖时超时或 404

**排查**:

```bash
# 测试网络
docker exec jenkins-agent-dotnet-test curl -I https://api.nuget.org/v3/index.json
```

**解决方案**:

如果内网有 Nexus，配置 NuGet 源：

```bash
# 在 Agent 容器中
docker exec jenkins-agent-dotnet-test bash -c "cat > /home/jenkins/.nuget/NuGet/NuGet.Config <<EOF
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<configuration>
  <packageSources>
    <clear />
    <add key=\"Nexus\" value=\"http://nexus.internal.com/repository/nuget-group/\" />
  </packageSources>
</configuration>
EOF"

# 重启构建
```

---

## 性能基准

### 首次构建（冷缓存）

```
环境检查:    5s
清理:        1s
还原依赖:    45s  ← 下载 NuGet 包
编译:        15s
发布:        5s
验证产物:    2s
归档产物:    3s
--------------------
总计:        ~76s
```

### 后续构建（热缓存）

```
环境检查:    5s
清理:        1s
还原依赖:    3s   ← 使用缓存
编译:        8s
发布:        3s
验证产物:    2s
归档产物:    3s
--------------------
总计:        ~25s
```

---

## 下一步

测试成功后，你可以：

### 1. 部署生产 .NET Agent

```bash
# 修改 docker-compose-agents.yml
# 启动多个 .NET Agent
docker-compose -f docker-compose-agents.yml up -d agent-dotnet-01 agent-dotnet-02
```

### 2. 构建真实项目

将你的 24 个微服务项目配置为 Pipeline，使用 .NET Agent 构建。

### 3. 继续构建其他 Agent

修复 Java Agent 的 Maven 下载问题，继续构建 Vue Agent。

### 4. 集成代码质量检查

添加 SonarQube 扫描到 Pipeline：

```groovy
stage('代码质量检查') {
    steps {
        sh '''
            dotnet sonarscanner begin \
                /k:"project-key" \
                /d:sonar.host.url="http://sonarqube:9000"

            dotnet build

            dotnet sonarscanner end
        '''
    }
}
```

---

## 测试结果记录

完成测试后，记录以下信息：

```
测试日期: _______________
Agent 版本: jenkins-agent-dotnet:1.0
.NET SDK: 8.0.416

✅ Agent 连接成功
✅ 环境检查通过
✅ 编译成功
✅ 发布成功
✅ 产物归档成功

首次构建时间: _____ 秒
后续构建时间: _____ 秒

测试人员: _______________
```

---

## 参考文档

- [测试项目 README](test-projects/dotnet-sample/README.md)
- [Docker Compose 配置](docker-compose-test-dotnet.yml)
- [Jenkinsfile](test-projects/dotnet-sample/Jenkinsfile)
- [Docker Agent 快速指南](DOCKER_AGENT_QUICKSTART.md)
- [方案对比分析](AGENT_DEPLOYMENT_COMPARISON.md)

---

**测试时间**: 约 10 分钟
**推荐指数**: ⭐⭐⭐⭐⭐
