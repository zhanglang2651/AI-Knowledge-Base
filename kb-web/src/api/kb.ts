/**
 * 知识库接口。
 *
 * @author zhanglang
 */

import { request } from './client';
import type { KbCreateRequest, KnowledgeBase, PageResult } from '../types';

export const kbApi = {
  /**
   * 分页查询。keyword 为空时不传该参数，避免后端收到空串后拼出 LIKE '%%'。
   */
  page: (params: { keyword?: string; pageNo?: number; pageSize?: number }) => {
    const query = new URLSearchParams();
    if (params.keyword) {
      query.set('keyword', params.keyword);
    }
    query.set('pageNo', String(params.pageNo ?? 1));
    query.set('pageSize', String(params.pageSize ?? 12));
    return request<PageResult<KnowledgeBase>>(`/api/v1/kb?${query.toString()}`);
  },

  /** 详情 */
  detail: (id: number) => request<KnowledgeBase>(`/api/v1/kb/${id}`),

  /** 创建，返回新 ID */
  create: (data: KbCreateRequest) => request<number>('/api/v1/kb', { method: 'POST', body: data }),

  /** 更新 */
  update: (id: number, data: KbCreateRequest) => request<void>(`/api/v1/kb/${id}`, { method: 'PUT', body: data }),

  /** 删除（后端会级联清理文档与向量） */
  remove: (id: number) => request<void>(`/api/v1/kb/${id}`, { method: 'DELETE' }),
};
