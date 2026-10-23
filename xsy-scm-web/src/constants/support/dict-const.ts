/**
 * 字典key 编码 常量
 *
 * 该常量来自于 字典管理中的数据，写在该文件目的是为了统一引用，将来好修改
 *
 */

export const DICT_SPLIT = ',';

export const DICT_CODE_ENUM = {
  GOODS_PLACE: 'GOODS_PLACE',
};

export const DICT_DATA_STYLE_ENUM = {
  DEFAULT: {
    value: 'default',
    desc: '默认',
    color: 'colorText',
  },
  PRIMARY: {
    value: 'primary',
    desc: '主要',
    color: 'colorPrimary',
  },
  SUCCESS: {
    value: 'success',
    desc: '成功',
    color: 'colorSuccess',
  },
  INFO: {
    value: 'info',
    desc: '信息',
    color: 'colorInfo',
  },
  WARNING: {
    value: 'warning',
    desc: '警告',
    color: 'colorWarning',
  },
  DANGER: {
    value: 'danger',
    desc: '危险',
    color: 'colorError',
  },
};

export default {
  DICT_CODE_ENUM,
  DICT_DATA_STYLE_ENUM,
};
