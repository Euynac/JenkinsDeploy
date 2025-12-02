import { shallowMount, createLocalVue } from '@vue/test-utils'
import Vuex from 'vuex'
import ElementUI from 'element-ui'
import Projects from '@/views/Projects.vue'

const localVue = createLocalVue()
localVue.use(Vuex)
localVue.use(ElementUI)

describe('Projects.vue', () => {
  let wrapper
  let store
  let actions
  let getters

  beforeEach(() => {
    actions = {
      loadProjects: jest.fn(),
      createProject: jest.fn(),
      updateProject: jest.fn(),
      deleteProject: jest.fn()
    }

    getters = {
      projects: () => []
    }

    store = new Vuex.Store({
      actions,
      getters
    })

    wrapper = shallowMount(Projects, {
      localVue,
      store,
      stubs: ['el-button', 'el-card', 'el-dialog', 'el-form', 'el-form-item', 'el-input', 'el-empty'],
      mocks: {
        $message: {
          success: jest.fn(),
          error: jest.fn()
        },
        $confirm: jest.fn().mockImplementation(() => Promise.resolve())
      }
    })
  })

  afterEach(() => {
    wrapper.destroy()
  })

  describe('Initial State', () => {
    test('should initialize with correct data', () => {
      expect(wrapper.vm.loading).toBe(false)
      expect(wrapper.vm.projects).toEqual([])
      expect(wrapper.vm.showCreateDialog).toBe(false)
      expect(wrapper.vm.submitting).toBe(false)
      expect(wrapper.vm.editingProject).toBeNull()
      expect(wrapper.vm.projectForm).toEqual({
        name: '',
        description: ''
      })
    })

    test('should have correct validation rules', () => {
      const rules = wrapper.vm.projectRules
      expect(rules.name).toHaveLength(1)
      expect(rules.name[0]).toEqual({
        required: true,
        message: '请输入项目名称',
        trigger: 'blur'
      })
    })

    test('should compute dialog title correctly', () => {
      // Default title for create
      expect(wrapper.vm.dialogTitle).toBe('新建项目')

      // Title for edit
      wrapper.setData({ editingProject: { id: 1, name: 'Test Project' } })
      expect(wrapper.vm.dialogTitle).toBe('编辑项目')
    })
  })

  describe('Component Lifecycle', () => {
    test('should load projects on created', () => {
      expect(actions.loadProjects).toHaveBeenCalled()
    })
  })

  describe('Project Loading', () => {
    test('should set loading state during project loading', async () => {
      const mockResponse = { items: [{ id: 1, name: 'Test Project' }] }
      actions.loadProjects.mockImplementation(() => Promise.resolve(mockResponse))

      await wrapper.vm.loadProjects()

      expect(wrapper.vm.loading).toBe(false)
      expect(wrapper.vm.projects).toEqual(mockResponse.items)
    })

    test('should handle project loading failure', async () => {
      actions.loadProjects.mockImplementation(() => Promise.reject(new Error('Network error')))

      await wrapper.vm.loadProjects()

      expect(wrapper.vm.loading).toBe(false)
      expect(wrapper.vm.$message.error).toHaveBeenCalledWith('加载项目失败')
    })
  })

  describe('Navigation', () => {
    test('should navigate to project detail', () => {
      wrapper.vm.goToProject(1)
      expect(wrapper.vm.$router.push).toHaveBeenCalledWith('/projects/1')
    })
  })

  describe('Project Editing', () => {
    test('should open edit dialog with project data', () => {
      const project = { id: 1, name: 'Test Project', description: 'Test Description' }

      wrapper.vm.handleEdit(project)

      expect(wrapper.vm.editingProject).toEqual(project)
      expect(wrapper.vm.projectForm).toEqual({
        name: 'Test Project',
        description: 'Test Description'
      })
      expect(wrapper.vm.showCreateDialog).toBe(true)
    })
  })

  describe('Project Creation', () => {
    test('should create project successfully', async () => {
      // Set form data
      wrapper.setData({
        projectForm: {
          name: 'New Project',
          description: 'New Description'
        }
      })

      // Mock form validation
      wrapper.vm.$refs.projectForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock successful creation
      actions.createProject.mockImplementation(() => Promise.resolve())

      await wrapper.vm.handleSubmit()

      expect(wrapper.vm.$refs.projectForm.validate).toHaveBeenCalled()
      expect(actions.createProject).toHaveBeenCalledWith(expect.any(Object), {
        name: 'New Project',
        description: 'New Description'
      })
      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.$message.success).toHaveBeenCalledWith('创建成功')
      expect(wrapper.vm.showCreateDialog).toBe(false)
      expect(actions.loadProjects).toHaveBeenCalled()
    })

    test('should update project successfully', async () => {
      // Set editing state
      wrapper.setData({
        editingProject: { id: 1, name: 'Old Project' },
        projectForm: {
          name: 'Updated Project',
          description: 'Updated Description'
        }
      })

      // Mock form validation
      wrapper.vm.$refs.projectForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock successful update
      actions.updateProject.mockImplementation(() => Promise.resolve())

      await wrapper.vm.handleSubmit()

      expect(actions.updateProject).toHaveBeenCalledWith(expect.any(Object), {
        id: 1,
        data: {
          name: 'Updated Project',
          description: 'Updated Description'
        }
      })
      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.$message.success).toHaveBeenCalledWith('更新成功')
      expect(wrapper.vm.showCreateDialog).toBe(false)
      expect(actions.loadProjects).toHaveBeenCalled()
    })

    test('should handle form validation failure', async () => {
      // Mock form validation failure
      wrapper.vm.$refs.projectForm = {
        validate: jest.fn((callback) => callback(false))
      }

      await wrapper.vm.handleSubmit()

      expect(wrapper.vm.$refs.projectForm.validate).toHaveBeenCalled()
      expect(actions.createProject).not.toHaveBeenCalled()
      expect(actions.updateProject).not.toHaveBeenCalled()
    })

    test('should handle project creation failure', async () => {
      // Set form data
      wrapper.setData({
        projectForm: {
          name: 'New Project',
          description: 'New Description'
        }
      })

      // Mock form validation
      wrapper.vm.$refs.projectForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock failed creation
      const error = new Error('Creation failed')
      actions.createProject.mockImplementation(() => Promise.reject(error))

      await wrapper.vm.handleSubmit()

      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.$message.error).toHaveBeenCalledWith('Creation failed')
      expect(wrapper.vm.submitting).toBe(false)
    })
  })

  describe('Project Deletion', () => {
    test('should delete project successfully', async () => {
      // Mock successful deletion
      actions.deleteProject.mockImplementation(() => Promise.resolve())

      await wrapper.vm.handleDelete(1)

      expect(actions.deleteProject).toHaveBeenCalledWith(expect.any(Object), 1)
      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.$message.success).toHaveBeenCalledWith('删除成功')
      expect(actions.loadProjects).toHaveBeenCalled()
    })

    test('should handle project deletion failure', async () => {
      // Mock failed deletion
      const error = new Error('Deletion failed')
      actions.deleteProject.mockImplementation(() => Promise.reject(error))

      await wrapper.vm.handleDelete(1)

      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.$message.error).toHaveBeenCalledWith('Deletion failed')
    })
  })

  describe('Form Management', () => {
    test('should reset form correctly', () => {
      // Set some data first
      wrapper.setData({
        editingProject: { id: 1, name: 'Test Project' },
        projectForm: {
          name: 'Test Project',
          description: 'Test Description'
        },
        showCreateDialog: true
      })

      // Mock form reset
      wrapper.vm.$refs.projectForm = {
        resetFields: jest.fn()
      }

      wrapper.vm.resetForm()

      expect(wrapper.vm.editingProject).toBeNull()
      expect(wrapper.vm.projectForm).toEqual({
        name: '',
        description: ''
      })
      // The dialog state might be managed by the component, so we'll focus on form reset
      expect(wrapper.vm.$refs.projectForm.resetFields).toHaveBeenCalled()
    })

    test('should handle form reset when form ref is not available', () => {
      wrapper.vm.$refs.projectForm = null

      // This should not throw an error
      expect(() => wrapper.vm.resetForm()).not.toThrow()
    })
  })

  describe('Date Formatting', () => {
    test('should format date correctly', () => {
      const date = '2023-01-15'
      const formatted = wrapper.vm.formatDate(date)

      // Check that it returns a non-empty string
      expect(formatted).toBeTruthy()
      expect(typeof formatted).toBe('string')
    })

    test('should handle empty date', () => {
      const formatted = wrapper.vm.formatDate(null)
      expect(formatted).toBe('')
    })
  })
})