<template>
  <div class="project-detail-container">
    <div class="page-header" v-if="project">
      <div>
        <el-button
          type="text"
          icon="el-icon-arrow-left"
          @click="$router.push('/projects')"
        >
          返回
        </el-button>
        <h2>{{ project.name }}</h2>
        <p class="project-description">{{ project.description || '暂无描述' }}</p>
      </div>
      <el-button type="primary" icon="el-icon-plus" @click="showTodoDialog = true">
        新建 Todo
      </el-button>
    </div>

    <div class="todos-section" v-loading="loading">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="全部" name="all">
          <todo-list :todos="todos" @refresh="loadTodos" />
        </el-tab-pane>
        <el-tab-pane :label="`待完成 (${pendingCount})`" name="pending">
          <todo-list :todos="pendingTodos" @refresh="loadTodos" />
        </el-tab-pane>
        <el-tab-pane :label="`已完成 (${completedCount})`" name="completed">
          <todo-list :todos="completedTodos" @refresh="loadTodos" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 创建/编辑 Todo 对话框 -->
    <el-dialog
      :title="todoDialogTitle"
      :visible.sync="showTodoDialog"
      width="500px"
      @close="resetTodoForm"
    >
      <el-form
        ref="todoForm"
        :model="todoForm"
        :rules="todoRules"
        label-width="80px"
      >
        <el-form-item label="标题" prop="title">
          <el-input v-model="todoForm.title" placeholder="请输入 Todo 标题" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="todoForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入 Todo 描述"
          />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="showTodoDialog = false">取消</el-button>
        <el-button type="primary" @click="handleTodoSubmit" :loading="submitting">
          确定
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import TodoList from '@/components/TodoList.vue'

export default {
  name: 'ProjectDetail',
  components: {
    TodoList
  },
  data () {
    return {
      loading: false,
      project: null,
      todos: [],
      activeTab: 'all',
      showTodoDialog: false,
      submitting: false,
      editingTodo: null,
      todoForm: {
        title: '',
        description: ''
      },
      todoRules: {
        title: [
          { required: true, message: '请输入 Todo 标题', trigger: 'blur' }
        ]
      }
    }
  },
  computed: {
    projectId () {
      return parseInt(this.$route.params.id)
    },
    pendingTodos () {
      return this.todos.filter(t => !t.isCompleted)
    },
    completedTodos () {
      return this.todos.filter(t => t.isCompleted)
    },
    pendingCount () {
      return this.pendingTodos.length
    },
    completedCount () {
      return this.completedTodos.length
    },
    todoDialogTitle () {
      return this.editingTodo ? '编辑 Todo' : '新建 Todo'
    }
  },
  created () {
    this.loadProject()
    this.loadTodos()
  },
  methods: {
    async loadProject () {
      try {
        const response = await this.$store.dispatch('loadProjects', { pageSize: 100 })
        this.project = response.items?.find(p => p.id === this.projectId) || null
        if (!this.project) {
          // 如果不在列表中，单独加载
          const apiService = (await import('@/services/api')).default
          this.project = await apiService.getProject(this.projectId)
        }
      } catch (error) {
        this.$message.error('加载项目失败')
        this.$router.push('/projects')
      }
    },
    async loadTodos () {
      this.loading = true
      try {
        await this.$store.dispatch('loadProjectTodos', this.projectId)
        this.todos = this.$store.state.todos
      } catch (error) {
        this.$message.error('加载 Todo 失败')
      } finally {
        this.loading = false
      }
    },
    async handleTodoSubmit () {
      this.$refs.todoForm.validate(async (valid) => {
        if (!valid) return

        this.submitting = true
        try {
          if (this.editingTodo) {
            await this.$store.dispatch('updateTodo', {
              id: this.editingTodo.id,
              data: this.todoForm
            })
            this.$message.success('更新成功')
          } else {
            await this.$store.dispatch('createTodo', {
              ...this.todoForm,
              projectId: this.projectId
            })
            this.$message.success('创建成功')
          }
          this.showTodoDialog = false
          this.loadTodos()
        } catch (error) {
          this.$message.error(error.message || '操作失败')
        } finally {
          this.submitting = false
        }
      })
    },
    resetTodoForm () {
      this.editingTodo = null
      this.todoForm = {
        title: '',
        description: ''
      }
      this.$refs.todoForm?.resetFields()
    }
  }
}
</script>

<style lang="scss" scoped>
.project-detail-container {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: $spacing-lg;
    background: white;
    padding: $spacing-lg;
    border-radius: $border-radius-lg;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);

    h2 {
      margin: $spacing-sm 0;
      color: #303133;
    }

    .project-description {
      color: #606266;
      margin: $spacing-sm 0 0 0;
    }
  }

  .todos-section {
    background: white;
    padding: $spacing-lg;
    border-radius: $border-radius-lg;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  }
}
</style>
