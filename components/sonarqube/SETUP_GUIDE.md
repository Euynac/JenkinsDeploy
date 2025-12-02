# SonarQube 集成测试指南

## 🎯 目标

在 Jenkins Docker Agent 环境中运行完整的 CI/CD 流程，包括：
- ✅ 单元测试 + 代码覆盖率
- ✅ E2E 测试
- ✅ SonarQube 代码质量分析
- ✅ Docker 镜像构建

## 📋 前置条件

1. Jenkins Master 已启动并运行
2. .NET Docker Agent 已构建并运行
3. 测试项目已挂载到 Agent 容器

## ⚠️ 重要：代理配置检查

**如果你的 Jenkins Agent 使用了 HTTP 代理**，务必确保 `NO_PROXY` 包含 SonarQube 服务，否则会遇到 502 Bad Gateway 错误！

检查 `agents/docker-compose-test-dotnet.yml` 中的代理配置：

```yaml
environment:
  NO_PROXY: "localhost,127.0.0.1,jenkins-master-test,sonarqube,sonarqube-db,172.16.0.0/12,192.168.0.0/16,172.19.0.0/16"
  no_proxy: "localhost,127.0.0.1,jenkins-master-test,sonarqube,sonarqube-db,172.16.0.0/12,192.168.0.0/16,172.19.0.0/16"
```

**必须包含**：
- `sonarqube` - SonarQube 服务器主机名
- `172.19.0.0/16` - SonarQube 网络 CIDR

详见故障排查部分的"问题 0: HTTP 代理导致连接失败"。

## 🚀 快速启动流程

### 第 1 步: 启动 SonarQube

```bash
# 进入 SonarQube 目录
cd /mnt/d/Repositories/JenkinsDeploy/components/sonarqube

# 启动服务（首次启动需要 2-3 分钟）
./start.sh

# 等待输出显示 "✅ SonarQube 启动成功！"
```

**重要提示**:
- SonarQube 需要至少 2GB RAM
- 在 Linux 上可能需要设置 `vm.max_map_count=262144`
- 启动完成后访问: http://localhost:9000

### 第 2 步: 配置 SonarQube（首次使用）

#### 2.1 登录并修改密码

1. 访问 http://localhost:9000
2. 使用默认账号登录:
   - 账号: `admin`
   - 密码: `admin`
3. 系统会要求修改密码，设置新密码（例如: `admin123`）

#### 2.2 创建项目分析 Token

1. 点击右上角头像 → **My Account**
2. 选择 **Security** 标签
3. 在 **Generate Tokens** 部分填写:
   - **Token Name**: `jenkins-todoapp-backend`
   - **Type**: `Project Analysis Token`
   - **Expires in**: `No expiration` 或 `30 days`
4. 点击 **Generate**
5. **立即复制并保存 Token**（只显示一次！）

示例 Token (仅供参考):
```
squ_1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p
```

### 第 3 步: 在 Jenkins 中配置 SonarQube

#### 3.1 配置 SonarQube 服务器

1. 访问 Jenkins: http://localhost:8080
2. 进入: **Manage Jenkins** → **Configure System**
3. 滚动到 **SonarQube servers** 部分
4. 点击 **Add SonarQube**
5. 配置:
   - **Name**: `sonarqube-server` ⚠️ 必须与 Pipeline 中的名称一致
   - **Server URL**: `http://sonarqube:9000` （Docker 网络内部访问）
   - **Server authentication token**:
     - 点击 **Add** → **Jenkins**
     - Kind: `Secret text`
     - Secret: 粘贴步骤 2.2 中生成的 Token
     - ID: `sonarqube-token`
     - Description: `SonarQube Token for TodoApp Backend`
   - 在下拉菜单中选择��创建的凭证
6. 点击 **Save**

#### 3.2 验证配置

在终端中测试连接:

```bash
# 从 Jenkins Agent 容器内测试
docker exec jenkins-agent-dotnet-test curl -I http://sonarqube:9000

# 应该返回 HTTP/1.1 200 或 302
```

### 第 4 步: 重启 Jenkins Agent（连接到 SonarQube 网络）

```bash
# 停止现有 Agent
cd /mnt/d/Repositories/JenkinsDeploy/agents
docker compose -f docker-compose-test-dotnet.yml down

# 启动 Agent（现在会连接到 sonarqube-network）
docker compose -f docker-compose-test-dotnet.yml up -d

# 验证网络连接
docker network inspect sonarqube-network

# 应该能看到 jenkins-agent-dotnet-test 容器
```

### 第 5 步: 运行 Pipeline 测试

#### 5.1 创建 Pipeline Job

1. Jenkins 主页 → **新建 Item**
2. 输入名称: `TodoApp-Backend-QuickTest-WithSonarQube`
3. 选择: **Pipeline**
4. 点击 **确定**

#### 5.2 配置 Pipeline

