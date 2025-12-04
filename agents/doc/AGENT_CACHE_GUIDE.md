# Jenkins Agent 缓存配置指南

本文档说明如何配置 Jenkins Agent 的依赖包缓存，实现多个 Agent 共享缓存，提升构建速度并节省磁盘空间。

## 📋 目录

- [缓存架构设计](#缓存架构设计)
- [部署前准备](#部署前准备)
- [各Agent缓存说明](#各agent缓存说明)
- [多服务器共享缓存方案](#多服务器共享缓存方案)
- [维护和清理](#维护和清理)
- [常见问题](#常见问题)

---

## 缓存架构设计

### 为什么使用主机目录挂载而非 Docker Volume？

**Docker Volume 方案（旧）：**
```yaml
volumes:
  - jenkins-agent-dotnet-nuget:/home/jenkins/.nuget  # 每个 Agent 独立 volume
```

❌ **问题：**
- 每个 Agent 容器有独立的 volume，无法共享
- 浪费磁盘空间（相同依赖包被多次下载和存储）
- 难以备份和迁移

**主机目录挂载方案（新）：**
```yaml
volumes:
  - /data/jenkins-cache/nuget-packages:/home/jenkins/.nuget/packages  # 共享主机目录
```

✅ **优势：**
- 多个 Agent 共享同一缓存目录
- 节省磁盘空间和网络带宽
- 便于备份、监控和清理
- 支持跨服务器共享（通过 NFS）

---

## 部署前准备

### 单服务器部署

在 **每台运行 Jenkins Agent 的服务器** 上执行以下命令：

```bash
# 1. 创建缓存根目录
sudo mkdir -p /data/jenkins-cache

# 2. 创建各语言缓存目录
sudo mkdir -p /data/jenkins-cache/nuget-packages      # .NET NuGet packages
sudo mkdir -p /data/jenkins-cache/dotnet-tools        # .NET global tools (如 dotnet-sonarscanner)
sudo mkdir -p /data/jenkins-cache/npm-cache           # Node.js npm 缓存
sudo mkdir -p /data/jenkins-cache/node-cache          # Node.js 构建工具缓存（Vite, Webpack 等）
sudo mkdir -p /data/jenkins-cache/maven-repository    # Java Maven 仓库（预留）
sudo mkdir -p /data/jenkins-cache/gradle-cache        # Java Gradle 缓存（预留）

# 3. 设置权限（jenkins 容器用户 UID=1000, GID=1000）
sudo chown -R 1000:1000 /data/jenkins-cache

# 4. 验证权限
ls -la /data/jenkins-cache
```

**预期输出：**
```
drwxr-xr-x 8 1000 1000 4096 Dec  4 10:00 .
drwxr-xr-x 3 root root 4096 Dec  4 09:55 ..
drwxr-xr-x 2 1000 1000 4096 Dec  4 10:00 dotnet-tools
drwxr-xr-x 2 1000 1000 4096 Dec  4 10:00 maven-repository
drwxr-xr-x 2 1000 1000 4096 Dec  4 10:00 node-cache
drwxr-xr-x 2 1000 1000 4096 Dec  4 10:00 npm-cache
drwxr-xr-x 2 1000 1000 4096 Dec  4 10:00 nuget-packages
```

---

## 各Agent缓存说明

### .NET Agent

**缓存目录：**

| 容器内路径 | 主机路径 | 说明 | 典型大小 |
|-----------|---------|------|---------|
| `/home/jenkins/.nuget/packages` | `/data/jenkins-cache/nuget-packages` | NuGet 依赖包 | 1-5 GB |
| `/home/jenkins/.dotnet/tools` | `/data/jenkins-cache/dotnet-tools` | .NET global tools | 100-500 MB |

**配置文件：** `agents/dotnet/docker-compose-dotnet.yml`

**验证缓存生效：**

```bash
# 第一次构建 .NET 项目后，检查缓存目录
sudo ls -lh /data/jenkins-cache/nuget-packages

# 应该看到下载的 NuGet 包，例如：
# microsoft.aspnetcore.app.ref/
# microsoft.entityframeworkcore/
# newtonsoft.json/
```

**多Agent共享示例：**

假设你有 3 个 .NET Agent（agent-dotnet-1, agent-dotnet-2, agent-dotnet-3），它们的 `docker-compose.yml` 都配置相同的挂载：

```yaml
# agents/dotnet/docker-compose-agent1.yml
volumes:
  - /data/jenkins-cache/nuget-packages:/home/jenkins/.nuget/packages

# agents/dotnet/docker-compose-agent2.yml
volumes:
  - /data/jenkins-cache/nuget-packages:/home/jenkins/.nuget/packages  # 相同路径
```

✅ **结果：**
- agent-1 首次构建下载依赖包 → 缓存到 `/data/jenkins-cache/nuget-packages`
- agent-2 构建相同项目 → 直接使用缓存，无需下载
- 节省时间：从 2-3 分钟下载 → 10 秒读取缓存

---

### Vue/Node.js Agent

**缓存目录：**

| 容器内路径 | 主机路径 | 说明 | 典型大小 |
|-----------|---------|------|---------|
| `/home/jenkins/.npm` | `/data/jenkins-cache/npm-cache` | npm 包缓存 | 2-10 GB |
| `/home/jenkins/.cache` | `/data/jenkins-cache/node-cache` | 构建工具缓存（Vite, Webpack, Babel 等） | 500 MB - 2 GB |

**配置文件：** `agents/vue/docker-compose-vue.yml`

**验证缓存生效：**

```bash
# 第一次构建 Vue 项目后，检查缓存目录
sudo ls -lh /data/jenkins-cache/npm-cache/_cacache

# 应该看到 npm 缓存结构
# content-v2/
# index-v5/
# tmp/
```

---

### Java Agent（预留）

**未来如需添加 Java Agent，使用以下配置：**

```yaml
# agents/java/docker-compose-java.yml
volumes:
  # Maven 仓库缓存
  - /data/jenkins-cache/maven-repository:/home/jenkins/.m2/repository

  # Gradle 缓存
  - /data/jenkins-cache/gradle-cache:/home/jenkins/.gradle
```

**缓存目录：**

| 容器内路径 | 主机路径 | 说明 | 典型大小 |
|-----------|---------|------|---------|
| `/home/jenkins/.m2/repository` | `/data/jenkins-cache/maven-repository` | Maven 依赖包 | 1-5 GB |
| `/home/jenkins/.gradle` | `/data/jenkins-cache/gradle-cache` | Gradle 缓存 | 1-3 GB |

---

## 多服务器共享缓存方案

如果你在 **多台服务器** 上运行 Jenkins Agent，可以通过 **NFS 网络共享存储** 实现缓存共享。

### 架构图

```
┌─────────────────────┐
│   NFS Server        │
│  (10.0.1.10)        │  ← 统一缓存存储
│  /data/jenkins-cache│
└──────────┬──────────┘
           │ NFS 挂载
    ┌──────┴──────┬──────────┬──────────┐
    │             │          │          │
┌───▼────┐  ┌────▼───┐  ┌───▼────┐  ┌──▼─────┐
│ agent1 │  │ agent2 │  │ agent3 │  │ agent8 │
│(10.0.1.11)│(10.0.1.12)│(10.0.1.13)│(10.0.1.18)│
└────────┘  └────────┘  └────────┘  └────────┘
     ↓           ↓           ↓           ↓
   共享 /data/jenkins-cache (NFS mount)
```

### 部署步骤

#### 1. 在 NFS Server 上配置（选一台作为存储节点，如 jenkins-agent1）

```bash
# SSH 到 jenkins-agent1 服务器
ssh root@<NFS_SERVER_IP>

# 安装 NFS Server
apt-get update
apt-get install -y nfs-kernel-server

# 创建共享目录
mkdir -p /data/jenkins-cache
chown -R 1000:1000 /data/jenkins-cache

# 配置 NFS 导出（替换为你的内网IP段）
cat >> /etc/exports <<EOF
/data/jenkins-cache 10.0.1.0/24(rw,sync,no_subtree_check,no_root_squash)
EOF

# 应用配置
exportfs -ra

# 启动 NFS
systemctl enable nfs-server
systemctl start nfs-server

# 验证导出成功
showmount -e localhost
```

**预期输出：**
```
Export list for localhost:
/data/jenkins-cache 10.0.1.0/24
```

#### 2. 在其他 Agent 服务器上挂载 NFS（agent2 ~ agent8）

```bash
# SSH 到每台 Agent 服务器
ssh root@<AGENT_SERVER_IP>

# 安装 NFS 客户端
apt-get update
apt-get install -y nfs-common

# 创建挂载点
mkdir -p /data/jenkins-cache

# 挂载 NFS（替换为你的 NFS Server IP）
mount -t nfs <NFS_SERVER_IP>:/data/jenkins-cache /data/jenkins-cache

# 验证挂载
df -h | grep jenkins-cache
```

**预期输出：**
```
<NFS_SERVER_IP>:/data/jenkins-cache  100G   10G   90G  10% /data/jenkins-cache
```

#### 3. 配置自动挂载（开机自启）

```bash
# 每台 Agent 服务器上执行（替换为你的 NFS Server IP）
cat >> /etc/fstab <<EOF
<NFS_SERVER_IP>:/data/jenkins-cache /data/jenkins-cache nfs defaults,_netdev 0 0
EOF

# 测试自动挂载
umount /data/jenkins-cache
mount -a
df -h | grep jenkins-cache
```

#### 4. 启动 Agent 容器

```bash
# 在每台服务器上启动 Agent（配置文件无需修改，都使用 /data/jenkins-cache）
cd /path/to/JenkinsDeploy/agents/dotnet
docker compose -f docker-compose-dotnet.yml up -d
```

### NFS 性能优化建议

1. **使用千兆/万兆网络**：确保 NFS Server 和 Agent 之间网络带宽充足
2. **调整 NFS 挂载参数**：
   ```bash
   mount -t nfs -o rw,hard,intr,rsize=32768,wsize=32768 <NFS_SERVER_IP>:/data/jenkins-cache /data/jenkins-cache
   ```
3. **缓存预热**：在 NFS Server 上预先下载常用依赖包

---

## 维护和清理

### 查看缓存大小

```bash
# 查看总缓存大小
du -sh /data/jenkins-cache

# 查看各语言缓存大小
du -sh /data/jenkins-cache/*
```

**示例输出：**
```
8.5G    /data/jenkins-cache/dotnet-tools
2.3G    /data/jenkins-cache/node-cache
12G     /data/jenkins-cache/npm-cache
4.2G    /data/jenkins-cache/nuget-packages
```

### 清理过期缓存

#### .NET NuGet 缓存清理

```bash
# 停止所有 .NET Agent
docker compose -f agents/dotnet/docker-compose-dotnet.yml down

# 清理 NuGet 缓存
docker run --rm \
  -v /data/jenkins-cache/nuget-packages:/home/jenkins/.nuget/packages \
  jenkins-agent-dotnet:2.0 \
  dotnet nuget locals all --clear

# 重启 Agent
docker compose -f agents/dotnet/docker-compose-dotnet.yml up -d
```

#### npm 缓存清理

```bash
# 停止所有 Vue Agent
docker compose -f agents/vue/docker-compose-vue.yml down

# 清理 npm 缓存（保留最近 30 天）
docker run --rm \
  -v /data/jenkins-cache/npm-cache:/home/jenkins/.npm \
  jenkins-agent-vue:1.0 \
  npm cache clean --force

# 重启 Agent
docker compose -f agents/vue/docker-compose-vue.yml up -d
```

### 备份缓存

```bash
# 创建备份
tar -czf jenkins-cache-backup-$(date +%Y%m%d).tar.gz /data/jenkins-cache

# 恢复备份
tar -xzf jenkins-cache-backup-20241204.tar.gz -C /
chown -R 1000:1000 /data/jenkins-cache
```

---

## 常见问题

### Q1: Agent 容器报 "Permission denied" 无法写入缓存目录

**原因：** 主机目录权限不正确

**解决：**
```bash
# 检查目录所有者
ls -la /data/jenkins-cache

# 修正权限（jenkins 容器用户 UID=1000）
sudo chown -R 1000:1000 /data/jenkins-cache

# 重启 Agent
docker compose restart
```

### Q2: 多个 Agent 同时构建时，缓存是否会冲突？

**答：** 不会。包管理器（NuGet, npm, Maven）设计时考虑了并发访问，使用文件锁和原子操作保证数据一致性。

**验证：**
```bash
# 在 Jenkins 上同时触发 3 个 .NET 构建任务，监控缓存目录
watch -n 1 "ls -lh /data/jenkins-cache/nuget-packages | head -20"
```

### Q3: NFS 挂载失败，报 "mount.nfs: access denied"

**原因：** NFS Server 的导出配置限制了客户端 IP

**解决：**
```bash
# 在 NFS Server 上检查导出配置
cat /etc/exports

# 确保包含客户端 IP 段
/data/jenkins-cache 188.2.76.0/24(rw,sync,no_subtree_check,no_root_squash)

# 重新加载配置
exportfs -ra
```

### Q4: 缓存占用磁盘过大怎么办？

**监控缓存大小：**
```bash
# 设置磁盘使用率告警
df -h /data/jenkins-cache

# 查找最大的缓存文件
du -ah /data/jenkins-cache | sort -rh | head -20
```

**优化策略：**
1. **定期清理**：每月执行一次缓存清理（见上方清理命令）
2. **设置配额**：使用 LVM 或 ZFS 限制缓存目录大小
3. **分离存储**：将缓存放在独立磁盘，避免影响系统盘

### Q5: 如何验证缓存共享生效？

**测试步骤：**

```bash
# 1. 在 agent-1 上首次构建项目
# Jenkins Pipeline 中观察日志：
#   "Downloading NuGet package Microsoft.AspNetCore.App.Ref 8.0.0..."

# 2. 检查缓存目录
ls /data/jenkins-cache/nuget-packages/microsoft.aspnetcore.app.ref/

# 3. 在 agent-2 上构建相同项目
# Jenkins Pipeline 中观察日志：
#   "Using cached NuGet package Microsoft.AspNetCore.App.Ref 8.0.0"  ← 关键日志

# 4. 对比构建时间
# agent-1 首次构建：3 分钟（含下载依赖）
# agent-2 第二次构建：30 秒（使用缓存）
```

---

## 总结

✅ **完成配置后，你将获得：**

1. **提升构建速度**：依赖包下载从分钟级降至秒级
2. **节省磁盘空间**：多个 Agent 共享缓存，避免重复存储
3. **降低网络带宽**：减少重复下载，节省 Nexus 服务器负载
4. **简化维护**：统一的缓存目录便于监控和清理

📌 **下一步：**

- 部署完成后，运行一次完整的 CI/CD Pipeline，验证缓存生效
- 监控 `/data/jenkins-cache` 磁盘使用率
- 设置定期清理任务（cron job）

---

**文档版本：** v1.0
**最后更新：** 2024-12-04
**维护者：** DevOps Team
