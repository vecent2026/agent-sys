import { create } from 'zustand';
import { storage } from '@/utils/storage';
import type { UserInfo } from '@/types/user';
import type { Permission } from '@/types/permission';

interface UserState {
  token: {
    access: string | null;
    refresh: string | null;
  };
  userInfo: UserInfo | null;
  permissions: string[];
  menus: Permission[];
  setToken: (access: string, refresh: string) => void;
  setUserInfo: (userInfo: UserInfo) => void;
  setPermissions: (permissions: string[]) => void;
  setMenus: (menus: Permission[]) => void;
  logout: () => void;
}

export const useUserStore = create<UserState>((set) => ({
  token: {
    access: storage.getToken().access,
    refresh: storage.getToken().refresh,
  },
  userInfo: null,
  permissions: [],
  menus: [],
  setToken: (access, refresh) => {
    storage.setToken(access, refresh);
    set({ token: { access, refresh } });
  },
  setUserInfo: (userInfo) => set({ userInfo }),
  setPermissions: (permissions) => set({ permissions }),
  setMenus: (menus) => set({ menus }),
  logout: () => {
    storage.clearToken();
    set({ token: { access: null, refresh: null }, userInfo: null, permissions: [], menus: [] });
  },
}));
