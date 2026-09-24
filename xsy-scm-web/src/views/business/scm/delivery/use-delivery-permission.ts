/**
 * 配送页的**列级**权限判定。
 *
 * 为什么不能只用 `v-privilege`：指令按单个码删 DOM 节点，够管按钮，但「无金额权限时整列消失」
 * 是 `columns` 数组的计算逻辑，必须能在 script 里问一句「当前调用者有没有这个码」。
 *
 * 服务端已经先一步把无权限的金额字段抹成 `null`（`DeliveryVisibility`），这里只决定要不要留这一列；
 * 读的是指令同一份数据（`useUserStore().getPointList` 的 `webPerms`），不另建副本，避免
 * 「按钮没了但列还在」这类两套真相分叉。
 */
import {computed} from 'vue';
import {useUserStore} from '/@/store/modules/system/user';

/** 配送订单金额可见权；必须与 V55 种下的 `t_menu.perms` 逐字一致。 */
export const DELIVERY_AMOUNT_PERM = 'scm:delivery:amount:query';

interface WebPermPoint {
    webPerms?: string;
}

export function useDeliveryPermission() {
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
        /** 线路 / 停靠点 / 订单金额列的可见性；缺权限时服务端也会返回 null。 */
        canViewAmount: computed(() => hasPerm(DELIVERY_AMOUNT_PERM)),
    };
}
