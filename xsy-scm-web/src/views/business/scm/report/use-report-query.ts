/** 每个页签独立保存加载和错误状态，只采用最后一次查询结果。 */
import {type Ref} from 'vue';
import type {RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {TabView} from './report-model';
import {reportError} from './report-errors';

/**
 * 生成一个标签页的分页查询函数。
 * view 必须是 reactive(createTabView(...))；buildQuery 负责组装请求体。
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
      // 失败原因写在本页签的 Alert 上，不再弹全局 toast
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

/** 生成非分页请求（KPI、图表）的装载器；失败写进 error ref，免得空白被当成没数据。 */
export function createGuardedLoader<T>(
  task: (options?: RequestOptions) => Promise<ScmResponse<T>>,
  apply: (data: T) => void,
  error: Ref<string>
): () => Promise<void> {
  let request = 0;
  return async function load(): Promise<void> {
    const id = ++request;
    try {
      // 失败原因写在内联 Alert 上，不弹全局提示
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
