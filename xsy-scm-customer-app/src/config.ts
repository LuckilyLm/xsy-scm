/**
 * 运行时配置。TARO_APP_API_BASE 由 config/index.ts 中的 defineConstants 注入。
 * 小程序包体内是编译期常量；H5 开发期可设为 '/api' 走 devServer 代理。
 */
export const API_BASE: string = process.env.TARO_APP_API_BASE || 'https://api.example.com'

export const APP_NAME = '鲜蔬源商城'

/** 结算幂等键过期时间（毫秒），用于避免重复提交。 */
export const IDEMPOTENCY_TTL = 5 * 60 * 1000
