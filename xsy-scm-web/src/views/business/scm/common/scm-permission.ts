/** SCM 页面权限与指令使用同一份登录权限。按钮显隐和表单禁用状态都要按权限判断，服务端仍负责最终校验。 */
import {useUserStore} from '/@/store/modules/system/user';

/** `t_menu` 功能点在 store 里的最小形状（指令判定的就是 `webPerms` 这一个字段）。 */
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
