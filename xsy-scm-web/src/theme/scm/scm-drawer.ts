/** 业务抽屉宽度分档，窄屏由公共响应式样式限制。 */
export const SCM_DRAWER_WIDTH = {
  /** 600：简单配置 / 简单维护，以及单列只读详情 */
  s: 600,
  /** 780：中型主数据 / 常规编辑表单 */
  m: 780,
  /** 940：复杂业务编辑表单 */
  l: 940,
  /** 1120：复杂详情 / 内嵌宽表 / 多明细业务 */
  xl: 1120,
  /** 1440：受限特殊档，准入条件见 scmDrawerWidth */
  workspace: 1440,
} as const;

export type ScmDrawerSize = keyof typeof SCM_DRAWER_WIDTH;

/**
 * workspace 是受限特殊档，不是普通第五档。
 * 只用于地图工作台、分拣 / 称重工作台、报表下钻、超宽业务数据阅读、多面板业务工作台。
 * 普通表单和详情用 s/m/l/xl。长期规则见 `CONTRIBUTING.md` §SCM 界面规范。
 */
export function scmDrawerWidth(size: ScmDrawerSize): number {
  return SCM_DRAWER_WIDTH[size];
}
