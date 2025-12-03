# SonarQube 代码质量分析平台

## 📋 概述

SonarQube 是一个开源的代码质量管理平台，用于持续检查代码质量，检测 Bug、代码异味和安全漏洞。

本配置提供了一个独立的 SonarQube 实例，用于 Jenkins CI/CD 流程中的静态代码分析。

## 🚀 快速启动

### 1. 启动 SonarQube 服务

```bash
# 在 components/sonarqube 目录下
docker compose up -d

# 查看服务状态
docker compose ps

# 查看启动日志（SonarQube 首次启动需要 2-3 分钟）
docker compose logs -f sonarqube
```

### 2. 访问 Web UI

- **URL**: http://localhost:9000
- **默认账号**: admin
- **默认密码**: admin
- **首次登录**: 系统会要求修改密码

### 3. 创建项目 Token

首次使用需要创建分析 Token：

1. 登录 SonarQube (http://localhost:9000)
2. 点击右上角头像 → **My Account**
3. 选择 **Security** 标签
4. 在 **Generate Tokens** 部分：
   - Token Name: `jenkins-todoapp-backend`
   - Type: `Project Analysis Token`
   - Expires in: `No expiration` 或 `30 days`
5. 点击 **Generate**
6. **复制并保存 Token**（只显示一次！）

### 4. 在 Jenkins 中配置 SonarQube

#### 4.1 配置 SonarQube 服务器

1. 进入 Jenkins 管理界面：**Manage Jenkins** → **Configure System**
2. 滚动到 **SonarQube servers** 部分
3. 点击 **Add SonarQube**
4. 配置：
   - **Name**: `sonarqube-server`（与 Pipeline 中的名称一致）
   - **Server URL**: `http://sonarqube:9000`（Docker 网络内部访问）
     - 如果 Jenkins 不在同一 Docker 网络，使用 `http://host.docker.internal:9000`
   - **Server authentication token**: 点击 **Add** → **Jenkins**
     - Kind: `Secret text`
     - Secret: 粘贴步骤 3 中生成的 Token
     - ID: `sonarqube-token`
     - Description: `SonarQube Token for TodoApp Backend`
   - 选择刚创建的凭证
5. 点击 **Save**

#### 4.2 安装 SonarQube Scanner 插件

1. **Manage Jenkins** → **Manage Plugins**
2. 搜索并安装：
   - **SonarQube Scanner for Jenkins**
3. 重启 Jenkins（如需要）

#### 4.3 配置 SonarQube Scanner 工具（可选）

如果使用 MSBuild Scanner（备用方案）：

1. **Manage Jenkins** → **Global Tool Configuration**
2. 滚动到 **SonarQube Scanner** 部分
3. 点击 **Add SonarQube Scanner**
4. 配置：
   - **Name**: `ms-scanner-8`
   - **Install automatically**: 勾选
   - 选择版本：`SonarQube Scanner 5.0` 或最新版本
5. 点击 **Save**

#### 4.4 配置 Webhook（必需！）

**为什么需要 Webhook？**

Jenkins 的 `waitForQualityGate` 步骤需要等待 SonarQube 完成分析并返回质量门结果。如果**没有配置 webhook**，Jenkins 只能通过轮询检查状态，容易导致：
- ⏱️ 超时失败（默认 10 分钟）
- 🐌 响应缓慢，浪费构建时间
- ❌ Pipeline 频繁 ABORTED

配置 webhook 后，SonarQube 会**主动通知** Jenkins 分析完成，实现秒级响应。

**配置步骤**：

1. 登录 SonarQube Web UI (http://localhost:9000)
2. 进入 **Administration** → **Configuration** → **Webhooks**
3. 点击 **Create**
4. 填写配置：
   - **Name**: `Jenkins` 或 `Jenkins-Webhook`
   - **URL**: `http://jenkins-master-test:8080/sonarqube-webhook/`
     - ⚠️ 注意最后的斜杠 `/` 不能省略
     - 如果 Jenkins 使用其他容器名，相应修改主机名
   - **Secret**: 留空（可选，用于验证请求来源）
5. 点击 **Create**

**验证 Webhook 配置**：

```bash
# 1. 检查 SonarQube 网络配置是否正确（见下方"问题 5"）
docker exec sonarqube env | grep -i proxy

# 2. 测试从 SonarQube 到 Jenkins webhook 的连通性（应返回 405）
docker exec sonarqube curl -s -o /dev/null -w "%{http_code}" http://jenkins-master-test:8080/sonarqube-webhook/
# 预期结果: 405 (Method Not Allowed - 正常，因为 endpoint 只接受 POST)

# 3. 运行 Pipeline，查看质量门阶段是否快速完成（几秒内）
# 正常日志应该显示：
#   "SonarQube task 'xxx' status is 'SUCCESS'"
#   而不是超时 "Timeout has been exceeded"
```

**注意事项**：
- 可以为每个项目配置独立的 webhook（Project 级别），也可以配置全局 webhook（Global 级别）
- 全局 webhook 对所有项目生效，更方便管理
- 如果遇到 502 Bad Gateway 错误，参见下方"问题 5"

## 🔧 在 Pipeline 中使用

参考 `examples/quick-test-pipeline.groovy` 中的 SonarQube 阶段：

```groovy
stage('SonarQube Analysis') {
    steps {
        dir("${WORKSPACE}/${PROJECT_PATH}") {
            withSonarQubeEnv('sonarqube-server') {
                sh """
                    # 确保 PATH 包含 dotnet tools
                    export PATH="\$HOME/.dotnet/tools:\$PATH"

                    # 安装 dotnet-sonarscanner（如果未安装）
                    dotnet tool install --global dotnet-sonarscanner || true

                    # 开始分析
                    dotnet sonarscanner begin \\
                        /k:"${PROJECT_NAME}" \\
                        /n:"${PROJECT_NAME}" \\
                        /v:"${env.BUILD_NUMBER}" \\
                        /d:sonar.projectBaseDir="${BUILD_DIR}" \\
                        /d:sonar.cs.opencover.reportsPaths="${WORKSPACE}/test-results/coverage/coverage.opencover.xml"

                    # 构建项目
                    dotnet build --configuration Release --no-restore

                    # 结束分析并上传结果
                    dotnet sonarscanner end
                """
            }
        }
    }
}
```

## 📊 查看分析结果

1. 访问 http://localhost:9000
2. 登录后，在主页查看项目列表
3. 点击项目名称查看详细报告：
   - **Overview**: 总体质量概览
   - **Issues**: 发现的问题列表（Bug、Vulnerability、Code Smell）
   - **Measures**: 各项指标详情
   - **Code**: 代码浏览（带问题标注）
   - **Activity**: 历史分析记录

## 🛠️ 故障排查

### ⚠️ 问题 0: HTTP 代理导致连接失败（常见问题！）

**症状**:
- Pipeline 日志显示 `Http status code is BadGateway`
- 错误信息：`Downloading from http://sonarqube:9000/api/server/version failed`
- 使用 `curl -v` 测试时看到：`Uses proxy env variable http_proxy`

**根本原因**:
Jenkins Agent 配置了 HTTP 代理（如 `HTTP_PROXY=http://host.docker.internal:6666`），但 `NO_PROXY` 列表中**没有包含 SonarQube 服务**，导致：
1. 所有对 `http://sonarqube:9000` 的请求被发送到代理服务器
2. 代理服务器无法解析 Docker 内部的 `sonarqube` 域名
3. 返回 502 Bad Gateway

**解决方案**:

在 Jenkins Agent 的 `docker-compose.yml` 中更新 `NO_PROXY` 环境变量：

```yaml
environment:
  # 代理设置：排除内部 Docker 网络和 SonarQube
  NO_PROXY: "localhost,127.0.0.1,jenkins-master-test,sonarqube,sonarqube-db,172.16.0.0/12,192.168.0.0/16,172.19.0.0/16"
  no_proxy: "localhost,127.0.0.1,jenkins-master-test,sonarqube,sonarqube-db,172.16.0.0/12,192.168.0.0/16,172.19.0.0/16"
```

**必须添加**：
- `sonarqube` - SonarQube 服务器主机名
- `sonarqube-db` - SonarQube 数据库主机名（可选）
- `172.19.0.0/16` - SonarQube 网络 CIDR（根据实际网络调整）

**验证修复**：

```bash
# 1. 重启 Agent
docker compose -f docker-compose-test-dotnet.yml restart

# 2. 检查代理配置
docker exec jenkins-agent-dotnet-test env | grep -i proxy

# 3. 测试连接（应该看到 "no_proxy" 包含 sonarqube）
docker exec jenkins-agent-dotnet-test curl -v http://sonarqube:9000/api/server/version

# 4. 应该返回 HTTP/1.1 200 和版本号（如 25.11.0.114957）
docker exec jenkins-agent-dotnet-test curl -s http://sonarqube:9000/api/server/version
```

**预防措施**：
- 在任何使用代理的环境中，务必将内部服务添加到 `NO_PROXY`
- 使用 Docker 网络时，添加对应的 CIDR 到 `NO_PROXY`
- 建议始终包含：`localhost,127.0.0.1,*.local,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16`

---

### 问题 1: SonarQube 启动失败

**症状**: `docker compose ps` 显示 `sonarqube` 容器一直在重启

**可能原因**:
- 系统内存不足（SonarQube 需要至少 2GB RAM）
- Elasticsearch 检查失败

**解决方案**:

```bash
# 检查日志
docker compose logs sonarqube

# 如果是 vm.max_map_count 错误（Linux）
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf

# 重启容器
docker compose restart sonarqube
```

### 问题 2: Jenkins 无法连接 SonarQube

**症状**: Pipeline 阶段报错 "Unable to contact SonarQube server"

**可能原因**:
- Jenkins 和 SonarQube 不在同一 Docker 网络
- SonarQube 尚未完全启动

**解决方案**:

```bash
# 方案 1: 将 Jenkins Agent 加入 SonarQube 网络
# 修改 agents/docker-compose-test-dotnet.yml
networks:
  default:
    name: jenkins-network
  sonarqube:
    external: true
    name: sonarqube-network

# 方案 2: 使用 host.docker.internal（推荐用于开发环境）
# 在 Jenkins SonarQube 配置中使用：
# Server URL: http://host.docker.internal:9000

# 验证连接
docker exec jenkins-agent-dotnet curl -I http://sonarqube:9000
```

### 问题 3: 覆盖率报告未显示

**症状**: SonarQube 显示 0% 覆盖率

**可能原因**:
- 覆盖率文件路径不正确
- 覆盖率文件格式不支持（需要 OpenCover 格式）

**解决方案**:

```bash
# 检查覆盖率文件是否存在
ls -lh test-results/coverage/coverage.opencover.xml

# 确保测试阶段生成了 OpenCover 格式
dotnet test --collect:"XPlat Code Coverage" \
    -- DataCollectionRunSettings.DataCollectors.DataCollector.Configuration.Format=opencover

# 在 SonarQube 分析时指定正确路径
/d:sonar.cs.opencover.reportsPaths="path/to/coverage.opencover.xml"
```

### 问题 4: dotnet-sonarscanner 未找到

**症状**: `dotnet sonarscanner: command not found`

**解决方案**:

```bash
# 安装 dotnet-sonarscanner
dotnet tool install --global dotnet-sonarscanner

# 确保 PATH 包含 .dotnet/tools
export PATH="$HOME/.dotnet/tools:$PATH"

# 验证安装
dotnet sonarscanner --version
```

### 问题 5: Webhook 连接失败 - 质量门检查超时

**症状**:
- Pipeline 的 `waitForQualityGate` 阶段超时（10 分钟后 ABORTED）
- Jenkins 日志显示：`SonarQube task 'xxx' status is 'PENDING'`（一直停在 PENDING 状态）
- 即使 SonarQube 已完成分析，Jenkins 仍然等待超时

**根本原因**:

SonarQube 容器使用了 HTTP 代理，但 `NO_PROXY` 列表中**没有包含 Jenkins 主机名**，导致：
1. SonarQube 分析完成后尝试通过 webhook 通知 Jenkins
2. HTTP 请求被代理拦截（`http://jenkins-master-test:8080/sonarqube-webhook/`）
3. 代理无法解析 Docker 内部的 `jenkins-master-test` 域名
4. 返回 **502 Bad Gateway**，webhook 发送失败
5. Jenkins 无法收到通知，只能轮询等待，最终超时

**诊断方法**:

```bash
# 1. 检查 SonarQube 是否使用了代理
docker exec sonarqube env | grep -i proxy
# 如果输出包含 HTTP_PROXY 且 NO_PROXY 不包含 jenkins-master-test，即存在问题

# 2. 测试从 SonarQube 到 Jenkins webhook 的连通性
docker exec sonarqube curl -v http://jenkins-master-test:8080/sonarqube-webhook/ 2>&1 | head -20
# 正常：应看到 "HTTP/1.1 405" (Method Not Allowed - 正常，只接受 POST)
# 异常：看到 "Uses proxy" 和 "HTTP/1.1 502" (Bad Gateway - 代理拦截)
```

**解决方案**:

**方法 1: 更新 SonarQube 的 NO_PROXY 配置（推荐）**

编辑 `components/sonarqube/docker-compose.yml`：

```yaml
services:
  sonarqube:
    image: sonarqube:community
    environment:
      SONAR_JDBC_URL: jdbc:postgresql://sonarqube-db:5432/sonarqube
      SONAR_JDBC_USERNAME: sonar
      SONAR_JDBC_PASSWORD: sonar
      SONAR_ES_BOOTSTRAP_CHECKS_DISABLE: 'true'

      # 🔧 添加以下配置 - 允许直接访问内部 Jenkins 服务
      NO_PROXY: "localhost,127.0.0.1,jenkins-master-test,jenkins,sonarqube-db,172.19.0.0/16,172.20.0.0/16"
      no_proxy: "localhost,127.0.0.1,jenkins-master-test,jenkins,sonarqube-db,172.19.0.0/16,172.20.0.0/16"
```

**必须包含**：
- `jenkins-master-test` - Jenkins Master 容器主机名（根据实际名称调整）
- `jenkins` - Jenkins 的别名（如果有）
- `172.19.0.0/16`, `172.20.0.0/16` - Docker 网络 CIDR（根据实际网络调整）

重启 SonarQube：

```bash
cd components/sonarqube
docker compose down
docker compose up -d

# 等待启动（约 30 秒）
docker logs -f sonarqube | grep "SonarQube is operational"
```

**方法 2: 禁用 SonarQube 的代理（仅开发环境）**

如果 SonarQube 不需要访问外网，可以完全禁用代理：

```yaml
services:
  sonarqube:
    environment:
      # 覆盖继承的代理配置
      HTTP_PROXY: ""
      HTTPS_PROXY: ""
      http_proxy: ""
      https_proxy: ""
```

**验证修复**:

```bash
# 1. 确认 NO_PROXY 已更新
docker exec sonarqube env | grep NO_PROXY
# 应该输出包含 jenkins-master-test

# 2. 测试连接（应返回 405 而不是 502）
docker exec sonarqube curl -s -o /dev/null -w "%{http_code}" http://jenkins-master-test:8080/sonarqube-webhook/
# 预期输出: 405

# 3. 重新运行 Jenkins Pipeline
# waitForQualityGate 阶段应在几秒内完成，日志显示：
#   "SonarQube task 'xxx' status is 'SUCCESS'"
```

**预防措施**：
- 在配置代理的环境中，务必将所有内部服务添加到 `NO_PROXY`
- 建议的 `NO_PROXY` 模板：
  ```
  localhost,127.0.0.1,*.local,jenkins,jenkins-master-test,sonarqube,sonarqube-db,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16
  ```
- 使用 `docker network inspect <network-name>` 查看实际的 CIDR 并添加到 NO_PROXY

## 🔐 生产环境配置

### 1. 使用外部数据库

修改 `docker-compose.yml`：

```yaml
environment:
  SONAR_JDBC_URL: jdbc:postgresql://your-postgres-host:5432/sonarqube
  SONAR_JDBC_USERNAME: your-username
  SONAR_JDBC_PASSWORD: your-password
```

### 2. 配置 HTTPS

使用 Nginx 或 Traefik 作为反向代理：

```nginx
server {
    listen 443 ssl;
    server_name sonarqube.yourdomain.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://localhost:9000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 3. 数据备份

```bash
# 备份数据库
docker exec sonarqube-db pg_dump -U sonar sonarqube > sonarqube-backup-$(date +%Y%m%d).sql

# 备份 volumes
docker run --rm -v sonarqube-data:/data -v $(pwd):/backup alpine tar czf /backup/sonarqube-data-backup.tar.gz /data
```

## 📚 参考资料

- [SonarQube 官方文档](https://docs.sonarqube.org/latest/)
- [SonarQube Scanner for .NET](https://docs.sonarqube.org/latest/analyzing-source-code/scanners/dotnet/)
- [Jenkins SonarQube 插件](https://docs.sonarqube.org/latest/analyzing-source-code/scanners/jenkins-extension-sonarqube/)

## 🔄 维护命令

```bash
# 停止服务
docker compose down

# 停止并删除数据（重置 SonarQube）
docker compose down -v

# 查看日志
docker compose logs -f

# 更新镜像
docker compose pull
docker compose up -d

# 查看资源使用
docker stats sonarqube sonarqube-db
```

## 📝 注意事项

1. **首次启动较慢**: SonarQube 初始化需要 2-3 分钟
2. **资源需求**: 建议至少 2GB RAM，4GB 更佳
3. **Token 安全**: 妥善保管分析 Token，不要提交到代码仓库
4. **定期备份**: 生产环境建议定期备份数据库和配置
5. **质量门禁**: 可在 SonarQube 中配置 Quality Gate，自动阻止不达标的代码

## 🎯 最佳实践

1. **为每个项目创建独立的 Project**: 便于管理和追踪
2. **配置 Quality Gate**: 设置代码质量标准
3. **定期审查 Issues**: 及时修复检测到的问题
4. **集成 Pull Request 分析**: 在合并前检查代码质量
5. **监控技术债务**: 关注 Code Smell 和技术债务趋势
