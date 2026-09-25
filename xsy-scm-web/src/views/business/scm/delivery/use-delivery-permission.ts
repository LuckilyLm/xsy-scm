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

/**
 * 配送域功能点的**唯一来源**（与 V43 / V55 / V64 的 `t_menu.web_perms` 逐字一致）。
 *
 * 模板里的 `v-privilege` 只能引用这里的值：写错一个字符的表现是「按钮显示出来了，点下去 403」，
 * 而 403 文案是底座统一的「无权限」，看不出是拼错，排查成本远高于这里多写一层常量。
 * 新增取值必须先在 V64 同族的迁移里种下菜单功能点，再来加这一行。
 */
export const DELIVERY_PERM = {
    ROUTE_QUERY: 'scm:delivery:route:query',
    ROUTE_ADD: 'scm:delivery:route:add',
    ROUTE_UPDATE: 'scm:delivery:route:update',
    ROUTE_PLAN: 'scm:delivery:route:plan',
    ROUTE_CANCEL: 'scm:delivery:route:cancel',
    ROUTE_PRINT: 'scm:delivery:route:print',
    /** L3 发车：会真实出库，故与「确认规划」分权，能排线的人不必然是能发货的人。 */
    ROUTE_DISPATCH: 'scm:delivery:route:dispatch',
    /** L3 完成线路：DISPATCHED → COMPLETED，硬前置是全部活动订单已签收或登记异常。 */
    ROUTE_COMPLETE: 'scm:delivery:route:complete',
    /** L3 订单签收：权限点在**订单**维度，不在线路维度（司机只持这一条，不持发车）。 */
    ORDER_SIGN: 'scm:delivery:order:sign',
    /** 显式放宽到全部司机范围；缺它的人只看得见排给自己的线路。 */
    SCOPE_ALL_QUERY: 'scm:delivery:scope:all:query',
    AMOUNT_QUERY: DELIVERY_AMOUNT_PERM,
} as const;

/** 底座功能点：详情页共用统一操作日志入口，不属于配送域，但同样是模板里出现的码。 */
export const OPERATE_LOG_PERM = 'support:operateLog:query';

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
        /**
         * L3 三个动作的功能权限。服务端才是权威（发车还要再过库存域的仓库范围守卫），
         * 这里只决定按钮形态：缺权的按钮不出现，避免给用户「能点但必然失败」的入口。
         */
        canDispatch: computed(() => hasPerm(DELIVERY_PERM.ROUTE_DISPATCH)),
        canSign: computed(() => hasPerm(DELIVERY_PERM.ORDER_SIGN)),
        canComplete: computed(() => hasPerm(DELIVERY_PERM.ROUTE_COMPLETE)),
    };
}
