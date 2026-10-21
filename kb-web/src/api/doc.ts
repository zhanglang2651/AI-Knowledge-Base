/**
 * 文档接口。
 *
 * @author zhanglang
 */

import { request } from './client';
import type { DocumentVO } from '../types';

export const docApi = {
  /**
   * 上传文档。
   * <p>
   * 后端收到文件后立即返回（状态 PENDING），解析与向量化在后台异步执行，
   * 因此调用方拿到结果后必须轮询 {@link status} 才能知道真正的处理结果。
   *
   * @param kbId 知识库 ID
   * @param file 浏览器 File 对象
   */
  upload: (kbId: number, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<DocumentVO>(`/api/v1/kb/${kbId}/doc`, { method: 'POST', body: form });
  },

  /** 查询某知识库下的全部文档 */
  list: (kbId: number) => request<DocumentVO[]>(`/api/v1/kb/${kbId}/doc`),

  /** 查询单个文档状态（轮询接口） */
  status: (id: number) => request<DocumentVO>(`/api/v1/doc/${id}/status`),

  /** 删除文档，后端会同步删除已入库的向量 */
  remove: (id: number) => request<void>(`/api/v1/doc/${id}`, { method: 'DELETE' }),

  /** 重试失败的解析任务 */
  retry: (id: number) => request<void>(`/api/v1/doc/${id}/retry`, { method: 'POST' }),
};
