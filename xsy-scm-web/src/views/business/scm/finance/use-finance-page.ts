import {reactive, ref, shallowRef} from 'vue';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {FinancePageQuery} from './finance-types';
import {financeError} from './finance-errors';

export type FinancePageExport<Q> = (query: Omit<Q, 'pageNum' | 'pageSize'>) => Promise<unknown>;

export function useFinancePage<T, Q extends FinancePageQuery>(
    queryApi: (query: Q) => Promise<ScmResponse<ScmPage<T>>>,
    exportApi: FinancePageExport<Q>,
) {
    const tableData = shallowRef<T[]>([]);
    const total = ref(0);
    const loading = ref(false);
    const exporting = ref(false);
    const error = ref('');
    const queryState = reactive({requestId: 0});

    async function queryData(query: Q) {
        const requestId = ++queryState.requestId;
        loading.value = true;
        error.value = '';
        try {
            const response = await queryApi(query);
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

    async function exportData(query: Q) {
        exporting.value = true;
        error.value = '';
        const {pageNum: _pageNum, pageSize: _pageSize, ...filters} = query;
        void _pageNum;
        void _pageSize;
        try {
            await exportApi(filters as Omit<Q, 'pageNum' | 'pageSize'>);
        } catch (cause) {
            error.value = financeError(cause);
        } finally {
            exporting.value = false;
        }
    }

    return {tableData, total, loading, exporting, error, queryData, exportData};
}
