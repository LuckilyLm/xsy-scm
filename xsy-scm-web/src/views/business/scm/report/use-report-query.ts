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
      // 仅处理当前请求的失败，由共享反馈弹出一次提示。
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

/** 生成非分页请求装载器；保留失败状态，供共享反馈区分权限失败和空结果。 */
export function createGuardedLoader<T>(
  task: (options?: RequestOptions) => Promise<ScmResponse<T>>,
  apply: (data: T) => void,
  error: Ref<string>
): () => Promise<void> {
  let request = 0;
  return async function load(): Promise<void> {
    const id = ++request;
    try {
      // 页面统一反馈，避免请求层和页面各弹一次相同提示。
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
