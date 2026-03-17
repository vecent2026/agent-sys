import React, { useState, useEffect, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Switch, Modal, Form, Input,
  message, Popconfirm, Card, Select, Transfer, Tooltip,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, KeyOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import {
  getUserPage, createUser, updateUser, deleteUsers, changeUserStatus,
  resetUserPassword, getUserRoles, assignUserRoles, type UserVo, type UserDto,
} from '../../api/userApi';
import { getRoleList, type RoleVo } from '../../api/roleApi';

const UserPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<UserVo[]>([]);
  const [total, setTotal] = useState(0);
  const [pagination, setPagination] = useState({ page: 1, size: 10 });
  const [query, setQuery] = useState<{ username?: string; mobile?: string; status?: number }>({});
  const [selected, setSelected] = useState<number[]>([]);

  // 编辑弹窗
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<UserVo | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  // 重置密码弹窗
  const [pwdModal, setPwdModal] = useState<{ open: boolean; userId?: number }>({ open: false });
  const [pwdForm] = Form.useForm();

  // 角色分配弹窗
  const [roleModal, setRoleModal] = useState<{ open: boolean; userId?: number }>({ open: false });
  const [allRoles, setAllRoles] = useState<RoleVo[]>([]);
  const [targetRoleIds, setTargetRoleIds] = useState<string[]>([]);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getUserPage({ ...pagination, ...query });
      setData(res.records || []);
      setTotal(res.total || 0);
    } finally {
      setLoading(false);
    }
  }, [pagination, query]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ status: 1 });
    setModalOpen(true);
  };

  const openEdit = (record: UserVo) => {
    setEditing(record);
    form.setFieldsValue({ ...record, password: undefined });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const dto: UserDto = { ...values };
      if (editing) {
        await updateUser(editing.id, dto);
        message.success('修改成功');
      } else {
        await createUser(dto);
        message.success('创建成功');
      }
      setModalOpen(false);
      fetchData();
    } finally {
      setSaving(false);
    }
  };

  const handleBatchDelete = async () => {
    await deleteUsers(selected);
    message.success('删除成功');
    setSelected([]);
    fetchData();
  };

  const handleStatusChange = async (id: number, checked: boolean) => {
    await changeUserStatus(id, checked ? 1 : 0);
    fetchData();
  };

  const openPwdModal = (userId: number) => {
    pwdForm.resetFields();
    setPwdModal({ open: true, userId });
  };

  const handleResetPwd = async () => {
    const { password } = await pwdForm.validateFields();
    await resetUserPassword(pwdModal.userId!, password);
    message.success('密码已重置');
    setPwdModal({ open: false });
  };

  const openRoleModal = async (record: UserVo) => {
    const [roles, userRoleIds] = await Promise.all([
      getRoleList(),
      getUserRoles(record.id),
    ]);
    setAllRoles(roles);
    setTargetRoleIds(userRoleIds.map(String));
    setRoleModal({ open: true, userId: record.id });
  };

  const handleAssignRoles = async () => {
    await assignUserRoles(roleModal.userId!, targetRoleIds.map(Number));
    message.success('角色分配成功');
    setRoleModal({ open: false });
    fetchData();
  };

  const [queryForm] = Form.useForm();
  const handleSearch = () => {
    setQuery(queryForm.getFieldsValue());
    setPagination(p => ({ ...p, page: 1 }));
  };
  const handleReset = () => {
    queryForm.resetFields();
    setQuery({});
    setPagination(p => ({ ...p, page: 1 }));
  };

  const columns: ColumnsType<UserVo> = [
    {
      title: '用户名', dataIndex: 'username', width: 150,
      render: (val, record) => (
        <Space size={6}>
          {val}
          {record.isSuper === 1 && <Tag color="red">超管</Tag>}
        </Space>
      ),
    },
    { title: '昵称', dataIndex: 'nickname', width: 120 },
    { title: '手机号', dataIndex: 'mobile', width: 130 },
    {
      title: '角色', dataIndex: 'roleNames', width: 200,
      render: (names: string[]) => names?.map(n => <Tag key={n}>{n}</Tag>),
    },
    {
      title: '状态', dataIndex: 'status', width: 80,
      render: (val, record) => (
        <Tooltip title={record.isSuper === 1 ? '超级管理员状态不可修改' : undefined}>
          <Switch
            checked={val === 1}
            size="small"
            disabled={record.isSuper === 1}
            onChange={(c) => handleStatusChange(record.id, c)}
          />
        </Tooltip>
      ),
    },
    {
      title: '最后登录', dataIndex: 'lastLoginTime', width: 155,
      render: (val) => val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作', width: 220, fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Button type="link" size="small" icon={<KeyOutlined />} onClick={() => openPwdModal(record.id)}>重置密码</Button>
          {record.isSuper === 1 ? (
            <Tooltip title="超级管理员角色不可修改">
              <Button type="link" size="small" disabled>分配角色</Button>
            </Tooltip>
          ) : (
            <Button type="link" size="small" onClick={() => openRoleModal(record)}>分配角色</Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card size="small" style={{ marginBottom: 12 }}>
        <Form form={queryForm} layout="inline">
          <Form.Item name="username" label="用户名">
            <Input placeholder="请输入" allowClear style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="mobile" label="手机号">
            <Input placeholder="请输入" allowClear style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="全部" allowClear style={{ width: 100 }}>
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" onClick={handleSearch}>查询</Button>
              <Button onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card
        title="平台用户"
        extra={
          <Space>
            {selected.length > 0 && (
              <Popconfirm title={`确定删除选中的 ${selected.length} 个用户？`} onConfirm={handleBatchDelete}>
                <Button danger icon={<DeleteOutlined />}>批量删除</Button>
              </Popconfirm>
            )}
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增用户</Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={data}
          scroll={{ x: 1000 }}
          rowSelection={{
            selectedRowKeys: selected,
            onChange: (keys) => setSelected(keys as number[]),
            getCheckboxProps: (record) => ({ disabled: record.isSuper === 1 }),
          }}
          pagination={{
            current: pagination.page, pageSize: pagination.size, total,
            showSizeChanger: true, showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => setPagination({ page, size }),
          }}
        />
      </Card>

      {/* 编辑/新增弹窗 */}
      <Modal
        title={editing ? '编辑用户' : '新增用户'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input disabled={!!editing} />
          </Form.Item>
          {!editing && (
            <Form.Item name="password" label="初始密码" rules={[{ required: true, message: '请输入初始密码' }]}>
              <Input.Password />
            </Form.Item>
          )}
          <Form.Item name="nickname" label="昵称">
            <Input />
          </Form.Item>
          <Form.Item name="mobile" label="手机号">
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select>
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      {/* 重置密码弹窗 */}
      <Modal
        title="重置密码"
        open={pwdModal.open}
        onOk={handleResetPwd}
        onCancel={() => setPwdModal({ open: false })}
        destroyOnClose
      >
        <Form form={pwdForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="password" label="新密码" rules={[{ required: true, min: 6, message: '密码至少6位' }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>

      {/* 分配角色弹窗 */}
      <Modal
        title="分配角色"
        open={roleModal.open}
        onOk={handleAssignRoles}
        onCancel={() => setRoleModal({ open: false })}
        width={600}
        destroyOnClose
      >
        <Transfer
          dataSource={allRoles.map(r => ({ key: String(r.id), title: r.roleName, description: r.roleKey }))}
          targetKeys={targetRoleIds}
          onChange={(keys) => setTargetRoleIds(keys as string[])}
          render={(item) => item.title}
          titles={['可选角色', '已分配角色']}
          style={{ marginTop: 16 }}
        />
      </Modal>
    </div>
  );
};

export default UserPage;
