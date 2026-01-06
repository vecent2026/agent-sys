import React from 'react';
import { useUserStore } from '@/store/userStore';

interface AuthButtonProps {
  perm: string;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export const AuthButton: React.FC<AuthButtonProps> = ({ perm, children, fallback = null }) => {
  const permissions = useUserStore((state) => state.permissions);
  const hasPermission = permissions.includes('*:*:*') || permissions.includes(perm);

  if (hasPermission) {
    return <>{children}</>;
  }

  return <>{fallback}</>;
};
