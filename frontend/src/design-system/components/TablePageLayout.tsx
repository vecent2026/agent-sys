import React from 'react';
import { Card } from 'antd';
import { SectionHeader } from './SectionHeader';

interface TablePageLayoutProps {
  title?: string;
  description?: string;
  headerExtra?: React.ReactNode;
  filterBar?: React.ReactNode;
  toolbar?: React.ReactNode;
  children: React.ReactNode; // 通常是 Table
  footer?: React.ReactNode; // 通常是分页
}

export const TablePageLayout: React.FC<TablePageLayoutProps> = ({
  title,
  description,
  headerExtra,
  filterBar,
  toolbar,
  children,
  footer,
}) => {
  return (
    <Card
      bordered={false}
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        borderRadius: 0,
        boxShadow: 'none',
      }}
      bodyStyle={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12, flex: 1, minHeight: 0 }}
    >
      {(title != null || description != null || headerExtra != null) && (
        <SectionHeader title={title ?? ''} description={description} extra={headerExtra} />
      )}
      {filterBar}
      {toolbar && (
        <div style={{ alignSelf: 'flex-start', width: 'fit-content' }}>
          {toolbar}
        </div>
      )}
      <div style={{ flex: 1, minHeight: 0 }}>{children}</div>
      {footer && <div style={{ marginTop: 8 }}>{footer}</div>}
    </Card>
  );
};

