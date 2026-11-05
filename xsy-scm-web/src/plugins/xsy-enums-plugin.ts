/*
 * 枚举插件
 * 此插件为  自创的插件
 *
 */
import _ from 'lodash';
import {App} from 'vue';
import {FLAG_NUMBER_ENUM} from '/@/constants/common-const';
import {XsyEnum, XsyEnumWrapper, XsyEnumItem, XsyEnumPlugin} from '/@/types/xsy-enum';

export default {
  install: <T>(app: App, xsyEnumWrapper: XsyEnumWrapper<T>): void => {
    const xsyEnumPlugin = {} as XsyEnumPlugin;
    /**
     * 根据枚举值获取描述
     * @param {*} constantName 枚举名
     * @param {*} value          枚举值
     * @returns
     */
    xsyEnumPlugin.getDescByValue = function (constantName: string, value: unknown): string {
      if (!xsyEnumWrapper || !Object.prototype.hasOwnProperty.call(xsyEnumWrapper, constantName)) {
        console.error('无法找到变量名称：' + constantName + '，请检查 /constants/index.js 文件中是否引入此变量！');
        return '';
      }
      // boolean类型需要做特殊处理
      if (constantName === 'FLAG_NUMBER_ENUM' && !_.isUndefined(value) && typeof value === 'boolean') {
        value = value ? FLAG_NUMBER_ENUM.TRUE.value : FLAG_NUMBER_ENUM.FALSE.value;
      }

      let xsyEnum: XsyEnum<T> = xsyEnumWrapper[constantName];
      for (let item in xsyEnum) {
        if (xsyEnum[item].value === value) {
          return xsyEnum[item].desc;
        }
      }
      return '';
    };
    /**
     * 根据枚举名获取对应的描述键值对[{value:desc}]
     * @param {*} constantName 枚举名
     * @returns
     */
    xsyEnumPlugin.getValueDescList = function (constantName: string): XsyEnumItem<unknown>[] {
      if (!Object.prototype.hasOwnProperty.call(xsyEnumWrapper, constantName)) {
        console.error('无法找到变量名称：' + constantName + '，请检查 /constants/index.js 文件中是否引入此变量！');
        return [];
      }
      const result: XsyEnumItem<unknown>[] = [];
      let targetXsyEnum = xsyEnumWrapper[constantName];
      for (let item in targetXsyEnum) {
        result.push(targetXsyEnum[item]);
      }
      return result;
    };

    /**
     * 根据枚举名获取对应的value描述键值对{value:desc}
     * @param {*} constantName 枚举名
     * @returns
     */
    xsyEnumPlugin.getValueDesc = function (constantName: string): {[key: string]: string} {
      if (!Object.prototype.hasOwnProperty.call(xsyEnumWrapper, constantName)) {
        console.error('无法找到变量名称：' + constantName + '，请检查 /constants/index.js 文件中是否引入此变量！');
        return {};
      }
      let xsyEnum = xsyEnumWrapper[constantName];
      let result = {} as {[key: string]: string};
      for (let item in xsyEnum) {
        let key: string = xsyEnum[item].value + '';
        result[key] = xsyEnum[item].desc;
      }
      return result;
    };

    app.config.globalProperties.$xsyEnumPlugin = xsyEnumPlugin;
    app.provide('xsyEnumPlugin', xsyEnumPlugin);
  },
};
