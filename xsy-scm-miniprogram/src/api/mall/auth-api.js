import { getRequest, postRequest } from '@/lib/xsy-request';

export const mallAuthApi = {
  login: (form) => postRequest('/scm/mall/auth/login', form, { silent: true }),
  /** code仅来自微信手机号授权事件，手机号由服务端验证；失败不清理已有会话。 */
  wechatPhoneLogin: (form) => postRequest('/scm/mall/auth/wechat/phone-login', form, { anonymous: true, silent: true }),
  logout: () => postRequest('/scm/mall/auth/logout'),
  getProfile: () => getRequest('/scm/mall/auth/profile', {}, { silent: true }),
  changePassword: (form) => postRequest('/scm/mall/auth/password/change', form),
};
