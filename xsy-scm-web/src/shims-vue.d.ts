/*
 * 枚举、图标、工具类
 *
 */
import {XsyEnumPlugin} from '/@/types/xsy-enum';
import * as lodash from 'lodash';

declare module '*.vue' {
  import {Component} from 'vue';
  const component: Component;
  export default component;
}

// 对vue进行类型补充说明
declare module '@vue/runtime-core' {
  interface ComponentCustomProperties {
    // 常量插件
    $xsyEnumPlugin: XsyEnumPlugin;
    // 常量图标
    $antIcons: Object;
    // lodash工具类
    $lodash: lodash;
  }
}
