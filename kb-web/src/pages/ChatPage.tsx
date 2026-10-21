/**
 * 智能问答页。
 * <p>
 * 流式实现要点（与后端 SSE 契约一一对应）：
 * <ol>
 *     <li>引用（sources）先到达，正文（content）后逐字到达，最后是 done</li>
 *     <li>新会话的 ID 由 done 事件回传，前端拿到后保存，后续提问复用它实现多轮</li>
 *     <li>「停止生成」通过 AbortController 中断 fetch 读取，服务端会在下一次 write 时感知断连</li>
 * </ol>
 *
 * @author zhanglang
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Avatar,
  Button,
  Empty,
  Input,
  Popconfirm,
  Space,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { InputRef } from 'antd';
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  FileTextOutlined,
  MessageOutlined,
  PlusOutlined,
  RobotOutlined,
  SendOutlined,
  StopOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { chatApi, streamChat } from '../api/chat';
import { kbApi } from '../api/kb';
import { ApiError } from '../api/client';
import type { ConversationVO, KnowledgeBase, SourceVO } from '../types';
import SourceList from '../components/SourceList';
import { formatFromNow, formatLatency } from '../utils/format';
import { toast } from '../utils/feedback';

/** 界面用的消息结构：在 DTO 基础上增加流式状态 */
interface DisplayMessage {
  key: string;
  role: 'user' | 'assistant';
  content: string;
  sources?: SourceVO[];
  promptTokens?: number;
  completionTokens?: number;
  latencyMs?: number;
  gmtCreate?: string;
  /** 正在接收增量内容 */
  streaming?: boolean;
  /** 被用户主动停止 */
  stopped?: boolean;
  /** 生成失败信息 */
  error?: string;
}

/** 空状态下的引导问题。用真实文档内容提问比"随便说点什么"转化率高得多 */
const SUGGESTIONS = ['这份文档的主要内容是什么？', '帮我总结关键要点', '有哪些注意事项或限制？'];

