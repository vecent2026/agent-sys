import { create } from 'zustand';
import { storage } from '@/utils/storage';

interface PlatformUserInfo {
  id: number;
  username: string;
  nickname?: string;
  email?: string;
  avatar?: string;
  isSuper: boolean;
}

interface PlatformUserState {
  token: {
    access: string | null;
    refresh: string | null;
  };
  userInfo: PlatformUserInfo | null;
  permissions: string[];
  isSuper: boolean;

  setToken: (access: string, refresh: string) => void;
  setUserInfo: (userInfo: PlatformUserInfo) => void;
  setPermissions: (permissions: string[]) => void;
  logout: () => void;
}

export const usePlatformUserStore = create<PlatformUserState>((set) => ({
  token: {
    access: storage.getPlatformToken().access || null,
    refresh: storage.getPlatformToken().refresh || null,
  },
  userInfo: null,
  permissions: [],
  isSuper: false,

  setToken: (access, refresh) => {
    storage.setPlatformToken(access, refresh);
    set({ token: { access, refresh } });
  },
  setUserInfo: (userInfo) => set({ userInfo, isSuper: !!userInfo?.isSuper }),
  setPermissions: (permissions) => set({ permissions }),
  logout: () => {
    storage.clearPlatformToken();
    set({
      token: { access: null, refresh: null },
      userInfo: null,
      permissions: [],
      isSuper: false,
    });
  },
}));
