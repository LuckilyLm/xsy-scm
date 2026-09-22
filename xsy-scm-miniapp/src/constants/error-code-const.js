/*
 * 响应与错误码常量
 *
 * 后端（SmartAdmin）统一信封：{ code, msg, data }
 * **成功是 code === 1，不是 0** —— 这是与旧商城实现（code === 0）最容易踩错的一点。
 *
 * 平台级错误码来自 sa-base 的 UserErrorCode / UnexpectedErrorCode，
 * 已在后端源码中逐一核对，可安全硬编码。
 */

/** 成功码 */
export const SUCCESS_CODE = 1;

/**
 * 平台级错误码（sa-base/UserErrorCode，取值区间 30001–39999）
 * @see net.lab1024.sa.base.common.code.UserErrorCode
 */
export const PLATFORM_ERROR_CODE = {
  /** 参数错误 */
  PARAM_ERROR: 30001,
  /** 数据不存在 */
  DATA_NOT_EXIST: 30002,
  /** 数据已存在 */
  ALREADY_EXIST: 30003,
  /** 操作过快 */
  REPEAT_SUBMIT: 30004,
  /** 无权限 */
  NO_PERMISSION: 30005,
  /** 功能开发中 */
  DEVELOPING: 30006,
  /** 未登录或登录失效 —— 需要清理会话并跳登录页 */
  LOGIN_STATE_INVALID: 30007,
  /** 用户状态异常 —— 需要清理会话并跳登录页 */
  USER_STATUS_ERROR: 30008,
  /** 请勿重复提交 */
  FORM_REPEAT_SUBMIT: 30009,
  /** 登录连续失败已锁定 */
  LOGIN_FAIL_LOCK: 30010,
  /** 登录连续失败将锁定 */
  LOGIN_FAIL_WILL_LOCK: 30011,
  /** 长时间未操作，需要重新登录 —— 需要清理会话并跳登录页 */
  LOGIN_ACTIVE_TIMEOUT: 30012,
};

/** 会触发「清会话 + 跳登录页」的错误码集合 */
export const SESSION_EXPIRED_CODES = [
  PLATFORM_ERROR_CODE.LOGIN_STATE_INVALID,
  PLATFORM_ERROR_CODE.USER_STATUS_ERROR,
  PLATFORM_ERROR_CODE.LOGIN_ACTIVE_TIMEOUT,
];

/**
 * 商城业务错误码。
 *
 * ⚠️ 尚未冻结 —— 后端 `/scm/mall/**` 还未实现，码段需与后端一次性对齐后再填。
 *
 * 已核实的历史约束（见 docs/reference/2026-09-22-遗留商城v1契约参考.md §5）：
 * 旧商城用的 40070 / 40074 / 40970 已被 V2 的 OrderErrorCode 占用，
 * 因此**不能沿用旧码**，必须重新选段。
 *
 * 在冻结之前，客户端一律按「未知业务错误」处理：直接展示服务端 msg，
 * 不做任何依赖具体码值的分支。
 */
export const MALL_ERROR_CODE = {};

/**
 * 业务错误码中，需要客户端做特殊交互的语义位。
 * 这些语义必须先由后端确认码值，再填入上方 MALL_ERROR_CODE。
 */
export const MALL_ERROR_SEMANTIC = {
  /** 价格已变化 —— 前端应重新 preview 并提示用户确认 */
  PRICE_CHANGED: 'PRICE_CHANGED',
  /** 库存不足 —— 前端应提示并刷新可用量 */
  STOCK_INSUFFICIENT: 'STOCK_INSUFFICIENT',
  /** 商品不可见 —— 前端应从列表/购物车中标记失效 */
  SKU_NOT_VISIBLE: 'SKU_NOT_VISIBLE',
};
