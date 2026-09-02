import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 개발 서버에서도 /api를 백엔드로 프록시한다.
    // 배포 환경의 nginx가 하는 일과 똑같아서, 개발과 운영 모두 CORS가 발생하지 않는다.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
