/*
 * 所有路由入口
 *
 */
import {homeRouters} from './system/home';
import {loginRouters} from './system/login';
import {helpDocRouters} from './support/help-doc';
import {screenRouters} from './business/screen';
import PurchaseSharePublic from '/@/views/business/scm/purchase/purchase-share-public.vue';
import NotFound from '/@/views/system/40X/404.vue';
import NoPrivilege from '/@/views/system/40X/403.vue';

export const routerArray = [
    ...loginRouters,
    ...homeRouters,
    ...helpDocRouters,
    ...screenRouters,
    // 采购单分享页：令牌即凭证，不登录也不套后台布局
    {path: '/purchase-share/:token', name: 'purchaseSharePublic', component: PurchaseSharePublic},
    {path: '/:pathMatch(.*)*', name: '404', component: NotFound},
    {path: '/403', name: '403', component: NoPrivilege}
];
