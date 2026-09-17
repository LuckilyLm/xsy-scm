/*
 * 库存流水接口（只读）
 */
import { postRequest } from '/@/lib/axios';

export const stockFlowApi = {
  query: (param) => {
    return postRequest('/stock/flow/query', param);
  },
};
