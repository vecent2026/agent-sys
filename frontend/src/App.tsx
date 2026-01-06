import React, { useEffect, useState } from 'react';
import { ConfigProvider, Spin } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import zhCN from 'antd/locale/zh_CN';
import { AppRouter } from '@/router/AppRouter';
import { useUserStore } from '@/store/userStore';
import { getUserInfo, getMenus } from '@/api/auth';
import type { Permission } from '@/types/permission';
import 'dayjs/locale/zh-cn';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

const App: React.FC = () => {
  const { token, userInfo, menus, setUserInfo, setMenus, setPermissions } = useUserStore();
  const [isRestoring, setIsRestoring] = useState(false);

  useEffect(() => {
    // Restore user data from API if token exists
    const restoreUserState = async () => {
      // Only attempt to restore if we have a valid access token
      // and haven't already restored user data
      if (token.access && typeof token.access === 'string' && token.access.trim() !== '' && !userInfo && !menus.length) {
        setIsRestoring(true);
        try {
          // Get user info and menus from API
          const userInfo = await getUserInfo();
          setUserInfo(userInfo);

          const menus = await getMenus();
          setMenus(menus);

          // Extract permissions from menus for permission control
          const extractPermissions = (menuItems: Permission[]): string[] => {
            const perms: string[] = [];
            const traverse = (items: Permission[]) => {
              items.forEach(item => {
                if (item.permissionKey) {
                  perms.push(item.permissionKey);
                }
                if (item.children && item.children.length > 0) {
                  traverse(item.children);
                }
              });
            };
            traverse(menuItems);
            return perms;
          };
          
          const permissions = extractPermissions(menus);
          setPermissions(permissions);
        } catch (error) {
          console.error('Failed to restore user state:', error);
          // Token might be invalid, clear state
          // Use a timeout to avoid synchronous state updates causing loops
          setTimeout(() => {
            useUserStore.getState().logout();
          }, 0);
        } finally {
          setIsRestoring(false);
        }
      }
    };

    restoreUserState();
  }, [token.access, userInfo, menus, setUserInfo, setMenus, setPermissions]);

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider locale={zhCN}>
        {isRestoring ? (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
            <Spin size="large" />
          </div>
        ) : (
          <AppRouter />
        )}
      </ConfigProvider>
    </QueryClientProvider>
  );
};

export default App;
