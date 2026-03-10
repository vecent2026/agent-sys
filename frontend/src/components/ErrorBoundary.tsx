import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button, Card, Typography } from 'antd';
import { ReloadOutlined, BugOutlined } from '@ant-design/icons';

const { Title, Paragraph, Text } = Typography;

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
    errorInfo: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error, errorInfo: null };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('ErrorBoundary caught an error:', error, errorInfo);
    this.setState({ errorInfo });
  }

  private handleReload = () => {
    window.location.reload();
  };

  private handleGoBack = () => {
    window.history.back();
  };

  public render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            minHeight: '100vh',
            background: '#f0f2f5',
            padding: 24,
          }}
        >
          <Card
            style={{
              maxWidth: 600,
              width: '100%',
              borderRadius: 8,
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.1)',
            }}
          >
            <div style={{ textAlign: 'center', marginBottom: 24 }}>
              <BugOutlined style={{ fontSize: 64, color: '#ff4d4f' }} />
            </div>
            <Title level={3} style={{ textAlign: 'center', marginBottom: 16 }}>
              页面出错了
            </Title>
            <Paragraph style={{ textAlign: 'center', color: '#666', marginBottom: 24 }}>
              抱歉，页面遇到了一些问题。请尝试刷新页面或返回上一页。
            </Paragraph>
            {import.meta.env.DEV && this.state.error && (
              <Card
                size="small"
                style={{
                  background: '#fff2f0',
                  borderColor: '#ffccc7',
                  marginBottom: 24,
                }}
              >
                <Text type="danger" style={{ fontFamily: 'monospace', fontSize: 12 }}>
                  {this.state.error.toString()}
                </Text>
                {this.state.errorInfo && (
                  <pre
                    style={{
                      marginTop: 8,
                      fontSize: 11,
                      overflow: 'auto',
                      maxHeight: 200,
                      color: '#666',
                    }}
                  >
                    {this.state.errorInfo.componentStack}
                  </pre>
                )}
              </Card>
            )}
            <div style={{ textAlign: 'center' }}>
              <Button icon={<ReloadOutlined />} onClick={this.handleReload} style={{ marginRight: 12 }}>
                刷新页面
              </Button>
              <Button onClick={this.handleGoBack}>返回上一页</Button>
            </div>
          </Card>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
