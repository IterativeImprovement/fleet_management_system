import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // for map
      '/map/tiles': {
        target: 'http://backend-api:8080',
        changeOrigin: true,
        secure: false,
      },
      // for get route
      '/route': {
        target: 'http://backend-api:8080',
        changeOrigin: true,
        secure: false,
      },

      // for task
      '/task': {
        target: 'http://backend-api:8080',
        changeOrigin: true,
        secure: false,
      },

      // for locations
      '/locations': {
        target: 'http://backend-api:8080',
        changeOrigin: true,
        secure: false,
      },

      // for robot
      '/robot': {
        target: 'http://backend-api:8080',
        changeOrigin: true,
        secure: false,
      },

      // for simulation
      '/simulation': {
        target: 'http://backend-api:8080',
        changeOrigin: true,
        secure: false,
      },

      // for road
      '/road': {
        target: 'http://backend-api:8080',
        changeOrigin: true,
        secure: false,
      },

      // for websocket (robot position updates)
      '/ws': {
        target: 'ws://backend-api:8080',
        ws: true,
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
