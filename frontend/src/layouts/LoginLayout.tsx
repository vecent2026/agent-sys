import React from 'react';
import { Outlet } from 'react-router-dom';

const LoginLayout: React.FC = () => {
  return (
    <div style={{ height: '100vh', display: 'flex', justifyContent: 'center', alignItems: 'center', background: '#f0f2f5' }}>
      <Outlet />
    </div>
  );
};

export default LoginLayout;
