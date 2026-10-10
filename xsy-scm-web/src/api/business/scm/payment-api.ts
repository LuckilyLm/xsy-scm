import {getRequest, postRequest, request, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {Id} from '/@/views/business/scm/order/order-types';

export interface PaymentTransaction {
  id: Id; transactionNo: string; providerTransactionNo: string; providerAmount?: string;
  provider: string; status: string; paidAt?: string;
}
export interface PaymentIntent {
  id: Id; intentNo: string; amount: string; method: 'ONLINE' | 'BALANCE'; provider: string;
  status: string; transactions?: PaymentTransaction[];
}
export interface PaymentCreate {
  customerId: Id; sourceType: 'SALES_ORDER'; sourceId: Id; amount: string;
  method: 'ONLINE' | 'BALANCE'; provider: 'MOCK' | 'INTERNAL_BALANCE';
}
export interface BalanceMovement {
  id: Id; movementNo: string; customerName?: string; settlementCustomerName?: string;
  movementType: string; direction: string; amount: string; sourceType?: string; sourceId?: Id;
  occurredAt: string; reason?: string;
}
export const paymentApi = {
  create: (data: PaymentCreate, key: string) => request({url: '/scm/payment/intent/create', method: 'post', data,
    headers: {'Idempotency-Key': key}}) as unknown as Promise<ScmResponse<PaymentIntent>>,
  query: (orderId: Id, pageNum: number, options?: RequestOptions) => postRequest('/scm/payment/intent/query', {
    sourceType: 'SALES_ORDER', sourceId: orderId, pageNum, pageSize: 10,
  }, options) as unknown as Promise<ScmResponse<ScmPage<PaymentIntent>>>,
  detail: (id: Id, options?: RequestOptions) =>
    getRequest(`/scm/payment/intent/detail/${id}`, {}, options) as unknown as Promise<ScmResponse<PaymentIntent>>,
  movements: (filter: {movementId?: Id; sourceType?: string; sourceId?: Id}, options?: RequestOptions) =>
    postRequest('/scm/balance/movement/query', {...filter, pageNum: 1, pageSize: 10}, options) as unknown as
      Promise<ScmResponse<ScmPage<BalanceMovement>>>,
};
