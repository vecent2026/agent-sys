import request from '@/utils/request';
import type { UserInfo, UserForm, UserQuery } from '@/types/user';
import type { PageResult } from '@/types/api';

const paginateAndFilter = (list: UserInfo[], params: UserQuery): PageResult<UserInfo> => {
  const keywordUsername = (params.username || '').trim();
  const keywordMobile = (params.mobile || '').trim();

  const filtered = list.filter((item) => {
    const nickname = (item.nickname || '') as string;
    const mobile = (item.mobile || '') as string;
    const statusMatch = params.status === undefined || params.status === null || item.status === params.status;
    const nicknameMatch = !keywordUsername || nickname.includes(keywordUsername);
    const mobileMatch = !keywordMobile || mobile.includes(keywordMobile);
    return statusMatch && nicknameMatch && mobileMatch;
  });

  const page = params.page || 1;
  const size = params.size || 20;
  const start = (page - 1) * size;
  const end = start + size;

  return {
    records: filtered.slice(start, end),
    total: filtered.length,
    current: page,
    size,
    pages: Math.ceil(filtered.length / size),
  };
};

export const getUserList = async (params: UserQuery) => {
  const list = await request.get<UserInfo[]>('/api/tenant/members');
  return paginateAndFilter(Array.isArray(list) ? list : [], params);
};

export const createUser = (data: UserForm) => {
  return request.post('/api/tenant/members', {
    mobile: data.mobile,
    nickname: data.nickname,
    password: data.password,
    roleIds: data.roleIds || [],
  });
};

export const updateUser = (id: number, data: UserForm) => {
  return request.put(`/api/tenant/members/${id}/roles`, { roleIds: data.roleIds || [] });
};

export const deleteUser = (ids: number[]) => {
  return Promise.all(ids.map((id) => request.delete(`/api/tenant/members/${id}`)));
};

export const resetUserPassword = (_id: number, _password?: string) => {
  return Promise.reject(new Error('租户成员不支持在本页重置密码'));
};

export const updateUserStatus = (id: number, status: 0 | 1) => {
  return request.put(`/api/tenant/members/${id}/status`, { status });
};

export const getUserRoles = (id: number) => {
  return request.get<number[]>(`/api/tenant/members/${id}`).then((res: any) => res?.roleIds || []);
};

export const assignUserRoles = (id: number, roleIds: number[]) => {
  return request.put(`/api/tenant/members/${id}/roles`, { roleIds });
};
