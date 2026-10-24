import {watchEffect} from 'vue';
import {theme} from 'ant-design-vue';
import {useAppConfigStore} from '/@/store/modules/system/app-config';

const {useToken} = theme;

/**
 * 把 antd 主题 token 落成全局 `--scm-*` 变量；在 App 根组件调用一次即可。
 *
 * 中性色不直接用 antd 的纯灰：后台以「鲜蔬账台」为基调，纸面偏一点菜叶绿，
 * 让白卡片、表格和绿色主色处在同一色温里。暗色仍回到 antd token，避免自造一套暗色。
 */
export function useScmThemeVars(): void {
  const {token} = useToken();

  watchEffect(() => {
    const current = token.value;
    const style = document.documentElement.style;
    const dark = useAppConfigStore().$state.darkModeFlag;
    // 供 CSS 只调整「浅色底 + 同色字」这类无法用 token 表达的明暗差异
    document.documentElement.dataset.scmTheme = dark ? 'dark' : 'light';

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

    // 账台专用中性色：页底、发丝线、墨色、侧栏底色
    style.setProperty('--scm-page-bg', dark ? current.colorBgLayout : '#F2F5EE');
    style.setProperty('--scm-sider-bg', dark ? current.colorBgContainer : '#FBFCF8');
    style.setProperty('--scm-surface', dark ? current.colorBgContainer : '#FFFFFF');
    style.setProperty('--scm-line', dark ? current.colorBorderSecondary : '#E4E8DF');
    style.setProperty('--scm-line-strong', dark ? current.colorBorder : '#D3DACB');
    style.setProperty('--scm-ink', dark ? current.colorText : '#1D251E');
    style.setProperty('--scm-ink-soft', dark ? current.colorTextSecondary : '#68725F');
    // 选中行、当前菜单的淡色底跟随用户选的主题色，不写死绿色
    style.setProperty('--scm-accent-soft', current.colorPrimaryBg);
    style.setProperty('--scm-accent-line', current.colorPrimaryBorder);
  });
}
