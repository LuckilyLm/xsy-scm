import {ApiError} from '../api/http';

/**
 * 把后端稳定错误码映射为面向用户的文案。
 * 登录失败统一为“用户名或密码错误”，避免账号枚举；内部明细只用于分类，不展示凭据信息。
 */
export function describeAuthError(error: unknown): string {
    if (!(error instanceof ApiError)) {
        return '操作失败，请稍后重试';
    }
    switch (error.code) {
        case 40101:
            return '用户名或密码错误';
        case 40102:
        case 40103:
            return '登录状态已失效，请重新登录';
        case 40301:
            return '账号已停用，请联系管理员';
        case 40302:
            return '账号已被临时锁定，请稍后再试';
        case 40303:
            return '无权执行此操作';
        case 40304:
            return '页面安全令牌已失效，请刷新页面后重试';
        case 40305:
            return '请先修改密码后再继续操作';
        default:
            return error.message;
    }
}
