/**
 * Wave 3 订单录单效率（草稿恢复 / 历史复用 / 最近已确认订单价）前端契约单测。
 *
 * 钉死这一波最容易被日后改动悄悄破坏、且破坏后不报错的前端边界（后端读路径 / 权限由 Java 测试覆盖）：
 * 1. 最近已确认订单价是只读查询：走 getRequest，绝不复用带 Idempotency-Key 的 orderCommand；
 * 2. 最近已确认订单价仅旁证：不写回解析单价、不前端重算，preview / 定价路径保持不变；
 * 3. 草稿恢复 / 历史复用接在既有 Drawer，无新增路由 / 菜单；创建成功清草稿；
 * 4. 历史复用只读 GET detail，不引入后端「复制订单」命令；
 * 5. 「复用为新单」沿用 scm:order:add，明细「历史价」按客户 + SKU 现查。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

/** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的名字是合规的。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

test('最近已确认订单价走只读 getRequest 的 reference 端点，不复用幂等命令封装', () => {
  const api = code('../src/api/business/scm/order-api.ts');
  assert.match(api, /recentPrices:[\s\S]*?getRequest\('\/scm\/order\/reference\/recent-prices'/);
  assert.doesNotMatch(api, /recentPrices:[^\n]*orderCommand/);
  // 既有写命令与价格预览路径保持不变
  assert.match(api, /preview:[\s\S]*?postRequest\('\/scm\/order\/price\/preview'/);
});

test('录单页把草稿恢复 / 历史复用接在既有 Drawer，创建成功清草稿', () => {
  const drawer = code('../src/views/business/scm/order/components/order-form-drawer.vue');
  assert.match(drawer, /promptRestoreDraft/);
  assert.match(drawer, /closeDrawer/);
  assert.match(drawer, /openFromHistory/);
  assert.match(drawer, /defineExpose\(\{open,\s*openFromHistory\}\)/);
  assert.match(drawer, /if \(isNew\) clearDraft\(\)/);
  // 关闭 / 保存动作走 closeDrawer，不再直接置 visible=false
  assert.doesNotMatch(drawer, /@close="visible=false"/);
});

test('历史复用只读 GET detail，不引入后端复制命令', () => {
  const drawer = code('../src/views/business/scm/order/components/order-form-drawer.vue');
  assert.match(drawer, /orderApi\.detail\(id\)/);
  assert.match(drawer, /fromHistory/);
  const api = code('../src/api/business/scm/order-api.ts');
  assert.doesNotMatch(api, /\bcopy\b|\bduplicate\b|\bclone\b/i);
});

test('明细表按客户 + SKU 现查最近已确认订单价，且不回写解析单价、不重算', () => {
  const table = code('../src/views/business/scm/order/components/order-item-editable-table.vue');
  assert.match(table, /orderApi\.recentPrices\(props\.customerId,\s*record\.skuId/);
  // 只渲染后端字符串价，绝不前端 Decimal 重算或写回 draftUnitPrice
  assert.doesNotMatch(table, /Decimal/);
  assert.doesNotMatch(table, /record\.draftUnitPrice\s*=/);
});

test('订单列表把「复用为新单」接在既有操作列，沿用 scm:order:add 权限', () => {
  const list = code('../src/views/business/scm/order/order-list.vue');
  assert.match(list, /openFromHistory\(record\.orderId\)/);
  assert.match(list, /复用为新单/);
  assert.match(list, /v-privilege="'scm:order:add'"/);
});
