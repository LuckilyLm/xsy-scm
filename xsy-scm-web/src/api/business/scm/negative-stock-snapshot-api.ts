import {getRequest, postRequest, postDownload, request, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {InventoryNegativeStockRow, ReportId} from '/@/views/business/scm/report/report-types';

export interface NegativeStockSnapshot {
  id: ReportId;
  snapshotNo: string;
  warehouseId: ReportId;
  warehouseName: string;
  snapshotMonth: string;
  capturedAt: string;
  rowCount: number;
  zeroCostRowCount: number;
  estimatedAmount: string;
  remark?: string;
  createdBy: string;
  rows?: InventoryNegativeStockRow[];
}
const base = '/scm/report/inventory/negative-stock/snapshot';
const commandKeys = new Map<string, string>();
export const negativeStockSnapshotApi = {
  query: (data: {month: string; warehouseId?: ReportId; pageNum: number; pageSize: number}, options?: RequestOptions) =>
    postRequest<ScmPage<NegativeStockSnapshot>>(base + '/query', data, options),
  detail: (id: ReportId, options?: RequestOptions) => getRequest<NegativeStockSnapshot>(base + '/' + id, {}, options),
  export: (id: ReportId) => postDownload(base + '/' + id + '/export', {}),
  async create(data: {warehouseId: ReportId; remark?: string}) {
    const signature = JSON.stringify(data);
    const key = commandKeys.get(signature) || crypto.randomUUID();
    commandKeys.set(signature, key);
    const result = await request<NegativeStockSnapshot>({url: base + '/create', method: 'post', data, headers: {'Idempotency-Key': key}});
    commandKeys.delete(signature);
    return result;
  },
};
