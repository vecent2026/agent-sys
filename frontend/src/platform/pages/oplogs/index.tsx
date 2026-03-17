import React, { useState, useEffect, useCallback } from 'react';
import {
  Table, Button, Space, Tag, Card, Form, Input, Select, DatePicker,
} from 'antd';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { getLogPage, type LogDoc, type LogQueryParams } from '../../api/logApi';

const { RangePicker } = DatePicker;

const LogPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<LogDoc[]>([]);
  const [total, setTotal] = useState(0);
  const [pagination, setPagination] = useState({ page: 1, size: 20 });
  const [query, setQuery] = useState<LogQueryParams>({});

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getLogPage({ ...pagination, ...query });
      // 兼容 Spring Data Page 格式
      const content = res.content || res.records || [];
      const tot = res.totalElements || res.total || 0;
      setData(content);
      setTotal(tot);
    } finally {
      setLoading(false);
    }
  }, [pagination, query]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const [queryForm] = Form.useForm();
  const handleSearch = () => {
    const values = queryForm.getFieldsValue();
    const { timeRange, ...rest } = values;
    const q: LogQueryParams = { ...rest };
    if (timeRange && timeRange[0]) q.startTime = timeRange[0].toISOString();
    if (timeRange && timeRange[1]) q.endTime = timeRange[1].toISOString();
    setQuery(q);
    setPagination(p => ({ ...p, page: 1 }));
  };
  const handleReset = () => {
    queryForm.resetFields();
    setQuery({});
    setPagination(p => ({ ...p, page: 1 }));
  };

  const columns: ColumnsType<LogDoc> = [
    { title: '用户', dataIndex: 'username', width: 120 },
    { title: '模块', dataIndex: 'module', width: 120 },
    { title: '操作', dataIndex: 'action', width: 150 },
    { title: 'IP', dataIndex: 'ip', width: 130 },
    {
      title: '状态', dataIndex: 'status', width: 80,
      render: (val) => <Tag color={val === 'SUCCESS' ? 'green' : 'red'}>{val}</Tag>,
    },
    {
      title: '耗时(ms)', dataIndex: 'duration', width: 90,
      render: (val) => val ?? '-',
    },
    {
      title: '时间', dataIndex: 'createTime', width: 170,
      render: (val) => val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '错误信息', dataIndex: 'errorMsg', ellipsis: true,
      render: (val) => val ? <span style={{ color: '#ff4d4f' }}>{val}</span> : '-',
    },
  ];

  return (
    <div>
      <Card size="small" style={{ marginBottom: 12 }}>
        <Form form={queryForm} layout="inline">
          <Form.Item name="username" label="用户名">
            <Input placeholder="请输入" allowClear style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="module" label="模块">
            <Input placeholder="请输入" allowClear style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="action" label="操作">
            <Input placeholder="请输入" allowClear style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="全部" allowClear style={{ width: 100 }}>
              <Select.Option value="SUCCESS">成功</Select.Option>
              <Select.Option value="FAIL">失败</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="timeRange" label="时间范围">
            <RangePicker showTime style={{ width: 360 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" onClick={handleSearch}>查询</Button>
              <Button onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card title="操作日志">
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={data}
          scroll={{ x: 1000 }}
          pagination={{
            current: pagination.page, pageSize: pagination.size, total,
            showSizeChanger: true, showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => setPagination({ page, size }),
          }}
        />
      </Card>
    </div>
  );
};

export default LogPage;
