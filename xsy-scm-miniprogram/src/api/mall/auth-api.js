import {getRequest, postRequest} from '@/lib/smart-request';

export const mallAuthApi = {
  login: (form) => postRequest('/scm/mall/auth/login', form),
  /** code仅来自微信手机号授权事件，手机号由服务端验证；失败不清理已有会话。 */
  wechatPhoneLogin: (form) => postRequest('/scm/mall/auth/wechat/phone-login', form, {anonymous: true}),
  logout: () => postRequest('/scm/mall/auth/logout'),
  getProfile: () => getRequest('/scm/mall/auth/profile'),
  changePassword: (form) => postRequest('/scm/mall/auth/password/change', form),
};
