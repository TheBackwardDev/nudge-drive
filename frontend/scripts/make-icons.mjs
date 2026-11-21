// Renders the app icons (PNG) from scripts/icons.html. Run once after changing the logo:
//   node scripts/make-icons.mjs   (needs the `playwright` package and Chromium)
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'

const here = new URL('.', import.meta.url)
const out = (name) => fileURLToPath(new URL(`../public/${name}`, here))
const browser = await chromium.launch()
const page = await browser.newPage()
await page.goto(new URL('icons.html', here).href)
const shots = [
  ['any', 192, 'pwa-192x192.png'],
  ['any', 512, 'pwa-512x512.png'],
  ['maskable', 512, 'maskable-512x512.png'],
  ['maskable', 180, 'apple-touch-icon.png'],
]
for (const [id, size, file] of shots) {
  await page.setViewportSize({ width: size, height: size })
  await page.evaluate(([i, s]) => {
    for (const el of document.querySelectorAll('svg')) {
      el.style.display = el.id === i ? 'block' : 'none'
      el.setAttribute('width', s)
      el.setAttribute('height', s)
    }
  }, [id, size])
  await page.locator(`#${id}`).screenshot({ path: out(file), omitBackground: true })
}
await browser.close()
console.log('icons written')
