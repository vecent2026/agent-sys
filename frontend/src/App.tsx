import React, { useEffect, useState } from 'react';
import { ConfigProvider, Spin } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import zhCN from 'antd/locale/zh_CN';
import { AppRouter } from '@/router/AppRouter';
import { useUserStore } from '@/store/userStore';
import { getTenantPermissions, getTenantUserInfo } from '@/api/auth';
import { buildTenantMenus } from '@/config/tenantMenus';
import ErrorBoundary from '@/components/ErrorBoundary';
import 'dayjs/locale/zh-cn';
import { appTheme } from '@/design-system/theme';

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
      const {
        userInfo,
        menus,
        setUserInfo,
        setMenus,
        setPermissions,
        setIsTenantAdmin,
        setCurrentTenant,
        tenantList,
      } = useUserStore.getState();
      if (token.access && typeof token.access === 'string' && token.access.trim() !== '' && !userInfo && !menus.length) {
        setIsRestoring(true);
        try {
          const fetchedUserInfo = await getTenantUserInfo();
          const fetchedPermissions = await getTenantPermissions();
          const permissions = Array.isArray(fetchedPermissions) ? fetchedPermissions : [];
          const roleKeys = Array.isArray(fetchedUserInfo.roles) ? fetchedUserInfo.roles : [];
          const isTenantAdmin = roleKeys.includes('tenant_admin') || roleKeys.includes('TENANT_SUPER_ADMIN');

          setUserInfo(fetchedUserInfo);
          setPermissions(permissions);
          setIsTenantAdmin(isTenantAdmin);
          setMenus(buildTenantMenus(permissions, isTenantAdmin));

          const tenantInfo = (fetchedUserInfo as any).currentTenant || (fetchedUserInfo as any).tenant;
          const tenantId = tenantInfo?.tenantId ?? tenantInfo?.id ?? (fetchedUserInfo as any).currentTenantId;
          const matchedTenant = typeof tenantId === 'number'
            ? tenantList.find((tenant) => tenant.tenantId === tenantId)
            : null;
          const tenantName = tenantInfo?.tenantName ?? tenantInfo?.name ?? matchedTenant?.tenantName;
          if (typeof tenantId === 'number' && typeof tenantName === 'string' && tenantName.trim() !== '') {
            setCurrentTenant(tenantId, tenantName);
          }
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
        <ConfigProvider locale={zhCN} theme={appTheme}>
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
