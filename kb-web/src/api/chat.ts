/**
 * 问答接口与 SSE 流式解析。
 * <p>
 * 为什么不用 {@code EventSource}：它只能发 GET 且无法携带请求头，
 * 而本项目的鉴权依赖 {@code Authorization: Bearer xxx}。
 * 因此改用 {@code fetch} + {@code ReadableStream} 手动解析 SSE 文本流，
 * 协议格式仍是标准 SSE（{@code event: xxx\ndata: yyy\nddd\n\n}）。
 *
 * @author zhanglang
 */

import { API_BASE, ApiError, clearToken, getToken, request, UNAUTHORIZED_EVENT } from './client';
import type {
  ChatDoneInfo,
  ChatRequest,
  ConversationVO,
  MessageVO,
  SourceVO,
} from '../types';

/** 流式回调集合。全部可选，调用方只关心需要的部分 */
export interface StreamHandlers {
  /** 收到引用列表（先于正文到达，便于界面立刻反馈"参考了什么"） */
  onSources?: (sources: SourceVO[]) => void;
  /** 收到一段增量文本 */
  onDelta?: (delta: string) => void;
  /** 生成完成，携带会话 ID 与 token 统计 */
  onDone?: (info: ChatDoneInfo) => void;
  /** 服务端主动推送的错误事件 */
  onError?: (message: string, code?: string) => void;
}

/** 单个 SSE 事件块 */
interface SseEvent {
  event: string;
  data: string;
}

/**
 * 解析单个事件块。
 * <p>
 * 一个块可能包含多行 data（规范要求按 \n 拼接），也可能是以 ":" 开头的注释行（心跳），
 * 这些都必须在实现中处理，否则遇到心跳会解析出空事件。
 *
 * @param block 以空行分隔的单块文本
 * @returns 事件名与数据；无有效 data 时返回 null
 */
function parseBlock(block: string): SseEvent | null {
  let event = 'message';
  const dataLines: string[] = [];

  for (const rawLine of block.split('\n')) {
    const line = rawLine.replace(/\r$/, '');
    if (!line) {
      continue;
    }
    // 冒号开头是注释（心跳包），直接忽略
    if (line.startsWith(':')) {
      continue;
    }
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      // 规范中 data: 后的第一个空格应被去掉，但不能 trim 掉内容本身的缩进
      dataLines.push(line.slice('data:'.length).replace(/^ /, ''));
    }
  }

  if (dataLines.length === 0) {
    return null;
  }
  return { event, data: dataLines.join('\n') };
}

/**
 * 安全解析 JSON。
 * 服务端异常时可能推送非 JSON 文本，这里不做抛错处理，
 * 避免一个畸形事件导致整个问答流中断。
 */
function safeParse(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}

/**
 * 把解析出的事件分发给回调。
 */
function dispatch(event: SseEvent, handlers: StreamHandlers): void {
  const payload = safeParse(event.data);
  switch (event.event) {
    case 'sources': {
      handlers.onSources?.(Array.isArray(payload) ? (payload as SourceVO[]) : []);
      break;
    }
    case 'content': {
      const delta = (payload as { delta?: string } | null)?.delta;
      if (delta) {
        handlers.onDelta?.(delta);
      }
      break;
    }
    case 'done': {
      handlers.onDone?.(payload as ChatDoneInfo);
      break;
    }
    case 'error': {
      const body = payload as { message?: string; code?: string } | null;
      handlers.onError?.(body?.message ?? '生成回答时发生错误', body?.code);
      break;
    }
    default:
      // 未知事件忽略，保证前向兼容（后端后续新增事件不会打断旧前端）
      break;
  }
}

/**
 * 发起流式问答。
 * <p>
 * 该方法在流结束（或调用方 abort）后才 resolve，调用方可在 finally 中收尾界面状态。
 *
 * @param payload  问答请求
 * @param handlers 事件回调
 * @param signal   中断信号，用于「停止生成」
 * @throws ApiError 握手失败（如 401 / 400 / 500）
 */
export async function streamChat(
  payload: ChatRequest,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json; charset=utf-8',
    Accept: 'text/event-stream',
  };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}/api/v1/chat/stream`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
      signal,
    });
  } catch (error) {
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

  if (!response.ok || !response.body) {
    // 握手失败时后端返回的是标准 JSON（Result 结构），尽量把真实原因抛给用户
    const text = await response.text().catch(() => '');
    let message = `请求失败（HTTP ${response.status}）`;
    try {
      const parsed = JSON.parse(text) as { message?: string };
      if (parsed.message) {
        message = parsed.message;
      }
    } catch {
      // 非 JSON 响应（如网关错误页），保留默认提示
    }
    throw new ApiError(message, String(response.status), response.status);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      // 统一换行符：Windows 下某些网关会把 \n 改写成 \r\n，
      // 若不做归一化，按 "\n\n" 切分会永远切不开，表现为「一直转圈没有内容」
      buffer = buffer.replace(/\r\n/g, '\n');

      let boundary = buffer.indexOf('\n\n');
      while (boundary !== -1) {
        const block = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const event = parseBlock(block);
        if (event) {
          dispatch(event, handlers);
        }
        boundary = buffer.indexOf('\n\n');
      }
    }

    // 流结束但缓冲区还有残留（服务端未以空行收尾）
    const tail = parseBlock(buffer);
    if (tail) {
      dispatch(tail, handlers);
    }
  } finally {
    // 主动释放连接，避免浏览器把已结束的流挂在连接池里
    reader.cancel().catch(() => undefined);
  }
}

/** 会话与消息接口 */
export const chatApi = {
  /** 某知识库下的会话列表，按最后活跃时间倒序 */
  conversations: (kbId: number) => request<ConversationVO[]>(`/api/v1/chat/conversations?kbId=${kbId}`),

  /** 某会话的历史消息（引用来自消息表快照，不受知识库后续变更影响） */
  messages: (conversationId: number) =>
    request<MessageVO[]>(`/api/v1/chat/conversations/${conversationId}/messages`),

  /** 删除会话 */
  remove: (conversationId: number) =>
    request<void>(`/api/v1/chat/conversations/${conversationId}`, { method: 'DELETE' }),
};
