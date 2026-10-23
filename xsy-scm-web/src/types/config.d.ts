/*
 * 应用默认配置
 *
 */
/**
 *  语言 i18n
 */
export type LanguageType = 'zh_CN' | 'en' | 'ru' | 'ja' | 'ko';

/**
 * 四种布局: 左侧、左侧展开、顶部、混合
 */
export type LayoutType = 'side' | 'side-expand';

/**
 * 主题： 亮色，暗色，夜色
 */
export type ThemeType = 'light' | 'dark';

/**
 * 应用信息配置
 */
export interface AppConfig {
  // 默认配置版本号：app-config.ts 默认值变更时 +1，用于让浏览器缓存的旧配置失效
  configVersion: number;
  // i18n 语言选择
  language: LanguageType;
  // 布局
  layout: string;
  // 主题
  sideMenuTheme: ThemeType;
  //标签页位置
  pageTagLocation: string;
  // 侧边菜单宽度 ， 默认为256px
  sideMenuWidth: number;
  // 主题颜色索引
  colorIndex: number;
  // 顶部菜单页面宽度
  pageWidth: string;
  // 圆角
  borderRadius: number;
  // 标签页
  pageTagFlag: boolean;
  // 标签页样式: default、 antd、naive
  pageTagStyle: string;
  // 面包屑
  breadCrumbFlag: boolean;
  // 页脚
  footerFlag: boolean;
  // 帮助文档
  helpDocFlag: boolean;
  // 帮助文档默认展开
  helpDocExpandFlag: boolean;
  // 水印
  watermarkFlag: boolean;
  // 网站名称
  websiteName: string;
  // 主题颜色
  primaryColor: string;
  // 紧凑
  compactFlag: boolean;
  // 夜间模式
  darkModeFlag: boolean;
  // 菜单展开模式（同时只展开一个一级菜单）
  menuSingleExpandFlag: boolean;
  // 全屏（运行时状态，不属于持久化配置）
  fullScreenFlag?: boolean;
}
