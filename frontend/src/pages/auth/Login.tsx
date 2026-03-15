import React, { useState } from 'react';
import { Form, Input, Card, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useUserStore } from '@/store/userStore';
import { login, getUserInfo, getMenus } from '@/api/auth';
import { PrimaryButton } from '@/design-system/components/Buttons';

const Login: React.FC = () => {
  const navigate = useNavigate();
  const { setToken, setUserInfo, setMenus } = useUserStore();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: any) => {
    setLoading(true);
    try {
      const { accessToken, refreshToken } = await login(values);
      setToken(accessToken, refreshToken);
      
      const userInfo = await getUserInfo();
      setUserInfo(userInfo);

      const menus = await getMenus();
      // Ensure menus is an array
      const safeMenus = Array.isArray(menus) ? menus : [];
      setMenus(safeMenus);
      
      // Extract permissions from menus for permission control
      // This is a temporary solution, in a real app you'd get permissions from a separate API
      const extractPermissions = (menuItems: any[]): string[] => {
        if (!Array.isArray(menuItems)) return [];
        
        const perms: string[] = [];
        const traverse = (items: any[]) => {
          if (!Array.isArray(items)) return;
          
          items.forEach(item => {
            if (item.permissionKey) {
              perms.push(item.permissionKey);
            }
            if (item.children && Array.isArray(item.children)) {
              traverse(item.children);
            }
          });
        };
        traverse(menuItems);
        return perms;
      };
      
      const permissions = extractPermissions(safeMenus);
      useUserStore.getState().setPermissions(permissions);
      
      message.success('登录成功');
      navigate('/dashboard');
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card
      style={{
        width: 400,
        boxShadow: '0 20px 45px rgba(15, 23, 42, 0.18)',
        borderRadius: 16,
        backdropFilter: 'blur(18px)',
        background: 'rgba(248, 250, 252, 0.92)',
        border: '1px solid rgba(148, 163, 184, 0.35)',
      }}
    >
      <div style={{ textAlign: 'center', marginBottom: 24 }}>
        <h2 style={{ margin: 0 }}>系统登录</h2>
      </div>
      <Form
        name="login"
        initialValues={{ remember: true }}
        onFinish={onFinish}
        size="large"
      >
        <Form.Item
          name="username"
          rules={[{ required: true, message: '请输入用户名!' }]}
        >
          <Input prefix={<UserOutlined />} placeholder="用户名" />
        </Form.Item>
        <Form.Item
          name="password"
          rules={[{ required: true, message: '请输入密码!' }]}
        >
          <Input.Password prefix={<LockOutlined />} placeholder="密码" />
        </Form.Item>

        <Form.Item>
          <PrimaryButton htmlType="submit" block loading={loading}>
            登录
          </PrimaryButton>
        </Form.Item>
      </Form>
    </Card>
  );
};

export default Login;
