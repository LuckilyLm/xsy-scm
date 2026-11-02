/** 未保存修改的纯函数判断，便于单测；抽屉的确认弹窗在 scm-unsaved-guard.ts。 */
export function formSnapshot(value: unknown): string {
  return JSON.stringify(value);
}

/** baseline 为空表示尚未加载完成，不拦截关闭。 */
export function formChanged(current: unknown, baseline?: string): boolean {
  return baseline != null && baseline !== '' && formSnapshot(current) !== baseline;
}
