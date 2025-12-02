<template>
  <div class="register-container">
    <div class="register-card">
      <h1 class="title">TodoApp</h1>
      <p class="subtitle">创建新账户</p>

      <el-form
        ref="registerForm"
        :model="form"
        :rules="rules"
        class="register-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            prefix-icon="el-icon-user"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="email">
          <el-input
            v-model="form.email"
            type="email"
            placeholder="邮箱"
            prefix-icon="el-icon-message"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            prefix-icon="el-icon-lock"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            prefix-icon="el-icon-lock"
            size="large"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleRegister"
            class="register-button"
          >
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="footer">
        <span>已有账户？</span>
        <el-link type="primary" @click="$router.push('/login')">立即登录</el-link>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Register',
  data () {
    const validateConfirmPassword = (rule, value, callback) => {
      if (value !== this.form.password) {
        callback(new Error('两次输入的密码不一致'))
      } else {
        callback()
      }
    }

    return {
      form: {
        username: '',
        email: '',
        password: '',
        confirmPassword: ''
      },
      rules: {
        username: [
          { required: true, message: '请输入用户名', trigger: 'blur' },
          { min: 3, message: '用户名长度不能少于3位', trigger: 'blur' }
        ],
        email: [
          { required: true, message: '请输入邮箱', trigger: 'blur' },
          { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
        ],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, message: '请确认密码', trigger: 'blur' },
          { validator: validateConfirmPassword, trigger: 'blur' }
        ]
      },
      loading: false
    }
  },
  methods: {
    async handleRegister () {
      this.$refs.registerForm.validate(async (valid) => {
        if (!valid) return

        this.loading = true
        try {
          await this.$store.dispatch('register', {
            username: this.form.username,
            email: this.form.email,
            password: this.form.password
          })
          this.$message.success('注册成功')
          this.$router.push('/')
        } catch (error) {
          this.$message.error(error.message || '注册失败，请稍后重试')
        } finally {
          this.loading = false
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-md;
}

.register-card {
  width: 100%;
  max-width: 400px;
  background: white;
  border-radius: $border-radius-lg;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  padding: $spacing-xl;

  .title {
    text-align: center;
    font-size: 32px;
    font-weight: bold;
    color: #667eea;
    margin-bottom: $spacing-sm;
  }

  .subtitle {
    text-align: center;
    color: #909399;
    margin-bottom: $spacing-lg;
  }

  .register-form {
    margin-top: $spacing-lg;

    .register-button {
      width: 100%;
      margin-top: $spacing-md;
    }
  }

  .footer {
    text-align: center;
    margin-top: $spacing-lg;
    color: #909399;

    .el-link {
      margin-left: $spacing-xs;
    }
  }
}
</style>
