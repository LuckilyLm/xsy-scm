/**
 * SCM Drawer 宽度分级。
 *
 * 不按「全部统一成一个宽度」处理：简单配置、中型主数据、含明细的复杂单据所需空间差异很大。
 * 超出视口的部分由 `theme/scm/responsive.less` 的 `max-width: 96vw` 兜住 —— 业务页面
 * 不要再自己写 `min(NNNpx, 96vw)`，那会把视口兜底和宽度分级混成两件事。
 *
 * 业务页面一律通过 `scmDrawerWidth('s' | 'm' | 'l' | 'xl' | 'workspace')` 取值，
 * 不允许再出现硬编码宽度（契约测试 `test/scm-drawer-width-contract.test.mjs` 钉住这一点）。
 * 长期规则见 `docs/architecture/scm-ui-guidelines.md` §5。
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
  /** 1440：受限特殊档 —— <b>不是普通的第五档</b>，准入条件见 `scmDrawerWidth` 的注释 */
  workspace: 1440,
} as const;

export type ScmDrawerSize = keyof typeof SCM_DRAWER_WIDTH;

/**
 * 取 Drawer 宽度。
 *
 * `workspace` 是<b>受限特殊档</b>，不是普通 Drawer 的第五个宽度档，<b>仅限五类</b>：
 * 地图工作台 / 分拣 / 称重工作台 / 报表下钻 / 超宽业务数据阅读 / 多面板业务工作台。
 * 普通表单、编辑、配置与普通详情一律禁止 —— 这类内容应升到 `xl`（1120）或拆分。
 *
 * 白名单由 `test/scm-drawer-width-contract.test.mjs` 直接钉住；
 * 新增用例需先更新 UI 规范并同步契约。
 */
export function scmDrawerWidth(size: ScmDrawerSize): number {
  return SCM_DRAWER_WIDTH[size];
}
