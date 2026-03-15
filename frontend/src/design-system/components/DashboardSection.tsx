import React from 'react';
import { Card } from 'antd';

interface DashboardSectionProps {
  title: string;
  extra?: React.ReactNode;
  children: React.ReactNode;
}

export const DashboardSection: React.FC<DashboardSectionProps> = ({ title, extra, children }) => {
  return (
    <Card
      bordered={false}
      title={title}
      extra={extra}
      style={{ height: '100%' }}
      bodyStyle={{ padding: 16 }}
    >
      {children}
    </Card>
  );
};

