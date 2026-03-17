import { platformRequest } from '../utils/request';
import type { PageResult } from '@/types/api';
import type { Log, LogQuery } from '@/types/log';

export type PlatformLog = Log;
export type PlatformLogQuery = LogQuery;

export const getPlatformLogPage = async (params: PlatformLogQuery) => {
  const res = await platformRequest.get<any>('/api/platform/logs', { params });
  if (res?.content != null) {
    return {
      records: res.content,
      total: res.totalElements ?? 0,
      size: res.size ?? params.size,
      current: (res.number ?? 0) + 1,
      pages: res.totalPages ?? 0,
    } as PageResult<PlatformLog>;
  }
  if (res?.records != null) {
    return {
      records: res.records,
      total: res.total ?? 0,
      size: res.size ?? params.size,
      current: res.current ?? params.page,
      pages: res.pages ?? 0,
    } as PageResult<PlatformLog>;
  }
  return res as PageResult<PlatformLog>;
};

