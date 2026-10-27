/** 电子秤设备管理（后台员工）：登记、签发员工会话、查看会话与撤销绑定。设备端接口不在这里。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {Id} from '/@/views/business/scm/sorting/sorting-types';
import type {
  ScaleDevice,
  ScaleDeviceQuery,
  ScaleDeviceRegisterForm,
  ScaleDeviceRegisterResult,
  ScaleDeviceSession,
  ScaleOperatorSessionForm,
  ScaleOperatorSessionResult,
} from '/@/views/business/scm/sorting/scale-device-types';

const BASE = '/scm/scale/device';

export const scaleDeviceApi = {
  query: (data: ScaleDeviceQuery, options?: RequestOptions) =>
    postRequest<ScmPage<ScaleDevice>>(`${BASE}/query`, data, options),
  register: (data: ScaleDeviceRegisterForm) => postRequest<ScaleDeviceRegisterResult>(`${BASE}/register`, data),
  /** 签发员工会话：令牌明文只在响应里出现一次，关掉弹窗就找不回来。 */
  issueSession: (deviceId: Id, data: ScaleOperatorSessionForm) =>
    postRequest<ScaleOperatorSessionResult>(`${BASE}/${deviceId}/session`, data),
  sessions: (deviceId: Id, options?: RequestOptions) =>
    getRequest<ScaleDeviceSession[]>(`${BASE}/${deviceId}/sessions`, {}, options),
  endSession: (deviceId: Id, sessionId: Id) => postRequest<string>(`${BASE}/${deviceId}/session/${sessionId}/end`, {}),
  /** 撤销绑定：设备立即失效，历史读数保留，编号不释放。 */
  revoke: (deviceId: Id) => postRequest<string>(`${BASE}/${deviceId}/revoke`, {}),
};

export default scaleDeviceApi;
