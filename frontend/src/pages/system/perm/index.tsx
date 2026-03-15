import React, { useState, useMemo } from 'react';
import { Table, Button, Form, Input, InputNumber, Radio, Switch, message, Popconfirm, Tag, Space, Drawer } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getPermissionTree, createPermission, updatePermission, deletePermission } from '@/api/permission';
import { PageContainer } from '@/components/PageContainer';
import { TablePageLayout } from '@/design-system/components/TablePageLayout';
import { AuthButton } from '@/components/AuthButton';
import { IconPicker } from '@/components/IconPicker';
import type { Permission, PermissionForm } from '@/types/permission';
import type { ColumnsType } from 'antd/es/table';
import { designTokens } from '@/design-system/theme';

const PermissionList: React.FC = () => {
  const [form] = Form.useForm();
  const queryClient = useQueryClient();
  const [visible, setVisible] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [expandedRowKeys, setExpandedRowKeys] = useState<number[]>([]);


  const { data: treeData, isLoading } = useQuery({
    queryKey: ['permissions'],
    queryFn: getPermissionTree,
  });

  // 计算默认展开的行ID，只展开二级及以上的节点
  const defaultExpandedRowKeys = useMemo(() => {
    if (!treeData) return [];
    
    const expandedKeys: number[] = [];
    
    // 递归函数，收集二级及以上的节点ID
    const collectExpandedKeys = (nodes: Permission[], level: number = 1) => {
      nodes.forEach(node => {
        // 只展开二级节点（level === 2）且有子节点的节点
        if (level === 2 && node.children && node.children.length > 0) {
          expandedKeys.push(node.id);
        }
        // 递归处理子节点
        if (node.children) {
          collectExpandedKeys(node.children, level + 1);
        }
      });
    };
    
    collectExpandedKeys(treeData);
    return expandedKeys;
  }, [treeData]);

  // 当数据加载后，设置初始展开的行
  React.useEffect(() => {
    if (treeData && expandedRowKeys.length === 0) {
      setExpandedRowKeys(defaultExpandedRowKeys);
    }
  }, [treeData, defaultExpandedRowKeys, expandedRowKeys.length]);

  const createMutation = useMutation({
    mutationFn: createPermission,
    onSuccess: () => {
      message.success('创建成功');
      setVisible(false);
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['permissions'] });
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: { id: number; form: PermissionForm }) => updatePermission(data.id, data.form),
    onSuccess: () => {
      message.success('更新成功');
      setVisible(false);
      form.resetFields();
      setEditingId(null);
      queryClient.invalidateQueries({ queryKey: ['permissions'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deletePermission,
    onSuccess: () => {
      message.success('删除成功');
      queryClient.invalidateQueries({ queryKey: ['permissions'] });
    },
  });

  // 处理日志记录开关变更
  const handleLogEnabledChange = (id: number, logEnabled: boolean) => {
    // 查找当前节点
    const findNode = (nodes: Permission[] | undefined, targetId: number): Permission | null => {
      if (!nodes) return null;
      for (const node of nodes) {
        if (node.id === targetId) return node;
        const found = findNode(node.children, targetId);
        if (found) return found;
      }
      return null;
    };

    const node = findNode(treeData, id);
    if (node) {
      // 构建更新数据
      const updateData: PermissionForm = {
        parentId: node.parentId ?? undefined,
        name: node.name,
        type: node.type,
        permissionKey: node.permissionKey,
        path: node.path,
        component: node.component,
        icon: node.icon,
        sort: node.sort,
        logEnabled
      };
      
      // 调用更新API
      updateMutation.mutate({ id, form: updateData });
    }
  };

  const handleAdd = (pid: number | null = null) => {
    setEditingId(null);
    form.resetFields();
    form.setFieldsValue({ parentId: pid, type: 'MENU', sort: 0, logEnabled: true });
    setVisible(true);
  };

  const handleEdit = (record: Permission) => {
    setEditingId(record.id);
    // 根据节点类型处理表单字段，避免不必要的字段导致报错
    const formValues: any = { ...record };
    const type = record.type;
    
    if (type === 'BTN') {
      // 按钮类型节点，移除菜单类型的字段
      delete formValues.path;
      delete formValues.component;
      delete formValues.icon;
      // 确保logEnabled有值，默认为true
      if (formValues.logEnabled === undefined || formValues.logEnabled === null) {
        formValues.logEnabled = true;
      }
    } else {
      // 目录或菜单类型节点，移除按钮类型的字段
      delete formValues.permissionKey;
      // 非按钮类型节点不显示日志记录开关，移除该字段
      delete formValues.logEnabled;
    }
    
    form.setFieldsValue(formValues);
    setVisible(true);
  };

  const handleDelete = (id: number) => {
    deleteMutation.mutate(id);
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

  const columns: ColumnsType<Permission> = [
    {
      title: '节点名称',
      dataIndex: 'name',
      key: 'name',
      width: 240, // 增加宽度，约6个字符
      ellipsis: true, // 超长自动隐藏
      fixed: 'left', // 固定左侧
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (type) => {
        const config = designTokens.permissionTypeMap[type] || { color: 'default', label: type };
        return <Tag color={config.color}>{config.label}</Tag>;
      },
    },
    {
      title: '日志记录',
      dataIndex: 'logEnabled',
      key: 'logEnabled',
      width: 120,
      render: (logEnabled, record) => {
          // 仅按钮类型显示开关
          if (record.type !== 'BTN') {
            return '-';
          }
          // 日志记录默认开启
          const isEnabled = logEnabled !== undefined && logEnabled !== null ? logEnabled : true;
          return (
            <Switch
              checked={isEnabled}
              onChange={(checked) => handleLogEnabledChange(record.id, checked)}
            />
          );
        },
    },
    {
      title: '权限标识',
      dataIndex: 'permissionKey',
      key: 'permissionKey',
      width: 180,
      ellipsis: true,
    },
    {
      title: '路由地址',
      dataIndex: 'path',
      key: 'path',
      width: 200,
      ellipsis: true,
    },
    {
      title: '组件路径',
      dataIndex: 'component',
      key: 'component',
      width: 220,
      ellipsis: true,
    },
    {
      title: '操作',
      key: 'action',
      width: 280, // 增加操作列宽度以容纳三个按钮
      fixed: 'right', // 固定右侧
      render: (_, record) => (
        <Space size="small">
          {record.type !== 'BTN' && (
            <AuthButton perm="sys:menu:add">
              <Button type="link" size="small" icon={<PlusOutlined />} onClick={() => handleAdd(record.id)}>
                新增子级
              </Button>
            </AuthButton>
          )}
          <AuthButton perm="sys:menu:edit">
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
              编辑
            </Button>
          </AuthButton>
          <AuthButton perm="sys:menu:remove">
            <Popconfirm title="确定删除吗？" onConfirm={() => handleDelete(record.id)}>
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
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
          <AuthButton perm="sys:menu:add">
            <Button type="primary" icon={<PlusOutlined />} onClick={() => handleAdd(null)}>
              新增根节点
            </Button>
          </AuthButton>
        }
      >
        <Table
          columns={columns}
          dataSource={treeData}
          rowKey="id"
          loading={isLoading}
          size="small"
          onRow={() => ({ style: { height: 40 } })}
          pagination={false}
          scroll={{ x: 'max-content', y: 'calc(100vh - 360px)' }}
          sticky
          expandedRowKeys={expandedRowKeys}
          onExpandedRowsChange={(keys) => setExpandedRowKeys(keys as number[])}
          expandable={{
            defaultExpandAllRows: false,
          }}
        />
      </TablePageLayout>

      <Drawer
        title={editingId ? '编辑权限' : '新增权限'}
        open={visible}
        onClose={() => setVisible(false)}
        width={520}
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
          <Form.Item name="parentId" hidden>
            <Input />
          </Form.Item>
          
          <Form.Item
            name="type"
            label="节点类型"
            rules={[{ required: true }]}
          >
            <Radio.Group disabled={!!editingId}>
              <Radio value="DIR">目录</Radio>
              <Radio value="MENU">菜单</Radio>
              <Radio value="BTN">按钮</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            name="name"
            label="节点名称"
            rules={[
              { required: true, message: '请输入节点名称' },
              { min: 2, max: 20, message: '节点名称长度必须在2-20个字符之间' }
            ]}
          >
            <Input placeholder="请输入节点名称" />
          </Form.Item>

          <Form.Item
            noStyle
            shouldUpdate={(prev, current) => prev.type !== current.type}
          >
            {({ getFieldValue }) => {
              const type = getFieldValue('type');
              if (type === 'BTN') {
                return (
                  <Form.Item
                    name="permissionKey"
                    label="权限标识"
                    rules={[
                      { required: true, message: '请输入权限标识' },
                      { pattern: /^[a-zA-Z0-9_:-]+$/, message: '权限标识格式不正确，建议使用 module:action 格式' }
                    ]}
                    tooltip="如: user:list"
                  >
                    <Input placeholder="请输入权限标识" />
                  </Form.Item>
                );
              }
              return null;
            }}
          </Form.Item>

          <Form.Item
            noStyle
            shouldUpdate={(prev, current) => prev.type !== current.type}
          >
            {({ getFieldValue }) => {
              const type = getFieldValue('type');
              return type !== 'BTN' ? (
                <>
                  <Form.Item
                    name="path"
                    label="路由地址"
                    rules={[{ required: type === 'MENU', message: '请输入路由地址' }]}
                    tooltip="以 / 开头，如: /system/user"
                  >
                    <Input placeholder="请输入路由地址" />
                  </Form.Item>
                  <Form.Item
                    name="component"
                    label="组件路径"
                    rules={[{ required: type === 'MENU', message: '请输入组件路径' }]}
                    tooltip="相对于 src/pages 的路径，如: system/user/index"
                  >
                    <Input placeholder="请输入组件路径" />
                  </Form.Item>
                  <Form.Item name="icon" label="图标">
                    <IconPicker />
                  </Form.Item>
                </>
              ) : null;
            }}
          </Form.Item>

          <Form.Item
            name="sort"
            label="排序号"
            rules={[{ required: true }]}
          >
            <InputNumber style={{ width: '100%' }} min={0} />
          </Form.Item>
          
          <Form.Item
            noStyle
            shouldUpdate={(prev, current) => prev.type !== current.type}
          >
            {({ getFieldValue }) => {
              const type = getFieldValue('type');
              if (type === 'BTN') {
                return (
                  <Form.Item
                    name="logEnabled"
                    label="日志记录"
                    valuePropName="checked"
                    initialValue={true}
                  >
                    <Switch />
                  </Form.Item>
                );
              }
              return null;
            }}
          </Form.Item>
        </Form>
      </Drawer>
    </PageContainer>
  );
};

export default PermissionList;
