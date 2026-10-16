import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // In development the browser talks to Vite, and Vite hands /api calls to the server.
  server: { proxy: { '/api': 'http://localhost:3001' } },
});
