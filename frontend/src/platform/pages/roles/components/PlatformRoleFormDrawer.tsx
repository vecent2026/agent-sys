import React, { useEffect } from 'react';
import { Drawer, Form, Input, Button, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createRole, updateRole, type RoleDto, type RoleVo } from '../../../api/roleApi';

interface PlatformRoleFormDrawerProps {
  open: boolean;
  onClose: () => void;
  editing?: RoleVo | null;
}

const PlatformRoleFormDrawer: React.FC<PlatformRoleFormDrawerProps> = ({
  open,
  onClose,
  editing,
}) => {
  const [form] = Form.useForm<RoleDto>();
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: createRole,
    onSuccess: () => {
      message.success('创建成功');
      onClose();
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['platform-roles'] });
    },
    onError: (err: any) => {
      message.error(err?.message || '创建失败');
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: RoleDto }) => updateRole(id, data),
    onSuccess: () => {
      message.success('更新成功');
      onClose();
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['platform-roles'] });
    },
    onError: (err: any) => {
      message.error(err?.message || '更新失败');
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
      const payload: RoleDto = {
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
          <Button
            type="primary"
            onClick={handleSubmit}
            loading={createMutation.isPending || updateMutation.isPending}
          >
            确定
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="roleName"
          label="角色名称"
          rules={[
            { required: true, message: '请输入角色名称' },
            { min: 2, max: 20, message: '长度须在2~20之间' },
          ]}
        >
          <Input placeholder="如：系统管理员" />
        </Form.Item>

        <Form.Item
          name="roleKey"
          label="角色标识"
          rules={[
            { required: true, message: '请输入角色标识' },
            { pattern: /^[a-zA-Z0-9_]+$/, message: '仅字母、数字、下划线' },
            { min: 2, max: 50, message: '长度须在2~50之间' },
          ]}
        >
          <Input placeholder="如：platform_admin" disabled={!!editing} />
        </Form.Item>

        <Form.Item name="description" label="描述">
          <Input.TextArea rows={3} placeholder="选填" maxLength={200} />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default PlatformRoleFormDrawer;
