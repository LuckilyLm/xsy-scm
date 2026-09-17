/*
 * 采购收货接口
 */
import { postRequest } from '@/lib/smart-request';

export const purchaseReceiveApi = {
  // 分页查询收货单
  query: (param) => {
    return postRequest('/purchase/receive/query', param);
  },

  // 新增收货单（支持 directStock 直接入库）
  add: (param) => {
    return postRequest('/purchase/receive/add', param);
  },

  // 入库确认：已收 -> 已入库（Q5）
  confirmInbound: (receiveId) => {
    return postRequest(`/purchase/receive/confirmInbound/${receiveId}`);
  },
};
