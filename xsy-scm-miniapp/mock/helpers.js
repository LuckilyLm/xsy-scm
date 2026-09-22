/*
 * Mock 契约层 · 通用工具
 *
 * 返回值一律是 SmartAdmin 的响应信封 `{ code, msg, data }`，
 * 且 **code === 1 为成功** —— 与真实后端保持一致，这样页面代码不需要为 mock 写分支，
 * 请求层的错误处理、会话失效、Toast 等逻辑也都能被真实地走到。
 */

import { SUCCESS_CODE } from '@/constants/error-code-const';
import { USER_TOKEN } from '@/constants/local-storage-key-const';
import { findSession } from './session';

/** 构造成功信封 */
export function ok(data) {
  return { code: SUCCESS_CODE, msg: 'success', data };
}

/**
 * 构造失败信封。
 * @param {number} code 业务错误码
 * @param {string} msg  面向用户的提示
 */
export function fail(code, msg) {
  return { code, msg, data: null };
}

/**
 * 模拟网络延迟，让 loading 态在开发时可见。
 * 刻意保留少量抖动，避免 UI 出现"瞬间完成"的假象而掩盖竞态问题。
 */
export function delay(min = 120, max = 320) {
  const ms = min + Math.random() * (max - min);
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * 内存分页，对齐后端 `PageResult`。
 * 注意 V2 用 `pageNum` / `pageSize`（v1 的 `page` 已废弃）。
 */
export function paginate(list, { pageNum = 1, pageSize = 10 } = {}) {
  const num = Math.max(1, Number(pageNum) || 1);
  const size = Math.min(500, Math.max(1, Number(pageSize) || 10));
  const start = (num - 1) * size;
  return {
    list: list.slice(start, start + size),
    total: list.length,
    pageNum: num,
    pageSize: size,
    pages: Math.ceil(list.length / size),
  };
}

/**
 * 从本地存储里取当前登录客户，供需要登录态的 mock 接口使用。
 * 返回 null 表示未登录，调用方应返回会话失效码让请求层走统一跳转。
 */
export function currentCustomer() {
  const token = uni.getStorageSync(USER_TOKEN);
  return token ? findSession(token) : null;
}
