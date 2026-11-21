import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'node:path'
import { readFileSync } from 'node:fs'

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')) as { version: string }

// `vite preview` / `vite dev` forward /api to a backend; production is served by nginx behind Caddy.
const apiTarget = process.env.ND_API_PROXY ?? 'http://localhost:4000'
const proxy = { '^/api/': { target: apiTarget, changeOrigin: true, secure: true } } // not /api-keys

export default defineConfig({
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
      manifest: {
        id: '/',
        name: 'Nudge Drive',
        short_name: 'Nudge Drive',
        description: 'All your Google Drives and S3 buckets in one storage pool.',
        theme_color: '#7059F0',
        background_color: '#F7F4FF',
        display: 'standalone',
        start_url: '/home',
        scope: '/',
        icons: [
          { src: '/pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: '/pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: '/maskable-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
          { src: '/favicon.svg', sizes: 'any', type: 'image/svg+xml' },
        ],
      },
      workbox: {
        // Only the SPA's own routes fall back to index.html. Everything below is served by Caddy
        // (API, Collabora editor, static helper pages) and must always reach the network.
        navigateFallbackDenylist: [/^\/api(\/|$)/, /^\/(browser|hosting|cool|lool)\//, /^\/editor-host\.html/, /^\/\.well-known\//],
        globPatterns: ['**/*.{js,css,html,svg,ico,png,webp,woff2}'],
        globIgnores: ['**/landing/**'], // landing-page screenshots load on demand, not precached for app users
      },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: { proxy },
  preview: { proxy },
})
