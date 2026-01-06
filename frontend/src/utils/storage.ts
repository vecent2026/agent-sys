export const STORAGE_KEYS = {
  ACCESS_TOKEN: 'access_token',
  REFRESH_TOKEN: 'refresh_token',
  USER_INFO: 'user_info',
  THEME: 'theme',
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
  getToken() {
    return {
      access: this.get(STORAGE_KEYS.ACCESS_TOKEN) || '',
      refresh: this.get(STORAGE_KEYS.REFRESH_TOKEN) || '',
    };
  },
  setToken(access: string, refresh: string) {
    this.set(STORAGE_KEYS.ACCESS_TOKEN, access);
    this.set(STORAGE_KEYS.REFRESH_TOKEN, refresh);
  },
  clearToken() {
    this.remove(STORAGE_KEYS.ACCESS_TOKEN);
    this.remove(STORAGE_KEYS.REFRESH_TOKEN);
  },
};
