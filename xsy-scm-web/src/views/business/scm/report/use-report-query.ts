/** 每个报表页签独立保存加载与错误状态；只接受最后一次查询结果，避免旧响应覆盖新筛选结果。 */
import {type Ref} from 'vue';
import type {RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {TabView} from './report-model';
import {reportError} from './report-errors';

/**
 * 生成一个 Tab 的分页查询函数。
 *
 * `view` 必须是 `reactive(createTabView(...))` 的结果：装载器就地写 `rows` / `total` /
 * `loading` / `error`，因此这四个字段只属于这一个 Tab（切 Tab 不会串状态）。
 *
 * @param buildQuery 由调用方装配请求体（共享筛选 + 该 Tab 的分页）
 * @param fetch      对应的分页接口
 */
export function createTabLoader<T, Q extends object>(
  view: TabView<T>,
  buildQuery: () => Q,
  fetch: (query: Q, options?: RequestOptions) => Promise<ScmResponse<ScmPage<T>>>
): () => Promise<void> {
  let request = 0;
  return async function load(): Promise<void> {
    const id = ++request;
    view.loading = true;
    view.error = '';
    try {
      // 失败原因写在本 Tab 内联 Alert 上，不再让全局 toast 重复说一遍
      const response = await fetch(buildQuery(), {suppressGlobalErrorMessage: true});
      if (id !== request) {
        return;
      }
      view.rows = response.data.list;
      view.total = response.data.total;
    } catch (e) {
      if (id === request) {
        view.error = reportError(e);
      }
    } finally {
      if (id === request) {
        view.loading = false;
      }
    }
  };
}

/**
 * 生成一个非分页请求（KPI 卡 / TOP 图 / 趋势）的装载器。
 *
 * 失败写进调用方给的 `error` ref：图表拉不到数时必须看得见原因，
 * 静默留白会被读成「这个区间没有数据」。
 */
export function createGuardedLoader<T>(
  task: (options?: RequestOptions) => Promise<ScmResponse<T>>,
  apply: (data: T) => void,
  error: Ref<string>
): () => Promise<void> {
  let request = 0;
  return async function load(): Promise<void> {
    const id = ++request;
    try {
      // 同上：图表拉不到数的原因写在内联 Alert 上，不重复弹全局提示
      const response = await task({suppressGlobalErrorMessage: true});
      if (id === request) {
        apply(response.data);
      }
    } catch (e) {
      if (id === request) {
        error.value = reportError(e);
      }
    }
  };
}
