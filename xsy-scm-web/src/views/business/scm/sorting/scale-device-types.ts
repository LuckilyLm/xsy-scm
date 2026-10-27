/** 电子秤设备台账与员工授权会话；凭证/令牌明文只在签发响应里出现一次。 */
import type {Id, SortingPage} from './sorting-types';

export type ScaleDeviceStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'SUSPENDED' | 'REVOKED';

/** 设备状态展示；与后端 ScaleDeviceStatusEnum 同源。 */
export const SCM_SCALE_DEVICE_STATUS_ENUM: Record<ScaleDeviceStatus, {desc: string; color: string}> = {
  PENDING_ACTIVATION: {desc: '待激活', color: 'orange'},
  ACTIVE: {desc: '已激活', color: 'green'},
  SUSPENDED: {desc: '已暂停', color: 'default'},
  REVOKED: {desc: '已撤销', color: 'red'},
};

export interface ScaleDeviceQuery extends SortingPage {
  keyword?: string;
  status?: ScaleDeviceStatus;
  warehouseId?: Id;
}

export interface ScaleDevice {
  id: Id;
  deviceCode?: string;
  deviceName?: string | null;
  deviceModel?: string | null;
  warehouseId?: Id;
  warehouseName?: string | null;
  stationCode?: string | null;
  status?: ScaleDeviceStatus;
  protocolType?: string | null;
  agentVersion?: string | null;
  pairingExpiresAt?: string | null;
  credentialExpiresAt?: string | null;
  lastSeenAt?: string | null;
  lastReadingAt?: string | null;
  queueDepth?: number | null;
  scaleConnected?: boolean | null;
  printerConnected?: boolean | null;
  revokedAt?: string | null;
  createdAt?: string | null;
  /** 仍在有效期内的员工会话数。 */
  activeSessionCount?: number | null;
}

export interface ScaleDeviceRegisterForm {
  deviceCode: string;
  deviceName?: string;
  deviceModel?: string;
  warehouseId: Id;
  stationCode?: string;
}

/** 登记结果：配对码明文只出现这一次。 */
export interface ScaleDeviceRegisterResult {
  deviceId: Id;
  deviceCode: string;
  pairingCode: string;
  pairingExpiresAt?: string | null;
}

export interface ScaleOperatorSessionForm {
  employeeId: Id;
  /** 5–720 分钟，一个班次通常不超过 12 小时。 */
  validMinutes: number;
}

/** 会话签发结果：令牌明文只出现这一次。 */
export interface ScaleOperatorSessionResult {
  sessionToken: string;
  expiresAt?: string | null;
  employeeId?: Id;
}

export interface ScaleDeviceSession {
  id: Id;
  deviceId?: Id;
  employeeId?: Id;
  employeeName?: string | null;
  issuedAt?: string | null;
  expiresAt?: string | null;
  endedAt?: string | null;
  endReason?: string | null;
  active?: boolean | null;
}
