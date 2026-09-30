/** Finance R1 page queries, exports and append-only commands. */
import {getRequest, postDownload, postRequest, request} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    FinanceBusinessType,
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
    FinanceRefundOption,
    FinanceRefundOptionQuery,
    FinanceWriteOff,
    FinanceWriteOffAddForm,
    FinanceWriteOffReverseForm,
    PayableQuery,
    PaymentQuery,
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
    const result = await request({url: path, method: 'post', data, headers: {'Idempotency-Key': key}}) as unknown as ScmResponse<T>;
    commandKeys.delete(signature);
    return result;
}

export const financeApi = {
    receivableQuery: (data: ReceivableQuery) =>
        postRequest(`${BASE}/receivable/query`, data) as unknown as Promise<ScmResponse<ScmPage<FinanceReceivable>>>,
    receivableDetail: (id: FinanceId) =>
        getRequest(`${BASE}/receivable/${id}`, {}) as unknown as Promise<ScmResponse<FinanceReceivableDetail>>,
    receivableExport: (data: Partial<FinancePageQuery> & Omit<ReceivableQuery, 'pageNum' | 'pageSize'>) =>
        postDownload(`${BASE}/receivable/export`, data),

    payableQuery: (data: PayableQuery) =>
        postRequest(`${BASE}/payable/query`, data) as unknown as Promise<ScmResponse<ScmPage<FinancePayable>>>,
    payableDetail: (id: FinanceId) =>
        getRequest(`${BASE}/payable/${id}`, {}) as unknown as Promise<ScmResponse<FinancePayableDetail>>,
    payableRed: (data: FinancePayableRedForm) => command(`${BASE}/payable/red`, data),
    payableExport: (data: Partial<FinancePageQuery> & Omit<PayableQuery, 'pageNum' | 'pageSize'>) =>
        postDownload(`${BASE}/payable/export`, data),

    receiptQuery: (data: ReceiptQuery) =>
        postRequest(`${BASE}/receipt/query`, data) as unknown as Promise<ScmResponse<ScmPage<FinanceReceipt>>>,
    receiptDetail: (id: FinanceId) =>
        getRequest(`${BASE}/receipt/${id}`, {}) as unknown as Promise<ScmResponse<FinanceReceiptDetail>>,
    receiptAdd: (data: FinanceReceiptAddForm) => command(`${BASE}/receipt/add`, data),
    receiptReverse: (data: FinanceReceiptReverseForm) => command(`${BASE}/receipt/reverse`, data),
    receiptExport: (data: Partial<FinancePageQuery> & Omit<ReceiptQuery, 'pageNum' | 'pageSize'>) =>
        postDownload(`${BASE}/receipt/export`, data),

    paymentQuery: (data: PaymentQuery) =>
        postRequest(`${BASE}/payment/query`, data) as unknown as Promise<ScmResponse<ScmPage<FinancePayment>>>,
    paymentDetail: (id: FinanceId) =>
        getRequest(`${BASE}/payment/${id}`, {}) as unknown as Promise<ScmResponse<FinancePaymentDetail>>,
    refundOptions: (data: FinanceRefundOptionQuery) =>
        postRequest(`${BASE}/payment/refund-options`, data) as unknown as Promise<ScmResponse<ScmPage<FinanceRefundOption>>>,
    paymentAdd: (data: FinancePaymentAddForm) => command(`${BASE}/payment/add`, data),
    paymentReverse: (data: FinancePaymentReverseForm) => command(`${BASE}/payment/reverse`, data),
    paymentExport: (data: Partial<FinancePageQuery> & Omit<PaymentQuery, 'pageNum' | 'pageSize'>) =>
        postDownload(`${BASE}/payment/export`, data),

    writeOffQuery: (data: WriteOffQuery) =>
        postRequest(`${BASE}/write-off/query`, data) as unknown as Promise<ScmResponse<ScmPage<FinanceWriteOff>>>,
    writeOffAdd: (data: FinanceWriteOffAddForm) => command(`${BASE}/write-off/add`, data),
    writeOffReverse: (data: FinanceWriteOffReverseForm) => command(`${BASE}/write-off/reverse`, data),
    writeOffExport: (data: Partial<FinancePageQuery> & Omit<WriteOffQuery, 'pageNum' | 'pageSize'>) =>
        postDownload(`${BASE}/write-off/export`, data),
    operationLogs: (businessType: FinanceBusinessType, businessId: FinanceId) =>
        getRequest(`${BASE}/log/query`, {businessType, businessId}) as unknown as Promise<ScmResponse<FinanceOperationLog[]>>,
};

export default financeApi;
