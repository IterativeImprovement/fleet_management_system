import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // for map
      '/map/tiles': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      // for get route
      '/route': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },

      // for task
      '/task': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})

