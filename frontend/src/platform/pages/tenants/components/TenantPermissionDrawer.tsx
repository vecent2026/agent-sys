import React, { useEffect, useMemo, useState } from 'react';
import { Drawer, Tree, Button, message, Spin } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getTenantPermissions, updateTenantPermissions } from '../../../api/tenantApi';
import { getPermissionTree, type PermissionVo } from '../../../api/permissionApi';
import { normalizeCheckedKeys, toCheckablePermissionTreeData, toGrantableCheckedIds } from '@/utils/permissionTree';

interface TenantPermissionDrawerProps {
  open: boolean;
  onClose: () => void;
  tenantId: number | null;
  tenantName?: string;
}

function filterTenantScopeNodes(nodes: PermissionVo[]): PermissionVo[] {
  const result: PermissionVo[] = [];
  for (const node of nodes) {
    // 优先用 scope 字段；后端未升级时用 permissionKey 前缀兜底
    const isTenant =
      node.scope === 'tenant' ||
      (node.scope == null && !!node.permissionKey && node.permissionKey.startsWith('app:'));
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

const TenantPermissionDrawer: React.FC<TenantPermissionDrawerProps> = ({
  open,
  onClose,
  tenantId,
  tenantName,
}) => {
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  const queryClient = useQueryClient();

  const { data: allPerms, isLoading: loadingPerms } = useQuery({
    queryKey: ['platform-permissions-tree'],
    queryFn: getPermissionTree,
    enabled: open,
  });

  const { data: tenantPermIds, isLoading: loadingTenant } = useQuery({
    queryKey: ['tenant-permissions', tenantId],
    queryFn: () => getTenantPermissions(tenantId!),
    enabled: open && !!tenantId,
  });

  const saveMutation = useMutation({
    mutationFn: (ids: number[]) => updateTenantPermissions(tenantId!, ids),
    onSuccess: () => {
      message.success('权限配置已保存');
      onClose();
      queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
      queryClient.invalidateQueries({ queryKey: ['tenant-permissions', tenantId] });
    },
    onError: (err: any) => {
      message.error(err?.message || '保存失败');
    },
  });

  const tenantScopeTree = useMemo(() => {
    if (!allPerms) return [];
    const list = Array.isArray(allPerms) ? allPerms : (allPerms as any).children || [];
    return filterTenantScopeNodes(list);
  }, [allPerms]);

  const treeData = useMemo(() => toCheckablePermissionTreeData(tenantScopeTree), [tenantScopeTree]);

  useEffect(() => {
    if (open && tenantPermIds && Array.isArray(tenantPermIds)) {
      setCheckedKeys(tenantPermIds.map(String));
    }
  }, [open, tenantPermIds]);

  const handleSave = () => {
    saveMutation.mutate(toGrantableCheckedIds(tenantScopeTree, checkedKeys));
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
          <Button type="primary" onClick={handleSave} loading={saveMutation.isPending}>
            保存
          </Button>
        </div>
      }
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : treeData.length === 0 ? (
        <div style={{ color: 'var(--ant-color-text-secondary)', textAlign: 'center', padding: 48 }}>
          暂无可配置的租户权限节点
        </div>
      ) : (
        <>
          <p style={{ color: 'var(--ant-color-text-secondary)', marginBottom: 16, fontSize: 12 }}>
            以下为可授权给租户的权限节点。勾选后，该租户的角色管理中可分配这些权限。
          </p>
          <Tree
            checkable
            checkedKeys={checkedKeys}
            onCheck={(keys) => {
              setCheckedKeys(normalizeCheckedKeys(keys as any));
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
