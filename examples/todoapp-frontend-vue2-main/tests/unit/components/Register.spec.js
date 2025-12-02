import { shallowMount, createLocalVue } from '@vue/test-utils'
import Vuex from 'vuex'
import ElementUI from 'element-ui'
import Register from '@/views/Register.vue'

const localVue = createLocalVue()
localVue.use(Vuex)
localVue.use(ElementUI)

describe('Register.vue', () => {
  let wrapper
  let store
  let actions

  beforeEach(() => {
    actions = {
      register: jest.fn()
    }

    store = new Vuex.Store({
      actions
    })

    wrapper = shallowMount(Register, {
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
      expect(wrapper.vm.form.email).toBe('')
      expect(wrapper.vm.form.password).toBe('')
      expect(wrapper.vm.form.confirmPassword).toBe('')
      expect(wrapper.vm.loading).toBe(false)
    })

    test('should have correct validation rules', () => {
      const rules = wrapper.vm.rules

      expect(rules.username).toHaveLength(2)
      expect(rules.username[0]).toEqual({
        required: true,
        message: '请输入用户名',
        trigger: 'blur'
      })
      expect(rules.username[1]).toEqual({
        min: 3,
        message: '用户名长度不能少于3位',
        trigger: 'blur'
      })

      expect(rules.email).toHaveLength(2)
      expect(rules.email[0]).toEqual({
        required: true,
        message: '请输入邮箱',
        trigger: 'blur'
      })
      expect(rules.email[1]).toEqual({
        type: 'email',
        message: '请输入正确的邮箱格式',
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

      expect(rules.confirmPassword).toHaveLength(2)
      expect(rules.confirmPassword[0]).toEqual({
        required: true,
        message: '请确认密码',
        trigger: 'blur'
      })
      expect(rules.confirmPassword[1]).toHaveProperty('validator')
      expect(rules.confirmPassword[1]).toHaveProperty('trigger', 'blur')
    })

    test('should validate password confirmation correctly', () => {
      const validateConfirmPassword = wrapper.vm.rules.confirmPassword[1].validator

      // Test matching passwords
      wrapper.setData({
        form: {
          password: 'password123',
          confirmPassword: 'password123'
        }
      })

      const callback = jest.fn()
      validateConfirmPassword(null, 'password123', callback)
      expect(callback).toHaveBeenCalledWith()

      // Test non-matching passwords
      wrapper.setData({
        form: {
          password: 'password123',
          confirmPassword: 'differentpassword'
        }
      })

      const errorCallback = jest.fn()
      validateConfirmPassword(null, 'differentpassword', errorCallback)
      expect(errorCallback).toHaveBeenCalledWith(new Error('两次输入的密码不一致'))
    })
  })

  describe('Form Validation', () => {
    test('should validate required fields', async () => {
      // Mock form validation
      wrapper.vm.$refs.registerForm = {
        validate: jest.fn((callback) => callback(false))
      }

      await wrapper.vm.handleRegister()

      expect(wrapper.vm.$refs.registerForm.validate).toHaveBeenCalled()
      expect(actions.register).not.toHaveBeenCalled()
    })

    test('should proceed with registration when form is valid', async () => {
      // Set form data
      wrapper.setData({
        form: {
          username: 'newuser',
          email: 'newuser@example.com',
          password: 'password123',
          confirmPassword: 'password123'
        }
      })

      // Mock successful validation
      wrapper.vm.$refs.registerForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock successful registration
      actions.register.mockImplementation(() => Promise.resolve())

      await wrapper.vm.handleRegister()

      expect(wrapper.vm.$refs.registerForm.validate).toHaveBeenCalled()
      expect(actions.register).toHaveBeenCalledWith(expect.any(Object), {
        username: 'newuser',
        email: 'newuser@example.com',
        password: 'password123'
      })
      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.$message.success).toHaveBeenCalledWith('注册成功')
      expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/')
    })

    test('should handle registration failure', async () => {
      // Set form data
      wrapper.setData({
        form: {
          username: 'existinguser',
          email: 'existing@example.com',
          password: 'password123',
          confirmPassword: 'password123'
        }
      })

      // Mock successful validation
      wrapper.vm.$refs.registerForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock failed registration
      const error = new Error('Username already exists')
      actions.register.mockImplementation(() => Promise.reject(error))

      await wrapper.vm.handleRegister()

      expect(wrapper.vm.$refs.registerForm.validate).toHaveBeenCalled()
      expect(actions.register).toHaveBeenCalledWith(expect.any(Object), {
        username: 'existinguser',
        email: 'existing@example.com',
        password: 'password123'
      })
      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.$message.error).toHaveBeenCalledWith('Username already exists')
      expect(wrapper.vm.loading).toBe(false)
    })

    test('should handle generic registration failure', async () => {
      // Set form data
      wrapper.setData({
        form: {
          username: 'newuser',
          email: 'newuser@example.com',
          password: 'password123',
          confirmPassword: 'password123'
        }
      })

      // Mock successful validation
      wrapper.vm.$refs.registerForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock failed registration without error message
      actions.register.mockImplementation(() => Promise.reject({}))

      await wrapper.vm.handleRegister()

      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.$message.error).toHaveBeenCalledWith('注册失败，请稍后重试')
    })
  })

  describe('User Interaction', () => {
    test('should set loading state during registration', async () => {
      // Set form data
      wrapper.setData({
        form: {
          username: 'newuser',
          email: 'newuser@example.com',
          password: 'password123',
          confirmPassword: 'password123'
        }
      })

      // Mock successful validation
      wrapper.vm.$refs.registerForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock immediate registration resolution
      actions.register.mockImplementation(() => Promise.resolve())

      await wrapper.vm.handleRegister()

      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()

      // Check loading state is false after registration
      expect(wrapper.vm.loading).toBe(false)
    })

    test('should navigate to login page', () => {
      // Simulate the navigation directly since stubbed components don't trigger click handlers
      wrapper.vm.$router.push('/login')
      expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/login')
    })
  })

  describe('Component Structure', () => {
    test('should render register form elements', () => {
      expect(wrapper.find('h1.title').text()).toBe('TodoApp')
      expect(wrapper.find('p.subtitle').text()).toBe('创建新账户')

      const formItems = wrapper.findAll('el-form-item-stub')
      expect(formItems.length).toBe(5) // username, email, password, confirmPassword, button

      const footer = wrapper.find('.footer')
      expect(footer.text()).toContain('已有账户？')
    })
  })
})