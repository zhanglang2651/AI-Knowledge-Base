/**
 * 后台管理接口。
 *
 * @author zhanglang
 */

import { request } from './client';
import type {
  ModelConfigRequest,
  ModelConfigVO,
  RetrievalEvalCase,
  RetrievalEvalReport,
  StatOverviewVO,
  StatTrendItemVO,
} from '../types';

export const adminApi = {
  /** 模型配置列表，可按 chat / embedding 过滤 */
  listModels: (modelType?: string) => {
    const query = modelType ? `?modelType=${modelType}` : '';
    return request<ModelConfigVO[]>(`/api/v1/admin/models${query}`);
  },

  /** 新增模型配置 */
  createModel: (data: ModelConfigRequest) => request<number>('/api/v1/admin/models', { method: 'POST', body: data }),

  /** 更新模型配置（apiKey 留空表示不修改） */
  updateModel: (id: number, data: ModelConfigRequest) =>
    request<void>(`/api/v1/admin/models/${id}`, { method: 'PUT', body: data }),

  /** 删除模型配置 */
  removeModel: (id: number) => request<void>(`/api/v1/admin/models/${id}`, { method: 'DELETE' }),

  /** 连通性测试，返回耗时描述 */
  testModel: (id: number) => request<string>(`/api/v1/admin/models/${id}/test`, { method: 'POST' }),

  /** 今日与累计指标 */
  overview: () => request<StatOverviewVO>('/api/v1/admin/stat/overview'),

  /** 按天聚合的调用趋势 */
  trend: (days = 7) => request<StatTrendItemVO[]>(`/api/v1/admin/stat/trend?days=${days}`),

  /**
   * 检索质量评估。
   * <p>
   * 只跑检索不调用模型，因此不产生 token 成本，可以随意反复运行。
   *
   * @param kbId  知识库 ID
   * @param cases 用例；不传则使用后端内置用例
   * @param modes 策略；不传则全部执行
   */
  evaluateRetrieval: (kbId: number, cases?: RetrievalEvalCase[], modes?: string[]) => {
    const query = new URLSearchParams({ kbId: String(kbId) });
    if (modes && modes.length > 0) {
      query.set('modes', modes.join(','));
    }
    // cases 为空时必须显式传 undefined：传空数组会被后端当成"用户提供了 0 条用例"
    return request<RetrievalEvalReport>(`/api/v1/admin/eval/retrieval?${query.toString()}`, {
      method: 'POST',
      body: cases && cases.length > 0 ? cases : undefined,
    });
  },
};
