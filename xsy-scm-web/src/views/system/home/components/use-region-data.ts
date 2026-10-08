/**
 * 首页每个区块各自的加载状态。
 *
 * 首页有多个互不相关的数据源，刻意不做「一把抓」的全局 loading：任一失败都不该让整页
 * 变成错误页，也不该弹全局提示把用户从别处打断。这里只把「这一次请求的失败」收进区块
 * 自己的状态，由区块显示一行可重试的提示。
 *
 * `load` 永不 reject —— 调用方可以放心地并发等待它（例如顶部的刷新按钮）。
 */
import {onScopeDispose, ref, type Ref} from 'vue';
import type {ScmResponse} from '/@/types/business/scm/customer';

export interface RegionData<T> {
    data: Ref<T | null>;
    loading: Ref<boolean>;
    error: Ref<string>;
    load: () => Promise<void>;
}

/** 业务失败是 `{code, msg}` 信封，网络失败是 Error；两者都给用户一句可读的话。 */
function regionErrorMessage(failure: unknown, fallback: string): string {
    const envelope = failure as {msg?: string; data?: {msg?: string}; response?: {data?: {msg?: string}}} | undefined;
    const message = envelope?.response?.data?.msg ?? envelope?.data?.msg ?? envelope?.msg;
    if (typeof message === 'string' && message.trim()) {
        return message;
    }
    return fallback;
}

export function useRegionData<T>(
    fetcher: () => Promise<ScmResponse<T>>,
    fallbackMessage: string
): RegionData<T> {
    const data = ref<T | null>(null) as Ref<T | null>;
    const loading = ref(false);
    const error = ref('');
    // 快速连续切换条件时会有多次请求在飞，只认最后一次的结果
    let sequence = 0;
    let disposed = false;
    onScopeDispose(() => {
        disposed = true;
        sequence += 1;
    });

    async function load(): Promise<void> {
        if (disposed) return;
        const current = (sequence += 1);
        loading.value = true;
        error.value = '';
        try {
            const result = await fetcher();
            if (current !== sequence) {
                return;
            }
            data.value = result.data ?? null;
        } catch (failure) {
            if (current !== sequence) {
                return;
            }
            error.value = regionErrorMessage(failure, fallbackMessage);
        } finally {
            if (current === sequence) {
                loading.value = false;
            }
        }
    }

    return {data, loading, error, load};
}
