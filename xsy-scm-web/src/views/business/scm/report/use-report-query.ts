/**
 * 报表页的查询装载器（新增文件）。
 *
 * 五个报表页都有同一件事要做：**同一个 Tab 的慢响应不能盖掉新响应**。
 * 报表比普通列表更容易踩这个坑——用户会连续改日期、快速切 Tab，
 * 而一次概览刷新同时发 3 个请求。所以把竞态令牌收在一处，而不是每个页面各写一份
 * `let requestId = 0` + 三处 `if (id === requestId)`。
 *
 * 令牌放在闭包里而不是全局：一个页面的多个 Tab 与多个图表并发在飞时，
 * 共享一个计数器会互相把对方的结果判成「过期」，表现为图有数、表格空白。
 */
import {type Ref} from 'vue';
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
    fetch: (query: Q) => Promise<ScmResponse<ScmPage<T>>>
): () => Promise<void> {
    let request = 0;
    return async function load(): Promise<void> {
        const id = ++request;
        view.loading = true;
        view.error = '';
        try {
            const response = await fetch(buildQuery());
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
    task: () => Promise<ScmResponse<T>>,
    apply: (data: T) => void,
    error: Ref<string>
): () => Promise<void> {
    let request = 0;
    return async function load(): Promise<void> {
        const id = ++request;
        try {
            const response = await task();
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
