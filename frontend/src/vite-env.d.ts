/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Public source repository URL shown on the website (optional). */
  readonly VITE_GITHUB_URL?: string;
  /** Absolute API origin when the UI is hosted separately from the API (optional; default same-origin). */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
