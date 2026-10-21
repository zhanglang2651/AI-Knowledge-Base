/**
 * 应用根组件：主题、国际化、路由与登录态的装配。
 *
 * @author zhanglang
 */

import { useEffect } from 'react';
import { App as AntdApp, ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { Navigate, Route, Routes } from 'react-router-dom';
import { ThemeProvider, useTheme } from './context/ThemeContext';
import { AuthProvider } from './context/AuthContext';
import { bindNotifier } from './utils/feedback';
import RequireAuth from './components/RequireAuth';
import AppLayout from './layouts/AppLayout';
import LoginPage from './pages/LoginPage';
import KnowledgeBasePage from './pages/KnowledgeBasePage';
import KbDetailPage from './pages/KbDetailPage';
import ChatPage from './pages/ChatPage';
import AdminPage from './pages/AdminPage';

/**
 * 把 antd 的 message 实例注入到 feedback 桥接层，供非组件代码调用。
 */
function FeedbackBridge() {
  const { message } = AntdApp.useApp();
  useEffect(() => {
    bindNotifier(message);
  }, [message]);
  return null;
}

/**
 * 主题装配层。
 * <p>
 * 必须放在 ThemeProvider 内部才能读到当前模式；ConfigProvider 的 algorithm
 * 与 html[data-theme] 同步切换，保证 antd 组件与自定义区域配色一致。
 */
function ThemedApp() {
  const { mode } = useTheme();

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: mode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          colorPrimary: '#4f46e5',
          borderRadius: 8,
          fontSize: 14,
          colorBgLayout: mode === 'dark' ? '#0f1115' : '#f7f8fb',
        },
        components: {
          Layout: {
            headerBg: 'transparent',
            siderBg: mode === 'dark' ? '#141821' : '#ffffff',
            bodyBg: mode === 'dark' ? '#0f1115' : '#f7f8fb',
          },
          Card: { borderRadiusLG: 12 },
        },
      }}
    >
      <AntdApp>
        <FeedbackBridge />
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <RequireAuth>
                  <AppLayout />
                </RequireAuth>
              }
            >
              <Route path="/" element={<Navigate to="/kb" replace />} />
              <Route path="/kb" element={<KnowledgeBasePage />} />
              <Route path="/kb/:kbId" element={<KbDetailPage />} />
              <Route path="/kb/:kbId/chat" element={<ChatPage />} />
              <Route path="/admin" element={<AdminPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/kb" replace />} />
          </Routes>
        </AuthProvider>
      </AntdApp>
    </ConfigProvider>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <ThemedApp />
    </ThemeProvider>
  );
}
