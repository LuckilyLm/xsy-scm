/*
 * 通知
 *
 */

import {XsyEnum} from '/@/types/xsy-enum';

export const NOTICE_VISIBLE_RANGE_DATA_TYPE_ENUM: XsyEnum<number> = {
  EMPLOYEE: {
    value: 1,
    desc: '员工',
  },
  DEPARTMENT: {
    value: 2,
    desc: '部门',
  },
};

export const NOTICE_TYPE_ENUM: XsyEnum<number> = {
  ANNOUNCEMENT: {
    value: 1,
    desc: '公告',
  },
  NOTICE: {
    value: 2,
    desc: '通知',
  },
};

export default {
  NOTICE_VISIBLE_RANGE_DATA_TYPE_ENUM,
  NOTICE_TYPE_ENUM,
};
