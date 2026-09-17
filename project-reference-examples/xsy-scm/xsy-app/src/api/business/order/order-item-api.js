/*
 * 销售订单明细接口（价格快照 snapshotPrice / priceType 在此）
 */
import { postRequest } from '@/lib/smart-request';

export const orderItemApi = {
  query: (param) => postRequest('/order/item/query', param),
};
