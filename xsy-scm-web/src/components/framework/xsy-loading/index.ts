/*
 * loading 组件
 *
 */
import {useSpinStore} from '/@/store/modules/system/spin';

export const XsyLoading = {
  show: () => {
    useSpinStore().show();
  },

  hide: () => {
    useSpinStore().hide();
  },
};
