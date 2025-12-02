# TodoApp Backend

基于 .NET 8 的 Todo 管理 Web API 项目。

## 功能特性

- 用户认证（注册、登录）
- 项目管理（CRUD + 分页）
- Todo 管理（CRUD + 完成状态）

## 技术栈

- .NET 8
- PostgreSQL
- Entity Framework Core
- JWT 认证
- BCrypt 密码加密

## 数据库设置

使用 Docker Compose 启动 PostgreSQL 数据库：

```bash
docker-compose up -d
```

这将启动一个 PostgreSQL 容器，配置如下：
- 数据库名：`todoapp`
- 用户名：`postgres`
- 密码：`postgres`
- 端口：`5432`

停止数据库：

```bash
docker-compose down
```

## 配置

数据库连接字符串和 JWT 配置在 `appsettings.json` 中：

```json
{
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Port=5432;Database=todoapp;Username=postgres;Password=postgres"
  },
  "Jwt": {
    "Key": "YourSuperSecretKeyThatIsAtLeast32CharactersLong!",
    "Issuer": "TodoApp",
    "Audience": "TodoApp"
  }
}
```

## API 端点

### 认证
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录

### 项目
- `GET /api/projects` - 获取项目列表（分页）
- `POST /api/projects` - 创建项目
- `GET /api/projects/{id}` - 获取项目详情
- `PUT /api/projects/{id}` - 更新项目
- `DELETE /api/projects/{id}` - 删除项目
- `GET /api/projects/{id}/todos` - 获取项目的所有 Todo

### Todo
- `POST /api/todos` - 创建 Todo
- `PUT /api/todos/{id}` - 更新 Todo
- `DELETE /api/todos/{id}` - 删除 Todo
- `PATCH /api/todos/{id}/complete` - 切换 Todo 完成状态

**注意**：除认证接口外，其他接口都需要在请求头中携带 JWT Token：
```
Authorization: Bearer {token}
```

## 测试数据

项目在首次启动时会自动初始化测试数据（仅在数据库为空时执行），包括：

### 默认测试用户
- **用户名**：`admin`
- **密码**：`admin123`
- **邮箱**：`admin@example.com`

### 默认项目
1. **工作项目** - 日常工作相关的任务管理
2. **个人项目** - 个人生活相关的任务管理

### 默认 Todo
- **工作项目**下包含 3 个 Todo（1 个已完成，2 个待完成）
- **个人项目**下包含 3 个 Todo（1 个已完成，2 个待完成）

**注意**：
- 如果数据库中已存在用户数据，种子数据将不会自动添加，以避免覆盖现有数据。
- 如果遇到主键冲突错误（duplicate key value violates unique constraint），说明 PostgreSQL 序列未正确更新。解决方法：
  1. **推荐**：删除数据库并重新创建（数据会丢失）
     ```bash
     docker-compose down -v  # 删除数据库卷
     docker-compose up -d     # 重新创建数据库
     ```
  2. **保留数据**：手动更新序列（连接到数据库后执行）
     ```sql
     SELECT setval('"Users_Id_seq"', (SELECT MAX("Id") FROM "Users"));
     SELECT setval('"Projects_Id_seq"', (SELECT MAX("Id") FROM "Projects"));
     SELECT setval('"Todos_Id_seq"', (SELECT MAX("Id") FROM "Todos"));
     ```

## 运行项目

```bash
dotnet run
```

项目将在 `https://localhost:5001` 或 `http://localhost:5000` 运行。

Swagger UI 可在开发环境下访问：`https://localhost:5001/swagger`

