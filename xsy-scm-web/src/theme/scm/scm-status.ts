/** SCM 状态色：各业务枚举共用同一套档位，避免同状态不同颜色。 */

export type ScmStatusTone = 'success' | 'processing' | 'warning' | 'error' | 'neutral';

/** 档位 → antd 标签色 */
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

/** gray / grey 统一收敛到 default，避免渲染成实心灰底。 */
export function scmStatusColor(color?: string | null): string {
  if (!color) {
    return SCM_STATUS_TONE_COLOR.neutral;
  }
  if (color === 'gray' || color === 'grey') {
    return SCM_STATUS_TONE_COLOR.neutral;
  }
  return color;
}
