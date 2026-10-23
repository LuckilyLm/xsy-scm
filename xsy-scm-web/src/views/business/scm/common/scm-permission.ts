/** 页面和按钮使用同一份权限判断，服务端仍负责最终校验。 */
import {useUserStore} from '/@/store/modules/system/user';

/** 权限点中只关心 `webPerms`。 */
interface WebPermPoint {
  webPerms?: string;
}

/** 当前登录者是否持有指定功能权限码；超级管理员恒为真。 */
export function hasPermission(code: string): boolean {
  const userStore = useUserStore();
  if (userStore.administratorFlag) {
    return true;
  }
  const pointsList = userStore.getPointList as WebPermPoint[] | undefined;
  if (!pointsList) {
    return false;
  }
  return pointsList.some((point) => point.webPerms === code);
}
