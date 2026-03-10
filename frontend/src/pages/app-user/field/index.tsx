import React, { useState } from 'react';
import {
  Table,
  Button,
  Form,
  Input,
  Select,
  Drawer,
  Space,
  message,
  Popconfirm,
  Card,
  Tag,
  Tooltip,
  Divider,
  InputNumber,
  Switch,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  HolderOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getAppFieldList,
  createAppField,
  updateAppField,
  deleteAppField,
  updateAppFieldStatus,
  sortAppFields,
} from '@/api/app-user';
import { PageContainer } from '@/components/PageContainer';
import { AuthButton } from '@/components/AuthButton';
import type { AppUserField, AppUserFieldQuery } from '@/types/app-user';
import type { ColumnsType } from 'antd/es/table';
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import type { DragEndEvent } from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

const { Option } = Select;

interface SortableRowProps extends React.HTMLAttributes<HTMLTableRowElement> {
  'data-row-key': string;
}

const SortableRow: React.FC<SortableRowProps> = (props) => {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: props['data-row-key'],
  });

  const style: React.CSSProperties = {
    ...props.style,
    transform: CSS.Transform.toString(transform),
    transition,
    ...(isDragging
      ? { position: 'relative', zIndex: 9999, background: '#fafafa' }
      : {}),
  };

  return (
    <tr {...props} ref={setNodeRef} style={style} {...attributes} {...listeners}>
      {props.children}
    </tr>
  );
};

const FieldTypeConfig: React.FC<{
  fieldType: string;
  value?: Record<string, unknown>;
  onChange?: (value: Record<string, unknown>) => void;
}> = ({ fieldType, value = {}, onChange }) => {
  const options = (value.options as { label: string; value: string }[]) || [];

  const handleOptionsChange = (newOptions: { label: string; value: string }[]) => {
    onChange?.({ ...value, options: newOptions });
  };

  const addOption = () => {
    const newOption = {
      label: `选项${options.length + 1}`,
      value: `option_${Date.now()}`,
    };
    handleOptionsChange([...options, newOption]);
  };

  const updateOption = (
    index: number,
    field: 'label' | 'value',
    newValue: string
  ) => {
    const newOptions = [...options];
    newOptions[index] = { ...newOptions[index], [field]: newValue };
    handleOptionsChange(newOptions);
  };

  const removeOption = (index: number) => {
    const newOptions = options.filter((_, i) => i !== index);
    handleOptionsChange(newOptions);
  };

  if (fieldType === 'RADIO' || fieldType === 'CHECKBOX') {
    return (
      <div>
        <div style={{ marginBottom: 8 }}>
          <Button type="dashed" onClick={addOption} size="small">
            添加选项
          </Button>
        </div>
        {options.map((opt, index) => (
          <div key={index} style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
            <Input
              placeholder="选项名称"
              value={opt.label}
              onChange={(e) => updateOption(index, 'label', e.target.value)}
              style={{ width: 120 }}
            />
            <Input
              placeholder="选项值"
              value={opt.value}
              onChange={(e) => updateOption(index, 'value', e.target.value)}
              style={{ width: 120 }}
            />
            <Button danger size="small" onClick={() => removeOption(index)}>
              删除
            </Button>
          </div>
        ))}
      </div>
    );
  }

  if (fieldType === 'TEXT') {
    return (
      <div>
        <Form.Item label="最大长度">
          <InputNumber
            min={1}
            max={500}
            value={(value.maxLength as number) || 100}
            onChange={(v) => onChange?.({ ...value, maxLength: v })}
          />
        </Form.Item>
        <Form.Item label="正则校验">
          <Input
            placeholder="如：^[a-zA-Z0-9]+$"
            value={(value.pattern as string) || ''}
            onChange={(e) => onChange?.({ ...value, pattern: e.target.value })}
          />
        </Form.Item>
        <Form.Item label="校验提示">
          <Input
            placeholder="校验失败时的提示信息"
            value={(value.patternMessage as string) || ''}
            onChange={(e) =>
              onChange?.({ ...value, patternMessage: e.target.value })
            }
          />
        </Form.Item>
      </div>
    );
  }

  if (fieldType === 'LINK') {
    return (
      <div>
        <Form.Item label="链接类型">
          <Select
            value={(value.linkType as string) || 'url'}
            onChange={(v) => onChange?.({ ...value, linkType: v })}
            style={{ width: 200 }}
          >
            <Option value="url">网页链接</Option>
            <Option value="image">图片链接</Option>
            <Option value="video">视频链接</Option>
          </Select>
        </Form.Item>
        <Form.Item label="占位提示">
          <Input
            placeholder="请输入链接地址"
            value={(value.placeholder as string) || ''}
            onChange={(e) =>
              onChange?.({ ...value, placeholder: e.target.value })
            }
          />
        </Form.Item>
      </div>
    );
  }

  return null;
};

