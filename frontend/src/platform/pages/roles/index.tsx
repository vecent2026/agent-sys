import React, { useState, useEffect, useCallback } from 'react';
import {
  Table, Button, Space, Modal, Form, Input, Textarea,
  message, Popconfirm, Card, Tree, Spin,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SafetyOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import type { DataNode } from 'antd/es/tree';
import {
  getRolePage, createRole, updateRole, deleteRoles,
  getRolePermissions, assignRolePermissions, type RoleVo, type RoleDto,
} from '../../api/roleApi';
import { getPermissionTree, type PermissionVo } from '../../api/permissionApi';

const buildTreeNodes = (nodes: PermissionVo[]): DataNode[] =>
  nodes.map(n => ({
    key: n.id,
    title: `${n.name}${n.permissionKey ? ` [${n.permissionKey}]` : ''}`,
    children: n.children ? buildTreeNodes(n.children) : undefined,
  }));

const collectAllKeys = (nodes: PermissionVo[]): number[] => {
  const keys: number[] = [];
  const walk = (list: PermissionVo[]) => {
    list.forEach(n => {
      keys.push(n.id);
      if (n.children) walk(n.children);
    });
  };
  walk(nodes);
  return keys;
};

const RolePage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<RoleVo[]>([]);
  const [total, setTotal] = useState(0);
  const [pagination, setPagination] = useState({ page: 1, size: 10 });
  const [roleName, setRoleName] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<RoleVo | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  // 权限分配弹窗
  const [permModal, setPermModal] = useState<{ open: boolean; roleId?: number }>({ open: false });
  const [permTree, setPermTree] = useState<DataNode[]>([]);
  const [permTreeRaw, setPermTreeRaw] = useState<PermissionVo[]>([]);
  const [checkedKeys, setCheckedKeys] = useState<number[]>([]);
  const [permLoading, setPermLoading] = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getRolePage({ page: pagination.page, size: pagination.size, roleName });
      setData(res.records || []);
      setTotal(res.total || 0);
    } finally {
      setLoading(false);
    }
  }, [pagination, roleName]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: RoleVo) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const dto: RoleDto = values;
      if (editing) {
        await updateRole(editing.id, dto);
        message.success('修改成功');
      } else {
        await createRole(dto);
        message.success('创建成功');
      }
      setModalOpen(false);
      fetchData();
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    await deleteRoles([id]);
    message.success('删除成功');
    fetchData();
  };

  const openPermModal = async (roleId: number) => {
    setPermModal({ open: true, roleId });
    setPermLoading(true);
    try {
      const [tree, permIds] = await Promise.all([
        getPermissionTree(),
        getRolePermissions(roleId),
      ]);
      setPermTreeRaw(tree);
      setPermTree(buildTreeNodes(tree));
      setCheckedKeys(permIds);
    } finally {
      setPermLoading(false);
    }
  };

  const handleAssignPerms = async () => {
    await assignRolePermissions(permModal.roleId!, checkedKeys);
    message.success('权限分配成功');
    setPermModal({ open: false });
  };

  const columns: ColumnsType<RoleVo> = [
    { title: '角色名称', dataIndex: 'roleName', width: 160 },
    { title: '角色标识', dataIndex: 'roleKey', width: 180 },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    { title: '用户数', dataIndex: 'userCount', width: 80 },
    {
      title: '创建时间', dataIndex: 'createTime', width: 160,
      render: (val) => val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作', width: 200, fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Button type="link" size="small" icon={<SafetyOutlined />} onClick={() => openPermModal(record.id)}>分配权限</Button>
          <Popconfirm title="确定删除该角色？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card
        title="平台角色"
        extra={
          <Space>
            <Input.Search
              placeholder="角色名称"
              allowClear
              style={{ width: 200 }}
              onSearch={(v) => { setRoleName(v); setPagination(p => ({ ...p, page: 1 })); }}
            />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增角色</Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={data}
          scroll={{ x: 900 }}
          pagination={{
            current: pagination.page, pageSize: pagination.size, total,
            showSizeChanger: true, showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => setPagination({ page, size }),
          }}
        />
      </Card>

      <Modal
        title={editing ? '编辑角色' : '新增角色'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="roleName" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="roleKey" label="角色标识" rules={[{ required: true, message: '请输入角色标识' }]}>
            <Input placeholder="如 platform:admin" disabled={!!editing} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="分配权限"
        open={permModal.open}
        onOk={handleAssignPerms}
        onCancel={() => setPermModal({ open: false })}
        width={520}
        destroyOnClose
      >
        {permLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
        ) : (
          <Tree
            checkable
            defaultExpandAll
            treeData={permTree}
            checkedKeys={checkedKeys}
            onCheck={(keys) => setCheckedKeys((keys as number[]))}
            style={{ maxHeight: 400, overflowY: 'auto', marginTop: 8 }}
          />
        )}
      </Modal>
    </div>
  );
};

export default RolePage;
