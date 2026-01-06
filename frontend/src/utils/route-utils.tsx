import React, { Suspense } from 'react';
import type { RouteObject } from 'react-router-dom';
import { Spin } from 'antd';
import type { Permission } from '@/types/permission';

// Auto-import all page components
const modules = import.meta.glob('../pages/**/*.tsx');

/**
 * Convert backend menu tree to React Router routes
 */
export const generateRoutes = (menus: Permission[], parentPath = ''): RouteObject[] => {
  const routes: RouteObject[] = [];
  
  menus.forEach((menu) => {
    // For DIRECTORY type, only process its children, don't create a route node for it
    if (menu.type === 'DIR') {
      if (menu.children && menu.children.length > 0) {
        const childRoutes = generateRoutes(menu.children, parentPath);
        routes.push(...childRoutes);
      }
      return;
    }
    
    // For MENU type, create a route node
    if (menu.type === 'MENU' && menu.path) {
      // Normalize path for React Router (remove leading slash for child routes)
      const normalizedPath = menu.path.startsWith('/') ? menu.path.slice(1) : menu.path;
      const fullPath = parentPath ? `${parentPath}/${normalizedPath}` : normalizedPath;
      
      const route: RouteObject = {
        path: normalizedPath,
        id: `route-${menu.id}-${fullPath}`,
      };

      if (menu.component) {
        // Normalize component path
        let cleanComponent = menu.component.startsWith('/') ? menu.component.slice(1) : menu.component;
        
        // Handle component path mapping for permission page
        // Backend sends "system/permission/index" but frontend has "system/perm/index"
        if (cleanComponent === 'system/permission/index') {
          cleanComponent = 'system/perm/index';
        }
        
        // Try to find the component file
        let componentPath = '';
        if (modules[`../pages/${cleanComponent}.tsx`]) {
          componentPath = `../pages/${cleanComponent}.tsx`;
        } else if (modules[`../pages/${cleanComponent}/index.tsx`]) {
          componentPath = `../pages/${cleanComponent}/index.tsx`;
        }

        if (componentPath) {
          const Component = React.lazy(modules[componentPath] as any);
          route.element = (
            <Suspense fallback={<Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />}>
              <Component />
            </Suspense>
          );
        } else {
          console.warn(`Component not found: ${menu.component}, tried: ${cleanComponent}`);
          route.element = <div>Component not found: {menu.component}</div>;
        }
      }

      routes.push(route);
    }
  });
  
  return routes;
};
