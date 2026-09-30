import {useUserStore} from '/@/store/modules/system/user';
import {SCM_FINANCE_PERMISSION} from '/@/constants/business/scm/finance-const';

interface WebPermPoint {
    webPerms?: string;
}

export function useFinancePermission() {
    const userStore = useUserStore();

    function hasPermission(code: string): boolean {
        if (userStore.administratorFlag) return true;
        const points = userStore.getPointList as WebPermPoint[] | undefined;
        return points?.some((point) => point.webPerms === code) ?? false;
    }

    return {
        hasPermission,
        canExport: () => hasPermission(SCM_FINANCE_PERMISSION.EXPORT),
    };
}
