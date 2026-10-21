/**
 * 通用格式化工具。
 *
 * @author zhanglang
 */

import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

/**
 * 字节数转可读文本。
 *
 * @param bytes 字节数
 * @returns 如 "1.2 MB"
 */
export function formatBytes(bytes?: number): string {
  if (!bytes || bytes <= 0) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** index;
  return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

/**
 * 时间格式化为 yyyy-MM-dd HH:mm。
 *
 * @param value ISO 时间字符串
 */
export function formatDateTime(value?: string): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}

/**
 * 相对时间，如 "3 分钟前"。
 *
 * @param value ISO 时间字符串
 */
export function formatFromNow(value?: string): string {
  return value ? dayjs(value).fromNow() : '-';
}

/**
 * 数字千分位。
 *
 * @param value 数值
 */
export function formatNumber(value?: number): string {
  if (value === undefined || value === null) {
    return '0';
  }
  return value.toLocaleString('zh-CN');
}

/**
 * 金额，保留 4 位小数（大模型单价极低，2 位小数会全部显示为 0.00）。
 *
 * @param value 金额
 */
export function formatMoney(value?: number): string {
  return `¥${(value ?? 0).toFixed(4)}`;
}

/**
 * 毫秒转可读耗时。
 *
 * @param ms 毫秒
 */
export function formatLatency(ms?: number): string {
  if (!ms || ms <= 0) {
    return '-';
  }
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
}
