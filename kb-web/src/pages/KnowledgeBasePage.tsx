/**
 * 知识库列表页。
 *
 * @author zhanglang
 */

import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Col,
  Collapse,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Pagination,
  Popconfirm,
  Row,
  Skeleton,
  Space,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  CloudUploadOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  EditOutlined,
  MessageOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { kbApi } from '../api/kb';
import { ApiError } from '../api/client';
import type { KbCreateRequest, KnowledgeBase } from '../types';
import { formatDateTime } from '../utils/format';
import { toast } from '../utils/feedback';

/** 分页大小：3 列布局下 9 条刚好铺满三行 */
const PAGE_SIZE = 9;

/** 新建时的默认检索参数，与后端建库默认值保持一致 */
const DEFAULT_VALUES: KbCreateRequest = {
  name: '',
  description: '',
  chunkSize: 500,
  chunkOverlap: 60,
  topK: 5,
  similarityThreshold: 0.45,
};

export default function KnowledgeBasePage() {
  const [list, setList] = useState<KnowledgeBase[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<KnowledgeBase | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<KbCreateRequest>();
  const navigate = useNavigate();

  const load = useCallback(
    async (options?: { page?: number; kw?: string; silent?: boolean }) => {
      const nextPage = options?.page ?? pageNo;
      const nextKeyword = options?.kw ?? keyword;
      if (!options?.silent) {
        setLoading(true);
      }
      try {
        const result = await kbApi.page({ keyword: nextKeyword, pageNo: nextPage, pageSize: PAGE_SIZE });
        setList(result.records);
        setTotal(result.total);
        setPageNo(result.pageNo);
      } catch (error) {
        const message = error instanceof ApiError ? error.message : '知识库加载失败';
        toast.error(message);
      } finally {
        setLoading(false);
      }
    },
    [pageNo, keyword],
  );

  useEffect(() => {
    load({ page: 1, kw: '' });
    // 仅首次进入时加载，后续由搜索/翻页显式触发
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.setFieldsValue(DEFAULT_VALUES);
    setModalOpen(true);
  };

  const openEdit = (item: KnowledgeBase) => {
    setEditing(item);
    form.setFieldsValue({
      name: item.name,
      description: item.description ?? '',
      chunkSize: item.chunkSize,
      chunkOverlap: item.chunkOverlap,
      topK: item.topK,
      similarityThreshold: item.similarityThreshold,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    let values: KbCreateRequest;
    try {
      values = await form.validateFields();
    } catch {
      // 校验失败时 antd 已在表单上给出提示，无需额外处理
      return;
    }

    setSaving(true);
    try {
      if (editing) {
        await kbApi.update(editing.id, values);
        toast.success('知识库已更新');
      } else {
        await kbApi.create(values);
        toast.success('知识库创建成功');
      }
      setModalOpen(false);
      await load({ page: editing ? pageNo : 1, silent: true });
      if (!editing) {
        setPageNo(1);
      }
    } catch (error) {
      const message = error instanceof ApiError ? error.message : '保存失败';
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (item: KnowledgeBase) => {
    try {
      await kbApi.remove(item.id);
      toast.success('已删除，文档与向量已同步清理');
      // 当前页只剩最后一条时回退一页，避免停留在空白页
      const nextPage = list.length === 1 && pageNo > 1 ? pageNo - 1 : pageNo;
      await load({ page: nextPage });
    } catch (error) {
      const message = error instanceof ApiError ? error.message : '删除失败';
      toast.error(message);
    }
  };

  const handleSearch = (value: string) => {
    const kw = value.trim();
    setKeyword(kw);
    setPageNo(1);
    load({ page: 1, kw });
  };

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 18 }} gutter={[12, 12]}>
        <Col>
          <Space size={10}>
            <Typography.Title level={5} style={{ margin: 0 }}>
              全部知识库
            </Typography.Title>
            <Tag bordered={false}>{total} 个</Tag>
          </Space>
        </Col>
        <Col>
          <Space>
            <Input.Search
              allowClear
              placeholder="搜索知识库名称"
              prefix={<SearchOutlined />}
              style={{ width: 240 }}
              onSearch={handleSearch}
            />
            <Tooltip title="刷新">
              <Button icon={<ReloadOutlined />} onClick={() => load()} />
            </Tooltip>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建知识库
            </Button>
          </Space>
        </Col>
      </Row>

      {loading ? (
        <Row gutter={[16, 16]}>
          {[0, 1, 2].map((key) => (
            <Col key={key} xs={24} sm={12} xl={8}>
              <Card>
                <Skeleton active paragraph={{ rows: 3 }} />
              </Card>
            </Col>
          ))}
        </Row>
      ) : list.length === 0 ? (
        <Card variant="borderless" style={{ padding: '48px 0' }}>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              keyword ? `没有匹配「${keyword}」的知识库` : '还没有知识库，先创建一个开始使用'
            }
          >
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建知识库
            </Button>
          </Empty>
        </Card>
      ) : (
        <>
          <Row gutter={[16, 16]}>
            {list.map((item) => (
              <Col key={item.id} xs={24} sm={12} xl={8}>
                <Card className="kb-card" variant="outlined">
                  <div className="kb-card-head">
                    <div className="kb-card-name">{item.name}</div>
                    <Tag color={item.status === 1 ? 'green' : 'default'} bordered={false}>
                      {item.status === 1 ? '启用' : '停用'}
                    </Tag>
                  </div>

                  <div className="kb-card-desc">{item.description || '暂无描述'}</div>

                  <div className="kb-card-meta">
                    <Tag icon={<DatabaseOutlined />} bordered={false} color="blue">
                      {item.docCount} 文档
                    </Tag>
                    <Tag bordered={false}>{item.embeddingDim ?? '—'} 维</Tag>
                    <Tag bordered={false}>块 {item.chunkSize}</Tag>
                    <Tag bordered={false}>召回 {item.topK}</Tag>
                    <Tag bordered={false}>阈值 {Number(item.similarityThreshold).toFixed(2)}</Tag>
                  </div>

                  <Tooltip title={`创建于 ${formatDateTime(item.gmtCreate)}`}>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {item.embeddingModel || 'embedding 未配置'}
                    </Typography.Text>
                  </Tooltip>

                  <div className="kb-card-footer">
                    <Space size={4}>
                      <Button
                        type="link"
                        size="small"
                        icon={<CloudUploadOutlined />}
                        onClick={() => navigate(`/kb/${item.id}`)}
                      >
                        文档
                      </Button>
                      <Button
                        type="link"
                        size="small"
                        icon={<MessageOutlined />}
                        onClick={() => navigate(`/kb/${item.id}/chat`)}
                      >
                        问答
                      </Button>
                    </Space>
                    <Space size={0}>
                      <Tooltip title="编辑">
                        <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openEdit(item)} />
                      </Tooltip>
                      <Popconfirm
                        title="删除该知识库？"
                        description="库内文档与已入库的向量会一并删除，不可恢复。"
                        okText="删除"
                        okButtonProps={{ danger: true }}
                        cancelText="取消"
                        onConfirm={() => handleDelete(item)}
                      >
                        <Tooltip title="删除">
                          <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                        </Tooltip>
                      </Popconfirm>
                    </Space>
                  </div>
                </Card>
              </Col>
            ))}
          </Row>

          {total > PAGE_SIZE && (
            <Row justify="end" style={{ marginTop: 20 }}>
              <Pagination
                current={pageNo}
                pageSize={PAGE_SIZE}
                total={total}
                showSizeChanger={false}
                onChange={(page) => {
                  setPageNo(page);
                  load({ page });
                }}
              />
            </Row>
          )}
        </>
      )}

      <Modal
        title={editing ? '编辑知识库' : '新建知识库'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
        okText={editing ? '保存' : '创建'}
        cancelText="取消"
        destroyOnHidden
        width={560}
      >
        <Form form={form} layout="vertical" initialValues={DEFAULT_VALUES} style={{ marginTop: 12 }}>
          <Form.Item
            name="name"
            label="知识库名称"
            rules={[
              { required: true, message: '请输入知识库名称' },
              { max: 128, message: '名称不超过 128 个字符' },
            ]}
          >
            <Input placeholder="例如：员工手册 / 产品文档 / 技术规范" maxLength={128} showCount />
          </Form.Item>

          <Form.Item name="description" label="描述" rules={[{ max: 512, message: '描述不超过 512 个字符' }]}>
            <Input.TextArea rows={2} maxLength={512} showCount placeholder="这个知识库存放什么内容" />
          </Form.Item>

          <Collapse
            ghost
            items={[
              {
                key: 'advanced',
                label: '检索参数（高级）',
                children: (
                  <>
                    <Form.Item
                      name="chunkSize"
                      label="切分块大小（字符）"
                      tooltip="过小会把一句话切断，过大会稀释检索精度。中文文档建议 400-800"
                      rules={[{ required: true, message: '请输入切分块大小' }]}
                    >
                      <InputNumber min={200} max={2000} step={50} style={{ width: '100%' }} />
                    </Form.Item>

                    <Form.Item
                      name="chunkOverlap"
                      label="相邻块重叠（字符）"
                      tooltip="用于避免关键句子正好落在切分边界上，一般取块大小的 10%-15%"
                      rules={[{ required: true, message: '请输入重叠字符数' }]}
                    >
                      <InputNumber min={0} max={500} step={10} style={{ width: '100%' }} />
                    </Form.Item>

                    <Form.Item
                      name="topK"
                      label="召回条数"
                      tooltip="每次提问取回多少条片段。过大反而会把无关内容塞进上下文，降低准确率"
                      rules={[{ required: true, message: '请输入召回条数' }]}
                    >
                      <InputNumber min={1} max={20} style={{ width: '100%' }} />
                    </Form.Item>

                    <Form.Item
                      name="similarityThreshold"
                      label="相似度阈值"
                      tooltip="低于该分数的片段会被丢弃。越高越严格，答案更保守"
                      rules={[{ required: true, message: '请输入相似度阈值' }]}
                    >
                      <InputNumber min={0} max={1} step={0.05} style={{ width: '100%' }} />
                    </Form.Item>
                  </>
                ),
              },
            ]}
          />
        </Form>
      </Modal>
    </div>
  );
}
