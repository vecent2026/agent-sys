import React, { useEffect } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { usePlatformUserStore } from '@/store/platformUserStore';
import { getPlatformUserInfo, getPlatformPermissions } from '../api/auth';

function parseJwtExp(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp ?? null;
  } catch {
    return null;
  }
}

function isTokenExpired(token: string): boolean {
  const exp = parseJwtExp(token);
  if (!exp) return true;
  return Date.now() / 1000 >= exp - 60;
}

interface PlatformGuardProps {
  children: React.ReactNode;
}

export const PlatformGuard: React.FC<PlatformGuardProps> = ({ children }) => {
  const location = useLocation();
  const { token, userInfo, setUserInfo, setPermissions, logout } = usePlatformUserStore();

  useEffect(() => {
    if (!token.access) return;
    if (isTokenExpired(token.access)) {
      logout();
      return;
    }
    if (!userInfo) {
      Promise.all([getPlatformUserInfo(), getPlatformPermissions()])
        .then(([info, perms]) => {
          setUserInfo(info);
          setPermissions(perms || []);
        })
        .catch(() => logout());
    }
  }, [token.access, userInfo, setUserInfo, setPermissions, logout]);

  if (!token.access) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (isTokenExpired(token.access)) {
    logout();
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
};
