import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The browser only ever talks to the same origin ("/api/...").
// In dev, Vite proxies /api to the API Gateway. In production the
// frontend Nginx container does the same. This keeps the gateway as
// the single entry point for the browser (Phase 9 architecture).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
});