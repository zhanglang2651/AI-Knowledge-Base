/**
 * 文档解析状态标签。
 * <p>
 * 状态机与后端一致：PENDING → PARSING → EMBEDDING → COMPLETED / FAILED。
 * 集中在一处映射，避免表格、详情、轮询提示三处各写一套文案导致不一致。
 *
 * @author zhanglang
 */

import { LoadingOutlined } from '@ant-design/icons';
import { Tag, Tooltip } from 'antd';
import type { ParseStatus } from '../types';

interface StatusMeta {
  color: string;
  text: string;
  /** 是否为进行中状态，用于决定是否继续轮询 */
  pending: boolean;
}

const STATUS_META: Record<ParseStatus, StatusMeta> = {
  PENDING: { color: 'default', text: '排队中', pending: true },
  PARSING: { color: 'processing', text: '解析中', pending: true },
  EMBEDDING: { color: 'processing', text: '向量化', pending: true },
  COMPLETED: { color: 'success', text: '已完成', pending: false },
  FAILED: { color: 'error', text: '失败', pending: false },
};

/**
 * 判断某文档是否处于处理中（决定是否需要继续轮询）。
 *
 * @param status 解析状态
 */
export function isPending(status: ParseStatus): boolean {
  return STATUS_META[status]?.pending ?? false;
}

interface Props {
  status: ParseStatus;
  /** 失败原因，悬浮展示 */
  errorMsg?: string;
}

export default function StatusTag({ status, errorMsg }: Props) {
  const meta = STATUS_META[status] ?? { color: 'default', text: status, pending: false };

  const tag = (
    <Tag
      color={meta.color}
      bordered={false}
      icon={meta.pending ? <LoadingOutlined /> : undefined}
      style={{ marginInlineEnd: 0 }}
    >
      {meta.text}
    </Tag>
  );

  if (status === 'FAILED' && errorMsg) {
    return <Tooltip title={errorMsg}>{tag}</Tooltip>;
  }
  return tag;
}
