/**
 * 报表中心的**列级**权限判定（新增文件）。
 *
 * 为什么不能只用 `v-privilege`：指令按单个码删 DOM 节点，够管按钮，但
 * 「无成本权限时整列消失」是 `columns` 数组的计算逻辑，必须能在 script 里问一句
 * 「当前调用者有没有这个码」。
 *
 * 因此这里**读的就是指令读的同一份数据**：`useUserStore().getPointList` 里的 `webPerms`，
 * 并且同样先看 `administratorFlag`（超级管理员绕过校验）。不新建 store、不缓存副本，
 * 否则会出现「按钮没了但列还在」这类两套真相分叉。
 */
import {computed} from 'vue';
import {useUserStore} from '/@/store/modules/system/user';
import {SCM_REPORT_PERMISSION} from '/@/constants/business/scm/report-const';

/** `t_menu` 功能点在 store 里的最小形状（指令判定的就是 `webPerms` 这一个字段）。 */
interface WebPermPoint {
    webPerms?: string;
}

export function useReportPermission() {
    const userStore = useUserStore();

    function hasPerm(code: string): boolean {
        if (userStore.administratorFlag) {
            return true;
        }
        const pointsList = userStore.getPointList as WebPermPoint[] | undefined;
        if (!pointsList) {
            return false;
        }
        return pointsList.some((point) => point.webPerms === code);
    }

    return {
        hasPerm,
        /** 成本列 / 成本 Tab 的可见性。后端也会把无权限的成本字段置 null。 */
        canViewCost: computed(() => hasPerm(SCM_REPORT_PERMISSION.COST_QUERY)),
        /**
         * 导出按钮的可见性。
         *
         * 这里只看 `scm:report:export` 一个码：`v-privilege` 只吃一个码，
         * 「query AND export」的合取由后端裁决（有 export 无 query 同样导不出来）。
         */
        canExport: computed(() => hasPerm(SCM_REPORT_PERMISSION.EXPORT)),
    };
}
