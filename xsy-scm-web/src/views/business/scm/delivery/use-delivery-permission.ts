/** 配送表格根据当前用户权限显示列；没有金额权限时整列隐藏，与服务端的数据权限保持一致。 */
import {computed} from 'vue';
import {useUserStore} from '/@/store/modules/system/user';

/** 配送订单金额可见权；必须与菜单种子里的 `t_menu.perms` 逐字一致。 */
export const DELIVERY_AMOUNT_PERM = 'scm:delivery:amount:query';

/** 配送功能权限码与后端和菜单配置保持一致。新增权限时先更新权限种子，前端按钮统一引用常量，避免拼错码。 */
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
  /** L3 订单签收：权限点在订单维度，不在线路维度（司机只持这一条，不持发车）。 */
  ORDER_SIGN: 'scm:delivery:order:sign',
  /** ADM-10 排线建议：生成（只算建议）与应用（写回顺序）分开，避免「看一眼」顺手改线路。 */
  PLAN_QUERY: 'scm:delivery:plan:query',
  PLAN_PROPOSE: 'scm:delivery:plan:propose',
  PLAN_APPLY: 'scm:delivery:plan:apply',
  /** ADM-10 轨迹：查询（调度）与上报（司机 / 设备）分权。 */
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
