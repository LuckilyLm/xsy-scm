/** 报表列权限与按钮权限读取同一份用户权限。缺少成本权限时直接隐藏成本列，不能只隐藏按钮。 */
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
