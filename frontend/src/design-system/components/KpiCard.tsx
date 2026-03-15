import React from 'react';
import { Card, Statistic, Typography } from 'antd';

const { Text } = Typography;

export interface KpiCardProps {
  title: string;
  value: number | string;
  suffix?: string;
  trendText?: string;
  trendType?: 'up' | 'down' | 'neutral';
}

export const KpiCard: React.FC<KpiCardProps> = ({ title, value, suffix, trendText, trendType = 'neutral' }) => {
  const trendColor =
    trendType === 'up' ? '#22C55E' : trendType === 'down' ? '#EF4444' : '#9CA3AF';

  return (
    <Card
      bordered={false}
      style={{
        height: '100%',
        background:
          'radial-gradient(circle at top left, rgba(37,99,235,0.35), rgba(15,23,42,0.95))',
      }}
      bodyStyle={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 8 }}
    >
      <Text style={{ fontSize: 12, color: 'rgba(226, 232, 240, 0.85)' }}>
        {title}
      </Text>
      <Statistic
        value={value}
        suffix={suffix}
        valueStyle={{ fontSize: 24, fontWeight: 600, color: '#F9FAFB' }}
      />
      {trendText && (
        <Text style={{ fontSize: 12, color: trendColor }}>{trendText}</Text>
      )}
    </Card>
  );
};

