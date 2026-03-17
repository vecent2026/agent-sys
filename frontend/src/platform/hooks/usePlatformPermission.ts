import { usePlatformUserStore } from '@/store/platformUserStore';

export function usePlatformPermission() {
  const { permissions, isSuper } = usePlatformUserStore();

  return {
    has: (perm: string) => isSuper || permissions.includes(perm),
    hasAny: (perms: string[]) => isSuper || perms.some((p) => permissions.includes(p)),
    hasAll: (perms: string[]) => isSuper || perms.every((p) => permissions.includes(p)),
  };
}
