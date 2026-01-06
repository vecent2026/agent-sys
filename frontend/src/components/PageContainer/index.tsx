import React from 'react';
import { Card } from 'antd';

interface PageContainerProps {
  title?: string;
  children: React.ReactNode;
  extra?: React.ReactNode;
}

export const PageContainer: React.FC<PageContainerProps> = ({ title, children, extra }) => {
  // const location = useLocation();
  
  // Simple breadcrumb generation based on path
  // In a real app, you might want to map paths to names using the menu tree
  // const pathSnippets = location.pathname.split('/').filter((i) => i);
  // const breadcrumbItems = [
  //   { title: '首页' },
  //   ...pathSnippets.map((_, index) => {
  //     const url = `/${pathSnippets.slice(0, index + 1).join('/')}`;
  //     return {
  //       title: pathSnippets[index].charAt(0).toUpperCase() + pathSnippets[index].slice(1),
  //       href: url,
  //     };
  //   }),
  // ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* <Breadcrumb items={breadcrumbItems} style={{ marginBottom: 16 }} /> */}
      <Card title={title} extra={extra} variant="borderless" style={{ boxShadow: 'none' }}>
        {children}
      </Card>
    </div>
  );
};
