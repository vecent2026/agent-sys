import React, { useMemo } from 'react';
import { BrowserRouter, useRoutes, Navigate } from 'react-router-dom';
import { useUserStore } from '@/store/userStore';
import { generateRoutes } from '@/utils/route-utils';
import BasicLayout from '@/layouts/BasicLayout';
import LoginLayout from '@/layouts/LoginLayout';
import Login from '@/pages/auth/Login';
import Dashboard from '@/pages/dashboard';
import { AuthGuard } from '@/components/AuthGuard';

const Routes: React.FC = () => {
  const menus = useUserStore((state) => state.menus);
  const dynamicRoutes = useMemo(() => generateRoutes(menus), [menus]);

  const routes = useRoutes([
    {
      path: '/login',
      element: <LoginLayout />,
      children: [{ path: '', element: <Login /> }],
    },
    {
      path: '/',
      element: (
        <AuthGuard>
          <BasicLayout />
        </AuthGuard>
      ),
      children: [
        { path: '', element: <Navigate to="/dashboard" replace /> },
        { path: 'dashboard', element: <Dashboard /> },
        ...dynamicRoutes,
      ],
    },
    { path: '*', element: <div>404 Not Found</div> },
  ]);

  return routes;
};

export const AppRouter: React.FC = () => (
  <BrowserRouter>
    <Routes />
  </BrowserRouter>
);
