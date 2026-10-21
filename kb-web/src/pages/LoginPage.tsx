/**
 * 登录 / 注册页。
 *
 * @author zhanglang
 */

import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Divider,
  Form,
  Input,
  Segmented,
  Space,
  Typography,
} from 'antd';
import {
  ApiOutlined,
  BranchesOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useAuth } from '../context/AuthContext';
import { toast } from '../utils/feedback';
import { ApiError } from '../api/client';

type Mode = 'login' | 'register';

interface FormValues {
  username: string;
  password: string;
  confirm?: string;
  nickname?: string;
  email?: string;
}

export default function LoginPage() {
  const [mode, setMode] = useState<Mode>('login');
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<FormValues>();
  const { login, register, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const from = (location.state as { from?: string } | null)?.from || '/kb';

  // 已登录用户直接放行，避免手动访问 /login 时出现"登录页套已登录状态"的怪异体验
  if (user) {
    return <Navigate to={from} replace />;
  }

  const handleSubmit = async (values: FormValues) => {
    setSubmitting(true);
    try {
      if (mode === 'register') {
        await register({
          username: values.username.trim(),
          password: values.password,
          nickname: values.nickname?.trim() || undefined,
          email: values.email?.trim() || undefined,
        });
        // 注册接口只返回用户 ID，紧接着自动登录，省去用户再输一次密码
        const me = await login({ username: values.username.trim(), password: values.password });
        toast.success(`注册成功，欢迎你，${me.nickname || me.username}`);
      } else {
        const me = await login({ username: values.username.trim(), password: values.password });
        toast.success(`欢迎回来，${me.nickname || me.username}`);
      }
      navigate(from, { replace: true });
    } catch (error) {
      const message = error instanceof ApiError ? error.message : '操作失败，请稍后重试';
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  const switchMode = (next: Mode) => {
    setMode(next);
    // 切换时清空校验态，否则注册页残留的必填红框会带到登录页
    form.resetFields();
  };

  return (
    <div className="login-page">
      <aside className="login-brand">
        <div className="login-brand-inner">
          <Space size={10} align="center">
            <span className="logo-mark" style={{ width: 36, height: 36, borderRadius: 10, fontSize: 18 }}>
              <ApiOutlined />
            </span>
            <span style={{ fontSize: 15, letterSpacing: 1.5, opacity: 0.85 }}>AI KNOWLEDGE BASE</span>
          </Space>

          <h1>
            让每一份文档
            <br />
            都能被准确回答
          </h1>
          <p className="lead">
            上传文档即自动完成解析、切分与向量化。提问时先检索再生成，
            答案附带原文引用与相似度，可逐条溯源。
          </p>

          <div className="login-points">
            <div className="login-point">
              <span className="dot">
                <BranchesOutlined />
              </span>
              <div>
                <strong>检索增强生成（RAG）</strong>
                <span className="desc">向量召回 + 引用溯源，答案来自你的文档，而不是模型的记忆</span>
              </div>
            </div>
            <div className="login-point">
              <span className="dot">
                <ThunderboltOutlined />
              </span>
              <div>
                <strong>流式输出</strong>
                <span className="desc">SSE 逐字推送，引用先于正文到达，长回答也不必干等</span>
              </div>
            </div>
            <div className="login-point">
              <span className="dot">
                <SafetyCertificateOutlined />
              </span>
              <div>
                <strong>多知识库隔离</strong>
                <span className="desc">每个库独立配置切分粒度、召回条数与相似度阈值</span>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <main className="login-panel">
        <Card className="login-card" variant="borderless">
          <Space direction="vertical" size={2} style={{ marginBottom: 20 }}>
            <Typography.Title level={4} style={{ margin: 0 }}>
              {mode === 'login' ? '登录账号' : '创建账号'}
            </Typography.Title>
            <Typography.Text type="secondary" style={{ fontSize: 13 }}>
              {mode === 'login' ? '使用你的账号继续访问知识库' : '首个注册的账号将自动获得管理员权限'}
            </Typography.Text>
          </Space>

          <Segmented
            block
            value={mode}
            onChange={(value) => switchMode(value as Mode)}
            options={[
              { label: '登录', value: 'login' },
              { label: '注册', value: 'register' },
            ]}
            style={{ marginBottom: 20 }}
          />

          <Form<FormValues> form={form} layout="vertical" requiredMark={false} onFinish={handleSubmit}>
            <Form.Item
              name="username"
              label="用户名"
              rules={[
                { required: true, message: '请输入用户名' },
                { min: 3, max: 32, message: '用户名长度为 3-32 位' },
                {
                  pattern: /^[a-zA-Z0-9_-]+$/,
                  message: '只能包含字母、数字、下划线和短横线',
                },
              ]}
            >
              <Input size="large" prefix={<UserOutlined />} placeholder="请输入用户名" autoComplete="username" />
            </Form.Item>

            {mode === 'register' && (
              <Form.Item name="nickname" label="昵称（选填）" rules={[{ max: 32, message: '昵称不超过 32 位' }]}>
                <Input size="large" placeholder="展示用的名字" />
              </Form.Item>
            )}

            <Form.Item
              name="password"
              label="密码"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, max: 64, message: '密码长度为 6-64 位' },
              ]}
            >
              <Input.Password
                size="large"
                prefix={<LockOutlined />}
                placeholder="请输入密码"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                onPressEnter={() => form.submit()}
              />
            </Form.Item>

            {mode === 'register' && (
              <Form.Item
                name="confirm"
                label="确认密码"
                dependencies={['password']}
                rules={[
                  { required: true, message: '请再次输入密码' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('password') === value) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error('两次输入的密码不一致'));
                    },
                  }),
                ]}
              >
                <Input.Password size="large" prefix={<LockOutlined />} placeholder="再次输入密码" />
              </Form.Item>
            )}

            <Button type="primary" size="large" block htmlType="submit" loading={submitting} style={{ marginTop: 4 }}>
              {mode === 'login' ? '登录' : '注册并登录'}
            </Button>
          </Form>

          {mode === 'register' && (
            <Alert
              style={{ marginTop: 16 }}
              type="info"
              showIcon
              message="密码使用 BCrypt 加盐哈希存储，服务端不保存明文"
            />
          )}

          <Divider style={{ margin: '20px 0 12px' }} />
          <div className="login-switch">
            <span>AI 知识库问答平台 v1.0</span>
            <span>RAG · pgvector · Spring AI</span>
          </div>
        </Card>
      </main>
    </div>
  );
}
