const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    port: 8080,
    open: true,
    proxy: {
      '/api': {
        target: process.env.VUE_APP_API_BASE_URL || 'http://localhost:5085',
        changeOrigin: true,
        ws: true
      }
    }
  },
  css: {
    loaderOptions: {
      sass: {
        additionalData: '@import "@/styles/variables.scss";'
      }
    }
  }
})