export default function ChatPage() {
  const { kbId: kbIdParam } = useParams<{ kbId: string }>();
  const kbId = Number(kbIdParam);
  const navigate = useNavigate();

  const [kb, setKb] = useState<KnowledgeBase | null>(null);
  const [conversations, setConversations] = useState<ConversationVO[]>([]);
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [convLoading, setConvLoading] = useState(false);

  const abortRef = useRef<AbortController | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<InputRef>(null);
  // 消息 key 自增序号，保证同一次会话内不重复（时间戳在同毫秒内会撞）
  const seqRef = useRef(0);

  // 新建会话时 onDone 会把 conversationId 回填，该变化会触发「加载历史消息」的 effect。
  // 但此刻数据库里的消息可能还没写完（引用快照正在落库），直接重载会出现两种情况：
  // 正文被空内容覆盖，或者引用列表闪烁。用一次性标记跳过这次自动加载，
  // 等用户下次主动切换会话时再走历史接口。
  const skipHistoryOnceRef = useRef(false);

  const nextKey = (prefix: string) => {
    seqRef.current += 1;
    return `${prefix}-${seqRef.current}`;
  };

  const loadConversations = useCallback(async () => {
    if (!Number.isFinite(kbId)) {
      return;
    }
    try {
      setConversations(await chatApi.conversations(kbId));
    } catch {
      // 会话列表加载失败不阻塞主流程，静默处理
    }
  }, [kbId]);

  // 初始化：知识库信息 + 会话列表
  useEffect(() => {
    if (!Number.isFinite(kbId)) {
      return;
    }
    kbApi.detail(kbId).then(setKb).catch(() => undefined);
    loadConversations();
  }, [kbId, loadConversations]);

  // 切换会话时加载历史消息
  useEffect(() => {
    if (!conversationId) {
      setMessages([]);
      return;
    }
    if (skipHistoryOnceRef.current) {
      skipHistoryOnceRef.current = false;
      return;
    }
    let cancelled = false;
    setConvLoading(true);
    chatApi
      .messages(conversationId)
      .then((list) => {
        if (cancelled) {
          return;
        }
        setMessages(
          list.map((item) => ({
            key: `m-${item.id}`,
            role: item.role === 'user' ? 'user' : 'assistant',
            content: item.content,
            sources: item.sources,
            promptTokens: item.promptTokens,
            completionTokens: item.completionTokens,
            gmtCreate: item.gmtCreate,
          })),
        );
      })
      .catch((error) => {
        if (!cancelled) {
          toast.error(error instanceof ApiError ? error.message : '历史消息加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setConvLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [conversationId]);

  // 新内容到达后滚到底部。用 instant 而非 smooth：流式每秒更新数十次，
  // smooth 会不断重置动画，表现为页面"抖动着追不上"
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' });
  }, [messages]);

  // 离开页面时必须中断流，否则连接会一直挂着直到服务端超时
  useEffect(
    () => () => {
      abortRef.current?.abort();
    },
    [],
  );

  /** 局部更新某条消息 */
  const patchMessage = (
    key: string,
    updater: Partial<DisplayMessage> | ((msg: DisplayMessage) => Partial<DisplayMessage>),
  ) => {
    setMessages((prev) =>
      prev.map((msg) => {
        if (msg.key !== key) {
          return msg;
        }
        const delta = typeof updater === 'function' ? updater(msg) : updater;
        return { ...msg, ...delta };
      }),
    );
  };

  const handleSend = async (preset?: string) => {
    const question = (preset ?? input).trim();
    if (!question || sending) {
      return;
    }
    if (kb && kb.docCount === 0) {
      toast.warning('该知识库还没有可用文档，先上传并等待解析完成');
      return;
    }

    setInput('');
    const userKey = nextKey('u');
    const aiKey = nextKey('a');
    const now = new Date().toISOString();

    setMessages((prev) => [
      ...prev,
      { key: userKey, role: 'user', content: question, gmtCreate: now },
      { key: aiKey, role: 'assistant', content: '', streaming: true, gmtCreate: now },
    ]);
    setSending(true);

    const controller = new AbortController();
    abortRef.current = controller;
    let finished = false;

    try {
      await streamChat(
        { kbId, conversationId: conversationId ?? undefined, question },
        {
          onSources: (sources) => patchMessage(aiKey, { sources }),
          onDelta: (delta) => patchMessage(aiKey, (msg) => ({ content: msg.content + delta })),
          onDone: (info) => {
            finished = true;
            patchMessage(aiKey, {
              streaming: false,
              promptTokens: info.promptTokens,
              completionTokens: info.completionTokens,
              latencyMs: info.latencyMs,
            });
            // 新建会话时由服务端生成 ID，必须回填，否则下一轮会重复建会话
            if (!conversationId) {
              skipHistoryOnceRef.current = true;
              setConversationId(info.conversationId);
            }
            loadConversations();
          },
          onError: (message) => {
            finished = true;
            patchMessage(aiKey, { streaming: false, error: message });
          },
        },
        controller.signal,
      );

      if (!finished) {
        // 流在没有 done / error 的情况下结束（如网关掐断），也要收尾，避免光标一直闪
        patchMessage(aiKey, { streaming: false });
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        patchMessage(aiKey, { streaming: false, stopped: true });
      } else {
        const message = error instanceof ApiError ? error.message : '问答失败，请稍后重试';
        patchMessage(aiKey, { streaming: false, error: message });
      }
    } finally {
      setSending(false);
      abortRef.current = null;
      inputRef.current?.focus();
    }
  };

  const handleStop = () => {
    abortRef.current?.abort();
  };

  const handleNewConversation = () => {
    if (sending) {
      toast.warning('正在生成中，请先停止');
      return;
    }
    setConversationId(null);
    setMessages([]);
    inputRef.current?.focus();
  };

  const handleSelectConversation = (id: number) => {
    if (sending) {
      toast.warning('正在生成中，请先停止');
      return;
    }
    if (id === conversationId) {
      return;
    }
    setConversationId(id);
  };

  const handleDeleteConversation = async (id: number) => {
    try {
      await chatApi.remove(id);
      toast.success('会话已删除');
      if (id === conversationId) {
        setConversationId(null);
        setMessages([]);
      }
      loadConversations();
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : '删除失败');
    }
  };

  return (
    <div className="chat-page">
      <aside className="chat-sidebar">
        <div className="chat-sidebar-head">
          <Button type="primary" block icon={<PlusOutlined />} onClick={handleNewConversation}>
            新建对话
          </Button>
        </div>
        <div className="chat-conv-list">
          {conversations.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={<span style={{ fontSize: 12.5 }}>暂无历史会话</span>}
              style={{ marginTop: 40 }}
            />
          ) : (
            conversations.map((conv) => (
              <div
                key={conv.id}
                className={`chat-conv-item${conv.id === conversationId ? ' active' : ''}`}
                onClick={() => handleSelectConversation(conv.id)}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <MessageOutlined style={{ fontSize: 12, opacity: 0.5 }} />
                  <div className="chat-conv-title" title={conv.title}>
                    {conv.title || '未命名会话'}
                  </div>
                  <Popconfirm
                    title="删除该会话？"
                    okText="删除"
                    okButtonProps={{ danger: true }}
                    cancelText="取消"
                    onConfirm={(e) => {
                      // 阻止冒泡，避免点击删除时同时触发会话切换
                      e?.stopPropagation();
                      handleDeleteConversation(conv.id);
                    }}
                  >
                    <Button
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      style={{ opacity: 0.55 }}
                      onClick={(e) => e.stopPropagation()}
                    />
                  </Popconfirm>
                </div>
                <div className="chat-conv-meta">
                  <span>{conv.messageCount} 条消息</span>
                  <span>{formatFromNow(conv.lastMessageAt)}</span>
                </div>
              </div>
            ))
          )}
        </div>
      </aside>

      <section className="chat-main">
        <div className="chat-main-head">
          <Space size={8} wrap>
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate(`/kb/${kbId}`)}>
              文档
            </Button>
            <Typography.Text strong>{kb?.name ?? '知识库'}</Typography.Text>
            {kb?.embeddingModel && (
              <Tag bordered={false} color="blue">
                {kb.embeddingModel}
              </Tag>
            )}
            {kb && (
              <Tag bordered={false}>
                召回 {kb.topK} · 阈值 {Number(kb.similarityThreshold).toFixed(2)}
              </Tag>
            )}
          </Space>
          <Space size={6}>
            <Tooltip title="查看文档列表">
              <Button type="text" icon={<FileTextOutlined />} onClick={() => navigate(`/kb/${kbId}`)} />
            </Tooltip>
          </Space>
        </div>

        <div className="chat-scroll">
          {messages.length === 0 ? (
            <div className="chat-hero">
              <Avatar size={56} style={{ background: 'linear-gradient(135deg,#6366f1,#8b5cf6)' }} icon={<RobotOutlined />} />
              <h2>基于你的文档回答问题</h2>
              <p>
                提问后会先在知识库中检索相关片段，再由模型依据片段作答，
                每条答案都附带原文引用与相似度，可点击展开核对。
              </p>
              <div className="chat-suggests">
                {SUGGESTIONS.map((item) => (
                  <Button key={item} shape="round" onClick={() => handleSend(item)}>
                    {item}
                  </Button>
                ))}
              </div>
              {kb && kb.docCount === 0 && (
                <Alert
                  style={{ marginTop: 24, textAlign: 'left' }}
                  type="warning"
                  showIcon
                  message="当前知识库还没有可用文档"
                  description="请先上传文档并等待解析完成，否则检索不到任何内容。"
                  action={
                    <Button size="small" onClick={() => navigate(`/kb/${kbId}`)}>
                      去上传
                    </Button>
                  }
                />
              )}
            </div>
          ) : (
            <div className="chat-thread" style={{ opacity: convLoading ? 0.5 : 1 }}>
              {messages.map((msg) => (
                <div key={msg.key} className={`msg-row ${msg.role}`}>
                  <Avatar
                    className="msg-avatar"
                    size={30}
                    style={
                      msg.role === 'user'
                        ? { background: '#64748b' }
                        : { background: 'linear-gradient(135deg,#6366f1,#8b5cf6)' }
                    }
                    icon={msg.role === 'user' ? <UserOutlined /> : <RobotOutlined />}
                  />
                  <div className="msg-body">
                    {msg.role === 'assistant' && msg.sources && msg.sources.length > 0 && (
                      <div style={{ marginBottom: 6 }}>
                        <Space size={6} wrap>
                          <Tag color="purple" bordered={false}>
                            已检索 {msg.sources.length} 个片段
                          </Tag>
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            最高相似度 {msg.sources[0].score?.toFixed(4)}
                          </Typography.Text>
                        </Space>
                      </div>
                    )}

                    <div className="msg-bubble">
                      {msg.content}
                      {msg.streaming && <span className="caret" />}
                      {!msg.content && msg.streaming && !msg.sources && (
                        <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                          正在检索知识库…
                        </Typography.Text>
                      )}
                      {!msg.content && !msg.streaming && !msg.error && (
                        <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                          （无内容）
                        </Typography.Text>
                      )}
                    </div>

                    {msg.error && (
                      <Alert
                        style={{ marginTop: 8 }}
                        type="error"
                        showIcon
                        message={msg.error}
                      />
                    )}

                    {msg.role === 'assistant' && msg.sources && msg.sources.length > 0 && (
                      <SourceList sources={msg.sources} />
                    )}

                    <div className="msg-footnote">
                      {msg.stopped && <span>已停止生成</span>}
                      {/* 用户消息没有 token 消耗，历史接口返回的是 0，
                          这里必须同时判断角色与数值，否则用户气泡下会冒出 "Token 0 + 0" */}
                      {msg.role === 'assistant' &&
                        (msg.promptTokens ?? 0) + (msg.completionTokens ?? 0) > 0 && (
                          <span>
                            Token {msg.promptTokens} + {msg.completionTokens}
                          </span>
                        )}
                      {msg.role === 'assistant' && msg.latencyMs !== undefined && (
                        <span>耗时 {formatLatency(msg.latencyMs)}</span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
              <div ref={bottomRef} />
            </div>
          )}
        </div>

        <div className="chat-composer">
          <div className="chat-composer-inner">
            <Input.TextArea
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="输入你的问题，Enter 发送，Shift + Enter 换行"
              autoSize={{ minRows: 1, maxRows: 6 }}
              maxLength={2000}
              disabled={sending}
              onPressEnter={(e) => {
                // 中文输入法合成阶段按 Enter 是"选词确认"，不能当成发送
                if (e.nativeEvent.isComposing) {
                  return;
                }
                if (!e.shiftKey) {
                  e.preventDefault();
                  handleSend();
                }
              }}
              style={{ borderRadius: 10 }}
            />
            <div className="chat-composer-tip">
              <span>答案由模型基于检索片段生成，请核对引用原文</span>
              <Space size={8}>
                <span>{input.length}/2000</span>
                {sending ? (
                  <Button danger icon={<StopOutlined />} onClick={handleStop}>
                    停止生成
                  </Button>
                ) : (
                  <Button
                    type="primary"
                    icon={<SendOutlined />}
                    onClick={() => handleSend()}
                    disabled={!input.trim()}
                  >
                    发送
                  </Button>
                )}
              </Space>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
