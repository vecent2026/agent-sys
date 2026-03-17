import React, { useMemo, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  message,
  Modal,
  Form,
  Input,
  Select,
  Popconfirm,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getPlatformUserPage,
  updatePlatformUserStatus,
  resetPlatformUserPassword,
  deletePlatformUser,
  type PlatformUserQuery,
} from '../../api/user';
import type { UserInfo } from '@/types/user';
import { PlatformAuthButton } from '../../components/PlatformAuthButton';
import PlatformUserFormDrawer from './components/PlatformUserFormDrawer';

const PlatformUsersPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [searchForm] = Form.useForm();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState<string | undefined>();
  const [status, setStatus] = useState<number | undefined>();
  const [formDrawerOpen, setFormDrawerOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserInfo | null>(null);
  const [resetPwdUser, setResetPwdUser] = useState<UserInfo | null>(null);
  const [resetPwdForm] = Form.useForm();

  const queryParams = useMemo(
    () => ({ page, size, username: keyword, status } as PlatformUserQuery),
    [page, size, keyword, status],
  );

  const { data, isLoading } = useQuery({
    queryKey: ['platform-users', queryParams],
    queryFn: () => getPlatformUserPage(queryParams),
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: 0 | 1 }) =>
      updatePlatformUserStatus(id, status),
    onSuccess: () => {
      message.success('状态更新成功');
      queryClient.invalidateQueries({ queryKey: ['platform-users'] });
    },
  });

  const resetPwdMutation = useMutation({
    mutationFn: ({ id, password }: { id: number; password?: string }) =>
      resetPlatformUserPassword(id, password),
    onSuccess: () => {
      message.success('密码已重置');
      setResetPwdUser(null);
      resetPwdForm.resetFields();
      queryClient.invalidateQueries({ queryKey: ['platform-users'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deletePlatformUser,
    onSuccess: () => {
      message.success('删除成功');
      queryClient.invalidateQueries({ queryKey: ['platform-users'] });
    },
  });

  const handleFilterChange = (_changed: Record<string, unknown>, all: Record<string, unknown>) => {
    setKeyword((all.keyword as string) || undefined);
    setStatus(all.status as number | undefined);
    setPage(1);
  };

  const handleCreate = () => {
    setEditingUser(null);
    setFormDrawerOpen(true);
  };

  const handleEdit = (record: UserInfo) => {
    setEditingUser(record);
    setFormDrawerOpen(true);
  };

  const handleResetPwd = (record: UserInfo) => {
    setResetPwdUser(record);
  };

  const handleResetPwdSubmit = () => {
    if (!resetPwdUser) return;
    resetPwdForm.validateFields().then((values) => {
      resetPwdMutation.mutate({
        id: resetPwdUser.id,
        password: values.password || undefined,
      });
    });
  };

  const columns: ColumnsType<UserInfo> = [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      width: 140,
      ellipsis: true,
    },
    {
      title: '昵称',
      dataIndex: 'nickname',
      key: 'nickname',
      width: 120,
      ellipsis: true,
    },
    {
      title: '手机号',
      dataIndex: 'mobile',
      key: 'mobile',
      width: 130,
      ellipsis: true,
      render: (v) => v || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (val: number) => (
        <Tag color={val === 1 ? 'success' : 'default'}>
          {val === 1 ? '启用' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '角色',
      dataIndex: 'roleNames',
      key: 'roleNames',
      width: 140,
      ellipsis: true,
      render: (roleNames: string[] | undefined) =>
        roleNames?.length ? roleNames.join(', ') : '-',
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginTime',
      key: 'lastLoginTime',
      width: 180,
      render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
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
      width: 240,
      render: (_: unknown, record) => (
        <Space size={8}>
          <PlatformAuthButton permission="platform:user:edit">
            <Button type="link" size="small" onClick={() => handleEdit(record)}>
              编辑
            </Button>
          </PlatformAuthButton>
          <PlatformAuthButton permission="platform:user:reset">
            <Button type="link" size="small" onClick={() => handleResetPwd(record)}>
              重置密码
            </Button>
          </PlatformAuthButton>
          <PlatformAuthButton permission="platform:user:edit">
            <Popconfirm
              title={record.status === 1 ? '确定禁用？' : '确定启用？'}
              onConfirm={() =>
                statusMutation.mutate({
                  id: record.id,
                  status: record.status === 1 ? 0 : 1,
                })
              }
            >
              <Button type="link" size="small">
                {record.status === 1 ? '禁用' : '启用'}
              </Button>
            </Popconfirm>
          </PlatformAuthButton>
          <PlatformAuthButton permission="platform:user:remove">
            <Popconfirm
              title="确定删除该用户？"
              onConfirm={() => deleteMutation.mutate([record.id])}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </PlatformAuthButton>
        </Space>
      ),
    },
  ];

  return (
    <Card title="平台用户" bodyStyle={{ padding: 0 }}>
      <div style={{ padding: 16, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
        <Form form={searchForm} layout="inline" onValuesChange={handleFilterChange} style={{ flex: 'none' }}>
          <Form.Item name="keyword">
            <Input placeholder="用户名/手机号" allowClear style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="status">
            <Select placeholder="状态" allowClear style={{ width: 100 }}>
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
        </Form>
        <div style={{ flex: 'none' }}>
          <PlatformAuthButton permission="platform:user:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              新增用户
            </Button>
          </PlatformAuthButton>
        </div>
      </div>

      <Table<UserInfo>
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

      <PlatformUserFormDrawer
        open={formDrawerOpen}
        onClose={() => { setFormDrawerOpen(false); setEditingUser(null); }}
        editing={editingUser}
      />

      <Modal
        title="重置密码"
        open={!!resetPwdUser}
        onCancel={() => { setResetPwdUser(null); resetPwdForm.resetFields(); }}
        onOk={handleResetPwdSubmit}
        confirmLoading={resetPwdMutation.isPending}
      >
        <Form form={resetPwdForm} layout="vertical">
          <Form.Item name="password" label="新密码（留空则系统生成）">
            <Input.Password placeholder="6-20位，留空由系统生成" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default PlatformUsersPage;
