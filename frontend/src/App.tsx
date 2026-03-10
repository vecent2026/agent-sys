import React, { useEffect, useState } from 'react';
import { ConfigProvider, Spin } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import zhCN from 'antd/locale/zh_CN';
import { AppRouter } from '@/router/AppRouter';
import { useUserStore } from '@/store/userStore';
import { getUserInfo, getMenus } from '@/api/auth';
import ErrorBoundary from '@/components/ErrorBoundary';
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
  const token = useUserStore((state) => state.token);
  const [isRestoring, setIsRestoring] = useState(false);

  useEffect(() => {
    const restoreUserState = async () => {
      const { userInfo, menus, setUserInfo, setMenus, setPermissions } = useUserStore.getState();
      if (token.access && typeof token.access === 'string' && token.access.trim() !== '' && !userInfo && !menus.length) {
        setIsRestoring(true);
        try {
          const fetchedUserInfo = await getUserInfo();
          setUserInfo(fetchedUserInfo);

          const fetchedMenus = await getMenus();
          setMenus(fetchedMenus);

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
          
          const permissions = extractPermissions(fetchedMenus);
          setPermissions(permissions);
        } catch (error) {
          console.error('Failed to restore user state:', error);
          setTimeout(() => {
            useUserStore.getState().logout();
          }, 0);
        } finally {
          setIsRestoring(false);
        }
      }
    };

    restoreUserState();
    // 只依赖 token.access，避免 setMenus → menus 变化 → effect 重新触发的循环
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token.access]);

  return (
    <ErrorBoundary>
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
    </ErrorBoundary>
  );
};

export default App;
