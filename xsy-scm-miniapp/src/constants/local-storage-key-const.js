/*
 * 本地存储 key 常量
 *
 * 前缀与后台管理端（xsy-scm-web）隔离，避免同域 H5 部署时互相覆盖登录态。
 */

/**
 * key前缀
 */
const KEY_PREFIX = 'xsy_mall_';
/**
 * localStorageKey集合
 */
// token
export const USER_TOKEN = `${KEY_PREFIX}token`;
/**
 * 搜索历史（客户端本地维护，规划 §13）
 * 热词来自服务端，历史词只存本机，不上报。
 */
export const SEARCH_HISTORY = `${KEY_PREFIX}search_history`;
