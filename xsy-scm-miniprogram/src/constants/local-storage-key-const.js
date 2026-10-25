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
/**
 * 结算页临时选中的收货地址 id。
 * 地址列表页被结算页以「选择」模式打开时写入，结算页 onShow 读取后清除；
 * 只存 id，地址内容仍以服务端列表为准。
 */
export const CHECKOUT_ADDRESS = `${KEY_PREFIX}checkout_address`;
/**
 * 首页弹窗「每日首次展示」记录：{version, date}。
 * version 变化时当天会重新展示一次，便于新活动当天上线。
 */
export const HOME_POPUP_SEEN = `${KEY_PREFIX}home_popup_seen`;
