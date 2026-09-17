import scmOrder from './business/scm/order-const';
import * as scmPricing from './business/scm/pricing-const';
import scmPurchase from './business/scm/purchase-const';
/*
 * 所有常量入口
 *
 * @Author:    1024创新实验室-主任：卓大
 * @Date:      2022-09-06 19:58:28
 * @Wechat:    zhuda1024
 * @Email:     lab1024@163.com
 * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
 */
import menu from './system/menu-const';
import goods from './business/erp/goods-const';
import category from './business/erp/category-const';
import { LOGIN_DEVICE_ENUM } from './system/login-device-const';
import { FLAG_NUMBER_ENUM, GENDER_ENUM, USER_TYPE_ENUM } from './common-const';
import { LAYOUT_ENUM } from './layout-const';
import file from './support/file-const';
import notice from './business/oa/notice-const';
import loginLog from './support/login-log-const';
import enterprise from './business/oa/enterprise-const';
import message from './business/message/message-const';
import codeGeneratorConst from './support/code-generator-const';
import changeLogConst from './support/change-log-const';
import jobConst from './support/job-const';
import dictConst from './support/dict-const';
// SCM 业务枚举（W2 起）：注册进 SmartEnum 插件后，SmartEnumSelect / $smartEnumPlugin 才能取到值
import scmCustomer from './business/scm/customer-const';
import scmSupplier from './business/scm/supplier-const';
import type { SmartEnumWrapper } from '/@/types/smart-enum';

const constantsInfo = {
  FLAG_NUMBER_ENUM,
  LOGIN_DEVICE_ENUM,
  GENDER_ENUM,
  USER_TYPE_ENUM,
  LAYOUT_ENUM,
  ...loginLog,
  ...menu,
  ...goods,
  ...category,
  ...file,
  ...notice,
  ...enterprise,
  ...message,
  ...codeGeneratorConst,
  ...changeLogConst,
  ...jobConst,
  ...dictConst,
  ...scmCustomer,
  ...scmSupplier,
  ...scmPricing,
  ...scmOrder,
  ...scmPurchase,
};

/*
 * 显式声明导出类型。
 *
 * 上游 SmartAdmin 的 `DICT_CODE_ENUM` 形状是 `{ GOODS_PLACE: string }`，不是 `SmartEnumItem`，
 * 因此这个对象字面量无法直接赋给 `SmartEnumWrapper<unknown>`（TS2769）。
 * 枚举注册表在类型上本来就是「异构枚举袋」，参数化为 `unknown` 才是诚实的表达；
 * 这里用一次显式断言把上游的形状缺陷挡在模块边界上，调用方（main.ts 的插件注册）不再受影响。
 */
export default constantsInfo as unknown as SmartEnumWrapper<unknown>;
