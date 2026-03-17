import React, { useMemo, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Switch,
  message,
  Modal,
  Form,
  Input,
  Select,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getPlatformTenantPage,
  updatePlatformTenantStatus,
  type PlatformTenant,
  type PlatformTenantQuery,
} from '../../api/tenant';
import { PlatformAuthButton } from '../../components/PlatformAuthButton';
import TenantFormDrawer from './components/TenantFormDrawer';
import TenantPermissionDrawer from './components/TenantPermissionDrawer';

const PlatformTenantPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState<string | undefined>();
  const [status, setStatus] = useState<number | undefined>();
  const [formDrawerOpen, setFormDrawerOpen] = useState(false);
  const [permDrawerOpen, setPermDrawerOpen] = useState(false);
  const [editingTenant, setEditingTenant] = useState<PlatformTenant | null>(null);
  const [permTenant, setPermTenant] = useState<{ id: number; name: string } | null>(null);

  const queryParams: PlatformTenantQuery = useMemo(
    () => ({ page, size, keyword, status }),
    [page, size, keyword, status],
  );

  const { data, isLoading } = useQuery({
    queryKey: ['platform-tenants', queryParams],
    queryFn: () => getPlatformTenantPage(queryParams),
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, nextStatus }: { id: number; nextStatus: 0 | 1 }) =>
      updatePlatformTenantStatus(id, nextStatus),
    onSuccess: () => {
      message.success('租户状态更新成功');
      queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
    },
  });

  const handleToggleStatus = (record: PlatformTenant) => {
    const nextStatus: 0 | 1 = record.status === 1 ? 0 : 1;
    if (nextStatus === 0) {
      Modal.confirm({
        title: '禁用租户',
        content: '禁用后，该租户所有成员将立即被踢出登录，确认操作？',
        okText: '确认禁用',
        okButtonProps: { danger: true },
        cancelText: '取消',
        onOk: () => statusMutation.mutate({ id: record.id, nextStatus }),
      });
    } else {
      statusMutation.mutate({ id: record.id, nextStatus });
    }
  };

  const handleFilterChange = (_changed: Record<string, unknown>, all: Record<string, unknown>) => {
    setKeyword((all.keyword as string) || undefined);
    setStatus(all.status as number | undefined);
    setPage(1);
  };

  const handleCreate = () => {
    setEditingTenant(null);
    setFormDrawerOpen(true);
  };

  const handleEdit = (record: PlatformTenant) => {
    setEditingTenant(record);
    setFormDrawerOpen(true);
  };

  const handleConfigPermission = (record: PlatformTenant) => {
    setPermTenant({ id: record.id, name: record.tenantName });
    setPermDrawerOpen(true);
  };

  const columns: ColumnsType<PlatformTenant> = [
    {
      title: '租户名称',
      dataIndex: 'tenantName',
      key: 'tenantName',
      width: 200,
      ellipsis: true,
    },
    {
      title: '租户编码',
      dataIndex: 'tenantCode',
      key: 'tenantCode',
      width: 160,
      ellipsis: true,
    },
    {
      title: '联系人',
      dataIndex: 'contactName',
      key: 'contactName',
      width: 140,
      ellipsis: true,
      render: (val: string, record) => val || record.contactPhone || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (val: number) => (
        <Tag color={val === 1 ? 'success' : 'default'}>
          {val === 1 ? '启用' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '到期时间',
      dataIndex: 'expireTime',
      key: 'expireTime',
      width: 180,
      render: (val?: string | null) =>
        val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '永久',
    },
    {
      title: '最大用户数',
      dataIndex: 'maxUsers',
      key: 'maxUsers',
      width: 140,
      render: (val?: number | null) => (val == null ? '不限' : val),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (val: string) => dayjs(val).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 260,
      render: (_: unknown, record) => (
        <Space size={8}>
          <PlatformAuthButton permission="platform:tenant:edit">
            <Button type="link" size="small" onClick={() => handleEdit(record)}>
              编辑
            </Button>
          </PlatformAuthButton>
          <PlatformAuthButton permission="platform:tenant:permission">
            <Button type="link" size="small" onClick={() => handleConfigPermission(record)}>
              配置权限
            </Button>
          </PlatformAuthButton>
          <PlatformAuthButton permission="platform:tenant:disable">
            <Switch
              size="small"
              checked={record.status === 1}
              checkedChildren="启用"
              unCheckedChildren="禁用"
              onChange={() => handleToggleStatus(record)}
              loading={statusMutation.isPending}
            />
          </PlatformAuthButton>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="租户管理"
      bodyStyle={{ padding: 0 }}
    >
      <div style={{ padding: 16, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
        <Form form={form} layout="inline" onValuesChange={handleFilterChange} style={{ flex: 'none' }}>
          <Form.Item name="keyword">
            <Input
              placeholder="租户名称/编码"
              allowClear
              style={{ width: 180 }}
            />
          </Form.Item>
          <Form.Item name="status">
            <Select placeholder="状态" allowClear style={{ width: 100 }}>
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
        </Form>
        <div style={{ flex: 'none' }}>
          <PlatformAuthButton permission="platform:tenant:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              创建租户
            </Button>
          </PlatformAuthButton>
        </div>
      </div>

      <Table<PlatformTenant>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={(data as any)?.records || []}
        scroll={{ x: 1100, y: 'calc(100vh - 320px)' }}
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

      <TenantFormDrawer
        open={formDrawerOpen}
        onClose={() => { setFormDrawerOpen(false); setEditingTenant(null); }}
        editing={editingTenant}
      />

      <TenantPermissionDrawer
        open={permDrawerOpen}
        onClose={() => { setPermDrawerOpen(false); setPermTenant(null); }}
        tenantId={permTenant?.id ?? null}
        tenantName={permTenant?.name}
      />
    </Card>
  );
};

export default PlatformTenantPage;
