import React, { useState, useMemo } from 'react';
import { Layout, Menu, theme, Dropdown, Avatar } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  UserOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  DashboardOutlined,
  TeamOutlined,
  SafetyOutlined,
  ApartmentOutlined,
  KeyOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { usePlatformUserStore } from '@/store/platformUserStore';
import type { MenuProps } from 'antd';

const { Header, Sider, Content } = Layout;

interface PlatformMenuItem {
  key: string;
  icon: React.ReactNode;
  label: string;
  permission?: string; // undefined = 所有人可见（如控制台）
}

const RAW_PLATFORM_MENUS: PlatformMenuItem[] = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: '控制台' },
  { key: '/tenants',   icon: <ApartmentOutlined />, label: '租户管理', permission: 'platform:tenant:list' },
  { key: '/users',     icon: <TeamOutlined />,      label: '平台用户', permission: 'platform:user:list' },
  { key: '/roles',     icon: <SafetyOutlined />,    label: '平台角色', permission: 'platform:role:list' },
  { key: '/permissions', icon: <KeyOutlined />,     label: '权限节点', permission: 'platform:menu:list' },
  { key: '/logs',      icon: <FileTextOutlined />,  label: '操作日志', permission: 'platform:log:list' },
];

const PlatformLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const {
    token: { colorBgContainer },
  } = theme.useToken();
  const navigate = useNavigate();
  const location = useLocation();
  const { userInfo, permissions, isSuper, logout } = usePlatformUserStore();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const userMenu: MenuProps['items'] = [
    {
      key: 'logout',
      label: '退出登录',
      icon: <LogoutOutlined />,
      onClick: handleLogout,
    },
  ];

  const menuItemsWithClick = useMemo<MenuProps['items']>(() =>
    RAW_PLATFORM_MENUS
      .filter(item => !item.permission || isSuper || permissions.includes(item.permission))
      .map(item => ({
        key: item.key,
        icon: item.icon,
        label: item.label,
        onClick: () => navigate(item.key),
      })),
    [navigate, permissions, isSuper]
  );

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden', display: 'flex', width: '100%' }}>
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={collapsed ? 80 : 200}
        style={{ overflowY: 'auto', background: '#0F172A' }}
      >
        <div style={{
          height: 32,
          margin: 16,
          background: 'rgba(255, 255, 255, 0.2)',
          borderRadius: 4,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}>
          {!collapsed && (
            <span style={{ color: '#fff', fontSize: 12, fontWeight: 600 }}>平台管理中心</span>
          )}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItemsWithClick}
        />
      </Sider>
      <Layout style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
        <Header style={{
          padding: '0',
          background: colorBgContainer,
          display: 'flex',
          alignItems: 'center',
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
          minHeight: '64px',
          flexShrink: 0,
          zIndex: 1,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', padding: '0 16px' }}>
            {React.createElement(collapsed ? MenuUnfoldOutlined : MenuFoldOutlined, {
              onClick: () => setCollapsed(!collapsed),
              style: { fontSize: '18px', cursor: 'pointer', padding: '8px', borderRadius: '4px' },
            } as any)}
          </div>
          <div style={{ flex: 1 }} />
          <div style={{ display: 'flex', alignItems: 'center', padding: '0 16px' }}>
            <Dropdown menu={{ items: userMenu }} trigger={['click']}>
              <div style={{
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '8px 12px',
                borderRadius: '6px',
              }}>
                <Avatar icon={<UserOutlined />} size="default" />
                <span style={{ fontSize: '14px', fontWeight: 500 }}>
                  {userInfo?.nickname || userInfo?.username || 'Admin'}
                </span>
              </div>
            </Dropdown>
          </div>
        </Header>
        <Content style={{ padding: 24, overflowY: 'auto', flex: 1, background: colorBgContainer }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default PlatformLayout;
