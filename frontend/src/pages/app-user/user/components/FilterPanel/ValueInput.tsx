import React from 'react';
import { Input, Select, DatePicker, InputNumber } from 'antd';
import dayjs from 'dayjs';
import { DATE_PRESETS } from '../../utils/filter-utils';

interface ValueInputProps {
  value: any;
  onChange: (value: any) => void;
  type: 'string' | 'enum' | 'date' | 'number';
  operator: string;
  fieldConfig?: any;
}

const ValueInput: React.FC<ValueInputProps> = ({ value, onChange, type, operator, fieldConfig }) => {
  if (['empty', 'not_empty'].includes(operator)) {
    return null;
  }

  if (type === 'string') {
    return (
      <Input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="请输入"
        style={{ width: 200 }}
      />
    );
  }

  if (type === 'number') {
    return (
      <InputNumber
        value={value}
        onChange={(val) => onChange(val)}
        placeholder="请输入"
        style={{ width: 200 }}
      />
    );
  }

  if (type === 'enum') {
    const options = fieldConfig?.options || [];
    const isMultiple = ['contains', 'not_contains'].includes(operator);
    
    return (
      <Select
        value={value}
        onChange={onChange}
        options={options}
        placeholder="请选择"
        style={{ width: 200 }}
        mode={isMultiple ? 'multiple' : undefined}
        allowClear
        maxTagCount={1}
      />
    );
  }

  if (type === 'date') {
    const currentValue = value || {};
    const presetType = currentValue.presetType || '指定时间';
    const dateValue = currentValue.value ? dayjs(currentValue.value) : undefined;

    const handlePresetChange = (newPresetType: string) => {
      if (newPresetType === '指定时间') {
        onChange({ presetType: newPresetType, value: undefined });
      } else {
        const preset = DATE_PRESETS[newPresetType as keyof typeof DATE_PRESETS];
        if (preset && preset.type === 'preset' && (preset as any).getValue) {
          onChange({ 
            presetType: newPresetType, 
            value: (preset as any).getValue().format('YYYY-MM-DD HH:mm:ss') 
          });
        }
      }
    };

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, width: 200 }}>
        <Select
          value={presetType}
          onChange={handlePresetChange}
          options={Object.keys(DATE_PRESETS).map(key => ({
            label: DATE_PRESETS[key as keyof typeof DATE_PRESETS].label,
            value: key,
          }))}
          style={{ width: '100%' }}
        />
        
        {presetType === '指定时间' && (
          <DatePicker
            value={dateValue}
            onChange={(date) => onChange({ presetType: '指定时间', value: date ? date.format('YYYY-MM-DD HH:mm:ss') : undefined })}
            style={{ width: '100%' }}
            showTime
          />
        )}
      </div>
    );
  }

  return null;
};

export default ValueInput;
