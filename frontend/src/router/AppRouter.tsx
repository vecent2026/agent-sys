import React, { useMemo } from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { useUserStore } from '@/store/userStore';
import { generateRoutes } from '@/utils/route-utils';
import { getStaticRoutes } from './index';

export const AppRouter: React.FC = () => {
  const menus = useUserStore((state) => state.menus);

  const router = useMemo(() => {
    const routes = getStaticRoutes();
    
    // Find the layout route (path: '/')
    const layoutRoute = routes.find((r) => r.path === '/');
    if (layoutRoute && layoutRoute.children) {
      const dynamicRoutes = generateRoutes(menus);
      layoutRoute.children = [...layoutRoute.children, ...dynamicRoutes];
    }

    return createBrowserRouter(routes);
  }, [menus]);

  return <RouterProvider router={router} />;
};
