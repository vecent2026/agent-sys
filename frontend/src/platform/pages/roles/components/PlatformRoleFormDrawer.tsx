import React, { useEffect } from 'react';
import { Drawer, Form, Input, Button, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  createPlatformRole,
  updatePlatformRole,
  type PlatformRoleForm,
} from '../../../api/role';
import type { Role } from '@/types/role';

interface PlatformRoleFormDrawerProps {
  open: boolean;
  onClose: () => void;
  editing?: Role | null;
}

const PlatformRoleFormDrawer: React.FC<PlatformRoleFormDrawerProps> = ({
  open,
  onClose,
  editing,
}) => {
  const [form] = Form.useForm<PlatformRoleForm>();
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: createPlatformRole,
    onSuccess: () => {
      message.success('创建成功');
      onClose();
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['platform-roles'] });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: PlatformRoleForm }) =>
      updatePlatformRole(id, data),
    onSuccess: () => {
      message.success('更新成功');
      onClose();
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['platform-roles'] });
    },
  });

  useEffect(() => {
    if (open) {
      if (editing) {
        form.setFieldsValue({
          roleName: editing.roleName,
          roleKey: editing.roleKey,
          description: editing.description,
        });
      } else {
        form.resetFields();
      }
    }
  }, [open, editing, form]);

  const handleSubmit = () => {
    form.validateFields().then((values) => {
      const payload: PlatformRoleForm = {
        roleName: values.roleName,
        roleKey: values.roleKey,
        description: values.description,
      };
      if (editing) {
        updateMutation.mutate({ id: editing.id, data: payload });
      } else {
        createMutation.mutate(payload);
      }
    });
  };

  return (
    <Drawer
      title={editing ? '编辑平台角色' : '新增平台角色'}
      open={open}
      onClose={onClose}
      width={440}
      destroyOnClose
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={handleSubmit} loading={createMutation.isPending || updateMutation.isPending}>
            确定
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="roleName"
          label="角色名称"
          rules={[{ required: true, message: '请输入角色名称' }]}
        >
          <Input placeholder="如：系统管理员" />
        </Form.Item>

        <Form.Item
          name="roleKey"
          label="角色标识"
          rules={[
            { required: true, message: '请输入角色标识' },
            { pattern: /^[a-zA-Z0-9_]+$/, message: '仅字母、数字、下划线' },
          ]}
        >
          <Input placeholder="如：platform_admin" disabled={!!editing} />
        </Form.Item>

        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} placeholder="选填" />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default PlatformRoleFormDrawer;
