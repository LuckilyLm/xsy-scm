/**
 * 与后端 `PasswordPolicies.valid` 保持一致的客户端校验：
 * 12—72 个 UTF-8 字节，不含空白字符，且同时包含大写、小写、数字和符号。
 * 客户端只做即时提示，服务端策略仍是唯一约束。
 */

export const PASSWORD_HINT = '12—72 位，需包含大写字母、小写字母、数字和符号，且不含空格';

export function passwordByteLength(value: string): number {
    return new TextEncoder().encode(value).length;
}

export function isValidPassword(value: string): boolean {
    if (value.length === 0) {
        return false;
    }
    const bytes = passwordByteLength(value);
    if (bytes < 12 || bytes > 72) {
        return false;
    }
    if (/\s/.test(value)) {
        return false;
    }
    return /[A-Z]/.test(value) && /[a-z]/.test(value) && /\d/.test(value) && /[^A-Za-z0-9]/.test(value);
}
