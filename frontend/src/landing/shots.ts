/** Real screenshots: Android app (720×1600) and the web app signed in as the demo account (1440×900). */
const base = '/landing'

export const PHONE = {
  home: `${base}/phone-home.webp`,
  files: `${base}/phone-files.webp`,
  photos: `${base}/phone-photos.webp`,
  viewer: `${base}/phone-viewer.webp`,
  nearby: `${base}/phone-nearby.webp`,
  backup: `${base}/phone-backup.webp`,
  drives: `${base}/phone-drives.webp`,
  share: `${base}/phone-share.webp`,
} as const

export const DESK = {
  homeLight: `${base}/desk-home-light.webp`,
  homeDark: `${base}/desk-home-dark.webp`,
  photosLight: `${base}/desk-photos-light.webp`,
  photosDark: `${base}/desk-photos-dark.webp`,
} as const
