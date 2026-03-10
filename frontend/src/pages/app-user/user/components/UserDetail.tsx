import React from 'react';
import { Descriptions, Tag, Space, Divider, Card, Avatar, Typography, Row, Col, Skeleton } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getAppUserDetail, getAppUserFieldValues } from '@/api/app-user';
import type { UserFieldValue } from '@/types/app-user';
import dayjs from 'dayjs';
import { REGISTER_SOURCES, USER_STATUS } from '@/types/app-user';
import FieldValueEditor from './FieldValueEditor';
import {
  UserOutlined,
  PhoneOutlined,
  MailOutlined,
  CalendarOutlined,
  GlobalOutlined,
  TagOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';

const { Title, Text } = Typography;

interface UserDetailProps {
  userId: number;
}

const UserDetail: React.FC<UserDetailProps> = ({ userId }) => {
  const { data: user, isLoading } = useQuery({
    queryKey: ['app-user-detail', userId],
    queryFn: () => getAppUserDetail(userId),
  });

  const { data: fieldValues } = useQuery({
    queryKey: ['app-user-field-values', userId],
    queryFn: () => getAppUserFieldValues(userId),
  });

  if (isLoading) {
    return (
      <div style={{ padding: 24 }}>
        <Skeleton avatar paragraph={{ rows: 4 }} active />
        <Divider />
        <Skeleton paragraph={{ rows: 3 }} active />
      </div>
    );
  }

  if (!user) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Text type="secondary">用户数据加载失败</Text>
      </div>
    );
  }

  const getRegisterSourceLabel = (source: string) => {
    return REGISTER_SOURCES.find((s) => s.value === source)?.label || source;
  };

  const getStatusLabel = (status: number) => {
    return USER_STATUS.find((s) => s.value === status)?.label || '未知';
  };

  const getStatusColor = (status: number) => {
    const colorMap: Record<number, string> = {
      1: 'success',
      0: 'error',
      2: 'default',
    };
    return colorMap[status] || 'default';
  };

  const getGenderLabel = (gender: number) => {
    const genderMap: Record<number, { label: string; color: string }> = {
      1: { label: '男', color: 'blue' },
      2: { label: '女', color: 'pink' },
      0: { label: '未知', color: 'default' },
    };
    return genderMap[gender] || { label: '未知', color: 'default' };
  };

  return (
    <div style={{ padding: '8px 0' }}>
      {/* 用户基本信息卡片 */}
      <Card
        variant="borderless"
        style={{
          background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          borderRadius: 12,
          marginBottom: 24,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
          <Avatar
            size={80}
            src={user.avatar}
            icon={<UserOutlined />}
            style={{
              border: '4px solid rgba(255, 255, 255, 0.3)',
              background: '#fff',
            }}
          />
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
              <Title level={4} style={{ margin: 0, color: '#fff' }}>
                {user.nickname || '未设置昵称'}
              </Title>
              <Tag color={getStatusColor(user.status)} style={{ margin: 0 }}>
                {getStatusLabel(user.status)}
              </Tag>
            </div>
            <Space size={24} style={{ color: 'rgba(255, 255, 255, 0.85)' }}>
              <span>
                <PhoneOutlined style={{ marginRight: 6 }} />
                {user.mobile || '-'}
              </span>
              <span>
                <MailOutlined style={{ marginRight: 6 }} />
                {user.email || '-'}
              </span>
            </Space>
          </div>
        </div>
      </Card>

      {/* 详细信息 */}
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card
            title={
              <Space>
                <InfoCircleOutlined />
                <span>基础信息</span>
              </Space>
            }
            variant="borderless"
            style={{ background: '#fafafa', borderRadius: 8 }}
          >
            <Descriptions column={2} size="small" colon={false}>
              <Descriptions.Item label="用户ID">
                <Text copyable style={{ fontFamily: 'monospace' }}>
                  {user.id}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="账号状态">
                <Tag color={getStatusColor(user.status)}>{getStatusLabel(user.status)}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="性别">
                {(() => {
                  const gender = getGenderLabel(user.gender);
                  return <Tag color={gender.color}>{gender.label}</Tag>;
                })()}
              </Descriptions.Item>
              <Descriptions.Item label="生日">
                {user.birthday ? (
                  <Space>
                    <CalendarOutlined />
                    {user.birthday}
                  </Space>
                ) : (
                  '-'
                )}
              </Descriptions.Item>
              <Descriptions.Item label="注册来源">
                <Tag color="blue">{getRegisterSourceLabel(user.registerSource)}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="注册时间">
                {user.registerTime
                  ? dayjs(user.registerTime).format('YYYY-MM-DD HH:mm:ss')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="最后登录时间">
                {user.lastLoginTime
                  ? dayjs(user.lastLoginTime).format('YYYY-MM-DD HH:mm:ss')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="最后登录IP">{user.lastLoginIp || '-'}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col span={24}>
          <Card
            title={
              <Space>
                <TagOutlined />
                <span>标签信息</span>
              </Space>
            }
            variant="borderless"
            style={{ background: '#fafafa', borderRadius: 8 }}
          >
            {user.tags && user.tags.length > 0 ? (
              <Space wrap size={8}>
                {user.tags.map((tag) => (
                  <Tag
                    key={tag.id}
                    color={tag.color}
                    style={{ padding: '4px 12px', fontSize: 13 }}
                  >
                    {tag.name}
                  </Tag>
                ))}
              </Space>
            ) : (
              <Text type="secondary">暂无标签</Text>
            )}
          </Card>
        </Col>

        {fieldValues && fieldValues.length > 0 && (
          <Col span={24}>
            <Card
              title={
                <Space>
                  <GlobalOutlined />
                  <span>扩展信息</span>
                </Space>
              }
              variant="borderless"
              style={{ background: '#fafafa', borderRadius: 8 }}
            >
              <Descriptions column={1} size="small" colon={false}>
                {fieldValues.map((fv: UserFieldValue) => (
                  <Descriptions.Item key={fv.fieldId} label={fv.fieldName}>
                    <FieldValueEditor
                      userId={userId}
                      fieldId={fv.fieldId}
                      fieldType={fv.fieldType}
                      fieldValue={fv.fieldValue}
                      config={{}}
                    />
                  </Descriptions.Item>
                ))}
              </Descriptions>
            </Card>
          </Col>
        )}
      </Row>
    </div>
  );
};

export default UserDetail;
