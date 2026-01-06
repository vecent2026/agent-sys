import React, { useState, useMemo } from 'react';
import { Modal, Input, Empty } from 'antd';
import * as Icons from '@ant-design/icons';
import { Grid } from 'react-window';

interface IconPickerProps {
  value?: string;
  onChange?: (value: string) => void;
}

const allIcons = Object.keys(Icons)
  .filter((key) => typeof (Icons as any)[key] === 'object')
  .filter((key) => !['default', 'createFromIconfontCN', 'getTwoToneColor', 'setTwoToneColor'].includes(key));

export const IconPicker: React.FC<IconPickerProps> = ({ value, onChange }) => {
  const [visible, setVisible] = useState(false);
  const [searchValue, setSearchValue] = useState('');

  const filteredIcons = useMemo(() => {
    if (!searchValue) return allIcons;
    return allIcons.filter((icon) => icon.toLowerCase().includes(searchValue.toLowerCase()));
  }, [searchValue]);

  const handleSelect = (icon: string) => {
    onChange?.(icon);
    setVisible(false);
  };

  const SelectedIcon = value ? (Icons as any)[value] : null;

  const Cell = ({ columnIndex, rowIndex, style, icons, onSelect }: any) => {
    const index = rowIndex * 4 + columnIndex;
    if (index >= icons.length) {
      return <div style={style} />;
    }
    
    const iconName = icons[index];
    const Icon = (Icons as any)[iconName];

    return (
      <div
        style={{
          ...style,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          border: '1px solid #f0f0f0',
          margin: 4,
          borderRadius: 4,
        }}
        onClick={() => onSelect(iconName)}
        title={iconName}
      >
        <Icon style={{ fontSize: 24, marginBottom: 8 }} />
        <span style={{ fontSize: 10, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', width: '100%', textAlign: 'center' }}>
          {iconName}
        </span>
      </div>
    );
  };

  return (
    <>
      <Input
        readOnly
        value={value}
        onClick={() => setVisible(true)}
        placeholder="点击选择图标"
        prefix={SelectedIcon ? <SelectedIcon /> : null}
        style={{ cursor: 'pointer' }}
      />
      <Modal
        title="选择图标"
        open={visible}
        onCancel={() => setVisible(false)}
        footer={null}
        width={600}
      >
        <Input.Search
          placeholder="搜索图标"
          allowClear
          onChange={(e) => setSearchValue(e.target.value)}
          style={{ marginBottom: 16 }}
        />
        {filteredIcons.length > 0 ? (
          <Grid
            columnCount={4}
            columnWidth={135}
            defaultHeight={400}
            rowCount={Math.ceil(filteredIcons.length / 4)}
            rowHeight={100}
            defaultWidth={550}
            cellComponent={Cell}
            cellProps={{ icons: filteredIcons, onSelect: handleSelect }}
          />
        ) : (
          <Empty description="未找到图标" />
        )}
      </Modal>
    </>
  );
};
