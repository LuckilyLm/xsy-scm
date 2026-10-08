import {onDeactivated, onScopeDispose, ref, shallowRef, watch} from 'vue';
import type {ScmResponse} from '/@/types/business/scm/customer';
import type {FinanceId} from './finance-types';
import {financeError} from './finance-errors';

/** 详情只接收最后一次请求；关闭或离开页面后，迟到响应不能回填。 */
export function useFinanceDetail<T>(fetcher: (id: FinanceId) => Promise<ScmResponse<T>>) {
    const open = ref(false);
    const loading = ref(false);
    const error = ref('');
    const data = shallowRef<T | null>(null);
    let selectedId: FinanceId | undefined;
    let sequence = 0;

    function invalidate() {
        sequence += 1;
        selectedId = undefined;
        loading.value = false;
        data.value = null;
        error.value = '';
    }

    async function load() {
        if (!open.value || selectedId === undefined) return;
        const current = ++sequence;
        const id = selectedId;
        loading.value = true;
        error.value = '';
        data.value = null;
        try {
            const response = await fetcher(id);
            if (current !== sequence || !open.value) return;
            data.value = response.data ?? null;
            if (!data.value) error.value = '未找到单据详情';
        } catch (cause) {
            if (current === sequence && open.value) error.value = financeError(cause);
        } finally {
            if (current === sequence) loading.value = false;
        }
    }

    function show(id: FinanceId) {
        selectedId = id;
        open.value = true;
        return load();
    }

    watch(open, (visible) => {
        if (!visible) invalidate();
    }, {flush: 'sync'});
    onDeactivated(() => { open.value = false; });
    onScopeDispose(invalidate);

    return {open, loading, error, data, show, load};
}
