/**
 * SCM Drawer 宽度分级。
 *
 * 不按「全部统一成一个宽度」处理：简单配置、中型主数据、含明细的复杂单据所需空间差异很大。
 * 超出视口的部分由 `theme/scm/responsive.less` 的 `max-width: 96vw` 兜住 —— 业务页面
 * 不要再自己写 `min(NNNpx, 96vw)`，那会把视口兜底和宽度分级混成两件事。
 *
 * 业务页面一律通过 `scmDrawerWidth('s' | 'm' | 'l' | 'xl' | 'workspace')` 取值，
 * 不允许再出现硬编码宽度（契约测试 `test/scm-drawer-width-contract.test.mjs` 钉住这一点）。
 * 完整映射见 `docs/plan/active/frontend-ui-closeout-decisions.md` §C2。
 */
export const SCM_DRAWER_WIDTH = {
  /** 600：简单配置 / 简单维护，以及单列只读详情 */
  s: 600,
  /** 780：中型主数据 / 常规编辑表单 */
  m: 780,
  /** 940：复杂业务编辑表单 */
  l: 940,
  /** 1120：复杂详情 / 内嵌宽表 / 多明细业务 */
  xl: 1120,
  /** 1440：受限特殊档 —— **不是普通的第五档**，准入条件见 `scmDrawerWidth` 的注释 */
  workspace: 1440,
} as const;

export type ScmDrawerSize = keyof typeof SCM_DRAWER_WIDTH;

/**
 * 取 Drawer 宽度。
 *
 * `workspace` **不是普通 Drawer 的第五个宽度档**，而是一个受限语义：
 * 只有「横向空间本身就是业务内容」的场景才允许使用，且**仅限以下五类**：
 *
 * 1. 地图工作台
 * 2. 分拣 / 称重工作台
 * 3. 报表下钻
 * 4. 超宽业务数据阅读
 * 5. 多面板业务工作台
 *
 * **普通表单 / 编辑 / 配置 / 普通详情一律禁止使用 `workspace`** ——
 * 这类内容应该升到 `xl`（1120）或者拆分，而不是靠再放宽抽屉来容纳。
 *
 * 使用 `workspace` 的页面必须在代码注释里写明它属于上述哪一类；
 * 当前批准的 5 个用例见 `docs/plan/active/frontend-ui-closeout-decisions.md` §C2.3，
 * 新增用例需要先在该文件登记。
 */
export function scmDrawerWidth(size: ScmDrawerSize): number {
  return SCM_DRAWER_WIDTH[size];
}
