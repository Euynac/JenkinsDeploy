<template>
  <div class="login-container">
    <div class="login-card">
      <h1 class="title">TodoApp</h1>
      <p class="subtitle">欢迎回来，请登录您的账户</p>

      <el-form
        ref="loginForm"
        :model="form"
        :rules="rules"
        class="login-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            prefix-icon="el-icon-user"
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
            @keyup.enter.native="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleLogin"
            class="login-button"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="footer">
        <span>还没有账户？</span>
        <el-link type="primary" @click="$router.push('/register')">立即注册</el-link>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Login',
  data () {
    return {
      form: {
        username: '',
        password: ''
      },
      rules: {
        username: [
          { required: true, message: '请输入用户名', trigger: 'blur' }
        ],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
        ]
      },
      loading: false
    }
  },
  methods: {
    async handleLogin () {
      this.$refs.loginForm.validate(async (valid) => {
        if (!valid) return

        this.loading = true
        try {
          await this.$store.dispatch('login', this.form)
          this.$message.success('登录成功')
          this.$router.push('/')
        } catch (error) {
          this.$message.error(error.message || '登录失败，请检查用户名和密码')
        } finally {
          this.loading = false
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-md;
}

.login-card {
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

  .login-form {
    margin-top: $spacing-lg;

    .login-button {
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
