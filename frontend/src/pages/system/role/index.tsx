import React, { useState, useEffect } from 'react';
import { Table, Button, Form, Input, message, Popconfirm, Tree, Drawer, Pagination, Space, Tooltip, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SettingOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getRoleList, createRole, updateRole, deleteRole, getRolePermissions, assignRolePermissions } from '@/api/role';
import { getPermissionTree } from '@/api/permission';
import { PageContainer } from '@/components/PageContainer';
import { TablePageLayout } from '@/design-system/components/TablePageLayout';
import { AuthButton } from '@/components/AuthButton';
import type { Role, RoleForm } from '@/types/role';
import type { ColumnsType } from 'antd/es/table';
import { formatDate } from '@/utils/dateUtils';
import { designTokens } from '@/design-system/theme';

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
    pageSize: 20,
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
    if (record.isBuiltin === 1) {
      message.warning('内置超管角色不可编辑');
      return;
    }
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
    if (currentRole?.isBuiltin === 1) {
      message.warning('内置超管角色权限为系统全量权限，不支持修改');
      return;
    }
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
      fixed: 'left',
      width: 150,
    },
    {
      title: '角色标识',
      dataIndex: 'roleKey',
      key: 'roleKey',
      render: (value: string, record) => (
        <Space size="small">
          <span>{value}</span>
          {record.isBuiltin === 1 && <Tag color="red">内置超管</Tag>}
        </Space>
      ),
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
        <Space size="small">
          <AuthButton perm="sys:role:edit">
            {record.isBuiltin === 1 ? (
              <Tooltip title="内置超管角色不可编辑">
                <Button type="link" size="small" icon={<EditOutlined />} disabled>
                  编辑
                </Button>
              </Tooltip>
            ) : (
              <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
                编辑
              </Button>
            )}
          </AuthButton>
          <AuthButton perm="role:assign">
            <Button type="link" size="small" icon={<SettingOutlined />} onClick={() => handleAssignPerm(record)}>
              分配权限
            </Button>
          </AuthButton>
          <AuthButton perm="sys:role:remove">
            {record.isBuiltin === 1 ? (
              <Tooltip title="内置超管角色不可删除">
                <Button type="link" size="small" danger icon={<DeleteOutlined />} disabled>
                  删除
                </Button>
              </Tooltip>
            ) : (
              <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.id)}>
                <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                  删除
                </Button>
              </Popconfirm>
            )}
          </AuthButton>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer>
      <TablePageLayout
        toolbar={
          <AuthButton perm="sys:role:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
              新增角色
            </Button>
          </AuthButton>
        }
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Pagination
              size="small"
              current={pagination.current}
              pageSize={pagination.pageSize}
              total={roleData?.total}
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
          dataSource={roleData?.records}
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
        title={editingId ? '编辑角色' : '新增角色'}
        open={visible}
        onClose={() => setVisible(false)}
        width={480}
        destroyOnClose
        footer={
          <div style={{ textAlign: 'right', borderTop: `1px solid ${designTokens.colorBorder}`, paddingTop: 16 }}>
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
          </div>
        }
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
      </Drawer>

      <Drawer
        title={`分配权限 - ${currentRole?.roleName}`}
        open={permVisible}
        onClose={() => setPermVisible(false)}
        size="large"
        footer={
          <div style={{ textAlign: 'right', borderTop: `1px solid ${designTokens.colorBorder}`, paddingTop: 16 }}>
            <Space>
              <Button onClick={() => setPermVisible(false)}>取消</Button>
              <Button
                type="primary"
                onClick={handlePermSubmit}
                disabled={currentRole?.isBuiltin === 1}
                loading={assignPermMutation.isPending}
              >
                保存
              </Button>
            </Space>
          </div>
        }
      >
        {currentRole?.isBuiltin === 1 && (
          <div style={{ marginBottom: 12, color: designTokens.colorTextTertiary }}>
            内置超管角色默认拥有租户全部权限，权限树仅展示不可编辑。
          </div>
        )}
        {permTree && (
          <Tree
            checkable
            defaultExpandAll
            treeData={permTree}
            fieldNames={{ title: 'name', key: 'id', children: 'children' }}
            checkedKeys={checkedKeys}
            disabled={currentRole?.isBuiltin === 1}
            onCheck={(keys) => setCheckedKeys(keys as React.Key[])}
          />
        )}
      </Drawer>
    </PageContainer>
  );
};

export default RoleList;
