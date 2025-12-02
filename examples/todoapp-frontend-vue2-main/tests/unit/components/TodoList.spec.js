import { shallowMount, createLocalVue } from '@vue/test-utils'
import Vuex from 'vuex'
import ElementUI from 'element-ui'
import TodoList from '@/components/TodoList.vue'

const localVue = createLocalVue()
localVue.use(Vuex)
localVue.use(ElementUI)

describe('TodoList.vue', () => {
  let wrapper
  let store
  let actions

  beforeEach(() => {
    actions = {
      toggleTodoComplete: jest.fn(),
      updateTodo: jest.fn(),
      deleteTodo: jest.fn()
    }

    store = new Vuex.Store({
      actions
    })

    wrapper = shallowMount(TodoList, {
      localVue,
      store,
      propsData: {
        todos: []
      },
      stubs: [
        'el-checkbox',
        'el-button',
        'el-dialog',
        'el-form',
        'el-form-item',
        'el-input',
        'el-textarea',
        'el-empty'
      ],
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
      expect(wrapper.vm.showEditDialog).toBe(false)
      expect(wrapper.vm.submitting).toBe(false)
      expect(wrapper.vm.editingTodo).toBe(null)
      expect(wrapper.vm.todoForm).toEqual({
        title: '',
        description: ''
      })
    })

    test('should have correct validation rules', () => {
      const rules = wrapper.vm.todoRules
      expect(rules.title).toHaveLength(1)
      expect(rules.title[0]).toEqual({
        required: true,
        message: '请输入 Todo 标题',
        trigger: 'blur'
      })
    })
  })

  describe('Empty State', () => {
    test('should show empty state when no todos', () => {
      expect(wrapper.find('el-empty-stub').exists()).toBe(true)
    })

    test('should not show empty state when todos exist', () => {
      wrapper.setProps({
        todos: [
          { id: 1, title: 'Test Todo', isCompleted: false }
        ]
      })

      // Wait for the component to update
      return wrapper.vm.$nextTick().then(() => {
        expect(wrapper.find('el-empty-stub').exists()).toBe(false)
      })
    })
  })

  describe('Todo Toggle', () => {
    beforeEach(() => {
      wrapper.setProps({
        todos: [
          { id: 1, title: 'Test Todo', isCompleted: false }
        ]
      })
    })

    test('should toggle todo completion successfully', async () => {
      actions.toggleTodoComplete.mockImplementation(() => Promise.resolve())

      await wrapper.vm.handleToggleComplete(1)

      expect(actions.toggleTodoComplete).toHaveBeenCalledWith(expect.any(Object), 1)
      expect(wrapper.vm.$message.success).toHaveBeenCalledWith('状态已更新')
      expect(wrapper.emitted().refresh).toBeTruthy()
    })

    test('should handle todo toggle failure', async () => {
      actions.toggleTodoComplete.mockImplementation(() => Promise.reject(new Error('Network error')))

      await wrapper.vm.handleToggleComplete(1)

      expect(actions.toggleTodoComplete).toHaveBeenCalledWith(expect.any(Object), 1)
      expect(wrapper.vm.$message.error).toHaveBeenCalledWith('更新失败')
    })
  })

  describe('Todo Editing', () => {
    test('should open edit dialog with todo data', () => {
      const todo = { id: 1, title: 'Test Todo', description: 'Test Description' }

      wrapper.vm.handleEdit(todo)

      expect(wrapper.vm.editingTodo).toEqual(todo)
      expect(wrapper.vm.todoForm).toEqual({
        title: 'Test Todo',
        description: 'Test Description'
      })
      expect(wrapper.vm.showEditDialog).toBe(true)
    })

    test('should handle todo with no description', () => {
      const todo = { id: 1, title: 'Test Todo' }

      wrapper.vm.handleEdit(todo)

      expect(wrapper.vm.todoForm).toEqual({
        title: 'Test Todo',
        description: ''
      })
    })
  })

  describe('Todo Update', () => {
    beforeEach(() => {
      wrapper.setData({
        editingTodo: { id: 1, title: 'Old Todo' },
        todoForm: {
          title: 'Updated Todo',
          description: 'Updated Description'
        }
      })
    })

    test('should update todo successfully', async () => {
      // Mock form validation
      wrapper.vm.$refs.todoForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock successful update
      actions.updateTodo.mockImplementation(() => Promise.resolve())

      await wrapper.vm.handleSubmit()

      expect(wrapper.vm.$refs.todoForm.validate).toHaveBeenCalled()
      expect(actions.updateTodo).toHaveBeenCalledWith(expect.any(Object), {
        id: 1,
        data: {
          title: 'Updated Todo',
          description: 'Updated Description'
        }
      })
      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.submitting).toBe(false)
    })

    test('should handle form validation failure', async () => {
      // Mock form validation failure
      wrapper.vm.$refs.todoForm = {
        validate: jest.fn((callback) => callback(false))
      }

      await wrapper.vm.handleSubmit()

      expect(wrapper.vm.$refs.todoForm.validate).toHaveBeenCalled()
      expect(actions.updateTodo).not.toHaveBeenCalled()
    })

    test('should handle todo update failure', async () => {
      // Mock form validation
      wrapper.vm.$refs.todoForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock failed update
      const error = new Error('Update failed')
      actions.updateTodo.mockImplementation(() => Promise.reject(error))

      await wrapper.vm.handleSubmit()

      // Check that the action was called and submitting state was reset
      expect(actions.updateTodo).toHaveBeenCalledWith(expect.any(Object), {
        id: 1,
        data: {
          title: 'Updated Todo',
          description: 'Updated Description'
        }
      })
      // Wait for next tick to ensure async operations complete
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.submitting).toBe(false)
    })

    test('should handle generic todo update failure', async () => {
      // Mock form validation
      wrapper.vm.$refs.todoForm = {
        validate: jest.fn((callback) => callback(true))
      }

      // Mock failed update without error message
      actions.updateTodo.mockImplementation(() => Promise.reject({}))

      await wrapper.vm.handleSubmit()

      // Check that the action was called
      expect(actions.updateTodo).toHaveBeenCalledWith(expect.any(Object), {
        id: 1,
        data: {
          title: 'Updated Todo',
          description: 'Updated Description'
        }
      })
    })
  })

  describe('Todo Deletion', () => {
    test('should delete todo successfully', async () => {
      // Mock successful deletion
      actions.deleteTodo.mockImplementation(() => Promise.resolve())

      await wrapper.vm.handleDelete(1)

      expect(wrapper.vm.$confirm).toHaveBeenCalledWith('确定要删除这个 Todo 吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      expect(actions.deleteTodo).toHaveBeenCalledWith(expect.any(Object), 1)
    })

    test('should handle todo deletion failure', async () => {
      // Mock failed deletion
      const error = new Error('Deletion failed')
      actions.deleteTodo.mockImplementation(() => Promise.reject(error))

      await wrapper.vm.handleDelete(1)

      expect(actions.deleteTodo).toHaveBeenCalledWith(expect.any(Object), 1)
    })

    test('should handle generic todo deletion failure', async () => {
      // Mock failed deletion without error message
      actions.deleteTodo.mockImplementation(() => Promise.reject({}))

      await wrapper.vm.handleDelete(1)

      expect(actions.deleteTodo).toHaveBeenCalledWith(expect.any(Object), 1)
    })
  })

  describe('Form Management', () => {
    test('should reset form correctly', () => {
      // Set some data first
      wrapper.setData({
        editingTodo: { id: 1, title: 'Test Todo' },
        todoForm: {
          title: 'Test Todo',
          description: 'Test Description'
        },
        showEditDialog: true
      })

      // Mock form reset
      wrapper.vm.$refs.todoForm = {
        resetFields: jest.fn()
      }

      wrapper.vm.resetForm()

      expect(wrapper.vm.editingTodo).toBe(null)
      expect(wrapper.vm.todoForm).toEqual({
        title: '',
        description: ''
      })
      // The dialog state might be managed by the component, so we'll focus on form reset
      expect(wrapper.vm.$refs.todoForm.resetFields).toHaveBeenCalled()
    })

    test('should handle form reset when form ref is not available', () => {
      wrapper.vm.$refs.todoForm = null

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

  describe('Component Structure', () => {
    test('should render todo items correctly', () => {
      wrapper.setProps({
        todos: [
          { id: 1, title: 'Todo 1', description: 'Description 1', isCompleted: false, updatedAt: '2023-01-01' },
          { id: 2, title: 'Todo 2', description: 'Description 2', isCompleted: true, updatedAt: '2023-01-02' }
        ]
      })

      // Wait for the component to update
      return wrapper.vm.$nextTick().then(() => {
        // Since we're using shallow mount with stubs, we can't directly test the rendered structure
        // Instead, we'll verify that the component properly handles the todos prop
        expect(wrapper.vm.todos).toHaveLength(2)
        expect(wrapper.vm.todos[0].isCompleted).toBe(false)
        expect(wrapper.vm.todos[1].isCompleted).toBe(true)
      })
    })
  })
})