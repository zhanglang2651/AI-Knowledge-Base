/**
 * 后端 DTO 的 TypeScript 镜像。
 * <p>
 * 命名与 Java DTO 一一对应，避免前端各处重复声明「差不多」的结构
 * 导致字段名拼错却只在运行时才发现（TypeScript 只能校验编译期已知类型）。
 *
 * @author zhanglang
 */

/** 统一响应体，对应后端 {@code Result<T>} */
export interface ApiResult<T> {
  /** 业务错误码，00000 表示成功 */
  code: string;
  message: string;
  data: T;
  timestamp: number;
}

/** 分页响应体，对应后端 {@code PageResult<T>} */
export interface PageResult<T> {
  pageNo: number;
  pageSize: number;
  total: number;
  pages: number;
  records: T[];
}

/** 用户信息 */
export interface UserVO {
  id: number;
  username: string;
  nickname?: string;
  email?: string;
  /** USER / ADMIN */
  role: string;
  /** 1 启用，0 禁用 */
  status: number;
  gmtCreate?: string;
}

/** 登录响应 */
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  username: string;
  nickname?: string;
  role: string;
}

/** 登录请求 */
export interface LoginRequest {
  username: string;
  password: string;
}

/** 注册请求 */
export interface RegisterRequest {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
}

/** 知识库 */
export interface KnowledgeBase {
  id: number;
  name: string;
  description?: string;
  docCount: number;
  embeddingModel?: string;
  embeddingDim?: number;
  chunkSize: number;
  chunkOverlap: number;
  topK: number;
  similarityThreshold: number;
  status: number;
  gmtCreate?: string;
}

/** 知识库创建 / 更新请求 */
export interface KbCreateRequest {
  name: string;
  description?: string;
  chunkSize?: number;
  chunkOverlap?: number;
  topK?: number;
  similarityThreshold?: number;
}

/** 文档解析状态 */
export type ParseStatus = 'PENDING' | 'PARSING' | 'EMBEDDING' | 'COMPLETED' | 'FAILED';

/** 文档 */
export interface DocumentVO {
  id: number;
  kbId: number;
  fileName: string;
  fileSize: number;
  fileType: string;
  pageCount?: number;
  chunkCount: number;
  parseStatus: ParseStatus;
  errorMsg?: string;
  gmtCreate?: string;
}

/** 引用来源 */
export interface SourceVO {
  docId: number;
  docName: string;
  pageNo?: number;
  chunkNo?: number;
  score: number;
  content: string;
  /** 命中来源：VECTOR / KEYWORD / BOTH，老数据无此字段 */
  via?: 'VECTOR' | 'KEYWORD' | 'BOTH';
}

/** 会话 */
export interface ConversationVO {
  id: number;
  kbId: number;
  title: string;
  messageCount: number;
  lastMessageAt?: string;
}

/** 消息 */
export interface MessageVO {
  id: number;
  /** user / assistant */
  role: string;
  content: string;
  sources?: SourceVO[];
  promptTokens?: number;
  completionTokens?: number;
  gmtCreate?: string;
}

/** 问答请求 */
export interface ChatRequest {
  kbId: number;
  conversationId?: number | null;
  question: string;
}

/** SSE done 事件载荷 */
export interface ChatDoneInfo {
  conversationId: number;
  promptTokens: number;
  completionTokens: number;
  latencyMs: number;
}

/** 模型配置 */
export interface ModelConfigVO {
  id: number;
  provider: string;
  modelName: string;
  /** chat / embedding */
  modelType: string;
  baseUrl: string;
  /** 脱敏后的 key，如 sk-k****qunm */
  apiKeyMasked: string;
  inputPrice?: number;
  outputPrice?: number;
  isDefault: number;
  status: number;
  gmtCreate?: string;
}

/** 模型配置创建 / 更新请求 */
export interface ModelConfigRequest {
  provider: string;
  modelName: string;
  modelType: 'chat' | 'embedding';
  baseUrl: string;
  /** 更新时留空表示不修改原密钥 */
  apiKey?: string;
  inputPrice?: number;
  outputPrice?: number;
  isDefault?: number;
  status?: number;
}

/** 调用统计总览 */
export interface StatOverviewVO {
  todayCalls: number;
  todayTokens: number;
  todayCost: number;
  todayFailCount: number;
  todayFailRate: number;
  todayAvgLatencyMs: number;
  totalCalls: number;
  totalTokens: number;
}

/** 调用趋势项 */
export interface StatTrendItemVO {
  statDate: string;
  callCount: number;
  totalTokens: number;
  totalCost: number;
  failCount: number;
}

/** 检索评估用例 */
export interface RetrievalEvalCase {
  question: string;
  /** 期望关键词，全部出现才算覆盖 */
  expectKeywords?: string[];
  /** 期望文档名，任一匹配即可 */
  expectDocNames?: string[];
  /** 是否为负样本（期望检索不到） */
  expectMiss?: boolean;
}

/** 单条用例在某个策略下的结果 */
export interface EvalCaseResult {
  mode: 'HYBRID' | 'VECTOR' | 'KEYWORD';
  question: string;
  negative: boolean;
  rank: number;
  correct: boolean;
  retrievedCount: number;
  topDocName?: string;
  topVia?: string;
  topScore?: number;
  note?: string;
}

/** 某个策略的汇总指标 */
export interface EvalModeReport {
  mode: string;
  modeName: string;
  total: number;
  positiveTotal: number;
  negativeTotal: number;
  /** 正样本命中率 */
  recall: number;
  /** 负样本正确拒绝率，越低说明误召回越严重 */
  negativePassRate: number;
  /** 平均倒数排名 */
  mrr: number;
  /** 综合准确率 */
  accuracy: number;
}

/** 检索评估报告 */
export interface RetrievalEvalReport {
  kbId: number;
  kbName: string;
  topK: number;
  builtInCases: boolean;
  modes: EvalModeReport[];
  cases: EvalCaseResult[];
}
