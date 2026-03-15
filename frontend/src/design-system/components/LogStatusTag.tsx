import React from 'react';
import { Tag } from 'antd';
import { designTokens } from '@/design-system/theme';

export interface LogStatusTagProps {
  status: string;
}

export const LogStatusTag: React.FC<LogStatusTagProps> = ({ status }) => {
  const config = designTokens.logStatusMap[status] || { color: 'default', label: status };
  return <Tag color={config.color}>{config.label}</Tag>;
};
