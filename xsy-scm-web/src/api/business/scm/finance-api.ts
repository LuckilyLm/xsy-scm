/** 财务域查询、导出与只追加命令。 */
import {getRequest, postDownload, postRequest, request} from '/@/lib/axios';
import type {RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
  CustomerExpense,
  CustomerExpenseAdd,
  FinanceBusinessType,
  FinanceCodCollectionForm,
  FinanceCodCollectionPreview,
  FinanceId,
  FinanceOperationLog,
  FinancePageQuery,
  FinancePayable,
  FinancePayableDetail,
  FinancePayableRedForm,
  FinancePayment,
  FinancePaymentAddForm,
  FinancePaymentDetail,
  FinancePaymentReverseForm,
  FinanceReceivable,
  FinanceReceivableDetail,
  FinanceReceipt,
  FinanceReceiptAddForm,
  FinanceReceiptDetail,
  FinanceReceiptReverseForm,
  FinancePosting,
  FinancePostingAddForm,
  FinancePostingUpdateForm,
  FinancePostingVoidForm,
  FinanceRefundOption,
  FinanceRefundOptionQuery,
  FinanceWaiver,
  FinanceWaiverAddForm,
  FinanceWaiverReverseForm,
  FinanceWriteOff,
  FinanceWriteOffAddForm,
  FinanceWriteOffAutoForm,
  FinanceWriteOffAutoPlan,
  FinanceWriteOffReverseForm,
  PayableQuery,
  PaymentQuery,
  PostingQuery,
  ReceivableQuery,
  ReceiptQuery,
  WriteOffQuery,
} from '/@/views/business/scm/finance/finance-types';

const BASE = '/scm/finance';
const commandKeys = new Map<string, string>();

async function command<T>(path: string, data: unknown): Promise<ScmResponse<T>> {
  const signature = `${path}:${JSON.stringify(data)}`;
  let key = commandKeys.get(signature);
  if (!key) {
    key = crypto.randomUUID();
    commandKeys.set(signature, key);
  }
  const result = await request<T>({url: path, method: 'post', data, headers: {'Idempotency-Key': key}});
  commandKeys.delete(signature);
  return result;
}

