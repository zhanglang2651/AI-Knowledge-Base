/**
 * 通知能力的桥接。
 * <p>
 * antd 5 的静态方法（message.xxx）拿不到 ConfigProvider 的主题上下文，
 * 控制台会给出「Static function can not consume context」警告。
 * 这里在 App 内部用 App.useApp() 取到实例后注入，业务代码统一走 toast，
 * 既保留了非组件环境（如请求封装）可调用的便利，也不会出现主题不一致。
 *
 * @author zhanglang
 */

interface Notifier {
  success: (content: string) => void;
  error: (content: string) => void;
  warning: (content: string) => void;
  info: (content: string) => void;
}

/** 未注入前的兜底实现，保证在极早期调用也不会抛空指针 */
let notifier: Notifier = {
  success: (c) => console.info(c),
  error: (c) => console.error(c),
  warning: (c) => console.warn(c),
  info: (c) => console.info(c),
};

/**
 * 注入 antd 的 message 实例。
 *
 * @param instance App.useApp() 返回的 message 对象
 */
export function bindNotifier(instance: Notifier): void {
  notifier = instance;
}

export const toast: Notifier = {
  success: (content) => notifier.success(content),
  error: (content) => notifier.error(content),
  warning: (content) => notifier.warning(content),
  info: (content) => notifier.info(content),
};
