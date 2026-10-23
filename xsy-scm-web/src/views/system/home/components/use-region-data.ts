/** 首页每个区块单独处理加载、错误和重试，某一区块失败不影响其他区块。 */
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

export function useRegionData<T>(fetcher: () => Promise<ScmResponse<T>>, fallbackMessage: string): RegionData<T> {
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
