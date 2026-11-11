import axios, { AxiosError } from 'axios';

/** Token is kept in memory + sessionStorage (cleared when the browser tab closes). */
const TOKEN_KEY = 'nexus.token';

let token: string | null = sessionStorage.getItem(TOKEN_KEY);
let onUnauthorized: (() => void) | null = null;

export function setToken(t: string | null) {
  token = t;
  if (t) sessionStorage.setItem(TOKEN_KEY, t);
  else sessionStorage.removeItem(TOKEN_KEY);
}

export function getToken() {
  return token;
}

export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

/** Same-origin by default (nginx proxies /api). Set VITE_API_BASE_URL when the UI is hosted separately. */
const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
export const api = axios.create({ baseURL: `${API_ORIGIN}/api/v1`, timeout: 30000 });

api.interceptors.request.use((config) => {
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (error: AxiosError) => {
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/login')) {
      setToken(null);
      onUnauthorized?.();
    }
    return Promise.reject(error);
  },
);

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  violations?: { field: string; message: string }[];
}

/** Human readable message for any API/network error. */
export function errorMessage(e: unknown): string {
  if (axios.isAxiosError(e)) {
    const body = e.response?.data as ApiErrorBody | undefined;
    if (body?.violations?.length) {
      return body.violations.map((v) => `${v.field}: ${v.message}`).join('; ');
    }
    if (body?.message) return body.message;
    if (e.code === 'ECONNABORTED') return 'Request timed out';
    if (!e.response) return 'Backend unreachable';
    return `HTTP ${e.response.status}`;
  }
  return e instanceof Error ? e.message : String(e);
}

/** Downloads an authenticated binary/CSV endpoint as a file. */
export async function download(path: string, params: Record<string, unknown>, fallbackName: string) {
  const res = await api.get(path, { params, responseType: 'blob' });
  const cd = (res.headers['content-disposition'] as string | undefined) ?? '';
  const name = /filename="?([^";]+)"?/.exec(cd)?.[1] ?? fallbackName;
  const url = URL.createObjectURL(res.data as Blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
