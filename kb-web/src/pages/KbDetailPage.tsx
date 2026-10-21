/**
 * 知识库详情页：文档上传、解析状态轮询与文档管理。
 *
 * @author zhanglang
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Popconfirm,
  Progress,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import type { TableColumnsType, UploadProps } from 'antd';
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  FileTextOutlined,
  InboxOutlined,
  MessageOutlined,
  RedoOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { docApi } from '../api/doc';
import { kbApi } from '../api/kb';
import { ApiError } from '../api/client';
import type { DocumentVO, KnowledgeBase } from '../types';
import StatusTag, { isPending } from '../components/StatusTag';
import { formatBytes, formatDateTime } from '../utils/format';
import { toast } from '../utils/feedback';

/** 轮询间隔：解析通常在数秒内完成，2 秒一次足够灵敏又不会打爆后端 */
const POLL_INTERVAL = 2000;

/** 允许上传的扩展名，与后端 Tika 解析能力对齐 */
const ACCEPT = '.pdf,.doc,.docx,.txt,.md,.markdown,.html,.htm,.csv';

export default function KbDetailPage() {
  const { kbId: kbIdParam } = useParams<{ kbId: string }>();
  const kbId = Number(kbIdParam);
  const navigate = useNavigate();

  const [kb, setKb] = useState<KnowledgeBase | null>(null);
  const [docs, setDocs] = useState<DocumentVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [retrying, setRetrying] = useState<number | null>(null);

  // 用 ref 保存定时器，组件卸载或依赖变化时可精确清理
  const timerRef = useRef<number | null>(null);

  const loadDocs = useCallback(
    async (silent = false) => {
      if (!Number.isFinite(kbId)) {
        return;
      }
      try {
        const data = await docApi.list(kbId);
        setDocs(data);
      } catch (error) {
        if (!silent) {
          toast.error(error instanceof ApiError ? error.message : '文档列表加载失败');
        }
      }
    },
    [kbId],
  );

  useEffect(() => {
    if (!Number.isFinite(kbId)) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    Promise.all([kbApi.detail(kbId), docApi.list(kbId)])
      .then(([detail, list]) => {
        if (!cancelled) {
          setKb(detail);
          setDocs(list);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          toast.error(error instanceof ApiError ? error.message : '加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [kbId]);

  // 只要还有处理中的文档就持续轮询。
  // 用「递归 setTimeout」而非 setInterval：每次拿到新数据后重新判断，
  // 全部完成立即停止，不会出现定时器空转，也不会出现请求堆积。
  const pendingCount = useMemo(() => docs.filter((d) => isPending(d.parseStatus)).length, [docs]);

  useEffect(() => {
    if (pendingCount === 0) {
      if (timerRef.current) {
        window.clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      return;
    }
    timerRef.current = window.setTimeout(async () => {
      await loadDocs(true);
      // 解析完成后刷新知识库统计（docCount 会变化）
      if (pendingCount > 0) {
        kbApi.detail(kbId).then(setKb).catch(() => undefined);
      }
    }, POLL_INTERVAL);

    return () => {
      if (timerRef.current) {
        window.clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [docs, pendingCount, loadDocs, kbId]);

  const customRequest: UploadProps['customRequest'] = async (options) => {
    const file = options.file as File;
    setUploading(true);
    try {
      const doc = await docApi.upload(kbId, file);
      options.onSuccess?.(doc);
      toast.success(`${file.name} 上传成功，正在后台解析`);
      // 立即刷新一次，让用户看到 PENDING 记录；随后由轮询接管
      await loadDocs(true);
      kbApi.detail(kbId).then(setKb).catch(() => undefined);
    } catch (error) {
      const message = error instanceof ApiError ? error.message : '上传失败';
      options.onError?.(error as Error);
      toast.error(message);
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (doc: DocumentVO) => {
    try {
      await docApi.remove(doc.id);
      toast.success('文档已删除，对应向量已同步清理');
      await loadDocs(true);
      kbApi.detail(kbId).then(setKb).catch(() => undefined);
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : '删除失败');
    }
  };

  const handleRetry = async (doc: DocumentVO) => {
    setRetrying(doc.id);
    try {
      await docApi.retry(doc.id);
      toast.success('已重新提交解析');
      await loadDocs(true);
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : '重试失败');
    } finally {
      setRetrying(null);
    }
  };

  const columns: TableColumnsType<DocumentVO> = [
    {
      title: '文档',
      dataIndex: 'fileName',
      key: 'fileName',
      render: (name: string, record) => (
        <Space size={8}>
          <FileTextOutlined style={{ color: 'var(--brand)' }} />
          <Tooltip title={name}>
            <span style={{ fontWeight: 500 }}>{name}</span>
          </Tooltip>
          <Tag bordered={false} style={{ textTransform: 'uppercase', fontSize: 11 }}>
            {record.fileType || '未知'}
          </Tag>
        </Space>
      ),
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 96,
      render: (size: number) => <Typography.Text type="secondary">{formatBytes(size)}</Typography.Text>,
    },
    {
      title: '页数',
      dataIndex: 'pageCount',
      key: 'pageCount',
      width: 76,
      render: (page?: number) => (page && page > 0 ? page : '—'),
    },
    {
      title: '分块',
      dataIndex: 'chunkCount',
      key: 'chunkCount',
      width: 76,
      render: (count: number) => (count > 0 ? count : '—'),
    },
    {
      title: '状态',
      dataIndex: 'parseStatus',
      key: 'parseStatus',
      width: 108,
      render: (status: DocumentVO['parseStatus'], record) => (
        <StatusTag status={status} errorMsg={record.errorMsg} />
      ),
    },
    {
      title: '上传时间',
      dataIndex: 'gmtCreate',
      key: 'gmtCreate',
      width: 158,
      render: (value?: string) => (
        <Typography.Text type="secondary" style={{ fontSize: 12.5 }}>
          {formatDateTime(value)}
        </Typography.Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 130,
      align: 'right',
      render: (_, record) => (
        <Space size={2}>
          {record.parseStatus === 'FAILED' && (
            <Tooltip title="重新解析">
              <Button
                type="text"
                size="small"
                icon={<RedoOutlined />}
                loading={retrying === record.id}
                onClick={() => handleRetry(record)}
              />
            </Tooltip>
          )}
          <Popconfirm
            title="删除该文档？"
            description="文档及其已入库的向量会一并删除。"
            okText="删除"
            okButtonProps={{ danger: true }}
            cancelText="取消"
            onConfirm={() => handleDelete(record)}
          >
            <Tooltip title="删除">
              <Button type="text" size="small" danger icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const failedDocs = docs.filter((d) => d.parseStatus === 'FAILED');
  const completedRate =
    docs.length === 0
      ? 0
      : Math.round((docs.filter((d) => d.parseStatus === 'COMPLETED').length / docs.length) * 100);

  return (
    <div>
      <Space style={{ marginBottom: 14 }}>
        <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/kb')}>
          返回知识库
        </Button>
      </Space>

      <Card
        variant="borderless"
        style={{ marginBottom: 16 }}
        loading={loading}
        title={
          <Space size={10} wrap>
            <span>{kb?.name ?? '加载中…'}</span>
            <Tag color="blue" bordered={false}>
              {kb?.docCount ?? 0} 个可用文档
            </Tag>
          </Space>
        }
        extra={
          <Button type="primary" icon={<MessageOutlined />} onClick={() => navigate(`/kb/${kbId}/chat`)}>
            开始问答
          </Button>
        }
      >
        <Descriptions column={{ xs: 1, sm: 2, lg: 4 }} size="small">
          <Descriptions.Item label="描述">{kb?.description || '—'}</Descriptions.Item>
          <Descriptions.Item label="向量模型">
            {kb?.embeddingModel || '—'}
            {kb?.embeddingDim ? `（${kb.embeddingDim} 维）` : ''}
          </Descriptions.Item>
          <Descriptions.Item label="切分参数">
            块 {kb?.chunkSize ?? '—'} / 重叠 {kb?.chunkOverlap ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="检索参数">
            召回 {kb?.topK ?? '—'} / 阈值 {kb ? Number(kb.similarityThreshold).toFixed(2) : '—'}
          </Descriptions.Item>
        </Descriptions>

        {docs.length > 0 && (
          <div style={{ marginTop: 8, maxWidth: 360 }}>
            <Typography.Text type="secondary" style={{ fontSize: 12.5 }}>
              解析完成率 {completedRate}%
            </Typography.Text>
            <Progress percent={completedRate} size="small" showInfo={false} />
          </div>
        )}
      </Card>

      <Card variant="borderless" style={{ marginBottom: 16 }}>
        <Upload.Dragger
          multiple
          accept={ACCEPT}
          customRequest={customRequest}
          showUploadList={false}
          disabled={uploading}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">点击或拖拽文件到此处上传</p>
          <p className="ant-upload-hint">
            支持 PDF / Word / TXT / Markdown / HTML / CSV，单文件建议不超过 20MB。
            上传后自动完成文本抽取、切分与向量化，扫描件 PDF（无文字层）当前无法解析。
          </p>
        </Upload.Dragger>
        {uploading && (
          <Alert style={{ marginTop: 12 }} type="info" showIcon message="正在上传文件…" />
        )}
      </Card>

      {failedDocs.length > 0 && (
        <Alert
          style={{ marginBottom: 16 }}
          type="warning"
          showIcon
          message={`${failedDocs.length} 个文档解析失败`}
          description={
            <Space direction="vertical" size={2}>
              {failedDocs.slice(0, 3).map((doc) => (
                <span key={doc.id}>
                  {doc.fileName}：{doc.errorMsg || '未知原因'}
                </span>
              ))}
              {failedDocs.length > 3 && <span>其余文档请查看列表状态提示</span>}
            </Space>
          }
          action={
            <Button size="small" icon={<ReloadOutlined />} onClick={() => loadDocs()}>
              刷新
            </Button>
          }
        />
      )}

      <Card
        variant="borderless"
        title={
          <Space>
            <span>文档列表</span>
            {pendingCount > 0 && (
              <Tag color="processing" bordered={false}>
                {pendingCount} 个处理中
              </Tag>
            )}
          </Space>
        }
        extra={
          <Tooltip title="刷新">
            <Button type="text" icon={<ReloadOutlined />} onClick={() => loadDocs()} />
          </Tooltip>
        }
      >
        <Table
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={docs}
          loading={loading}
          pagination={false}
          scroll={{ x: 860 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="还没有文档，拖拽文件到上方区域即可上传"
              />
            ),
          }}
        />
      </Card>
    </div>
  );
}
