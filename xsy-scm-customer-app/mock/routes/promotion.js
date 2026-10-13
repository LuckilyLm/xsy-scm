import { fail, ok, paginate } from '../helpers';
import { PLATFORM_ERROR_CODE } from '@/constants/error-code-const';

const guard = (session) => session ? null : fail(PLATFORM_ERROR_CODE.LOGIN_STATE_INVALID, '登录已失效，请重新登录');

// 离线开发只提供空营销事实，不能把虚构活动或领券成功当作正式权益。
export const promotionRoutes = [
  { method: 'GET', path: '/scm/mall/promotion/activities',
    handler: async ({ session }) => guard(session) || ok([]) },
  { method: 'GET', path: '/scm/mall/promotion/new-products',
    handler: async ({ session }) => guard(session) || ok([]) },
  { method: 'GET', path: '/scm/mall/promotion/coupons/claimable',
    handler: async ({ session, data }) => guard(session) || ok(paginate([], data)) },
  { method: 'GET', path: '/scm/mall/promotion/coupons/mine',
    handler: async ({ session, data }) => guard(session) || ok(paginate([], data)) },
  { method: 'POST', path: '/scm/mall/promotion/coupons/:couponId/claim',
    handler: async ({ session }) => guard(session) || fail(41334, '离线模式不提供领券') },
];
