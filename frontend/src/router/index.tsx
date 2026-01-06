import { Navigate } from 'react-router-dom';
import type { RouteObject } from 'react-router-dom';
import BasicLayout from '@/layouts/BasicLayout';
import LoginLayout from '@/layouts/LoginLayout';
import Login from '@/pages/auth/Login';
import Dashboard from '@/pages/dashboard';
import { AuthGuard } from '@/components/AuthGuard';

export const getStaticRoutes = (): RouteObject[] => [
  {
    path: '/login',
    element: <LoginLayout />,
    children: [
      {
        path: '',
        element: <Login />,
      },
    ],
  },
  {
    path: '/',
    element: (
      <AuthGuard>
        <BasicLayout />
      </AuthGuard>
    ),
    children: [
      {
        path: '',
        element: <Navigate to="/dashboard" replace />,
      },
      {
        path: 'dashboard',
        element: <Dashboard />,
      },
      // Dynamic routes will be injected here
    ],
  },
  {
    path: '*',
    element: <div>404 Not Found</div>,
  },
];
