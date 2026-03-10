import React, { useState } from 'react';
import { Popover, Button, Badge, Space, Typography } from 'antd';
import { EyeOutlined, EyeInvisibleOutlined, ColumnWidthOutlined } from '@ant-design/icons';
import { useUserManagementStore } from '@/store/userManagementStore';

const { Text } = Typography;

const FieldPanel: React.FC = () => {
  const { 
    fieldDefinitions, 
    hiddenFields, 
    toggleField,
    currentViewId,
    updateView,
    viewLoading,
  } = useUserManagementStore();
  const [visible, setVisible] = useState(false);

  const hiddenCount = hiddenFields.length;

  const toggle = async (fieldKey: string) => {
    if (viewLoading) return;
    
    // Calculate new hidden fields first
    const newHiddenFields = hiddenFields.includes(fieldKey)
      ? hiddenFields.filter(k => k !== fieldKey)
      : [...hiddenFields, fieldKey];
    
    toggleField(fieldKey);
    
    // If current view is not default, save changes to view
    if (currentViewId) {
      try {
        await updateView(currentViewId, {
          hiddenFields: newHiddenFields,
        });
      } catch (error) {
        // Error handling is done in store
      }
    }
  };

  const content = (
    <div style={{ width: 200 }}>
      <Text style={{ color: "#888", display: "block", marginBottom: 8 }}>字段显示</Text>
      <Space orientation="vertical" style={{ width: "100%" }} size={2}>
        {fieldDefinitions.length === 0 ? (
          <div style={{ textAlign: 'center', color: '#999', padding: '20px 0' }}>
            暂无字段配置
          </div>
        ) : (
          fieldDefinitions.map((field) => {
            const isHidden = hiddenFields.includes(field.fieldKey);
            return (
              <div
                key={field.fieldKey}
                onClick={() => toggle(field.fieldKey)}
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  padding: "6px 8px",
                  cursor: viewLoading ? 'not-allowed' : "pointer",
                  borderRadius: 4,
                  transition: "background 0.15s",
                  opacity: viewLoading ? 0.6 : 1,
                }}
              >
                <Text style={{ textDecoration: isHidden ? "line-through" : "none", color: isHidden ? "#bbb" : "#333" }}>
                  {field.fieldName}
                </Text>
                {isHidden ? <EyeInvisibleOutlined style={{ color: "#ccc" }} /> : <EyeOutlined style={{ color: "#999" }} />}
              </div>
            );
          })
        )}
      </Space>
    </div>
  );

  return (
    <Popover
      open={visible}
      onOpenChange={setVisible}
      trigger="click"
      placement="bottomLeft"
      content={content}
    >
      <Badge count={hiddenCount} size="small" offset={[-4, 4]}>
        <Button type={hiddenCount > 0 ? "primary" : "default"} ghost={hiddenCount > 0} size="small" icon={<ColumnWidthOutlined />}>
          字段
        </Button>
      </Badge>
    </Popover>
  );
};

export default FieldPanel;
