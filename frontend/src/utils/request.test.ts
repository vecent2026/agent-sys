import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import { instance, request } from '@/utils/request';
import { storage } from '@/utils/storage';
import { useUserStore } from '@/store/userStore';

describe('request', () => {
  let mock: MockAdapter;

  beforeEach(() => {
    mock = new MockAdapter(instance);
    useUserStore.getState().logout();
    vi.clearAllMocks();
  });

  afterEach(() => {
    mock.restore();
  });

  it('should add authorization header if token exists', async () => {
    storage.setToken('test-token', 'refresh-token');
    
    mock.onGet('/test').reply(200, { code: 200, data: 'success' });

    await request.get('/test');

    expect(mock.history.get[0].headers?.Authorization).toBe('Bearer test-token');
  });

  it('should return data directly on success', async () => {
    const responseData = { id: 1, name: 'test' };
    mock.onGet('/test').reply(200, { code: 200, data: responseData });

    const result = await request.get('/test');
    expect(result).toEqual(responseData);
  });

  it('should handle business error', async () => {
    mock.onGet('/test').reply(200, { code: 500, message: 'Business Error' });

    // The request interceptor rejects with an Error object
    await expect(request.get('/test')).rejects.toThrow('Business Error');
  });
});
