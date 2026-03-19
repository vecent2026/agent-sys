import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Switch, message, Popconfirm, Space, Drawer, Tag, Pagination } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getUserList, createUser, deleteUser, updateUserStatus, getUserRoles, assignUserRoles } from '@/api/user';
import { getAllRoles } from '@/api/role';
import { PageContainer } from '@/components/PageContainer';
import { AuthButton } from '@/components/AuthButton';
import { TablePageLayout } from '@/design-system/components/TablePageLayout';
import { PrimaryButton } from '@/design-system/components/Buttons';
import { designTokens } from '@/design-system/theme';
import type { UserInfo, UserForm } from '@/types/user';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { debounce } from 'lodash-es';

const UserList: React.FC = () => {
  const [form] = Form.useForm();
  const [roleForm] = Form.useForm();
  const queryClient = useQueryClient();
  const [visible, setVisible] = useState(false);
  const [roleVisible, setRoleVisible] = useState(false);
  const [currentUser, setCurrentUser] = useState<UserInfo | null>(null);

  const [pagination, setPagination] = useState({ current: 1, pageSize: 20 });
  const [searchParams, setSearchParams] = useState<{username?: string; mobile?: string; status?: number}>({});
  const SUPER_ROLE_KEYS = useMemo(() => new Set(['tenant_admin', 'tenant_super_admin', 'super_admin']), []);

  const { data: userData, isLoading } = useQuery({
    queryKey: ['users', pagination, searchParams],
    queryFn: () => getUserList({ page: pagination.current, size: pagination.pageSize, ...searchParams }),
    staleTime: 0,
  });

  const { data: roleData } = useQuery({
    queryKey: ['roles_all'],
    queryFn: getAllRoles,
    enabled: visible || roleVisible,
  });

  const { data: userRoleIds } = useQuery({
    queryKey: ['userRoles', currentUser?.id],
    queryFn: () => getUserRoles(currentUser!.id),
    enabled: !!currentUser && roleVisible,
  });

  const superRoleIds = useMemo(
    () =>
      (roleData || [])
        .filter((role: any) => role?.isSuper === 1 || SUPER_ROLE_KEYS.has(role?.roleKey))
        .map((role: any) => Number(role.id)),
    [roleData, SUPER_ROLE_KEYS]
  );

  const createMutation = useMutation({
    mutationFn: (data: UserForm) => createUser(data),
    onSuccess: () => {
      message.success('新增成员成功');
      setVisible(false);
      form.resetFields();
      setPagination(prev => ({ ...prev, current: 1 }));
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (error: any) => message.error(error?.response?.data?.message || error?.message || '新增成员失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteUser,
    onSuccess: () => {
      message.success('移除成功');
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (error: any) => message.error(error?.response?.data?.message || error?.message || '移除失败'),
  });

  const statusMutation = useMutation({
    mutationFn: (data: { id: number; status: 0 | 1 }) => updateUserStatus(data.id, data.status),
    onSuccess: () => {
      message.success('状态更新成功');
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (error: any) => message.error(error?.response?.data?.message || error?.message || '状态更新失败'),
  });

  const assignRoleMutation = useMutation({
    mutationFn: (data: { id: number; roleIds: number[] }) => assignUserRoles(data.id, data.roleIds),
    onSuccess: () => {
      message.success('角色分配成功');
      setRoleVisible(false);
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (error: any) => message.error(error?.response?.data?.message || error?.message || '角色分配失败'),
  });

  const handleAdd = () => {
    form.resetFields();
    form.setFieldsValue({ status: 1 });
    setVisible(true);
  };

  const handleDelete = (id: number) => deleteMutation.mutate([id]);

  const handleStatusChange = (id: number, checked: boolean) => {
    statusMutation.mutate({ id, status: checked ? 1 : 0 });
  };

  const handleAssignRole = (record: UserInfo) => {
    setCurrentUser(record);
    setRoleVisible(true);
  };

  useEffect(() => {
    if (userRoleIds) {
      roleForm.setFieldsValue({ roleIds: userRoleIds });
    } else {
      roleForm.resetFields();
    }
  }, [userRoleIds, roleForm]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      createMutation.mutate(values);
    } catch (error) {
      console.error('Form validation error:', error);
      message.error('表单验证失败');
    }
  };

  const applySearchParams = useCallback((values: Record<string, unknown>) => {
    setSearchParams(values);
    setPagination((prev) => ({ ...prev, current: 1 }));
  }, []);

  const debouncedApply = useMemo(() => debounce(applySearchParams, 300), [applySearchParams]);

  useEffect(() => () => debouncedApply.cancel(), [debouncedApply]);

  const handleFilterChange = useCallback(
    (changedValues: Record<string, unknown>, allValues: Record<string, unknown>) => {
      const params = {
        username: allValues.username,
        mobile: allValues.mobile,
        status: allValues.status,
      };
      if ('username' in changedValues || 'mobile' in changedValues) {
        debouncedApply(params);
      } else {
        debouncedApply.cancel();
        applySearchParams(params);
      }
    },
    [applySearchParams, debouncedApply]
  );

  const handleRoleSubmit = async () => {
    try {
      const values = await roleForm.validateFields();
      if (currentUser) {
        const nextRoleIds: number[] = (values.roleIds || []).map(Number);
        const currentRoleIdList: number[] = (userRoleIds || currentUser.roleIds || []).map(Number);
        const hadSuperRole = currentRoleIdList.some((id) => superRoleIds.includes(id));
        const willKeepSuperRole = nextRoleIds.some((id) => superRoleIds.includes(id));
        const canComputeSuperCount = (userData?.records || []).every((u) => Array.isArray(u.roleIds));
        const currentSuperAdminCount = canComputeSuperCount
          ? (userData?.records || []).filter((u) => (u.roleIds || []).some((id) => superRoleIds.includes(Number(id)))).length
          : undefined;

        if (hadSuperRole && !willKeepSuperRole && currentSuperAdminCount === 1) {
          message.warning('当前租户仅剩最后一名超管，不能移除其超管角色。请先为其他成员分配超管角色。');
          return;
        }

        assignRoleMutation.mutate({ id: currentUser.id, roleIds: values.roleIds });
      }
    } catch (error) {
      console.error(error);
    }
  };

  const columns: ColumnsType<UserInfo> = [
    {
      title: '昵称',
      dataIndex: 'nickname',
      key: 'nickname',
      width: 180,
      fixed: 'left',
      ellipsis: true,
    },
    {
      title: '手机号',
      dataIndex: 'mobile',
      key: 'mobile',
      width: 150,
    },
    {
      title: '角色',
      dataIndex: 'roleNames',
      key: 'roleNames',
      width: 220,
      render: (roleNames: string[]) => (
        <Space wrap>
          {roleNames?.map((roleName: string) => (
            <Tag key={roleName} color="blue">{roleName}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status, record) => (
        <Switch
          checked={status === 1}
          onChange={(checked) => handleStatusChange(record.id, checked)}
          loading={statusMutation.isPending && statusMutation.variables?.id === record.id}
        />
      ),
    },
    {
      title: '加入时间',
      dataIndex: 'joinTime',
      key: 'joinTime',
      width: 180,
      render: (text) => text ? dayjs(text).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      fixed: 'right',
      align: 'right',
      render: (_, record) => (
        <Space size="small">
          <AuthButton perm="tenant:member:role">
            <Button type="link" size="small" onClick={() => handleAssignRole(record)}>授权</Button>
          </AuthButton>
          <AuthButton perm="tenant:member:remove">
            <Popconfirm title="确定移除该成员吗？" onConfirm={() => handleDelete(record.id)}>
              <Button type="link" size="small" danger>移除</Button>
            </Popconfirm>
          </AuthButton>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer>
      <TablePageLayout
        toolbar={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16 }}>
            <div>
              <AuthButton perm="tenant:member:add">
                <PrimaryButton icon={<PlusOutlined />} onClick={handleAdd}>新增成员</PrimaryButton>
              </AuthButton>
            </div>
            <Form
              layout="inline"
              style={{ rowGap: 16 }}
              onValuesChange={handleFilterChange}
              initialValues={{ username: searchParams.username, mobile: searchParams.mobile, status: searchParams.status }}
            >
              <Form.Item name="username" label="昵称">
                <Input placeholder="请输入昵称" allowClear />
              </Form.Item>
              <Form.Item name="mobile" label="手机号">
                <Input placeholder="请输入手机号" allowClear />
              </Form.Item>
              <Form.Item name="status" label="状态">
                <Select placeholder="请选择" allowClear style={{ width: 120 }}>
                  <Select.Option value={1}>启用</Select.Option>
                  <Select.Option value={0}>禁用</Select.Option>
                </Select>
              </Form.Item>
            </Form>
          </div>
        }
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Pagination
              size="small"
              current={pagination.current}
              pageSize={pagination.pageSize}
              total={userData?.total}
              showSizeChanger
              showQuickJumper
              pageSizeOptions={[20, 50, 100]}
              showTotal={(total) => `共 ${total} 条`}
              onChange={(page, pageSize) =>
                setPagination((prev) => ({
                  current: pageSize && pageSize !== prev.pageSize ? 1 : page,
                  pageSize: pageSize || prev.pageSize,
                }))
              }
            />
          </div>
        }
      >
        <Table
          columns={columns}
          dataSource={userData?.records}
          rowKey="id"
          loading={isLoading}
          size="small"
          onRow={() => ({ style: { height: 40 } })}
          scroll={{ x: 'max-content', y: 'calc(100vh - 360px)' }}
          sticky
          pagination={false}
        />
      </TablePageLayout>

      <Drawer
        title="新增成员"
        open={visible}
        onClose={() => setVisible(false)}
        size="large"
        destroyOnClose
        footer={
          <div style={{ textAlign: 'right', borderTop: `1px solid ${designTokens.colorBorder}`, paddingTop: 16 }}>
            <Space>
              <Button onClick={() => setVisible(false)}>取消</Button>
              <PrimaryButton onClick={handleSubmit} loading={createMutation.isPending}>确定</PrimaryButton>
            </Space>
          </div>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="mobile"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' }
            ]}
          >
            <Input placeholder="请输入手机号" />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[
              { required: true, message: '请输入初始密码' },
              { min: 6, max: 20, message: '长度6-20字符' },
              { pattern: /^(?=.*[a-zA-Z])(?=.*\d).+$/, message: '必须包含字母和数字' }
            ]}
          >
            <Input.Password placeholder="请输入初始密码" />
          </Form.Item>
          <Form.Item
            name="nickname"
            label="昵称"
            rules={[{ required: true, message: '请输入昵称' }, { min: 2, max: 20, message: '昵称长度必须在2-20个字符之间' }]}
          >
            <Input placeholder="请输入昵称" />
          </Form.Item>
          <Form.Item name="roleIds" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select mode="multiple" placeholder="请选择角色" options={roleData?.map(role => ({ label: role.roleName, value: role.id }))} />
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        title={`分配角色 - ${currentUser?.nickname || currentUser?.mobile || currentUser?.id}`}
        open={roleVisible}
        onOk={handleRoleSubmit}
        onCancel={() => setRoleVisible(false)}
        confirmLoading={assignRoleMutation.isPending}
        forceRender
      >
        <Form form={roleForm} layout="vertical">
          <Form.Item name="roleIds" label="选择角色">
            <Select
              mode="multiple"
              placeholder="请选择角色"
              options={roleData?.map(role => ({ label: role.roleName, value: role.id }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default UserList;
