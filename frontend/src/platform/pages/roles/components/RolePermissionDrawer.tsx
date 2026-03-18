import React, { useEffect, useMemo, useState } from 'react';
import { Drawer, Tree, Button, message, Spin } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getRolePermissions, assignRolePermissions } from '../../../api/roleApi';
import { getPermissionTree } from '../../../api/permissionApi';
import { normalizeCheckedKeys, toCheckablePermissionTreeData, toGrantableCheckedIds } from '@/utils/permissionTree';

interface RolePermissionDrawerProps {
  open: boolean;
  onClose: () => void;
  roleId: number | null;
  roleName?: string;
}

const RolePermissionDrawer: React.FC<RolePermissionDrawerProps> = ({
  open,
  onClose,
  roleId,
  roleName,
}) => {
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  const queryClient = useQueryClient();

  const { data: allPerms, isLoading: loadingPerms } = useQuery({
    queryKey: ['platform-permissions-tree'],
    queryFn: getPermissionTree,
    enabled: open,
  });

  const { data: rolePermIds, isLoading: loadingRole } = useQuery({
    queryKey: ['platform-role-permissions', roleId],
    queryFn: () => getRolePermissions(roleId!),
    enabled: open && !!roleId,
  });

  const saveMutation = useMutation({
    mutationFn: (ids: number[]) => assignRolePermissions(roleId!, ids),
    onSuccess: () => {
      message.success('权限配置已保存');
      onClose();
      queryClient.invalidateQueries({ queryKey: ['platform-roles'] });
      queryClient.invalidateQueries({ queryKey: ['platform-role-permissions', roleId] });
    },
    onError: (err: any) => {
      message.error(err?.message || '保存失败');
    },
  });

  const allPermsList = useMemo(() => {
    if (!allPerms) return [];
    return Array.isArray(allPerms) ? allPerms : (allPerms as any).children || [];
  }, [allPerms]);

  const treeData = useMemo(() => toCheckablePermissionTreeData(allPermsList), [allPermsList]);

  useEffect(() => {
    if (open && rolePermIds && Array.isArray(rolePermIds)) {
      setCheckedKeys(rolePermIds.map(String));
    }
  }, [open, rolePermIds]);

  const handleSave = () => {
    saveMutation.mutate(toGrantableCheckedIds(allPermsList, checkedKeys));
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
          暂无权限节点
        </div>
      ) : (
        <>
          <p style={{ color: 'var(--ant-color-text-secondary)', marginBottom: 16, fontSize: 12 }}>
            勾选后该角色将拥有对应权限。
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

export default RolePermissionDrawer;
