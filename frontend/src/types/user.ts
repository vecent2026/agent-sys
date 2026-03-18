export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
  avatar?: string;
  mobile?: string;
  email?: string;
  status: 0 | 1; // 0: 禁用, 1: 启用
  createTime: string;
  lastLoginTime?: string;
  roles?: string[]; // Role keys or Role objects, depending on API. Usually keys for simple auth, or objects for management.
  permissions?: string[];
  roleIds?: number[];
  roleNames?: string[];
  joinTime?: string;
}

export interface LoginResult {
  accessToken: string;
  refreshToken: string;
}

export interface UserQuery {
  page: number;
  size: number;
  username?: string;
  mobile?: string;
  status?: number;
}

export interface UserForm {
  username: string;
  password?: string; // Optional for edit
  nickname: string;
  status: 0 | 1;
  mobile?: string;
  email?: string;
  roleIds?: number[];
}
