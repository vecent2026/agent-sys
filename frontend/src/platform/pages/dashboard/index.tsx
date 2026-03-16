import React from 'react';
import { Card, Row, Col, Statistic, Typography } from 'antd';
import { TeamOutlined, ApartmentOutlined, SafetyOutlined } from '@ant-design/icons';
import { usePlatformUserStore } from '@/store/platformUserStore';

const { Title } = Typography;

const PlatformDashboard: React.FC = () => {
  const { userInfo, isSuper } = usePlatformUserStore();

  return (
    <div>
      <Title level={4} style={{ marginTop: 0, marginBottom: 24 }}>
        欢迎回来，{userInfo?.nickname || userInfo?.username}
        {isSuper && <span style={{ color: '#ff4d4f', fontSize: 12, marginLeft: 8 }}>[超管]</span>}
      </Title>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="租户总数"
              value={'-'}
              prefix={<ApartmentOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="平台用户"
              value={'-'}
              prefix={<TeamOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="权限节点"
              value={'-'}
              prefix={<SafetyOutlined />}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default PlatformDashboard;
