# 项目结构说明

## 文件清单

```
JenkinsDeploy/
│
├── 核心构建文件
│   ├── Dockerfile                      # Jenkins Master镜像定义
│   ├── plugins.txt                     # 预装插件列表 (80+插件)
│   └── docker-compose.yml              # 容器编排配置
│
├── 脚本文件
│   ├── build.sh                        # [外网] 构建并导出镜像
│   └── import.sh                       # [内网] 导入镜像
│
├── 配置文件
│   ├── config/
│   │   └── jenkins-casc.yaml           # JCasC配置即代码
│   └── .env.example                    # 环境变量模板
│
├── 示例流水线
│   └── examples/
│       ├── Jenkinsfile-dotnet          # .NET项目流水线
│       ├── Jenkinsfile-java            # Java项目流水线
│       ├── Jenkinsfile-vue             # Vue前端流水线
│       └── Jenkinsfile-multibranch     # 多分支流水线
│
├── 文档
│   ├── README.md                       # 完整部署文档
│   ├── QUICKSTART.md                   # 10分钟快速上手
│   └── PROJECT_STRUCTURE.md            # 本文件
│
└── 其他
    └── .gitignore                      # Git忽略规则

```

## 文件用途详解

### 1. Dockerfile
**用途**: 定义 Jenkins Master 镜像构建过程
**关键内容**:
- 基于 `jenkins/jenkins:lts`
- 安装系统依赖和工具
- 预装插件列表
- 配置时区和环境变量
- 禁用安装向导

**使用场景**: `build.sh` 自动调用,无需手动执行

---

### 2. plugins.txt
**用途**: 列出需要预装的所有插件
**插件分类**:
- 核心基础插件 (Pipeline、凭证管理等)
- SCM集成 (Git、GitLab)
- Agent管理 (SSH、JNLP)
- 构建工具 (MSBuild、Maven、NodeJS)
- 代码质量 (SonarQube)
- 制品管理 (Nexus、Artifactory)
- 通知插件 (Email、钉钉、Webhook)
- UI增强 (Blue Ocean、Dashboard)
- 权限管理 (矩阵授权、角色策略)
- 实用工具 (JCasC、Job DSL等)

**插件总数**: 80+

**自定义方法**:
1. 添加新插件: 在文件末尾添加 `plugin-name:latest`
2. 指定版本: 使用 `plugin-name:1.2.3` 格式
3. 重新构建镜像

---

### 3. docker-compose.yml
**用途**: 定义 Jenkins Master 容器运行配置
**配置项**:
- 端口映射: 8080 (Web UI), 50000 (Agent)
- 数据卷: `./jenkins_home` 持久化
- 环境变量: JVM参数、时区等
- 资源限制: CPU/内存配额
- 健康检查: 自动检测服务状态
- 日志配置: 日志轮转策略

**修改建议**:
- 调整内存: 修改 `JAVA_OPTS` 中的 `-Xmx` 和 `-Xms`
- 更改端口: 修改 `ports` 映射
- 添加环境变量: 在 `environment` 部分添加

---

### 4. build.sh
**用途**: [外网环境] 一键构建并导出镜像
**执行步骤**:
1. 检查构建文件 (Dockerfile、plugins.txt)
2. 构建 Docker 镜像 (约 15-30 分钟)
3. 测试镜像启动
4. 导出为 tar 文件
5. 生成 MD5 校验文件

**输出文件**:
- `jenkins-master-offline-1.0.tar` (约 2-4GB)
- `jenkins-master-offline-1.0.tar.md5`

**使用方法**:
```bash
chmod +x build.sh
bash build.sh
```

---

### 5. import.sh
**用途**: [内网环境] 导入镜像到 Docker
**执行步骤**:
1. 检查镜像 tar 文件
2. MD5 校验文件完整性
3. 导入镜像到 Docker
4. 验证镜像成功加载

**使用方法**:
```bash
chmod +x import.sh
bash import.sh
```

---

### 6. jenkins-casc.yaml
**用途**: Jenkins Configuration as Code (JCasC) 配置
**配置内容**:
- Jenkins 系统消息
- 执行器配置 (Master不执行构建)
- 安全域 (默认管理员账号)
- 授权策略
- 全局工具配置 (Maven、JDK等)
- 凭证配置 (GitLab、SSH等)
- 外部服务集成 (GitLab、SonarQube、Nexus)

