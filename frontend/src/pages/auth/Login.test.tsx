import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import Login from '@/pages/auth/Login';
import * as authApi from '@/api/auth';
import { useUserStore } from '@/store/userStore';

// Mock the auth api
vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  getUserInfo: vi.fn(),
  getMenus: vi.fn(),
}));

// Mock message
vi.mock('antd', async (importOriginal) => {
  await importOriginal<typeof import('antd')>();
  return {
    message: {
      success: vi.fn(),
      error: vi.fn(),
    },
  };
});

// Mock useNavigate
const navigateMock = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => navigateMock,
  };
});

describe('Login Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useUserStore.getState().logout();
  });

  it('should render login form', () => {
    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>
    );

    expect(screen.getByPlaceholderText('用户名')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('密码')).toBeInTheDocument();
    // Use getByRole since the button text has a space
    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('should show validation error on empty submit', async () => {
    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>
    );

    // Use getByRole for the button
    const button = screen.getByRole('button');
    fireEvent.click(button);

    await waitFor(() => {
      expect(screen.getByText('请输入用户名!')).toBeInTheDocument();
      expect(screen.getByText('请输入密码!')).toBeInTheDocument();
    });
  });

  it('should call login api and redirect on success', async () => {
    const mockLoginResult = { accessToken: 'access', refreshToken: 'refresh' };
    const mockUserInfo = { 
      id: 1, 
      username: 'admin', 
      nickname: 'Admin',
      status: 1,
      createTime: '2024-01-01 00:00:00'
    };
    const mockMenus: any[] = [];

    vi.mocked(authApi.login).mockResolvedValue(mockLoginResult);
    vi.mocked(authApi.getUserInfo).mockResolvedValue(mockUserInfo as any);
    vi.mocked(authApi.getMenus).mockResolvedValue(mockMenus);

    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>
    );

    fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'admin' } });
    fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: '123456' } });
    
    // Use getByRole for the button
    const loginButton = screen.getByRole('button');
    fireEvent.click(loginButton);

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith({ username: 'admin', password: '123456' });
    });
    
    await waitFor(() => {
        expect(authApi.getUserInfo).toHaveBeenCalled();
    });

    await waitFor(() => {
        expect(authApi.getMenus).toHaveBeenCalled();
    });

    await waitFor(() => {
        expect(navigateMock).toHaveBeenCalledWith('/dashboard');
    });

    const state = useUserStore.getState();
    expect(state.token.access).toBe('access');
    expect(state.userInfo).toEqual(mockUserInfo);
  });
});
