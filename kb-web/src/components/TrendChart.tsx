/**
 * 调用趋势图。
 * <p>
 * 手写 SVG 而不引图表库的原因：
 * <ul>
 *     <li>只需柱状图一种形态，引入 echarts / recharts 会让包体积增加数百 KB</li>
 *     <li>SVG 用 CSS 变量取色，天然跟随明暗主题，无需在 JS 里同步配色</li>
 *     <li>零依赖也意味着零版本风险，不会因为图表库大版本升级而返工</li>
 * </ul>
 *
 * @author zhanglang
 */

import { Empty, Typography } from 'antd';
import type { StatTrendItemVO } from '../types';

export type MetricKey = 'callCount' | 'totalTokens' | 'totalCost';

interface Props {
  data: StatTrendItemVO[];
  metric: MetricKey;
}

/** 画布尺寸，配合 CSS 的 width:100% 自适应缩放 */
const WIDTH = 760;
const HEIGHT = 260;
const PADDING = { top: 18, right: 18, bottom: 34, left: 58 };

/** 指标的中文名与数值格式化 */
const METRIC_META: Record<MetricKey, { label: string; format: (value: number) => string; tooltip: (value: number) => string }> = {
  callCount: {
    label: '调用次数',
    format: (v) => (v >= 10000 ? `${(v / 10000).toFixed(1)}w` : String(Math.round(v))),
    tooltip: (v) => `${v} 次`,
  },
  totalTokens: {
    label: 'Token 消耗',
    format: (v) => (v >= 1000 ? `${(v / 1000).toFixed(1)}k` : String(Math.round(v))),
    tooltip: (v) => `${v.toLocaleString('zh-CN')} tokens`,
  },
  totalCost: {
    label: '费用',
    format: (v) => (v >= 1 ? v.toFixed(2) : v.toFixed(3)),
    tooltip: (v) => `¥${v.toFixed(4)}`,
  },
};

export default function TrendChart({ data, metric }: Props) {
  const meta = METRIC_META[metric];

  if (!data || data.length === 0) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="暂无调用记录，发起一次问答后即可看到趋势"
        style={{ padding: '32px 0' }}
      />
    );
  }

  const innerW = WIDTH - PADDING.left - PADDING.right;
  const innerH = HEIGHT - PADDING.top - PADDING.bottom;

  const values = data.map((item) => {
    const raw = metric === 'callCount' ? item.callCount : metric === 'totalTokens' ? item.totalTokens : item.totalCost;
    return Number(raw ?? 0);
  });
  const maxValue = Math.max(...values, 1);

  // Y 轴取 4 段，向上取整到"好看"的刻度，避免出现 137.5 这种刻度值
  const tickCount = 4;
  const rawStep = maxValue / tickCount;
  const magnitude = 10 ** Math.floor(Math.log10(rawStep || 1));
  const step = Math.ceil(rawStep / magnitude) * magnitude || 1;
  const axisMax = step * tickCount;

  const stepX = innerW / data.length;
  const barWidth = Math.min(38, Math.max(10, stepX * 0.5));

  /** 横轴标签过密时隔一个显示 */
  const labelEvery = data.length > 14 ? Math.ceil(data.length / 10) : 1;

  return (
    <div>
      <Typography.Text type="secondary" style={{ fontSize: 12.5 }}>
        {meta.label}（近 {data.length} 天）
      </Typography.Text>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        width="100%"
        height="auto"
        role="img"
        aria-label={`${meta.label}趋势图`}
        style={{ display: 'block', marginTop: 6 }}
      >
        {/* Y 轴网格与刻度 */}
        {Array.from({ length: tickCount + 1 }, (_, index) => {
          const value = step * index;
          const y = PADDING.top + innerH - (value / axisMax) * innerH;
          return (
            <g key={`grid-${index}`}>
              <line
                x1={PADDING.left}
                x2={WIDTH - PADDING.right}
                y1={y}
                y2={y}
                stroke="var(--chart-grid)"
                strokeWidth={1}
                strokeDasharray={index === 0 ? undefined : '3 4'}
              />
              <text
                x={PADDING.left - 10}
                y={y + 4}
                textAnchor="end"
                fontSize={11}
                fill="var(--chart-axis)"
              >
                {meta.format(value)}
              </text>
            </g>
          );
        })}

        {/* 柱体 */}
        {data.map((item, index) => {
          const value = values[index];
          const height = axisMax === 0 ? 0 : (value / axisMax) * innerH;
          const x = PADDING.left + stepX * index + (stepX - barWidth) / 2;
          const y = PADDING.top + innerH - height;
          const showLabel = index % labelEvery === 0;
          return (
            <g key={item.statDate}>
              <rect
                x={x}
                y={y}
                width={barWidth}
                height={Math.max(height, value > 0 ? 2 : 0)}
                rx={4}
                fill="var(--chart-bar)"
                opacity={0.88}
              >
                {/* 原生 tooltip：无需额外事件监听，鼠标悬停即显示精确值 */}
                <title>{`${item.statDate}  ${meta.tooltip(value)}`}</title>
              </rect>
              {showLabel && (
                <text
                  x={PADDING.left + stepX * index + stepX / 2}
                  y={HEIGHT - 12}
                  textAnchor="middle"
                  fontSize={11}
                  fill="var(--chart-axis)"
                >
                  {item.statDate?.slice(5)}
                </text>
              )}
            </g>
          );
        })}
      </svg>
    </div>
  );
}
