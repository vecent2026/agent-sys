import React from 'react';
import { Space, Typography } from 'antd';

const { Title, Text } = Typography;

export interface SectionHeaderProps {
  title: string;
  description?: string;
  extra?: React.ReactNode;
}

export const SectionHeader: React.FC<SectionHeaderProps> = ({ title, description, extra }) => {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: 16,
        marginBottom: 16,
      }}
    >
      <Space direction="vertical" size={4}>
        <Title level={4} style={{ margin: 0 }}>
          {title}
        </Title>
        {description && (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {description}
          </Text>
        )}
      </Space>
      {extra && <div>{extra}</div>}
    </div>
  );
};

