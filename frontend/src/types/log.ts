export interface Log {
  id: string;          // ES UUID
  traceId: string;     // 链路追踪ID
  userId: number;
  username: string;
  module: string;
  action: string;
  ip: string;
  status: string;      // "SUCCESS" / "FAIL"
  costTime: number;
  createTime: string;
  params?: string;     // 详情字段
  result?: string;     // 详情字段
  errorMsg?: string;   // 异常堆栈信息
}

export interface LogQuery {
  page: number;
  size: number;
  username?: string;
  module?: string;
  action?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}
