import React, { useState } from 'react';
import { Table, Button, Form, Input, Select, DatePicker, Drawer, Tag, Descriptions } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getLogList } from '@/api/log';
import { PageContainer } from '@/components/PageContainer';
import type { Log } from '@/types/log';
import type { ColumnsType } from 'antd/es/table';
import JsonViewer from '@/components/JsonViewer';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;

const LogList: React.FC = () => {
  const [visible, setVisible] = useState(false);
  const [currentLog, setCurrentLog] = useState<Log | null>(null);
  
  // Pagination & Search state
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
  });
  const [searchParams, setSearchParams] = useState<any>({});

  // Fetch Logs
  const { data: logData, isLoading } = useQuery({
    queryKey: ['logs', pagination, searchParams],
    queryFn: () => getLogList({ page: pagination.current, size: pagination.pageSize, ...searchParams }),
  });

  const handleSearch = (values: any) => {
    const params: any = { ...values };
    if (values.timeRange) {
      params.startTime = values.timeRange[0].format('YYYY-MM-DD HH:mm:ss');
      params.endTime = values.timeRange[1].format('YYYY-MM-DD HH:mm:ss');
      delete params.timeRange;
    }
    setSearchParams(params);
    setPagination({ ...pagination, current: 1 });
  };

  const handleView = (record: Log) => {
    setCurrentLog(record);
    setVisible(true);
  };

  // Disable future dates
  const disabledDate = (current: any) => {
    return current && current > dayjs().endOf('day');
  };

  const columns: ColumnsType<Log> = [
    {
      title: '操作人',
      dataIndex: 'username',
      key: 'username',
      width: 120,
      fixed: 'left',
    },
    {
      title: '操作模块',
      dataIndex: 'module',
      key: 'module',
      width: 150,
    },
    {
      title: '操作事件',
      dataIndex: 'action',
      key: 'action',
      width: 150,
    },
    {
      title: 'IP地址',
      dataIndex: 'ip',
      key: 'ip',
      width: 150,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status) => (
        <Tag color={status === 'SUCCESS' ? 'success' : 'error'}>
          {status === 'SUCCESS' ? '成功' : '失败'}
        </Tag>
      ),
    },
    {
      title: '耗时(ms)',
      dataIndex: 'costTime',
      key: 'costTime',
      width: 100,
    },
    {
      title: '操作时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (text) => {
        return dayjs(text).format('YYYY-MM-DD HH:mm:ss');
      }
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      fixed: 'right',
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => handleView(record)}>
          详情
        </Button>
      ),
    },
  ];

  return (
    <PageContainer title="操作日志">
      <Form layout="inline" style={{ marginBottom: 16, rowGap: 16 }} onFinish={handleSearch}>
        <Form.Item name="username" label="操作人">
          <Input placeholder="请输入操作人" allowClear />
        </Form.Item>
        <Form.Item name="module" label="操作模块">
          <Input placeholder="请输入操作模块" allowClear />
        </Form.Item>
        <Form.Item name="action" label="操作事件">
          <Input placeholder="请输入操作事件" allowClear />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select placeholder="请选择" allowClear style={{ width: 120 }}>
            <Select.Option value="SUCCESS">成功</Select.Option>
            <Select.Option value="FAIL">失败</Select.Option>
          </Select>
        </Form.Item>
        <Form.Item name="timeRange" label="时间范围">
          <RangePicker 
            showTime={{ defaultValue: [dayjs('00:00:00', 'HH:mm:ss'), dayjs('23:59:59', 'HH:mm:ss')] }}
            disabledDate={disabledDate} 
          />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit">查询</Button>
        </Form.Item>
      </Form>

      <Table
        columns={columns}
        dataSource={logData?.records}
        rowKey="id"
        loading={isLoading}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: logData?.total,
          onChange: (page, size) => setPagination({ current: page, pageSize: size }),
        }}
        scroll={{ x: 1200 }}
      />

      <Drawer
        title="日志详情"
        open={visible}
        onClose={() => setVisible(false)}
        size="large"
      >
        {currentLog && (
          <Descriptions column={1} bordered>
            <Descriptions.Item label="TraceId">{currentLog.traceId}</Descriptions.Item>
            <Descriptions.Item label="操作人">{currentLog.username}</Descriptions.Item>
            <Descriptions.Item label="操作模块">{currentLog.module}</Descriptions.Item>
            <Descriptions.Item label="操作事件">{currentLog.action}</Descriptions.Item>
            <Descriptions.Item label="IP地址">{currentLog.ip}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={currentLog.status === 'SUCCESS' ? 'success' : 'error'}>
                {currentLog.status === 'SUCCESS' ? '成功' : '失败'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="耗时">{currentLog.costTime} ms</Descriptions.Item>
            <Descriptions.Item label="操作时间">{dayjs(currentLog.createTime).format('YYYY-MM-DD HH:mm:ss')}</Descriptions.Item>
            <Descriptions.Item label="请求参数">
              <JsonViewer data={currentLog.params} />
            </Descriptions.Item>
            <Descriptions.Item label="返回结果">
              <JsonViewer data={currentLog.result} />
            </Descriptions.Item>
            {currentLog.errorMsg && (
              <Descriptions.Item label="异常信息">
                <Input.TextArea
                  readOnly
                  value={currentLog.errorMsg}
                  autoSize={{ minRows: 2, maxRows: 10 }}
                  style={{ color: 'red' }}
                />
              </Descriptions.Item>
            )}
          </Descriptions>
        )}
      </Drawer>
    </PageContainer>
  );
};

export default LogList;