import React, { useEffect, useMemo, useState } from 'react';
import { Drawer, Tree, Button, message, Spin } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getPlatformTenantPermissionIds,
  updatePlatformTenantPermissionIds,
} from '../../../api/tenant';
import { getPlatformPermissionTree, type PlatformPermission } from '../../../api/permission';

interface TenantPermissionDrawerProps {
  open: boolean;
  onClose: () => void;
  tenantId: number | null;
  tenantName?: string;
}

function filterTenantScopeNodes(nodes: PlatformPermission[]): PlatformPermission[] {
  const result: PlatformPermission[] = [];
  for (const node of nodes) {
    const scope = (node as any).scope;
    const isTenant = scope === 'tenant' || node.permissionKey?.startsWith('tenant:');
    const child = { ...node };
    if (child.children?.length) {
      child.children = filterTenantScopeNodes(child.children);
      if (child.children.length > 0) result.push(child);
    } else if (isTenant) {
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

function toTreeData(nodes: PlatformPermission[], checkedIds: Set<number>): DataNode[] {
  return nodes.map((n) => {
    const hasKey = !!n.permissionKey;
    const children = n.children?.length ? toTreeData(n.children, checkedIds) : undefined;
    return {
      key: String(n.id),
      title: n.name || n.permissionKey || `节点${n.id}`,
      children: children?.length ? children : undefined,
      checkable: hasKey,
      disabled: !hasKey,
      isLeaf: !children?.length && hasKey,
    };
  });
}

const TenantPermissionDrawer: React.FC<TenantPermissionDrawerProps> = ({
  open,
  onClose,
  tenantId,
  tenantName,
}) => {
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  const [halfCheckedKeys, setHalfCheckedKeys] = useState<React.Key[]>([]);
  const queryClient = useQueryClient();

  const { data: allPerms, isLoading: loadingPerms } = useQuery({
    queryKey: ['platform-permissions-tree'],
    queryFn: getPlatformPermissionTree,
    enabled: open,
  });

  const { data: tenantPermIds, isLoading: loadingTenant } = useQuery({
    queryKey: ['platform-tenant-permissions', tenantId],
    queryFn: () => getPlatformTenantPermissionIds(tenantId!),
    enabled: open && !!tenantId,
  });

  const updateMutation = useMutation({
    mutationFn: (ids: number[]) => updatePlatformTenantPermissionIds(tenantId!, ids),
    onSuccess: () => {
      message.success('权限配置已保存');
      onClose();
      queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
      queryClient.invalidateQueries({ queryKey: ['platform-tenant-permissions', tenantId] });
    },
  });

  const tenantScopeTree = useMemo(() => {
    if (!allPerms) return [];
    const list = Array.isArray(allPerms) ? allPerms : (allPerms as any).children || [];
    return filterTenantScopeNodes(list);
  }, [allPerms]);

  const treeData = useMemo(
    () => toTreeData(tenantScopeTree, new Set(tenantPermIds || [])),
    [tenantScopeTree]
  );

  useEffect(() => {
    if (open && tenantPermIds && Array.isArray(tenantPermIds)) {
      setCheckedKeys(tenantPermIds.map(String));
    }
  }, [open, tenantPermIds]);

  const handleSave = () => {
    const allCheckedSet = new Set([...checkedKeys, ...halfCheckedKeys].map(String));
    const leafIds = collectLeafPermissionIds(tenantScopeTree).filter((id) =>
      allCheckedSet.has(String(id))
    );
    updateMutation.mutate(leafIds);
  };

  const loading = loadingPerms || loadingTenant;

  return (
    <Drawer
      title={`配置权限 — ${tenantName || '租户'}`}
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
            以下权限节点为可授权给租户的范围，勾选后该租户可在角色管理中分配这些权限。
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

export default TenantPermissionDrawer;
