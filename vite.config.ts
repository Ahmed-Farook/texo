import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8'));

// https://vite.dev/config/
export default defineConfig({
  // Use root '/' when deployed on Vercel or web so subpaths like /s/<slug> resolve assets correctly.
  // Use relative './' for Electron and Capacitor file:// environments.
  base: process.env.VERCEL || process.env.VITE_BASE ? '/' : './',
  plugins: [react()],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
});
