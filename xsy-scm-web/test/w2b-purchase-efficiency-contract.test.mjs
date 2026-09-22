/**
 * Wave 2B 采购效率（批量 / 导出 / 打印 / 按商品收货工作台）前端契约单测（新增文件）。
 *
 * 守的是这一批「不新增迁移、复用既有权限」的效率增量最容易被悄悄违背、且违背后不报错的线：
 * 1. 导出走只读 `postDownload('/scm/purchase/export')`，绝不落写接口；
 * 2. 批量少收关单 / 按商品工作台走只读 `postRequest`，且**不得**复用带 Idempotency-Key 的 `purchaseCommand`
 *    （后端这两个端点都不接幂等头，误用会让契约与后端不一致）；
 * 3. 按商品工作台只渲染后端逐行裁剪后聚合的四位定点字符串，前端绝不重算欠收 / 超收；
 * 4. 打印是纯客户端动作，`purchase-order-print.ts` 里不得出现任何网络 / 写调用；
 * 5. 导出列目录与后端 `PurchaseOrderExportSupport` 的 15 列逐字对齐（顺序敏感）。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {SCM_PURCHASE_EXPORT_COLUMNS} from '../src/constants/business/scm/purchase-const.ts';

/** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被剪枝的名字是合规的。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

test('采购单导出走只读 postDownload，不落任何写接口', () => {
  const api = code('../src/api/business/scm/purchase-order-api.ts');
  assert.match(api, /export:\s*\(data:\s*OrderExportPayload\)\s*=>\s*postDownload\('\/scm\/purchase\/export'/);
  // 只读：导出自身不得使用命令式幂等封装
  assert.doesNotMatch(api, /export:[^\n]*purchaseCommand/);
});

test('批量少收关单走只读 postRequest，不复用带幂等键的 purchaseCommand', () => {
  const api = code('../src/api/business/scm/purchase-order-api.ts');
  assert.match(api, /batchShortClose:\s*\(data:\s*OrderBatchShortClosePayload\)\s*=>\s*postRequest\('\/scm\/purchase\/batch\/short-close'/);
  assert.doesNotMatch(api, /batchShortClose:[^\n]*purchaseCommand/);
});

test('按商品收货工作台走只读 postRequest 的 item-workbench 端点', () => {
  const api = code('../src/api/business/scm/purchase-receipt-api.ts');
  assert.match(api, /itemWorkbench:\s*\(data:\s*ReceiptItemWorkbenchQuery\)\s*=>\s*postRequest\('\/scm\/purchase\/receipt\/item-workbench\/query'/);
  assert.doesNotMatch(api, /itemWorkbench:[^\n]*purchaseCommand/);
});

test('工作台组件只渲染后端聚合量，绝不重算欠收 / 超收', () => {
  const src = code('../src/views/business/scm/purchase/components/purchase-receipt-item-workbench.vue');
  assert.match(src, /scm-purchase-receipt-item-workbench-table/);
  assert.match(src, /purchaseReceiptApi\.itemWorkbench/);
  assert.doesNotMatch(src, /Decimal/);
  // §6.6：欠收 / 超收来自后端逐行裁剪求和，前端不得自行做 planned-received 之类减法
  assert.doesNotMatch(src, /plannedQuantity\s*[-+]/);
  assert.doesNotMatch(src, /receivedQuantity\s*[-+]/);
  assert.doesNotMatch(src, /plannedQuantity\s*-\s*receivedQuantity/);
});

test('打印工具是纯客户端，无任何网络或写调用', () => {
  const src = code('../src/views/business/scm/purchase/purchase-order-print.ts');
  assert.match(src, /export function printPurchaseOrders/);
  assert.doesNotMatch(src, /postRequest|purchaseCommand|getRequest|axios|fetch\(|XMLHttpRequest/);
});

test('导出列目录与后端 15 列逐字对齐且顺序敏感', () => {
  assert.deepEqual(SCM_PURCHASE_EXPORT_COLUMNS.map((c) => c.key), [
    'orderNo',
    'supplierName',
    'supplierCode',
    'purchaserName',
    'warehouseName',
    'warehouseCode',
    'plannedArrivalDate',
    'status',
    'totalAmount',
    'receivedProgress',
    'remark',
    'cancelReason',
    'shortCloseReason',
    'submittedAt',
    'createdAt',
  ]);
});

test('收货页把工作台作为「按商品」Tab 内联，无新增路由 / 菜单', () => {
  const list = code('../src/views/business/scm/purchase/purchase-receipt-list.vue');
  assert.match(list, /PurchaseReceiptItemWorkbench/);
  assert.match(list, /a-tab-pane\s+key="by-item"/);
  // 既有按单据写入口（确认 / 入库 / 删除）仍在，未被工作台改写
  assert.match(list, /scm:purchase:receipt:confirm/);
  assert.match(list, /scm:purchase:receipt:putaway/);
});

test('采购单页把批量 / 导出 / 打印入口接在既有工具栏，权限沿用既有码', () => {
  const list = code('../src/views/business/scm/purchase/purchase-order-list.vue');
  assert.match(list, /batchShortClose/);
  assert.match(list, /onExport/);
  assert.match(list, /printRow/);
  assert.match(list, /scm:purchase:short-close/);
  assert.match(list, /scm:purchase:query/);
});
