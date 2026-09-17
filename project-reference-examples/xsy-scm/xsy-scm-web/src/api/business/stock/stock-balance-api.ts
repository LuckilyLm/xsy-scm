/*
 * 库存余额接口（只读）
 */
import { postRequest } from '/@/lib/axios';

export const stockBalanceApi = {
  query: (param) => {
    return postRequest('/stock/balance/query', param);
  },
};
