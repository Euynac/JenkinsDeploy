import Vue from 'vue'
import Vuex from 'vuex'
import apiService from '@/services/api'

Vue.use(Vuex)

export default new Vuex.Store({
  state: {
    user: null,
    token: null,
    projects: [],
    currentProject: null,
    todos: []
  },
  mutations: {
    SET_USER (state, user) {
      state.user = user
      if (user) {
        localStorage.setItem('user', JSON.stringify(user))
      } else {
        localStorage.removeItem('user')
      }
    },
    SET_TOKEN (state, token) {
      state.token = token
      if (token) {
        localStorage.setItem('token', token)
      } else {
        localStorage.removeItem('token')
      }
    },
    SET_PROJECTS (state, projects) {
      state.projects = projects
    },
    ADD_PROJECT (state, project) {
      state.projects.push(project)
    },
    UPDATE_PROJECT (state, project) {
      const index = state.projects.findIndex(p => p.id === project.id)
      if (index !== -1) {
        Vue.set(state.projects, index, project)
      }
    },
    REMOVE_PROJECT (state, projectId) {
      state.projects = state.projects.filter(p => p.id !== projectId)
    },
    SET_CURRENT_PROJECT (state, project) {
      state.currentProject = project
    },
    SET_TODOS (state, todos) {
      state.todos = todos
    },
    ADD_TODO (state, todo) {
      state.todos.push(todo)
    },
    UPDATE_TODO (state, todo) {
      const index = state.todos.findIndex(t => t.id === todo.id)
      if (index !== -1) {
        Vue.set(state.todos, index, todo)
      }
    },
    REMOVE_TODO (state, todoId) {
      state.todos = state.todos.filter(t => t.id !== todoId)
    }
  },
  actions: {
    async login ({ commit }, credentials) {
      const response = await apiService.login(credentials)
      commit('SET_TOKEN', response.token)
      commit('SET_USER', { id: response.userId, username: response.username })
      return response
    },
    async register ({ commit }, userData) {
      const response = await apiService.register(userData)
      commit('SET_TOKEN', response.token)
      commit('SET_USER', { id: response.userId, username: response.username })
      return response
    },
    logout ({ commit }) {
      commit('SET_TOKEN', null)
      commit('SET_USER', null)
      commit('SET_PROJECTS', [])
      commit('SET_CURRENT_PROJECT', null)
      commit('SET_TODOS', [])
    },
    async loadProjects ({ commit }, params = {}) {
      const response = await apiService.getProjects(params)
      commit('SET_PROJECTS', response.items)
      return response
    },
    async createProject ({ commit }, projectData) {
      const project = await apiService.createProject(projectData)
      commit('ADD_PROJECT', project)
      return project
    },
    async updateProject ({ commit }, { id, data }) {
      const project = await apiService.updateProject(id, data)
      commit('UPDATE_PROJECT', project)
      return project
    },
    async deleteProject ({ commit }, id) {
      await apiService.deleteProject(id)
      commit('REMOVE_PROJECT', id)
    },
    async loadProjectTodos ({ commit }, projectId) {
      const todos = await apiService.getProjectTodos(projectId)
      commit('SET_TODOS', todos)
      return todos
    },
    async createTodo ({ commit }, todoData) {
      const todo = await apiService.createTodo(todoData)
      commit('ADD_TODO', todo)
      return todo
    },
    async updateTodo ({ commit }, { id, data }) {
      const todo = await apiService.updateTodo(id, data)
      commit('UPDATE_TODO', todo)
      return todo
    },
    async deleteTodo ({ commit }, id) {
      await apiService.deleteTodo(id)
      commit('REMOVE_TODO', id)
    },
    async toggleTodoComplete ({ commit }, id) {
      const todo = await apiService.toggleTodoComplete(id)
      commit('UPDATE_TODO', todo)
      return todo
    }
  },
  getters: {
    isAuthenticated: state => !!state.token,
    currentUser: state => state.user,
    completedTodos: state => state.todos.filter(t => t.isCompleted),
    pendingTodos: state => state.todos.filter(t => !t.isCompleted)
  }
})
