import React, { useState, useEffect, useRef } from 'react';
import {
  Drawer,
  Upload,
  Button,
  Progress,
  Steps,
  Result,
  Space,
  Table,
  Alert,
  message,
  Card,
  Statistic,
  Row,
  Col,
  Typography,
  Divider,
} from 'antd';
import {
  DownloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  FileExcelOutlined,
  InboxOutlined,
  CheckOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import {
  downloadImportTemplate,
  validateImportData,
  downloadValidateResult,
  executeImport,
  getImportProgress,
  type ImportValidateResult,
  type ImportProgress,
} from '@/api/app-user';
import dayjs from 'dayjs';

const { Title, Text } = Typography;

interface ImportModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
}

type ImportStep = 'upload' | 'validate' | 'importing' | 'result';

const ImportModal: React.FC<ImportModalProps> = ({ visible, onCancel, onSuccess }) => {
  const [currentStep, setCurrentStep] = useState<ImportStep>('upload');
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [validateResult, setValidateResult] = useState<ImportValidateResult | null>(null);
  const [importProgress, setImportProgress] = useState<ImportProgress | null>(null);
  const [loading, setLoading] = useState(false);
  const [contentVisible, setContentVisible] = useState(true);
  const progressTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    return () => {
      if (progressTimerRef.current) {
        clearInterval(progressTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (!visible) {
      resetState();
    }
  }, [visible]);

  const resetState = () => {
    setCurrentStep('upload');
    setFileList([]);
    setValidateResult(null);
    setImportProgress(null);
    setLoading(false);
    setContentVisible(true);
    if (progressTimerRef.current) {
      clearInterval(progressTimerRef.current);
      progressTimerRef.current = null;
    }
  };

  const handleStepChange = (newStep: ImportStep) => {
    setContentVisible(false);
    setTimeout(() => {
      setCurrentStep(newStep);
      setContentVisible(true);
    }, 150);
  };

  const handleDownloadTemplate = async () => {
    try {
      const blob = await downloadImportTemplate();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `用户导入模板_${dayjs().format('YYYYMMDD')}.xlsx`;
      a.click();
      window.URL.revokeObjectURL(url);
    } catch {
      message.error('下载模板失败');
    }
  };

  const handleValidate = async () => {
    if (fileList.length === 0) {
      message.warning('请先选择文件');
      return;
    }

    const file = fileList[0].originFileObj;
    if (!file) {
      message.error('文件读取失败');
      return;
    }

    setLoading(true);
    try {
      const result = await validateImportData(file);
      setValidateResult(result);
      handleStepChange('validate');
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err?.response?.data?.message || '校验失败');
    } finally {
      setLoading(false);
    }
  };

  const handleDownloadValidateResult = async () => {
    if (!validateResult) return;
    try {
      const blob = await downloadValidateResult(validateResult.taskId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `校验结果_${dayjs().format('YYYYMMDDHHmmss')}.xlsx`;
      a.click();
      window.URL.revokeObjectURL(url);
    } catch {
      message.error('下载校验结果失败');
    }
  };

  const handleExecuteImport = async () => {
    if (!validateResult) return;

    setLoading(true);
    try {
      const taskId = await executeImport(validateResult.taskId);
      handleStepChange('importing');
      startProgressPolling(taskId);
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err?.response?.data?.message || '执行导入失败');
      setLoading(false);
    }
  };

  const startProgressPolling = (taskId: string) => {
    progressTimerRef.current = setInterval(async () => {
      try {
        const progress = await getImportProgress(taskId);
        setImportProgress(progress);

        if (progress.status === 'COMPLETED' || progress.status === 'FAILED') {
          if (progressTimerRef.current) {
            clearInterval(progressTimerRef.current);
            progressTimerRef.current = null;
          }
          handleStepChange('result');
          setLoading(false);
          if (progress.status === 'COMPLETED' && progress.success > 0) {
            onSuccess();
          }
        }
      } catch {
        if (progressTimerRef.current) {
          clearInterval(progressTimerRef.current);
          progressTimerRef.current = null;
        }
        setLoading(false);
        message.error('获取导入进度失败');
      }
    }, 1000);
  };

  const handleClose = () => {
    if (progressTimerRef.current) {
      clearInterval(progressTimerRef.current);
      progressTimerRef.current = null;
    }
    onCancel();
  };

  const handleBackToUpload = () => {
    setFileList([]);
    handleStepChange('upload');
  };

  const renderUploadStep = () => (
    <div style={{ padding: '24px 0' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          marginBottom: 24,
          padding: '12px 16px',
          background: '#fafafa',
          borderRadius: 8,
        }}
      >
        <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>
          下载模板
        </Button>
        <Text type="secondary" style={{ fontSize: 14 }}>
          下载模板并按照模板格式填写用户数据，单次导入最多20,000条数据
        </Text>
      </div>

      <Upload.Dragger
        accept=".xlsx"
        fileList={fileList}
        beforeUpload={() => false}
        onChange={({ fileList }) => setFileList(fileList.slice(-1))}
        maxCount={1}
        style={{
          borderRadius: 8,
          border: '2px dashed #d9d9d9',
          background: '#fafafa',
        }}
      >
        <div style={{ padding: '32px 0' }}>
          <p className="ant-upload-drag-icon">
            <InboxOutlined style={{ fontSize: 48, color: '#1890ff' }} />
          </p>
          <p className="ant-upload-text" style={{ fontSize: 16, marginBottom: 8 }}>
            点击或拖拽文件到此区域上传
          </p>
          <p className="ant-upload-hint" style={{ color: '#999' }}>
            支持 .xlsx 格式
          </p>
        </div>
      </Upload.Dragger>

      {fileList.length > 0 && (
        <div
          style={{
            marginTop: 16,
            padding: '12px 16px',
            background: '#f6ffed',
            border: '1px solid #b7eb8f',
            borderRadius: 8,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Space>
            <FileExcelOutlined style={{ color: '#52c41a', fontSize: 20 }} />
            <span>{fileList[0].name}</span>
          </Space>
          <Text type="success">已就绪</Text>
        </div>
      )}

      <div style={{ marginTop: 32, display: 'flex', justifyContent: 'center', gap: 16 }}>
        <Button onClick={handleClose}>取消</Button>
        <Button
          type="primary"
          loading={loading}
          onClick={handleValidate}
          disabled={fileList.length === 0}
        >
          开始校验
        </Button>
      </div>
    </div>
  );

  const renderValidateStep = () => {
    if (!validateResult) return null;

    const hasErrors = validateResult.invalidCount > 0;

    return (
      <div style={{ padding: '24px 0' }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div
            style={{
              width: 64,
              height: 64,
              borderRadius: '50%',
              background: hasErrors ? '#fff7e6' : '#f6ffed',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 16px',
            }}
          >
            {hasErrors ? (
              <WarningOutlined style={{ fontSize: 32, color: '#faad14' }} />
            ) : (
              <CheckOutlined style={{ fontSize: 32, color: '#52c41a' }} />
            )}
          </div>
          <Title level={4} style={{ margin: '0 0 8px' }}>
            {hasErrors ? '校验完成，发现部分错误' : '校验通过'}
          </Title>
          <Text type="secondary">
            {hasErrors
              ? '请下载错误报告查看详情，或继续导入正确数据'
              : '数据格式正确，可以开始导入'}
          </Text>
        </div>

        <Row gutter={16} style={{ marginBottom: 32 }}>
          <Col span={8}>
            <Card variant="borderless" style={{ textAlign: 'center', background: '#f5f5f5' }}>
              <Statistic
                title="总数据"
                value={validateResult.total}
                suffix="条"
                valueStyle={{ fontSize: 28 }}
              />
            </Card>
          </Col>
          <Col span={8}>
            <Card
              variant="borderless"
              style={{ textAlign: 'center', background: '#f6ffed', border: '1px solid #b7eb8f' }}
            >
              <Statistic
                title="正确数据"
                value={validateResult.validCount}
                suffix="条"
                valueStyle={{ color: '#52c41a', fontSize: 28 }}
              />
            </Card>
          </Col>
          <Col span={8}>
            <Card
              variant="borderless"
              style={{
                textAlign: 'center',
                background: hasErrors ? '#fff2f0' : '#f6ffed',
                border: hasErrors ? '1px solid #ffccc7' : '1px solid #b7eb8f',
              }}
            >
              <Statistic
                title="错误数据"
                value={validateResult.invalidCount}
                suffix="条"
                valueStyle={{ color: hasErrors ? '#ff4d4f' : '#52c41a', fontSize: 28 }}
              />
            </Card>
          </Col>
        </Row>

        {hasErrors && (
          <Alert
            message="检测到数据错误"
            description={`共有 ${validateResult.invalidCount} 条数据存在格式错误或重复，建议下载错误报告查看详情并修正后重新上传。`}
            type="warning"
            showIcon
            style={{ marginBottom: 24 }}
          />
        )}

        <Divider />

        <div style={{ display: 'flex', justifyContent: 'center', gap: 16 }}>
          {hasErrors && (
            <Button icon={<DownloadOutlined />} onClick={handleDownloadValidateResult} size="large">
              下载错误报告
            </Button>
          )}
          <Button size="large" onClick={handleBackToUpload}>
            重新选择
          </Button>
          {validateResult.canProceed && (
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={handleExecuteImport}
              size="large"
              loading={loading}
            >
              {hasErrors ? '继续导入（仅正确数据）' : '开始导入'}
            </Button>
          )}
        </div>
      </div>
    );
  };

  const renderImportingStep = () => (
    <div style={{ padding: '40px 20px' }}>
      <div style={{ textAlign: 'center', marginBottom: 40 }}>
        <Progress
          type="circle"
          percent={importProgress?.progress || 0}
          status={importProgress?.status === 'FAILED' ? 'exception' : 'active'}
          strokeWidth={8}
          size={160}
          strokeColor={{
            '0%': '#108ee9',
            '100%': '#87d068',
          }}
        />
      </div>

      <div style={{ marginBottom: 32 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Title level={5} style={{ margin: 0 }}>正在导入数据...</Title>
          <Text type="secondary">
            已处理 {importProgress?.processed || 0} / {importProgress?.total || 0} 条
          </Text>
        </div>

        <Progress
          percent={importProgress?.progress || 0}
          status={importProgress?.status === 'FAILED' ? 'exception' : 'active'}
          strokeColor={{ from: '#108ee9', to: '#87d068' }}
          showInfo={false}
        />
      </div>

      <Row gutter={16}>
        <Col span={12}>
          <Card
            variant="borderless"
            style={{
              textAlign: 'center',
              background: '#f6ffed',
              border: '1px solid #b7eb8f',
            }}
          >
            <Statistic
              title="导入成功"
              value={importProgress?.success || 0}
              suffix="条"
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card
            variant="borderless"
            style={{
              textAlign: 'center',
              background: (importProgress?.failed || 0) > 0 ? '#fff2f0' : '#f6ffed',
              border: (importProgress?.failed || 0) > 0 ? '1px solid #ffccc7' : '1px solid #b7eb8f',
            }}
          >
            <Statistic
              title="导入失败"
              value={importProgress?.failed || 0}
              suffix="条"
              valueStyle={{ color: (importProgress?.failed || 0) > 0 ? '#ff4d4f' : '#52c41a' }}
            />
          </Card>
        </Col>
      </Row>

      <div style={{ marginTop: 32, textAlign: 'center' }}>
        <Button onClick={handleClose}>后台执行</Button>
      </div>
    </div>
  );

  const renderResultStep = () => {
    const isSuccess = importProgress?.status === 'COMPLETED';
    const hasErrors = importProgress && importProgress.failed > 0;

    const errorColumns = [
      { title: '行号', dataIndex: 'row', key: 'row', width: 80, align: 'center' as const },
      { title: '手机号', dataIndex: 'mobile', key: 'mobile', width: 150 },
      {
        title: '失败原因',
        dataIndex: 'reason',
        key: 'reason',
        render: (reason: string) => (
          <Text type="danger" style={{ fontSize: 13 }}>
            {reason}
          </Text>
        ),
      },
    ];

    return (
      <div style={{ padding: '24px 0' }}>
        <Result
          icon={
            isSuccess ? (
              <CheckCircleOutlined style={{ color: '#52c41a' }} />
            ) : (
              <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
            )
          }
          title={isSuccess ? '导入完成' : '导入失败'}
          subTitle={
            <div style={{ marginTop: 16 }}>
              <Row gutter={32} justify="center">
                <Col>
                  <Statistic
                    title="成功导入"
                    value={importProgress?.success || 0}
                    suffix="条"
                    valueStyle={{ color: '#52c41a' }}
                  />
                </Col>
                {hasErrors && (
                  <Col>
                    <Statistic
                      title="导入失败"
                      value={importProgress?.failed}
                      suffix="条"
                      valueStyle={{ color: '#ff4d4f' }}
                    />
                  </Col>
                )}
              </Row>
            </div>
          }
        />

        {hasErrors && importProgress?.errors && importProgress.errors.length > 0 && (
          <Card
            title="失败详情"
            variant="borderless"
            style={{
              marginTop: 24,
              background: '#fafafa',
            }}
          >
            <Table
              columns={errorColumns}
              dataSource={importProgress.errors}
              rowKey="row"
              pagination={false}
              size="small"
              scroll={{ y: 240 }}
              style={{ background: '#fff' }}
            />
          </Card>
        )}

        <div style={{ marginTop: 32, textAlign: 'center' }}>
          <Button type="primary" onClick={handleClose}>
            关闭
          </Button>
        </div>
      </div>
    );
  };

  const renderContent = () => {
    const contentStyle: React.CSSProperties = {
      opacity: contentVisible ? 1 : 0,
      transform: contentVisible ? 'translateY(0)' : 'translateY(10px)',
      transition: 'all 0.2s ease-in-out',
    };

    return (
      <div style={contentStyle}>
        {(() => {
          switch (currentStep) {
            case 'upload':
              return renderUploadStep();
            case 'validate':
              return renderValidateStep();
            case 'importing':
              return renderImportingStep();
            case 'result':
              return renderResultStep();
            default:
              return renderUploadStep();
          }
        })()}
      </div>
    );
  };

  const stepItems = [
    { title: '上传文件' },
    { title: '校验数据' },
    { title: '导入中' },
    { title: '完成' },
  ];

  const getCurrentStepIndex = () => {
    const stepMap: Record<ImportStep, number> = {
      upload: 0,
      validate: 1,
      importing: 2,
      result: 3,
    };
    return stepMap[currentStep];
  };

  return (
    <Drawer
      title="导入用户"
      open={visible}
      onClose={handleClose}
      width={720}
      destroyOnClose
      maskClosable={false}
    >
      <Steps
        current={getCurrentStepIndex()}
        items={stepItems}
        size="small"
        style={{ marginBottom: 24, marginTop: 8 }}
      />
      {renderContent()}
    </Drawer>
  );
};

export default ImportModal;
