<template>
  <div class="todo-list">
    <el-empty v-if="todos.length === 0" description="暂无 Todo" />

    <div v-else class="todo-items">
      <div
        v-for="todo in todos"
        :key="todo.id"
        class="todo-item"
        :class="{ completed: todo.isCompleted }"
      >
        <el-checkbox
          :value="todo.isCompleted"
          @change="handleToggleComplete(todo.id)"
          class="todo-checkbox"
        />

        <div class="todo-content" @click="handleEdit(todo)">
          <h4 class="todo-title">{{ todo.title }}</h4>
          <p v-if="todo.description" class="todo-description">
            {{ todo.description }}
          </p>
          <span class="todo-date">
            {{ formatDate(todo.updatedAt) }}
          </span>
        </div>

        <div class="todo-actions">
          <el-button
            type="text"
            icon="el-icon-edit"
            @click="handleEdit(todo)"
          />
          <el-button
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(todo.id)"
          />
        </div>
      </div>
    </div>

    <!-- 编辑 Todo 对话框 -->
    <el-dialog
      title="编辑 Todo"
      :visible.sync="showEditDialog"
      width="500px"
      @close="resetForm"
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
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          确定
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'TodoList',
  props: {
    todos: {
      type: Array,
      default: () => []
    }
  },
  data () {
    return {
      showEditDialog: false,
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
  methods: {
    async handleToggleComplete (id) {
      try {
        await this.$store.dispatch('toggleTodoComplete', id)
        this.$message.success('状态已更新')
        this.$emit('refresh')
      } catch (error) {
        this.$message.error('更新失败')
      }
    },
    handleEdit (todo) {
      this.editingTodo = todo
      this.todoForm = {
        title: todo.title,
        description: todo.description || ''
      }
      this.showEditDialog = true
    },
    async handleSubmit () {
      this.$refs.todoForm.validate(async (valid) => {
        if (!valid) return

        this.submitting = true
        try {
          await this.$store.dispatch('updateTodo', {
            id: this.editingTodo.id,
            data: this.todoForm
          })
          this.$message.success('更新成功')
          this.showEditDialog = false
          this.$emit('refresh')
        } catch (error) {
          this.$message.error(error.message || '更新失败')
        } finally {
          this.submitting = false
        }
      })
    },
    handleDelete (id) {
      this.$confirm('确定要删除这个 Todo 吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        try {
          await this.$store.dispatch('deleteTodo', id)
          this.$message.success('删除成功')
          this.$emit('refresh')
        } catch (error) {
          this.$message.error(error.message || '删除失败')
        }
      })
    },
    resetForm () {
      this.editingTodo = null
      this.todoForm = {
        title: '',
        description: ''
      }
      this.$refs.todoForm?.resetFields()
    },
    formatDate (date) {
      if (!date) return ''
      const d = new Date(date)
      return d.toLocaleDateString('zh-CN')
    }
  }
}
</script>

<style lang="scss" scoped>
.todo-list {
  .todo-items {
    display: flex;
    flex-direction: column;
    gap: $spacing-md;
  }

  .todo-item {
    display: flex;
    align-items: flex-start;
    gap: $spacing-md;
    padding: $spacing-md;
    background: #f9fafc;
    border-radius: $border-radius-md;
    border: 1px solid #e4e7ed;
    transition: all 0.2s;

    &:hover {
      border-color: #409eff;
      box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
    }

    &.completed {
      opacity: 0.7;

      .todo-title {
        text-decoration: line-through;
        color: #909399;
      }
    }

    .todo-checkbox {
      margin-top: 4px;
    }

    .todo-content {
      flex: 1;
      cursor: pointer;

      .todo-title {
        margin: 0 0 $spacing-xs 0;
        color: #303133;
        font-size: 16px;
        font-weight: 500;
      }

      .todo-description {
        margin: $spacing-xs 0;
        color: #606266;
        font-size: 14px;
        line-height: 1.5;
      }

      .todo-date {
        font-size: 12px;
        color: #909399;
      }
    }

    .todo-actions {
      display: flex;
      gap: $spacing-xs;
    }
  }
}
</style>
