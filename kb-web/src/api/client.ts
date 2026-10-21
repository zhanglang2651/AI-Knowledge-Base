/**
 * HTTP 请求封装。
 * <p>
 * 不引入 axios 的理由：本项目需要原生 {@code fetch} 的流式读取能力（SSE），
 * 而 axios 在浏览器端拿不到可读流；统一用 fetch 可以只维护一套请求/错误处理逻辑。
 * <p>
 * 统一约定（与后端 {@code Result<T>} 对齐）：
 * <ul>
 *     <li>HTTP 非 2xx 或 code !== '00000' 一律抛 {@link ApiError}</li>
 *     <li>401 自动清除本地令牌并广播事件，由 AuthProvider 统一跳登录页，
 *         避免每个页面各写一遍跳转逻辑</li>
 * </ul>
 *
 * @author zhanglang
 */

import type { ApiResult } from '../types';

/** 本地存储键名。加 kb_ 前缀，避免与同域下其他应用冲突 */
const TOKEN_KEY = 'kb_access_token';

/** 业务成功码 */
export const SUCCESS_CODE = '00000';

/** 未授权事件名，AuthProvider 监听后清理登录态 */
export const UNAUTHORIZED_EVENT = 'kb:unauthorized';

/** 接口基址：生产可注入 VITE_API_BASE_URL，开发留空走 vite proxy */
export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

/**
 * 业务异常。
 * 携带 code / status，便于上层按错误类型做差异化处理（如 403 引导申请权限）。
 */
export class ApiError extends Error {
  readonly code: string;

  readonly status: number;

  constructor(message: string, code: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

/**
 * 读取本地令牌。
 *
 * @returns 令牌字符串；未登录返回 null
 */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

/**
 * 写入令牌。
 *
 * @param token JWT
 */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

/**
 * 清除令牌。
 */
export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

/** 请求参数 */
export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  /** 普通对象会被 JSON 序列化；FormData 原样发送（由浏览器自动设置 boundary） */
  body?: unknown;
  signal?: AbortSignal;
  /** 追加的请求头 */
  headers?: Record<string, string>;
}

/**
 * 解析响应体为 Result 结构。
 * <p>
 * 后端异常路径（如网关返回 HTML 502）拿不到 JSON，此时用 HTTP 状态码兜底提示，
 * 不能直接把 "Unexpected token <" 抛给用户。
 */
async function parseResult<T>(response: Response): Promise<ApiResult<T>> {
  const text = await response.text();
  if (!text) {
    return { code: String(response.status), message: `服务无响应（HTTP ${response.status}）`, data: null as T, timestamp: Date.now() };
  }
  try {
    return JSON.parse(text) as ApiResult<T>;
  } catch {
    throw new ApiError(
      response.ok ? '服务返回了无法解析的内容' : `请求失败（HTTP ${response.status}）`,
      String(response.status),
      response.status,
    );
  }
}

/**
 * 发起请求并解包业务数据。
 *
 * @param path    接口路径，如 /api/v1/kb
 * @param options 请求选项
 * @returns 业务数据（Result.data）
 * @throws ApiError 业务失败或网络异常
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const result = await requestRaw<T>(path, options);
  return result.data;
}

/**
 * 发起请求但保留完整响应体（需要读取 code / message 时使用）。
 *
 * @param path    接口路径
 * @param options 请求选项
 * @returns 完整响应体
 */
export async function requestRaw<T>(path: string, options: RequestOptions = {}): Promise<ApiResult<T>> {
  const { method = 'GET', body, signal, headers = {} } = options;

  const finalHeaders: Record<string, string> = { Accept: 'application/json', ...headers };
  const token = getToken();
  if (token) {
    finalHeaders.Authorization = `Bearer ${token}`;
  }

  let payload: BodyInit | undefined;
  if (body instanceof FormData) {
    // 不设置 Content-Type：浏览器需要自己补 multipart 的 boundary
    payload = body;
  } else if (body !== undefined) {
    finalHeaders['Content-Type'] = 'application/json; charset=utf-8';
    payload = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { method, headers: finalHeaders, body: payload, signal });
  } catch (error) {
    // AbortError 是主动取消，交给调用方判断，不要包装成「网络异常」
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }
    throw new ApiError('网络连接失败，请检查后端服务是否已启动', 'NETWORK_ERROR', 0);
  }

  if (response.status === 401) {
    clearToken();
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    throw new ApiError('登录已失效，请重新登录', 'UNAUTHORIZED', 401);
  }

  const result = await parseResult<T>(response);
  if (!response.ok || result.code !== SUCCESS_CODE) {
    throw new ApiError(result.message || `请求失败（HTTP ${response.status}）`, result.code, response.status);
  }
  return result;
}
