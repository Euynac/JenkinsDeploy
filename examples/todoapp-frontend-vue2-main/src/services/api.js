import axios from 'axios'
import mockService from './mockService'

const useMock = process.env.VUE_APP_USE_MOCK === 'true'
const baseURL = process.env.VUE_APP_API_BASE_URL || 'http://localhost:5085'

// 创建 axios 实例
const api = axios.create({
  baseURL: useMock ? '' : baseURL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    if (error.response) {
      if (error.response.status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        window.location.href = '/login'
      }
      return Promise.reject(error.response.data || error.message)
    }
    return Promise.reject(error.message)
  }
)

// API 方法
const apiService = {
  // 认证
  async register (data) {
    if (useMock) return mockService.register(data)
    return api.post('/api/auth/register', data)
  },

  async login (data) {
    if (useMock) return mockService.login(data)
    return api.post('/api/auth/login', data)
  },

  // 项目
  async getProjects (params = {}) {
    if (useMock) return mockService.getProjects(params)
    return api.get('/api/projects', { params })
  },

  async getProject (id) {
    if (useMock) return mockService.getProject(id)
    return api.get(`/api/projects/${id}`)
  },

  async createProject (data) {
    if (useMock) return mockService.createProject(data)
    return api.post('/api/projects', data)
  },

  async updateProject (id, data) {
    if (useMock) return mockService.updateProject(id, data)
    return api.put(`/api/projects/${id}`, data)
  },

  async deleteProject (id) {
    if (useMock) return mockService.deleteProject(id)
    return api.delete(`/api/projects/${id}`)
  },

  async getProjectTodos (projectId) {
    if (useMock) return mockService.getProjectTodos(projectId)
    return api.get(`/api/projects/${projectId}/todos`)
  },

  // Todo
  async createTodo (data) {
    if (useMock) return mockService.createTodo(data)
    return api.post('/api/todos', data)
  },

  async updateTodo (id, data) {
    if (useMock) return mockService.updateTodo(id, data)
    return api.put(`/api/todos/${id}`, data)
  },

  async deleteTodo (id) {
    if (useMock) return mockService.deleteTodo(id)
    return api.delete(`/api/todos/${id}`)
  },

  async toggleTodoComplete (id) {
    if (useMock) return mockService.toggleTodoComplete(id)
    return api.patch(`/api/todos/${id}/complete`)
  }
}

export default apiService
