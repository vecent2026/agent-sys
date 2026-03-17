import React, { useEffect } from 'react';
import { Drawer, Form, Input, Select, Button, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createPlatformUser,
  updatePlatformUser,
  type PlatformUserForm,
} from '../../../api/user';
import { getAllPlatformRoles } from '../../../api/role';
import type { UserInfo } from '@/types/user';

interface PlatformUserFormDrawerProps {
  open: boolean;
  onClose: () => void;
  editing?: UserInfo | null;
}

const PlatformUserFormDrawer: React.FC<PlatformUserFormDrawerProps> = ({
  open,
  onClose,
  editing,
}) => {
  const [form] = Form.useForm<PlatformUserForm>();
  const queryClient = useQueryClient();

  const { data: roles } = useQuery({
    queryKey: ['platform-roles-all'],
    queryFn: getAllPlatformRoles,
    enabled: open,
  });

  const createMutation = useMutation({
    mutationFn: createPlatformUser,
    onSuccess: () => {
      message.success('创建成功');
      onClose();
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['platform-users'] });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: PlatformUserForm }) =>
      updatePlatformUser(id, data),
    onSuccess: () => {
      message.success('更新成功');
      onClose();
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['platform-users'] });
    },
  });

  useEffect(() => {
    if (open) {
      if (editing) {
        form.setFieldsValue({
          username: editing.username,
          nickname: editing.nickname,
          mobile: editing.mobile,
          email: editing.email,
          status: editing.status,
          roleIds: (editing as any).roleIds ?? [],
        });
      } else {
        form.resetFields();
        form.setFieldsValue({ status: 1 });
      }
    }
  }, [open, editing, form]);

  const handleSubmit = () => {
    form.validateFields().then((values) => {
      const payload: PlatformUserForm = {
        username: values.username,
        nickname: values.nickname,
        status: values.status ?? 1,
        mobile: values.mobile,
        email: values.email,
        roleIds: values.roleIds,
      };
      if (editing) {
        updateMutation.mutate({ id: editing.id, data: payload });
      } else {
        payload.password = values.password;
        createMutation.mutate(payload);
      }
    });
  };

  return (
    <Drawer
      title={editing ? '编辑平台用户' : '新增平台用户'}
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
          name="username"
          label="用户名"
          rules={[{ required: true, message: '请输入用户名' }]}
        >
          <Input placeholder="登录用户名" disabled={!!editing} />
        </Form.Item>

        {!editing && (
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }, { min: 6, max: 20, message: '6-20位' }]}
          >
            <Input.Password placeholder="6-20位" />
          </Form.Item>
        )}

        <Form.Item
          name="nickname"
          label="昵称"
          rules={[{ required: true, message: '请输入昵称' }]}
        >
          <Input placeholder="昵称" />
        </Form.Item>

        <Form.Item name="mobile" label="手机号">
          <Input placeholder="选填" />
        </Form.Item>

        <Form.Item name="email" label="邮箱">
          <Input placeholder="选填" />
        </Form.Item>

        <Form.Item name="status" label="状态">
          <Select>
            <Select.Option value={1}>启用</Select.Option>
            <Select.Option value={0}>禁用</Select.Option>
          </Select>
        </Form.Item>

        <Form.Item name="roleIds" label="角色">
          <Select mode="multiple" placeholder="选择角色" allowClear>
            {(roles || []).map((r) => (
              <Select.Option key={r.id} value={r.id}>
                {r.roleName}
              </Select.Option>
            ))}
          </Select>
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default PlatformUserFormDrawer;
