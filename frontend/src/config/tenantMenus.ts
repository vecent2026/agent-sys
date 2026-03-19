import type { Permission } from '@/types/permission';

/**
 * 租户端全量菜单配置
 * permissionKey 对应后端权限码，超管跳过过滤
 */
const ALL_TENANT_MENUS: Permission[] = [
  {
    id: 100,
    parentId: null,
    name: '用户管理',
    type: 'DIR',
    permissionKey: '',
    path: '/app-user',
    icon: 'TeamOutlined',
    sort: 1,
    logEnabled: false,
    children: [
      {
        id: 101,
        parentId: 100,
        name: '用户列表',
        type: 'MENU',
        permissionKey: 'app:user:list',
        path: '/app-user/user',
        component: 'app-user/user/index',
        sort: 1,
        logEnabled: false,
      },
      {
        id: 102,
        parentId: 100,
        name: '标签管理',
        type: 'MENU',
        permissionKey: 'app:tag:list',
        path: '/app-user/tag',
        component: 'app-user/tag/index',
        sort: 2,
        logEnabled: false,
      },
      {
        id: 103,
        parentId: 100,
        name: '自定义字段',
        type: 'MENU',
        permissionKey: 'app:field:list',
        path: '/app-user/field',
        component: 'app-user/field/index',
        sort: 3,
        logEnabled: false,
      },
    ],
  },
  {
    id: 200,
    parentId: null,
    name: '系统设置',
    type: 'DIR',
    permissionKey: '',
    path: '/system',
    icon: 'SettingOutlined',
    sort: 2,
    logEnabled: false,
    children: [
      {
        id: 201,
        parentId: 200,
        name: '角色管理',
        type: 'MENU',
        permissionKey: 'tenant:role:list',
        path: '/system/role',
        component: 'system/role/index',
        sort: 1,
        logEnabled: false,
      },
      {
        id: 202,
        parentId: 200,
        name: '成员管理',
        type: 'MENU',
        permissionKey: 'tenant:member:list',
        path: '/system/user',
        component: 'system/user/index',
        sort: 2,
        logEnabled: false,
      },
      {
        id: 203,
        parentId: 200,
        name: '操作日志',
        type: 'MENU',
        permissionKey: 'tenant:log:list',
        path: '/system/log',
        component: 'system/log/index',
        sort: 3,
        logEnabled: false,
      },
    ],
  },
];

/**
 * 根据权限码过滤菜单
 * @param permKeys 用户拥有的权限码列表
 * @param isTenantAdmin 是否租户超管（跳过过滤）
 */
export function buildTenantMenus(permKeys: string[], isTenantAdmin: boolean): Permission[] {
  if (isTenantAdmin) return ALL_TENANT_MENUS;

  return ALL_TENANT_MENUS.map((dir) => ({
    ...dir,
    children: (dir.children ?? []).filter(
      (menu) => !menu.permissionKey || permKeys.includes(menu.permissionKey),
    ),
  })).filter((dir) => (dir.children ?? []).length > 0);
}
