import React from 'react';
import { Tag } from 'antd';

export type AgentStatus = 'healthy' | 'warning' | 'error' | 'stopped';

export interface StatusTagProps {
  status: AgentStatus;
}

const STATUS_MAP: Record<AgentStatus, { color: string; label: string }> = {
  healthy: { color: 'success', label: '健康' },
  warning: { color: 'warning', label: '警告' },
  error: { color: 'error', label: '错误' },
  stopped: { color: 'default', label: '已停止' },
};

export const StatusTag: React.FC<StatusTagProps> = ({ status }) => {
  const { color, label } = STATUS_MAP[status];
  return <Tag color={color}>{label}</Tag>;
};

