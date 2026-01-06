import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useUserStore } from '@/store/userStore';

export const AuthGuard = ({ children }: { children: React.ReactElement }) => {
  const token = useUserStore((state) => state.token.access);
  const location = useLocation();

  if (!token) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
};
