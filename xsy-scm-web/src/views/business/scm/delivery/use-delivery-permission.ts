/** 配送表格根据当前用户权限显示列；没有金额权限时整列隐藏，与服务端的数据权限保持一致。 */
import {computed} from 'vue';
import {useUserStore} from '/@/store/modules/system/user';

/** 配送订单金额可见权；必须与菜单种子里的 `t_menu.perms` 逐字一致。 */
export const DELIVERY_AMOUNT_PERM = 'scm:delivery:amount:query';

/** 配送权限码，与后端和菜单配置一致；按钮统一引用这里的常量。 */
export const DELIVERY_PERM = {
  ROUTE_QUERY: 'scm:delivery:route:query',
  ROUTE_ADD: 'scm:delivery:route:add',
  ROUTE_UPDATE: 'scm:delivery:route:update',
  ROUTE_PLAN: 'scm:delivery:route:plan',
  ROUTE_CANCEL: 'scm:delivery:route:cancel',
  ROUTE_PRINT: 'scm:delivery:route:print',
  /** 发车会真实出库，单独授权，能排线不等于能发货。 */
  ROUTE_DISPATCH: 'scm:delivery:route:dispatch',
  /** 完成线路：所有活动订单已签收或登记异常后才能完成。 */
  ROUTE_COMPLETE: 'scm:delivery:route:complete',
  /** 签收权限在订单维度，司机只持这一条。 */
  ORDER_SIGN: 'scm:delivery:order:sign',
  /** 生成建议和应用建议分开授权，避免顺手改线路。 */
  PLAN_QUERY: 'scm:delivery:plan:query',
  PLAN_PROPOSE: 'scm:delivery:plan:propose',
  PLAN_APPLY: 'scm:delivery:plan:apply',
  /** 可查看全部司机的线路；没有的人只能看到排给自己的线路。 */
  SCOPE_ALL_QUERY: 'scm:delivery:scope:all:query',
  AMOUNT_QUERY: DELIVERY_AMOUNT_PERM,
} as const;

/** 底座操作日志权限，详情页会用到。 */
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
    /** 金额列是否可见；没有权限时服务端也会返回 null。 */
    canViewAmount: computed(() => hasPerm(DELIVERY_AMOUNT_PERM)),
    /** 发车、签收、完成三个动作的权限；没有权限的按钮不显示，最终以后端校验为准。 */
    canDispatch: computed(() => hasPerm(DELIVERY_PERM.ROUTE_DISPATCH)),
    canSign: computed(() => hasPerm(DELIVERY_PERM.ORDER_SIGN)),
    canComplete: computed(() => hasPerm(DELIVERY_PERM.ROUTE_COMPLETE)),
  };
}
