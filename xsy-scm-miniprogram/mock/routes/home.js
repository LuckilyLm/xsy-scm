/*
 * 商城首页装修（mock 契约层）
 *
 * 首页装修由后台发布后下发；本地没有后台，这里返回「未发布」，
 * 让首页回退到常购 / 活动 / 新品 / 商品目录的默认组合。
 * 不伪造轮播与专题，避免把假装修当成正式内容。
 */
import { ok } from '../helpers';

export const homeRoutes = [
  {
    // 轻量主题读取：登录页、改密页在未登录时也会调用，必须匿名可用
    method: 'GET',
    path: '/scm/mall/home/theme',
    handler: async () => ok({ releaseId: null, themeCode: 'FRESH' }),
  },
  {
    method: 'GET',
    path: '/scm/mall/home',
    handler: async () => ok({ releaseId: null, themeCode: 'FRESH', blocks: [] }),
  },
];
