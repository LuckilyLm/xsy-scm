/*
 * Mock 契约层 · 会话
 *
 * 假后端自己维护「令牌 → 客户」的映射，**不去读 Pinia store**：
 * store 依赖 api 层、api 层依赖请求层、请求层再引用 mock 层，
 * 若 mock 层反过来读 store 就又绕成环。
 *
 * 真实的令牌持久化仍由 store 负责（写 `USER_TOKEN`），这里只按令牌查客户，
 * 因此刷新应用后 mock 也能正确识别登录态。
 */

/** token -> customer */
const SESSIONS = new Map();

let seq = 0;

/** 签发一个假令牌并登记会话 */
export function issueSession(customer) {
  seq += 1;
  const token = `mock-token-${Date.now()}-${seq}`;
  SESSIONS.set(token, customer);
  return { token, customer };
}

export function findSession(token) {
  return token ? SESSIONS.get(token) || null : null;
}

export function revokeSession(token) {
  if (token) {
    SESSIONS.delete(token);
  }
}

/** 供测试或"重置数据"使用 */
export function clearSessions() {
  SESSIONS.clear();
}
