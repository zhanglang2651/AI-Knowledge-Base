/**
 * 引用来源列表。
 * <p>
 * 这是「可溯源」在界面上的最终呈现：每条引用都能看到来自哪份文档、第几块、
 * 匹配度多少，并可展开查看原文片段 —— 用户据此判断答案是否可信，
 * 而不是只能无条件相信模型。
 *
 * @author zhanglang
 */

import { Collapse, Progress, Space, Tag, Typography } from 'antd';
import { FileTextOutlined } from '@ant-design/icons';
import type { SourceVO } from '../types';

interface Props {
  sources: SourceVO[];
}

/**
 * 命中来源的展示文案与配色。
 * VECTOR：语义相近，可能同义不同词；KEYWORD：字面精确命中（型号、错误码）；
 * BOTH：两路都命中，可信度最高。
 */
const VIA_META: Record<string, { label: string; color: string }> = {
  VECTOR: { label: '语义', color: 'blue' },
  KEYWORD: { label: '关键词', color: 'orange' },
  BOTH: { label: '双路命中', color: 'green' },
};

/**
 * 按相似度给出颜色：越高越可信。
 * 阈值参考：> 0.75 强相关，0.55-0.75 相关，< 0.55 仅供参考。
 */
function scoreColor(score: number): string {
  if (score >= 0.75) {
    return '#16a34a';
  }
  if (score >= 0.55) {
    return '#2563eb';
  }
  return '#d97706';
}

export default function SourceList({ sources }: Props) {
  if (!sources || sources.length === 0) {
    return null;
  }

  return (
    <div style={{ marginTop: 8 }}>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        引用来源 · {sources.length} 条
      </Typography.Text>
      <Collapse
        size="small"
        ghost
        style={{ marginTop: 4 }}
        items={sources.map((source, index) => ({
          key: `${source.docId}-${source.chunkNo ?? index}`,
          label: (
            <div className="source-head">
              <Tag color="blue" bordered={false} style={{ marginInlineEnd: 0 }}>
                [{index + 1}]
              </Tag>
              <FileTextOutlined style={{ opacity: 0.55 }} />
              <Typography.Text style={{ fontSize: 13 }} ellipsis={{ tooltip: source.docName }}>
                {source.docName}
              </Typography.Text>
              {source.pageNo ? (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  第 {source.pageNo} 页
                </Typography.Text>
              ) : null}
              {source.chunkNo ? (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  第 {source.chunkNo} 块
                </Typography.Text>
              ) : null}
              {source.via && VIA_META[source.via] && (
                <Tag color={VIA_META[source.via].color} bordered={false} style={{ marginInlineEnd: 0 }}>
                  {VIA_META[source.via].label}
                </Tag>
              )}
              <Space size={4} style={{ marginLeft: 'auto' }}>
                <Progress
                  type="circle"
                  size={18}
                  percent={Math.round((source.score ?? 0) * 100)}
                  strokeColor={scoreColor(source.score ?? 0)}
                  showInfo={false}
                />
                <Typography.Text style={{ fontSize: 12, color: scoreColor(source.score ?? 0) }}>
                  {(source.score ?? 0).toFixed(4)}
                </Typography.Text>
              </Space>
            </div>
          ),
          children: <p className="source-snippet">{source.content}</p>,
        }))}
      />
    </div>
  );
}
