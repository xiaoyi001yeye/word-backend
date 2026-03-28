import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import solid from "vite-plugin-solid";

export default defineConfig({
  base: "/",
  plugins: [solid()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    proxy: {
      "/api": {
        target: process.env.ADMIN_FRONTEND_PROXY_TARGET ?? "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    },
  },
})
