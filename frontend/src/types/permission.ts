export type PermissionType = 'DIR' | 'MENU' | 'BTN';

export interface Permission {
  id: number;
  parentId: number | null;
  name: string;
  type: PermissionType;
  permissionKey: string;
  path?: string;
  component?: string;
  icon?: string;
  sort: number;
  logEnabled: boolean;
  children?: Permission[]; // For tree structure
}

export interface PermissionForm {
  parentId?: number;
  name: string;
  type: PermissionType;
  permissionKey: string;
  path?: string;
  component?: string;
  icon?: string;
  sort: number;
  logEnabled?: boolean;
}
