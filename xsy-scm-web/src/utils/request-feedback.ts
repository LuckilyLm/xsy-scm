import {message} from 'ant-design-vue';
import {errorText} from './error-text';
import {h, type VNodeChild} from 'vue';

/**
 * 请求层和页面共用同一条提示，避免同一次失败重复弹出。
 * @param text 请求失败原因，空文本由通用错误文案兜底。
 * @param action 可选的恢复入口；省略时只显示错误原因。
 */
export function notifyRequestError(text: string, action?: () => VNodeChild): void {
  const content = action ? h('span', {class: 'xsy-request-message'}, [h('span', errorText(text)), h({render: action})]) : errorText(text);
  void message.error({key: 'xsy-request-error', content});
}

/** 权限失败使用独立空态；空文本及普通请求错误返回false。 */
export function isPermissionDenied(text: string): boolean {
  return /无权|没有权限|权限不足|暂无权限|不具备.*权限|禁止访问/i.test(text);
}
