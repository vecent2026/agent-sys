import React from 'react';
import { BrowserRouter, useRoutes, Navigate } from 'react-router-dom';
import { PlatformGuard } from '../guards/PlatformGuard';
import PlatformLayout from '../layouts/PlatformLayout';
import PlatformLogin from '../pages/auth/Login';
import PlatformDashboard from '../pages/dashboard';
import TenantPage from '../pages/tenants';
import UserPage from '../pages/users';
import RolePage from '../pages/roles';
import PermissionPage from '../pages/permissions';
import LogPage from '../pages/oplogs';

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
        { path: 'tenants', element: <TenantPage /> },
        { path: 'users', element: <UserPage /> },
        { path: 'roles', element: <RolePage /> },
        { path: 'permissions', element: <PermissionPage /> },
        { path: 'logs', element: <LogPage /> },
      ],
    },
    { path: '*', element: <Navigate to="/" replace /> },
  ]);

  return routes;
};

export const PlatformRouter: React.FC = () => (
  <BrowserRouter basename="/platform">
    <Routes />
  </BrowserRouter>
);
