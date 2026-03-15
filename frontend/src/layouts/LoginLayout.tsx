import React from 'react';
import { Outlet } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import { appTheme } from '@/design-system/theme';

const LoginLayout: React.FC = () => {
  return (
    <ConfigProvider theme={appTheme}>
      <div
        style={{
          height: '100vh',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          background:
            'radial-gradient(circle at top left, #E0F2FE, #F8FAFC 50%, #E5E7EB 100%)',
        }}
      >
        <Outlet />
      </div>
    </ConfigProvider>
  );
};

export default LoginLayout;
