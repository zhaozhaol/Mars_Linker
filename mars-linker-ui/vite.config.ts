import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://140.210.218.252:8088',
        //target: 'http://127.0.0.1:11884',
        changeOrigin: true,
        timeout: 60000,
        proxyTimeout: 60000
      }
    }
  },
  build: {
    outDir: '../mars-linker-broker/src/main/resources/static',
    emptyOutDir: true
  }
})
