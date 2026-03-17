import React, { useMemo, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  message,
  Popconfirm,
  Form,
  Input,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getPlatformRolePage,
  deletePlatformRole,
  type PlatformRoleQuery,
} from '../../api/role';
import type { Role } from '@/types/role';
import { PlatformAuthButton } from '../../components/PlatformAuthButton';
import PlatformRoleFormDrawer from './components/PlatformRoleFormDrawer';
import RolePermissionDrawer from './components/RolePermissionDrawer';

type RoleWithCount = Role & { permissionCount?: number; userCount?: number };

const PlatformRolesPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState<string | undefined>();
  const [formDrawerOpen, setFormDrawerOpen] = useState(false);
  const [permDrawerOpen, setPermDrawerOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [permRole, setPermRole] = useState<{ id: number; name: string } | null>(null);

  const queryParams: PlatformRoleQuery = useMemo(
    () => ({ page, size, roleName: keyword }),
    [page, size, keyword],
  );

  const { data, isLoading } = useQuery({
    queryKey: ['platform-roles', queryParams],
    queryFn: () => getPlatformRolePage(queryParams),
  });

  const deleteMutation = useMutation({
    mutationFn: deletePlatformRole,
    onSuccess: () => {
      message.success('删除成功');
      queryClient.invalidateQueries({ queryKey: ['platform-roles'] });
    },
  });

  const handleFilterChange = (_changed: Record<string, unknown>, all: Record<string, unknown>) => {
    setKeyword((all.roleName as string) || undefined);
    setPage(1);
  };

  const handleCreate = () => {
    setEditingRole(null);
    setFormDrawerOpen(true);
  };

  const handleEdit = (record: Role) => {
    setEditingRole(record);
    setFormDrawerOpen(true);
  };

  const handleConfigPermission = (record: Role) => {
    setPermRole({ id: record.id, name: record.roleName });
    setPermDrawerOpen(true);
  };

  const columns: ColumnsType<RoleWithCount> = [
    {
      title: '角色名称',
      dataIndex: 'roleName',
      key: 'roleName',
      width: 160,
      ellipsis: true,
    },
    {
      title: '角色标识',
      dataIndex: 'roleKey',
      key: 'roleKey',
      width: 180,
      ellipsis: true,
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      width: 200,
      ellipsis: true,
      render: (v) => v || '-',
    },
    {
      title: '权限数量',
      dataIndex: 'permissionCount',
      key: 'permissionCount',
      width: 120,
      render: (v: number) => v ?? '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 220,
      render: (_: unknown, record) => {
        const userCount = (record as RoleWithCount).userCount ?? 0;
        const canDelete = userCount === 0;
        return (
          <Space size={8}>
            <PlatformAuthButton permission="platform:role:edit">
              <Button type="link" size="small" onClick={() => handleEdit(record)}>
                编辑
              </Button>
            </PlatformAuthButton>
            <PlatformAuthButton permission="platform:role:edit">
              <Button type="link" size="small" onClick={() => handleConfigPermission(record)}>
                配置权限
              </Button>
            </PlatformAuthButton>
            <PlatformAuthButton permission="platform:role:remove">
              {canDelete ? (
                <Popconfirm
                  title="确定删除该角色？"
                  onConfirm={() => deleteMutation.mutate([record.id])}
                >
                  <Button type="link" size="small" danger>
                    删除
                  </Button>
                </Popconfirm>
              ) : (
                <Tooltip title={`该角色已有 ${userCount} 名用户，无法删除`}>
                  <span>
                    <Button type="link" size="small" danger disabled>
                      删除
                    </Button>
                  </span>
                </Tooltip>
              )}
            </PlatformAuthButton>
          </Space>
        );
      },
    },
  ];

  return (
    <Card title="平台角色" bodyStyle={{ padding: 0 }}>
      <div style={{ padding: 16, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
        <Form form={form} layout="inline" onValuesChange={handleFilterChange} style={{ flex: 'none' }}>
          <Form.Item name="roleName">
            <Input placeholder="角色名称" allowClear style={{ width: 180 }} />
          </Form.Item>
        </Form>
        <div style={{ flex: 'none' }}>
          <PlatformAuthButton permission="platform:role:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              新增角色
            </Button>
          </PlatformAuthButton>
        </div>
      </div>

      <Table<RoleWithCount>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={(data as any)?.records || []}
        scroll={{ x: 1000, y: 'calc(100vh - 320px)' }}
        pagination={{
          size: 'small',
          current: page,
          pageSize: size,
          total: (data as any)?.total ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100],
          showTotal: (total) => `共 ${total} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s || size);
          },
        }}
      />

      <PlatformRoleFormDrawer
        open={formDrawerOpen}
        onClose={() => { setFormDrawerOpen(false); setEditingRole(null); }}
        editing={editingRole}
      />

      <RolePermissionDrawer
        open={permDrawerOpen}
        onClose={() => { setPermDrawerOpen(false); setPermRole(null); }}
        roleId={permRole?.id ?? null}
        roleName={permRole?.name}
      />
    </Card>
  );
};

export default PlatformRolesPage;
