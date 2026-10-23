/* 路由 */
import nProgress from 'nprogress';
import 'nprogress/nprogress.css';
import {nextTick} from 'vue';
import {createRouter, createWebHashHistory} from 'vue-router';
import {routerArray} from './routers';
import {PAGE_PATH_404, PAGE_PATH_LOGIN, HOME_PAGE_PATH} from '/@/constants/common-const';
import {HOME_PAGE_NAME} from '/@/constants/system/home-const';
import SmartLayout from '../layout/index.vue';
import {useUserStore} from '/@/store/modules/system/user';
import {localClear, localRead} from '/@/utils/local-util';
import _ from 'lodash';
import LocalStorageKeyConst from '/@/constants/local-storage-key-const';

export const router = createRouter({
  history: createWebHashHistory(),
  routes: routerArray,
  strict: true,
  scrollBehavior: () => ({left: 0, top: 0}),
});

// 路由加载前
router.beforeEach(async (to, from, next) => {
  // 进度条开始
  nProgress.start();

  // 公共页面，任何时候都可以跳转
  if (to.path === PAGE_PATH_404) {
    next();
    return;
  }

  // 采购单分享页与客户推广码落地页由路径里的令牌授权，登录态与菜单权限都不参与
  if (to.path.startsWith('/purchase-share/') || to.path.startsWith('/promo/')) {
    next();
    return;
  }

  // 扫码溯源页面向客户与监管：标签上的令牌即凭证，不登录也不套后台布局
  if (to.path.startsWith('/t/')) {
    next();
    return;
  }

  // 验证登录
  const token = localRead(LocalStorageKeyConst.USER_TOKEN);
  if (!token) {
    useUserStore().logout();
    if (to.path === PAGE_PATH_LOGIN) {
      next();
    } else {
      next({path: PAGE_PATH_LOGIN});
    }
    return;
  }

  // 登录页，则跳转到首页
  if (to.path === PAGE_PATH_LOGIN) {
    next({path: HOME_PAGE_PATH});
    return;
  }

  // 首页需要登录，不校验菜单权限
  if (to.path === HOME_PAGE_NAME) {
    next();
    return;
  }

  // 懒加载组件并补上 name，供 keep-alive 使用
  let toRouterInfo = routerMap.get(to.name);
  if (toRouterInfo && _.isFunction(toRouterInfo.component) && toRouterInfo.meta.renameComponentFlag === false) {
    // 懒加载组件是函数，直接调用
    toRouterInfo.component().then((val) => {
      val.default.name = to.meta.componentName;
      toRouterInfo.meta.renameComponentFlag = true;
    });
  }

  // 设置 tagNav
  useUserStore().setTagNav(to, from);

  // 记录需要缓存的页面
  if (to.meta.keepAlive) {
    nextTick(() => {
      useUserStore().pushKeepAliveIncludes(to.meta.componentName);
    });
  }

  next();
});

// 路由加载后
router.afterEach(() => {
  nProgress.done();
});

// 构建路由对象
const routerMap = new Map();

export function buildRoutes(menuRouterList) {
  let menuList = menuRouterList ? menuRouterList : useUserStore().getMenuRouterList || [];
  const routerList = [];
  // 全部页面组件，按菜单路径匹配
  const modules = import.meta.glob('../views/**/**.vue');

  for (const e of menuList) {
    if (!e.menuId) {
      continue;
    }
    if (!e.path) {
      continue;
    }
    if (e.deletedFlag && e.deletedFlag === true) {
      continue;
    }
    let route = {
      path: e.path.startsWith('/') ? e.path : `/${e.path}`,
      // 用 menuId 作唯一的 name
      name: e.menuId.toString(),
      meta: {
        // 数据库菜单 id
        id: e.menuId.toString(),
        // 组件名称
        componentName: e.menuId.toString(),
        // 菜单标题
        title: e.menuName,
        // 菜单图标
        icon: e.icon,
        // 是否在菜单隐藏
        hideInMenu: !e.visibleFlag,
        // 是否缓存页面
        keepAlive: e.cacheFlag,
        // 是否为外链
        frameFlag: e.frameFlag,
        // 外链地址
        frameUrl: e.frameUrl,
        // 是否已补组件 name
        renameComponentFlag: false,
      },
    };

    if (e.frameFlag) {
      route.component = () => import('../components/framework/iframe/iframe-index.vue');
    } else {
      let componentPath = e.component && e.component.startsWith('/') ? e.component : '/' + e.component;
      let relativePath = `../views${componentPath}`;
      // // eslint-disable-next-line no-prototype-builtins
      route.component = modules[relativePath];
    }
    routerList.push(route);
    routerMap.set(e.menuId.toString(), route);
  }

  // 挂到布局路由下
  router.addRoute({
    path: '/',
    meta: {},
    component: SmartLayout,
    children: routerList,
  });
}
