export interface Result<T = any> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
}

export interface PageResult<T = any> {
  records: T[];  // 修改为 records，与后端保持一致
  total: number;
  size: number;
  current: number;
  pages: number;
}
