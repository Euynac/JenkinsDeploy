# TodoApp Frontend

基于 Vue 2.x 的 Todo 管理前端应用。

## 功能特性

- 🎨 美观的现代化 UI 设计
- 🔐 用户认证（登录、注册）
- 📁 项目管理（创建、编辑、删除、查看）
- ✅ Todo 管理（创建、编辑、删除、完成状态切换）
- 🔄 Mock 数据支持，无需后端即可运行
- 🌐 支持切换真实 API 和 Mock 模式

## 技术栈

- Vue 2.7
- Vue Router 3.x
- Vuex 3.x
- Element UI 2.x
- Axios
- SCSS

## 环境要求

- Node.js >= 14.x
- npm >= 6.x

## 安装依赖

```bash
npm install
```

## 开发模式

### 使用 Mock 数据（默认）

项目默认使用 Mock 数据，无需后端即可运行：

```bash
npm run serve
```

应用将在 `http://localhost:8080` 启动。

### 使用真实 API

要使用真实的后端 API，需要修改 `.env.development` 文件：

```env
VUE_APP_API_BASE_URL=http://localhost:5000
VUE_APP_USE_MOCK=false
```

然后启动后端服务，再运行前端：

```bash
npm run serve
```

## 环境变量配置

### 开发环境 (.env.development)

```env
VUE_APP_API_BASE_URL=http://localhost:5000
VUE_APP_USE_MOCK=true
```

### 生产环境 (.env.production)

```env
VUE_APP_API_BASE_URL=http://localhost:5000
VUE_APP_USE_MOCK=false
```

## 构建生产版本

```bash
npm run build
```

构建后的文件将输出到 `dist` 目录。

## 项目结构

```
TodoApp-frontend/
├── public/              # 静态资源
├── src/
│   ├── components/      # 组件
│   │   └── TodoList.vue
│   ├── views/           # 页面视图
│   │   ├── Login.vue
│   │   ├── Register.vue
│   │   ├── Layout.vue
│   │   ├── Projects.vue
│   │   └── ProjectDetail.vue
│   ├── router/          # 路由配置
│   ├── store/           # Vuex 状态管理
│   ├── services/        # API 服务
│   │   ├── api.js       # API 调用封装
│   │   └── mockService.js # Mock 数据服务
│   ├── styles/          # 样式文件
│   └── main.js          # 入口文件
├── .env.development     # 开发环境配置
├── .env.production      # 生产环境配置
└── vue.config.js        # Vue CLI 配置
```

## Mock 数据说明

项目内置了 Mock 服务，提供以下测试数据：

- **默认用户**：
  - 用户名：`admin`
  - 密码：`admin123`

- **默认项目**：
  - 工作项目
  - 个人项目

- **默认 Todo**：
  - 每个项目下都有示例 Todo 数据

## API 接口

项目实现了以下 API 接口：

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

## 使用说明

1. **注册/登录**：
   - 首次使用需要注册账户
   - 或使用 Mock 模式下的默认账户登录

2. **项目管理**：
   - 在项目列表页面可以创建、编辑、删除项目
   - 点击项目卡片可以进入项目详情页面

3. **Todo 管理**：
   - 在项目详情页面可以创建、编辑、删除 Todo
   - 可以通过复选框切换 Todo 的完成状态
   - 支持按全部/待完成/已完成筛选

## 注意事项

- 使用 Mock 模式时，数据仅存储在内存中，刷新页面后数据会重置
- 切换到真实 API 模式时，确保后端服务已启动并配置正确的 CORS
- JWT Token 存储在 localStorage 中，退出登录时会清除

## 开发建议

- 使用 Element UI 组件库保持 UI 一致性
- 遵循 Vue 2.x 的最佳实践
- 使用 Vuex 管理全局状态
- 使用 Vue Router 进行路由管理

