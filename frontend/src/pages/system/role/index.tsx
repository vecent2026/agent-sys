import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, message, Popconfirm, Tree, Drawer } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SettingOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getRoleList, createRole, updateRole, deleteRole, getRolePermissions, assignRolePermissions } from '@/api/role';
import { getPermissionTree } from '@/api/permission';
import { PageContainer } from '@/components/PageContainer';
import { AuthButton } from '@/components/AuthButton';
import type { Role, RoleForm } from '@/types/role';
import type { ColumnsType } from 'antd/es/table';
import { formatDate } from '@/utils/dateUtils';

const RoleList: React.FC = () => {
  const [form] = Form.useForm();
  const queryClient = useQueryClient();
  const [visible, setVisible] = useState(false);
  const [permVisible, setPermVisible] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [currentRole, setCurrentRole] = useState<Role | null>(null);
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  
  // Pagination state
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
  });

  // Fetch Roles
  const { data: roleData, isLoading } = useQuery({
    queryKey: ['roles', pagination],
    queryFn: () => getRoleList({ page: pagination.current, size: pagination.pageSize }),
  });

  // Fetch Permission Tree (for assignment)
  const { data: permTree } = useQuery({
    queryKey: ['permissions'],
    queryFn: getPermissionTree,
    enabled: permVisible, // Only fetch when drawer is open
  });

  // Fetch Role Permissions
  const { data: rolePerms } = useQuery({
    queryKey: ['rolePermissions', currentRole?.id],
    queryFn: () => getRolePermissions(currentRole!.id),
    enabled: !!currentRole && permVisible,
  });

  // Sync checked keys when rolePerms loaded
  useEffect(() => {
    if (rolePerms) {
      setCheckedKeys(rolePerms);
    }
  }, [rolePerms]);

  const createMutation = useMutation({
    mutationFn: createRole,
    onSuccess: () => {
      message.success('创建成功');
      setVisible(false);
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['roles'] });
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: { id: number; form: RoleForm }) => updateRole(data.id, data.form),
    onSuccess: () => {
      message.success('更新成功');
      setVisible(false);
      form.resetFields();
      setEditingId(null);
      queryClient.invalidateQueries({ queryKey: ['roles'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteRole,
    onSuccess: () => {
      message.success('删除成功');
      queryClient.invalidateQueries({ queryKey: ['roles'] });
    },
  });

  const assignPermMutation = useMutation({
    mutationFn: (data: { id: number; permissionIds: number[] }) => assignRolePermissions(data.id, data.permissionIds),
    onSuccess: () => {
      message.success('权限分配成功');
      setPermVisible(false);
      queryClient.invalidateQueries({ queryKey: ['roles'] });
    },
  });

  const handleAdd = () => {
    setEditingId(null);
    form.resetFields();
    setVisible(true);
  };

  const handleEdit = (record: Role) => {
    setEditingId(record.id);
    form.setFieldsValue(record);
    setVisible(true);
  };

  const handleDelete = (id: number) => {
    deleteMutation.mutate([id]);
  };

  const handleAssignPerm = (record: Role) => {
    setCurrentRole(record);
    setPermVisible(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editingId) {
        updateMutation.mutate({ id: editingId, form: values });
      } else {
        createMutation.mutate(values);
      }
    } catch (error) {
      console.error(error);
    }
  };

  const handlePermSubmit = () => {
    if (currentRole) {
      // Note: In a real tree, you might need to include half-checked keys (parent nodes)
      // AntD Tree onCheck gives { checked: [], halfChecked: [] } if checkStrictly is true
      // Here we assume backend handles parent inference or we send all checked keys
      assignPermMutation.mutate({
        id: currentRole.id,
        permissionIds: checkedKeys as number[],
      });
    }
  };

  const columns: ColumnsType<Role> = [
    {
      title: '角色名称',
      dataIndex: 'roleName',
      key: 'roleName',
    },
    {
      title: '角色标识',
      dataIndex: 'roleKey',
      key: 'roleKey',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: '创建人',
      dataIndex: 'createBy',
      key: 'createBy',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      render: (text: string | null) => formatDate(text),
    },
    {
      title: '最后更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      render: (text: string | null) => formatDate(text),
    },
    {
      title: '操作',
      key: 'action',
      width: 250,
      fixed: 'right',
      render: (_, record) => (
        <div style={{ display: 'flex', gap: 8 }}>
          <AuthButton perm="sys:role:edit">
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
              编辑
            </Button>
          </AuthButton>
          <AuthButton perm="role:assign">
            <Button type="link" size="small" icon={<SettingOutlined />} onClick={() => handleAssignPerm(record)}>
              分配权限
            </Button>
          </AuthButton>
          <AuthButton perm="sys:role:remove">
            <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.id)}>
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </AuthButton>
        </div>
      ),
    },
  ];

  return (
    <PageContainer
      title="角色管理"
      extra={
        <AuthButton perm="sys:role:add">
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新增角色
          </Button>
        </AuthButton>
      }
    >
      <Table
        columns={columns}
        dataSource={roleData?.records}
        rowKey="id"
        loading={isLoading}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: roleData?.total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (page, size) => setPagination({ current: page, pageSize: size }),
        }}
        scroll={{ x: 'max-content' }}
      />

      <Modal
        title={editingId ? '编辑角色' : '新增角色'}
        open={visible}
        onOk={handleSubmit}
        onCancel={() => setVisible(false)}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        forceRender
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="roleName"
            label="角色名称"
            rules={[
              { required: true, message: '请输入角色名称' },
              { min: 2, max: 20, message: '角色名称长度必须在2-20个字符之间' }
            ]}
          >
            <Input placeholder="请输入角色名称" />
          </Form.Item>
          <Form.Item
            name="roleKey"
            label="角色标识"
            rules={[
              { required: true, message: '请输入角色标识' },
              { min: 2, max: 50, message: '角色标识长度必须在2-50个字符之间' }
            ]}
          >
            <Input placeholder="请输入角色标识" />
          </Form.Item>
          <Form.Item 
            name="description" 
            label="描述"
            rules={[
              { max: 200, message: '描述长度不能超过200个字符' }
            ]}
          >
            <Input.TextArea rows={4} placeholder="请输入描述" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={`分配权限 - ${currentRole?.roleName}`}
        open={permVisible}
        onClose={() => setPermVisible(false)}
        size="large"
        extra={
          <Button type="primary" onClick={handlePermSubmit} loading={assignPermMutation.isPending}>
            保存
          </Button>
        }
      >
        {permTree && (
          <Tree
            checkable
            defaultExpandAll
            treeData={permTree}
            fieldNames={{ title: 'name', key: 'id', children: 'children' }}
            checkedKeys={checkedKeys}
            onCheck={(keys) => setCheckedKeys(keys as React.Key[])}
          />
        )}
      </Drawer>
    </PageContainer>
  );
};

export default RoleList;
