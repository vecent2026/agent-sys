import React, { useState, useCallback, useMemo, useEffect } from 'react';
import { Table, Button, Form, Input, Select, DatePicker, Drawer, Descriptions, Pagination, Popover } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getLogList } from '@/api/log';
import { PageContainer } from '@/components/PageContainer';
import type { Log } from '@/types/log';
import type { ColumnsType } from 'antd/es/table';
import JsonViewer from '@/components/JsonViewer';
import dayjs from 'dayjs';
import { TablePageLayout } from '@/design-system/components/TablePageLayout';
import { LogStatusTag } from '@/design-system/components/LogStatusTag';
import { designTokens } from '@/design-system/theme';
import { FilterOutlined } from '@ant-design/icons';
import { debounce } from 'lodash-es';

const { RangePicker } = DatePicker;

const LogList: React.FC = () => {
  const [visible, setVisible] = useState(false);
  const [currentLog, setCurrentLog] = useState<Log | null>(null);
  
  // Pagination & Search state
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
  });
  const [searchParams, setSearchParams] = useState<Record<string, unknown>>({});
  const [moreFilters, setMoreFilters] = useState<{ module?: string; action?: string }>({});
  const [moreFilterInput, setMoreFilterInput] = useState<{ module?: string; action?: string }>({});

  // Fetch Logs
  const mergedParams = useMemo(
    () => ({ ...searchParams, ...moreFilters }),
    [searchParams, moreFilters]
  );

  const { data: logData, isLoading } = useQuery({
    queryKey: ['logs', pagination, mergedParams],
    queryFn: () => getLogList({ page: pagination.current, size: pagination.pageSize, ...mergedParams }),
  });

  const normalizeParams = useCallback((values: Record<string, unknown>) => {
    const params: Record<string, unknown> = { ...values };
    if (values.timeRange && Array.isArray(values.timeRange) && values.timeRange[0] && values.timeRange[1]) {
      params.startTime = (values.timeRange[0] as dayjs.Dayjs).format('YYYY-MM-DD HH:mm:ss');
      params.endTime = (values.timeRange[1] as dayjs.Dayjs).format('YYYY-MM-DD HH:mm:ss');
      delete params.timeRange;
    }
    return params;
  }, []);

  const applySearchParams = useCallback((values: Record<string, unknown>) => {
    setSearchParams(normalizeParams(values));
    setPagination((prev) => ({ ...prev, current: 1 }));
  }, [normalizeParams]);

  const debouncedApply = useMemo(() => debounce(applySearchParams, 300), [applySearchParams]);

  useEffect(() => () => debouncedApply.cancel(), [debouncedApply]);

  const handleFilterChange = useCallback(
    (changedValues: Record<string, unknown>, allValues: Record<string, unknown>) => {
      const fullParams = { ...searchParams, ...moreFilters, ...allValues };
      if ('username' in changedValues) {
        debouncedApply(fullParams);
      } else {
        debouncedApply.cancel();
        applySearchParams(fullParams);
      }
    },
    [searchParams, moreFilters, applySearchParams, debouncedApply]
  );

  const debouncedSetMoreFilters = useMemo(
    () =>
      debounce((updates: { module?: string; action?: string }) => {
        setMoreFilters((prev) => ({ ...prev, ...updates }));
        setPagination((prev) => ({ ...prev, current: 1 }));
      }, 300),
    []
  );

  useEffect(() => () => debouncedSetMoreFilters.cancel(), [debouncedSetMoreFilters]);

  const handleMoreFilterChange = useCallback(
    (field: 'module' | 'action', value: string | undefined) => {
      setMoreFilterInput((prev) => ({ ...prev, [field]: value }));
      debouncedSetMoreFilters({ [field]: value });
    },
    [debouncedSetMoreFilters]
  );


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
      render: (status) => <LogStatusTag status={status} />,
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
    <PageContainer>
      <TablePageLayout
        filterBar={
          <Form
            layout="inline"
            style={{ marginBottom: 8, rowGap: 16 }}
            onValuesChange={handleFilterChange}
            initialValues={{
              username: searchParams.username,
              status: searchParams.status,
              timeRange: searchParams.timeRange,
            }}
          >
            <Form.Item name="username" label="操作人">
              <Input placeholder="请输入操作人" allowClear />
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
            <Popover
              content={
                <div style={{ width: 280 }}>
                  <div style={{ marginBottom: 16 }}>
                    <div style={{ marginBottom: 8, fontSize: 12, color: designTokens.colorTextTertiary }}>
                      操作模块
                    </div>
                    <Input
                      placeholder="请输入操作模块"
                      allowClear
                      value={moreFilterInput.module ?? moreFilters.module ?? ''}
                      onChange={(e) => handleMoreFilterChange('module', e.target.value || undefined)}
                    />
                  </div>
                  <div>
                    <div style={{ marginBottom: 8, fontSize: 12, color: designTokens.colorTextTertiary }}>
                      操作事件
                    </div>
                    <Input
                      placeholder="请输入操作事件"
                      allowClear
                      value={moreFilterInput.action ?? moreFilters.action ?? ''}
                      onChange={(e) => handleMoreFilterChange('action', e.target.value || undefined)}
                    />
                  </div>
                </div>
              }
              trigger="click"
              placement="bottomLeft"
            >
              <Button icon={<FilterOutlined />}>更多筛选</Button>
            </Popover>
          </Form>
        }
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Pagination
              size="small"
              current={pagination.current}
              pageSize={pagination.pageSize}
              total={logData?.total}
              showSizeChanger
              showQuickJumper
              pageSizeOptions={[20, 50, 100]}
              showTotal={(total) => `共 ${total} 条`}
              onChange={(page, pageSize) =>
                setPagination((prev) => ({
                  current: pageSize && pageSize !== prev.pageSize ? 1 : page,
                  pageSize: pageSize || prev.pageSize,
                }))
              }
            />
          </div>
        }
      >
        <Table
          columns={columns}
          dataSource={logData?.records}
          rowKey="id"
          loading={isLoading}
          size="small"
          onRow={() => ({ style: { height: 40 } })}
          scroll={{ x: 'max-content', y: 'calc(100vh - 360px)' }}
          sticky
          pagination={false}
        />
      </TablePageLayout>

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
              <LogStatusTag status={currentLog.status} />
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
                  style={{ color: designTokens.colorError }}
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