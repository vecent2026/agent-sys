import React, { useState } from 'react';
import { Form, Input, Card, message, List, Button, Typography } from 'antd';
import { MobileOutlined, LockOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useUserStore } from '@/store/userStore';
import {
  tenantLogin,
  tenantSelectTenant,
  getTenantUserInfo,
  getTenantPermissions,
} from '@/api/auth';
import { PrimaryButton } from '@/design-system/components/Buttons';

const { Title, Text } = Typography;

type Step = 'credentials' | 'select_tenant';

interface TenantItem {
  tenantId: number;
  tenantName: string;
  tenantCode?: string;
}

const Login: React.FC = () => {
  const navigate = useNavigate();
  const { setToken, setUserInfo, setPermissions, setCurrentTenant, setTenantList } = useUserStore();
  const [loading, setLoading] = useState(false);
  const [step, setStep] = useState<Step>('credentials');
  const [preToken, setPreToken] = useState<string>('');
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [selectingId, setSelectingId] = useState<number | null>(null);

  const initAfterLogin = async (tenantId: number, tenantName: string) => {
    const userInfo = await getTenantUserInfo();
    setUserInfo(userInfo as any);
    const permissions = await getTenantPermissions();
    setPermissions(Array.isArray(permissions) ? permissions : []);
    setCurrentTenant(tenantId, tenantName);
    message.success('登录成功');
    navigate('/dashboard');
  };

  const onCredentials = async (values: { mobile: string; password: string }) => {
    setLoading(true);
    try {
      const res = await tenantLogin(values);
      if (res.preToken) {
        // 多租户：进入选择步骤
        setPreToken(res.preToken);
        const tenantList: TenantItem[] = res.tenants || [];
        setTenants(tenantList);
        setTenantList(tenantList);
        setStep('select_tenant');
      } else {
        // 单租户：直接登录
        setToken(res.accessToken, res.refreshToken);
        await initAfterLogin(res.tenantId, res.tenantName);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const onSelectTenant = async (tenant: TenantItem) => {
    setSelectingId(tenant.tenantId);
    try {
      const res = await tenantSelectTenant({ preToken, tenantId: tenant.tenantId });
      setToken(res.accessToken, res.refreshToken);
      await initAfterLogin(res.tenantId, res.tenantName);
    } catch (err) {
      console.error(err);
    } finally {
      setSelectingId(null);
    }
  };

  return (
    <Card
      style={{
        width: 420,
        boxShadow: '0 20px 45px rgba(15, 23, 42, 0.18)',
        borderRadius: 16,
        backdropFilter: 'blur(18px)',
        background: 'rgba(248, 250, 252, 0.92)',
        border: '1px solid rgba(148, 163, 184, 0.35)',
      }}
    >
      {step === 'credentials' ? (
        <>
          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <Title level={3} style={{ margin: 0 }}>系统登录</Title>
          </div>
          <Form name="login" onFinish={onCredentials} size="large">
            <Form.Item
              name="mobile"
              rules={[{ required: true, message: '请输入手机号!' }]}
            >
              <Input prefix={<MobileOutlined />} placeholder="手机号" />
            </Form.Item>
            <Form.Item
              name="password"
              rules={[{ required: true, message: '请输入密码!' }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="密码" />
            </Form.Item>
            <Form.Item>
              <PrimaryButton htmlType="submit" block loading={loading}>
                登录
              </PrimaryButton>
            </Form.Item>
          </Form>
        </>
      ) : (
        <>
          <div style={{ display: 'flex', alignItems: 'center', marginBottom: 16, gap: 8 }}>
            <Button
              icon={<ArrowLeftOutlined />}
              type="text"
              onClick={() => setStep('credentials')}
            />
            <Title level={4} style={{ margin: 0 }}>选择租户</Title>
          </div>
          <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
            您的账号属于多个租户，请选择本次登录的租户：
          </Text>
          <List
            dataSource={tenants}
            renderItem={(tenant) => (
              <List.Item>
                <Button
                  block
                  loading={selectingId === tenant.tenantId}
                  onClick={() => onSelectTenant(tenant)}
                  style={{ textAlign: 'left', height: 'auto', padding: '8px 12px' }}
                >
                  <div>
                    <div style={{ fontWeight: 600 }}>{tenant.tenantName}</div>
                    {tenant.tenantCode && (
                      <div style={{ fontSize: 12, color: '#999' }}>{tenant.tenantCode}</div>
                    )}
                  </div>
                </Button>
              </List.Item>
            )}
          />
        </>
      )}
    </Card>
  );
};

export default Login;
