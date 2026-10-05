/**
 * SCM 统一状态视觉语义。
 *
 * 各业务域的枚举值仍由 `constants/business/scm/*-const.ts` 维护，这里只负责
 * 把语义档位收敛成同一套 antd 标签色，避免同一状态在不同模块呈现不同颜色。
 */

export type ScmStatusTone = 'success' | 'processing' | 'warning' | 'error' | 'neutral';

/** 语义档位 → antd 预设标签色 */
export const SCM_STATUS_TONE_COLOR: Record<ScmStatusTone, string> = {
  // 上架 / 启用 / 已完成 / 已结清 / 已收货
  success: 'green',
  // 已提交 / 处理中 / 配送中 / 审核中
  processing: 'blue',
  // 草稿 / 待审核 / 待收货 / 部分完成 / 预警
  warning: 'orange',
  // 拒绝 / 黑名单 / 严重预警 / 失败 / 逾期
  error: 'red',
  // 下架 / 停用 / 取消 / 已作废
  neutral: 'default',
};

/**
 * 归一化历史遗留的颜色写法。
 *
 * 部分业务常量表沿用了 `gray`，但 antd 会把它当作自定义色值渲染成实心灰底白字，
 * 与"失效/停用"应有的弱化观感不符，统一收敛到 `default`。
 */
export function scmStatusColor(color?: string | null): string {
  if (!color) {
    return SCM_STATUS_TONE_COLOR.neutral;
  }
  if (color === 'gray' || color === 'grey') {
    return SCM_STATUS_TONE_COLOR.neutral;
  }
  return color;
}
