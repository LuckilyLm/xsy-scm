import {XsyEnum} from '/@/types/xsy-enum';

/**
 * job 常量
 * @type {{CRON: {value: number, desc: string}, FIXED_DELAY: {value: number, desc: string}}}
 */
export const TRIGGER_TYPE_ENUM: XsyEnum<string> = {
  CRON: {
    value: 'cron',
    desc: 'cron表达式',
  },
  FIXED_DELAY: {
    value: 'fixed_delay',
    desc: '固定间隔',
  },
};

export default {
  TRIGGER_TYPE_ENUM,
};
