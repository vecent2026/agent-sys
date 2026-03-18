import platformRequest from '../utils/request';

export interface LogDoc {
  id: string;
  traceId: string;
  userId: number;
  tenantId?: number;
  isPlatform?: boolean;
  username: string;
  module: string;
  action: string;
  ip: string;
  status: string;
  errorMsg?: string;
  costTime: number;
  params?: string;
  result?: string;
  createTime: string;
}

export interface LogQueryParams {
  page?: number;
  size?: number;
  username?: string;
  module?: string;
  action?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}

export const getLogPage = (params: LogQueryParams) =>
  platformRequest.get<any>('/api/platform/logs', { params });
