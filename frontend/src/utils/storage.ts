export const STORAGE_KEYS = {
  // 租户端 Token
  TENANT_ACCESS_TOKEN: 'tenant_access_token',
  TENANT_REFRESH_TOKEN: 'tenant_refresh_token',
  // 平台端 Token
  PLATFORM_ACCESS_TOKEN: 'platform_access_token',
  PLATFORM_REFRESH_TOKEN: 'platform_refresh_token',
  // 通用
  USER_INFO: 'user_info',
  THEME: 'theme',
  // 向后兼容
  ACCESS_TOKEN: 'tenant_access_token',
  REFRESH_TOKEN: 'tenant_refresh_token',
} as const;

export const storage = {
  get(key: string) {
    return localStorage.getItem(key);
  },
  set(key: string, value: string) {
    localStorage.setItem(key, value);
  },
  remove(key: string) {
    localStorage.removeItem(key);
  },
  clear() {
    localStorage.clear();
  },

  // ── 租户端 Token ──────────────────────────────
  getToken() {
    return {
      access: this.get(STORAGE_KEYS.TENANT_ACCESS_TOKEN) || '',
      refresh: this.get(STORAGE_KEYS.TENANT_REFRESH_TOKEN) || '',
    };
  },
  setToken(access: string, refresh: string) {
    this.set(STORAGE_KEYS.TENANT_ACCESS_TOKEN, access);
    this.set(STORAGE_KEYS.TENANT_REFRESH_TOKEN, refresh);
  },
  clearToken() {
    this.remove(STORAGE_KEYS.TENANT_ACCESS_TOKEN);
    this.remove(STORAGE_KEYS.TENANT_REFRESH_TOKEN);
  },

  // ── 平台端 Token ──────────────────────────────
  getPlatformToken() {
    return {
      access: this.get(STORAGE_KEYS.PLATFORM_ACCESS_TOKEN) || '',
      refresh: this.get(STORAGE_KEYS.PLATFORM_REFRESH_TOKEN) || '',
    };
  },
  setPlatformToken(access: string, refresh: string) {
    this.set(STORAGE_KEYS.PLATFORM_ACCESS_TOKEN, access);
    this.set(STORAGE_KEYS.PLATFORM_REFRESH_TOKEN, refresh);
  },
  clearPlatformToken() {
    this.remove(STORAGE_KEYS.PLATFORM_ACCESS_TOKEN);
    this.remove(STORAGE_KEYS.PLATFORM_REFRESH_TOKEN);
  },
};
