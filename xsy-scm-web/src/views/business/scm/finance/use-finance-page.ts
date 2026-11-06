import {reactive, ref, shallowRef, type Ref, type ShallowRef} from 'vue';
import type {RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {FinancePageQuery} from './finance-types';
import {financeError} from './finance-errors';

export type FinancePageExport<Q> = (query: Omit<Q, 'pageNum' | 'pageSize'>) => Promise<unknown>;

export interface FinancePageState<T, Q> {
  tableData: ShallowRef<T[]>;
  total: Ref<number>;
  loading: Ref<boolean>;
  exporting: Ref<boolean>;
  error: Ref<string>;
  queryData: (query: Q) => Promise<void>;
  exportData: (query: Q) => Promise<void>;
}

export function useFinancePage<T, Q extends FinancePageQuery>(
  queryApi: (query: Q, options?: RequestOptions) => Promise<ScmResponse<ScmPage<T>>>,
  exportApi: FinancePageExport<Q>
): FinancePageState<T, Q> {
  const tableData = shallowRef<T[]>([]);
  const total = ref(0);
  const loading = ref(false);
  const exporting = ref(false);
  const error = ref('');
  const queryState = reactive({requestId: 0});

  async function queryData(query: Q): Promise<void> {
    const requestId = ++queryState.requestId;
    loading.value = true;
    error.value = '';
    try {
      // 请求失败交给共享反馈处理，避免重复提示。
      const response = await queryApi(query, {suppressGlobalErrorMessage: true});
      if (requestId === queryState.requestId) {
        tableData.value = response.data.list ?? [];
        total.value = response.data.total ?? 0;
      }
    } catch (cause) {
      if (requestId === queryState.requestId) error.value = financeError(cause);
    } finally {
      if (requestId === queryState.requestId) loading.value = false;
    }
  }

  async function exportData(query: Q): Promise<void> {
    exporting.value = true;
    error.value = '';
    const {pageNum: _pageNum, pageSize: _pageSize, ...filters} = query;
    void _pageNum;
    void _pageSize;
    try {
      await exportApi(filters as Omit<Q, 'pageNum' | 'pageSize'>);
    } catch {
      // 导出失败只走全局 toast
    } finally {
      exporting.value = false;
    }
  }

  return {tableData, total, loading, exporting, error, queryData, exportData};
}
