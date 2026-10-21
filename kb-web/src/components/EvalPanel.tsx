/**
 * 检索质量评估面板。
 * <p>
 * 存在的意义：把"融合权重该怎么调"从手感问题变成数据问题。
 * 面板会同时跑多种检索策略（混合 / 纯向量 / 纯关键词），横向对比三项指标：
 * <ul>
 *     <li><b>命中率</b>：该找到的找没找到</li>
 *     <li><b>MRR</b>：找到的排得靠不靠前</li>
 *     <li><b>负样本拒绝率</b>：不该召回的有没有被召回（幻觉的主要来源）</li>
 * </ul>
 *
 * @author zhanglang
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { PlayCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { adminApi } from '../api/admin';
import { kbApi } from '../api/kb';
import { ApiError } from '../api/client';
import type { EvalCaseResult, KnowledgeBase, RetrievalEvalCase, RetrievalEvalReport } from '../types';
import { toast } from '../utils/feedback';

/** 用例编辑格式说明，直接放在输入框上方，省去另写帮助文档 */
const FORMAT_HELP =
  '每行一条用例，用 | 分隔：问题 | 期望关键词(逗号分隔) | 期望文档名(逗号分隔) | 负样本(true 表示期望检索不到)';

/** 初始用例，与后端内置用例保持一致，方便第一次打开就能直接跑 */
const DEFAULT_CASES = [
  '试用期是多久？|试用期|test-doc.txt|',
  '年假有几天？|年假|test-doc.txt|',
  'ZK-8823 错误码怎么处理？|ZK-8823|ops-manual.txt|',
  '公司股票期权怎么行权？|||true',
].join('\n');

/** 策略中文名与配色 */
const MODE_META: Record<string, { name: string; color: string }> = {
  HYBRID: { name: '混合检索', color: 'purple' },
  VECTOR: { name: '纯向量', color: 'blue' },
  KEYWORD: { name: '纯关键词', color: 'orange' },
};

/** 解析用户输入的用例文本 */
function parseCases(text: string): RetrievalEvalCase[] {
  const cases: RetrievalEvalCase[] = [];
  text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith('#'))
    .forEach((line) => {
      const parts = line.split('|').map((part) => part.trim());
      const question = parts[0];
      if (!question) {
        return;
      }
      cases.push({
        question,
        expectKeywords: parts[1] ? parts[1].split(/[,，]/).map((k) => k.trim()).filter(Boolean) : undefined,
        expectDocNames: parts[2] ? parts[2].split(/[,，]/).map((k) => k.trim()).filter(Boolean) : undefined,
        expectMiss: parts[3]?.toLowerCase() === 'true',
      });
    });
  return cases;
}

interface RowData {
  question: string;
  negative: boolean;
  [mode: string]: EvalCaseResult | string | boolean | undefined;
}

