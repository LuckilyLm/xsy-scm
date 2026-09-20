import { onBeforeUnmount, onMounted, ref } from 'vue';
import { screenApi } from '/@/api/business/screen-api';
import {
  emptyTrend,
  type BusinessData,
  type InventoryData,
  type PurchaseData,
  type ScreenRange,
  type TrendData,
} from '../types';

/**
 * 静默刷新间隔。
 *
 * <p>30 秒对应设计稿的「排行榜 30s 更新一次」。刷新时**不显示 loading**：
 * 大屏上任何一次整屏闪烁都会被注意到，而绝大多数刷新拿回的数据和上次一样。
 */
const REFRESH_INTERVAL = 30_000;

/**
 * 大屏数据源。
 *
 * <p><b>三态设计</b>：
 * <ul>
 *   <li>{@code loading} —— 只有**首次**加载为 true，用于整屏 loading 骨架；</li>
 *   <li>{@code error} —— 只有首次加载失败才设置，此时页面没有可用数据，显示错误态；</li>
 *   <li>{@code staleError} —— 静默刷新失败。**保留旧数据**，只把「数据可能过期」的
 *       提示交给头部状态栏。大屏上把已经渲染好的数据换成错误页是最差的选择。</li>
 * </ul>
 *
 * <p><b>趋势为什么单独拉</b>：切换 7 天 / 30 天只影响趋势带，没必要连带把
 * 经营、库存、采购三个聚合重拉一遍。
 */
export function useScreenData() {
  const business = ref<BusinessData | null>(null);
  const inventory = ref<InventoryData | null>(null);
  const purchase = ref<PurchaseData | null>(null);
  const trend = ref<TrendData>(emptyTrend());

  const loading = ref(true);
  const refreshing = ref(false);
  const error = ref('');
  const staleError = ref('');
  const updatedAt = ref<Date | null>(null);
  const range = ref<ScreenRange>('7d');

  let timer: number | undefined;

  async function fetchTrend(target: ScreenRange) {
    const res = await screenApi.getTrendData(target);
    trend.value = (res.data as TrendData) ?? emptyTrend(target);
  }

  /** 首屏 / 手动刷新：四个接口并发，任一失败不影响其余。 */
  async function loadAll() {
    const results = await Promise.allSettled([
      screenApi.getBusinessData(),
      screenApi.getInventoryData(),
      screenApi.getPurchaseData(),
      screenApi.getTrendData(range.value),
    ]);

    const [biz, inv, pur, trd] = results;
    if (biz.status === 'fulfilled') {
      business.value = (biz.value.data as BusinessData) ?? null;
    }
    if (inv.status === 'fulfilled') {
      inventory.value = (inv.value.data as InventoryData) ?? null;
    }
    if (pur.status === 'fulfilled') {
      purchase.value = (pur.value.data as PurchaseData) ?? null;
    }
    if (trd.status === 'fulfilled') {
      trend.value = (trd.value.data as TrendData) ?? emptyTrend(range.value);
    }

    const failed = results.filter((r) => r.status === 'rejected').length;
    if (failed === results.length) {
      // 全挂：没有可用数据，交给错误态
      const first = results[0] as PromiseRejectedResult;
      error.value = String(first.reason?.msg ?? first.reason?.message ?? '数据加载失败');
      return false;
    }
    error.value = '';
    staleError.value = failed > 0 ? `${failed} 个接口加载失败` : '';
    updatedAt.value = new Date();
    return true;
  }

  /** 首次加载：显示 loading。 */
  async function bootstrap() {
    loading.value = true;
    try {
      await loadAll();
    } finally {
      loading.value = false;
    }
  }

  /** 静默刷新：不显示 loading，失败也只记 staleError。 */
  async function refresh() {
    if (refreshing.value) {
      return;
    }
    refreshing.value = true;
    try {
      await loadAll();
    } finally {
      refreshing.value = false;
    }
  }

  /** 切换趋势区间：只重拉趋势。 */
  async function setRange(next: ScreenRange) {
    if (next === range.value) {
      return;
    }
    range.value = next;
    try {
      await fetchTrend(next);
      updatedAt.value = new Date();
      staleError.value = '';
    } catch (e: any) {
      staleError.value = String(e?.msg ?? '趋势数据加载失败');
    }
  }

  /**
   * 页面不可见时跳过刷新。
   *
   * <p>大屏常年开着，切到后台标签页还在每 30 秒打接口纯属浪费；
   * 而重新可见时立刻补一次，避免用户切回来看到的是十几分钟前的数据。
   */
  function onVisibilityChange() {
    if (!document.hidden) {
      refresh();
    }
  }

  onMounted(() => {
    bootstrap();
    timer = window.setInterval(() => {
      if (!document.hidden) {
        refresh();
      }
    }, REFRESH_INTERVAL);
    document.addEventListener('visibilitychange', onVisibilityChange);
  });

  onBeforeUnmount(() => {
    if (timer !== undefined) {
      window.clearInterval(timer);
      timer = undefined;
    }
    document.removeEventListener('visibilitychange', onVisibilityChange);
  });

  return {
    business,
    inventory,
    purchase,
    trend,
    loading,
    refreshing,
    error,
    staleError,
    updatedAt,
    range,
    refresh,
    setRange,
  };
}
