import React, { useState } from 'react';
import {
  Card,
  Tree,
  Button,
  Space,
  Drawer,
  Form,
  Input,
  Select,
  InputNumber,
  message,
  Popconfirm,
  Tag,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getPlatformPermissionTree,
  createPlatformPermission,
  updatePlatformPermission,
  deletePlatformPermission,
  type PlatformPermission,
  type PlatformPermissionForm,
} from '../../api/permission';
import { PlatformAuthButton } from '../../components/PlatformAuthButton';

function toTreeData(
  nodes: PlatformPermission[],
  onSelect: (node: PlatformPermission) => void,
  selectedId: number | null
): DataNode[] {
  return nodes.map((n) => ({
    key: String(n.id),
    title: (
      <span>
        {n.name || n.permissionKey || `节点${n.id}`}
        {(n as any).scope && (
          <Tag color="blue" style={{ marginLeft: 8, fontSize: 10 }}>
            {(n as any).scope}
          </Tag>
        )}
      </span>
    ),
    children: n.children?.length
      ? toTreeData(n.children, onSelect, selectedId)
      : undefined,
    isLeaf: !n.children?.length,
    onClick: () => onSelect(n),
  }));
}

const PlatformPermissionsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [form] = Form.useForm<PlatformPermissionForm>();
  const [selectedNode, setSelectedNode] = useState<PlatformPermission | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingNode, setEditingNode] = useState<PlatformPermission | null>(null);

  const { data: treeData } = useQuery({
    queryKey: ['platform-permissions-tree'],
    queryFn: getPlatformPermissionTree,
  });

  const createMutation = useMutation({
    mutationFn: createPlatformPermission,
    onSuccess: () => {
      message.success('创建成功');
      setDrawerOpen(false);
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['platform-permissions-tree'] });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: PlatformPermissionForm }) =>
      updatePlatformPermission(id, data),
    onSuccess: () => {
      message.success('更新成功');
      setDrawerOpen(false);
      form.resetFields();
      setEditingNode(null);
      queryClient.invalidateQueries({ queryKey: ['platform-permissions-tree'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deletePlatformPermission,
    onSuccess: () => {
      message.success('删除成功');
      setSelectedNode(null);
      queryClient.invalidateQueries({ queryKey: ['platform-permissions-tree'] });
    },
  });

  const flatList = Array.isArray(treeData) ? treeData : (treeData as any)?.children || [];

  const handleNodeSelect = (node: PlatformPermission) => {
    setSelectedNode(node);
  };

  const handleAddChild = (parentId: number) => {
    setEditingNode(null);
    form.resetFields();
    form.setFieldsValue({ parentId, type: 'BTN', sort: 0 });
    setDrawerOpen(true);
  };

  const handleEdit = (node: PlatformPermission) => {
    setEditingNode(node);
    form.setFieldsValue({
      parentId: node.parentId ?? 0,
      name: node.name,
      type: node.type,
      permissionKey: node.permissionKey || '',
      path: node.path,
      component: node.component,
      sort: node.sort,
      scope: (node as any).scope,
    });
    setDrawerOpen(true);
  };

  const handleSubmit = () => {
    form.validateFields().then((values) => {
      const payload: PlatformPermissionForm = {
        parentId: values.parentId || 0,
        name: values.name,
        type: values.type,
        permissionKey: values.permissionKey || '',
        path: values.path,
        component: values.component,
        sort: values.sort ?? 0,
        scope: values.scope,
      };
      if (editingNode) {
        updateMutation.mutate({ id: editingNode.id, data: payload });
      } else {
        createMutation.mutate(payload);
      }
    });
  };

  return (
    <Card title="权限节点" bodyStyle={{ padding: 0 }}>
      <div style={{ display: 'flex', minHeight: 'calc(100vh - 200px)' }}>
        <div style={{ width: 360, padding: 16, borderRight: '1px solid #f0f0f0' }}>
          <Tree
            showLine
            defaultExpandAll
            treeData={toTreeData(flatList, handleNodeSelect, selectedNode?.id ?? null)}
            selectedKeys={selectedNode ? [String(selectedNode.id)] : []}
          />
        </div>

        <div style={{ flex: 1, padding: 16 }}>
          {selectedNode ? (
            <div>
              <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
                <h4 style={{ margin: 0 }}>节点详情</h4>
                <Space>
                  <PlatformAuthButton permission="platform:perm:add">
                    <Button
                      type="primary"
                      size="small"
                      icon={<PlusOutlined />}
                      onClick={() => handleAddChild(selectedNode.id)}
                    >
                      新增子节点
                    </Button>
                  </PlatformAuthButton>
                  <PlatformAuthButton permission="platform:perm:edit">
                    <Button
                      size="small"
                      icon={<EditOutlined />}
                      onClick={() => handleEdit(selectedNode)}
                    >
                      编辑
                    </Button>
                  </PlatformAuthButton>
                  <PlatformAuthButton permission="platform:perm:remove">
                    <Popconfirm
                      title="确定删除该节点？"
                      onConfirm={() => deleteMutation.mutate(selectedNode.id)}
                    >
                      <Button size="small" danger icon={<DeleteOutlined />}>
                        删除
                      </Button>
                    </Popconfirm>
                  </PlatformAuthButton>
                </Space>
              </div>
              <div style={{ marginBottom: 8 }}>
                <strong>ID:</strong> {selectedNode.id}
              </div>
              <div style={{ marginBottom: 8 }}>
                <strong>名称:</strong> {selectedNode.name || '-'}
              </div>
              <div style={{ marginBottom: 8 }}>
                <strong>类型:</strong> {selectedNode.type}
              </div>
              <div style={{ marginBottom: 8 }}>
                <strong>权限标识:</strong> {selectedNode.permissionKey || '-'}
              </div>
              <div style={{ marginBottom: 8 }}>
                <strong>路径:</strong> {selectedNode.path || '-'}
              </div>
              <div style={{ marginBottom: 8 }}>
                <strong>Scope:</strong> {(selectedNode as any).scope || '-'}
              </div>
            </div>
          ) : (
            <div style={{ color: 'var(--ant-color-text-secondary)', padding: 48, textAlign: 'center' }}>
              选择左侧节点查看详情
            </div>
          )}
        </div>
      </div>

      <Drawer
        title={editingNode ? '编辑权限节点' : '新增权限节点'}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setEditingNode(null);
          form.resetFields();
        }}
        width={440}
        destroyOnClose
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={() => { setDrawerOpen(false); setEditingNode(null); form.resetFields(); }}>
              取消
            </Button>
            <Button type="primary" onClick={handleSubmit} loading={createMutation.isPending || updateMutation.isPending}>
              确定
            </Button>
          </div>
        }
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="parentId" hidden>
            <Input />
          </Form.Item>
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input placeholder="节点名称" />
          </Form.Item>
          <Form.Item
            name="type"
            label="类型"
            rules={[{ required: true }]}
          >
            <Select>
              <Select.Option value="DIR">目录</Select.Option>
              <Select.Option value="MENU">菜单</Select.Option>
              <Select.Option value="BTN">按钮</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="permissionKey" label="权限标识">
            <Input placeholder="如 platform:tenant:list" />
          </Form.Item>
          <Form.Item name="path" label="路径">
            <Input placeholder="路由路径" />
          </Form.Item>
          <Form.Item name="component" label="组件">
            <Input placeholder="组件路径" />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="scope" label="Scope">
            <Select allowClear>
              <Select.Option value="platform">platform</Select.Option>
              <Select.Option value="tenant">tenant</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Drawer>
    </Card>
  );
};

export default PlatformPermissionsPage;
