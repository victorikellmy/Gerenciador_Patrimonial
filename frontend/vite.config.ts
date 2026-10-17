import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// A SPA é servida pelo Spring Boot em /app (classpath:/static/app). O build grava
// direto em build/resources/main/static/app (tarefa Gradle buildFrontend).
// Em desenvolvimento, `npm run dev` sobe em :5173 e encaminha /api, /css, /js e
// /images para o backend em :8080.
export default defineConfig({
  plugins: [react()],
  base: '/app/',
  build: {
    outDir: '../build/frontend/static/app',
    emptyOutDir: true,
    sourcemap: false,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/css': 'http://localhost:8080',
      '/js': 'http://localhost:8080',
      '/images': 'http://localhost:8080',
    },
  },
})