在 **Pipeline** 部分:
- **Definition**: `Pipeline script from SCM`
- **SCM**: `Git`
- **Repository URL**: `/test-projects` 或实际仓库 URL
- **Script Path**: `quick-test-pipeline.groovy`

或者直接粘贴脚本:
- **Definition**: `Pipeline script`
- 复制 `/mnt/d/Repositories/JenkinsDeploy/examples/quick-test-pipeline.groovy` 的全部内容

#### 5.3 运行构建

1. 点击 **Build Now**
2. 观察构建日志

### 第 6 步: 查看结果

#### 6.1 Jenkins 构建结果

构建成功后，你应该看到:

```
========================================
✅ Docker Agent 测试成功！
========================================
项目: TodoApp-backend
Agent: agent-dotnet-8
构建号: 1

测试结果:
- 环境检查: ✅
- 依赖还原: ✅
- 项目编译: ✅
- 单元测试: ✅
- E2E 测试: ✅
- SonarQube 代码扫描: ✅
- Docker 镜像: ✅

SonarQube 报告:
- 访问地址: http://localhost:9000
- 项目键: todoapp-backend
- 项目名称: TodoApp Backend API
========================================
```

#### 6.2 SonarQube 分析报告

1. 访问 http://localhost:9000
2. 在主页查看项目列表，找到 `todoapp-backend`（项目键）或 `TodoApp Backend API`（项目名称）
3. 点击项目查看详细报告:
   - **Overview**: 总体质量概览
     - **Quality Gate**: 质量门禁状态（Passed/Failed）
     - **Bugs**: 发现的 Bug 数量
     - **Vulnerabilities**: 安全漏洞
     - **Code Smells**: 代码异味
     - **Coverage**: 代码覆盖率
     - **Duplications**: 重复代码
   - **Issues**: 所有问题的详细列表
   - **Measures**: 各项指标详情
   - **Code**: 浏览代码（带问题标注）
   - **Activity**: 历史分析记录和趋势图

#### 6.3 验证覆盖率数据

在 SonarQube 报告中，应该能看到:
- **Coverage**: 显示实际的代码覆盖率百分比（如 75.2%）
- 如果显示 0.0%，说明覆盖率文件未正确导入

## 🔍 故障排查

### ⚠️ 问题 0: HTTP 代理导致 502 Bad Gateway（最常见！）

**错误信息**:
```
Downloading from http://sonarqube:9000/api/server/version failed. Http status code is BadGateway.
An error occured while querying the server version!
```

**根本原因**: Jenkins Agent 配置了 HTTP 代理，但 `NO_PROXY` 没有包含 SonarQube

**诊断步骤**:
```bash
# 1. 检查 Agent 是否使用代理
docker exec jenkins-agent-dotnet-test env | grep -i proxy

# 2. 测试 SonarQube 连接（注意是否经过代理）
docker exec jenkins-agent-dotnet-test curl -v http://sonarqube:9000/api/server/version

# 如果看到 "Uses proxy env variable http_proxy"，说明请求被发送到代理了
```

**解决方案**:

1. 编辑 `agents/docker-compose-test-dotnet.yml`：
   ```yaml
   environment:
     NO_PROXY: "localhost,127.0.0.1,jenkins-master-test,sonarqube,sonarqube-db,172.16.0.0/12,192.168.0.0/16,172.19.0.0/16"
     no_proxy: "localhost,127.0.0.1,jenkins-master-test,sonarqube,sonarqube-db,172.16.0.0/12,192.168.0.0/16,172.19.0.0/16"
   ```

2. 重启 Agent：
   ```bash
   cd /path/to/agents
   docker compose -f docker-compose-test-dotnet.yml restart
   ```

3. 验证修复：
   ```bash
   # 应该看到 "no_proxy" 包含 sonarqube，并且直接连接（不经过代理）
   docker exec jenkins-agent-dotnet-test curl -v http://sonarqube:9000/api/server/version

   # 应该返回 HTTP/1.1 200 和版本号
   docker exec jenkins-agent-dotnet-test curl -s http://sonarqube:9000/api/server/version
   ```

**详细说明**: 参考 `components/sonarqube/README.md` 中的"问题 0"

---

### 问题 1: SonarQube Analysis 阶段失败（网络连接）

**错误信息**: `Unable to contact SonarQube server`

**原因**: Jenkins Agent 无法连接到 SonarQube

**解决方案**:
```bash
# 1. 验证 SonarQube 正在运行
docker ps | grep sonarqube

# 2. 验证网络连接
docker network inspect sonarqube-network
# 应该能看到 jenkins-agent-dotnet-test 和 sonarqube 都在这个网络中

# 3. 从 Agent 容器内测试连接
docker exec jenkins-agent-dotnet-test curl -I http://sonarqube:9000

# 4. 如果网络配置有问题，重启 Agent
cd /mnt/d/Repositories/JenkinsDeploy/agents
docker compose -f docker-compose-test-dotnet.yml down
docker compose -f docker-compose-test-dotnet.yml up -d
```

