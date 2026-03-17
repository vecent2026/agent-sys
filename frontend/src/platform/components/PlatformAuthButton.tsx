import React from 'react';
import { usePlatformPermission } from '../hooks/usePlatformPermission';

interface PlatformAuthButtonProps {
  permission: string;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export const PlatformAuthButton: React.FC<PlatformAuthButtonProps> = ({
  permission,
  children,
  fallback = null,
}) => {
  const { has } = usePlatformPermission();
  return has(permission) ? <>{children}</> : <>{fallback}</>;
};
