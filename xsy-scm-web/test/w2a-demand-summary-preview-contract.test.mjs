/**
 * Wave 2A 订单汇总 / 库存缺口预览前端契约单测（新增文件）。
 *
 * 守的是这一「只读辅助决策」增量最容易被悄悄违背、且违背后不报错的几条线：
 * 1. 计算状态枚举与后端 `PurchaseDemandSummaryVO.calculationStatus` 逐字对齐（5 值），颜色映射全覆盖；
 * 2. 预览端点走只读 `postRequest`（无幂等键），且**不得**复用/改写 `generate`；
 * 3. 前端绝不重算 `availableQuantity` / `shortageAgainstAvailable`（§6A.4），也不引入浮点/Decimal 运算；
 * 4. §6A.6 尚未裁决「在途采购是否抵扣缺口」→ `openPurchaseQuantity` 不得出现在前端契约里。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {SCM_DEMAND_SUMMARY_STATUS_ENUM, SCM_DEMAND_SUMMARY_STATUS_COLOR} from '../src/constants/business/scm/purchase-const.ts';

/** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被剪枝的名字是合规的。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

test('summary calculation status enum matches the backend 5 states and every value has a color', () => {
  assert.deepEqual(Object.keys(SCM_DEMAND_SUMMARY_STATUS_ENUM), [
    'STOCK_ENOUGH',
    'SHORTAGE',
    'ZERO_STOCK',
    'UNIT_MISMATCH',
    'NO_BALANCE',
  ]);
  assert.deepEqual(
    Object.values(SCM_DEMAND_SUMMARY_STATUS_ENUM).map((e) => e.value),
    Object.keys(SCM_DEMAND_SUMMARY_STATUS_ENUM)
  );
  for (const key of Object.keys(SCM_DEMAND_SUMMARY_STATUS_ENUM)) {
    assert.ok(SCM_DEMAND_SUMMARY_STATUS_COLOR[key], `${key} 缺少 tag 颜色映射`);
  }
});

test('summary-preview is a read-only postRequest and does not touch generate', () => {
  const api = code('../src/api/business/scm/purchase-demand-api.ts');
  assert.match(api, /summaryPreview:\s*\(data:\s*DemandSummaryPreviewQuery\)\s*=>\s*postRequest\('\/scm\/purchase\/demand\/summary-preview'/);
  // 只读：预览自身不得走带 Idempotency-Key 的命令式封装（只看 summaryPreview 到其 postRequest 之间）
  assert.doesNotMatch(api, /summaryPreview:[^\n]*purchaseCommand/);
  // generate 端点仍是原样（未被预览改写语义）
  assert.match(api, /purchaseCommand<GenerateResult>\('\/scm\/purchase\/demand\/generate'/);
});

test('preview component renders backend-derived quantities and never recomputes available or gap', () => {
  const src = code('../src/views/business/scm/purchase/components/purchase-demand-summary-preview.vue');
  assert.match(src, /scm-purchase-demand-summary-preview-table/);
  assert.match(src, /purchaseDemandApi\.summaryPreview/);
  assert.match(src, /SCM_DEMAND_SUMMARY_STATUS_ENUM\[record\.calculationStatus\]/);
  // §6A.4：数量全部后端算好，前端只渲染，绝不自己算可用量 / 差额
  assert.doesNotMatch(src, /Decimal/);
  assert.doesNotMatch(src, /availableQuantity\s*[-+]/);
  assert.doesNotMatch(src, /onHandQuantity\s*[-+]\s*reservedQuantity/);
  assert.doesNotMatch(src, /orderDemandQuantity\s*[-+]/);
  // 审计修复：预留必须分「本批自身 / 其他业务」两段，且旧的 shortageAgainstAvailable 语义已废弃
  assert.match(src, /selectedOrderReservedQuantity/);
  assert.match(src, /otherReservedQuantity/);
  assert.match(src, /stockAvailableForSelectedOrders/);
  assert.doesNotMatch(src, /shortageAgainstAvailable/);
  // 差额不是采购建议：文案必须显性说明
  assert.match(src, /不是最终净采购建议/);
});

test('undecided in-transit deduction stays out of the Wave 2A frontend contract', () => {
  const types = code('../src/views/business/scm/purchase/purchase-types.ts');
  assert.doesNotMatch(types, /openPurchaseQuantity/);
  assert.match(types, /interface DemandSummaryRow/);
  assert.match(types, /interface DemandSummaryPreviewQuery/);
});

test('the preview is a tab inside the existing demand page with no new route or menu wiring', () => {
  const list = code('../src/views/business/scm/purchase/purchase-demand-list.vue');
  assert.match(list, /PurchaseDemandSummaryPreview/);
  assert.match(list, /a-tab-pane\s+key="preview"/);
  // 既有需求列表的写入口（generate / allocate）仍在本页
  assert.match(list, /scm:purchase:demand:generate/);
  assert.match(list, /scm:purchase:demand:allocate/);
});
