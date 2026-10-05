/**
 * SCM Drawer 宽度分级。
 *
 * 不按"全部统一成一个宽度"处理：简单配置、中型主数据、含明细的复杂单据所需空间差异很大。
 * 超出视口的部分由 `theme/scm/responsive.less` 的 `max-width: 96vw` 兜住。
 */
export const SCM_DRAWER_WIDTH = {
  /** 560~640：协议价、客户类型、简单字典、简单线路配置 */
  s: 600,
  /** 720~820：供应商、仓库、司机/车辆、简单库存业务表单 */
  m: 780,
  /** 900~960：商品、客户、采购单、销售订单等含多行明细的复杂单据 */
  l: 940,
} as const;

export type ScmDrawerSize = keyof typeof SCM_DRAWER_WIDTH;

export function scmDrawerWidth(size: ScmDrawerSize): number {
  return SCM_DRAWER_WIDTH[size];
}