### 问题 2: Token 认证失败

**错误信息**: `Unauthorized - Insufficient privileges`

**原因**: Token 无效或过期

**解决方案**:
1. 在 SonarQube 中重新生成 Token
2. 在 Jenkins 中更新凭证:
   - Manage Jenkins → Manage Credentials
   - 找到 `sonarqube-token`
   - 点击 **Update**
   - 粘贴新的 Token

### 问题 3: 覆盖率报告未显示

**错误信息**: SonarQube 显示 Coverage: 0.0%

**原因**: 覆盖率文件路径不正确或格式不支持

**解决方案**:
```bash
# 1. 检查覆盖率文件是否生成
# 在 Pipeline 日志中搜索:
# "找到 OpenCover 覆盖率文件"

# 2. 验证文件存在
docker exec jenkins-agent-dotnet-test ls -lh /home/jenkins/agent/workspace/*/test-results/coverage/

# 3. 确保 Unit Test 阶段正确配置了覆盖率收集
# 参考 quick-test-pipeline.groovy 第 132-140 行
```

### 问题 4: dotnet-sonarscanner 安装失败

**错误信息**: `dotnet tool install failed`

**原因**: NuGet 配置问题或网络问题

**解决方案**:
```bash
# 1. 进入 Agent 容器
docker exec -it jenkins-agent-dotnet-test bash

# 2. 手动安装工具
dotnet tool install --global dotnet-sonarscanner

# 3. 验证安装
export PATH="$HOME/.dotnet/tools:$PATH"
dotnet sonarscanner --version

# 4. 如果成功，退出容器并重新运行 Pipeline
```

## 📊 预期结果

### Pipeline 各阶段执行时间（参考）

| 阶段 | 预期时间 | 说明 |
|------|---------|------|
| Copy Project | 5-10s | 复制测试项目 |
| Environment Check | 3-5s | 检查构建环境 |
| Restore Dependencies | 10-30s | 还原 NuGet 包 |
| Build | 10-20s | 编译项目 |
| Unit Test | 5-15s | 运行单元测试 + 覆盖率 |
| E2E Tests | 60-120s | 端到端测试（包括启动 DB 和 API） |
| **SonarQube Analysis** | **30-60s** | 代码扫描和上传 |
| Build Docker Image | 20-40s | 构建 Docker 镜像 |
| **总计** | **~3-5 分钟** | 完整流程 |

### SonarQube 报告预期指标

对于 TodoApp-backend 项目:
- **Lines of Code**: ~500-1000
- **Coverage**: 60-80%（取决于测试完整性）
- **Bugs**: 0-3
- **Vulnerabilities**: 0-1
- **Code Smells**: 5-15
- **Technical Debt**: < 1h
- **Duplications**: < 3%

## 🎓 最佳实践

1. **定期查看 SonarQube 报告**: 每次构建后检查新增的问题
2. **配置 Quality Gate**: 在 SonarQube 中设置质量门禁
3. **修复高优先级问题**: 优先处理 Bugs 和 Vulnerabilities
4. **监控覆盖率趋势**: 确保覆盖率不降低
5. **定期备份数据**: 使用 `docker exec sonarqube-db pg_dump...`

## 📚 参考资料

- [SonarQube 官方文档](https://docs.sonarqube.org/latest/)
- [SonarQube for .NET](https://docs.sonarqube.org/latest/analyzing-source-code/scanners/dotnet/)
- [Jenkins SonarQube Plugin](https://docs.sonarqube.org/latest/analyzing-source-code/scanners/jenkins-extension-sonarqube/)
- 项目 README: `/mnt/d/Repositories/JenkinsDeploy/components/sonarqube/README.md`

## 🔄 维护命令

```bash
# 查看 SonarQube 日志
cd /mnt/d/Repositories/JenkinsDeploy/components/sonarqube
docker compose logs -f sonarqube

# 重启 SonarQube
docker compose restart sonarqube

# 停止 SonarQube
./stop.sh

# 完全重置 SonarQube（删除所有数据）
docker compose down -v

# 备份 SonarQube 数据库
docker exec sonarqube-db pg_dump -U sonar sonarqube > sonarqube-backup-$(date +%Y%m%d).sql

# 查看资源使用
docker stats sonarqube sonarqube-db
```

## ✅ 完成检查清单

- [ ] SonarQube 已启动并可访问 (http://localhost:9000)
- [ ] 已修改默认密码
- [ ] 已创建项目分析 Token
- [ ] Jenkins 中已配置 SonarQube 服务器
- [ ] Jenkins Agent 已连接到 sonarqube-network
- [ ] Pipeline 构建成功，所有阶段通过
- [ ] SonarQube 中能看到 `todoapp-backend` 项目（或 `TodoApp Backend API`）
- [ ] 覆盖率数据正确显示（非 0.0%）
- [ ] 能查看详细的代码问题列表

完成所有检查后，你的 SonarQube 集成环境就配置完成了！🎉
