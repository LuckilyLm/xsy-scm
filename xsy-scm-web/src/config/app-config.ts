/*
 * 应用默认配置
 *
 */
import type {AppConfig} from '/@/types/config';

export const appDefaultConfig: Omit<AppConfig, 'fullScreenFlag'> = {
    // 默认配置版本号：只要本文件的默认值发生变更，就把它 +1，
    // 已有浏览器里 localStorage 缓存的旧配置会被自动迁移到新默认值，
    // 否则老用户永远停留在旧样式上（详见 store/modules/system/app-config.ts）。
    configVersion: 2,
    // i18n 语言选择
    language: 'zh_CN',
    // 布局: side 或者 side-expand 或者 top
    layout: 'side',
    // 侧边菜单宽度 ， 默认为200px
    sideMenuWidth: 200,
    //标签页位置
    pageTagLocation: 'center',
    // 夜间模式
    darkModeFlag: false,
    // 菜单主题
    sideMenuTheme: 'light',
    // 主题颜色索引
    colorIndex: 2,
    // 顶部菜单页面宽度
    pageWidth: '99%',
    // 圆角
    borderRadius: 6,
    // 菜单展开模式
    menuSingleExpandFlag: true,
    // 标签页
    pageTagFlag: true,
    // 标签页样式: default、 antd、chrome
    pageTagStyle: 'default',
    // 面包屑
    breadCrumbFlag: true,
    // 页脚
    footerFlag: true,
    // 帮助文档
    helpDocFlag: true,
    // 帮助文档默认展开
    helpDocExpandFlag: false,
    // 水印
    watermarkFlag: true,
    // 网站名称
    websiteName: '鲜蔬源智链',
    // 主题颜色
    primaryColor: '#1677ff',
    // 紧凑
    compactFlag: true,
};
