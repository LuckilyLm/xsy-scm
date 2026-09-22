/*
 * Mock 契约层 · 入口
 *
 * ============================ 这是什么 ============================
 * 后端 `/scm/mall/**` 尚未实现（xsy-scm-server 里没有 scm/mall 模块），
 * 而本分支只负责小程序客户端。为了不让客户端开发被后端阻塞，
 * 这里按**冻结的契约**提供一层假后端。
 *
 * ============================ 为什么这样设计 ============================
 * mock 挂在**请求层内部**，而不是替换 `src/api/mall/**`：
 *   - 页面代码零改动，永远只调 `src/api/mall/**`；
 *   - 返回的是真实信封 `{ code, msg, data }`（code === 1 为成功），
 *     所以请求层的错误处理、会话失效跳转、Toast 全都会被真实走到；
 *   - 后端就绪后，**只需把 VITE_APP_USE_MOCK 置为 false**，无需改任何业务代码。
 *
 * ============================ 如何关闭 ============================
 * 在对应 `.env.*` 里设 `VITE_APP_USE_MOCK=false`（默认即 false）。
 * 关闭后本模块因静态条件为假而被 Rollup tree-shake 掉，不进生产包。
 *
 * ============================ 契约维护 ============================
 * 字段形状是**暂定**的，依据 v1 冻结契约 + V2 既有领域习惯。
 * 后端落地后需逐字段核对 fixtures.js 与 routes/**，页面与 api 层不用改。
 */
import { authRoutes } from './routes/auth';
import { catalogRoutes } from './routes/catalog';
import { homeRoutes } from './routes/home';
import { delay } from './helpers';
import { findSession } from './session';
import { USER_TOKEN } from '@/constants/local-storage-key-const';

/** 是否启用 mock。Vite 在构建时会把 import.meta.env.* 替换为字面量，便于 DCE。 */
export const USE_MOCK = import.meta.env.VITE_APP_USE_MOCK === 'true';

const ROUTES = [...authRoutes, ...catalogRoutes, ...homeRoutes];

/**
 * 匹配 `/a/:id/b` 形式的路径，返回参数对象；不匹配返回 null。
 * 只做简单的段级匹配，够用且无依赖。
 */
function matchPath(pattern, url) {
  const patternSegments = pattern.split('/').filter(Boolean);
  const urlSegments = url.split('?')[0].split('/').filter(Boolean);

  if (patternSegments.length !== urlSegments.length) {
    return null;
  }

  const params = {};
  for (let i = 0; i < patternSegments.length; i += 1) {
    const p = patternSegments[i];
    if (p.startsWith(':')) {
      params[p.slice(1)] = decodeURIComponent(urlSegments[i]);
    } else if (p !== urlSegments[i]) {
      return null;
    }
  }
  return params;
}

/**
 * 分发一次 mock 请求。
 *
 * @returns {Promise<object|null>} 命中的信封对象；**未命中返回 null**，
 *          调用方应回落到真实网络请求（这样新增接口不会因为没写 mock 而直接失败）。
 */
export async function dispatchMock(url, method, data) {
  const route = ROUTES.find((r) => r.method === method.toUpperCase() && matchPath(r.path, url) !== null);
  if (!route) {
    return null;
  }

  await delay();

  const params = matchPath(route.path, url) || {};
  const token = uni.getStorageSync(USER_TOKEN);
  const session = findSession(token);

  try {
    return await route.handler({ params, data: data || {}, session, token });
  } catch (e) {
    // mock 自身写错时不要伪装成业务错误，直接把信息暴露出来便于定位
    return {
      code: 0,
      msg: `[mock] ${route.method} ${route.path} 处理异常：${e && e.message}`,
      data: null,
    };
  }
}

/** 已注册的 mock 路由，便于开发时自检覆盖范围 */
export function listMockRoutes() {
  return ROUTES.map((r) => `${r.method} ${r.path}`);
}
