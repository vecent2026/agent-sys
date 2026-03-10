import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useUserStore } from '@/store/userStore';
import { storage } from '@/utils/storage';

// Mock the storage module
vi.mock('@/utils/storage', () => {
  return {
    storage: {
      get: vi.fn(),
      set: vi.fn(),
      remove: vi.fn(),
      clear: vi.fn(),
      getToken: vi.fn(() => ({ access: '', refresh: '' })),
      setToken: vi.fn(),
      clearToken: vi.fn(),
    },
  };
});

describe('userStore', () => {
  beforeEach(() => {
    useUserStore.getState().logout();
    vi.clearAllMocks();
  });

  it('should set token correctly', () => {
    const { setToken } = useUserStore.getState();
    setToken('access-token', 'refresh-token');

    const state = useUserStore.getState();
    expect(state.token.access).toBe('access-token');
    expect(state.token.refresh).toBe('refresh-token');
    expect(storage.setToken).toHaveBeenCalledWith('access-token', 'refresh-token');
  });

  it('should set user info correctly', () => {
    const { setUserInfo } = useUserStore.getState();
    const userInfo = { 
      id: 1, 
      username: 'admin', 
      nickname: 'Admin',
      status: 1,
      createTime: '2024-01-01 00:00:00'
    };
    
    setUserInfo(userInfo as any);

    const state = useUserStore.getState();
    expect(state.userInfo).toEqual(userInfo);
  });

  it('should logout correctly', () => {
    const { setToken, setUserInfo, logout } = useUserStore.getState();
    setToken('access-token', 'refresh-token');
    setUserInfo({ 
      id: 1, 
      username: 'admin', 
      nickname: 'Admin',
      status: 1,
      createTime: '2024-01-01 00:00:00'
    } as any);

    logout();

    const state = useUserStore.getState();
    expect(state.token.access).toBeNull();
    expect(state.token.refresh).toBeNull();
    expect(state.userInfo).toBeNull();
    expect(storage.clearToken).toHaveBeenCalled();
  });
});
