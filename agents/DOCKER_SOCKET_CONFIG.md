# Docker Socket 权限配置指南

## 概述

使用 Docker-outside-of-Docker (DooD) 模式时，容器内的进程需要访问宿主机的 Docker socket (`/var/run/docker.sock`)。本文档说明如何正确配置权限。

## 方案对比

### ❌ 临时方案（不推荐）
```bash
# 手动添加用户到 Docker 组
docker exec -u root <container> usermod -aG docker jenkins
docker restart <container>
```
**缺点**: 需要手动操作，容器重建后失效

### ✅ 推荐方案：使用 `group_add`

在 `docker-compose.yml` 中配置：

```yaml
services:
  agent:
    image: jenkins-agent-dotnet:2.0
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock

    # 添加 Docker socket 的组 ID
    group_add:
      - "1001"  # 替换为实际的 GID
```

**优点**:
- 配置即代码，可版本控制
- 容器启动时自动应用
- 无需修改 Dockerfile 或 entrypoint 脚本
- 跨平台兼容性好

## 如何获取正确的 GID

### Linux/WSL/macOS
```bash
stat -c '%g' /var/run/docker.sock
```

### Windows (PowerShell)
在 WSL 中运行上述 Linux 命令

## 不同系统的常见 GID

| 系统 | 常见 GID | 说明 |
|------|----------|------|
| Ubuntu/Debian | 999 或 1001 | 取决于 Docker 版本 |
| CentOS/RHEL | 993 或 994 | |
| macOS | 自动管理 | Docker Desktop 自动处理 |
| WSL2 | 1001 | 通常是 1001 |

## 配置步骤

### 1. 检查宿主机 Docker socket GID
```bash
stat -c '%g' /var/run/docker.sock
```

### 2. 更新 docker-compose.yml
```yaml
group_add:
  - "YOUR_GID_HERE"  # 替换为步骤 1 的结果
```

### 3. 重启容器应用配置
```bash
docker compose down
docker compose up -d
```

### 4. 验证配置
```bash
# 检查容器主进程的组成员
docker exec <container> cat /proc/1/status | grep Groups

# 测试 Docker 访问
docker exec <container> docker ps
docker exec <container> docker compose version
```

## 验证成功的标志

容器主进程应该在两个组中：
- 主组 (通常是 1000)
- Docker socket 组 (如 1001)

```bash
Groups:	1000 1001
          ^^^^  ^^^^
          主组  Docker组
```

## 故障排查

### 问题：permission denied while trying to connect to Docker socket

**原因**: `group_add` 的 GID 与 Docker socket 的实际 GID 不匹配

**解决**:
1. 重新检查 Docker socket GID: `stat -c '%g' /var/run/docker.sock`
2. 更新 `group_add` 配置
3. 重启容器

### 问题：容器中 `docker ps` 能运行，但 `groups` 命令看不到 Docker 组

**说明**: 这是正常的！`group_add` 添加的是附加组 ID，不会在 `/etc/group` 中创建组名。

**验证方法**:
```bash
# 正确的验证方式
docker exec <container> cat /proc/1/status | grep Groups
```

## 多环境部署

不同环境的 Docker socket GID 可能不同，有两种处理方式：

### 方式 1: 环境变量
```yaml
group_add:
  - "${DOCKER_GID:-1001}"  # 默认 1001，可通过环境变量覆盖
```

部署时：
```bash
export DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)
docker compose up -d
```

### 方式 2: 多个 compose 文件
```
docker-compose.yml          # 基础配置
docker-compose.dev.yml      # 开发环境 (GID 1001)
docker-compose.prod.yml     # 生产环境 (GID 999)
```

部署时：
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## 安全注意事项

⚠️ **警告**: 允许容器访问 Docker socket 等同于给予 root 权限！

- 仅在受信任的环境中使用
- 不要在生产环境中暴露有 Docker 访问权限的服务
- 考虑使用 Docker-in-Docker (DinD) 作为更安全的替代方案

## 参考文档

- [Docker Compose group_add](https://docs.docker.com/compose/compose-file/05-services/#group_add)
- [Docker-outside-of-Docker vs Docker-in-Docker](https://jpetazzo.github.io/2015/09/03/do-not-use-docker-in-docker-for-ci/)
