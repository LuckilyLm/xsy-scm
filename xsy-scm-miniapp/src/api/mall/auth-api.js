/*
 * 商城认证接口
 *
 * ⚠️ 后端 `/scm/mall/**` 尚未实现，本文件的路径与出入参与后端冻结契约一致后才能联调。
 * 路径前缀遵循 V2 后端既有约定 `/scm/<domain>`（SmartAdmin 的 SCM 控制器均如此），
 * 而不是旧商城的 `/api/mall/**`。
 *
 * 身份模型见规划文档 §31：服务端由登录态派生客户身份，客户端不传 customerId。
 */
import { getRequest, postRequest } from '@/lib/smart-request';

export const mallAuthApi = {
  /**
   * 账号密码登录（可选路径，用于 AppID 未就绪时兜底）
   * @param {{loginName: string, password: string}} param
   */
  login: (param) => postRequest('/scm/mall/auth/login', param),

  /**
   * 微信授权登录
   * @param {{code: string}} param  wx.login 拿到的 code，服务端换 openid
   */
  wechatLogin: (param) => postRequest('/scm/mall/auth/wechat-login', param),

  /**
   * 手机号验证码登录
   * @param {{phone: string, code: string}} param
   */
  smsLogin: (param) => postRequest('/scm/mall/auth/sms-login', param),

  /**
   * 发送登录短信验证码
   * @param {string} phone
   */
  sendSmsCode: (phone) => postRequest(`/scm/mall/auth/sms-code/${phone}`),

  /** 退出登录 */
  logout: () => postRequest('/scm/mall/auth/logout'),

  /** 获取当前登录客户资料（应用启动时用于恢复会话） */
  getProfile: () => getRequest('/scm/mall/auth/profile'),
};
