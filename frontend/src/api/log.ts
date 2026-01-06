import request from '@/utils/request';
import type { Log, LogQuery } from '@/types/log';
import type { PageResult } from '@/types/api';

export const getLogList = async (params: LogQuery) => {
  const res = await request.get<any>('/api/logs', { params });
  return {
    records: res.content,
    total: res.totalElements,
    size: res.size,
    current: res.number + 1,
    pages: res.totalPages,
  } as PageResult<Log>;
};