export default function EvalPanel() {
  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [kbId, setKbId] = useState<number | undefined>();
  const [text, setText] = useState(DEFAULT_CASES);
  const [report, setReport] = useState<RetrievalEvalReport | null>(null);
  const [loading, setLoading] = useState(false);

  const loadKbs = useCallback(async () => {
    try {
      const result = await kbApi.page({ pageNo: 1, pageSize: 100 });
      setKbs(result.records);
      if (result.records.length > 0) {
        setKbId((prev) => prev ?? result.records[0].id);
      }
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : '知识库列表加载失败');
    }
  }, []);

  useEffect(() => {
    loadKbs();
  }, [loadKbs]);

  const run = async () => {
    if (!kbId) {
      toast.warning('请先选择知识库');
      return;
    }
    setLoading(true);
    try {
      // 解析不出用例时传空数组交给后端用内置用例，避免"用例格式写错 → 评估 0 条"
      const cases = parseCases(text);
      const result = await adminApi.evaluateRetrieval(kbId, cases);
      setReport(result);
      toast.success(`评估完成，共 ${result.modes.length} 种策略 × ${result.cases.length / result.modes.length} 条用例`);
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : '评估失败');
    } finally {
      setLoading(false);
    }
  };

  /** 把「策略 × 用例」的扁平结果透视成「用例为行、策略为列」 */
  const rows = useMemo<RowData[]>(() => {
    if (!report) {
      return [];
    }
    const map = new Map<string, RowData>();
    report.cases.forEach((item) => {
      const row = map.get(item.question) ?? { question: item.question, negative: item.negative };
      row[item.mode] = item;
      map.set(item.question, row);
    });
    return Array.from(map.values());
  }, [report]);

  const columns: TableColumnsType<RowData> = useMemo(() => {
    const base: TableColumnsType<RowData> = [
      {
        title: '用例',
        key: 'question',
        width: 300,
        render: (_, record) => (
          <Space size={6}>
            <span>{record.question}</span>
            {record.negative && (
              <Tag color="warning" bordered={false}>
                负样本
              </Tag>
            )}
          </Space>
        ),
      },
    ];

    if (!report) {
      return base;
    }

    const modeColumns = report.modes.map((mode) => ({
      title: (
        <Tag color={MODE_META[mode.mode]?.color} bordered={false}>
          {mode.modeName}
        </Tag>
      ),
      key: mode.mode,
      render: (_: unknown, record: RowData) => {
        const item = record[mode.mode] as EvalCaseResult | undefined;
        if (!item) {
          return '—';
        }
        return (
          <Tooltip title={item.note}>
            <Space size={6}>
              <Tag color={item.correct ? 'success' : 'error'} bordered={false} style={{ marginInlineEnd: 0 }}>
                {item.correct ? '通过' : '未通过'}
              </Tag>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {item.negative ? (item.rank === 0 ? '未召回' : `误召回 ${item.rank}`) : item.rank > 0 ? `第 ${item.rank} 位` : '未命中'}
              </Typography.Text>
              {item.topVia && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {MODE_META[item.topVia === 'BOTH' ? 'HYBRID' : item.topVia === 'VECTOR' ? 'VECTOR' : 'KEYWORD']?.name ?? item.topVia}
                </Typography.Text>
              )}
            </Space>
          </Tooltip>
        );
      },
    }));

    return [...base, ...modeColumns];
  }, [report]);

  return (
    <div>
      <Alert
        style={{ marginBottom: 16 }}
        type="info"
        showIcon
        message="评估只执行检索、不调用大模型，因此不消耗 token 额度"
        description="同一批用例会分别用「混合 / 纯向量 / 纯关键词」三种策略跑一遍，用于验证融合权重是否真的带来了提升。"
      />

      <Card variant="borderless" style={{ marginBottom: 16 }}>
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space wrap>
            <Typography.Text>知识库</Typography.Text>
            <Select
              style={{ width: 260 }}
              value={kbId}
              placeholder="选择要评估的知识库"
              options={kbs.map((kb) => ({ label: `${kb.name}（${kb.docCount} 文档 / topK ${kb.topK}）`, value: kb.id }))}
              onChange={setKbId}
            />
            <Button type="primary" icon={<PlayCircleOutlined />} loading={loading} onClick={run}>
              运行评估
            </Button>
            <Tooltip title="重新加载知识库列表">
              <Button icon={<ReloadOutlined />} onClick={loadKbs} />
            </Tooltip>
          </Space>

          <div>
            <Typography.Text type="secondary" style={{ fontSize: 12.5 }}>
              {FORMAT_HELP}
            </Typography.Text>
            <Input.TextArea
              value={text}
              onChange={(e) => setText(e.target.value)}
              rows={5}
              style={{ marginTop: 6 }}
              placeholder={DEFAULT_CASES}
            />
          </div>
        </Space>
      </Card>

      {report && (
        <>
          <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
            {report.modes.map((mode) => (
              <Col key={mode.mode} xs={24} lg={8}>
                <Card variant="borderless" loading={loading}>
                  <Space direction="vertical" size={2}>
                    <Tag color={MODE_META[mode.mode]?.color} bordered={false} style={{ alignSelf: 'flex-start' }}>
                      {mode.modeName}
                    </Tag>
                    <Row gutter={12} style={{ marginTop: 8 }}>
                      <Col span={12}>
                        <Statistic
                          title="命中率"
                          value={mode.recall * 100}
                          precision={1}
                          suffix="%"
                          valueStyle={{ fontSize: 20, color: mode.recall >= 0.9 ? '#16a34a' : mode.recall >= 0.6 ? '#d97706' : '#dc2626' }}
                        />
                      </Col>
                      <Col span={12}>
                        <Tooltip title="平均倒数排名：命中片段排得越靠前得分越高">
                          <Statistic title="MRR" value={mode.mrr} precision={3} valueStyle={{ fontSize: 20 }} />
                        </Tooltip>
                      </Col>
                    </Row>
                    <Row gutter={12} style={{ marginTop: 8 }}>
                      <Col span={12}>
                        <Tooltip title="负样本被正确拒绝的比例，越低说明误召回越严重">
                          <Statistic
                            title="负样本拒绝率"
                            value={mode.negativePassRate * 100}
                            precision={1}
                            suffix="%"
                            valueStyle={{ fontSize: 20 }}
                          />
                        </Tooltip>
                      </Col>
                      <Col span={12}>
                        <Tooltip title="正样本命中 + 负样本正确拒绝 的综合比例">
                          <Statistic title="综合准确率" value={mode.accuracy * 100} precision={1} suffix="%" valueStyle={{ fontSize: 20 }} />
                        </Tooltip>
                      </Col>
                    </Row>
                  </Space>
                </Card>
              </Col>
            ))}
          </Row>

          <Card variant="borderless" title={`逐条用例结果（topK=${report.topK}）`}>
            <Table
              rowKey="question"
              size="middle"
              columns={columns}
              dataSource={rows}
              pagination={false}
              scroll={{ x: 720 }}
              locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无结果" /> }}
            />
          </Card>
        </>
      )}
    </div>
  );
}
