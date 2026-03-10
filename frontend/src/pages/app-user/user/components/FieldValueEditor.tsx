import React, { useState } from 'react';
import { Input, Select, Checkbox, Button, Space, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateAppUserFieldValues } from '@/api/app-user';
import type { FieldConfig } from '@/types/app-user';

interface FieldValueEditorProps {
  userId: number;
  fieldId: number;
  fieldType: string;
  fieldValue?: string | string[];
  config?: FieldConfig;
}

const FieldValueEditor: React.FC<FieldValueEditorProps> = ({
  userId,
  fieldId,
  fieldType,
  fieldValue,
  config,
}) => {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState<string | string[]>(fieldValue || '');

  const mutation = useMutation({
    mutationFn: (newValue: string | string[]) =>
      updateAppUserFieldValues(userId, {
        fieldValues: [{ fieldId, fieldValue: newValue }],
      }),
    onSuccess: () => {
      message.success('更新成功');
      setEditing(false);
      queryClient.invalidateQueries({ queryKey: ['app-user-field-values', userId] });
    },
  });

  const handleSave = () => {
    mutation.mutate(value);
  };

  const handleCancel = () => {
    setValue(fieldValue || '');
    setEditing(false);
  };

  const renderValueDisplay = () => {
    if (!fieldValue) return <span style={{ color: '#999' }}>-</span>;

    switch (fieldType) {
      case 'RADIO':
        const radioOption = config?.options?.find((o) => o.value === fieldValue);
        return <span>{radioOption?.label || fieldValue}</span>;
      case 'CHECKBOX':
        const values = Array.isArray(fieldValue) ? fieldValue : JSON.parse(fieldValue as string);
        const labels = values.map((v: string) => {
          const opt = config?.options?.find((o) => o.value === v);
          return opt?.label || v;
        });
        return <span>{labels.join(', ')}</span>;
      case 'LINK':
        return (
          <a href={fieldValue as string} target="_blank" rel="noopener noreferrer">
            {fieldValue}
          </a>
        );
      default:
        return <span>{fieldValue as string}</span>;
    }
  };

  const renderEditor = () => {
    switch (fieldType) {
      case 'RADIO':
        return (
          <Select
            value={value as string}
            onChange={setValue}
            style={{ width: 200 }}
            options={config?.options?.map((o) => ({ label: o.label, value: o.value }))}
          />
        );
      case 'CHECKBOX':
        return (
          <Checkbox.Group
            value={Array.isArray(value) ? value : JSON.parse(value as string)}
            onChange={(checkedValues) => setValue(checkedValues as string[])}
            options={config?.options?.map((o) => ({ label: o.label, value: o.value }))}
          />
        );
      case 'LINK':
        return (
          <Input
            value={value as string}
            onChange={(e) => setValue(e.target.value)}
            style={{ width: 300 }}
            placeholder="请输入链接地址"
          />
        );
      default:
        return (
          <Input
            value={value as string}
            onChange={(e) => setValue(e.target.value)}
            style={{ width: 300 }}
          />
        );
    }
  };

  if (!editing) {
    return (
      <Space>
        {renderValueDisplay()}
        <Button type="link" size="small" onClick={() => setEditing(true)}>
          编辑
        </Button>
      </Space>
    );
  }

  return (
    <Space>
      {renderEditor()}
      <Button type="primary" size="small" onClick={handleSave} loading={mutation.isPending}>
        保存
      </Button>
      <Button size="small" onClick={handleCancel}>
        取消
      </Button>
    </Space>
  );
};

export default FieldValueEditor;
