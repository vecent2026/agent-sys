import React from 'react';
import { Button, Space } from 'antd';
import { ImportOutlined, ExportOutlined } from '@ant-design/icons';
import FilterPanel from '../FilterPanel';
import FieldPanel from '../FieldPanel';

interface ActionBarProps {
  onImportClick: () => void;
  onExportClick: () => void;
}

const ActionBar: React.FC<ActionBarProps> = ({
  onImportClick,
  onExportClick,
}) => {
  return (
    <div style={{ height: 44, borderBottom: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', padding: '0 16px', gap: 4, flexShrink: 0, background: '#fff' }}>
      <Space size={4}>
        <FilterPanel />
        <FieldPanel />
        <Button size="small" icon={<ImportOutlined />} onClick={onImportClick}>
          导入
        </Button>
        <Button size="small" icon={<ExportOutlined />} onClick={onExportClick}>
          导出
        </Button>
      </Space>
    </div>
  );
};

export default ActionBar;
