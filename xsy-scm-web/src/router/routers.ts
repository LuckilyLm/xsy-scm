/*
 * 所有路由入口
 *
 */
import {homeRouters} from './system/home';
import {loginRouters} from './system/login';
import {helpDocRouters} from './support/help-doc';
import {screenRouters} from './business/screen';
import PurchaseSharePublic from '/@/views/business/scm/purchase/purchase-share-public.vue';
import CustomerPromoLanding from '/@/views/business/scm/customer/customer-promo-landing.vue';
import TracePublic from '/@/views/business/scm/trace/trace-public.vue';
import NotFound from '/@/views/system/40X/404.vue';
import NoPrivilege from '/@/views/system/40X/403.vue';

export const routerArray = [
  ...loginRouters,
  ...homeRouters,
  ...helpDocRouters,
  ...screenRouters,
  // 采购单分享页：令牌即凭证，不登录也不套后台布局
  {path: '/purchase-share/:token', name: 'purchaseSharePublic', component: PurchaseSharePublic},
  // 客户推广码落地页：扫码即记录来源，不需要登录
  {path: '/promo/:code', name: 'customerPromoLanding', component: CustomerPromoLanding},
  // 扫码溯源：标签上的令牌即凭证，客户与监管都不登录
  {path: '/t/:token', name: 'tracePublic', component: TracePublic},
  {path: '/:pathMatch(.*)*', name: '404', component: NotFound},
  {path: '/403', name: '403', component: NoPrivilege},
];
