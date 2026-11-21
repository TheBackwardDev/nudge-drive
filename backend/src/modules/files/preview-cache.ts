import { mkdir, stat, writeFile, readdir, unlink, readFile } from 'node:fs/promises'
import path from 'node:path'

/** Disk cache for thumbnails and converted documents (a Docker volume on the server). */
const CACHE_DIR = process.env.PREVIEW_CACHE_DIR || '/tmp/nudge-preview-cache'
const CACHE_MAX_BYTES = 1024 * 1024 * 1024

export async function cachePath(key: string) {
  const p = path.join(CACHE_DIR, key)
  try {
    if ((await stat(p)).size > 0) return p
  } catch { /* miss */ }
  return null
}

export async function cacheStore(key: string, data: Buffer) {
  await mkdir(CACHE_DIR, { recursive: true })
  await writeFile(path.join(CACHE_DIR, key), data)
  void pruneCache()
}

/** Removes every cached preview of these files (account deletion). Keys always contain the file id. */
export async function purgeCacheFor(fileIds: string[]) {
  if (fileIds.length === 0) return
  const ids = new Set(fileIds)
  const names = await readdir(CACHE_DIR).catch(() => [] as string[])
  await Promise.all(names.filter((n) => [...ids].some((id) => n.includes(id))).map((n) => unlink(path.join(CACHE_DIR, n)).catch(() => {})))
}

let pruning = false
async function pruneCache() {
  if (pruning) return
  pruning = true
  try {
    const names = await readdir(CACHE_DIR)
    const entries = await Promise.all(names.map(async (n) => ({ n, s: await stat(path.join(CACHE_DIR, n)).catch(() => null) })))
    const files = entries.filter((e) => e.s).map((e) => ({ n: e.n, size: e.s!.size, t: e.s!.atimeMs })).sort((a, b) => a.t - b.t)
    let total = files.reduce((a, f) => a + f.size, 0)
    for (const f of files) {
      if (total <= CACHE_MAX_BYTES) break
      await unlink(path.join(CACHE_DIR, f.n)).catch(() => {})
      total -= f.size
    }
  } finally {
    pruning = false
  }
}


export async function cacheRead(key: string): Promise<Buffer | null> {
  const p = await cachePath(key)
  return p ? readFile(p).catch(() => null) : null
}
