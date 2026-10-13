import { fail, ok, paginate } from '../helpers';
import { PLATFORM_ERROR_CODE } from '@/constants/error-code-const';

function guard(session) {
  return session ? null : fail(PLATFORM_ERROR_CODE.LOGIN_STATE_INVALID, '登录已失效，请重新登录');
}

// 本地 mock 只提供空账本，用于页面空态；不伪造充值、授信或应收资金事实。
export const fundsRoutes = [
  {
    method: 'GET', path: '/scm/mall/funds/wallet/summary',
    handler: async ({ session }) => guard(session) || ok({
      currency: 'CNY', balanceVisible: true, hasWallet: false, availableBalance: '0.0000',
    }),
  },
  {
    method: 'GET', path: '/scm/mall/funds/wallet/movements',
    handler: async ({ session, data }) => guard(session) || ok(paginate([], data)),
  },
  {
    method: 'GET', path: '/scm/mall/funds/credit/summary',
    handler: async ({ session }) => guard(session) || ok({
      sharedSettlement: false, creditLimitVisible: false, creditLimit: null,
      creditPeriodType: null, creditPeriodValue: null, creditPeriodUnit: null,
      creditAmountThreshold: null, settleDay: null,
      openReceivableAmount: '0.0000', confirmedOrderAmount: '0.0000', earliestOverdueDate: null,
    }),
  },
  {
    method: 'GET', path: '/scm/mall/funds/receivables',
    handler: async ({ session, data }) => guard(session) || ok(paginate([], data)),
  },
  {
    method: 'GET', path: '/scm/mall/funds/receivables/:id',
    handler: async ({ session }) => guard(session) || fail(40490, '账单不存在'),
  },
];
