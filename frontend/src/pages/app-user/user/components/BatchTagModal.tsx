import React, { useEffect, useState } from 'react';
import { Modal, Select, message, Space, Tag, Typography, Divider } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getAppTagList } from '@/api/app-user';
import { TagOutlined, CheckCircleOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface BatchTagModalProps {
  visible: boolean;
  mode: 'add' | 'remove';
  onConfirm: (tagIds: number[]) => void;
  onCancel: () => void;
  loading: boolean;
}

const BatchTagModal: React.FC<BatchTagModalProps> = ({
  visible,
  mode,
  onConfirm,
  onCancel,
  loading,
}) => {
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([]);

  useEffect(() => {
    if (visible) {
      setSelectedTagIds([]);
    }
  }, [visible, mode]);

  const { data: tagData } = useQuery({
    queryKey: ['app-tags-all'],
    queryFn: () => getAppTagList({ page: 1, size: 100, status: 1 }),
    enabled: visible,
  });

  // 使用 key 属性来重置组件状态，避免在 effect 中调用 setState
  // 当 visible 变为 true 时，Modal 的 destroyOnClose 会重新创建组件

  const handleOk = () => {
    if (selectedTagIds.length === 0) {
      message.warning('请选择标签');
      return;
    }
    onConfirm(selectedTagIds);
    setSelectedTagIds([]);
  };

  const handleCancel = () => {
    setSelectedTagIds([]);
    onCancel();
  };

  const handleRemoveTag = (tagId: number) => {
    setSelectedTagIds((prev) => prev.filter((id) => id !== tagId));
  };

  const getTagInfo = (tagId: number) => {
    return tagData?.records?.find((tag) => tag.id === tagId);
  };

  const tagOptions = tagData?.records?.map((tag) => ({
    label: (
      <Space>
        <Tag color={tag.color} style={{ margin: 0 }}>
          {tag.name}
        </Tag>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {tag.categoryName}
        </Text>
      </Space>
    ),
    value: tag.id,
    searchLabel: `${tag.name} ${tag.categoryName}`,
  }));

  return (
    <Modal
      title={
        <Space>
          <TagOutlined />
          {mode === 'add' ? '批量打标签' : '批量移除标签'}
        </Space>
      }
      open={visible}
      onOk={handleOk}
      onCancel={handleCancel}
      confirmLoading={loading}
      destroyOnClose
      width={520}
      okText={mode === 'add' ? '添加标签' : '移除标签'}
      okButtonProps={{
        icon: <CheckCircleOutlined />,
        disabled: selectedTagIds.length === 0,
      }}
    >
      <div style={{ padding: '8px 0' }}>
        {/* 标签选择 */}
        <div>
          <Text style={{ display: 'block', marginBottom: 8 }}>
            {mode === 'add' ? '选择要添加的标签：' : '选择要移除的标签：'}
          </Text>
          <Select
            mode="multiple"
            style={{ width: '100%' }}
            placeholder="请选择标签（可多选）"
            value={selectedTagIds}
            onChange={setSelectedTagIds}
            options={tagOptions}
            optionFilterProp="searchLabel"
            showSearch
            allowClear
            maxTagCount={0} // 隐藏默认的标签渲染，使用下方自定义预览
            dropdownStyle={{ maxHeight: 300 }}
            notFoundContent="暂无标签"
          />
        </div>

        {/* 已选标签预览（展示在下拉框下方） */}
        {selectedTagIds.length > 0 && (
          <>
            <Divider style={{ margin: '16px 0' }} />
            <div style={{ marginBottom: 12 }}>
              <Text type="secondary" style={{ fontSize: 13 }}>
                已选择 {selectedTagIds.length} 个标签：
              </Text>
            </div>
            <Space wrap size={8} style={{ marginBottom: 16 }}>
              {selectedTagIds.map((tagId) => {
                const tag = getTagInfo(tagId);
                return tag ? (
                  <Tag
                    key={tagId}
                    color={tag.color}
                    closable
                    onClose={() => handleRemoveTag(tagId)}
                    style={{ padding: '4px 12px', fontSize: 13 }}
                  >
                    {tag.name}
                  </Tag>
                ) : null;
              })}
            </Space>
          </>
        )}

        {/* 提示信息 */}
        <div
          style={{
            marginTop: 16,
            padding: '12px 16px',
            background: mode === 'add' ? '#f6ffed' : '#fff7e6',
            borderRadius: 6,
            border: `1px solid ${mode === 'add' ? '#b7eb8f' : '#ffd591'}`,
          }}
        >
          <Text
            type={mode === 'add' ? 'success' : 'warning'}
            style={{ fontSize: 13 }}
          >
            {mode === 'add'
              ? '提示：选中的标签将被添加到所有已选择的用户上，已有标签不会重复添加。'
              : '提示：选中的标签将从所有已选择的用户上移除，不影响其他标签。'}
          </Text>
        </div>
      </div>
    </Modal>
  );
};

export default BatchTagModal;
