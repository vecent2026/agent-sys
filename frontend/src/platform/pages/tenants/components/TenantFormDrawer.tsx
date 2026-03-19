import React, { useEffect } from 'react';
import { Drawer, Form, Input, DatePicker, InputNumber, Button, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  createTenant,
  updateTenant,
  type TenantDto,
  type TenantVo,
} from '../../../api/tenantApi';
import dayjs from 'dayjs';

interface TenantFormDrawerProps {
  open: boolean;
  onClose: () => void;
  editing?: TenantVo | null;
}

const TenantFormDrawer: React.FC<TenantFormDrawerProps> = ({ open, onClose, editing }) => {
  const [form] = Form.useForm();
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: (data: TenantDto) => createTenant(data),
    onSuccess: () => {
      message.success('创建成功');
      onClose();
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
    },
    onError: (err: any) => {
      message.error(err?.message || '创建失败');
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: TenantDto }) => updateTenant(id, data),
    onSuccess: () => {
      message.success('更新成功');
      onClose();
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
    },
    onError: (err: any) => {
      message.error(err?.message || '更新失败');
    },
  });

  useEffect(() => {
    if (open) {
      if (editing) {
        form.setFieldsValue({
          tenantName: editing.tenantName,
          tenantCode: editing.tenantCode,
          description: editing.description,
          contactName: editing.contactName,
          contactPhone: editing.contactPhone,
          contactEmail: editing.contactEmail,
          expireTime: editing.expireTime ? dayjs(editing.expireTime) : undefined,
          maxUsers: editing.maxUsers ?? undefined,
        });
      } else {
        form.resetFields();
        form.setFieldsValue({ maxUsers: 100 });
      }
    }
  }, [open, editing, form]);

  const handleSubmit = () => {
    form.validateFields().then((values) => {
      const expireTime = values.expireTime
        ? values.expireTime.format('YYYY-MM-DDTHH:mm:ss')
        : undefined;

      if (editing) {
        const data: TenantDto = {
          tenantName: values.tenantName,
          tenantCode: editing.tenantCode,
          description: values.description,
          contactName: values.contactName,
          contactPhone: values.contactPhone,
          contactEmail: values.contactEmail,
          expireTime,
          maxUsers: values.maxUsers ?? undefined,
        };
        updateMutation.mutate({ id: editing.id, data });
      } else {
        const adminMobile = values.adminMobile?.trim();
        const adminPassword = values.adminPassword?.trim();
        const data: TenantDto = {
          tenantCode: values.tenantCode,
          tenantName: values.tenantName,
          description: values.description,
          contactName: values.contactName,
          contactPhone: values.contactPhone,
          contactEmail: values.contactEmail,
          expireTime,
          maxUsers: values.maxUsers ?? undefined,
          adminUser: adminMobile
            ? {
                mobile: adminMobile,
                nickname: values.adminNickname?.trim() || adminMobile,
                password: adminPassword,
              }
            : undefined,
        };
        createMutation.mutate(data);
      }
    });
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;

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
          <Button type="primary" onClick={handleSubmit} loading={isLoading}>
            确定
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="tenantName"
          label="租户名称"
          rules={[
            { required: true, message: '请输入租户名称' },
            { max: 128, message: '不超过128个字符' },
          ]}
        >
          <Input placeholder="请输入租户名称" maxLength={128} />
        </Form.Item>

        <Form.Item
          name="tenantCode"
          label="租户编码"
          rules={[
            { required: !editing, message: '请输入租户编码' },
            { pattern: /^[a-z0-9_-]{2,64}$/, message: '仅允许小写字母、数字、下划线、连字符，长度2-64' },
          ]}
        >
          <Input placeholder="如：my-tenant（小写字母/数字/-/_）" disabled={!!editing} maxLength={64} />
        </Form.Item>

        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} placeholder="选填" maxLength={500} />
        </Form.Item>

        <Form.Item name="contactName" label="联系人">
          <Input placeholder="选填" maxLength={64} />
        </Form.Item>

        <Form.Item name="contactPhone" label="联系电话">
          <Input placeholder="选填" maxLength={32} />
        </Form.Item>

        <Form.Item
          name="contactEmail"
          label="联系邮箱"
          rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
        >
          <Input placeholder="选填" maxLength={128} />
        </Form.Item>

        <Form.Item name="expireTime" label="到期时间">
          <DatePicker
            showTime
            style={{ width: '100%' }}
            placeholder="不填则永不过期"
            disabledDate={(d) => d && d.isBefore(dayjs(), 'day')}
          />
        </Form.Item>

        <Form.Item
          name="maxUsers"
          label="最大用户数"
          rules={[{ type: 'number', min: 1, max: 100000, message: '须在 1~100000 之间' }]}
        >
          <InputNumber min={1} max={100000} style={{ width: '100%' }} placeholder="不填则不限" />
        </Form.Item>

        {!editing && (
          <>
            <Form.Item
              name="adminMobile"
              label="初始管理员手机号"
              rules={[
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    const password = getFieldValue('adminPassword')?.trim();
                    const mobile = value?.trim();
                    if (!mobile && !password) {
                      return Promise.resolve();
                    }
                    if (!mobile) {
                      return Promise.reject(new Error('请输入初始管理员手机号'));
                    }
                    if (!/^1[3-9]\d{9}$/.test(mobile)) {
                      return Promise.reject(new Error('请输入有效的11位手机号'));
                    }
                    return Promise.resolve();
                  },
                }),
              ]}
            >
              <Input placeholder="选填，创建后可再邀请管理员" maxLength={11} />
            </Form.Item>

            <Form.Item name="adminNickname" label="管理员昵称">
              <Input placeholder="选填，不填默认使用手机号" maxLength={50} />
            </Form.Item>

            <Form.Item
              name="adminPassword"
              label="初始管理员密码"
              dependencies={['adminMobile']}
              rules={[
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    const mobile = getFieldValue('adminMobile')?.trim();
                    const password = value?.trim();
                    if (!mobile && !password) {
                      return Promise.resolve();
                    }
                    if (!password) {
                      return Promise.reject(new Error('请输入初始管理员密码'));
                    }
                    if (password.length < 6 || password.length > 20) {
                      return Promise.reject(new Error('初始密码长度为 6-20 位'));
                    }
                    return Promise.resolve();
                  },
                }),
              ]}
            >
              <Input.Password placeholder="请输入初始管理员密码" maxLength={20} />
            </Form.Item>
          </>
        )}
      </Form>
    </Drawer>
  );
};

export default TenantFormDrawer;
