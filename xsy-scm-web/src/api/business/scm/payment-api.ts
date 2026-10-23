import {getRequest, postRequest, request, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {Id} from '/@/views/business/scm/order/order-types';

export interface PaymentTransaction {
  id: Id;
  transactionNo: string;
  providerTransactionNo: string;
  providerAmount?: string;
  provider: string;
  status: string;
  paidAt?: string;
}
export interface PaymentIntent {
  id: Id;
  intentNo: string;
  amount: string;
  method: 'ONLINE' | 'BALANCE';
  provider: string;
  status: string;
  transactions?: PaymentTransaction[];
}
export interface PaymentCreate {
  customerId: Id;
  sourceType: 'SALES_ORDER';
  sourceId: Id;
  amount: string;
  method: 'ONLINE' | 'BALANCE';
  provider: 'MOCK' | 'INTERNAL_BALANCE';
}
export interface BalanceMovement {
  id: Id;
  movementNo: string;
  customerName?: string;
  settlementCustomerName?: string;
  movementType: string;
  direction: string;
  amount: string;
  sourceType?: string;
  sourceId?: Id;
  occurredAt: string;
  reason?: string;
}
export const paymentApi = {
  create: (data: PaymentCreate, key: string) =>
    request<PaymentIntent>({url: '/scm/payment/intent/create', method: 'post', data, headers: {'Idempotency-Key': key}}),
  query: (orderId: Id, pageNum: number, options?: RequestOptions) =>
    postRequest<ScmPage<PaymentIntent>>(
      '/scm/payment/intent/query',
      {
        sourceType: 'SALES_ORDER',
        sourceId: orderId,
        pageNum,
        pageSize: 10,
      },
      options
    ),
  detail: (id: Id, options?: RequestOptions) => getRequest<PaymentIntent>(`/scm/payment/intent/detail/${id}`, {}, options),
  movements: (filter: {movementId?: Id; sourceType?: string; sourceId?: Id}, options?: RequestOptions) =>
    postRequest<ScmPage<BalanceMovement>>('/scm/balance/movement/query', {...filter, pageNum: 1, pageSize: 10}, options),
};
