import React from 'react';
import { Navigate } from 'react-router-dom';
import { usePlatformUserStore } from '@/store/platformUserStore';

interface PlatformGuardProps {
  children: React.ReactNode;
}

export const PlatformGuard: React.FC<PlatformGuardProps> = ({ children }) => {
  const token = usePlatformUserStore((state) => state.token);

  if (!token.access) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
};
