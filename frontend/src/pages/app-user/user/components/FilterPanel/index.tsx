import React, { useState, useEffect, useMemo } from 'react';
import { Popover, Button, Badge, Select, Space, Typography, message } from 'antd';
import { FilterOutlined, PlusOutlined } from '@ant-design/icons';
import { useUserManagementStore } from '@/store/userManagementStore';
import type { FilterCondition, FieldDefinition } from '@/store/userManagementStore';
import FilterConditionRow from './FilterConditionRow';

const { Text } = Typography;

function useDebounceValue<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [value, delay]);

  return debouncedValue;
}

const FilterPanel: React.FC = () => {
  const { 
    filters: storeFilters, 
    filterLogic: storeFilterLogic, 
    setFilterLogic: setStoreFilterLogic, 
    addFilter: addStoreFilter, 
    removeFilter: removeStoreFilter,
    updateFilter: updateStoreFilter,
    fieldDefinitions,
    currentViewId,
    updateView,
  } = useUserManagementStore();

  const [visible, setVisible] = useState(false);

  // Generate field options from store definitions
  const fieldOptions = useMemo(() => {
    return fieldDefinitions.map((f: FieldDefinition) => {
      let type = 'string';
      let enumValues: string[] | undefined = undefined;

      if (f.fieldType === 'DATE') type = 'date';
      else if (f.fieldType === 'RADIO' || f.fieldType === 'CHECKBOX') {
        type = 'enum';
        enumValues = f.config?.options?.map((o: any) => o.value) || [];
      }

      return {
        value: f.fieldKey,
        label: f.fieldName,
        type,
        enumValues
      };
    });
  }, [fieldDefinitions]);

  const handleLogicChange = (logic: 'AND' | 'OR') => {
    setStoreFilterLogic(logic);
    if (currentViewId) {
      updateView(currentViewId, { filterLogic: logic });
    }
  };

  const addCondition = () => {
    if (storeFilters.length >= 20) return;
    const defaultField = fieldOptions[0];
    if (!defaultField) return;

    addStoreFilter({ 
      id: Date.now().toString(), 
      field: defaultField.value, 
      operator: '等于', 
      value: undefined, 
      type: defaultField.type as any 
    });
  };

  const remove = (id: string) => {
    removeStoreFilter(id);
  };

  const update = (id: string, updates: Partial<FilterCondition>) => {
    updateStoreFilter(id, updates);
  };

  // Auto-save view when filters change (debounced)
  // We need to be careful not to create a loop.
  // Let's use a debounced effect to save the view if it's not default.
  const debouncedState = useDebounceValue(
    {
      filters: storeFilters,
      logic: storeFilterLogic,
      viewId: currentViewId,
    },
    800,
  );

  useEffect(() => {
    const { filters, logic, viewId } = debouncedState;
    if (!viewId) return;
    (async () => {
      try {
        await updateView(viewId, {
          filters,
          filterLogic: logic,
        });
      } catch (error) {
        // 不打断用户操作，但给出轻量提示，方便排查“条件丢失”
        // eslint-disable-next-line no-console
        console.error('Failed to save view filters', error);
        message.error('保存视图筛选条件失败，请稍后重试');
      }
    })();
  }, [debouncedState, updateView]);

  // Count valid filters (non-empty value or empty/not_empty operator)
  const validFilterCount = storeFilters.filter(f => {
    if (f.operator === '为空' || f.operator === '不为空') return true;
    if (Array.isArray(f.value)) return f.value.length > 0;
    return f.value !== '' && f.value !== null && f.value !== undefined;
  }).length;

  const content = (
    <div style={{ width: 620 }}>
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
        <Text style={{ color: "#666" }}>设置筛选条件</Text>
        <Space>
          <Text style={{ color: "#888" }}>符合以下</Text>
          <Select
            size="small"
            value={storeFilterLogic === 'AND' ? '所有' : '任一'}
            onChange={(v) => handleLogicChange(v === '所有' ? 'AND' : 'OR')}
            style={{ width: 70 }}
            options={[{ value: "所有", label: "所有" }, { value: "任一", label: "任一" }]}
          />
          <Text style={{ color: "#888" }}>条件</Text>
        </Space>
      </div>

      {/* Conditions */}
      <div style={{ minHeight: 120, maxHeight: 360, overflowY: "auto" }}>
        {storeFilters.length === 0 ? (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: 120, color: "#bbb", fontSize: 13 }}>
            暂无筛选条件，点击下方添加
          </div>
        ) : (
          <Space orientation="vertical" style={{ width: "100%" }} size={0}>
            {storeFilters.map((cond) => (
              <FilterConditionRow 
                key={cond.id} 
                condition={cond} 
                onChange={update} 
                onRemove={remove} 
              />
            ))}
          </Space>
        )}
      </div>

      {/* Add */}
      <div style={{ marginTop: 10 }}>
        <Button
          type="link"
          size="small"
          icon={<PlusOutlined />}
          onClick={addCondition}
          disabled={storeFilters.length >= 20}
          style={{ paddingLeft: 0 }}
        >
          添加条件
        </Button>
      </div>
    </div>
  );

  return (
    <Popover
      open={visible}
      onOpenChange={(v) => {
        setVisible(v);
      }}
      trigger="click"
      placement="bottomLeft"
      content={content}
    >
      <Badge count={validFilterCount} size="small" offset={[-4, 4]}>
        <Button type={validFilterCount > 0 ? "primary" : "default"} ghost={validFilterCount > 0} size="small" icon={<FilterOutlined />}>
          筛选
        </Button>
      </Badge>
    </Popover>
  );
};

export default FilterPanel;