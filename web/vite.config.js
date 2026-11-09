import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// Output goes to build/web-dist, which Gradle bundles into the plugin jar under /web.
export default defineConfig({
  plugins: [svelte()],
  build: { outDir: '../build/web-dist', emptyOutDir: true },
  // `npm run dev` proxies API calls to a running server on loopback.
  server: { proxy: { '/api': 'http://127.0.0.1:8123', '/login': 'http://127.0.0.1:8123' } }
});
