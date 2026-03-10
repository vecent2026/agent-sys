import React from 'react';
import { Button, Space, Checkbox } from 'antd';
import { TagsOutlined, DeleteOutlined } from '@ant-design/icons';
import { AuthButton } from '@/components/AuthButton';

interface BatchBarProps {
  visible: boolean;
  selectedCount: number;
  totalCount: number;
  onBatchTag: (mode: 'add' | 'remove') => void;
  onSelectAll: (checked: boolean) => void;
}

const BatchBar: React.FC<BatchBarProps> = ({
  visible,
  selectedCount,
  totalCount,
  onBatchTag,
  onSelectAll,
}) => {
  if (!visible) return null;

  const isAllSelected = selectedCount === totalCount && totalCount > 0;
  const isPartialSelected = selectedCount > 0 && selectedCount < totalCount;

  return (
    <div style={{ height: 48, borderTop: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 16px', flexShrink: 0, background: '#fff' }}>
      <Space>
        <Checkbox
          checked={isAllSelected}
          indeterminate={isPartialSelected}
          onChange={(e) => onSelectAll(e.target.checked)}
        />
        <AuthButton perm="app:user:tag">
          <Button size="small" disabled={selectedCount === 0} icon={<TagsOutlined />} onClick={() => onBatchTag('add')}>
            打标签
          </Button>
        </AuthButton>
        <AuthButton perm="app:user:tag">
          <Button size="small" disabled={selectedCount === 0} icon={<DeleteOutlined />} onClick={() => onBatchTag('remove')}>
            移除标签
          </Button>
        </AuthButton>
        {selectedCount > 0 && (
          <span style={{ color: "#666", fontSize: 12 }}>已选 {selectedCount} 条</span>
        )}
      </Space>
    </div>
  );
};

export default BatchBar;
