# 环境配置说明

本项目支持在 Mock 数据和真实后端 API 之间切换。

## 环境变量

### VUE_APP_USE_MOCK
- **类型**: `string` (true/false)
- **说明**: 是否使用 Mock 数据
  - `true`: 使用 Mock 数据，无需后端服务
  - `false`: 使用真实后端 API

### VUE_APP_API_BASE_URL
- **类型**: `string`
- **说明**: 后端 API 的基础 URL
- **默认值**: `http://localhost:5085`
- **示例**: 
  - `http://localhost:5085` (HTTP)
  - `https://localhost:7182` (HTTPS)
  - `https://api.example.com` (生产环境)

## 环境文件

### .env.development
开发环境配置，默认使用 Mock 数据。

```env
VUE_APP_USE_MOCK=true
VUE_APP_API_BASE_URL=http://localhost:5085
```

### .env.production
生产环境配置，使用真实 API。

```env
VUE_APP_USE_MOCK=false
VUE_APP_API_BASE_URL=http://localhost:5085
```

### .env.local
本地环境配置（可选），用于覆盖开发环境配置。
此文件不会被 Git 提交，适合个人本地开发使用。

## 使用方式

### 1. 使用 Mock 数据（默认）

直接运行，无需启动后端服务：

```bash
npm run serve
```

### 2. 使用真实后端 API

#### 方式一：修改 .env.development

编辑 `.env.development` 文件：

```env
VUE_APP_USE_MOCK=false
VUE_APP_API_BASE_URL=http://localhost:5085
```

#### 方式二：创建 .env.local（推荐）

创建 `.env.local` 文件（不会被 Git 提交）：

```env
VUE_APP_USE_MOCK=false
VUE_APP_API_BASE_URL=http://localhost:5085
```

然后启动后端服务：

```bash
# 在 todoapp-backend-api 目录下
cd ../todoapp-backend-api
dotnet run
```

再启动前端：

```bash
npm run serve
```

## 后端服务端口

根据 `todoapp-backend-api` 的配置：
- **HTTP**: `http://localhost:5085`
- **HTTPS**: `https://localhost:7182`

如果后端运行在其他端口，请相应修改 `VUE_APP_API_BASE_URL`。

## 注意事项

1. 修改环境变量后，需要重启开发服务器才能生效
2. `.env.local` 文件的优先级高于 `.env.development`
3. 生产环境构建时使用 `.env.production` 配置
4. 确保后端服务已启动并配置了正确的 CORS，否则会出现跨域错误