interface FieldFormData {
  fieldName: string;
  fieldKey: string;
  fieldType: string;
  config?: Record<string, unknown>;
  isRequired?: number;
  status?: number;
}

const FieldList: React.FC = () => {
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const [searchForm] = Form.useForm();
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [fieldType, setFieldType] = useState<string>('TEXT');
  const [fieldConfig, setFieldConfig] = useState<Record<string, unknown>>({});

  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [searchParams, setSearchParams] = useState<AppUserFieldQuery>({
    page: 1,
    size: 10,
  });

  const { data: fieldData, isLoading } = useQuery({
    queryKey: ['app-fields', pagination, searchParams],
    queryFn: () => getAppFieldList(searchParams),
  });

  const createMutation = useMutation({
    mutationFn: (data: FieldFormData) => createAppField(data),
    onSuccess: () => {
      message.success('创建成功');
      setDrawerVisible(false);
      form.resetFields();
      setFieldConfig({});
      queryClient.invalidateQueries({ queryKey: ['app-fields'] });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: FieldFormData }) =>
      updateAppField(id, data),
    onSuccess: () => {
      message.success('更新成功');
      setDrawerVisible(false);
      form.resetFields();
      setEditingId(null);
      setFieldConfig({});
      queryClient.invalidateQueries({ queryKey: ['app-fields'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteAppField(id),
    onSuccess: () => {
      message.success('删除成功');
      queryClient.invalidateQueries({ queryKey: ['app-fields'] });
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: number }) =>
      updateAppFieldStatus(id, status),
    onSuccess: () => {
      message.success('状态更新成功');
      queryClient.invalidateQueries({ queryKey: ['app-fields'] });
    },
  });

  const sortMutation = useMutation({
    mutationFn: (fieldIds: number[]) => sortAppFields(fieldIds),
    onSuccess: () => {
      message.success('排序保存成功');
      queryClient.invalidateQueries({ queryKey: ['app-fields'] });
    },
  });

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 1,
      },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;

    if (over && active.id !== over.id) {
      const records = fieldData?.records || [];
      const draggedRecord = records.find((r: AppUserField) => String(r.id) === active.id);
      
      if (draggedRecord && draggedRecord.fieldKey === 'nickname') {
        return;
      }

      const oldIndex = records.findIndex(
        (r: AppUserField) => String(r.id) === active.id
      );
      const newIndex = records.findIndex(
        (r: AppUserField) => String(r.id) === over.id
      );

      const newRecords = arrayMove(records, oldIndex, newIndex);
      const fieldIds = newRecords.map((r: AppUserField) => r.id);
      sortMutation.mutate(fieldIds);
    }
  };

  const handleAdd = () => {
    setEditingId(null);
    form.resetFields();
    setFieldType('TEXT');
    setFieldConfig({});
    setDrawerVisible(true);
  };

  const handleEdit = (record: AppUserField) => {
    setEditingId(record.id);
    setFieldType(record.fieldType);
    setFieldConfig((record.config as Record<string, unknown>) || {});
    form.setFieldsValue({
      fieldName: record.fieldName,
      fieldKey: record.fieldKey,
      fieldType: record.fieldType,
      isRequired: record.isRequired,
      status: record.status,
    });
    setDrawerVisible(true);
  };

  const handleDelete = (id: number) => {
    deleteMutation.mutate(id);
  };

  const handleStatusChange = (id: number, checked: boolean) => {
    statusMutation.mutate({ id, status: checked ? 1 : 0 });
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const submitData = {
        ...values,
        config: fieldConfig,
      };

      if (editingId) {
        updateMutation.mutate({ id: editingId, data: submitData });
      } else {
        createMutation.mutate(submitData);
      }
    } catch (error) {
      console.error('Form validation error:', error);
    }
  };

  const handleSearch = (values: Record<string, unknown>) => {
    const params: AppUserFieldQuery = {
      page: 1,
      size: pagination.pageSize,
      name: values.name as string,
      type: values.type as string,
      status: values.status as number,
    };
    setSearchParams(params);
    setPagination({ ...pagination, current: 1 });
  };

  const columns: ColumnsType<AppUserField> = [
    {
      key: 'drag',
      width: 40,
      render: (_, record) => {
        const isNickname = record.fieldKey === 'nickname';
        const isDefaultField = record.isDefault === 1;
        if (isNickname) {
          return <HolderOutlined style={{ cursor: 'not-allowed', color: '#ccc' }} />;
        }
        return <HolderOutlined style={{ cursor: 'grab', color: isDefaultField ? '#999' : '#999' }} />;
      },
    },
    {
      title: '字段名称',
      dataIndex: 'fieldName',
      key: 'fieldName',
      width: 180,
      render: (text, record) => (
        <Space>
          {text}
          {record.isDefault === 1 && <Tag color="blue">系统</Tag>}
        </Space>
      ),
    },
    {
      title: '字段标识',
      dataIndex: 'fieldKey',
      key: 'fieldKey',
      width: 150,
      render: (text) => (
        <code style={{ background: '#f5f5f5', padding: '2px 6px', borderRadius: 4 }}>
          {text}
        </code>
      ),
    },
    {
      title: '字段类型',
      dataIndex: 'fieldType',
      key: 'fieldType',
      width: 100,
      render: (type) => {
        const typeMap: Record<string, { text: string; color: string }> = {
          TEXT: { text: '文本', color: 'blue' },
          RADIO: { text: '单选', color: 'green' },
          CHECKBOX: { text: '多选', color: 'orange' },
          LINK: { text: '链接', color: 'purple' },
        };
        const { text, color } = typeMap[type] || { text: type, color: 'default' };
        return <Tag color={color}>{text}</Tag>;
      },
    },
    {
      title: '必填',
      dataIndex: 'isRequired',
      key: 'isRequired',
      width: 80,
      align: 'center',
      render: (value) => (
        <Tag color={value === 1 ? 'red' : 'default'}>
          {value === 1 ? '必填' : '选填'}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      align: 'center',
      render: (status, record) => (
        <Switch
          checked={status === 1}
          onChange={(checked) => handleStatusChange(record.id, checked)}
          loading={statusMutation.isPending && statusMutation.variables?.id === record.id}
          disabled={record.isDefault === 1}
        />
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 170,
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          {record.isDefault !== 1 && (
            <AuthButton perm="app:field:edit">
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={() => handleEdit(record)}
              >
                编辑
              </Button>
            </AuthButton>
          )}
          {record.isDefault !== 1 && (
            <AuthButton perm="app:field:remove">
              <Popconfirm
                title="确定删除该字段吗？删除后用户数据中的该字段值也将被删除。"
                onConfirm={() => handleDelete(record.id)}
              >
                <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                  删除
                </Button>
              </Popconfirm>
            </AuthButton>
          )}
        </Space>
      ),
    },
  ];

  const records = fieldData?.records || [];
  const sortableIds = records
    .filter((r: AppUserField) => r.fieldKey !== 'nickname')
    .map((r: AppUserField) => String(r.id));

  return (
    <PageContainer title="字段管理">
      <Card style={{ marginBottom: 16 }}>
        <Form form={searchForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="name" label="字段名称">
            <Input placeholder="请输入" allowClear style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="type" label="字段类型">
            <Select placeholder="请选择" allowClear style={{ width: 120 }}>
              <Option value="TEXT">文本</Option>
              <Option value="RADIO">单选</Option>
              <Option value="CHECKBOX">多选</Option>
              <Option value="LINK">链接</Option>
            </Select>
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="请选择" allowClear style={{ width: 100 }}>
              <Option value={1}>启用</Option>
              <Option value={0}>禁用</Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
              <Button onClick={() => searchForm.resetFields()}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card
        title="字段列表"
        extra={
          <Space>
            <Tooltip title="拖拽行可调整字段顺序">
              <QuestionCircleOutlined style={{ color: '#999' }} />
            </Tooltip>
            <AuthButton perm="app:field:add">
              <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
                新增字段
              </Button>
            </AuthButton>
          </Space>
        }
      >
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext items={sortableIds} strategy={verticalListSortingStrategy}>
            <Table
              columns={columns}
              dataSource={records}
              rowKey={(record) => String(record.id)}
              loading={isLoading}
              components={{
                body: {
                  row: SortableRow,
                },
              }}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                total: fieldData?.total,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total) => `共 ${total} 条`,
                onChange: (page, size) => {
                  setPagination({ current: page, pageSize: size });
                  setSearchParams({ ...searchParams, page, size });
                },
              }}
            />
          </SortableContext>
        </DndContext>
      </Card>

      <Drawer
        title={editingId ? '编辑字段' : '新增字段'}
        open={drawerVisible}
        onClose={() => {
          setDrawerVisible(false);
          setEditingId(null);
          form.resetFields();
          setFieldConfig({});
        }}
        width={500}
        destroyOnClose
        footer={
          <div style={{ textAlign: 'right' }}>
            <Space>
              <Button
                onClick={() => {
                  setDrawerVisible(false);
                  setEditingId(null);
                  form.resetFields();
                  setFieldConfig({});
                }}
              >
                取消
              </Button>
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
          <Form.Item
            name="fieldName"
            label="字段名称"
            rules={[{ required: true, message: '请输入字段名称' }]}
          >
            <Input placeholder="如：公司名称" />
          </Form.Item>
          <Form.Item
            name="fieldKey"
            label="字段标识"
            rules={[
              { required: true, message: '请输入字段标识' },
              {
                pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/,
                message: '以字母开头，仅允许字母数字下划线',
              },
            ]}
            extra={
              <span style={{ color: '#999', fontSize: 12 }}>
                字段标识用于程序中引用该字段，创建后不可修改
              </span>
            }
          >
            <Input placeholder="如：company_name" disabled={!!editingId} />
          </Form.Item>
          <Form.Item
            name="fieldType"
            label="字段类型"
            rules={[{ required: true, message: '请选择字段类型' }]}
          >
            <Select
              placeholder="请选择"
              onChange={(v) => setFieldType(v)}
              disabled={!!editingId}
            >
              <Option value="TEXT">文本</Option>
              <Option value="RADIO">单选</Option>
              <Option value="CHECKBOX">多选</Option>
              <Option value="LINK">链接</Option>
            </Select>
          </Form.Item>

          <Divider titlePlacement="left" plain style={{ margin: '16px 0' }}>
            字段配置
          </Divider>

          <FieldTypeConfig
            fieldType={fieldType}
            value={fieldConfig}
            onChange={setFieldConfig}
          />

          <Divider titlePlacement="left" plain style={{ margin: '16px 0' }}>
            其他设置
          </Divider>

          <Form.Item name="isRequired" label="是否必填" initialValue={0}>
            <Select>
              <Option value={1}>必填</Option>
              <Option value={0}>选填</Option>
            </Select>
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select>
              <Option value={1}>启用</Option>
              <Option value={0}>禁用</Option>
            </Select>
          </Form.Item>
        </Form>
      </Drawer>
    </PageContainer>
  );
};

export default FieldList;
