import { getRequest } from '@/lib/smart-request';

export const mallFundsApi = {
  walletSummary: () => getRequest('/scm/mall/funds/wallet/summary'),
  walletMovements: (query) => getRequest('/scm/mall/funds/wallet/movements', query),
  creditSummary: () => getRequest('/scm/mall/funds/credit/summary'),
  receivables: (query) => getRequest('/scm/mall/funds/receivables', query),
  receivable: (id) => getRequest(`/scm/mall/funds/receivables/${id}`),
};
