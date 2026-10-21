/**
 * 路由守卫。
 * <p>
 * 关键点：首次加载时不能因为 user 还是 null 就直接跳登录页，
 * 否则刷新页面会先闪一下登录页再跳回来。因此 loading 期间渲染骨架屏。
 *
 * @author zhanglang
 */

import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Flex, Spin, Typography } from 'antd';
import { useAuth } from '../context/AuthContext';

interface Props {
  children: ReactNode;
}

export default function RequireAuth({ children }: Props) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <Flex align="center" justify="center" style={{ height: '100vh', flexDirection: 'column', gap: 16 }}>
        <Spin size="large" />
        <Typography.Text type="secondary">正在恢复登录状态…</Typography.Text>
      </Flex>
    );
  }

  if (!user) {
    // 记录来源路径，登录成功后原路返回（含查询串，便于分享会话链接）
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }

  return <>{children}</>;
}
