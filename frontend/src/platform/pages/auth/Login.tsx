import React, { useState } from 'react';
import { Form, Input, Button, Card, Typography, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { usePlatformUserStore } from '@/store/platformUserStore';
import { platformLogin, getPlatformUserInfo, getPlatformPermissions } from '../../api/auth';

const { Title, Text } = Typography;

const PlatformLogin: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { setToken, setUserInfo, setPermissions } = usePlatformUserStore();

  const handleLogin = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const result = await platformLogin(values);
      const { accessToken, refreshToken } = result;
      setToken(accessToken, refreshToken);

      // 获取用户信息和权限
      const [userInfo, permissions] = await Promise.all([
        getPlatformUserInfo(),
        getPlatformPermissions(),
      ]);
      setUserInfo(userInfo);
      setPermissions(permissions || []);

      message.success('登录成功');
      navigate('/dashboard');
    } catch (err: any) {
      // error already shown by request interceptor
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #0F172A 0%, #1E293B 100%)',
    }}>
      <Card
        style={{ width: 420, borderRadius: 12, boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }}
        styles={{ body: { padding: '40px 40px 32px' } }}
      >
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{
            width: 56,
            height: 56,
            borderRadius: 12,
            background: 'linear-gradient(135deg, #3B82F6, #6366F1)',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: 16,
          }}>
            <UserOutlined style={{ fontSize: 24, color: '#fff' }} />
          </div>
          <Title level={3} style={{ margin: 0, marginBottom: 4 }}>平台管理中心</Title>
          <Text type="secondary">使用管理员账号登录</Text>
        </div>

        <Form layout="vertical" onFinish={handleLogin} autoComplete="off">
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input
              prefix={<UserOutlined style={{ color: '#bfbfbf' }} />}
              placeholder="用户名"
              size="large"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: '#bfbfbf' }} />}
              placeholder="密码"
              size="large"
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <Button
              type="primary"
              htmlType="submit"
              size="large"
              loading={loading}
              block
              style={{ height: 44, borderRadius: 8, fontSize: 15 }}
            >
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default PlatformLogin;
