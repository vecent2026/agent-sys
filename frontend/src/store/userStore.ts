import { create } from 'zustand';
import { storage } from '@/utils/storage';
import type { UserInfo } from '@/types/user';
import type { Permission } from '@/types/permission';

interface TenantInfo {
  tenantId: number;
  tenantName: string;
  tenantCode?: string;
}

interface UserState {
  token: {
    access: string | null;
    refresh: string | null;
  };
  userInfo: UserInfo | null;
  permissions: string[];
  menus: Permission[];
  isTenantAdmin: boolean;
  // 租户上下文
  currentTenantId: number | null;
  currentTenantName: string | null;
  tenantList: TenantInfo[];

  setToken: (access: string, refresh: string) => void;
  setUserInfo: (userInfo: UserInfo) => void;
  setPermissions: (permissions: string[]) => void;
  setMenus: (menus: Permission[]) => void;
  setIsTenantAdmin: (v: boolean) => void;
  setCurrentTenant: (tenantId: number, tenantName: string) => void;
  setTenantList: (tenants: TenantInfo[]) => void;
  logout: () => void;
}

export const useUserStore = create<UserState>((set) => ({
  token: {
    access: storage.getToken().access || null,
    refresh: storage.getToken().refresh || null,
  },
  userInfo: null,
  permissions: [],
  menus: [],
  isTenantAdmin: false,
  currentTenantId: null,
  currentTenantName: null,
  tenantList: [],

  setToken: (access, refresh) => {
    storage.setToken(access, refresh);
    set({ token: { access, refresh } });
  },
  setUserInfo: (userInfo) => set({ userInfo }),
  setPermissions: (permissions) => set({ permissions }),
  setMenus: (menus) => set({ menus }),
  setIsTenantAdmin: (v) => set({ isTenantAdmin: v }),
  setCurrentTenant: (tenantId, tenantName) =>
    set({ currentTenantId: tenantId, currentTenantName: tenantName }),
  setTenantList: (tenants) => set({ tenantList: tenants }),
  logout: () => {
    storage.clearToken();
    set({
      token: { access: null, refresh: null },
      userInfo: null,
      permissions: [],
      menus: [],
      isTenantAdmin: false,
      currentTenantId: null,
      currentTenantName: null,
      tenantList: [],
    });
  },
}));
