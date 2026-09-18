/**
 * W5 采购域契约单测（新增文件，仿 W4 `w4-order-contract.test.mjs`）。
 *
 * 这里守的是**前端最容易悄悄违背、且违背后不会报错**的几条契约：
 * 1. 三态定点数（`null` / `"0.0000"` / 有值）在渲染层不得被合并；
 * 2. 分配是**集合**（Q13），改一条不得牵动同行其它条；
 * 3. 单位不一致（Q17）必须拒绝，不得猜换算系数；
 * 4. 提交前必须覆盖全部收货行（40998）、非标品必须给实重（40083）；
 * 5. 剪枝项（A4/A5、`resizable`）不得从 C 回流。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
  fixed,
  amount,
  quantity,
  progress,
  newOrder,
  newOrderItem,
  newAllocation,
  allocatedOnItem,
  allocationCapacity,
  unitMismatch,
  hasAllocation,
  validateOrder,
  payload,
  newConfirmLines,
  isNonStandard,
  validateConfirm,
  confirmPayload,
  toleranceHint,
  beyondRemaining,
} from '../src/views/business/scm/purchase/purchase-form-model.ts';
import {purchaseError} from '../src/views/business/scm/purchase/purchase-errors.ts';
import {
  SCM_PURCHASE_STATUS_ENUM,
  SCM_RECEIPT_STATUS_ENUM,
  SCM_DEMAND_STATUS_ENUM,
  SCM_WAREHOUSE_STATUS_ENUM,
  SCM_RECEIPT_MODE_ENUM,
  SCM_PUTAWAY_STATUS_ENUM,
  SCM_PURCHASE_OPERATION_ENUM,
  SCM_PURCHASE_TABLE_ID,
} from '../src/constants/business/scm/purchase-const.ts';

/** 一张最小的合法采购单。 */
function form() {
  const f = newOrder();
  f.supplierId = '1';
  f.warehouseId = '2';
  f.items = [{skuId: '3', plannedQuantity: '1.0000', purchasePrice: '2.0000', allocations: []}];
  return f;
}

/** 一张含标准品 + 非标品两行的收货单。 */
function receipt() {
  return {
    id: 1,
    version: 0,
    items: [
      {id: 11, version: 0, productType: 'STANDARD', remainingQuantity: '5.0000'},
      {id: 12, version: 0, productType: 'NON_STANDARD', remainingQuantity: '2.0000'},
    ],
  };
}

/** 一条待分配的需求：required 10 = allocated 4 + unallocated 6。 */
function demand(over = {}) {
  return {
    id: 7,
    skuId: '3',
    demandUnit: 'kg',
    status: 'PENDING',
    requiredQuantity: '10.0000',
    allocatedQuantity: '4.0000',
    unallocatedQuantity: '6.0000',
    version: 5,
    ...over,
  };
}

