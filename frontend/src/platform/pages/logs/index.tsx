import React, { useMemo, useState } from 'react';
import { Card, Table, Form, Input, Select } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DatePicker } from 'antd';
import dayjs from 'dayjs';
import { useQuery } from '@tanstack/react-query';
import { getPlatformLogPage } from '../../api/log';
import type { Log, LogQuery } from '@/types/log';

const PlatformLogsPage: React.FC = () => {
  const [form] = Form.useForm();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [username, setUsername] = useState<string | undefined>();
  const [module, setModule] = useState<string | undefined>();
  const [status, setStatus] = useState<string | undefined>();
  const [dateRange, setDateRange] = useState<[string, string] | undefined>();

  const queryParams: LogQuery = useMemo(
    () => ({
      page,
      size,
      username,
      module,
      status,
      startTime: dateRange?.[0],
      endTime: dateRange?.[1],
    }),
    [page, size, username, module, status, dateRange],
  );

  const { data, isLoading } = useQuery({
    queryKey: ['platform-logs', queryParams],
    queryFn: () => getPlatformLogPage(queryParams),
  });

  const handleFilterChange = (_changed: Record<string, unknown>, all: Record<string, unknown>) => {
    setUsername((all.username as string) || undefined);
    setModule((all.module as string) || undefined);
    setStatus((all.status as string) || undefined);
    const range = all.dateRange as [dayjs.Dayjs, dayjs.Dayjs] | undefined;
    if (range?.[0] && range?.[1]) {
      setDateRange([
        range[0].format('YYYY-MM-DD HH:mm:ss'),
        range[1].format('YYYY-MM-DD HH:mm:ss'),
      ]);
    } else {
      setDateRange(undefined);
    }
    setPage(1);
  };

  const columns: ColumnsType<Log> = [
    {
      title: '操作人',
      dataIndex: 'username',
      key: 'username',
      width: 120,
      ellipsis: true,
    },
    {
      title: '模块',
      dataIndex: 'module',
      key: 'module',
      width: 140,
      ellipsis: true,
      render: (v) => v || '-',
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      width: 120,
      ellipsis: true,
      render: (v) => v || '-',
    },
    {
      title: 'IP',
      dataIndex: 'ip',
      key: 'ip',
      width: 140,
      ellipsis: true,
      render: (v) => v || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (v: string) => (v === 'SUCCESS' ? '成功' : '失败'),
    },
    {
      title: '耗时(ms)',
      dataIndex: 'costTime',
      key: 'costTime',
      width: 100,
      render: (v: number) => v ?? '-',
    },
    {
      title: '操作时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
  ];

  return (
    <Card title="平台操作日志" bodyStyle={{ padding: 0 }}>
      <div style={{ padding: 16, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
        <Form form={form} layout="inline" onValuesChange={handleFilterChange} style={{ flex: 'none' }}>
          <Form.Item name="username">
            <Input placeholder="操作人" allowClear style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="module">
            <Input placeholder="模块" allowClear style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="status">
            <Select placeholder="状态" allowClear style={{ width: 100 }}>
              <Select.Option value="SUCCESS">成功</Select.Option>
              <Select.Option value="FAIL">失败</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="dateRange" label="时间范围">
            <DatePicker.RangePicker
              showTime
              style={{ width: 360 }}
              placeholder={['开始时间', '结束时间']}
            />
          </Form.Item>
        </Form>
      </div>

      <Table<Log>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={(data as any)?.records || []}
        scroll={{ x: 900, y: 'calc(100vh - 320px)' }}
        pagination={{
          size: 'small',
          current: page,
          pageSize: size,
          total: (data as any)?.total ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100],
          showTotal: (total) => `共 ${total} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s || size);
          },
        }}
      />
    </Card>
  );
};

export default PlatformLogsPage;
