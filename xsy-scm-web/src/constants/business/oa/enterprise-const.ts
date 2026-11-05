/*
 * 企业
 *
 */

import {XsyEnum} from '/@/types/xsy-enum';

export const ENTERPRISE_TYPE_ENUM: XsyEnum<number> = {
  NORMAL: {
    value: 1,
    desc: '有限企业',
  },
  FOREIGN: {
    value: 2,
    desc: '外资企业',
  },
};

export default {
  ENTERPRISE_TYPE_ENUM,
};
