import React, { useEffect, useMemo, useState } from 'react';
import { Drawer, Tree, Button, message, Spin } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getPlatformRolePermissionIds,
  assignPlatformRolePermissionIds,
} from '../../../api/role';
import { getPlatformPermissionTree, type PlatformPermission } from '../../../api/permission';

interface RolePermissionDrawerProps {
  open: boolean;
  onClose: () => void;
  roleId: number | null;
  roleName?: string;
}

function filterPlatformScopeNodes(nodes: PlatformPermission[]): PlatformPermission[] {
  const result: PlatformPermission[] = [];
  for (const node of nodes) {
    const scope = (node as any).scope;
    const isPlatform = scope === 'platform' || node.permissionKey?.startsWith('platform:');
    const child = { ...node };
    if (child.children?.length) {
      child.children = filterPlatformScopeNodes(child.children);
      if (child.children.length > 0) result.push(child);
    } else if (isPlatform) {
      result.push(child);
    }
  }
  return result;
}

function collectLeafPermissionIds(nodes: PlatformPermission[]): number[] {
  const ids: number[] = [];
  const walk = (list: PlatformPermission[]) => {
    for (const n of list) {
      if (n.permissionKey) ids.push(n.id);
      if (n.children?.length) walk(n.children);
    }
  };
  walk(nodes);
  return ids;
}

function toTreeData(nodes: PlatformPermission[]): DataNode[] {
  return nodes.map((n) => {
    const hasKey = !!n.permissionKey;
    const children = n.children?.length ? toTreeData(n.children) : undefined;
    return {
      key: String(n.id),
      title: n.name || n.permissionKey || `节点${n.id}`,
      children: children?.length ? children : undefined,
      checkable: hasKey,
      disabled: !hasKey,
    };
  });
}

const RolePermissionDrawer: React.FC<RolePermissionDrawerProps> = ({
  open,
  onClose,
  roleId,
  roleName,
}) => {
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  const [halfCheckedKeys, setHalfCheckedKeys] = useState<React.Key[]>([]);
  const queryClient = useQueryClient();

  const { data: allPerms, isLoading: loadingPerms } = useQuery({
    queryKey: ['platform-permissions-tree'],
    queryFn: getPlatformPermissionTree,
    enabled: open,
  });

  const { data: rolePermIds, isLoading: loadingRole } = useQuery({
    queryKey: ['platform-role-permissions', roleId],
    queryFn: () => getPlatformRolePermissionIds(roleId!),
    enabled: open && !!roleId,
  });

  const updateMutation = useMutation({
    mutationFn: (ids: number[]) => assignPlatformRolePermissionIds(roleId!, ids),
    onSuccess: () => {
      message.success('权限配置已保存');
      onClose();
      queryClient.invalidateQueries({ queryKey: ['platform-roles'] });
      queryClient.invalidateQueries({ queryKey: ['platform-role-permissions', roleId] });
    },
  });

  const platformScopeTree = useMemo(() => {
    if (!allPerms) return [];
    const list = Array.isArray(allPerms) ? allPerms : (allPerms as any).children || [];
    return filterPlatformScopeNodes(list);
  }, [allPerms]);

  const treeData = useMemo(() => toTreeData(platformScopeTree), [platformScopeTree]);

  useEffect(() => {
    if (open && rolePermIds && Array.isArray(rolePermIds)) {
      setCheckedKeys(rolePermIds.map(String));
    }
  }, [open, rolePermIds]);

  const handleSave = () => {
    const allCheckedSet = new Set([...checkedKeys, ...halfCheckedKeys].map(String));
    const leafIds = collectLeafPermissionIds(platformScopeTree).filter((id) =>
      allCheckedSet.has(String(id))
    );
    updateMutation.mutate(leafIds);
  };

  const loading = loadingPerms || loadingRole;

  return (
    <Drawer
      title={`配置角色权限 — ${roleName || '角色'}`}
      open={open}
      onClose={onClose}
      width={520}
      destroyOnClose
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={handleSave} loading={updateMutation.isPending}>
            保存
          </Button>
        </div>
      }
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : (
        <>
          <p style={{ color: 'var(--ant-color-text-secondary)', marginBottom: 16, fontSize: 12 }}>
            以下为平台权限节点，勾选后该角色将拥有对应权限。
          </p>
          <Tree
            checkable
            checkedKeys={{ checked: checkedKeys, halfChecked: halfCheckedKeys }}
            onCheck={(keys) => {
              const k = keys as { checked?: React.Key[]; halfChecked?: React.Key[] };
              setCheckedKeys(Array.isArray(k) ? k : (k.checked || []));
              setHalfCheckedKeys(Array.isArray(k) ? [] : (k.halfChecked || []));
            }}
            treeData={treeData}
            defaultExpandAll
          />
        </>
      )}
    </Drawer>
  );
};

export default RolePermissionDrawer;
