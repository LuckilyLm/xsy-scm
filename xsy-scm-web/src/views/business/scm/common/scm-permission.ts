/**
 * SCM 页面在 `<script>` 里的功能权限判定（与 `v-privilege` 指令读同一份数据）。
 *
 * 为什么不能只用 `v-privilege`：指令按单个码删 DOM 节点，够管按钮显隐；但「无分配权时把负责人
 * 框置灰、编辑态让它只读」和「无全量范围权限时空表文案换口径」都是 `:disabled` / 计算属性，
 * 必须在脚本里问一句「当前调用者有没有这个码」。判定口径与 `directives/privilege.ts` 一致
 * （超管放行，否则比对功能点的 `webPerms`），不另建第二套真相；服务端才是权威，这里只决定 UI 形态。
 */
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
