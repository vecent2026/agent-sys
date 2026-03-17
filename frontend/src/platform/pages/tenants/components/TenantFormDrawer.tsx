import React, { useEffect } from 'react';
import { Drawer, Form, Input, DatePicker, InputNumber, Button, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  createPlatformTenant,
  updatePlatformTenant,
  type CreateTenantForm,
  type UpdateTenantForm,
  type PlatformTenant,
} from '../../../api/tenant';
import dayjs from 'dayjs';

interface TenantFormDrawerProps {
  open: boolean;
  onClose: () => void;
  editing?: PlatformTenant | null;
}

const TenantFormDrawer: React.FC<TenantFormDrawerProps> = ({
  open,
  onClose,
  editing,
}) => {
  const [form] = Form.useForm<CreateTenantForm>();
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: createPlatformTenant,
    onSuccess: () => {
      message.success('创建成功');
      onClose();
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateTenantForm }) =>
      updatePlatformTenant(id, data),
    onSuccess: () => {
      message.success('更新成功');
      onClose();
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
    },
  });

  useEffect(() => {
    if (open) {
      if (editing) {
        form.setFieldsValue({
          tenantName: editing.tenantName,
          description: editing.description,
          contactName: editing.contactName,
          contactPhone: editing.contactPhone,
          contactEmail: editing.contactEmail,
          expireTime: editing.expireTime ? dayjs(editing.expireTime) : undefined,
          maxUsers: editing.maxUsers ?? undefined,
        } as any);
      } else {
        form.resetFields();
      }
    }
  }, [open, editing, form]);

  const handleSubmit = () => {
    form.validateFields().then((values: any) => {
      const expireVal = values.expireTime;
      // 使用本地时间格式（不带时区），避免 Jackson 解析 LocalDateTime 失败
      const expireStr = expireVal && typeof expireVal.format === 'function'
        ? expireVal.format('YYYY-MM-DDTHH:mm:ss')
        : null;

      if (editing) {
        const data: UpdateTenantForm = {
          tenantName: values.tenantName,
          description: values.description,
          contactName: values.contactName,
          contactPhone: values.contactPhone,
          contactEmail: values.contactEmail,
          expireTime: expireStr,
          maxUsers: values.maxUsers ?? null,
        };
        updateMutation.mutate({ id: editing.id, data });
      } else {
        const payload: CreateTenantForm = {
          tenantCode: values.tenantCode,
          tenantName: values.tenantName,
          description: values.description,
          contactName: values.contactName,
          contactPhone: values.contactPhone,
          contactEmail: values.contactEmail,
          expireTime: expireStr,
          maxUsers: values.maxUsers ?? null,
          adminUser: {
            mobile: values.adminMobile,
            nickname: values.adminNickname,
          },
        };
        createMutation.mutate(payload);
      }
    });
  };

  return (
    <Drawer
      title={editing ? '编辑租户' : '创建租户'}
      open={open}
      onClose={onClose}
      width={480}
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
          name="tenantName"
          label="租户名称"
          rules={[{ required: true, message: '请输入租户名称' }, { max: 100, message: '最长100字' }]}
        >
          <Input placeholder="请输入租户名称" />
        </Form.Item>

        <Form.Item
          name="tenantCode"
          label="租户编码"
          rules={[
            { required: !editing, message: '请输入租户编码' },
            { pattern: /^[a-zA-Z0-9_]+$/, message: '仅支持字母、数字、下划线' },
            { max: 50, message: '最长50字' },
          ]}
        >
          <Input placeholder="如 tiannan" disabled={!!editing} />
        </Form.Item>

        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} placeholder="选填" />
        </Form.Item>

        <Form.Item name="contactName" label="联系人">
          <Input placeholder="选填" />
        </Form.Item>

        <Form.Item name="contactPhone" label="联系电话">
          <Input placeholder="选填" />
        </Form.Item>

        <Form.Item name="contactEmail" label="联系邮箱">
          <Input placeholder="选填" />
        </Form.Item>

        <Form.Item name="expireTime" label="到期时间">
          <DatePicker showTime style={{ width: '100%' }} placeholder="选填，不填则永久" />
        </Form.Item>

        <Form.Item name="maxUsers" label="最大用户数">
          <InputNumber min={1} style={{ width: '100%' }} placeholder="选填，不填则不限" />
        </Form.Item>

        {!editing && (
          <>
            <Form.Item
              name="adminMobile"
              label="初始管理员手机号"
              rules={[
                { required: true, message: '请输入管理员手机号' },
                { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的11位手机号' },
              ]}
            >
              <Input placeholder="作为初始管理员的登录账号" maxLength={11} />
            </Form.Item>

            <Form.Item
              name="adminNickname"
              label="管理员昵称"
            >
              <Input placeholder="选填，不填默认使用手机号" maxLength={50} />
            </Form.Item>
          </>
        )}
      </Form>
    </Drawer>
  );
};

export default TenantFormDrawer;
