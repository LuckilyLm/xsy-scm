/*
 * 应用默认配置
 *
 */
import type {AppConfig} from '/@/types/config';

export const appDefaultConfig: Omit<AppConfig, 'fullScreenFlag'> = {
    // 默认配置版本号：只要本文件的默认值发生变更，就把它 +1，
    // 已有浏览器里 localStorage 缓存的旧配置会被自动迁移到新默认值，
    // 否则老用户永远停留在旧样式上（详见 store/modules/system/app-config.ts）。
    // 8：仅把沿用旧默认品牌名的浏览器配置迁移到新名称，保留用户自定义名称。
    configVersion: 8,
    // i18n 语言选择
    language: 'zh_CN',
    // 布局: side 或者 side-expand 或者 top
    layout: 'side',
    // 侧栏为业务分组和二级菜单保留可读宽度，收起态仍为 64px。
    // 默认宽度变化时升级 configVersion；对应迁移只重置此项，保留用户的其他界面偏好。
    sideMenuWidth: 168,
    //标签页位置
    pageTagLocation: 'top',
    // 夜间模式
    darkModeFlag: false,
    // 菜单主题
    sideMenuTheme: 'light',
    // 主题颜色索引（themeColors 下标：7 = 极光绿 #52c41a）
    colorIndex: 7,
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
    // 页脚：默认关闭。页脚只放版权与备案号，占掉一行高度却每页都在，
    // 需要的人可以在「界面设置」里打开。
    footerFlag: false,
    // 帮助文档
    helpDocFlag: false,
    // 帮助文档默认展开
    helpDocExpandFlag: false,
    // 水印
    watermarkFlag: false,
    // 网站名称
    websiteName: '鲜蔬园智链',
    // 主题颜色
    primaryColor: '#1677ff',
    // 紧凑
    compactFlag: true,
};
