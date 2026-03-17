import React, { useState, useEffect, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Modal, Form, Input, Select,
  InputNumber, Switch, message, Popconfirm, Card, TreeSelect,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import {
  getPermissionTree, createPermission, updatePermission, deletePermission,
  type PermissionVo, type PermissionDto,
} from '../../api/permissionApi';

const TYPE_LABELS: Record<string, { color: string; label: string }> = {
  directory: { color: 'blue', label: '目录' },
  menu: { color: 'green', label: '菜单' },
  button: { color: 'orange', label: '按钮/API' },
};

const buildTreeSelectData = (nodes: PermissionVo[]): any[] =>
  nodes.map(n => ({
    value: n.id,
    title: n.name,
    children: n.children ? buildTreeSelectData(n.children) : undefined,
  }));

const flattenTree = (nodes: PermissionVo[]): PermissionVo[] => {
  const result: PermissionVo[] = [];
  const walk = (list: PermissionVo[]) => {
    list.forEach(n => { result.push(n); if (n.children) walk(n.children); });
  };
  walk(nodes);
  return result;
};

const PermissionPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [treeData, setTreeData] = useState<PermissionVo[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<PermissionVo | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const tree = await getPermissionTree();
      setTreeData(tree);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  const openCreate = (parentId?: number) => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ parentId, type: 'menu', sort: 0, logEnabled: false });
    setModalOpen(true);
  };

  const openEdit = (record: PermissionVo) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const dto: PermissionDto = { ...values };
      if (editing) {
        await updatePermission(editing.id, dto);
        message.success('修改成功');
      } else {
        await createPermission(dto);
        message.success('创建成功');
      }
      setModalOpen(false);
      fetchData();
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    await deletePermission(id);
    message.success('删除成功');
    fetchData();
  };

  const columns: ColumnsType<PermissionVo> = [
    { title: '名称', dataIndex: 'name', width: 200 },
    {
      title: '类型', dataIndex: 'type', width: 90,
      render: (val) => {
        const cfg = TYPE_LABELS[val] || { color: 'default', label: val };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '权限标识', dataIndex: 'permissionKey', ellipsis: true },
    { title: '路径', dataIndex: 'path', ellipsis: true },
    { title: '排序', dataIndex: 'sort', width: 60 },
    {
      title: '记录日志', dataIndex: 'logEnabled', width: 90,
      render: (val) => <Tag color={val ? 'green' : 'default'}>{val ? '是' : '否'}</Tag>,
    },
    {
      title: '操作', width: 200, fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<PlusOutlined />} onClick={() => openCreate(record.id)}>子节点</Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除？有子节点时无法删除" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card
        title="权限节点"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate()}>新增根节点</Button>}
      >
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={treeData}
          childrenColumnName="children"
          pagination={false}
          scroll={{ x: 900 }}
          expandable={{ defaultExpandAllRows: true }}
        />
      </Card>

      <Modal
        title={editing ? '编辑权限节点' : '新增权限节点'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="parentId" label="父节点">
            <TreeSelect
              treeData={buildTreeSelectData(treeData)}
              allowClear
              placeholder="根节点（不选）"
              treeDefaultExpandAll
            />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="directory">目录</Select.Option>
              <Select.Option value="menu">菜单</Select.Option>
              <Select.Option value="button">按钮/API</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="permissionKey" label="权限标识">
            <Input placeholder="如 platform:user:list" />
          </Form.Item>
          <Form.Item name="path" label="路由路径">
            <Input placeholder="如 /users" />
          </Form.Item>
          <Form.Item name="component" label="组件路径">
            <Input placeholder="如 views/users/index" />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="logEnabled" label="记录操作日志" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default PermissionPage;
