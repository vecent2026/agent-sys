export interface Role {
  id: number;
  roleName: string;
  roleKey: string;
  description?: string;
  createTime: string | null;
  updateTime: string | null;
  createBy: string;
}

export interface RoleQuery {
  page: number;
  size: number;
  roleName?: string;
}

export interface RoleForm {
  roleName: string;
  roleKey: string;
  description?: string;
  permissionIds?: number[];
}
