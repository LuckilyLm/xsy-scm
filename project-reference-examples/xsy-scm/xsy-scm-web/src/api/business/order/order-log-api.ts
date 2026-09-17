/*
 * 销售订单日志接口（仅查询与新增，不支持修改/删除）
 */
import { postRequest } from '/@/lib/axios';

export const orderLogApi = {
  query: (param) => postRequest('/order/log/query', param),
  add: (param) => postRequest('/order/log/add', param),
};
