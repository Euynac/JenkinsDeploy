// Mock 数据存储
const mockUsers = [
  { id: 1, username: 'admin', email: 'admin@example.com', password: 'admin123' }
]

const mockProjects = [
  { id: 1, name: '工作项目', description: '日常工作相关', userId: 1, createdAt: new Date(), updatedAt: new Date() },
  { id: 2, name: '个人项目', description: '个人学习项目', userId: 1, createdAt: new Date(), updatedAt: new Date() }
]

let mockTodos = [
  { id: 1, title: '完成项目文档', description: '编写项目说明文档', isCompleted: false, projectId: 1, createdAt: new Date(), updatedAt: new Date() },
  { id: 2, title: '代码审查', description: '审查团队代码', isCompleted: true, projectId: 1, createdAt: new Date(), updatedAt: new Date() },
  { id: 3, title: '学习 Vue 3', description: '学习 Vue 3 新特性', isCompleted: false, projectId: 2, createdAt: new Date(), updatedAt: new Date() }
]

let currentUserId = null
let nextProjectId = 3
let nextTodoId = 4

// 模拟延迟
const delay = (ms = 300) => new Promise(resolve => setTimeout(resolve, ms))

const mockService = {
  // 认证
  async register (data) {
    await delay()
    const existingUser = mockUsers.find(u => u.username === data.username || u.email === data.email)
    if (existingUser) {
      throw new Error('用户名或邮箱已存在')
    }
    const newUser = {
      id: mockUsers.length + 1,
      username: data.username,
      email: data.email,
      password: data.password
    }
    mockUsers.push(newUser)
    currentUserId = newUser.id
    return {
      token: `mock_token_${newUser.id}`,
      userId: newUser.id,
      username: newUser.username
    }
  },

  async login (data) {
    await delay()
    const user = mockUsers.find(u => u.username === data.username && u.password === data.password)
    if (!user) {
      throw new Error('用户名或密码错误')
    }
    currentUserId = user.id
    return {
      token: `mock_token_${user.id}`,
      userId: user.id,
      username: user.username
    }
  },

  // 项目
  async getProjects (params = {}) {
    await delay()
    const pageNumber = parseInt(params.pageNumber) || 1
    const pageSize = parseInt(params.pageSize) || 10
    const userProjects = mockProjects.filter(p => p.userId === currentUserId)
    const totalCount = userProjects.length
    const start = (pageNumber - 1) * pageSize
    const items = userProjects.slice(start, start + pageSize)

    return {
      items: items.map(p => ({
        id: p.id,
        name: p.name,
        description: p.description,
        createdAt: p.createdAt,
        updatedAt: p.updatedAt
      })),
      totalCount,
      pageNumber,
      pageSize,
      totalPages: Math.ceil(totalCount / pageSize)
    }
  },

  async getProject (id) {
    await delay()
    const project = mockProjects.find(p => p.id === parseInt(id) && p.userId === currentUserId)
    if (!project) {
      throw new Error('项目不存在')
    }
    return {
      id: project.id,
      name: project.name,
      description: project.description,
      createdAt: project.createdAt,
      updatedAt: project.updatedAt
    }
  },

  async createProject (data) {
    await delay()
    const newProject = {
      id: nextProjectId++,
      name: data.name,
      description: data.description,
      userId: currentUserId,
      createdAt: new Date(),
      updatedAt: new Date()
    }
    mockProjects.push(newProject)
    return {
      id: newProject.id,
      name: newProject.name,
      description: newProject.description,
      createdAt: newProject.createdAt,
      updatedAt: newProject.updatedAt
    }
  },

  async updateProject (id, data) {
    await delay()
    const project = mockProjects.find(p => p.id === parseInt(id) && p.userId === currentUserId)
    if (!project) {
      throw new Error('项目不存在')
    }
    project.name = data.name
    project.description = data.description
    project.updatedAt = new Date()
    return {
      id: project.id,
      name: project.name,
      description: project.description,
      createdAt: project.createdAt,
      updatedAt: project.updatedAt
    }
  },

  async deleteProject (id) {
    await delay()
    const index = mockProjects.findIndex(p => p.id === parseInt(id) && p.userId === currentUserId)
    if (index === -1) {
      throw new Error('项目不存在')
    }
    mockProjects.splice(index, 1)
    // 同时删除项目下的所有 Todo
    mockTodos = mockTodos.filter(t => t.projectId !== parseInt(id))
    return {}
  },

  async getProjectTodos (projectId) {
    await delay()
    const todos = mockTodos.filter(t => t.projectId === parseInt(projectId))
    return todos.map(t => ({
      id: t.id,
      title: t.title,
      description: t.description,
      isCompleted: t.isCompleted,
      projectId: t.projectId,
      createdAt: t.createdAt,
      updatedAt: t.updatedAt
    }))
  },

  // Todo
  async createTodo (data) {
    await delay()
    const newTodo = {
      id: nextTodoId++,
      title: data.title,
      description: data.description,
      isCompleted: false,
      projectId: data.projectId,
      createdAt: new Date(),
      updatedAt: new Date()
    }
    mockTodos.push(newTodo)
    return {
      id: newTodo.id,
      title: newTodo.title,
      description: newTodo.description,
      isCompleted: newTodo.isCompleted,
      projectId: newTodo.projectId,
      createdAt: newTodo.createdAt,
      updatedAt: newTodo.updatedAt
    }
  },

  async updateTodo (id, data) {
    await delay()
    const todo = mockTodos.find(t => t.id === parseInt(id))
    if (!todo) {
      throw new Error('Todo 不存在')
    }
    todo.title = data.title
    todo.description = data.description
    todo.updatedAt = new Date()
    return {
      id: todo.id,
      title: todo.title,
      description: todo.description,
      isCompleted: todo.isCompleted,
      projectId: todo.projectId,
      createdAt: todo.createdAt,
      updatedAt: todo.updatedAt
    }
  },

  async deleteTodo (id) {
    await delay()
    const index = mockTodos.findIndex(t => t.id === parseInt(id))
    if (index === -1) {
      throw new Error('Todo 不存在')
    }
    mockTodos.splice(index, 1)
    return {}
  },

  async toggleTodoComplete (id) {
    await delay()
    const todo = mockTodos.find(t => t.id === parseInt(id))
    if (!todo) {
      throw new Error('Todo 不存在')
    }
    todo.isCompleted = !todo.isCompleted
    todo.updatedAt = new Date()
    return {
      id: todo.id,
      title: todo.title,
      description: todo.description,
      isCompleted: todo.isCompleted,
      projectId: todo.projectId,
      createdAt: todo.createdAt,
      updatedAt: todo.updatedAt
    }
  }
}

export default mockService
