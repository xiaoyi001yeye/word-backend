import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const buildStamp = process.env.FRONTEND_BUILD_STAMP ?? String(Date.now())

// https://vite.dev/config/
export default defineConfig({
  define: {
    __FRONTEND_BUILD_STAMP__: JSON.stringify(buildStamp),
  },
  plugins: [react()],
})
