/**
 * 认证相关接口。
 *
 * @author zhanglang
 */

import { request } from './client';
import type { LoginRequest, LoginResponse, RegisterRequest, UserVO } from '../types';

export const authApi = {
  /**
   * 登录，成功后返回令牌与用户信息。
   */
  login: (data: LoginRequest) => request<LoginResponse>('/api/v1/auth/login', { method: 'POST', body: data }),

  /**
   * 注册，返回新用户 ID。
   */
  register: (data: RegisterRequest) => request<number>('/api/v1/auth/register', { method: 'POST', body: data }),

  /**
   * 查询当前登录用户。
   * 页面刷新后用它恢复登录态，而不是直接信任 localStorage 里的用户快照
   * （快照可能是被改过的，也必须校验令牌是否仍然有效）。
   */
  me: () => request<UserVO>('/api/v1/auth/me'),
};
