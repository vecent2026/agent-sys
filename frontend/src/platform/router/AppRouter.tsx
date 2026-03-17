import React from 'react';
import { BrowserRouter, useRoutes, Navigate } from 'react-router-dom';
import { PlatformGuard } from '../guards/PlatformGuard';
import PlatformLayout from '../layouts/PlatformLayout';
import PlatformLogin from '../pages/auth/Login';
import PlatformDashboard from '../pages/dashboard';
import PlatformTenantPage from '../pages/tenants';
import PlatformUsersPage from '../pages/users';
import PlatformRolesPage from '../pages/roles';
import PlatformPermissionsPage from '../pages/permissions';
import PlatformLogsPage from '../pages/logs';

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
        { path: 'tenants', element: <PlatformTenantPage /> },
        { path: 'users', element: <PlatformUsersPage /> },
        { path: 'roles', element: <PlatformRolesPage /> },
        { path: 'permissions', element: <PlatformPermissionsPage /> },
        { path: 'logs', element: <PlatformLogsPage /> },
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
