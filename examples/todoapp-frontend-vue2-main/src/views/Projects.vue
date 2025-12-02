<template>
  <div class="projects-container">
    <div class="page-header">
      <h2>我的项目</h2>
      <el-button type="primary" icon="el-icon-plus" @click="showCreateDialog = true">
        新建项目
      </el-button>
    </div>

    <div class="projects-grid" v-loading="loading">
      <el-card
        v-for="project in projects"
        :key="project.id"
        class="project-card"
        shadow="hover"
        @click.native="goToProject(project.id)"
      >
        <div slot="header" class="card-header">
          <span class="project-name">{{ project.name }}</span>
          <div class="card-actions" @click.stop>
            <el-button
              type="text"
              icon="el-icon-edit"
              @click="handleEdit(project)"
            />
            <el-button
              type="text"
              icon="el-icon-delete"
              @click="handleDelete(project.id)"
            />
          </div>
        </div>
        <p class="project-description">{{ project.description || '暂无描述' }}</p>
        <div class="project-meta">
          <span class="meta-item">
            <i class="el-icon-time"></i>
            {{ formatDate(project.updatedAt) }}
          </span>
        </div>
      </el-card>

      <el-empty v-if="!loading && projects.length === 0" description="暂无项目，创建第一个项目吧！" />
    </div>

    <!-- 创建/编辑项目对话框 -->
    <el-dialog
      :title="dialogTitle"
      :visible.sync="showCreateDialog"
      width="500px"
      @close="resetForm"
    >
      <el-form
        ref="projectForm"
        :model="projectForm"
        :rules="projectRules"
        label-width="80px"
      >
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="projectForm.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目描述" prop="description">
          <el-input
            v-model="projectForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入项目描述"
          />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          确定
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'Projects',
  data () {
    return {
      loading: false,
      projects: [],
      showCreateDialog: false,
      submitting: false,
      editingProject: null,
      projectForm: {
        name: '',
        description: ''
      },
      projectRules: {
        name: [
          { required: true, message: '请输入项目名称', trigger: 'blur' }
        ]
      }
    }
  },
  computed: {
    dialogTitle () {
      return this.editingProject ? '编辑项目' : '新建项目'
    }
  },
  created () {
    this.loadProjects()
  },
  methods: {
    async loadProjects () {
      this.loading = true
      try {
        const response = await this.$store.dispatch('loadProjects')
        this.projects = response.items || []
      } catch (error) {
        this.$message.error('加载项目失败')
      } finally {
        this.loading = false
      }
    },
    goToProject (id) {
      this.$router.push(`/projects/${id}`)
    },
    handleEdit (project) {
      this.editingProject = project
      this.projectForm = {
        name: project.name,
        description: project.description || ''
      }
      this.showCreateDialog = true
    },
    async handleSubmit () {
      this.$refs.projectForm.validate(async (valid) => {
        if (!valid) return

        this.submitting = true
        try {
          if (this.editingProject) {
            await this.$store.dispatch('updateProject', {
              id: this.editingProject.id,
              data: this.projectForm
            })
            this.$message.success('更新成功')
          } else {
            await this.$store.dispatch('createProject', this.projectForm)
            this.$message.success('创建成功')
          }
          this.showCreateDialog = false
          this.loadProjects()
        } catch (error) {
          this.$message.error(error.message || '操作失败')
        } finally {
          this.submitting = false
        }
      })
    },
    handleDelete (id) {
      this.$confirm('确定要删除这个项目吗？项目下的所有 Todo 也会被删除。', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        try {
          await this.$store.dispatch('deleteProject', id)
          this.$message.success('删除成功')
          this.loadProjects()
        } catch (error) {
          this.$message.error(error.message || '删除失败')
        }
      })
    },
    resetForm () {
      this.editingProject = null
      this.projectForm = {
        name: '',
        description: ''
      }
      this.$refs.projectForm?.resetFields()
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
.projects-container {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: $spacing-lg;

    h2 {
      margin: 0;
      color: #303133;
    }
  }

  .projects-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: $spacing-lg;

    .project-card {
      cursor: pointer;
      transition: transform 0.2s;

      &:hover {
        transform: translateY(-4px);
      }

      .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .project-name {
          font-size: 18px;
          font-weight: 600;
          color: #303133;
        }

        .card-actions {
          display: flex;
          gap: $spacing-xs;
        }
      }

      .project-description {
        color: #606266;
        margin: $spacing-md 0;
        min-height: 40px;
      }

      .project-meta {
        display: flex;
        gap: $spacing-md;
        font-size: 12px;
        color: #909399;

        .meta-item {
          display: flex;
          align-items: center;
          gap: $spacing-xs;
        }
      }
    }
  }
}
</style>
