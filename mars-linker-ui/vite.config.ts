import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        //target: 'https://broker.cdzytx.com',
        target: 'http://10.16.2.82:11884',
        changeOrigin: true,
        timeout: 60000,
        proxyTimeout: 60000
      }
    }
  },
  build: {
    // 独立部署（前后端分离）：产物输出到本目录 dist/
    outDir: 'dist',
    emptyOutDir: true
    // 若改为“打进 Spring Boot jar”的部署方式，取消下行注释：
    // outDir: '../mars-linker-broker/src/main/resources/static',
  }
})
