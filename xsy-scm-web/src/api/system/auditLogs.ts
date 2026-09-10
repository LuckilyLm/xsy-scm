import type { PageData } from '../../types/product';
import { apiClient } from '../http';
import { toQueryParams } from '../../utils/query';

export interface LoginLog {
  id: number;
  userId: number | null;
  usernameSnapshot: string | null;
  result: 'SUCCESS' | 'FAILURE' | 'LOCKED' | 'LOGOUT';
  failureReasonCode: string | null;
  ip: string | null;
  userAgent: string | null;
  occurredAt: string;
}

export interface LoginLogQuery {
  page?: number;
  pageSize?: number;
  startTime?: string;
  endTime?: string;
  userId?: number;
  username?: string;
  result?: LoginLog['result'];
}

export interface OperationLog {
  id: number;
  actorUserId: number | null;
  actorNameSnapshot: string | null;
  module: string;
  operationCode: string;
  targetType: string | null;
  targetId: string | null;
  success: boolean;
  errorCode: number | null;
  occurredAt: string;
}

export interface OperationLogQuery {
  page?: number;
  pageSize?: number;
  startTime?: string;
  endTime?: string;
  actorUserId?: number;
  module?: string;
  operationCode?: string;
  targetType?: string;
  targetId?: string;
  success?: boolean;
}

export function fetchLoginLogs(query: LoginLogQuery = {}) {
  return apiClient
    .get<PageData<LoginLog>>('/system/login-logs', { params: toQueryParams(query) })
    .then((response) => response.data);
}

export function fetchOperationLogs(query: OperationLogQuery = {}) {
  return apiClient
    .get<PageData<OperationLog>>('/system/operation-logs', { params: toQueryParams(query) })
    .then((response) => response.data);
}
