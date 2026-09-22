/**
 * 首页业务待办接口（Wave 4）。
 *
 * 与后端 `ScmTodoController` 一一对应，**只有一个只读端点**：
 * `GET /scm/dashboard/todo` 返回当前登录人「可见」的待办卡片清单。
 *
 * 这里刻意是 **Pull（读时现算）而非 Push**：待办不是一种会被「解决」的记录，
 * 只是各业务域「待处理量」的当前快照。卡片是否出现、数字是多少全由后端按权限决定，
 * 前端不重复判断权限，也不缓存计数 —— 刷新即最新。
 */
import {getRequest} from '/@/lib/axios';
import type {ScmResponse} from '/@/types/business/scm/customer';

/** 一张待办卡片：key 稳定标识，route 为已带查询条件的目标列表页路径。 */
export interface ScmTodo {
    key: string;
    label: string;
    count: number;
    route: string;
}

export const scmDashboardApi = {
    /** 当前人的业务待办卡片（无权限的卡片后端直接省略，不返回 0）。 */
    todo: () => getRequest('/scm/dashboard/todo', {}) as unknown as Promise<ScmResponse<ScmTodo[]>>,
};

export default scmDashboardApi;
