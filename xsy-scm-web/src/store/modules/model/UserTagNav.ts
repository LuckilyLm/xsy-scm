/* 用户标签页 */

import {LocationQueryRaw} from 'vue-router';

export interface UserTagNav {
  /** 菜单名称-ID */
  menuName: string;
  /** 菜单标题 */
  menuTitle: string;
  /** 菜单请求参数 */
  menuQuery?: LocationQueryRaw;
  /** 来源菜单 */
  fromMenuName?: string;
  /** 来源菜单参数 */
  fromMenuQuery?: LocationQueryRaw;
}
