import {watchEffect} from 'vue';
import {theme} from 'ant-design-vue';

const {useToken} = theme;

/** 把 antd 主题 token 落成全局 `--scm-*` 变量；在 App 根组件调用一次即可。 */
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
    // antd-vue 未开启 cssVar，--ant-color-* 不存在，需显式落变量
    style.setProperty('--scm-success', current.colorSuccess);
    style.setProperty('--scm-warning', current.colorWarning);
    style.setProperty('--scm-fill', current.colorFillTertiary);
    style.setProperty('--scm-text', current.colorText);
    style.setProperty('--scm-text-secondary', current.colorTextSecondary);
    style.setProperty('--scm-text-disabled', current.colorTextDisabled);
    style.setProperty('--scm-border', current.colorBorderSecondary);
    style.setProperty('--scm-bg-container', current.colorBgContainer);
  });
}
