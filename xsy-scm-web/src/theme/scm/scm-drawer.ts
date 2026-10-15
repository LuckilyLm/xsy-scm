/** 业务抽屉按复杂度选择宽度，窄屏由公共响应式样式限制。长期规则见 `CONTRIBUTING.md` §SCM 界面规范。 */
export const SCM_DRAWER_WIDTH = {
  /** 600：简单配置 / 简单维护，以及单列只读详情 */
  s: 600,
  /** 780：中型主数据 / 常规编辑表单 */
  m: 780,
  /** 940：复杂业务编辑表单 */
  l: 940,
  /** 1120：复杂详情 / 内嵌宽表 / 多明细业务 */
  xl: 1120,
  /** 1440：受限特殊档 —— 不是普通的第五档，准入条件见 `scmDrawerWidth` 的注释 */
  workspace: 1440,
} as const;

export type ScmDrawerSize = keyof typeof SCM_DRAWER_WIDTH;

/**
 * 取 Drawer 宽度。
 *
 * `workspace` 是受限特殊档，不是普通 Drawer 的第五个宽度档，仅限五类：
 * 地图工作台 / 分拣 / 称重工作台 / 报表下钻 / 超宽业务数据阅读 / 多面板业务工作台。
 * 普通表单、编辑、配置与普通详情一律禁止 —— 这类内容应升到 `xl`（1120）或拆分。
 *
 * 白名单由 `test/scm-drawer-width-contract.test.mjs` 直接钉住；
 * 新增用例需先更新 UI 规范并同步契约。
 */
export function scmDrawerWidth(size: ScmDrawerSize): number {
  return SCM_DRAWER_WIDTH[size];
}
