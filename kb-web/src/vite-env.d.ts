/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端接口基址，留空则使用相对路径（同源或代理） */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
