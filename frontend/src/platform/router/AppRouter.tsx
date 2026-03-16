import React from 'react';
import { BrowserRouter, useRoutes, Navigate } from 'react-router-dom';
import { PlatformGuard } from '../guards/PlatformGuard';
import PlatformLayout from '../layouts/PlatformLayout';
import PlatformLogin from '../pages/auth/Login';
import PlatformDashboard from '../pages/dashboard';

const Routes: React.FC = () => {
  const routes = useRoutes([
    {
      path: '/login',
      element: <PlatformLogin />,
    },
    {
      path: '/',
      element: (
        <PlatformGuard>
          <PlatformLayout />
        </PlatformGuard>
      ),
      children: [
        { path: '', element: <Navigate to="/dashboard" replace /> },
        { path: 'dashboard', element: <PlatformDashboard /> },
        // 占位页面（后续按需添加完整实现）
        { path: 'tenants', element: <PlaceholderPage title="租户管理" /> },
        { path: 'users', element: <PlaceholderPage title="平台用户" /> },
        { path: 'roles', element: <PlaceholderPage title="平台角色" /> },
        { path: 'permissions', element: <PlaceholderPage title="权限节点" /> },
        { path: 'logs', element: <PlaceholderPage title="操作日志" /> },
      ],
    },
    { path: '*', element: <Navigate to="/" replace /> },
  ]);

  return routes;
};

const PlaceholderPage: React.FC<{ title: string }> = ({ title }) => (
  <div style={{ padding: 24, textAlign: 'center', color: '#8c8c8c', fontSize: 16, marginTop: 60 }}>
    {title} — 敬请期待
  </div>
);

export const PlatformRouter: React.FC = () => (
  <BrowserRouter basename="/platform">
    <Routes />
  </BrowserRouter>
);
