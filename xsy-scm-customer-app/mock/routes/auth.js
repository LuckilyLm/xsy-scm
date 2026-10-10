import { fail, ok } from '../helpers';
import { issueSession, revokeSession } from '../session';
import { MOCK_CUSTOMER } from '../fixtures';
import { PLATFORM_ERROR_CODE } from '@/constants/error-code-const';
import { USER_TOKEN } from '@/constants/local-storage-key-const';

const LOGIN_FAILED = 40170;

function profile(firstPasswordChange = false) {
  return { ...MOCK_CUSTOMER, firstPasswordChange };
}

export const authRoutes = [
  {
    method: 'POST',
    path: '/scm/mall/auth/login',
    handler: async ({ data }) => {
      if (!/^1\d{10}$/.test(data?.phone || '') || !data?.password) {
        return fail(PLATFORM_ERROR_CODE.PARAM_ERROR, '请输入手机号和密码');
      }
      if (String(data.password).length < 8) return fail(LOGIN_FAILED, '手机号或密码不正确');
      const { token } = issueSession(MOCK_CUSTOMER);
      return ok({ token, profile: profile(String(data.password) === '12345678') });
    },
  },
  {
    method: 'POST',
    path: '/scm/mall/auth/logout',
    handler: async () => {
      revokeSession(uni.getStorageSync(USER_TOKEN));
      return ok(null);
    },
  },
  {
    method: 'GET',
    path: '/scm/mall/auth/profile',
    handler: async ({ session }) => session
      ? ok(profile(false))
      : fail(PLATFORM_ERROR_CODE.LOGIN_STATE_INVALID, '登录已失效，请重新登录'),
  },
  {
    method: 'POST',
    path: '/scm/mall/auth/password/change',
    handler: async ({ session, data }) => {
      if (!session) return fail(PLATFORM_ERROR_CODE.LOGIN_STATE_INVALID, '登录已失效，请重新登录');
      if (!data?.oldPassword || String(data?.newPassword || '').length < 8) {
        return fail(PLATFORM_ERROR_CODE.PARAM_ERROR, '新密码至少 8 位');
      }
      return ok(null);
    },
  },
];
