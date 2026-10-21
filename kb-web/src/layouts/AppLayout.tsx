/**
 * 主框架布局：侧边导航 + 顶栏 + 内容区。
 *
 * @author zhanglang
 */

import { useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  Avatar,
  Button,
  Dropdown,
  Layout,
  Menu,
  Space,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { MenuProps } from 'antd';
import {
  ApiOutlined,
  BulbOutlined,
  DatabaseOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SettingOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';

const { Sider, Header, Content } = Layout;

/**
 * 根据路径推导页面标题。
 * 用简单映射而非在每个页面里各自设标题，避免顶栏与页面内容两处维护。
 */
function resolveTitle(pathname: string): { title: string; subtitle: string } {
  if (pathname.startsWith('/admin')) {
    return { title: '后台管理', subtitle: '模型配置与调用统计' };
  }
  if (/^\/kb\/\d+\/chat$/.test(pathname)) {
    return { title: '智能问答', subtitle: '基于知识库的检索增强问答' };
  }
  if (/^\/kb\/\d+$/.test(pathname)) {
    return { title: '文档管理', subtitle: '上传、解析与向量化' };
  }
  return { title: '知识库', subtitle: '管理你的专属知识资产' };
}

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();
  const { mode, toggle } = useTheme();

  const isAdmin = user?.role === 'ADMIN';

  const menuItems = useMemo<MenuProps['items']>(() => {
    const items: MenuProps['items'] = [
      { key: '/kb', icon: <DatabaseOutlined />, label: '知识库' },
    ];
    if (isAdmin) {
      items.push({ key: '/admin', icon: <SettingOutlined />, label: '后台管理' });
    }
    return items;
  }, [isAdmin]);

  const selectedKey = location.pathname.startsWith('/admin') ? '/admin' : '/kb';
  const { title, subtitle } = resolveTitle(location.pathname);

  // 对话页需要占满整个内容区且内部自己滚动，因此去掉外层 padding
  const flush = location.pathname.endsWith('/chat');

  const userMenu: MenuProps['items'] = [
    {
      key: 'info',
      label: (
        <div style={{ minWidth: 168 }}>
          <div style={{ fontWeight: 600 }}>{user?.nickname || user?.username}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {user?.username} · {isAdmin ? '管理员' : '普通用户'}
          </Typography.Text>
        </div>
      ),
      disabled: true,
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: () => {
        logout();
        navigate('/login', { replace: true });
      },
    },
  ];

  return (
    <Layout style={{ height: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        trigger={null}
        width={220}
        theme={mode === 'dark' ? 'dark' : 'light'}
        style={{ borderRight: '1px solid var(--panel-border)' }}
      >
        <div className="app-sider-logo">
          <span className="logo-mark">
            <ApiOutlined />
          </span>
          {!collapsed && <span>AI 知识库</span>}
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          theme={mode === 'dark' ? 'dark' : 'light'}
          onClick={({ key }) => navigate(key)}
          style={{ borderInlineEnd: 'none', paddingTop: 4 }}
        />
      </Sider>

      <Layout>
        <Header className="app-header" style={{ height: 64, lineHeight: '64px', padding: '0 20px' }}>
          <Space size={12} align="center">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed((v) => !v)}
            />
            <div>
              <div className="page-title" style={{ lineHeight: 1.3 }}>
                {title}
              </div>
              <div className="page-subtitle" style={{ lineHeight: 1.3, marginTop: 0 }}>
                {subtitle}
              </div>
            </div>
          </Space>

          <Space size={8} align="center">
            <Tooltip title={mode === 'dark' ? '切换到浅色' : '切换到深色'}>
              <Button type="text" icon={<BulbOutlined />} onClick={toggle} />
            </Tooltip>
            <Dropdown menu={{ items: userMenu }} placement="bottomRight" trigger={['click']}>
              <Space size={8} style={{ cursor: 'pointer', padding: '0 4px' }}>
                <Avatar size={30} style={{ background: '#4f46e5' }} icon={<UserOutlined />} />
                <span style={{ fontSize: 13.5, lineHeight: 1 }}>
                  {user?.nickname || user?.username}
                  {isAdmin && (
                    <Tag color="purple" style={{ marginLeft: 8, transform: 'scale(0.9)' }}>
                      ADMIN
                    </Tag>
                  )}
                </span>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content className={flush ? 'app-content app-content--flush' : 'app-content'}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
