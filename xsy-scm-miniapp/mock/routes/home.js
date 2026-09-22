/*
 * Mock 契约层 · 首页装修
 *
 * 对齐 src/api/mall/home-api.js 的路径。
 * 楼层类型见规划 §9.1；P0 只消费「一组配置 + 一处生效」，不做通用拖拽装修器。
 */
import { ok } from '../helpers';
import { MOCK_CUSTOMER, MOCK_HOME_FLOORS } from '../fixtures';

export const homeRoutes = [
  {
    method: 'GET',
    path: '/scm/mall/home',
    handler: async () =>
      ok({
        store: {
          customerId: MOCK_CUSTOMER.customerId,
          customerName: MOCK_CUSTOMER.customerName,
          customerStatus: MOCK_CUSTOMER.customerStatus,
        },
        theme: { primaryColor: '#16a34a' },
        // 弹窗体系见规划 §10；P0 暂不投放
        popup: null,
        sections: MOCK_HOME_FLOORS,
        generatedAt: new Date().toISOString(),
      }),
  },

  {
    method: 'GET',
    path: '/scm/mall/theme',
    handler: async () => ok({ primaryColor: '#16a34a', logoText: '鲜蔬源商城' }),
  },
];