/**
 * 读源码并剥掉注释 —— 门禁只针对**代码**，Provenance 里提到被剪枝的名字是合规的。
 *
 * 剥离顺序不能反：Provenance 头自己会写出「斜杠 + 双星号」的路径通配写法，
 * 若先跑块注释正则，那个双星号会被当成块注释开头、一路吃到下一个「星号 + 斜杠」，
 * 把 HTML 注释的收尾标记一起吞掉，于是头注释反而留在了「代码」里。
 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

// ------------------------------------------------------------------
// 定点数与三态渲染
// ------------------------------------------------------------------

test('null, zero and missing amounts stay distinguishable in three states', () => {
  assert.equal(amount(null, true), '未定价');
  assert.equal(amount(null), '—');
  assert.equal(amount('0.0000', true), '¥ 0.0000');
  assert.equal(amount('0.0000'), '¥ 0.0000');
  assert.equal(quantity(null), '—');
  assert.equal(quantity('0.0000'), '0.0000');
  assert.equal(quantity(''), '—');
});

test('fixed rounds half-up to four decimals and never fabricates zero', () => {
  assert.equal(fixed('1.00005'), '1.0001');
  assert.equal(fixed('1'), '1.0000');
  assert.equal(fixed(2.5), '2.5000');
  assert.equal(fixed(null), '');
  assert.equal(fixed(undefined), '');
  assert.equal(fixed(''), '');
});

test('received progress is a ratio rendered as a percentage and stays blank when absent', () => {
  assert.equal(progress(null), '—');
  assert.equal(progress('0.0000'), '0.00%');
  assert.equal(progress('0.5000'), '50.00%');
  assert.equal(progress('1.0000'), '100.00%');
  assert.equal(progress('0.33335'), '33.34%');
});

// ------------------------------------------------------------------
// 采购单校验与请求体
// ------------------------------------------------------------------

test('order validation requires supplier, warehouse and at least one line', () => {
  const f = newOrder();
  assert.match(validateOrder(f), /供应商/);
  f.supplierId = '1';
  assert.match(validateOrder(f), /仓库/);
  f.warehouseId = '2';
  // `newOrder()` 自带一行空白行，先清空才能验到「至少一行」这条
  assert.match(validateOrder(f), /采购商品/);
  f.items = [];
  assert.match(validateOrder(f), /至少添加一行/);
});

test('order validation rejects duplicate SKU, non-positive quantity and bad price shape', () => {
  const f = form();
  f.items.push({...f.items[0]});
  assert.match(validateOrder(f), /重复/);
  f.items.pop();
  f.items[0].plannedQuantity = '0.0000';
  assert.match(validateOrder(f), /采购数量/);
  // 用户键入期间 antd 的 `precision` 不生效（见 purchase-form-model 的 typedFixed），
  // 模型里就是裸数：这里断言「校验时机与提交口径一致」——
  // 形状合法的短小数按 4 位定点归一后放行，而 `payload` 归一出的正是同一个值。
  f.items[0].plannedQuantity = '1.00';
  assert.equal(validateOrder(f), undefined);
  assert.equal(payload(f).items[0].quantity, '1.0000');
  assert.equal(validateOrder(f), undefined);
  // 真正不合法的形状仍然要被拒（不放宽定点纪律）。
  for (const bad of ['', '0.0000', '-1', '2.', '.5', 'abc', '1e5', '12345678901234567890']) {
    f.items[0].plannedQuantity = bad;
    assert.match(validateOrder(f), /采购数量/, `应收紧 ${JSON.stringify(bad)}`);
  }
  f.items[0].plannedQuantity = '1.0000';
  f.items[0].purchasePrice = '-1.0000';
  assert.match(validateOrder(f), /采购单价/);
  // 单价可以留空（未定价）；填了就必须是非负定点数。
  f.items[0].purchasePrice = '';
  assert.equal(validateOrder(f), undefined);
  f.items[0].purchasePrice = '0.0000';
  assert.equal(validateOrder(f), undefined);
});

test('allocation validation requires demand, positive quantity and demand version', () => {
  const f = form();
  f.items[0].allocations = [{quantity: '1.0000', demandVersion: 0}];
  assert.match(validateOrder(f), /分配缺少采购需求/);
  f.items[0].allocations = [{demandId: '7', quantity: '0.0000', demandVersion: 0}];
  assert.match(validateOrder(f), /分配数量/);
  // 分配数量同样按「输入框键入中的裸数」口径归一后再判定形状。
  f.items[0].allocations = [{demandId: '7', quantity: '1.00', demandVersion: 0}];
  assert.equal(validateOrder(f), undefined);
  assert.equal(payload(f).items[0].allocations[0].quantity, '1.0000');
  f.items[0].allocations = [{demandId: '7', quantity: '', demandVersion: 0}];
  assert.match(validateOrder(f), /分配数量/);
  f.items[0].allocations = [{demandId: '7', quantity: '1.0000'}];
  assert.match(validateOrder(f), /需求版本/);
  f.items[0].allocations = [{demandId: '7', quantity: '1.0000', demandVersion: 0}];
  assert.equal(validateOrder(f), undefined);
});

test('payload normalizes to four-decimal strings and keeps nulls as null', () => {
  const f = form();
  f.items[0].plannedQuantity = '3';
  f.items[0].purchasePrice = '2.5';
  const body = payload(f);
  assert.deepEqual(body.items[0], {
    id: undefined,
    version: undefined,
    skuId: '3',
    quantity: '3.0000',
    price: '2.5000',
    allocations: [],
  });
  assert.equal(body.purchaserId, null);
  assert.equal(body.plannedArrivalDate, null);
  assert.equal(body.remark, null);
  assert.equal('id' in body, false);
  assert.equal('version' in body, false);
});

test('payload keeps id and version only for retained lines', () => {
  const f = form();
  f.id = '9';
  f.version = 3;
  f.items = [
    {
      id: '11',
      version: 2,
      skuId: '3',
      plannedQuantity: '1.0000',
      purchasePrice: '2.0000',
      allocations: [{demandId: '7', quantity: '0.5000', demandVersion: 5}],
    },
    {skuId: '4', plannedQuantity: '1.0000', purchasePrice: '1.0000', allocations: []},
  ];
  const body = payload(f);
  assert.equal(body.id, '9');
  assert.equal(body.version, 3);
  assert.deepEqual(body.items[0], {
    id: '11',
    version: 2,
    skuId: '3',
    quantity: '1.0000',
    price: '2.0000',
    allocations: [{demandId: '7', quantity: '0.5000', demandVersion: 5}],
  });
  assert.deepEqual(body.items[1], {
    id: undefined,
    version: undefined,
    skuId: '4',
    quantity: '1.0000',
    price: '1.0000',
    allocations: [],
  });
});

// ------------------------------------------------------------------
// 分配集合（Q13 / A31）与单位（Q17 / A32）
// ------------------------------------------------------------------

test('new allocation carries the demand version and its unallocated remainder', () => {
  assert.deepEqual(newAllocation(demand()), {
    demandId: 7,
    skuId: '3',
    quantity: '6.0000',
    demandUnit: 'kg',
    demandVersion: 5,
    demandStatus: 'PENDING',
  });
  const blank = newAllocation({id: 8});
  assert.equal(blank.quantity, '0.0000');
  assert.equal(blank.demandVersion, 0);
  assert.deepEqual(newOrderItem(), {plannedQuantity: '1.0000', purchasePrice: '0.0000', allocations: []});
});

test('item totals and per-demand capacity follow the server allocation identity', () => {
  const item = {
    ...newOrderItem(),
    allocations: [
      {demandId: '7', quantity: '3.0000', demandVersion: 0},
      {demandId: '8', quantity: '2.0000', demandVersion: 0},
    ],
  };
  assert.equal(allocatedOnItem(item), '5.0000');
  assert.equal(allocatedOnItem(newOrderItem()), '0.0000');
  // 全库剩余 6 已扣掉本行旧分配 3 → 本行本次上限 6 + 3 = 9
  assert.equal(allocationCapacity(demand(), '3.0000').toFixed(4), '9.0000');
  assert.equal(allocationCapacity(demand(), null).toFixed(4), '6.0000');
  assert.equal(allocationCapacity({}, '').toFixed(4), '0.0000');
});

test('A31 editing one allocation leaves the sibling allocation untouched', () => {
  const item = {
    ...newOrderItem(),
    allocations: [
      {demandId: '7', quantity: '1.0000', demandVersion: 0},
      {demandId: '8', quantity: '2.0000', demandVersion: 0},
    ],
  };
  const next = item.allocations.map((row) => (String(row.demandId) === '7' ? {...row, quantity: '3.0000'} : row));
  assert.equal(next[1].quantity, '2.0000');
  assert.equal(allocatedOnItem({...item, allocations: next}), '5.0000');
  const removed = next.filter((row) => String(row.demandId) !== '8');
  assert.equal(removed.length, 1);
  assert.equal(hasAllocation({...item, allocations: removed}, '8'), false);
  assert.equal(hasAllocation({...item, allocations: removed}, '7'), true);
});

test('allocation identity compares numerically so id types never diverge', () => {
  const item = {...newOrderItem(), allocations: [{demandId: 7, quantity: '1.0000', demandVersion: 0}]};
  assert.equal(hasAllocation(item, '7'), true);
  assert.equal(hasAllocation(item, 7), true);
  assert.equal(hasAllocation(item, '8'), false);
  assert.equal(hasAllocation(newOrderItem(), '7'), false);
});

test('A32 unit mismatch blocks allocation instead of guessing a conversion', () => {
  assert.equal(unitMismatch({purchaseUnit: 'kg'}, demand({demandUnit: '箱'})), true);
  assert.equal(unitMismatch({purchaseUnit: 'kg'}, demand({demandUnit: 'kg'})), false);
  assert.equal(unitMismatch({}, demand()), false);
  assert.equal(unitMismatch({purchaseUnit: 'kg'}, {}), false);
});

// ------------------------------------------------------------------
// 收货确认（P24 / A25 / A26）
// ------------------------------------------------------------------

test('confirm lines default to the remaining quantity of every receipt line', () => {
  assert.deepEqual(newConfirmLines(receipt()), [
    {receiptItemId: 11, version: 0, receivedQuantity: '5.0000', actualWeight: null, weightSource: null, correctionReason: null},
    {receiptItemId: 12, version: 0, receivedQuantity: '2.0000', actualWeight: null, weightSource: null, correctionReason: null},
  ]);
  assert.equal(newConfirmLines({items: []}).length, 0);
});

test('only NON_STANDARD products are treated as weighed goods', () => {
  assert.equal(isNonStandard({productType: 'NON_STANDARD'}), true);
  assert.equal(isNonStandard({productType: 'STANDARD'}), false);
  assert.equal(isNonStandard(undefined), false);
});

test('confirm must cover every receipt line', () => {
  const r = receipt();
  assert.match(validateConfirm(r, []), /全部收货明细/);
  assert.match(validateConfirm(r, newConfirmLines(r).slice(0, 1)), /全部收货明细/);
  assert.match(validateConfirm({items: []}, []), /没有明细/);
});

test('A25 non-standard lines demand a manual actual weight', () => {
  const r = receipt();
  const lines = newConfirmLines(r);
  assert.match(validateConfirm(r, lines), /非标品必须录入实重/);
  lines[1].actualWeight = '2.0000';
  assert.match(validateConfirm(r, lines), /实重来源/);
  lines[1].weightSource = 'MANUAL';
  assert.equal(validateConfirm(r, lines), undefined);
});

test('standard lines may omit the weight but a supplied weight must be fixed-point', () => {
  const r = receipt();
  const lines = newConfirmLines(r);
  lines[1].actualWeight = '2.0000';
  lines[1].weightSource = 'MANUAL';
  lines[0].actualWeight = '5.00';
  assert.match(validateConfirm(r, lines), /实重必须为四位定点数/);
  lines[0].actualWeight = '5.0000';
  assert.equal(validateConfirm(r, lines), undefined);
  lines[0].actualWeight = null;
  assert.equal(validateConfirm(r, lines), undefined);
});

test('declared quantity shape and line identity are validated before submit', () => {
  const r = receipt();
  const lines = newConfirmLines(r);
  lines[1].actualWeight = '2.0000';
  lines[1].weightSource = 'MANUAL';
  lines[0].receivedQuantity = '1.00';
  assert.match(validateConfirm(r, lines), /声明数量/);
  lines[0].receivedQuantity = '-1.0000';
  assert.match(validateConfirm(r, lines), /声明数量/);
  lines[0].receivedQuantity = '0.0000';
  assert.equal(validateConfirm(r, lines), undefined);
  lines[0].receiptItemId = 99;
  assert.match(validateConfirm(r, lines), /不匹配/);
});

test('confirm payload normalizes quantities and derives the manual weight source', () => {
  const r = receipt();
  const lines = newConfirmLines(r);
  lines[0].receivedQuantity = '1';
  lines[1].actualWeight = '2.0000';
  lines[1].weightSource = 'MANUAL';
  const body = confirmPayload(r, lines);
  assert.equal(body.id, 1);
  assert.equal(body.version, 0);
  assert.deepEqual(body.items[0], {
    receiptItemId: 11,
    version: 0,
    receivedQuantity: '1.0000',
    actualWeight: null,
    weightSource: null,
    correctionReason: null,
  });
  assert.deepEqual(body.items[1], {
    receiptItemId: 12,
    version: 0,
    receivedQuantity: '2.0000',
    actualWeight: '2.0000',
    weightSource: 'MANUAL',
    correctionReason: null,
  });
});

test('A26 tolerance stays server-side while the client only warns on the remainder', () => {
  assert.match(toleranceHint({remainingQuantity: '5.0000'}), /剩余可收 5\.0000/);
  assert.equal(beyondRemaining({remainingQuantity: '5.0000'}, '6.0000'), true);
  assert.equal(beyondRemaining({remainingQuantity: '5.0000'}, '5.0000'), false);
  assert.equal(beyondRemaining({remainingQuantity: '5.0000'}, '1.00'), false);
  assert.equal(beyondRemaining({remainingQuantity: null}, '9.0000'), false);
});

// ------------------------------------------------------------------
// 错误码与枚举门禁
// ------------------------------------------------------------------

test('purchase errors resolve from body, data and response shapes', () => {
  assert.match(purchaseError({code: 40921}), /刷新/);
  assert.match(purchaseError({code: 40971}), /不做自动换算/);
  assert.match(purchaseError({code: 40082}), /可分配余量/);
  assert.match(purchaseError({code: 40989}), /超收容差/);
  assert.match(purchaseError({code: 40998}), /全部明细/);
  assert.match(purchaseError({code: 41005}), /库存余额/);
  assert.match(purchaseError({code: 41007}), /待入库收货单/);
  assert.match(purchaseError({code: 41008}), /已入库/);
  assert.match(purchaseError({data: {code: 40982}}), /状态/);
  assert.equal(purchaseError({msg: '业务提示'}), '业务提示');
  assert.equal(purchaseError({response: {data: {msg: '业务提示'}}}), '业务提示');
  assert.equal(purchaseError({code: 99999}), '操作失败，请重试');
  assert.equal(purchaseError({}), '操作失败，请重试');
});

test('purchase enums expose exactly the states the backend state machine allows', () => {
  assert.deepEqual(Object.keys(SCM_PURCHASE_STATUS_ENUM), [
    'DRAFT',
    'SUBMITTED',
    'PARTIALLY_RECEIVED',
    'RECEIVED',
    'SHORT_CLOSED',
    'CANCELLED',
  ]);
  assert.deepEqual(
    Object.values(SCM_PURCHASE_STATUS_ENUM).map((e) => e.value),
    Object.keys(SCM_PURCHASE_STATUS_ENUM)
  );
  assert.deepEqual(Object.keys(SCM_RECEIPT_STATUS_ENUM), ['DRAFT', 'CONFIRMED']);
  assert.deepEqual(Object.keys(SCM_DEMAND_STATUS_ENUM), ['PENDING', 'PARTIALLY_ALLOCATED', 'ALLOCATED']);
  assert.deepEqual(Object.keys(SCM_WAREHOUSE_STATUS_ENUM), ['ENABLED', 'DISABLED']);
  assert.deepEqual(Object.keys(SCM_RECEIPT_MODE_ENUM), ['DIRECT', 'WAREHOUSE_CONFIRM']);
  assert.deepEqual(Object.keys(SCM_PUTAWAY_STATUS_ENUM), ['PENDING', 'COMPLETED']);
  assert.deepEqual(Object.keys(SCM_PURCHASE_OPERATION_ENUM), [
    'DEMAND_GENERATE',
    'DEMAND_ALLOCATE',
    'CREATE',
    'UPDATE',
    'SUBMIT',
    'CANCEL',
    'SHORT_CLOSE',
    'DELETE',
    'RECEIPT_CREATE',
    'RECEIPT_UPDATE',
    'RECEIPT_CONFIRM',
    'RECEIPT_DELETE',
    'RECEIPT_PUTAWAY',
  ]);
  assert.deepEqual(Object.keys(SCM_PURCHASE_TABLE_ID), ['ORDER', 'RECEIPT', 'DEMAND', 'LOG', 'WAREHOUSE']);
  assert.equal(SCM_PURCHASE_TABLE_ID.ORDER, 'scm-purchase-order-table');
});

test('pruned legacy enums, resize plumbing and inventory wiring never reach the W5 sources', () => {
  const constants = code('../src/constants/business/scm/purchase-const.ts');
  assert.doesNotMatch(constants, /RECEIVE_FLAG_ENUM|SUPPLIER_STATUS_ENUM|INQUIRY_STATUS_ENUM|PURCHASE_ITEM_STATUS_ENUM/);
  assert.doesNotMatch(constants, /resizable|resizeColumn/);

  const pages = [
    'purchase-order-list.vue',
    'purchase-receipt-list.vue',
    'purchase-demand-list.vue',
    'purchase-log-list.vue',
    'warehouse-list.vue',
  ];
  for (const page of pages) {
    const source = code('../src/views/business/scm/purchase/' + page);
    assert.doesNotMatch(source, /resizable|resizeColumn|handleResizeColumn/, page + ' 残留 resize 管线');
    assert.match(source, /TableOperator/, page + ' 未使用 TableOperator');
    assert.match(source, /SCM_PURCHASE_TABLE_ID/, page + ' 未绑定表格 DOM id');
  }

  const editable = code('../src/views/business/scm/purchase/components/purchase-order-item-editable-table.vue');
  assert.doesNotMatch(editable, /resizable|resizeColumn|handleResizeColumn/);

  // W5 不实现库存：采购域不得出现库存读写入口（`OrderInventoryContract` 只有定义、零调用点）。
  const inventory = code('../src/api/business/scm/purchase-order-api.ts');
  assert.doesNotMatch(inventory, /inventory|stock/i);
});

test('B1 commands and action guards stay explicit in the frontend contract', () => {
  const receiptApi = code('../src/api/business/scm/purchase-receipt-api.ts');
  assert.match(receiptApi, /purchaseCommand<Receipt>\('\/scm\/purchase\/receipt\/putaway'/);

  const warehouseApi = code('../src/api/business/scm/warehouse-api.ts');
  assert.match(warehouseApi, /postRequest\('\/scm\/warehouse\/enable'/);
  assert.match(warehouseApi, /postRequest\('\/scm\/warehouse\/disable'/);

  const receiptList = code('../src/views/business/scm/purchase/purchase-receipt-list.vue');
  assert.match(
    receiptList,
    /record\.status === 'CONFIRMED'\s*&&\s*record\.receiptMode === 'WAREHOUSE_CONFIRM'\s*&&\s*record\.putawayStatus === 'PENDING'/
  );
  assert.match(receiptList, /scm:purchase:receipt:putaway/);

  const warehouseList = code('../src/views/business/scm/purchase/warehouse-list.vue');
  assert.match(warehouseList, /record\.status === 'ENABLED'[\s\S]*scm:warehouse:disable/);
  assert.match(warehouseList, /record\.status === 'DISABLED'[\s\S]*scm:warehouse:enable/);
});