**使用方法**:
1. 复制到 `jenkins_home/` 目录
2. 在 Jenkins UI 中: Manage Jenkins -> Configuration as Code
3. 或在 docker-compose.yml 中配置:
   ```yaml
   environment:
     - CASC_JENKINS_CONFIG=/var/jenkins_home/jenkins-casc.yaml
   ```

---

### 7. .env.example
**用途**: 环境变量配置模板
**使用方法**:
```bash
cp .env.example .env
# 编辑 .env 文件,修改配置
docker-compose --env-file .env up -d
```

---

### 8. 示例流水线

#### Jenkinsfile-dotnet
- **适用**: C# .NET 微服务项目
- **流程**: 恢复依赖 → SonarQube → 编译 → 测试 → 打包 → 上传Nexus
- **特性**: 参数化构建、代码质量门、测试报告

#### Jenkinsfile-java
- **适用**: Spring Boot / Maven 项目
- **流程**: 编译 → 测试 → SonarQube → 打包 → 制品上传
- **特性**: Maven Profile、JUnit报告

#### Jenkinsfile-vue
- **适用**: Vue 前端项目
- **流程**: 安装依赖 → ESLint → 测试 → 构建 → 打包 → 部署
- **特性**: 多环境构建、自动部署

#### Jenkinsfile-multibranch
- **适用**: 多分支开发流程
- **流程**: 根据分支 (develop/release/master) 执行不同策略
- **特性**: 质量门检查、人工确认部署、健康检查

---

## 部署流程

### 外网环境
```
1. 执行 build.sh
   ↓
2. 生成 jenkins-master-offline-1.0.tar
   ↓
3. 传输到内网
```

### 内网环境
```
1. 执行 import.sh
   ↓
2. docker-compose up -d
   ↓
3. 访问 http://IP:8080
```

## 数据持久化

### jenkins_home 目录结构
```
jenkins_home/
├── config.xml              # Jenkins全局配置
├── credentials.xml         # 凭证加密存储
├── jobs/                   # 所有Job定义
├── workspace/              # 构建工作空间
├── plugins/                # 插件文件
├── secrets/                # 密钥 (initialAdminPassword等)
├── users/                  # 用户配置
├── logs/                   # 日志文件
└── war/                    # Jenkins WAR包缓存
```

## 版本管理

### 更新插件
1. 修改 `plugins.txt`
2. 重新执行 `build.sh`
3. 传输新镜像到内网
4. 停止容器: `docker-compose down`
5. 导入新镜像: `bash import.sh`
6. 启动容器: `docker-compose up -d`

### 回滚版本
```bash
# 保留旧版本镜像
docker tag jenkins-master-offline:1.0 jenkins-master-offline:1.0-backup

# 回滚
docker-compose down
docker tag jenkins-master-offline:1.0-backup jenkins-master-offline:1.0
docker-compose up -d
```

## 安全注意事项

1. **修改默认密码**: 首次登录后立即修改 `jenkins-casc.yaml` 中的默认密码
2. **凭证管理**: 使用 Jenkins Credentials 管理敏感信息,不要硬编码
3. **网络隔离**: 仅开放必要端口 (8080, 50000)
4. **定期备份**: 建立 `jenkins_home` 自动备份策略
5. **HTTPS**: 生产环境建议通过 Nginx 反向代理启用 HTTPS

## 性能调优

### 内存配置
- **小规模** (< 10 Job): `-Xmx2g -Xms512m`
- **中等规模** (10-50 Job): `-Xmx4g -Xms1g`
- **大规模** (> 50 Job): `-Xmx8g -Xms2g`

### 磁盘优化
- 定期清理旧构建记录
- 清理 workspace 工作空间
- 使用 SSD 存储 `jenkins_home`

### Master节点优化
- 将执行器数量设置为 0
- 所有构建任务在 Agent 节点执行

## 故障排查

### 查看日志
```bash
docker-compose logs -f jenkins
```

### 进入容器
```bash
docker-compose exec jenkins bash
```

### 重启服务
```bash
docker-compose restart jenkins
```

### 重置 Jenkins
```bash
docker-compose down
rm -rf jenkins_home/*
docker-compose up -d
```

## 技术支持

- **完整文档**: [README.md](README.md)
- **快速上手**: [QUICKSTART.md](QUICKSTART.md)
- **官方文档**: https://www.jenkins.io/doc/

---

**项目版本**: 1.0
**最后更新**: 2025-01-26
**维护团队**: DevOps Team
