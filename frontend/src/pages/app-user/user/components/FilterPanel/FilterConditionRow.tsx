import React, { useMemo } from 'react';
import { Select, Button } from 'antd';
import { CloseOutlined } from '@ant-design/icons';
import { useUserManagementStore } from '@/store/userManagementStore';
import type { FilterCondition } from '@/store/userManagementStore';
import { FIELD_FILTER_CONFIG } from '../../utils/filter-utils';
import ValueInput from './ValueInput';

const TAG_FILTER_CONFIG = {
  operators: [{ label: '包含', value: 'contains' }],
  valueType: 'tagCascade' as const,
};

interface FilterConditionRowProps {
  condition: FilterCondition;
  onChange: (id: string, updates: Partial<FilterCondition>) => void;
  onRemove: (id: string) => void;
}

const FilterConditionRow: React.FC<FilterConditionRowProps> = ({ condition, onChange, onRemove }) => {
  const { fieldDefinitions } = useUserManagementStore();

  const fieldSelectOptions = useMemo(
    () => [
      ...fieldDefinitions.map(f => ({ label: f.fieldName, value: f.fieldKey })),
      { label: '标签', value: 'tags' },
    ],
    [fieldDefinitions],
  );

  const isTagField = condition.field === 'tags';
  const currentField = fieldDefinitions.find(f => f.fieldKey === condition.field);
  const fieldType = isTagField ? 'TAG_CASCADE' : (currentField?.fieldType || 'TEXT');
  const filterConfig = isTagField ? TAG_FILTER_CONFIG : (FIELD_FILTER_CONFIG[fieldType] || FIELD_FILTER_CONFIG['TEXT']);

  const handleFieldChange = (fieldKey: string) => {
    if (fieldKey === 'tags') {
      onChange(condition.id, {
        field: 'tags',
        operator: 'contains',
        value: undefined,
        type: 'tagCascade',
      });
      return;
    }
    const newField = fieldDefinitions.find(f => f.fieldKey === fieldKey);
    const newFieldType = newField?.fieldType || 'TEXT';
    const newFilterConfig = FIELD_FILTER_CONFIG[newFieldType] || FIELD_FILTER_CONFIG['TEXT'];
    onChange(condition.id, {
      field: fieldKey,
      operator: newFilterConfig.operators[0].value,
      value: undefined,
      type: newFilterConfig.valueType as any,
    });
  };

  return (
    <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start', marginBottom: 8 }}>
      <Select
        value={condition.field}
        onChange={handleFieldChange}
        options={fieldSelectOptions}
        style={{ width: 160 }}
        placeholder="选择字段"
        showSearch
        filterOption={(input, option) =>
          (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
        }
      />

      <Select
        value={condition.operator}
        onChange={(operator) => onChange(condition.id, { operator })}
        options={filterConfig.operators}
        style={{ width: 120 }}
      />

      <ValueInput
        value={condition.value}
        onChange={(value) => onChange(condition.id, { value })}
        type={filterConfig.valueType}
        operator={condition.operator}
        fieldConfig={currentField?.config}
      />

      <Button 
        type="text" 
        icon={<CloseOutlined />} 
        onClick={() => onRemove(condition.id)}
        danger
      />
    </div>
  );
};

export default FilterConditionRow;
