export interface ScmActionItem {
  key: string;
  label: string;
  /** 危险动作：菜单项以红色呈现，二次确认由调用方处理 */
  danger?: boolean;
  disabled?: boolean;
  /** 无操作权限时保留入口并禁用；业务状态仍由hidden决定。 */
  authorized?: boolean;
  disabledReason?: string;
  /** 按单据状态动态决定是否收纳进"更多" */
  hidden?: boolean;
}

/**
 * 业务状态决定是否显示，权限只决定是否禁用；不修改调用方的动作定义。
 * @param actions 页面按当前业务状态提供的动作，空数组表示没有操作。
 * @param authorized 整组是否有权限；省略时分别采用每个动作的权限结果。
 */
export function resolveActions(actions: readonly ScmActionItem[], authorized = true): ScmActionItem[] {
  return actions
    .filter((action) => !action.hidden)
    .map((action) => {
      const allowed = authorized && action.authorized !== false;
      return {
        ...action,
        authorized: allowed,
        disabled: action.disabled || !allowed,
        disabledReason: allowed ? action.disabledReason : '暂无操作权限，请联系管理员',
      };
    });
}
