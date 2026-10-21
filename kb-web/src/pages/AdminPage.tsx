/**
 * 后台管理页：调用统计 + 模型配置。
 * <p>
 * 权限：整个 /api/v1/admin/** 由后端拦截器校验 ADMIN 角色，
 * 前端这里的角色判断只负责「不给无权限用户看到入口和空页面」，
 * 真正的安全边界始终在服务端 —— 前端隐藏按钮从来不是权限控制。
 *
 * @author zhanglang
 */

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Result,
  Row,
  Segmented,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import {
  ApiOutlined,
  CloudServerOutlined,
  DeleteOutlined,
  EditOutlined,
  ExperimentOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { adminApi } from '../api/admin';
import { ApiError } from '../api/client';
import type {
  ModelConfigRequest,
  ModelConfigVO,
  StatOverviewVO,
  StatTrendItemVO,
} from '../types';
import { useAuth } from '../context/AuthContext';
import TrendChart from '../components/TrendChart';
import type { MetricKey } from '../components/TrendChart';
import EvalPanel from '../components/EvalPanel';
import { formatLatency, formatMoney, formatNumber } from '../utils/format';
import { toast } from '../utils/feedback';

/** 费用为 0 时提示用户这是"未配置单价"而非"真的免费" */
const COST_HINT = '按模型配置中的输入/输出单价估算，未配置单价时为 0';

export default function AdminPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [overview, setOverview] = useState<StatOverviewVO | null>(null);
  const [trend, setTrend] = useState<StatTrendItemVO[]>([]);
  const [days, setDays] = useState(7);
  const [metric, setMetric] = useState<MetricKey>('callCount');
  const [statLoading, setStatLoading] = useState(false);

  const [models, setModels] = useState<ModelConfigVO[]>([]);
  const [modelLoading, setModelLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ModelConfigVO | null>(null);
  const [saving, setSaving] = useState(false);
  const [testingId, setTestingId] = useState<number | null>(null);
  const [form] = Form.useForm<ModelConfigRequest>();

  const loadStat = useCallback(async (range: number) => {
    setStatLoading(true);
    try {
      const [ov, tr] = await Promise.all([adminApi.overview(), adminApi.trend(range)]);
      setOverview(ov);
      setTrend(tr);
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : '统计数据加载失败');
    } finally {
      setStatLoading(false);
    }
  }, []);

  const loadModels = useCallback(async () => {
    setModelLoading(true);
    try {
      setModels(await adminApi.listModels());
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : '模型配置加载失败');
    } finally {
      setModelLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!isAdmin) {
      return;
    }
    loadStat(days);
    loadModels();
    // 仅在挂载时初始化，后面由用户操作显式刷新
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin]);

  if (!isAdmin) {
    return (
      <Result
        status="403"
        title="没有访问权限"
        subTitle="后台管理仅对管理员开放。如需权限请联系系统管理员。"
      />
    );
  }

  const openCreate = () => {
    setEditing(null);
    form.setFieldsValue({
      provider: '',
      modelName: '',
      modelType: 'chat',
      baseUrl: '',
      apiKey: '',
      inputPrice: 0,
      outputPrice: 0,
      isDefault: 0,
      status: 1,
    });
    setModalOpen(true);
  };

  const openEdit = (record: ModelConfigVO) => {
    setEditing(record);
    form.setFieldsValue({
      provider: record.provider,
      modelName: record.modelName,
      modelType: record.modelType as 'chat' | 'embedding',
      baseUrl: record.baseUrl,
      // 密钥不回显：后端只返回脱敏值，留空即"不修改"
      apiKey: '',
      inputPrice: record.inputPrice ?? 0,
      outputPrice: record.outputPrice ?? 0,
      isDefault: record.isDefault,
      status: record.status,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    let values: ModelConfigRequest;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }

    // Switch 给的是 boolean，后端 DTO 是 Integer，必须显式转换，否则反序列化失败
    const payload: ModelConfigRequest = {
      ...values,
      isDefault: Number(values.isDefault ?? 0),
      status: Number(values.status ?? 1),
      apiKey: values.apiKey?.trim() || undefined,
    };

    setSaving(true);
    try {
      if (editing) {
        await adminApi.updateModel(editing.id, payload);
        toast.success('配置已更新');
      } else {
        await adminApi.createModel(payload);
        toast.success('配置已创建');
      }
      setModalOpen(false);
      await loadModels();
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleToggle = async (record: ModelConfigVO, patch: Partial<ModelConfigRequest>) => {
    try {
      await adminApi.updateModel(record.id, {
        provider: record.provider,
        modelName: record.modelName,
        modelType: record.modelType as 'chat' | 'embedding',
        baseUrl: record.baseUrl,
        inputPrice: record.inputPrice,
        outputPrice: record.outputPrice,
        isDefault: record.isDefault,
        status: record.status,
        ...patch,
      });
      await loadModels();
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : '更新失败');
    }
  };

  const handleTest = async (record: ModelConfigVO) => {
    setTestingId(record.id);
    try {
      const message = await adminApi.testModel(record.id);
      toast.success(message || '连接成功');
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : '连通性测试失败');
    } finally {
      setTestingId(null);
    }
  };

  const handleDelete = async (record: ModelConfigVO) => {
    try {
      await adminApi.removeModel(record.id);
      toast.success('配置已删除');
      await loadModels();
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : '删除失败');
    }
  };

  const columns: TableColumnsType<ModelConfigVO> = [
    {
      title: '模型',
      key: 'model',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.modelName}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {record.provider}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'modelType',
      key: 'modelType',
      width: 96,
      render: (type: string) => (
        <Tag color={type === 'chat' ? 'geekblue' : 'cyan'} bordered={false}>
          {type === 'chat' ? '对话' : '向量'}
        </Tag>
      ),
    },
    {
      title: '接口地址',
      dataIndex: 'baseUrl',
      key: 'baseUrl',
      ellipsis: true,
      render: (url: string) => (
        <Typography.Text style={{ fontSize: 12.5 }} copyable={{ text: url }}>
          {url}
        </Typography.Text>
      ),
    },
    {
      title: 'API Key',
      dataIndex: 'apiKeyMasked',
      key: 'apiKeyMasked',
      width: 150,
      render: (masked: string) => (
        <Typography.Text code style={{ fontSize: 12 }}>
          {masked || '未配置'}
        </Typography.Text>
      ),
    },
    {
      title: '默认',
      dataIndex: 'isDefault',
      key: 'isDefault',
      width: 84,
      render: (value: number, record) => (
        <Switch
          size="small"
          checked={value === 1}
          onChange={(checked) => handleToggle(record, { isDefault: checked ? 1 : 0 })}
        />
      ),
    },
    {
      title: '启用',
      dataIndex: 'status',
      key: 'status',
      width: 84,
      render: (value: number, record) => (
        <Switch
          size="small"
          checked={value === 1}
          onChange={(checked) => handleToggle(record, { status: checked ? 1 : 0 })}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      align: 'right',
      render: (_, record) => (
        <Space size={0}>
          <Tooltip title="连通性测试（会真实发起一次调用）">
            <Button
              type="text"
              size="small"
              icon={<ExperimentOutlined />}
              loading={testingId === record.id}
              onClick={() => handleTest(record)}
            />
          </Tooltip>
          <Tooltip title="编辑">
            <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)} />
          </Tooltip>
          <Tooltip title="删除">
            <Button
              type="text"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => {
                Modal.confirm({
                  title: `删除模型配置 ${record.modelName}？`,
                  content: '删除后引用该配置的功能将不可用，请确认已有替代配置。',
                  okText: '删除',
                  okButtonProps: { danger: true },
                  cancelText: '取消',
                  onOk: () => handleDelete(record),
                });
              }}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  const overviewTab = (
    <div>
      <Row gutter={[16, 16]}>
        <Col xs={12} lg={6}>
          <Card variant="borderless" loading={statLoading}>
            <Statistic
              title="今日调用"
              value={overview?.todayCalls ?? 0}
              suffix="次"
              prefix={<ApiOutlined style={{ color: 'var(--brand)' }} />}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card variant="borderless" loading={statLoading}>
            <Statistic title="今日 Token" value={overview?.todayTokens ?? 0} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card variant="borderless" loading={statLoading}>
            <Tooltip title={COST_HINT}>
              <Statistic title="今日费用（估算）" value={formatMoney(overview?.todayCost ?? 0)} />
            </Tooltip>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card variant="borderless" loading={statLoading}>
            <Statistic title="今日平均延迟" value={formatLatency(overview?.todayAvgLatencyMs ?? 0)} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={12} lg={6}>
          <Card variant="borderless" loading={statLoading}>
            <Statistic
              title="今日失败"
              value={overview?.todayFailCount ?? 0}
              suffix="次"
              valueStyle={{ color: (overview?.todayFailCount ?? 0) > 0 ? '#dc2626' : undefined }}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card variant="borderless" loading={statLoading}>
            <Statistic
              title="失败率"
              value={overview?.todayFailRate ?? 0}
              precision={1}
              suffix="%"
              valueStyle={{ color: (overview?.todayFailRate ?? 0) > 5 ? '#dc2626' : undefined }}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card variant="borderless" loading={statLoading}>
            <Statistic
              title="累计调用"
              value={formatNumber(overview?.totalCalls ?? 0)}
              prefix={<CloudServerOutlined style={{ opacity: 0.45 }} />}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card variant="borderless" loading={statLoading}>
            <Statistic title="累计 Token" value={formatNumber(overview?.totalTokens ?? 0)} />
          </Card>
        </Col>
      </Row>

      <Card
        variant="borderless"
        style={{ marginTop: 16 }}
        title="调用趋势"
        extra={
          <Space wrap>
            <Segmented
              size="small"
              value={metric}
              onChange={(value) => setMetric(value as MetricKey)}
              options={[
                { label: '调用次数', value: 'callCount' },
                { label: 'Token', value: 'totalTokens' },
                { label: '费用', value: 'totalCost' },
              ]}
            />
            <Segmented
              size="small"
              value={days}
              onChange={(value) => {
                const range = Number(value);
                setDays(range);
                loadStat(range);
              }}
              options={[
                { label: '7 天', value: 7 },
                { label: '14 天', value: 14 },
                { label: '30 天', value: 30 },
              ]}
            />
            <Tooltip title="刷新">
              <Button type="text" size="small" icon={<ReloadOutlined />} onClick={() => loadStat(days)} />
            </Tooltip>
          </Space>
        }
        loading={statLoading}
      >
        <TrendChart data={trend} metric={metric} />
      </Card>
    </div>
  );

  const modelsTab = (
    <div>
      <Alert
        style={{ marginBottom: 16 }}
        type="info"
        showIcon
        message="模型配置用于记录各供应商的接入参数与计费单价"
        description="问答链路当前使用 application.yml 与环境变量中的配置；此处的配置用于管理与成本核算，修改后需重启服务才会作用于推理链路。"
      />
      <Card
        variant="borderless"
        title="模型配置"
        extra={
          <Space>
            <Tooltip title="刷新">
              <Button type="text" icon={<ReloadOutlined />} onClick={loadModels} />
            </Tooltip>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新增配置
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={models}
          loading={modelLoading}
          pagination={false}
          scroll={{ x: 900 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="还没有模型配置，点击右上角新增"
              />
            ),
          }}
        />
      </Card>
    </div>
  );

  return (
    <div>
      <Tabs
        defaultActiveKey="overview"
        items={[
          { key: 'overview', label: '调用统计', children: overviewTab },
          { key: 'models', label: '模型配置', children: modelsTab },
          { key: 'eval', label: '检索评估', children: <EvalPanel /> },
        ]}
      />

      <Modal
        title={editing ? '编辑模型配置' : '新增模型配置'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
        okText={editing ? '保存' : '创建'}
        cancelText="取消"
        destroyOnHidden
        width={560}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="provider"
                label="提供方"
                rules={[{ required: true, message: '请输入提供方' }]}
                tooltip="仅作分类展示，如 siliconflow / openai / dashscope"
              >
                <Input placeholder="siliconflow" maxLength={32} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="modelType"
                label="类型"
                rules={[{ required: true, message: '请选择类型' }]}
              >
                <Select
                  options={[
                    { label: '对话（chat）', value: 'chat' },
                    { label: '向量（embedding）', value: 'embedding' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="modelName"
            label="模型名"
            rules={[{ required: true, message: '请输入模型名' }]}
          >
            <Input placeholder="BAAI/bge-m3 或 agnes-3.0-flash" maxLength={64} />
          </Form.Item>

          <Form.Item
            name="baseUrl"
            label="接口地址"
            rules={[
              { required: true, message: '请输入接口地址' },
              { pattern: /^https?:\/\/.+/, message: '必须以 http:// 或 https:// 开头' },
            ]}
            tooltip="OpenAI 兼容地址，末尾必须带 /v1，否则会 404"
          >
            <Input placeholder="https://api.siliconflow.cn/v1" maxLength={255} />
          </Form.Item>

          <Form.Item
            name="apiKey"
            label={editing ? 'API Key（留空表示不修改）' : 'API Key'}
            rules={editing ? [] : [{ required: true, message: '请输入 API Key' }]}
            tooltip="服务端使用 AES/GCM 加密存储，列表只回显脱敏值"
          >
            <Input.Password
              placeholder={editing ? `当前：${editing.apiKeyMasked}` : 'sk-...'}
              autoComplete="new-password"
            />
          </Form.Item>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="inputPrice"
                label="输入单价（元 / 1K token）"
                tooltip="用于成本核算，不确定可填 0"
              >
                <InputNumber min={0} step={0.0001} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="outputPrice" label="输出单价（元 / 1K token）">
                <InputNumber min={0} step={0.0001} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="isDefault"
                label="设为该类型默认"
                valuePropName="checked"
                // 数据库存 1/0，Switch 需要 boolean。两个方向都要转换：
                // getValueProps 负责 1 → true（回显），getValueFromEvent 负责 true → 1（提交）
                getValueProps={(value) => ({ checked: value === 1 })}
                getValueFromEvent={(checked: boolean) => (checked ? 1 : 0)}
              >
                <Switch />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="status"
                label="启用"
                valuePropName="checked"
                getValueProps={(value) => ({ checked: value === 1 })}
                getValueFromEvent={(checked: boolean) => (checked ? 1 : 0)}
              >
                <Switch />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
}
