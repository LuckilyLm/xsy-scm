/*
 * Mock 契约层 · 认证
 *
 * 对齐 src/api/mall/auth-api.js 的路径。
 *
 * 登录成功后返回的 `data` 结构必须与 store 的 setUserLoginInfo 对齐：
 *   { token, accountId, customerId, customerCode, customerName, contactName, phone, wechatBound, customerStatus }
 */
import { fail, ok } from '../helpers';
import { issueSession, revokeSession } from '../session';
import { MOCK_CUSTOMER } from '../fixtures';
import { PLATFORM_ERROR_CODE } from '@/constants/error-code-const';
import { USER_TOKEN } from '@/constants/local-storage-key-const';

/** mock 环境固定验证码，便于联调；sendSmsCode 会把它回显出来 */
const MOCK_SMS_CODE = '123456';

/**
 * 登录失败码。v1 用 40170，且该码段在 V2 中确认空闲
 * （见 docs/reference/2026-09-22-遗留商城v1契约参考.md §5），
 * 但正式码值仍需与后端一次性冻结后再落到 MALL_ERROR_CODE。
 */
const CODE_LOGIN_FAILED = 40170;

function buildLoginPayload(token) {
  return { ...MOCK_CUSTOMER, token, wechatBound: false };
}

export const authRoutes = [
  {
    method: 'POST',
    path: '/scm/mall/auth/login',
    handler: async ({ data }) => {
      const { loginName, password } = data || {};
      if (!loginName || !password) {
        return fail(PLATFORM_ERROR_CODE.PARAM_ERROR, '请输入账号和密码');
      }
      // mock 环境：任意非空账密均可登录，密码 6 位以上更接近真实校验
      if (String(password).length < 6) {
        return fail(CODE_LOGIN_FAILED, '账号或密码不正确');
      }
      const { token } = issueSession(MOCK_CUSTOMER);
      return ok(buildLoginPayload(token));
    },
  },

  {
    method: 'POST',
    path: '/scm/mall/auth/sms-login',
    handler: async ({ data }) => {
      const { phone, code } = data || {};
      if (!/^1\d{10}$/.test(phone || '')) {
        return fail(PLATFORM_ERROR_CODE.PARAM_ERROR, '请输入正确的手机号');
      }
      if (String(code) !== MOCK_SMS_CODE) {
        return fail(CODE_LOGIN_FAILED, `验证码不正确（mock 环境固定为 ${MOCK_SMS_CODE}）`);
      }
      const { token } = issueSession(MOCK_CUSTOMER);
      return ok(buildLoginPayload(token));
    },
  },

  {
    method: 'POST',
    path: '/scm/mall/auth/wechat-login',
    handler: async () => {
      // v1 该接口返回 50170 未开通；mock 环境直接放行，方便 H5 调试
      const { token } = issueSession({ ...MOCK_CUSTOMER, wechatBound: true });
      return ok({ ...buildLoginPayload(token), wechatBound: true });
    },
  },

  {
    method: 'POST',
    path: '/scm/mall/auth/sms-code/:phone',
    handler: async ({ params }) => {
      if (!/^1\d{10}$/.test(params.phone || '')) {
        return fail(PLATFORM_ERROR_CODE.PARAM_ERROR, '请输入正确的手机号');
      }
      // 真实环境绝不可回显验证码；这里回显是为了让 mock 联调可自助完成
      return ok({ code: MOCK_SMS_CODE, expiresIn: 300, mock: true });
    },
  },

  {
    method: 'POST',
    path: '/scm/mall/auth/logout',
    handler: async () => {
      const token = uni.getStorageSync(USER_TOKEN);
      revokeSession(token);
      return ok(null);
    },
  },

  {
    method: 'GET',
    path: '/scm/mall/auth/profile',
    handler: async ({ session }) => {
      if (!session) {
        // 交给请求层按会话失效统一处理（清会话 + 跳登录）
        return fail(PLATFORM_ERROR_CODE.LOGIN_STATE_INVALID, '登录已失效，请重新登录');
      }
      return ok({ ...session, token: uni.getStorageSync(USER_TOKEN) });
    },
  },
];
