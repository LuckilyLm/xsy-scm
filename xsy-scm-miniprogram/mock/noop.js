/*
 * Mock 契约层的空实现。
 *
 * 当 `VITE_APP_USE_MOCK !== 'true'` 时，vite.config.js 会把 `@/mock` 别名到本文件，
 * 从而保证假数据与 mock 路由**绝对不会进入构建产物**。
 *
 * 之所以不用「静态 import + 条件分支 + 指望 tree-shaking」：
 * fixtures.js 在模块顶层做了 `PRODUCT_TABLE.map(...)`，Rollup 无法证明其无副作用，
 * 有可能把整包假数据留在产物里。别名替换是确定性的，不依赖打包器的推断能力。
 *
 * 本文件必须与 src/mock/index.js 导出同名同签名的接口。
 */

export const USE_MOCK = false;

/** 永远不命中，调用方直接回落真实请求 */
export async function dispatchMock() {
  return null;
}

export function listMockRoutes() {
  return [];
}