export const financeApi = {
  customerExpenseQuery: (data: {expenseMonth: string; customerId?: FinanceId; pageNum: number; pageSize: number}, options?: RequestOptions) =>
    postRequest<ScmPage<CustomerExpense>>(`${BASE}/customer-expense/query`, data, options),
  customerExpenseAdd: (data: CustomerExpenseAdd) => command<CustomerExpense>(`${BASE}/customer-expense/add`, data),
  customerExpenseVoid: (data: {expenseId: FinanceId; reason: string}) => command<CustomerExpense>(`${BASE}/customer-expense/void`, data),
  receivableQuery: (data: ReceivableQuery, options?: RequestOptions) =>
    postRequest<ScmPage<FinanceReceivable>>(`${BASE}/receivable/query`, data, options),
  receivableDetail: (id: FinanceId, options?: RequestOptions) => getRequest<FinanceReceivableDetail>(`${BASE}/receivable/${id}`, {}, options),
  receivableExport: (data: Partial<FinancePageQuery> & Omit<ReceivableQuery, 'pageNum' | 'pageSize'>) =>
    postDownload(`${BASE}/receivable/export`, data),

  payableQuery: (data: PayableQuery, options?: RequestOptions) => postRequest<ScmPage<FinancePayable>>(`${BASE}/payable/query`, data, options),
  payableDetail: (id: FinanceId, options?: RequestOptions) => getRequest<FinancePayableDetail>(`${BASE}/payable/${id}`, {}, options),
  payableRed: (data: FinancePayableRedForm) => command(`${BASE}/payable/red`, data),
  payableExport: (data: Partial<FinancePageQuery> & Omit<PayableQuery, 'pageNum' | 'pageSize'>) => postDownload(`${BASE}/payable/export`, data),

  receiptQuery: (data: ReceiptQuery, options?: RequestOptions) => postRequest<ScmPage<FinanceReceipt>>(`${BASE}/receipt/query`, data, options),
  receiptDetail: (id: FinanceId, options?: RequestOptions) => getRequest<FinanceReceiptDetail>(`${BASE}/receipt/${id}`, {}, options),
  receiptAdd: (data: FinanceReceiptAddForm) => command(`${BASE}/receipt/add`, data),
  receiptCodPreview: (orderId: FinanceId, options?: RequestOptions) =>
    getRequest<FinanceCodCollectionPreview>(`${BASE}/receipt/cod-preview/${orderId}`, {}, options),
  receiptCodCollect: (data: FinanceCodCollectionForm) => command<FinanceReceipt>(`${BASE}/receipt/cod-collection`, data),
  receiptReverse: (data: FinanceReceiptReverseForm) => command(`${BASE}/receipt/reverse`, data),
  receiptExport: (data: Partial<FinancePageQuery> & Omit<ReceiptQuery, 'pageNum' | 'pageSize'>) => postDownload(`${BASE}/receipt/export`, data),

  paymentQuery: (data: PaymentQuery, options?: RequestOptions) => postRequest<ScmPage<FinancePayment>>(`${BASE}/payment/query`, data, options),
  paymentDetail: (id: FinanceId, options?: RequestOptions) => getRequest<FinancePaymentDetail>(`${BASE}/payment/${id}`, {}, options),
  refundOptions: (data: FinanceRefundOptionQuery, options?: RequestOptions) =>
    postRequest<ScmPage<FinanceRefundOption>>(`${BASE}/payment/refund-options`, data, options),
  paymentAdd: (data: FinancePaymentAddForm) => command(`${BASE}/payment/add`, data),
  paymentReverse: (data: FinancePaymentReverseForm) => command(`${BASE}/payment/reverse`, data),
  paymentExport: (data: Partial<FinancePageQuery> & Omit<PaymentQuery, 'pageNum' | 'pageSize'>) => postDownload(`${BASE}/payment/export`, data),

  writeOffQuery: (data: WriteOffQuery, options?: RequestOptions) => postRequest<ScmPage<FinanceWriteOff>>(`${BASE}/write-off/query`, data, options),
  writeOffAdd: (data: FinanceWriteOffAddForm) => command(`${BASE}/write-off/add`, data),
  writeOffAutoPreview: (data: FinanceWriteOffAutoForm) => postRequest<FinanceWriteOffAutoPlan[]>(`${BASE}/write-off/auto/preview`, data),
  writeOffAuto: (data: FinanceWriteOffAutoForm) => command(`${BASE}/write-off/auto`, data),
  writeOffReverse: (data: FinanceWriteOffReverseForm) => command(`${BASE}/write-off/reverse`, data),
  writeOffExport: (data: Partial<FinancePageQuery> & Omit<WriteOffQuery, 'pageNum' | 'pageSize'>) => postDownload(`${BASE}/write-off/export`, data),

  postingQuery: (data: PostingQuery, options?: RequestOptions) => postRequest<ScmPage<FinancePosting>>(`${BASE}/posting/query`, data, options),
  postingDetail: (id: FinanceId, options?: RequestOptions) => getRequest<FinancePosting>(`${BASE}/posting/${id}`, {}, options),
  postingAdd: (data: FinancePostingAddForm) => command<FinancePosting>(`${BASE}/posting/add`, data),
  postingUpdate: (data: FinancePostingUpdateForm) => command<FinancePosting>(`${BASE}/posting/update`, data),
  postingVoid: (data: FinancePostingVoidForm) => command<FinancePosting>(`${BASE}/posting/void`, data),
  postingExport: (data: Partial<FinancePageQuery> & Omit<PostingQuery, 'pageNum' | 'pageSize'>) => postDownload(`${BASE}/posting/export`, data),

  waiverAdd: (data: FinanceWaiverAddForm) => command<FinanceWaiver>(`${BASE}/waiver/add`, data),
  waiverReverse: (data: FinanceWaiverReverseForm) => command<FinanceWaiver>(`${BASE}/waiver/reverse`, data),
  operationLogs: (businessType: FinanceBusinessType, businessId: FinanceId, options?: RequestOptions) =>
    getRequest<FinanceOperationLog[]>(`${BASE}/log/query`, {businessType, businessId}, options),
};

export default financeApi;
