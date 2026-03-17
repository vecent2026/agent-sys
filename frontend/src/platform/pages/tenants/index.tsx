import React, { useMemo, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Switch,
  message,
  Popconfirm,
  Form,
  Input,
  Select,
  Modal,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getTenantPage,
  deleteTenant,
  changeTenantStatus,
  type TenantVo,
  type TenantQueryParams,
} from '../../api/tenantApi';
import TenantFormDrawer from './components/TenantFormDrawer';
import TenantPermissionDrawer from './components/TenantPermissionDrawer';

const TenantPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState<string | undefined>();
  const [status, setStatus] = useState<number | undefined>();
  const [formDrawerOpen, setFormDrawerOpen] = useState(false);
  const [permDrawerOpen, setPermDrawerOpen] = useState(false);
  const [editingTenant, setEditingTenant] = useState<TenantVo | null>(null);
  const [permTenant, setPermTenant] = useState<{ id: number; name: string } | null>(null);

  const queryParams: TenantQueryParams = useMemo(
    () => ({ page, size, tenantName: keyword, status }),
    [page, size, keyword, status]
  );

  const { data, isLoading } = useQuery({
    queryKey: ['platform-tenants', queryParams],
    queryFn: () => getTenantPage(queryParams),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteTenant,
    onSuccess: () => {
      message.success('删除成功');
      queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
    },
    onError: (err: any) => {
      message.error(err?.message || '删除失败');
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, s }: { id: number; s: number }) => changeTenantStatus(id, s),
    onSuccess: () => {
      message.success('状态已更新');
      queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
    },
  });

  const handleFilterChange = (_: any, all: any) => {
    setKeyword(all.keyword || undefined);
    setStatus(all.status ?? undefined);
    setPage(1);
  };

  const handleStatusChange = (record: TenantVo, checked: boolean) => {
    if (!checked) {
      Modal.confirm({
        title: '确认禁用',
        content: `禁用租户「${record.tenantName}」后，该租户下所有在线用户将立即下线。确定继续？`,
        okType: 'danger',
        onOk: () => statusMutation.mutate({ id: record.id, s: 0 }),
      });
    } else {
      statusMutation.mutate({ id: record.id, s: 1 });
    }
  };

  const columns: ColumnsType<TenantVo> = [
    { title: '租户编码', dataIndex: 'tenantCode', width: 140, ellipsis: true },
    { title: '租户名称', dataIndex: 'tenantName', width: 160, ellipsis: true },
    { title: '联系人', dataIndex: 'contactName', width: 100, render: (v) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (val, record) => (
        <Switch
          checked={val === 1}
          size="small"
          onChange={(checked) => handleStatusChange(record, checked)}
          loading={statusMutation.isPending}
        />
      ),
    },
    {
      title: '到期时间',
      dataIndex: 'expireTime',
      width: 160,
      render: (val) =>
        val ? dayjs(val).format('YYYY-MM-DD HH:mm') : <Tag color="green">永不过期</Tag>,
    },
    { title: '最大用户数', dataIndex: 'maxUsers', width: 100, render: (v) => v ?? '不限' },
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
      width: 200,
      render: (_, record) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditingTenant(record);
              setFormDrawerOpen(true);
            }}
          >
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setPermTenant({ id: record.id, name: record.tenantName });
              setPermDrawerOpen(true);
            }}
          >
            配置权限
          </Button>
          <Popconfirm
            title="确定删除该租户？此操作不可恢复"
            onConfirm={() => deleteMutation.mutate(record.id)}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const records = (data as any)?.records || [];
  const total = (data as any)?.total ?? 0;

  return (
    <Card title="租户管理" bodyStyle={{ padding: 0 }}>
      <div style={{ padding: 16, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
        <Form form={form} layout="inline" onValuesChange={handleFilterChange}>
          <Form.Item name="keyword">
            <Input placeholder="租户名称/编码" allowClear style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="status">
            <Select placeholder="全部状态" allowClear style={{ width: 120 }}>
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
        </Form>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setEditingTenant(null);
            setFormDrawerOpen(true);
          }}
        >
          新增租户
        </Button>
      </div>

      <Table<TenantVo>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={records}
        scroll={{ x: 1100, y: 'calc(100vh - 320px)' }}
        pagination={{
          size: 'small',
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100],
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s || size);
          },
        }}
      />

      <TenantFormDrawer
        open={formDrawerOpen}
        onClose={() => {
          setFormDrawerOpen(false);
          setEditingTenant(null);
        }}
        editing={editingTenant}
      />

      <TenantPermissionDrawer
        open={permDrawerOpen}
        onClose={() => {
          setPermDrawerOpen(false);
          setPermTenant(null);
        }}
        tenantId={permTenant?.id ?? null}
        tenantName={permTenant?.name}
      />
    </Card>
  );
};

export default TenantPage;
