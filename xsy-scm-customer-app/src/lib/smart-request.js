/*
 * 统一请求封装
 *
 * 与 SmartAdmin 共用 `{ code, ok, msg, data, dataType }` 响应信封，code === 0 表示成功。
 * 令牌统一使用 `Authorization: Bearer <token>`；会话失效码由请求层清理并跳回登录页。
 */
import { USER_TOKEN } from '@/constants/local-storage-key-const';
import { PLATFORM_ERROR_CODE, SESSION_EXPIRED_CODES, SUCCESS_CODE } from '@/constants/error-code-const';
/*
 * 注意：这里刻意用 `@mock` 而不是 `@/mock`。
 * uni-app 自带一条 `@` → `src/` 的别名且优先级更高，`@/mock` 会被它先命中、
 * 解析成 src/mock（该目录已不存在）。`@mock` 不匹配 uni-app 的 `@` 规则，
 * 因此能被 vite.config.js 里我们自己的别名稳定接管。
 */
import { USE_MOCK, dispatchMock } from '@mock';

const baseUrl = import.meta.env.VITE_APP_API_URL;

/** 登录相关接口自身的失败不应触发「登录失效跳转」，否则会与登录页互相重定向 */
const AUTH_PATHS = ['/scm/mall/auth/login'];

let redirecting = false;

/**
 * 会话失效回调。
 *
 * 请求层**刻意不 import Pinia store**：store 要 import api 层，api 层 import 请求层，
 * 请求层若再 import store 就会形成循环依赖（Vite 构建时会报 Circular chunk）。
 * 因此改为由 store 侧调用 setSessionExpiredHandler 注入，把依赖方向反过来。
 */
let sessionExpiredHandler = null;

/**
 * 注册会话失效回调，由 store 在模块初始化时调用。
 * @param {Function} handler 无参回调，用于清理内存中的登录态
 */
export function setSessionExpiredHandler(handler) {
  sessionExpiredHandler = handler;
}

/**
 * 业务错误。携带后端原始 code / msg，便于调用方按码分支。
 * 字段名保持 `code` / `msg`，与后端信封一致，避免二次映射出错。
 */
export class ApiError extends Error {
  constructor(code, msg, response) {
    super(msg || '请求失败');
    this.name = 'ApiError';
    this.code = code;
    this.msg = msg || '请求失败';
    /** 原始响应，排障时可用 */
    this.response = response;
  }
}

export function isApiError(error, code) {
  return error instanceof ApiError && (code === undefined || error.code === code);
}

function getUserToken() {
  return uni.getStorageSync(USER_TOKEN) || '';
}

function isAuthPath(url) {
  return AUTH_PATHS.some((item) => url.startsWith(item));
}

/** 令牌失效：清会话 + 提示 + 跳登录页（做去重，避免并发请求触发多次跳转） */
function handleSessionExpired() {
  if (redirecting) {
    return;
  }
  redirecting = true;

  // 令牌的读写都是请求层自己的事，这里直接清掉，
  // 保证即使回调未注册（store 尚未初始化）也不会残留脏令牌。
  uni.removeStorageSync(USER_TOKEN);

  try {
    sessionExpiredHandler?.();
  } catch {
    // 回调异常不应阻塞跳转
  }

  uni.showToast({ title: '登录已失效，请重新登录', icon: 'none' });
  setTimeout(() => {
    redirecting = false;
    uni.reLaunch({ url: '/pages/login/login' });
  }, 800);
}

/**
 * 处理返回的信封。
 * 无论 HTTP 状态码为何都优先解析信封，否则业务错误会退化成「网络错误」。
 */
function handleResponse(response, resolve, reject, url) {
  const res = response.data;
  const code = res && res.code;

  if (code === SUCCESS_CODE && res?.ok !== false) {
    resolve(res);
    return;
  }

  // 令牌失效
  if (!isAuthPath(url) && SESSION_EXPIRED_CODES.includes(code)) {
    handleSessionExpired();
    reject(new ApiError(code, res.msg, response));
    return;
  }

  const message = (res && res.msg) || `请求失败 (${response.statusCode})`;
  uni.showToast({ title: message, icon: 'none' });
  reject(new ApiError(code, message, response));
}

/**
 * 通用请求
 * @param {string} url    以 / 开头的接口路径，如 '/scm/mall/catalog/products'
 * @param {string} method GET / POST / PUT / DELETE
 * @param {object} data   请求体
 * @param {object} options { idempotencyKey, header }
 */
export const request = function (url, method, data, options = {}) {
  const header = { 'Content-Type': 'application/json', ...(options.header || {}) };
  const token = getUserToken();
  if (token) {
    header.Authorization = 'Bearer ' + token;
  }
  if (options.idempotencyKey) {
    header['Idempotency-Key'] = options.idempotencyKey;
  }

  return new Promise((resolve, reject) => {
    // mock 开关为编译期常量：关闭时整段被 tree-shake，且 mock 模块不会进生产包。
    // 未命中 mock 路由时回落真实请求，所以新增接口不必先补 mock。
    if (USE_MOCK) {
      dispatchMock(url, method, data, { header }).then((mocked) => {
        if (mocked === null) {
          sendReal();
          return;
        }
        handleResponse({ statusCode: 200, data: mocked }, resolve, reject, url);
      });
      return;
    }
    sendReal();

    function sendReal() {
      uni.request({
        url: baseUrl + url,
        data,
        method,
        header,
        success: (response) => handleResponse(response, resolve, reject, url),
        fail: (error) => reject(new ApiError(0, '网络连接失败，请检查网络后重试', error)),
      });
    }
  });
};

/**
 * GET 请求。
 * @param {string} url    以 / 开头的接口路径
 * @param {object} params 查询参数（uni.request 会把 GET 的 data 拼到 query string）
 */
export const getRequest = (url, params) => request(url, 'GET', params);

export const postRequest = (url, data, options) => request(url, 'POST', data, options);

export const putRequest = (url, data, options) => request(url, 'PUT', data, options);

export const deleteRequest = (url, data, options) => request(url, 'DELETE', data, options);

/** 供调用方判断「登录失效」，避免各处硬编码 30007 */
export const LOGIN_INVALID_CODE = PLATFORM_ERROR_CODE.LOGIN_STATE_INVALID;
