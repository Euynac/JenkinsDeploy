<template>
  <div class="layout-container">
    <el-container>
      <el-header class="header">
        <div class="header-content">
          <h1 class="logo">TodoApp</h1>
          <div class="user-info">
            <span class="username">{{ currentUser?.username }}</span>
            <el-button type="text" @click="handleLogout">退出</el-button>
          </div>
        </div>
      </el-header>

      <el-container>
        <el-aside width="250px" class="sidebar">
          <el-menu
            :default-active="activeMenu"
            router
            class="sidebar-menu"
          >
            <el-menu-item index="/projects">
              <i class="el-icon-folder"></i>
              <span slot="title">项目管理</span>
            </el-menu-item>
          </el-menu>
        </el-aside>

        <el-main class="main-content">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script>
export default {
  name: 'Layout',
  computed: {
    currentUser () {
      return this.$store.getters.currentUser
    },
    activeMenu () {
      return this.$route.path
    }
  },
  methods: {
    handleLogout () {
      this.$store.dispatch('logout')
      this.$router.push('/login')
      this.$message.success('已退出登录')
    }
  }
}
</script>

<style lang="scss" scoped>
.layout-container {
  min-height: 100vh;
  background: #f5f7fa;
}

.header {
  background: white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 0;
  height: 60px !important;
  line-height: 60px;

  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 100%;
    padding: 0 $spacing-lg;

    .logo {
      font-size: 24px;
      font-weight: bold;
      color: #667eea;
      margin: 0;
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: $spacing-md;

      .username {
        color: #606266;
        font-weight: 500;
      }
    }
  }
}

.sidebar {
  background: white;
  box-shadow: 2px 0 4px rgba(0, 0, 0, 0.05);

  .sidebar-menu {
    border-right: none;
    height: 100%;
  }
}

.main-content {
  padding: $spacing-lg;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
}
</style>
