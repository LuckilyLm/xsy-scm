/*
 * 类别
 *
 */

import {XsyEnum} from '/@/types/xsy-enum';

export const CATEGORY_TYPE_ENUM: XsyEnum<number> = {
  GOODS: {
    value: 1,
    desc: '商品',
  },
  DEMO: {
    value: 2,
    desc: '演示分类',
  },
};

export default {
  CATEGORY_TYPE_ENUM,
};
