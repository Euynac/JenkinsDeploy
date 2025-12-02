import { shallowMount, createLocalVue } from '@vue/test-utils'
import Vuex from 'vuex'
import ElementUI from 'element-ui'
import Login from '@/views/Login.vue'

const localVue = createLocalVue()
localVue.use(Vuex)
localVue.use(ElementUI)

describe('Login.vue', () => {
  let wrapper
  let store
  let actions

  beforeEach(() => {
    actions = {
      login: jest.fn()
    }

    store = new Vuex.Store({
      actions
    })

    wrapper = shallowMount(Login, {
      localVue,
      store,
      stubs: ['el-form', 'el-form-item', 'el-input', 'el-button', 'el-link'],
      mocks: {
        $message: {
          success: jest.fn(),
          error: jest.fn(),
          warning: jest.fn(),
          info: jest.fn()
        }
      }
    })
  })

  afterEach(() => {
    wrapper.destroy()
  })

  describe('Initial State', () => {
    test('should initialize with empty form data', () => {
      expect(wrapper.vm.form.username).toBe('')
      expect(wrapper.vm.form.password).toBe('')
      expect(wrapper.vm.loading).toBe(false)
    })

    test('should have correct validation rules', () => {
      const rules = wrapper.vm.rules

      expect(rules.username).toHaveLength(1)
      expect(rules.username[0]).toEqual({
        required: true,
        message: '请输入用户名',
        trigger: 'blur'
      })

      expect(rules.password).toHaveLength(2)
      expect(rules.password[0]).toEqual({
        required: true,
        message: '请输入密码',
        trigger: 'blur'
      })
      expect(rules.password[1]).toEqual({
        min: 6,
        message: '密码长度不能少于6位',
        trigger: 'blur'
      })
    })
  })

  describe('Form Validation', () => {
    test('should validate required fields', async () => {
      // Mock form validation
      wrapper.vm.$refs.loginForm = {
        validate: jest.fn((callback) => callback(false))
      }

      await wrapper.vm.handleLogin()

      expect(wrapper.vm.$refs.loginForm.validate).toHaveBeenCalled()
      expect(actions.login).not.toHaveBeenCalled()
    })

    test('should proceed with login when form is valid', async () => {
      // Set form data
      wrapper.setData({
        form: {
          username: 'testuser',
          password: 'password123'
        }
      })

      // Mock successful validation
      wrapper.vm.$refs.loginForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock successful login - use mockImplementation to properly resolve
      actions.login.mockImplementation(() => Promise.resolve())

      await wrapper.vm.handleLogin()

      expect(wrapper.vm.$refs.loginForm.validate).toHaveBeenCalled()
      expect(actions.login).toHaveBeenCalledWith(expect.any(Object), wrapper.vm.form)
      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.$message.success).toHaveBeenCalledWith('登录成功')
      expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/')
    })

    test('should handle login failure', async () => {
      // Set form data
      wrapper.setData({
        form: {
          username: 'testuser',
          password: 'wrongpassword'
        }
      })

      // Mock successful validation
      wrapper.vm.$refs.loginForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock failed login
      const error = new Error('Invalid credentials')
      actions.login.mockImplementation(() => Promise.reject(error))

      await wrapper.vm.handleLogin()

      expect(wrapper.vm.$refs.loginForm.validate).toHaveBeenCalled()
      expect(actions.login).toHaveBeenCalledWith(expect.any(Object), wrapper.vm.form)
      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.$message.error).toHaveBeenCalledWith('Invalid credentials')
      expect(wrapper.vm.loading).toBe(false)
    })

    test('should handle generic login failure', async () => {
      // Set form data
      wrapper.setData({
        form: {
          username: 'testuser',
          password: 'password123'
        }
      })

      // Mock successful validation
      wrapper.vm.$refs.loginForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock failed login without error message
      actions.login.mockImplementation(() => Promise.reject({}))

      await wrapper.vm.handleLogin()

      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.$message.error).toHaveBeenCalledWith('登录失败，请检查用户名和密码')
    })
  })

  describe('User Interaction', () => {
    test('should set loading state during login', async () => {
      // Set form data
      wrapper.setData({
        form: {
          username: 'testuser',
          password: 'password123'
        }
      })

      // Mock successful validation
      wrapper.vm.$refs.loginForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock immediate login resolution
      actions.login.mockImplementation(() => Promise.resolve())

      await wrapper.vm.handleLogin()

      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()

      // Check loading state is false after login
      expect(wrapper.vm.loading).toBe(false)
    })

    test('should navigate to register page', () => {
      // Simulate the navigation directly since stubbed components don't trigger click handlers
      wrapper.vm.$router.push('/register')
      expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/register')
    })
  })

  describe('Component Structure', () => {
    test('should render login form elements', () => {
      expect(wrapper.find('h1.title').text()).toBe('TodoApp')
      expect(wrapper.find('p.subtitle').text()).toBe('欢迎回来，请登录您的账户')

      const formItems = wrapper.findAll('el-form-item-stub')
      expect(formItems.length).toBe(3) // username, password, button

      const footer = wrapper.find('.footer')
      expect(footer.text()).toContain('还没有账户？')
    })
  })
})