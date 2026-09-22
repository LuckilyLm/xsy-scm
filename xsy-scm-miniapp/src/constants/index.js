/*
 * 所有常量入口
 *
 * 该对象被 main.js 注入 smart-enums 插件，供 $smartEnumPlugin.getDescByValue(...) 使用。
 * 新增枚举时必须在此登记，否则插件查不到。
 */
import { FLAG_NUMBER_ENUM, GENDER_ENUM, USER_TYPE_ENUM } from './common-const';
import loginDevice from './system/login-device-const';

export default {
  FLAG_NUMBER_ENUM,
  GENDER_ENUM,
  USER_TYPE_ENUM,
  ...loginDevice,
};
