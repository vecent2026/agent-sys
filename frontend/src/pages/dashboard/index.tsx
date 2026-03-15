import React from 'react';
import { Button, ConfigProvider, DatePicker, Row, Col, Space, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { PageContainer } from '@/components/PageContainer';
import { KpiCard } from '@/design-system/components/KpiCard';
import { DashboardSection } from '@/design-system/components/DashboardSection';
import { appTheme } from '@/design-system/theme';

const { RangePicker } = DatePicker;
const { Title, Text } = Typography;

const Dashboard: React.FC = () => {
  return (
    <ConfigProvider theme={appTheme}>
      <PageContainer
        title={
          <Space direction="vertical" size={4}>
            <Title level={4} style={{ margin: 0 }}>
              智能体运行总览
            </Title>
            <Text type="secondary" style={{ fontSize: 12 }}>
              快速了解系统健康度与关键指标，并进入常用操作。
            </Text>
          </Space>
        }
        extra={
          <Space>
            <RangePicker />
            <Button type="primary" icon={<PlusOutlined />}>
              创建智能体
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" size={24} style={{ width: '100%' }}>
          {/* KPI 区域 */}
          <Row gutter={[16, 16]}>
            <Col xs={12} md={6}>
              <KpiCard title="在线智能体" value={12} trendText="较昨日 +2" trendType="up" />
            </Col>
            <Col xs={12} md={6}>
              <KpiCard title="今日请求总数" value={18234} />
            </Col>
            <Col xs={12} md={6}>
              <KpiCard title="成功率" value={98.3} suffix="%" trendText="近 7 日稳定" />
            </Col>
            <Col xs={12} md={6}>
              <KpiCard title="平均响应时间" value={236} suffix="ms" trendText="较上周 -12ms" trendType="up" />
            </Col>
          </Row>

          {/* 图表与状态分布占位区 */}
          <Row gutter={[16, 16]}>
            <Col xs={24} lg={16}>
              <DashboardSection title="调用趋势（占位）">
                <Text type="secondary">
                  这里可集成折线图或面积图，展示一段时间内的调用量与错误率趋势。
                </Text>
              </DashboardSection>
            </Col>
            <Col xs={24} lg={8}>
              <DashboardSection title="智能体状态分布（占位）">
                <Text type="secondary">
                  这里可集成饼图/环形图，并列出 Top N 问题智能体，支持快速跳转。
                </Text>
              </DashboardSection>
            </Col>
          </Row>
        </Space>
      </PageContainer>
    </ConfigProvider>
  );
};

export default Dashboard;
