import React, { useState, useEffect, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Switch, Modal, Form, Input,
  InputNumber, DatePicker, message, Popconfirm, Card, Row, Col, Select,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import {
  getTenantPage, createTenant, updateTenant, deleteTenant, changeTenantStatus,
  type TenantVo, type TenantDto,
} from '../../api/tenantApi';

const TENANT_CODE_PATTERN = /^[a-z0-9_-]{2,64}$/;

const TenantPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<TenantVo[]>([]);
  const [total, setTotal] = useState(0);
  const [pagination, setPagination] = useState({ page: 1, size: 10 });
  const [query, setQuery] = useState<{ tenantName?: string; tenantCode?: string; status?: number }>({});
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<TenantVo | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getTenantPage({ ...pagination, ...query });
      setData(res.records || []);
      setTotal(res.total || 0);
    } finally {
      setLoading(false);
    }
  }, [pagination, query]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ status: 1, maxUsers: 100 });
    setModalOpen(true);
  };

  const openEdit = (record: TenantVo) => {
    setEditing(record);
    form.setFieldsValue({
      ...record,
      expireTime: record.expireTime ? dayjs(record.expireTime) : null,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const dto: TenantDto = {
        ...values,
        // 使用本地时间格式，避免带时区后缀导致 Jackson 解析 LocalDateTime 失败
        expireTime: values.expireTime ? values.expireTime.format('YYYY-MM-DDTHH:mm:ss') : undefined,
      };
      if (editing) {
        await updateTenant(editing.id, dto);
        message.success('修改成功');
      } else {
        await createTenant(dto);
        message.success('创建成功');
      }
      setModalOpen(false);
      fetchData();
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    await deleteTenant(id);
    message.success('删除成功');
    fetchData();
  };

  const handleStatusChange = async (id: number, checked: boolean) => {
    await changeTenantStatus(id, checked ? 1 : 0);
    message.success('状态已更新');
    fetchData();
  };

  const [queryForm] = Form.useForm();
  const handleSearch = () => {
    const values = queryForm.getFieldsValue();
    setQuery(values);
    setPagination(p => ({ ...p, page: 1 }));
  };
  const handleReset = () => {
    queryForm.resetFields();
    setQuery({});
    setPagination(p => ({ ...p, page: 1 }));
  };

  const columns: ColumnsType<TenantVo> = [
    { title: '租户编码', dataIndex: 'tenantCode', width: 140 },
    { title: '租户名称', dataIndex: 'tenantName', width: 180 },
    {
      title: '状态', dataIndex: 'status', width: 80,
      render: (val, record) => (
        <Switch
          checked={val === 1}
          size="small"
          onChange={(checked) => handleStatusChange(record.id, checked)}
        />
      ),
    },
    {
      title: '到期时间', dataIndex: 'expireTime', width: 160,
      render: (val) => val ? dayjs(val).format('YYYY-MM-DD HH:mm') : <Tag color="green">永不过期</Tag>,
    },
    { title: '最大用户数', dataIndex: 'maxUsers', width: 100 },
    {
      title: '创建时间', dataIndex: 'createTime', width: 160,
      render: (val) => dayjs(val).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: '操作', width: 120, fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除该租户？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card size="small" style={{ marginBottom: 12 }}>
        <Form form={queryForm} layout="inline">
          <Form.Item name="tenantName" label="租户名称">
            <Input placeholder="请输入" allowClear style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="tenantCode" label="租户编码">
            <Input placeholder="请输入" allowClear style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="全部" allowClear style={{ width: 100 }}>
              <Select.Option value={1}>启用</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" onClick={handleSearch}>查询</Button>
              <Button onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card
        title="租户列表"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增租户</Button>}
      >
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={data}
          scroll={{ x: 900 }}
          pagination={{
            current: pagination.page,
            pageSize: pagination.size,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => setPagination({ page, size }),
          }}
        />
      </Card>

      <Modal
        title={editing ? '编辑租户' : '新增租户'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        width={520}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="tenantCode"
                label="租户编码"
                rules={[
                  { required: true, message: '请输入租户编码' },
                  { max: 64, message: '不超过64个字符' },
                  {
                    pattern: TENANT_CODE_PATTERN,
                    message: '仅允许小写字母、数字、下划线、连字符，长度2-64',
                  },
                ]}
              >
                <Input placeholder="如：my-tenant（小写字母/数字/-/_）" disabled={!!editing} maxLength={64} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="tenantName"
                label="租户名称"
                rules={[
                  { required: true, message: '请输入租户名称' },
                  { max: 128, message: '不超过128个字符' },
                  { whitespace: true, message: '名称不能为空白字符' },
                ]}
              >
                <Input placeholder="显示名称" maxLength={128} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="status" label="状态" initialValue={1}>
                <Select>
                  <Select.Option value={1}>启用</Select.Option>
                  <Select.Option value={0}>禁用</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="maxUsers"
                label="最大用户数"
                rules={[{ required: true, message: '请填写最大用户数' }]}
              >
                <InputNumber min={1} max={100000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="expireTime"
            label="到期时间（不填表示永不过期）"
          >
            <DatePicker
              showTime
              style={{ width: '100%' }}
              disabledDate={(d) => d && d.isBefore(dayjs(), 'day')}
              placeholder="留空表示永不过期"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TenantPage;
