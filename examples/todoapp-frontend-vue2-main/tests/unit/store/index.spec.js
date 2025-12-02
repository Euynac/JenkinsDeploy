import store from '@/store/index'

describe('Vuex Store', () => {
  describe('State', () => {
    test('should have correct initial state', () => {
      expect(store.state.user).toBeNull()
      expect(store.state.token).toBeNull()
      expect(store.state.projects).toEqual([])
      expect(store.state.currentProject).toBeNull()
      expect(store.state.todos).toEqual([])
    })
  })

  describe('Mutations', () => {
    describe('SET_USER', () => {
      test('should set user state and store in localStorage', () => {
        const user = { id: 1, username: 'testuser' }
        store.commit('SET_USER', user)
        expect(store.state.user).toEqual(user)
        expect(localStorage.setItem).toHaveBeenCalledWith('user', JSON.stringify(user))
      })

      test('should remove user from state and localStorage when null', () => {
        store.commit('SET_USER', null)
        expect(store.state.user).toBeNull()
        expect(localStorage.removeItem).toHaveBeenCalledWith('user')
      })
    })

    describe('SET_TOKEN', () => {
      test('should set token state and store in localStorage', () => {
        const token = 'test-token'
        store.commit('SET_TOKEN', token)
        expect(store.state.token).toBe(token)
        expect(localStorage.setItem).toHaveBeenCalledWith('token', token)
      })

      test('should remove token from state and localStorage when null', () => {
        store.commit('SET_TOKEN', null)
        expect(store.state.token).toBeNull()
        expect(localStorage.removeItem).toHaveBeenCalledWith('token')
      })
    })

    describe('Project Mutations', () => {
      beforeEach(() => {
        store.state.projects = [
          { id: 1, name: 'Project 1', description: 'Description 1' },
          { id: 2, name: 'Project 2', description: 'Description 2' }
        ]
      })

      test('SET_PROJECTS should replace projects array', () => {
        const newProjects = [{ id: 3, name: 'Project 3' }]
        store.commit('SET_PROJECTS', newProjects)
        expect(store.state.projects).toEqual(newProjects)
      })

      test('ADD_PROJECT should add project to array', () => {
        const newProject = { id: 3, name: 'Project 3' }
        store.commit('ADD_PROJECT', newProject)
        expect(store.state.projects).toHaveLength(3)
        expect(store.state.projects[2]).toEqual(newProject)
      })

      test('UPDATE_PROJECT should update existing project', () => {
        const updatedProject = { id: 1, name: 'Updated Project 1', description: 'Updated Description' }
        store.commit('UPDATE_PROJECT', updatedProject)
        expect(store.state.projects[0]).toEqual(updatedProject)
      })

      test('UPDATE_PROJECT should not update non-existent project', () => {
        const nonExistentProject = { id: 999, name: 'Non-existent' }
        store.commit('UPDATE_PROJECT', nonExistentProject)
        expect(store.state.projects).not.toContain(nonExistentProject)
      })

      test('REMOVE_PROJECT should remove project by id', () => {
        store.commit('REMOVE_PROJECT', 1)
        expect(store.state.projects).toHaveLength(1)
        expect(store.state.projects[0].id).toBe(2)
      })
    })

    describe('Todo Mutations', () => {
      beforeEach(() => {
        store.state.todos = [
          { id: 1, title: 'Todo 1', isCompleted: false },
          { id: 2, title: 'Todo 2', isCompleted: true }
        ]
      })

      test('SET_TODOS should replace todos array', () => {
        const newTodos = [{ id: 3, title: 'Todo 3' }]
        store.commit('SET_TODOS', newTodos)
        expect(store.state.todos).toEqual(newTodos)
      })

      test('ADD_TODO should add todo to array', () => {
        const newTodo = { id: 3, title: 'Todo 3' }
        store.commit('ADD_TODO', newTodo)
        expect(store.state.todos).toHaveLength(3)
        expect(store.state.todos[2]).toEqual(newTodo)
      })

      test('UPDATE_TODO should update existing todo', () => {
        const updatedTodo = { id: 1, title: 'Updated Todo 1', isCompleted: true }
        store.commit('UPDATE_TODO', updatedTodo)
        expect(store.state.todos[0]).toEqual(updatedTodo)
      })

      test('UPDATE_TODO should not update non-existent todo', () => {
        const nonExistentTodo = { id: 999, title: 'Non-existent' }
        store.commit('UPDATE_TODO', nonExistentTodo)
        expect(store.state.todos).not.toContain(nonExistentTodo)
      })

      test('REMOVE_TODO should remove todo by id', () => {
        store.commit('REMOVE_TODO', 1)
        expect(store.state.todos).toHaveLength(1)
        expect(store.state.todos[0].id).toBe(2)
      })
    })

    describe('SET_CURRENT_PROJECT', () => {
      test('should set current project', () => {
        const project = { id: 1, name: 'Test Project' }
        store.commit('SET_CURRENT_PROJECT', project)
        expect(store.state.currentProject).toEqual(project)
      })
    })
  })

  describe('Getters', () => {
    beforeEach(() => {
      store.state.token = 'test-token'
      store.state.user = { id: 1, username: 'testuser' }
      store.state.todos = [
        { id: 1, title: 'Todo 1', isCompleted: false },
        { id: 2, title: 'Todo 2', isCompleted: true },
        { id: 3, title: 'Todo 3', isCompleted: false }
      ]
    })

    test('isAuthenticated should return true when token exists', () => {
      expect(store.getters.isAuthenticated).toBe(true)
    })

    test('isAuthenticated should return false when no token', () => {
      store.state.token = null
      expect(store.getters.isAuthenticated).toBe(false)
    })

    test('currentUser should return user state', () => {
      expect(store.getters.currentUser).toEqual({ id: 1, username: 'testuser' })
    })

    test('completedTodos should return only completed todos', () => {
      const completed = store.getters.completedTodos
      expect(completed).toHaveLength(1)
      expect(completed[0].id).toBe(2)
      expect(completed[0].isCompleted).toBe(true)
    })

    test('pendingTodos should return only pending todos', () => {
      const pending = store.getters.pendingTodos
      expect(pending).toHaveLength(2)
      expect(pending.every(todo => !todo.isCompleted)).toBe(true)
    })
  })

  describe('Actions', () => {
    beforeEach(() => {
      // 在每个测试前重置 store 状态
      store.state.user = null
      store.state.token = null
      store.state.projects = []
      store.state.todos = []
      store.state.currentProject = null
    })

    test('logout action should clear all state', () => {
      // Set some initial state
      store.state.token = 'test-token'
      store.state.user = { id: 1, username: 'test' }
      store.state.projects = [{ id: 1, name: 'Project 1' }]
      store.state.currentProject = { id: 1, name: 'Project 1' }
      store.state.todos = [{ id: 1, title: 'Todo 1' }]

      store.dispatch('logout')

      expect(store.state.token).toBeNull()
      expect(store.state.user).toBeNull()
      expect(store.state.projects).toEqual([])
      expect(store.state.currentProject).toBeNull()
      expect(store.state.todos).toEqual([])
    })
  })
})