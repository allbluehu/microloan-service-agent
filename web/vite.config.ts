import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({ plugins: [vue()], base: '/ui/', build: { outDir: '../src/main/resources/static/ui', emptyOutDir: true } });
