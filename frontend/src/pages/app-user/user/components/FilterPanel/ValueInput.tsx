import React from 'react';
import { Input, Select, DatePicker, InputNumber, Tag } from 'antd';
import dayjs from 'dayjs';
import { useQuery } from '@tanstack/react-query';
import { DATE_PRESETS } from '../../utils/filter-utils';
import { getTagCategoryList, getAppTagList } from '@/api/app-user';

export interface TagCascadeValue {
  categoryId: number | null;
  tagIds: number[];
}

interface ValueInputProps {
  value: any;
  onChange: (value: any) => void;
  type: 'string' | 'enum' | 'date' | 'number' | 'tagCascade';
  operator: string;
  fieldConfig?: any;
}

const ValueInput: React.FC<ValueInputProps> = ({ value, onChange, type, operator, fieldConfig }) => {
  if (['empty', 'not_empty'].includes(operator)) {
    return null;
  }

  if (type === 'tagCascade') {
    const tagValue: TagCascadeValue = value && typeof value === 'object' && 'tagIds' in value
      ? { categoryId: value.categoryId ?? null, tagIds: Array.isArray(value.tagIds) ? value.tagIds : [] }
      : { categoryId: null, tagIds: [] };

    const { data: categories } = useQuery({
      queryKey: ['tag-categories'],
      queryFn: getTagCategoryList,
    });
    const { data: tagData } = useQuery({
      queryKey: ['app-tags-filter', tagValue.categoryId],
      queryFn: () => getAppTagList({ page: 1, size: 500, categoryId: tagValue.categoryId ?? undefined, status: 1 }),
      enabled: tagValue.categoryId != null,
    });
    const tagOptions = tagData?.records?.map((t) => ({ label: t.name, value: t.id, color: t.color })) ?? [];

    return (
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', width: 200, flexWrap: 'nowrap' }}>
        <Select
          placeholder="分类"
          value={tagValue.categoryId}
          onChange={(categoryId) => onChange({ categoryId: categoryId ?? null, tagIds: [] })}
          options={[
            ...(categories ?? []).map((c) => ({ label: c.name, value: c.id })),
          ]}
          style={{ width: 88, flexShrink: 0 }}
          allowClear
        />
        <Select
          placeholder="标签"
          mode="multiple"
          value={tagValue.tagIds}
          onChange={(tagIds) => onChange({ ...tagValue, tagIds })}
          options={tagOptions}
          style={{ flex: 1, minWidth: 0 }}
          allowClear
          disabled={tagValue.categoryId == null}
          maxTagCount={1}
          optionRender={(opt) => (
            <Tag color={opt.data?.color ?? 'default'} style={{ margin: 0 }}>
              {opt.label}
            </Tag>
          )}
        />
      </div>
    );
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
