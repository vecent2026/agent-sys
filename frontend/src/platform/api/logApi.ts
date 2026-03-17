import platformRequest from '../utils/request';

export interface LogDoc {
  id: string;
  traceId: string;
  userId: number;
  username: string;
  module: string;
  action: string;
  ip: string;
  status: string;
  errorMsg?: string;
  duration: number;
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
  platformRequest.get<any>('/api/logs', { params });
