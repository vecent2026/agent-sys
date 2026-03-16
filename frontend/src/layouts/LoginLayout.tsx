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
          position: 'relative',
        }}
      >
        <a
          href="/platform"
          target="_blank"
          rel="noopener noreferrer"
          style={{
            position: 'absolute',
            top: 20,
            right: 24,
            fontSize: 13,
            color: '#64748b',
            textDecoration: 'none',
            display: 'flex',
            alignItems: 'center',
            gap: 4,
            padding: '6px 12px',
            borderRadius: 6,
            transition: 'background 0.15s, color 0.15s',
          }}
          onMouseEnter={e => {
            (e.currentTarget as HTMLAnchorElement).style.background = 'rgba(100,116,139,0.08)';
            (e.currentTarget as HTMLAnchorElement).style.color = '#1e293b';
          }}
          onMouseLeave={e => {
            (e.currentTarget as HTMLAnchorElement).style.background = 'transparent';
            (e.currentTarget as HTMLAnchorElement).style.color = '#64748b';
          }}
        >
          管理后台
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M2 2h8v8M10 2 4 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </a>
        <Outlet />
      </div>
    </ConfigProvider>
  );
};

export default LoginLayout;
