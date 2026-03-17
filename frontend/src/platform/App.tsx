import React, { useEffect, useState } from 'react';
import { ConfigProvider, Spin } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import zhCN from 'antd/locale/zh_CN';
import { PlatformRouter } from './router/AppRouter';
import { usePlatformUserStore } from '@/store/platformUserStore';
import { getPlatformUserInfo, getPlatformPermissions } from './api/auth';
import ErrorBoundary from '@/components/ErrorBoundary';
import 'dayjs/locale/zh-cn';
import { appTheme } from '@/design-system/theme';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
});

const PlatformApp: React.FC = () => {
  const token = usePlatformUserStore((state) => state.token);
  const [isRestoring, setIsRestoring] = useState(false);

  useEffect(() => {
    const restoreState = async () => {
      const { userInfo, setUserInfo, setPermissions } = usePlatformUserStore.getState();
      if (token.access && !userInfo) {
        setIsRestoring(true);
        try {
          const [fetchedUserInfo, permissions] = await Promise.all([
            getPlatformUserInfo(),
            getPlatformPermissions(),
          ]);
          setUserInfo(fetchedUserInfo);
          setPermissions(permissions || []);
        } catch (error) {
          console.error('Failed to restore platform user state:', error);
          usePlatformUserStore.getState().logout();
        } finally {
          setIsRestoring(false);
        }
      }
    };

    restoreState();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token.access]);

  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <ConfigProvider locale={zhCN} theme={appTheme}>
          {isRestoring ? (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
              <Spin size="large" />
            </div>
          ) : (
            <PlatformRouter />
          )}
        </ConfigProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  );
};

export default PlatformApp;
