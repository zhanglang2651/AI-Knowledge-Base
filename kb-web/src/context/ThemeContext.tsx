/**
 * 主题上下文。
 * <p>
 * 只保存 light / dark 两种模式并落到 localStorage。
 * 之所以不引第三方状态库：主题是纯 UI 状态，跨组件共享一个值即可，
 * 引入额外依赖只会增加体积和维护成本。
 *
 * @author zhanglang
 */

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'kb_theme';

interface ThemeContextValue {
  mode: ThemeMode;
  toggle: () => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

/**
 * 读取初始主题：优先本地记录，其次跟随系统偏好。
 */
function readInitialMode(): ThemeMode {
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved === 'light' || saved === 'dark') {
    return saved;
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>(readInitialMode);

  // 同步到 html[data-theme]，供 global.css 中的 CSS 变量切换使用
  // （antd 组件由 ConfigProvider 的 algorithm 控制，两者必须同时切换，否则会出现
  //  antd 已经是深色而自定义区域还是浅色的撕裂现象）
  useEffect(() => {
    document.documentElement.dataset.theme = mode;
    localStorage.setItem(STORAGE_KEY, mode);
  }, [mode]);

  const toggle = useCallback(() => {
    setMode((prev) => (prev === 'light' ? 'dark' : 'light'));
  }, []);

  const value = useMemo(() => ({ mode, toggle }), [mode, toggle]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

/**
 * 获取主题上下文。
 *
 * @returns 当前模式与切换函数
 */
export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme 必须在 ThemeProvider 内部使用');
  }
  return ctx;
}
