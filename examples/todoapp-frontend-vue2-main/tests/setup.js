const Vue = require('vue').default || require('vue')

// 全局注册 Vue 插件
Vue.config.productionTip = false

// Mock Element UI 的 $message 和 $confirm
Vue.prototype.$message = {
  success: jest.fn(),
  error: jest.fn(),
  warning: jest.fn(),
  info: jest.fn()
}

Vue.prototype.$confirm = jest.fn().mockImplementation(() => Promise.resolve())

// Mock 路由跳转
Vue.prototype.$router = {
  push: jest.fn(),
  replace: jest.fn(),
  go: jest.fn(),
  back: jest.fn()
}

// 全局测试工具
window.alert = jest.fn()
window.confirm = jest.fn()

// 模拟 localStorage
Object.defineProperty(window, 'localStorage', {
  value: {
    getItem: jest.fn(),
    setItem: jest.fn(),
    removeItem: jest.fn(),
    clear: jest.fn()
  },
  writable: true
})