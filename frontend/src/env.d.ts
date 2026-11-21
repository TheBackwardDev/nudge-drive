/// <reference types="vite/client" />

declare const __APP_VERSION__: string

interface ImportMetaEnv {
  readonly VITE_RECAPTCHA_SITE_KEY?: string
}
