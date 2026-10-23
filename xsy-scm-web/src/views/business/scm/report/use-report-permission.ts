/** 报表的列权限和按钮权限。没有成本权限时直接隐藏成本列，不只是隐藏按钮。 */
import {computed} from 'vue';
import {useUserStore} from '/@/store/modules/system/user';
import {SCM_REPORT_PERMISSION} from '/@/constants/business/scm/report-const';

/** 权限点在 store 里的最小形状，判定只看 webPerms。 */
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
    /** 导出按钮的可见性。只判 scm:report:export；有没有查询权限由后端判断。 */
    canExport: computed(() => hasPerm(SCM_REPORT_PERMISSION.EXPORT)),
  };
}
