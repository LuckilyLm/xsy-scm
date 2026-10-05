import {watchEffect} from 'vue';
import {theme} from 'ant-design-vue';

const {useToken} = theme;

/**
 * 把 antd 当前主题 token 落成全局 `--scm-*` 变量，供 `src/theme/scm/*.less` 使用。
 *
 * 只需在 App 根组件调用一次：主题色可切换、暗色模式可切换，变量随之更新，
 * 各业务组件不必再各自 useToken + 手写一份 CSSProperties。
 */
export function useScmThemeVars(): void {
  const {token} = useToken();

  watchEffect(() => {
    const current = token.value;
    const style = document.documentElement.style;
    style.setProperty('--scm-primary', current.colorPrimary);
    style.setProperty('--scm-primary-bg', current.colorPrimaryBg);
    style.setProperty('--scm-primary-border', current.colorPrimaryBorder);
    style.setProperty('--scm-primary-bg-hover', current.colorPrimaryBgHover);
    style.setProperty('--scm-error', current.colorError);
    style.setProperty('--scm-error-bg', current.colorErrorBg);
    style.setProperty('--scm-error-border', current.colorErrorBorder);
    style.setProperty('--scm-fill', current.colorFillTertiary);
    style.setProperty('--scm-text', current.colorText);
    style.setProperty('--scm-text-secondary', current.colorTextSecondary);
    style.setProperty('--scm-text-disabled', current.colorTextDisabled);
    style.setProperty('--scm-border', current.colorBorderSecondary);
    style.setProperty('--scm-bg-container', current.colorBgContainer);
  });
}
