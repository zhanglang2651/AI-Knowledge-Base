/**
 * 登录态上下文。
 * <p>
 * 设计要点：
 * <ul>
 *     <li>用户信息以 {@code /auth/me} 的返回为准，localStorage 只存令牌；
 *         存用户快照会带来「令牌已过期但界面仍显示已登录」的错觉</li>
 *     <li>监听 401 事件统一登出，请求层不必关心路由跳转</li>
 *     <li>refresh 失败必须清空用户态，否则会反复重试形成死循环</li>
 * </ul>
 *
 * @author zhanglang
 */

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { authApi } from '../api/auth';
import { clearToken, getToken, setToken, UNAUTHORIZED_EVENT } from '../api/client';
import type { LoginRequest, RegisterRequest, UserVO } from '../types';

interface AuthContextValue {
  /** 当前用户；null 表示未登录 */
  user: UserVO | null;
  /** 首次恢复登录态期间为 true，用于避免路由守卫误判为未登录 */
  loading: boolean;
  login: (values: LoginRequest) => Promise<UserVO>;
  register: (values: RegisterRequest) => Promise<number>;
  logout: () => void;
  /** 重新拉取用户信息（如角色变更后） */
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserVO | null>(null);
  const [loading, setLoading] = useState(true);

  // 用 ref 标记组件是否已卸载，避免异步回调在卸载后 setState 触发告警
  const mounted = useRef(true);

  const refresh = useCallback(async () => {
    if (!getToken()) {
      setUser(null);
      return;
    }
    try {
      const me = await authApi.me();
      if (mounted.current) {
        setUser(me);
      }
    } catch {
      // 令牌过期或后端不可达：清空本地态，让路由守卫把用户带回登录页
      clearToken();
      if (mounted.current) {
        setUser(null);
      }
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    refresh().finally(() => {
      if (mounted.current) {
        setLoading(false);
      }
    });
    return () => {
      mounted.current = false;
    };
  }, [refresh]);

  // 任意请求返回 401 时统一登出
  useEffect(() => {
    const handle = () => setUser(null);
    window.addEventListener(UNAUTHORIZED_EVENT, handle);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handle);
  }, []);

  const login = useCallback(async (values: LoginRequest) => {
    const resp = await authApi.login(values);
    // 先落盘令牌，再查用户信息：/auth/me 依赖该令牌
    setToken(resp.accessToken);
    const me = await authApi.me();
    setUser(me);
    return me;
  }, []);

  const register = useCallback(async (values: RegisterRequest) => authApi.register(values), []);

  const logout = useCallback(() => {
    clearToken();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, register, logout, refresh }),
    [user, loading, login, register, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * 获取登录态上下文。
 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth 必须在 AuthProvider 内部使用');
  }
  return ctx;
}
