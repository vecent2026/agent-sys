import React, { useState, useMemo } from 'react';
import { Table, Button, Form, Input, Select, Modal, message, Popconfirm, Space, List, Tag, Popover, Tooltip, Pagination } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, MoreOutlined, SearchOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getTagCategoryList, createTagCategory, updateTagCategory, deleteTagCategory, getAppTagList, createAppTag, updateAppTag, deleteAppTag, updateAppTagStatus } from '@/api/app-user';
import { AuthButton } from '@/components/AuthButton';
import type { AppUserTagDetail, AppUserTagCategory } from '@/types/app-user';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { TAG_COLORS } from '@/types/app-user';

// 颜色映射表（Ant Design Tag 颜色 -> CSS 颜色值）
const COLOR_MAP: Record<string, string> = {
  blue: '#1890ff',
  green: '#52c41a',
  orange: '#fa8c16',
  red: '#f5222d',
  purple: '#722ed1',
  cyan: '#13c2c2',
  magenta: '#eb2f96',
  volcano: '#fa541c',
  gold: '#faad14',
  lime: '#a0d911',
  geekblue: '#2f54eb',
  default: '#d9d9d9',
};

const TagManagement: React.FC = () => {
  const queryClient = useQueryClient();
  const [categoryForm] = Form.useForm();
  const [tagForm] = Form.useForm();
  const categoryColor = Form.useWatch('color', categoryForm);

  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [categoryModalVisible, setCategoryModalVisible] = useState(false);
  const [tagModalVisible, setTagModalVisible] = useState(false);
  const [editingCategory, setEditingCategory] = useState<AppUserTagCategory | null>(null);
  const [editingTag, setEditingTag] = useState<AppUserTagDetail | null>(null);
  const [searchName, setSearchName] = useState('');

  const [tagPagination, setTagPagination] = useState({
    current: 1,
    pageSize: 10,
  });

  const { data: categories } = useQuery({
    queryKey: ['tag-categories'],
    queryFn: getTagCategoryList,
  });

  const { data: tagData, isLoading: tagLoading } = useQuery({
    queryKey: ['app-tags', tagPagination, selectedCategoryId, searchName],
    queryFn: () =>
      getAppTagList({
        page: tagPagination.current,
        size: tagPagination.pageSize,
        categoryId: selectedCategoryId || undefined,
        name: searchName || undefined,
      }),
  });

  // 计算总标签数
  const totalTagCount = useMemo(() => {
    if (!categories) return 0;
    return categories.reduce((sum, cat) => sum + (cat.tagCount || 0), 0);
  }, [categories]);

  const sortedCategories = useMemo(() => {
    if (!categories) return [];
    return [...categories].sort((a, b) => (b.tagCount || 0) - (a.tagCount || 0));
  }, [categories]);

  const createCategoryMutation = useMutation({
    mutationFn: createTagCategory,
    onSuccess: () => {
      message.success('创建成功');
      setCategoryModalVisible(false);
      categoryForm.resetFields();
      queryClient.invalidateQueries({ queryKey: ['tag-categories'] });
    },
  });

  const updateCategoryMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: any }) => updateTagCategory(id, data),
    onSuccess: () => {
      message.success('更新成功');
      setCategoryModalVisible(false);
      categoryForm.resetFields();
      setEditingCategory(null);
      queryClient.invalidateQueries({ queryKey: ['tag-categories'] });
      queryClient.invalidateQueries({ queryKey: ['app-tags'] });
      queryClient.invalidateQueries();
    },
  });

  const deleteCategoryMutation = useMutation({
    mutationFn: deleteTagCategory,
    onSuccess: () => {
      message.success('删除成功');
      queryClient.invalidateQueries({ queryKey: ['tag-categories'] });
      if (selectedCategoryId === editingCategory?.id) {
        setSelectedCategoryId(null);
      }
    },
  });

  const createTagMutation = useMutation({
    mutationFn: createAppTag,
    onSuccess: () => {
      message.success('创建成功');
      setTagModalVisible(false);
      tagForm.resetFields();
      queryClient.invalidateQueries({ queryKey: ['app-tags'] });
      queryClient.invalidateQueries({ queryKey: ['tag-categories'] });
    },
  });

  const updateTagMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: any }) => updateAppTag(id, data),
    onSuccess: () => {
      message.success('更新成功');
      setTagModalVisible(false);
      tagForm.resetFields();
      setEditingTag(null);
      queryClient.invalidateQueries({ queryKey: ['app-tags'] });
      queryClient.invalidateQueries({ queryKey: ['tag-categories'] });
    },
  });

  const deleteTagMutation = useMutation({
    mutationFn: deleteAppTag,
    onSuccess: () => {
      message.success('删除成功');
      queryClient.invalidateQueries({ queryKey: ['app-tags'] });
      queryClient.invalidateQueries({ queryKey: ['tag-categories'] });
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: number }) => updateAppTagStatus(id, status),
    onSuccess: () => {
      message.success('状态更新成功');
      queryClient.invalidateQueries({ queryKey: ['app-tags'] });
    },
  });

  const handleAddCategory = () => {
    setEditingCategory(null);
    categoryForm.resetFields();
    categoryForm.setFieldsValue({ color: 'blue' });
    setCategoryModalVisible(true);
  };

  const handleEditCategory = (category: AppUserTagCategory) => {
    setEditingCategory(category);
    categoryForm.setFieldsValue(category);
    setCategoryModalVisible(true);
  };

  const handleDeleteCategory = (id: number) => {
    deleteCategoryMutation.mutate(id);
  };

  const handleCategorySubmit = async () => {
    const values = await categoryForm.validateFields();
    if (editingCategory) {
      updateCategoryMutation.mutate({ id: editingCategory.id, data: values });
    } else {
      createCategoryMutation.mutate(values);
    }
  };

  const handleAddTag = () => {
    setEditingTag(null);
    tagForm.resetFields();
    tagForm.setFieldsValue({ categoryId: selectedCategoryId });
    setTagModalVisible(true);
  };

  const handleEditTag = (tag: AppUserTagDetail) => {
    setEditingTag(tag);
    tagForm.setFieldsValue(tag);
    setTagModalVisible(true);
  };

  const handleDeleteTag = (id: number) => {
    deleteTagMutation.mutate(id);
  };

  const handleTagSubmit = async () => {
    const values = await tagForm.validateFields();
    if (editingTag) {
      updateTagMutation.mutate({ id: editingTag.id, data: values });
    } else {
      createTagMutation.mutate(values);
    }
  };

  const tagColumns: ColumnsType<AppUserTagDetail> = [
    {
      title: '标签名称',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      render: (text, record) => <Tag color={record.color}>{text}</Tag>,
    },
    {
      title: '所属分类',
      dataIndex: 'categoryName',
      key: 'categoryName',
      width: 120,
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      width: 260,
      render: (text: string) => (
        <Tooltip placement="topLeft" title={text}>
          <span
            style={{
              display: 'inline-block',
              maxWidth: '100%',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {text || '-'}
          </span>
        </Tooltip>
      ),
    },
    {
      title: '关联用户数',
      dataIndex: 'userCount',
      key: 'userCount',
      width: 100,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (text) => (text ? dayjs(text).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <AuthButton perm="app:tag:edit">
            <Button type="link" size="small" onClick={() => handleEditTag(record)}>
              编辑
            </Button>
          </AuthButton>
          <AuthButton perm="app:tag:status">
            <Popconfirm
              title={record.status === 1 ? '确定禁用吗？' : '确定启用吗？'}
              onConfirm={() => statusMutation.mutate({ id: record.id, status: record.status === 1 ? 0 : 1 })}
            >
              <Button
                type="link"
                size="small"
                style={{ color: record.status === 1 ? '#fa541c' : '#52c41a' }}
              >
                {record.status === 1 ? '禁用' : '启用'}
              </Button>
            </Popconfirm>
          </AuthButton>
          <AuthButton perm="app:tag:delete">
            <Popconfirm title="确定删除吗？" onConfirm={() => handleDeleteTag(record.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </AuthButton>
        </Space>
      ),
    },
  ];

  // 颜色选择器组件
  const ColorPicker: React.FC<{ value?: string; onChange?: (color: string) => void }> = (props) => {
    const { value, onChange } = props;
    
    return (
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {TAG_COLORS.map((c) => (
          <div
            key={c.value}
            onClick={() => onChange?.(c.value)}
            style={{
              width: 24,
              height: 24,
              borderRadius: '50%',
              backgroundColor: COLOR_MAP[c.value] || c.value,
              cursor: 'pointer',
              position: 'relative',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
            title={c.label}
          >
            {value === c.value && (
              <span style={{
                color: '#fff',
                fontSize: 12,
                fontWeight: 'bold',
              }}>✓</span>
            )}
          </div>
        ))}
      </div>
    );
  };

  // 分类颜色圆点
  const ColorDot = ({ color }: { color?: string }) => (
    <span
      style={{
        display: 'inline-block',
        width: 12,
        height: 12,
        borderRadius: '50%',
        backgroundColor: COLOR_MAP[color || 'blue'] || color || '#1890ff',
        marginRight: 8,
        flexShrink: 0,
      }}
    />
  );

  // 更多操作菜单
  const MoreActions = ({ category }: { category: AppUserTagCategory }) => (
    <Space direction="vertical" size="small" style={{ padding: '4px 0' }}>
      <AuthButton perm="app:tag:edit">
        <Button
          type="text"
          size="small"
          icon={<EditOutlined />}
          onClick={() => handleEditCategory(category)}
        >
          编辑
        </Button>
      </AuthButton>
      <AuthButton perm="app:tag:delete">
        <Popconfirm
          title="确定删除吗？"
          onConfirm={() => handleDeleteCategory(category.id)}
        >
          <Button
            type="text"
            size="small"
            danger
            icon={<DeleteOutlined />}
          >
            删除
          </Button>
        </Popconfirm>
      </AuthButton>
    </Space>
  );

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: 'calc(100vh - 196px)',
        overflow: 'hidden',
        background: '#fff',
        boxSizing: 'border-box',
      }}
    >
      <div
        style={{
          display: 'flex',
          flex: 1,
          minHeight: 0,
        }}
      >
        {/* 左侧标签分类 */}
        <div
          style={{
            width: 280,
            borderRight: '1px solid #f0f0f0',
            padding: '8px 8px 8px 0',
            boxSizing: 'border-box',
            display: 'flex',
            flexDirection: 'column',
            minHeight: 0,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 8,
              flexShrink: 0,
            }}
          >
            <span style={{ fontWeight: 500, fontSize: 13 }}>标签分类</span>
            <AuthButton perm="app:tag:add">
              <Button type="link" icon={<PlusOutlined />} onClick={handleAddCategory}>
                新增
              </Button>
            </AuthButton>
          </div>
          <List
            style={{ flex: 1, overflowY: 'auto' }}
            dataSource={[
              { id: 0, name: '全部', color: '', tagCount: totalTagCount } as AppUserTagCategory,
              ...(sortedCategories || []),
            ]}
            renderItem={(item) => {
              const isAll = item.id === 0;
              const isSelected = isAll ? selectedCategoryId === null : selectedCategoryId === item.id;
              return (
                <List.Item
                  onClick={() => setSelectedCategoryId(isAll ? null : item.id)}
                  style={{
                    cursor: 'pointer',
                    backgroundColor: isSelected ? '#e6f7ff' : 'transparent',
                    padding: '10px 12px',
                    borderRadius: 4,
                    transition: 'all 0.2s',
                  }}
                  actions={
                    isAll
                      ? []
                      : [
                          <Popover
                            content={<MoreActions category={item} />}
                            trigger="click"
                            placement="bottomRight"
                            key="more"
                          >
                            <Button
                              type="text"
                              size="small"
                              icon={<MoreOutlined />}
                              onClick={(e) => e.stopPropagation()}
                            />
                          </Popover>,
                        ]
                  }
                >
                  <div style={{ display: 'flex', alignItems: 'center', flex: 1 }}>
                    {isAll ? (
                      <span style={{ marginRight: 8, fontSize: 12 }}>●</span>
                    ) : (
                      <ColorDot color={item.color} />
                    )}
                    <span
                      style={{
                        flex: 1,
                        fontSize: 13,
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                      }}
                    >
                      {`${item.name}(${item.tagCount || 0})`}
                    </span>
                  </div>
                </List.Item>
              );
            }}
          />
        </div>

        {/* 右侧标签列表 */}
        <div
          style={{
            flex: 1,
            padding: '8px 0 8px 8px',
            boxSizing: 'border-box',
            display: 'flex',
            flexDirection: 'column',
            minHeight: 0,
          }}
        >
          {/* 顶部工具栏 */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
                marginBottom: 8,
              flexShrink: 0,
            }}
          >
            <Space>
              <span style={{ fontWeight: 500, fontSize: 13 }}>标签列表</span>
              {selectedCategoryId === null && <Tag color="blue">全部</Tag>}
            </Space>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                maxWidth: 420,
                flex: 1,
                justifyContent: 'flex-end',
              }}
            >
              {selectedCategoryId === null && (
                <Input
                  placeholder="搜索标签名称"
                  prefix={<SearchOutlined />}
                  value={searchName}
                  onChange={(e) => {
                    setSearchName(e.target.value);
                    setTagPagination({ current: 1, pageSize: 10 });
                  }}
                  allowClear
                  style={{
                    maxWidth: 260,
                    width: '100%',
                  }}
                />
              )}
              <AuthButton perm="app:tag:add">
                <Button type="primary" icon={<PlusOutlined />} onClick={handleAddTag}>
                  新增标签
                </Button>
              </AuthButton>
            </div>
          </div>

          {/* 表格区域：内部滚动 */}
          <div
            style={{
              flex: 1,
              minHeight: 0,
              display: 'flex',
              flexDirection: 'column',
              overflow: 'hidden',
            }}
          >
            <Table<AppUserTagDetail>
              columns={tagColumns}
              dataSource={tagData?.records}
              rowKey="id"
              loading={tagLoading}
              size="small"
              onRow={() => ({
                style: { height: 40 },
              })}
              // 表头固定，tbody 在内容区内部滚动，分页条固定在页面底部
              scroll={{ x: 'max-content', y: 'calc(100vh - 260px)' }}
              sticky
              pagination={false}
            />
          </div>
        </div>
      </div>

      {/* 底部分页条，固定在页面最底部 */}
      <div
        style={{
          height: 48,
          borderTop: '1px solid #f0f0f0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'flex-end',
          padding: '0 16px',
          flexShrink: 0,
          background: '#fff',
        }}
      >
        <Pagination
          current={tagPagination.current}
          pageSize={tagPagination.pageSize}
          total={tagData?.total}
          size="small"
          showSizeChanger
          showQuickJumper
          showTotal={(total) => `共 ${total} 条`}
          onChange={(page, size) => setTagPagination({ current: page, pageSize: size || tagPagination.pageSize })}
        />
      </div>

      {/* 分类编辑弹窗 */}
      <Modal
        title={editingCategory ? '编辑分类' : '新增分类'}
        open={categoryModalVisible}
        onOk={handleCategorySubmit}
        onCancel={() => setCategoryModalVisible(false)}
        confirmLoading={createCategoryMutation.isPending || updateCategoryMutation.isPending}
        destroyOnClose
      >
        <Form form={categoryForm} layout="vertical">
          <Form.Item name="name" label="分类名称" rules={[{ required: true, message: '请输入分类名称' }]}>
            <Input placeholder="请输入分类名称" />
          </Form.Item>
          <Form.Item name="color" label="分类颜色">
            <ColorPicker 
              value={categoryColor} 
              onChange={(color) => categoryForm.setFieldsValue({ color })} 
            />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea placeholder="请输入描述" rows={3} />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <Input type="number" placeholder="请输入排序号" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 标签编辑弹窗 */}
      <Modal
        title={editingTag ? '编辑标签' : '新增标签'}
        open={tagModalVisible}
        onOk={handleTagSubmit}
        onCancel={() => setTagModalVisible(false)}
        confirmLoading={createTagMutation.isPending || updateTagMutation.isPending}
        destroyOnClose
      >
        <Form form={tagForm} layout="vertical">
          <Form.Item name="categoryId" label="所属分类" rules={[{ required: true, message: '请选择分类' }]}>
            <Select
              placeholder="请选择分类"
              options={categories?.map((c) => ({ label: c.name, value: c.id }))}
            />
          </Form.Item>
          <Form.Item name="name" label="标签名称" rules={[{ required: true, message: '请输入标签名称' }]}>
            <Input placeholder="请输入标签名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea placeholder="请输入描述" rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TagManagement;
