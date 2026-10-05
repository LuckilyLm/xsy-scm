export interface ScmActionItem {
  key: string;
  label: string;
  /** 危险动作：菜单项以红色呈现，二次确认由调用方处理 */
  danger?: boolean;
  disabled?: boolean;
  /** 按单据状态动态决定是否收纳进"更多" */
  hidden?: boolean;
}
