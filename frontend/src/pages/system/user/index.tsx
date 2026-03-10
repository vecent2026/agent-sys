import React, { useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Switch, message, Popconfirm, Space, Drawer, Tag } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getUserList, createUser, updateUser, deleteUser, resetUserPassword, updateUserStatus, getUserRoles, assignUserRoles } from '@/api/user';
import { getAllRoles } from '@/api/role';
import { PageContainer } from '@/components/PageContainer';
import { AuthButton } from '@/components/AuthButton';
import type { UserInfo, UserForm } from '@/types/user';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';

const UserList: React.FC = () => {
  const [form] = Form.useForm();
  const [roleForm] = Form.useForm();
  const [passwordForm] = Form.useForm();
  const queryClient = useQueryClient();
  const [visible, setVisible] = useState(false);
  const [roleVisible, setRoleVisible] = useState(false);
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [currentUser, setCurrentUser] = useState<UserInfo | null>(null);
  
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
  });
  const [searchParams, setSearchParams] = useState<{username?: string; mobile?: string; status?: number}>({});

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

  const createMutation = useMutation({
    mutationFn: (data: UserForm) => createUser(data),
    onSuccess: () => {
      message.success('创建成功');
      setVisible(false);
      form.resetFields();
      setPagination(prev => ({ ...prev, current: 1 }));
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (error: any) => {
      message.error(error.message || '创建失败');
      console.error('创建用户失败:', error);
      if (error.response) {
        console.error('Error response:', error.response);
      } else if (error.request) {
        console.error('Error request:', error.request);
      } else {
        console.error('Error message:', error.message);
      }
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: { id: number; form: UserForm }) => updateUser(data.id, data.form),
    onSuccess: () => {
      message.success('更新成功');
      setVisible(false);
      form.resetFields();
      setEditingId(null);
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (error: any) => {
      if (error.response?.data?.message) {
        message.error(error.response.data.message);
      } else {
        console.error('Update error:', error);
        message.error('更新失败');
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteUser,
    onSuccess: () => {
      message.success('删除成功');
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  const statusMutation = useMutation({
    mutationFn: (data: { id: number; status: 0 | 1 }) => updateUserStatus(data.id, data.status),
    onSuccess: () => {
      message.success('状态更新成功');
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  const resetPwdMutation = useMutation({
    mutationFn: (data: { id: number; password: string }) => resetUserPassword(data.id, data.password),
    onSuccess: () => {
      message.success('密码重置成功');
      setPasswordVisible(false);
      passwordForm.resetFields();
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  const assignRoleMutation = useMutation({
    mutationFn: (data: { id: number; roleIds: number[] }) => assignUserRoles(data.id, data.roleIds),
    onSuccess: () => {
      message.success('角色分配成功');
      setRoleVisible(false);
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  const handleAdd = () => {
    setEditingId(null);
    form.resetFields();
    form.setFieldsValue({ status: 1 });
    setVisible(true);
  };

  const handleEdit = (record: UserInfo) => {
    setEditingId(record.id);
    form.setFieldsValue(record);
    setVisible(true);
  };

  const handleDelete = (id: number) => {
    deleteMutation.mutate([id]);
  };

  const handleStatusChange = (id: number, checked: boolean) => {
    statusMutation.mutate({ id, status: checked ? 1 : 0 });
  };

  const handleResetPwd = (record: UserInfo) => {
    setCurrentUser(record);
    passwordForm.resetFields();
    setPasswordVisible(true);
  };

  const handlePasswordSubmit = async () => {
    try {
      const values = await passwordForm.validateFields();
      if (currentUser) {
        resetPwdMutation.mutate({ id: currentUser.id, password: values.password });
      }
    } catch (error) {
      console.error('Form validation error:', error);
      message.error('表单验证失败');
    }
  };

  const handleAssignRole = (record: UserInfo) => {
    setCurrentUser(record);
    setRoleVisible(true);
  };

  React.useEffect(() => {
      if (userRoleIds) {
          roleForm.setFieldsValue({ roleIds: userRoleIds });
      } else {
          roleForm.resetFields();
      }
  }, [userRoleIds, roleForm]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const submitValues = { 
        ...values, 
        status: values.status !== undefined ? (values.status ? 1 : 0) : 1 
      };
      if (editingId) {
        updateMutation.mutate({ id: editingId, form: submitValues });
      } else {
        createMutation.mutate(submitValues);
      }
    } catch (error) {
      console.error('Form validation error:', error);
      message.error('表单验证失败');
    }
  };

  const handleRoleSubmit = async () => {
    try {
      const values = await roleForm.validateFields();
      if (currentUser) {
        assignRoleMutation.mutate({ id: currentUser.id, roleIds: values.roleIds });
      }
    } catch (error) {
      console.error(error);
    }
  };

  const columns: ColumnsType<UserInfo> = [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      width: 150,
      fixed: 'left',
    },
    {
      title: '昵称',
      dataIndex: 'nickname',
      key: 'nickname',
      width: 150,
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
      width: 200,
      render: (roleNames: string[]) => (
        <Space wrap>
          {roleNames?.map((roleName: string) => (
            <Tag key={roleName} color="blue">
              {roleName}
            </Tag>
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
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      ellipsis: true,
      render: (text) => text ? dayjs(text).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginTime',
      key: 'lastLoginTime',
      width: 180,
      ellipsis: true,
      render: (text) => text ? dayjs(text).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 280,
      fixed: 'right',
      align: 'right',
      render: (_, record) => (
        <Space size="small">
          <AuthButton perm="sys:user:edit">
            <Button 
              type="link" 
              size="small" 
              onClick={() => handleEdit(record)}
              disabled={record.username === 'admin'}
            >
              编辑
            </Button>
          </AuthButton>
          <AuthButton perm="sys:user:edit">
            <Button 
              type="link" 
              size="small" 
              onClick={() => handleAssignRole(record)}
              disabled={record.username === 'admin'}
            >
              授权
            </Button>
          </AuthButton>
          <AuthButton perm="sys:user:reset">
            <Button type="link" size="small" onClick={() => handleResetPwd(record)}>
              重置密码
            </Button>
          </AuthButton>
          <AuthButton perm="sys:user:remove">
            <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.id)}>
              <Button 
                type="link" 
                size="small" 
                danger
                disabled={record.username === 'admin'}
              >
                删除
              </Button>
            </Popconfirm>
          </AuthButton>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer
      title="管理员"
      extra={
        <AuthButton perm="sys:user:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新增管理员
          </Button>
        </AuthButton>
      }
    >
      <Form layout="inline" style={{ marginBottom: 16 }} onFinish={(values) => {
        setSearchParams(values);
        setPagination({ ...pagination, current: 1 });
      }}>
        <Form.Item name="username" label="用户名">
          <Input placeholder="请输入用户名" allowClear />
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
        <Form.Item>
          <Button type="primary" htmlType="submit">查询</Button>
        </Form.Item>
      </Form>

      <Table
        columns={columns}
        dataSource={userData?.records}
        rowKey="id"
        loading={isLoading}
        scroll={{ x: 1200, y: 'calc(100vh - 320px)' }}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: userData?.total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (page, size) => setPagination({ current: page, pageSize: size }),
        }}
      />

      <Drawer
        title={editingId ? '编辑管理员' : '新增管理员'}
        open={visible}
        onClose={() => setVisible(false)}
        size="large"
        forceRender
        extra={
          <Space>
            <Button onClick={() => setVisible(false)}>取消</Button>
            <Button 
              type="primary" 
              onClick={handleSubmit}
              loading={createMutation.isPending || updateMutation.isPending}
            >
              确定
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, message: '请输入用户名' },
              { pattern: /^[a-zA-Z0-9_]+$/, message: '仅允许字母数字下划线' },
              { min: 4, max: 20, message: '长度4-20字符' }
            ]}
          >
            <Input placeholder="请输入用户名" disabled={!!editingId} />
          </Form.Item>
          {!editingId && (
            <Form.Item
              name="password"
              label="密码"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, max: 20, message: '长度6-20字符' },
                { pattern: /^(?=.*[a-zA-Z])(?=.*\d).+$/, message: '必须包含字母和数字' }
              ]}
            >
              <Input.Password placeholder="请输入密码" />
            </Form.Item>
          )}
          <Form.Item
            name="nickname"
            label="昵称"
            rules={[
              { required: true, message: '请输入昵称' },
              { min: 2, max: 20, message: '昵称长度必须在2-20个字符之间' }
            ]}
          >
            <Input placeholder="请输入昵称" />
          </Form.Item>
          <Form.Item
            name="mobile"
            label="手机号"
            rules={[{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' }]}
          >
            <Input placeholder="请输入手机号" />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ type: 'email', message: '请输入正确的邮箱' }]}
          >
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item
            name="roleIds"
            label="角色"
            rules={[{ required: true, message: '请选择角色' }]}
          >
            <Select
              mode="multiple"
              placeholder="请选择角色"
              options={roleData?.map(role => ({ label: role.roleName, value: role.id }))}
            />
          </Form.Item>
          <Form.Item name="status" label="状态" valuePropName="checked" getValueFromEvent={(e) => e ? 1 : 0}>
             <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        title={`分配角色 - ${currentUser?.username}`}
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

      <Modal
        title="重置密码"
        open={passwordVisible}
        onOk={handlePasswordSubmit}
        onCancel={() => setPasswordVisible(false)}
        confirmLoading={resetPwdMutation.isPending}
        forceRender
      >
        <Form form={passwordForm} layout="vertical">
          <Form.Item
            name="password"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, max: 20, message: '长度6-20字符' },
              { pattern: /^(?=.*[a-zA-Z])(?=.*\d).+$/, message: '必须包含字母和数字' }
            ]}
          >
            <Input.Password placeholder="请输入新密码" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认密码"
            dependencies={['password']}
            rules={[
              { required: true, message: '请输入确认密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password placeholder="请输入确认密码" />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default UserList;
