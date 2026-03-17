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
import { getRolePage, deleteRoles, type RoleVo } from '../../api/roleApi';
import PlatformRoleFormDrawer from './components/PlatformRoleFormDrawer';
import RolePermissionDrawer from './components/RolePermissionDrawer';

const PlatformRolesPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState<string | undefined>();
  const [formDrawerOpen, setFormDrawerOpen] = useState(false);
  const [permDrawerOpen, setPermDrawerOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<RoleVo | null>(null);
  const [permRole, setPermRole] = useState<{ id: number; name: string } | null>(null);

  const queryParams = useMemo(() => ({ page, size, roleName: keyword }), [page, size, keyword]);

  const { data, isLoading } = useQuery({
    queryKey: ['platform-roles', queryParams],
    queryFn: () => getRolePage(queryParams),
  });

  const deleteMutation = useMutation({
    mutationFn: (ids: number[]) => deleteRoles(ids),
    onSuccess: () => {
      message.success('删除成功');
      queryClient.invalidateQueries({ queryKey: ['platform-roles'] });
    },
    onError: (err: any) => {
      message.error(err?.message || '删除失败');
    },
  });

  const handleFilterChange = (_: any, all: any) => {
    setKeyword(all.roleName || undefined);
    setPage(1);
  };

  const columns: ColumnsType<RoleVo> = [
    { title: '角色名称', dataIndex: 'roleName', width: 160, ellipsis: true },
    { title: '角色标识', dataIndex: 'roleKey', width: 180, ellipsis: true },
    {
      title: '描述',
      dataIndex: 'description',
      width: 220,
      ellipsis: true,
      render: (v) => v || '-',
    },
    { title: '用户数', dataIndex: 'userCount', width: 90, render: (v) => v ?? 0 },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 220,
      render: (_, record) => {
        const canDelete = !record.userCount || record.userCount === 0;
        return (
          <Space size={4}>
            <Button
              type="link"
              size="small"
              onClick={() => {
                setEditingRole(record);
                setFormDrawerOpen(true);
              }}
            >
              编辑
            </Button>
            <Button
              type="link"
              size="small"
              onClick={() => {
                setPermRole({ id: record.id, name: record.roleName });
                setPermDrawerOpen(true);
              }}
            >
              配置权限
            </Button>
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
              <Tooltip title={`该角色已有 ${record.userCount} 名用户，无法删除`}>
                <Button type="link" size="small" danger disabled>
                  删除
                </Button>
              </Tooltip>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <Card title="平台角色" bodyStyle={{ padding: 0 }}>
      <div style={{ padding: 16, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
        <Form form={form} layout="inline" onValuesChange={handleFilterChange}>
          <Form.Item name="roleName">
            <Input placeholder="角色名称" allowClear style={{ width: 180 }} />
          </Form.Item>
        </Form>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setEditingRole(null);
            setFormDrawerOpen(true);
          }}
        >
          新增角色
        </Button>
      </div>

      <Table<RoleVo>
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
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s || size);
          },
        }}
      />

      <PlatformRoleFormDrawer
        open={formDrawerOpen}
        onClose={() => {
          setFormDrawerOpen(false);
          setEditingRole(null);
        }}
        editing={editingRole}
      />

      <RolePermissionDrawer
        open={permDrawerOpen}
        onClose={() => {
          setPermDrawerOpen(false);
          setPermRole(null);
        }}
        roleId={permRole?.id ?? null}
        roleName={permRole?.name}
      />
    </Card>
  );
};

export default PlatformRolesPage;
