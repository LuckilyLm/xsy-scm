/*
 * 本文件由按域拆分的 E2E 用例合并而成：每个 test.describe 对应一份原文件，
 * 块内状态与钩子互不共享，执行顺序按来源文件排列。
 */
import {test, expect} from '../e2e/scm-test-base';
import {authenticate, accessibleName} from '../e2e/scm-e2e-account';
import {
  call,
  createReceipt,
  createSignedOrder,
  createWriteOff,
  financeDateRange,
  openFinanceHarness,
  type FinanceHarness,
  type Row,
  type SignedOrderFacts,
  createApprovedReturn,
  createPurchaseReceipt,
  stockSku,
  envelope,
} from '../e2e/scm-finance-fixtures';
import {createSku} from '../e2e/scm-delivery-fixtures';
import {createHmac, randomUUID} from 'node:crypto';

// ---------------------------------------------------------------- 来源：e2e-chains/adm-golden-chain-01.spec.ts
test.describe('adm-golden-chain-01', () => {
  /*
   * §15 E2E-01 黄金业务链（清单原文：客户 → 订单 → 确认 → 配送 → 正常签收 → NORMAL 应收 → 收款 → 核销 → 对账/利润）
   *
   * 与既有各域用例的区别：这里**一根线跑到底、后一步吃前一步的真实产物**，
   * 所以它证明的是「跨域闭环成立」，而不是每个页面各自能跑。
   * 数据一律经正式 API 产生（不直写库、不伪造余额/应收），页面断言用财务角色令牌。
   */

  test.describe.configure({mode: 'serial'});

  let harness: FinanceHarness;
  let facts: SignedOrderFacts;
  let receipt: Row;
  let writeOff: Row;
  const consoleErrors: string[] = [];

  test.beforeAll(async () => {
    harness = await openFinanceHarness();
  });

  test.afterAll(async () => {
    if (harness) await harness.close();
  });

  test('1 客户 → 订单 → 确认 → 配送 → 正常签收 → NORMAL 应收', async () => {
    facts = await createSignedOrder(harness, 'GOLDEN01', [{quantity: '10.0000', sortedQuantity: '10.0000', sortedResult: 'NORMAL'}]);
    // 签收形成应收，且方向是 NORMAL（不是红字）；金额由订单实发量算出
    expect(facts.receivable.entryType ?? 'NORMAL').toBe('NORMAL');
    expect(Number(facts.receivable.amount)).toBeGreaterThan(0);
    // 应收必须回指这张订单：列表行带 orderId，详情接口的主体嵌在 receivable 下一层
    expect(Number(facts.receivable.orderId), '应收列表行未回指订单').toBe(Number(facts.orderId));
    const detailMain = (facts.receivableDetail.receivable ?? facts.receivableDetail) as Row;
    expect(Number(detailMain.orderId ?? facts.orderId), '应收详情未回指订单').toBe(Number(facts.orderId));
    expect(Number(detailMain.openAmount), '新应收的未核销额应等于全额').toBe(Number(facts.receivable.amount));
  });

  test('2 收款 → 核销 → 应收未核销归零', async () => {
    const amount = String(facts.receivable.amount);
    receipt = await createReceipt(harness, facts.customerId, amount, 'GOLDEN01-R');
    expect(receipt.receiptId !== undefined, '收款单未落库').toBe(true);
    writeOff = await createWriteOff(harness, receipt.receiptId, [{targetId: facts.receivable.receivableId, amount}]);
    // /write-off/add 返回的是 {items: FinanceWriteOffVO[]}，核销号在 items[0] 上
    expect(Array.isArray(writeOff.items) && writeOff.items.length, '核销单未落库').toBe(1);
    expect(writeOff.items[0].writeOffId !== undefined, '核销明细未带回写核销号').toBe(true);

    const after = await call<Row>(harness.finance, 'get', `/scm/finance/receivable/${facts.receivable.receivableId}`);
    expect(Number(after.receivable?.openAmount ?? after.openAmount)).toBe(0);
    expect(Number(after.receivable?.writtenOffAmount ?? after.writtenOffAmount)).toBe(Number(amount));
  });

  test('3 应收页面：真实列表与明细抽屉都体现已核销', async ({page}) => {
    page.on('pageerror', (e) => consoleErrors.push(`receivables: ${e.message}`));
    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receivables');
    const table = page.locator('#scm-finance-receivable-table');
    await expect(table).toBeVisible();
    const row = table.locator('tr').filter({hasText: facts.receivable.receivableNo}).first();
    await expect(row).toBeVisible();
    await row.getByRole('button', {name: /明\s*细/}).click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '应收明细'}).first();
    await expect(drawer).toBeVisible();
    await expect(drawer).toContainText(facts.receivable.receivableNo);
    await expect(drawer).toContainText('核销');
    await page.screenshot({path: '../.runtime/golden01-receivable-detail.png', fullPage: true});
    await page.keyboard.press('Escape');
  });

  test('4 收款页面：该笔收款可见', async ({page}) => {
    page.on('pageerror', (e) => consoleErrors.push(`receipts: ${e.message}`));
    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receipts');
    const table = page.locator('#scm-finance-receipt-table');
    await expect(table).toBeVisible();
    await expect(table.locator('tr').filter({hasText: facts.customerName}).first()).toBeVisible();
    await page.screenshot({path: '../.runtime/golden01-receipts.png', fullPage: true});
  });

  test('5 客户对账单：冻结后本期应收=核销、期末净应收归零', async ({page}) => {
    page.on('pageerror', (e) => consoleErrors.push(`statement: ${e.message}`));
    await authenticate(page, harness.financeToken);
    await page.goto('/#/report/report-customer-statement');
    await expect(page.locator('.ant-table').first()).toBeVisible();

    const range = financeDateRange();
    const amount = Number(facts.receivable.amount);
    const statement = await call<Row>(harness.finance, 'post', '/scm/report/customer/statement/freeze', {
      settlementCustomerId: Number(facts.customerId),
      startDate: range.startDate,
      endDate: range.endDate,
    });
    expect(statement.id !== undefined, '对账单未冻结成功').toBe(true);
    // 闭环的会计证据：本期发生额、核销额、期末余额三者对得上
    expect(Number(statement.receivableIncrease), '对账单未计入本链应收').toBe(amount);
    expect(Number(statement.writeOffNet), '对账单核销净额与收款不符').toBe(amount);
    expect(Number(statement.closingReceivable), '全额核销后期初+本期-核销应归零').toBe(0);
    const history = await call<Row[]>(harness.finance, 'get', `/scm/report/customer/statement/history?settlementCustomerId=${facts.customerId}`);
    expect(
      (history as unknown as Row[]).some((r) => Number(r.id) === Number(statement.id)),
      '冻结出的对账单不在历史里'
    ).toBe(true);
    await page.screenshot({path: '../.runtime/golden01-statement.png', fullPage: true});
  });

  test('6 毛利分析页面出数且整链无浏览器异常', async ({page}) => {
    page.on('pageerror', (e) => consoleErrors.push(`profit: ${e.message}`));
    await authenticate(page, harness.financeToken);
    await page.goto('/#/report/report-finance-profit');
    const table = page.locator('#scm-report-finance-profit-table');
    await expect(table).toBeVisible();
    await page.screenshot({path: '../.runtime/golden01-profit.png', fullPage: true});
    expect(consoleErrors, `浏览器异常：${consoleErrors.join(' | ')}`).toEqual([]);
  });
});

// ---------------------------------------------------------------- 来源：e2e-chains/adm-golden-chain-02.spec.ts
test.describe('adm-golden-chain-02', () => {
  /*
   * §15 E2E-02 黄金业务链（清单原文：集团子客户 → 集团结算 → 收款 → 核销 → 客户对账）
   *
   * ADM-03 的核心不变量是「**下单的人**和**结账的人**可以不是同一个主体」：
   * 子客户自己下单，但应收必须记在集团结算主体名下，收款也只能由该主体交，
   * 最后对账单要按结算主体出，而不是按下单的子客户出。
   * 所以这条链每一步都同时盯「下单客户」与「结算客户」两个字段，
   * 只验金额会把「记错主体」这种最危险的错放过去。
   */

  test.describe.configure({mode: 'serial'});

  let harness: FinanceHarness;
  let parentCustomerId = '';
  let childCustomerId = '';
  let facts: SignedOrderFacts;
  let receipt: Row;

  /** 建一个客户并立刻置为合作中（`createLocatedCustomer` 的集团版：可指定结算主体）。 */
  const addCustomer = async (name: string, settleMode: string, settlementCustomerId?: number) => {
    const typeData = await call<Row>(harness.admin, 'post', '/scm/customer/type/option/list', {});
    const typeList = (Array.isArray(typeData) ? typeData : (typeData as any).options ?? []) as Row[];
    expect(typeList.length, '库里没有客户类型，无法建集团夹具').toBeGreaterThan(0);
    // CustomerValidator.validateParent 要求「上级客户的类型 typeCode 必须是 GROUP」，
    // 母客户挑错类型就会让子客户建不出来（40032 上级客户不正确）。
    const wantGroup = settleMode === 'GROUP' && !settlementCustomerId;
    const groupOption = typeList.find((t) => String(t.typeCode).toUpperCase() === 'GROUP' || String(t.name ?? t.typeName ?? '').includes('集团'));
    if (wantGroup) expect(groupOption !== undefined, '库里没有 GROUP 客户类型，建不出集团母客户').toBe(true);
    const chosen = wantGroup ? groupOption : typeList[0];
    // 客户编码由服务端生成（CUS + 6 位序号），创建载荷不再提交编码；后续按返回 id 使用。
    const id = Number(
      await call(harness.admin, 'post', '/scm/customer/add', {
        name,
        customerTypeId: Number(chosen.typeId ?? chosen.customerTypeId ?? chosen.id),
        settleMode,
        ...(settlementCustomerId ? {parentCustomerId: settlementCustomerId, settlementCustomerId} : {}),
        contactName: '集团夹具联系人',
        contactPhone: '13800000000',
        address: `${harness.runTag} 集团验收路 1 号`,
      })
    );
    const created = await call<Row>(harness.admin, 'get', `/scm/customer/detail/${id}`);
    await call(harness.admin, 'post', '/scm/customer/updateStatus', {customerId: id, version: created.version, status: 'COOPERATING'});
    return id;
  };

  test.beforeAll(async () => {
    harness = await openFinanceHarness();
  });

  test.afterAll(async () => {
    if (harness) await harness.close();
  });

  test('1 集团母客户 + 集团结算的子客户', async () => {
    parentCustomerId = String(await addCustomer(`${harness.runTag}集团母公司`, 'GROUP'));
    childCustomerId = String(await addCustomer(`${harness.runTag}集团子公司`, 'GROUP', Number(parentCustomerId)));

    const child = await call<Row>(harness.admin, 'get', `/scm/customer/detail/${childCustomerId}`);
    expect(String(child.settleMode)).toBe('GROUP');
    expect(Number(child.settlementCustomerId), '子客户没有挂上集团结算主体').toBe(Number(parentCustomerId));
  });

  test('2 子客户下单 → 应收记在集团结算主体名下', async () => {
    facts = await createSignedOrder(harness, 'GOLDEN02', [{quantity: '8.0000', sortedQuantity: '8.0000', sortedResult: 'NORMAL'}], childCustomerId);

    expect(Number(facts.receivable.amount) > 0).toBe(true);
    const detail = await call<Row>(harness.finance, 'get', `/scm/finance/receivable/${facts.receivable.receivableId}`);
    const main = (detail.receivable ?? detail) as Row;
    // 下单主体是子公司，结账主体必须是母公司 —— 记错主体就等于集团代付失效
    expect(Number(main.orderId), '应收未回指子客户的订单').toBe(Number(facts.orderId));
    expect(Number(main.settlementCustomerId ?? facts.customerId), '集团子客户的应收没有记到集团结算主体').toBe(Number(parentCustomerId));
  });

  test('3 集团统一收款 → 以该收款核销子客户形成的应收', async () => {
    const amount = String(facts.receivable.amount);
    receipt = await createReceipt(harness, parentCustomerId, amount, 'GOLDEN02-R');
    expect(receipt.receiptId !== undefined, '集团收款单未落库').toBe(true);

    const writeOff = await createWriteOff(harness, receipt.receiptId, [{targetId: facts.receivable.receivableId, amount}]);
    expect((writeOff.items as Row[]).length, '集团收款未能核销子客户应收').toBe(1);

    const after = await call<Row>(harness.finance, 'get', `/scm/finance/receivable/${facts.receivable.receivableId}`);
    expect(Number((after.receivable ?? after).openAmount), '集团收款核销后应收未归零').toBe(0);
  });

  test('4 对账单按集团结算主体出，且整链无浏览器异常', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const range = financeDateRange();
    const statement = await call<Row>(harness.finance, 'post', '/scm/report/customer/statement/freeze', {
      settlementCustomerId: Number(parentCustomerId),
      startDate: range.startDate,
      endDate: range.endDate,
    });
    expect(statement.id !== undefined, '集团对账单未能冻结').toBe(true);
    expect(Number(statement.receivableIncrease), '集团对账单未计入子客户形成的应收').toBe(Number(facts.receivable.amount));
    expect(Number(statement.closingReceivable), '集团统一收款核销后期末应归零').toBe(0);

    await authenticate(page, harness.adminToken);
    await page.goto('/#/report/report-customer-statement');
    await expect(page.locator('.ant-table').first()).toBeVisible();
    await page.screenshot({path: '../.runtime/golden02-group-statement.png', fullPage: true});
    expect(errors, `浏览器异常：${errors.join(' | ')}`).toEqual([]);
  });
});

// ---------------------------------------------------------------- 来源：e2e-chains/adm-golden-chain-03.spec.ts
test.describe('adm-golden-chain-03', () => {
  /*
   * §15 E2E-03 黄金业务链（清单原文：订单 → 退货审批 → 实物接收 → SALES_RETURN_IN → RED → 退款 → 报表）
   *
   * 与 E2E-01 同理：一条 serial 链，后一步吃前一步的真实产物，
   * 并在「退货回库」这一步同时验到库存事实（流水方向 + 余额增加）与财务事实（红字），
   * 因为 ADM-02 最容易出的错就是「退货只改财务、不回库」或「回库了但方向写反」。
   */

  test.describe.configure({mode: 'serial'});

  let harness: FinanceHarness;
  let facts: SignedOrderFacts;
  let returnFacts: {returnId: number; refund: Row; approved: Row};
  let balanceBefore = '0.0000';
  const consoleErrors: string[] = [];
  const RETURN_QTY = '4.0000';

  test.beforeAll(async () => {
    harness = await openFinanceHarness();
  });

  test.afterAll(async () => {
    if (harness) await harness.close();
  });

  test('1 订单 → 签收 → NORMAL 应收（退货前基线）', async () => {
    facts = await createSignedOrder(harness, 'GOLDEN03', [{quantity: '10.0000', sortedQuantity: '10.0000', sortedResult: 'NORMAL'}]);
    const skuId = Number(facts.skuIds[0]);
    const before = await call<Row>(harness.admin, 'post', '/scm/inventory/balance/query', {
      pageNum: 1,
      pageSize: 100,
      warehouseId: harness.warehouseId,
      skuId,
    });
    balanceBefore = String((before.list as Row[])?.[0]?.quantity ?? '0.0000');
  });

  test('2 退货审批 → RED 红字挂在同一张应收上', async () => {
    returnFacts = await createApprovedReturn(harness, facts, RETURN_QTY, 'GOLDEN03');
    expect(returnFacts.approved.status).toBe('APPROVED');
    const detail = await call<Row>(harness.finance, 'get', `/scm/finance/receivable/${facts.receivable.receivableId}`);
    const main = (detail.receivable ?? detail) as Row;
    const reds = (detail.redEntries ?? []) as Row[];
    expect(reds.length, '审批后未生成红字').toBe(1);
    expect(Number(main.netAmount) < Number(main.amount), '红字未冲减净应收').toBe(true);
  });

  test('3 实物接收 → 库存流水方向是 SALES_RETURN_IN 且余额真的增加', async () => {
    const skuId = Number(facts.skuIds[0]);
    const items = (returnFacts.approved.items ?? []) as Row[];
    const returnItemId = Number(items[0]?.returnItemId ?? items[0]?.id);
    await call(harness.admin, 'post', '/scm/order/return/receive', {
      returnId: returnFacts.returnId,
      version: returnFacts.approved.version,
      warehouseId: harness.warehouseId,
      items: [{returnItemId, quantity: RETURN_QTY, disposition: 'RETURN_TO_STOCK'}],
    });

    const movements = await call<Row>(harness.admin, 'post', '/scm/inventory/movement/query', {
      pageNum: 1,
      pageSize: 100,
      warehouseId: harness.warehouseId,
      skuId,
    });
    const inbound = (movements.list as Row[]).filter((m) => m.movementType === 'SALES_RETURN_IN');
    expect(inbound.length, '退货未产生 SALES_RETURN_IN 入库流水').toBeGreaterThanOrEqual(1);
    expect(Number(inbound[0].quantity) > 0, '退货入库流水数量必须为正数（方向由类型表达）').toBe(true);

    const after = await call<Row>(harness.admin, 'post', '/scm/inventory/balance/query', {
      pageNum: 1,
      pageSize: 100,
      warehouseId: harness.warehouseId,
      skuId,
    });
    const quantity = Number((after.list as Row[])?.[0]?.quantity ?? 0);
    expect(quantity).toBe(Number(balanceBefore) + Number(RETURN_QTY));
  });

  test('4 退款登记完成', async () => {
    const completed = await call<Row>(harness.admin, 'post', '/scm/order/refund/complete', {
      refundId: Number(returnFacts.refund.refundId),
      version: returnFacts.refund.version,
      externalReference: `${harness.runTag}-GOLDEN03-REFUND`,
    });
    expect(['COMPLETED', 'SUCCEEDED'].includes(String(completed.status)), `退款登记后状态异常：${completed.status}`).toBe(true);
  });

  test('5 退货单页面与报表：真实页面体现该退货，且整链无浏览器异常', async ({page}) => {
    page.on('pageerror', (e) => consoleErrors.push(`return-page: ${e.message}`));
    // 退货单列表用管理员令牌看：harness 的 sales 角色**没有**该菜单，深链会落到 404
    // （这正是 scm-data-scope 在证明的 fail-closed 行为，不是页面坏了）。
    // 跨域链里「看页面」用有权令牌，「证权限」另有专门用例，两件事不混在一起。
    await authenticate(page, harness.adminToken);
    await page.goto('/#/order/order-return-list');
    const table = page.locator('#order-return-table');
    await expect(table).toBeVisible();
    await page.screenshot({path: '../.runtime/golden03-return-list.png', fullPage: true});

    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receivables');
    const receivableTable = page.locator('#scm-finance-receivable-table');
    await expect(receivableTable).toBeVisible();
    const row = receivableTable.locator('tr').filter({hasText: facts.receivable.receivableNo}).first();
    await expect(row).toBeVisible();
    await row.getByRole('button', {name: /明\s*细/}).click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '应收明细'}).first();
    await expect(drawer).toContainText('红字');
    await page.screenshot({path: '../.runtime/golden03-receivable-red.png', fullPage: true});

    const range = financeDateRange();
    const statement = await call<Row>(harness.finance, 'post', '/scm/report/customer/statement/freeze', {
      settlementCustomerId: Number(facts.customerId),
      startDate: range.startDate,
      endDate: range.endDate,
    });
    expect(Number(statement.receivableRed), '对账单未计入红字冲减').toBeGreaterThan(0);
    expect(consoleErrors, `浏览器异常：${consoleErrors.join(' | ')}`).toEqual([]);
  });
});

// ---------------------------------------------------------------- 来源：e2e-chains/adm-golden-chain-04.spec.ts
test.describe('adm-golden-chain-04', () => {
  /*
   * §15 E2E-04 黄金业务链（清单原文：采购需求 → 净需求预览 → 冻结批次 → 生成需求 → 采购 → 收货 → 入库 → 应付 → 付款）
   *
   * 本轮覆盖的是「采购 → 收货 → 入库 → 应付 → 付款」这段可自动化的实链，
   * 并同时验**库存事实**（入库流水 + 余额增加）与**财务事实**（应付生成、付款核销后余额归零）——
   * 只验其中一边就会漏掉 ADM-04 最容易的错：收货了但没产生应付，或应付对了库存没动。
   *
   * 未覆盖：净需求预览 / 冻结批次 / 生成需求 这三步属 ADM-05，
   * 其页面断言目前红在裁决项 17（D-34），故不在此链里重复立案。
   */

  test.describe.configure({mode: 'serial'});

  const PLANNED = '20.0000';
  const RECEIVED = '20.0000';

  let harness: FinanceHarness;
  let purchase: {supplierId: string; skuId: string; purchaseOrder: Row; receipt: Row; payables: Row[]};
  let payable: Row;
  let balanceBefore = 0;
  const consoleErrors: string[] = [];

  test.beforeAll(async () => {
    harness = await openFinanceHarness();
  });

  test.afterAll(async () => {
    if (harness) await harness.close();
  });

  test('1 采购单 → 收货确认 → 产生库存入库事实', async () => {
    // createPurchaseReceipt 每次都新建一个 SKU，所以它的期初余额必然是 0，
    // 收货确认后余额应精确等于本次收货量（不需要"前后差值"这种弱断言）。
    purchase = await createPurchaseReceipt(harness, 'GOLDEN04', PLANNED, RECEIVED);
    const after = await call<Row>(harness.admin, 'post', '/scm/inventory/balance/query', {
      pageNum: 1,
      pageSize: 100,
      warehouseId: harness.warehouseId,
      skuId: Number(purchase.skuId),
    });
    const rows = (after.list ?? []) as Row[];
    expect(rows.length, '收货后没有余额行').toBe(1);
    expect(Number(rows[0].quantity), '余额不等于本次收货量').toBe(Number(RECEIVED));

    const movements = await call<Row>(harness.admin, 'post', '/scm/inventory/movement/query', {
      pageNum: 1,
      pageSize: 100,
      warehouseId: harness.warehouseId,
      skuId: Number(purchase.skuId),
    });
    const inbound = (movements.list as Row[]).filter((m) => String(m.movementType).endsWith('_IN'));
    expect(inbound.length, '收货没有产生入库流水').toBeGreaterThanOrEqual(1);
  });

  test('2 收货形成应付，方向与金额都是正数', async () => {
    const range = financeDateRange();
    const queried = await call<Row>(harness.finance, 'post', '/scm/finance/payable/query', {
      pageNum: 1,
      pageSize: 50,
      startDate: range.startDate,
      endDate: range.endDate,
      purchaseOrderId: Number(purchase.purchaseOrder.id ?? purchase.purchaseOrder.purchaseOrderId),
    });
    const rows = (queried.list ?? []) as Row[];
    payable = rows.find((row) => String(row.entryType ?? 'NORMAL') === 'NORMAL') ?? rows[0];
    expect(payable !== undefined, '收货后未生成应付').toBe(true);
    expect(Number(payable.amount) > 0, '应付金额本身必须是正数（方向由类型表达）').toBe(true);
    expect(Number(payable.openAmount), '新应付的未付额应等于全额').toBe(Number(payable.amount));
  });

  test('3 付款 → 核销 → 应付未付额归零', async () => {
    const amount = String(payable.amount);
    const payment = await call<Row>(harness.finance, 'post', '/scm/finance/payment/add', {
      counterpartyType: 'SUPPLIER',
      counterpartyId: Number(purchase.supplierId),
      amount,
      method: 'BANK_TRANSFER',
      paidAt: new Date().toISOString(),
      externalReference: `${harness.runTag}-GOLDEN04-PAY`,
      remark: `${harness.runTag} GOLDEN04`,
    });
    const paymentId = Number(payment.paymentId ?? payment.id);
    expect(paymentId > 0, '付款单未落库').toBe(true);

    await call(harness.finance, 'post', '/scm/finance/write-off/add', {
      sourceType: 'PAYMENT',
      sourceId: paymentId,
      items: [{targetId: Number(payable.payableId), amount}],
    });
    const after = await call<Row>(harness.finance, 'get', `/scm/finance/payable/${payable.payableId}`);
    const main = (after.payable ?? after) as Row;
    expect(Number(main.openAmount), '全额付款核销后应付未付额应归零').toBe(0);
  });

  test('4 采购收货与应付页面都体现这笔事实，且整链无浏览器异常', async ({page}) => {
    page.on('pageerror', (e) => consoleErrors.push(`page: ${e.message}`));
    await authenticate(page, harness.adminToken);
    await page.goto('/#/purchase/purchase-receipt-list');
    await expect(page.locator('.ant-table').first()).toBeVisible();
    await page.screenshot({path: '../.runtime/golden04-receipt-list.png', fullPage: true});

    await page.goto('/#/finance/payables');
    const table = page.locator('.ant-table').first();
    await expect(table).toBeVisible();
    const range = financeDateRange();
    expect(range.startDate < range.endDate, '对账期间窗口不成立').toBe(true);
    await page.screenshot({path: '../.runtime/golden04-payables.png', fullPage: true});
    expect(consoleErrors, `浏览器异常：${consoleErrors.join(' | ')}`).toEqual([]);
  });
});

// ---------------------------------------------------------------- 来源：e2e-chains/adm-golden-chain-05.spec.ts
test.describe('adm-golden-chain-05', () => {
  /*
   * §15 E2E-05 黄金业务链（清单原文：活动/券 → 发券 → 订单确认冻结优惠 → 配送 → 签收核券 → 净应收 → 利润）
   *
   * ADM-12 这条链的要害是「券什么时候真正生效、生效在哪个数上」：
   *   确认订单时冻结优惠 → 应收必须按**净额**记；
   *   签收时核销券 → 券实例状态必须离开「可用」；
   *   全程不能出现「毛额记应收、净额记利润」这种两边不一致。
   * 所以每一步都同时盯 券实例状态 / 订单毛额 / 应收净额 三处。
   *
   * 契约提醒（曾把测试自己的错当成产品缺陷）：券状态取值是 `ACTIVE` / `STOPPED`，不是 `ENABLED`；
   * `PromotionCouponService.updateStatus` 对不认识的目标状态、以及「要激活但已经是 ACTIVE」都抛 41327，
   * 且 `version` 必须从 detail 带回。
   */

  test.describe.configure({mode: 'serial'});

  const COUPON_VALUE = '5.0000';

  let harness: FinanceHarness;
  let couponId = 0;
  let instanceId = 0;
  let customerId = '';

  /** 该端点可能直接回数组，也可能回 {list:[]} / {options:[]}，三种都吃下。 */
  const instances = async (): Promise<Row[]> => {
    const rows: any = await call(harness.admin, 'get', `/scm/promotion/coupon/instances?customerId=${customerId}`);
    if (Array.isArray(rows)) return rows as Row[];
    return (rows?.list ?? rows?.options ?? []) as Row[];
  };

  const instanceState = async () => (await instances()).find((row) => Number(row.instanceId ?? row.id) === instanceId);

  test.beforeAll(async () => {
    harness = await openFinanceHarness();
  });

  test.afterAll(async () => {
    if (harness) await harness.close();
  });

  test('1 建一张满减券并激活', async () => {
    const validFrom = new Date(Date.now() - 86400000).toISOString();
    const validTo = new Date(Date.now() + 30 * 86400000).toISOString();
    couponId = Number(
      await call(harness.admin, 'post', '/scm/promotion/coupon/save', {
        couponCode: `${harness.runTag}-C5`,
        couponName: `${harness.runTag} 满减券`,
        discountType: 'AMOUNT',
        discountValue: COUPON_VALUE,
        minOrderAmount: '10.0000',
        validFrom,
        validTo,
        remark: `${harness.runTag} GOLDEN05`,
      })
    );
    expect(couponId > 0).toBe(true);

    const created = await call<Row>(harness.admin, 'get', `/scm/promotion/coupon/${couponId}`);
    if (String(created.status) !== 'ACTIVE') {
      await call(harness.admin, 'post', `/scm/promotion/coupon/${couponId}/status`, {id: couponId, version: created.version, status: 'ACTIVE'});
    }
    const enabled = await call<Row>(harness.admin, 'get', `/scm/promotion/coupon/${couponId}`);
    expect(String(enabled.status)).toBe('ACTIVE');
  });

  test('2 发券给下单客户，券实例先是可用', async () => {
    const plain = await createSignedOrder(harness, 'GOLDEN05A', [{quantity: '10.0000', sortedQuantity: '10.0000', sortedResult: 'NORMAL'}]);
    customerId = String(plain.customerId);
    expect(Number(plain.receivable.amount) > Number(COUPON_VALUE)).toBe(true);

    await call(harness.admin, 'post', '/scm/promotion/coupon/issue', {couponId, customerId: Number(customerId), quantity: 1});
    // 实例按 couponId 认，不按 id 字段名猜：Instance VO 的键与外层 VO 不同。
    const mine = (await instances()).filter((row) => Number(row.couponId) === couponId);
    expect(mine.length, '发券后该客户名下查不到这张券的实例').toBe(1);
    instanceId = Number(mine[0].id ?? mine[0].instanceId ?? mine[0].couponInstanceId);
    expect(instanceId > 0, `券实例 id 取不到，实际字段：${Object.keys(mine[0]).join(',')}`).toBe(true);
    expect(String(mine[0].status)).toBe('AVAILABLE');
  });

  test('3 带券下单：确认冻结优惠，应收按净额记', async () => {
    const withCoupon = await createSignedOrder(
      harness,
      'GOLDEN05B',
      [{quantity: '10.0000', sortedQuantity: '10.0000', sortedResult: 'NORMAL'}],
      customerId,
      {couponInstanceId: instanceId}
    );

    // 实测口径：应收的 amount 本身就已经是**扣券后的净额**（35 毛额 - 5 券 = 30），
    // 所以「券生效」要拿订单结算金额与应收金额比，而不是在应收上再减一次券值。
    const settlement = Number(withCoupon.orderDetail.settlementTotalAmount);
    const gross = Number(withCoupon.receivable.amount);
    const detail = await call<Row>(harness.finance, 'get', `/scm/finance/receivable/${withCoupon.receivable.receivableId}`);
    const main = (detail.receivable ?? detail) as Row;
    const net = Number(main.netAmount);
    expect(settlement, '订单结算金额应等于商品毛额（券作用在应收侧）').toBeGreaterThan(gross);
    expect(gross, `券没有在应收上生效：结算 ${settlement} / 应收 ${gross}`).toBe(settlement - Number(COUPON_VALUE));
    expect(net, '无红字时净额应等于应收本身').toBe(gross);
  });

  test('4 签收后券被核销：实例不再是 AVAILABLE', async () => {
    const mine = (await instances()).filter((row) => Number(row.couponId) === couponId);
    expect(mine.length).toBe(1);
    expect(String(mine[0].status)).not.toBe('AVAILABLE');
  });

  test('5 毛利分析页面渲染，整链无浏览器异常', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await authenticate(page, harness.adminToken);
    await page.goto('/#/report/report-finance-profit');
    await expect(page.locator('#scm-report-finance-profit-table')).toBeVisible();
    await page.screenshot({path: '../.runtime/golden05-profit.png', fullPage: true});
    expect(errors, `浏览器异常：${errors.join(' | ')}`).toEqual([]);
  });
});

// ---------------------------------------------------------------- 来源：e2e-chains/adm-golden-chain-06.spec.ts
test.describe('adm-golden-chain-06', () => {
  /*
   * §15 E2E-06 黄金业务链（清单原文：满赠 → 冻结赠品 → 分拣/小票 → 发车赠品出库 → 赠品成本 → 利润）
   *
   * 满赠最容易出错的两处：赠品被算进应收（客户没买的东西却挂了账），
   * 以及赠品随发车出库却没有对应的出库流水（库存与成本对不上）。
   * 所以这条链盯三件事：应收不含赠品、赠品有 `PROMOTION_GIFT_OUT` 流水、毛利页能出数。
   *
   * 契约（曾把测试自己的错当成产品缺陷）：
   *   `FULL_GIFT` 的 rule 只允许 `thresholdAmount` / `giftSkuId` / `giftQuantity` 三个键，
   *   多一个键 `PromotionRuleValidator.requireOnly` 就拒收；活动状态取值是 `ACTIVE` / `STOPPED`。
   *   赠品行**不复制成订单行**（`SortingTaskItemVO` 类说明）：它只以赠品权益的身份出现在
   *   分拣任务的合并视图里（`sourceType = PROMOTION_GIFT`），所以夹具按 index 认行会错位（D-41），
   *   现在按 skuId 认。
   */

  test.describe.configure({mode: 'serial'});

  const GIFT_QTY = '2.0000';
  const THRESHOLD = '10.0000';
  const GIFT_STOCK = '30.0000';

  let harness: FinanceHarness;
  let giftSkuId = 0;
  let activityId = 0;
  let giftBalanceBefore = '';
  let facts: SignedOrderFacts;

  const activityRow = (detail: Row): Row => (detail.activity ?? detail) as Row;

  const giftBalance = async (): Promise<string> => {
    const rows = await call<Row>(harness.admin, 'post', '/scm/inventory/balance/query', {
      pageNum: 1,
      pageSize: 100,
      warehouseId: harness.warehouseId,
      skuId: giftSkuId,
    });
    return String((rows.list as Row[])?.[0]?.quantity ?? '0.0000');
  };

  /** 活动是**库级共享状态**：留下 ACTIVE 的满赠，同库其它链的订单就会长出赠品行。 */
  const stopActivity = async () => {
    const detail = activityRow(await call<Row>(harness.admin, 'get', `/scm/promotion/activity/${activityId}`));
    if (String(detail.status) === 'STOPPED') return;
    await call(harness.admin, 'post', `/scm/promotion/activity/${activityId}/status`, {
      id: activityId,
      version: detail.version,
      status: 'STOPPED',
    });
  };

  test.beforeAll(async () => {
    harness = await openFinanceHarness();
  });

  test.afterAll(async () => {
    if (!harness) return;
    // 停不掉就让它变红，不能静默把 ACTIVE 活动留给下一次运行。
    try {
      if (activityId > 0) await stopActivity();
    } finally {
      await harness.close();
    }
  });

  test('1 赠品备货 + 满赠活动上线', async () => {
    giftSkuId = Number(await createSku(harness.admin, harness.runTag, 'G6G'));
    await stockSku(harness, 'G6S', String(giftSkuId), GIFT_STOCK);
    giftBalanceBefore = await giftBalance();
    expect(Number(giftBalanceBefore), '赠品没备进夹具仓，发车出库必然无货').toBeGreaterThan(Number(GIFT_QTY));

    activityId = Number(
      await call(harness.admin, 'post', '/scm/promotion/activity/save', {
        activityCode: `${harness.runTag}-A6`,
        activityName: `${harness.runTag} 满赠活动`,
        activityType: 'FULL_GIFT',
        priority: 10,
        validFrom: new Date(Date.now() - 86400000).toISOString(),
        validTo: new Date(Date.now() + 30 * 86400000).toISOString(),
        rule: {thresholdAmount: THRESHOLD, giftSkuId, giftQuantity: GIFT_QTY},
        remark: `${harness.runTag} GOLDEN06`,
      })
    );
    expect(activityId > 0).toBe(true);

    const created = activityRow(await call<Row>(harness.admin, 'get', `/scm/promotion/activity/${activityId}`));
    if (String(created.status) !== 'ACTIVE') {
      await call(harness.admin, 'post', `/scm/promotion/activity/${activityId}/status`, {
        id: activityId,
        version: created.version,
        status: 'ACTIVE',
      });
    }
    const enabled = activityRow(await call<Row>(harness.admin, 'get', `/scm/promotion/activity/${activityId}`));
    expect(String(enabled.status), '满赠活动没能上线').toBe('ACTIVE');
  });

  test('2 下单即冻结赠品权益，但应收里不含赠品', async () => {
    facts = await createSignedOrder(harness, 'GOLDEN06A', [{quantity: '10.0000', sortedQuantity: '10.0000', sortedResult: 'NORMAL'}]);
    const settlement = Number(facts.orderDetail.settlementTotalAmount);
    expect(settlement, `订单额低于满赠门槛 ${THRESHOLD}，本链夹具失效`).toBeGreaterThanOrEqual(Number(THRESHOLD));

    const gifts = (facts.orderDetail.gifts ?? []) as Row[];
    const mine = gifts.filter((gift) => Number(gift.skuId) === giftSkuId);
    expect(mine.length, `满赠未冻结赠品权益，订单里的权益：${JSON.stringify(gifts)}`).toBe(1);
    expect(Number(mine[0].quantity)).toBe(Number(GIFT_QTY));
    expect(Number(mine[0].activityId), '赠品权益没有回指到满赠活动').toBe(activityId);

    // 赠品不复制成订单行，也不进应收：客户没买的东西不能挂账。
    const items = (facts.orderDetail.items ?? []) as Row[];
    expect(
      items.some((item) => Number(item.skuId) === giftSkuId),
      '赠品被当成订单行了'
    ).toBe(false);
    expect(Number(facts.receivable.amount), '应收应当只有商品结算额').toBe(settlement);
  });

  test('3 发车产生 PROMOTION_GIFT_OUT 流水并扣减赠品库存', async () => {
    const movements = await call<Row>(harness.admin, 'post', '/scm/inventory/movement/query', {
      pageNum: 1,
      pageSize: 100,
      warehouseId: harness.warehouseId,
      skuId: giftSkuId,
    });
    const out = ((movements.list ?? []) as Row[]).filter((m) => m.movementType === 'PROMOTION_GIFT_OUT');
    expect(out.length, '签收发车后赠品没有 PROMOTION_GIFT_OUT 流水').toBeGreaterThanOrEqual(1);
    const total = out.reduce((sum, m) => sum + Number(m.quantity), 0);
    expect(total, `赠品出库合计 ${total} 应等于冻结的 ${GIFT_QTY}`).toBe(Number(GIFT_QTY));
    // 溯源锚点是赠品权益 id（不是订单行 id），成本按当时的移动加权均价记进流水。
    expect(String(out[0].sourceDocumentType), '赠品出库流水来源类型应指向赠品权益').toBe('ORDER_PROMOTION_GIFT');
    expect(Number(out[0].sourceDocumentItemId) > 0).toBe(true);
    expect(Number(out[0].unitCost), '赠品出库未记成本，毛利会把赠品当成免费货').toBeGreaterThan(0);
    expect(Number(out[0].afterQuantity), '流水里的前后量必须自洽').toBeCloseTo(Number(out[0].beforeQuantity) - Number(out[0].quantity), 4);

    const after = await giftBalance();
    expect(Number(after), '赠品出库未扣库存').toBeCloseTo(Number(giftBalanceBefore) - Number(GIFT_QTY), 4);
  });

  test('4 毛利分析页渲染，整链无浏览器异常', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(`golden06: ${e.message}`));
    page.on('console', (message) => {
      if (message.type() === 'error') errors.push(`golden06-console: ${message.text()}`);
    });
    await authenticate(page, harness.adminToken);
    await page.goto('/#/report/report-finance-profit');
    await expect(page.locator('#scm-report-finance-profit-table')).toBeVisible();
    await page.screenshot({path: '../.runtime/golden06-profit.png', fullPage: true});
    expect(errors, `浏览器异常：${errors.join(' | ')}`).toEqual([]);
  });
});

// ---------------------------------------------------------------- 来源：e2e-chains/adm-golden-chain-07.spec.ts
test.describe('adm-golden-chain-07', () => {
  /*
   * §15 E2E-07 黄金业务链（清单原文：在线充值 → Receipt → 钱包 CREDIT → 余额支付 → 无 Receipt → BALANCE 核销）
   *
   * 这条链守的是「钱的两种身份不能混」：
   *   充值是**真收钱** —— 必须留下一张财务 Receipt，并把钱包记一笔 CREDIT；
   *   余额支付是**花已经收过的钱** —— 绝不能再开一张 Receipt（否则收入被重复计一次），
   *   它只能以 BALANCE 身份去核销应收。
   * 只验钱包不验 Receipt 数量，就测不出「重复计收」这个最危险的不变量破坏。
   *
   * 关键契约：本链显式使用 `DELAYED` 模拟场景，把渠道确认与创建意图分成两步。
   * `/scm/balance/recharge/create` 只建立待支付意图；验签成功的渠道成功回调在同一事务中
   * 派生 Finance Receipt 与钱包 CREDIT。缺省 `SUCCESS` 场景会在创建时同步走完这条成功链，
   * 不能用来验证「渠道确认前不得到账」。
   * 回调报文用 `X-Scm-Mock-Signature` 携带 `HmacSHA256(rawBody, secret)` 的小写十六进制，
   * secret 取 `scm.payment.mock.secret`（dev 默认 `scm-local-mock-secret`）；
   * 签名不匹配时业务侧不会抛错，而是如实落一条 REJECTED 事件 —— 所以这里必须自己算签名，
   * 不能指望"随便发一发就算渠道回调"。
   */

  test.describe.configure({mode: 'serial'});

  const RECHARGE = '100.0000';
  const MOCK_SECRET = 'scm-local-mock-secret';

  let harness: FinanceHarness;
  let facts: SignedOrderFacts;
  let receiptsBefore = 0;
  let receiptsAtPayment = 0;
  let rechargeIntent: Row;

  test.beforeAll(async () => {
    harness = await openFinanceHarness();
  });

  test.afterAll(async () => {
    if (harness) await harness.close();
  });

  const receiptRows = async () => {
    const range = financeDateRange();
    const list = await call<Row>(harness.finance, 'post', '/scm/finance/receipt/query', {
      pageNum: 1,
      pageSize: 100,
      customerId: Number(facts.customerId),
      startDate: range.startDate,
      endDate: range.endDate,
    });
    return (list.list ?? []) as Row[];
  };

  const writeOffRows = async () => {
    const range = financeDateRange();
    const rows = await call<Row>(harness.finance, 'post', '/scm/finance/write-off/query', {
      pageNum: 1,
      pageSize: 100,
      targetId: Number(facts.receivable.receivableId),
      startDate: range.startDate,
      endDate: range.endDate,
    });
    return (rows.list ?? []) as Row[];
  };

  const wallet = async () => {
    const rows = await call<Row>(harness.admin, 'post', '/scm/balance/query', {pageNum: 1, pageSize: 20, customerId: Number(facts.customerId)});
    const list = (rows.list ?? []) as Row[];
    return list.length ? Number(list[0].availableBalance ?? 0) : 0;
  };

  /** 用与报文**逐字节相同**的字符串去算签名，否则一定验签失败。 */
  const deliverMockCallback = async (payload: Record<string, unknown>) => {
    const rawBody = JSON.stringify(payload);
    const signature = createHmac('sha256', MOCK_SECRET).update(rawBody, 'utf8').digest('hex');
    const response = await harness.admin.post('/scm/payment/callback/mock', {
      data: rawBody,
      headers: {
        'Content-Type': 'application/json',
        'X-Scm-Mock-Signature': signature,
        'X-Scm-Mock-Event-Id': String(payload.eventId),
        'Idempotency-Key': randomUUID(),
      },
    });
    const body = await response.json();
    expect(body.code, `回调投递失败：${body.code} ${body.msg}`).toBe(0);
    return body.data as Row;
  };

  test('1 订单 → 签收 → NORMAL 应收（余额支付要核销的对象）', async () => {
    facts = await createSignedOrder(harness, 'GOLDEN07', [{quantity: '10.0000', sortedQuantity: '10.0000', sortedResult: 'NORMAL'}]);
    expect(Number(facts.receivable.amount) > 0).toBe(true);
    receiptsBefore = (await receiptRows()).length;
    expect(await wallet(), '新客户的钱包期初余额应为 0').toBe(0);
  });

  test('2 延迟充值只建待支付意图，渠道确认前没有 Receipt 或钱包 CREDIT', async () => {
    rechargeIntent = await call<Row>(harness.admin, 'post', '/scm/balance/recharge/create', {
      customerId: Number(facts.customerId),
      amount: RECHARGE,
      provider: 'MOCK',
      mockScenario: 'DELAYED',
      remark: `${harness.runTag} GOLDEN07 充值`,
    });
    expect(String(rechargeIntent.status)).toBe('PENDING');
    expect(await wallet(), '还没收到渠道确认就入账 = 凭空生钱').toBe(0);
    expect((await receiptRows()).length, '渠道成功前不能登记真实收款').toBe(receiptsBefore);
  });

  test('3 合法成功回调同时形成 Receipt 与钱包 CREDIT，重复回调不重复入账', async () => {
    const intent = await call<Row>(harness.admin, 'get', `/scm/payment/intent/detail/${rechargeIntent.id}`);
    const transaction = (intent.transactions as Row[])[0];
    expect(transaction?.providerTransactionNo, '充值意图没有可供回调匹配的渠道交易号').toBeTruthy();
    const eventId = `${harness.runTag}-GOLDEN07-SUCCESS`;
    const payload = {
      eventId,
      eventType: 'PAYMENT_SUCCEEDED',
      providerTransactionNo: transaction.providerTransactionNo,
      amount: RECHARGE,
    };
    const applied = await deliverMockCallback(payload);
    expect(String(applied.processStatus)).toBe('APPLIED');
    expect(Number(applied.transactionId)).toBe(Number(transaction.id));

    const receipts = await receiptRows();
    expect(receipts.length, '渠道成功必须登记一张真实收款').toBe(receiptsBefore + 1);
    const rechargeReceipt = receipts.find((row) => String(row.method) === 'ONLINE_PAYMENT') as Row;
    expect(rechargeReceipt !== undefined, '充值没有形成 ONLINE_PAYMENT 收款').toBe(true);
    expect(Number(rechargeReceipt.amount)).toBe(Number(RECHARGE));
    expect(Boolean(rechargeReceipt.walletFunding), '充值收款必须标记为钱包权益来源').toBe(true);
    expect(await wallet(), '渠道成功后钱包没有增加权益').toBe(Number(RECHARGE));

    await deliverMockCallback(payload);
    expect((await receiptRows()).length, '重复回调产生了第二张收款').toBe(receiptsBefore + 1);
    expect(await wallet(), '重复回调产生了第二笔钱包 CREDIT').toBe(Number(RECHARGE));
  });

  test('4 余额支付只消费钱包，不产生第二张 Receipt', async () => {
    receiptsAtPayment = (await receiptRows()).length;
    const paid = String(facts.receivable.amount);
    const intent = await call<Row>(harness.admin, 'post', '/scm/payment/intent/create', {
      customerId: Number(facts.customerId),
      sourceType: 'SALES_ORDER',
      sourceId: Number(facts.orderId),
      amount: paid,
      method: 'BALANCE',
      provider: 'INTERNAL_BALANCE',
      remark: `${harness.runTag} GOLDEN07 余额支付`,
    });
    expect(String(intent.status)).toBe('SUCCEEDED');
    expect((await receiptRows()).length, '余额支付把已收的钱重复登记为收款').toBe(receiptsAtPayment);
    expect(await wallet(), '余额支付没有扣减钱包').toBeCloseTo(Number(RECHARGE) - Number(paid), 4);
  });

  test('5 余额消费以 BALANCE_MOVEMENT 自动核销订单应收', async () => {
    const rows = await writeOffRows();
    const balanceWriteOff = rows.find((row) => String(row.sourceType) === 'BALANCE_MOVEMENT') as Row;
    expect(balanceWriteOff !== undefined, '余额支付没有产生 BALANCE_MOVEMENT 核销').toBe(true);
    expect(Number(balanceWriteOff.amount)).toBe(Number(facts.receivable.amount));
    expect(String(balanceWriteOff.entryType)).toBe('NORMAL');

    const detail = await call<Row>(harness.finance, 'get', `/scm/finance/receivable/${facts.receivable.receivableId}`);
    const receivable = (detail.receivable ?? detail) as Row;
    expect(Number(receivable.writtenOffAmount)).toBe(Number(facts.receivable.amount));
    expect(Number(receivable.openAmount)).toBe(0);
  });
});

// ---------------------------------------------------------------- 来源：e2e-chains/adm-golden-chain-08.spec.ts
test.describe('adm-golden-chain-08', () => {
  /*
   * §15 E2E-08 黄金业务链（清单原文：ONLINE 支付 → Receipt → 自动核销 → 少发产生 overApplied → 售后后历史资金用途不 reverse）
   *
   * 这条链守的是「钱一旦收进来、用途一旦记下来，就只能追加不能改写」：
   *   ONLINE 支付必须**当场**立起一张财务 Receipt，并按实付全额核销到应收；
   *   少发（SHORT）让应收只按实发记，于是核销额 > 净应收 —— overApplied 只能在**读侧**表达，
   *     不允许为了「账面好看」去改历史核销额；
   *   售后退货只**追加红字**，那张收款和那条核销必须原样还在（同一 writeOffId、entryType 仍是 NORMAL、
   *     reverseOfId 仍为空），并且系统来源的收款根本不允许人工冲正（41144），只能走支付退款流程。
   *
   * 契约（读代码确认，别凭直觉）：
   *   `mockScenario` 缺省即 SUCCESS，`/scm/payment/intent/create` **同步**跑完整条财务链（Receipt + 自动核销），
   *   所以这里不需要再投渠道回调；渠道回调只在 DELAYED/EXPIRED 路径上才是必需的。
   *   `FinanceReceiptQueryVO` **没有** sourceType 字段，系统收款只能靠 `method === 'ONLINE_PAYMENT'`
   *   + `externalReference` 前缀 `MOCK-TXN-` + `walletFunding === false` 认出来。
   */

  test.describe.configure({mode: 'serial'});

  let harness: FinanceHarness;
  let facts: SignedOrderFacts;
  let receiptsBefore = 0;
  let paid = '';
  let onlineReceipt: Row;
  let writeOffId = 0;

  const receiptRows = async () => {
    const range = financeDateRange();
    const rows = await call<Row>(harness.finance, 'post', '/scm/finance/receipt/query', {
      pageNum: 1,
      pageSize: 100,
      customerId: Number(facts.customerId),
      startDate: range.startDate,
      endDate: range.endDate,
    });
    return (rows.list ?? []) as Row[];
  };

  const writeOffRows = async () => {
    const range = financeDateRange();
    const rows = await call<Row>(harness.finance, 'post', '/scm/finance/write-off/query', {
      pageNum: 1,
      pageSize: 100,
      targetId: Number(facts.receivable.receivableId),
      startDate: range.startDate,
      endDate: range.endDate,
    });
    return (rows.list ?? []) as Row[];
  };

  const receivableNow = async () => {
    const detail = await call<Row>(harness.finance, 'get', `/scm/finance/receivable/${facts.receivable.receivableId}`);
    return (detail.receivable ?? detail) as Row;
  };

  test.beforeAll(async () => {
    harness = await openFinanceHarness();
  });

  test.afterAll(async () => {
    if (harness) await harness.close();
  });

  test('1 少发订单签收：应收只按实发记，必然小于订单结算额', async () => {
    facts = await createSignedOrder(harness, 'GOLDEN08', [{quantity: '10.0000', sortedQuantity: '7.0000', sortedResult: 'SHORT'}]);
    const settlement = Number(facts.orderDetail.settlementTotalAmount);
    const actual = Number(facts.receivable.amount);
    expect(actual, '少发后应收应按实发生成').toBeLessThan(settlement);
    expect(actual > 0).toBe(true);
    paid = String(facts.orderDetail.settlementTotalAmount);
    receiptsBefore = (await receiptRows()).length;
  });

  test('2 ONLINE 支付当场立起系统收款', async () => {
    const intent = await call<Row>(harness.admin, 'post', '/scm/payment/intent/create', {
      customerId: Number(facts.customerId),
      sourceType: 'SALES_ORDER',
      sourceId: Number(facts.orderId),
      amount: paid,
      method: 'ONLINE',
      provider: 'MOCK',
      remark: `${harness.runTag} GOLDEN08 在线支付`,
    });
    expect(String(intent.status), '模拟渠道 SUCCESS 场景应当场支付成功').toBe('SUCCEEDED');
    expect(Number(intent.sourceId)).toBe(Number(facts.orderId));

    const rows = await receiptRows();
    expect(rows.length, '在线支付成功必须留下一张财务 Receipt').toBe(receiptsBefore + 1);
    onlineReceipt = rows.find((row) => String(row.method) === 'ONLINE_PAYMENT') as Row;
    expect(onlineReceipt !== undefined, '没有 method=ONLINE_PAYMENT 的系统收款').toBe(true);
    expect(Number(onlineReceipt.amount)).toBe(Number(paid));
    expect(String(onlineReceipt.externalReference), '收款必须回指渠道交易号').toContain('MOCK-TXN-');
    expect(Boolean(onlineReceipt.walletFunding), '订单收款不是钱包资金').toBe(false);
  });

  test('3 自动核销把整笔实付挂到应收上，超额只在读侧表达', async () => {
    const rows = await writeOffRows();
    expect(rows.length, '在线支付没有产生自动核销记录').toBe(1);
    const wo = rows[0];
    expect(String(wo.entryType)).toBe('NORMAL');
    expect(String(wo.sourceType)).toBe('RECEIPT');
    expect(Number(wo.sourceId)).toBe(Number(onlineReceipt.receiptId));
    expect(Number(wo.amount)).toBe(Number(paid));
    expect(String(wo.operator)).toContain('SYSTEM:ORDER_FUNDING');
    writeOffId = Number(wo.writeOffId);

    const now = await receivableNow();
    expect(Number(now.writtenOffAmount)).toBe(Number(paid));
    expect(Number(now.netAmount)).toBe(Number(facts.receivable.amount));
    expect(Number(now.openAmount), '整笔已核销，未核销额归零').toBe(0);
    // 少发让实收多于实发：差额必须是「超额核销」，不能被抹平。
    expect(Number(now.overAppliedAmount), 'overApplied 应等于实付减净应收').toBeCloseTo(Number(paid) - Number(facts.receivable.amount), 4);
  });

  test('4 售后退货只追加红字，历史收款与核销原样保留', async () => {
    const redQty = '4.0000';
    const before = await writeOffRows();
    await createApprovedReturn(harness, facts, redQty, 'GOLDEN08');

    const after = await writeOffRows();
    expect(after.length, '售后不应新增反向核销去改写历史资金用途').toBe(before.length);
    const same = after.find((row) => Number(row.writeOffId) === writeOffId) as Row;
    expect(same !== undefined, '原来那条自动核销记录消失了').toBe(true);
    expect(String(same.entryType)).toBe('NORMAL');
    expect(Number(same.amount)).toBe(Number(paid));
    expect(same.reverseOfId ?? null, '历史核销不能被售后反向').toBeNull();

    const receipt = (await receiptRows()).find((row) => Number(row.receiptId) === Number(onlineReceipt.receiptId)) as Row;
    expect(receipt !== undefined, '系统收款在售后后被删掉了').toBe(true);
    expect(Number(receipt.amount)).toBe(Number(paid));

    const now = await receivableNow();
    const detail = await call<Row>(harness.finance, 'get', `/scm/finance/receivable/${facts.receivable.receivableId}`);
    expect(((detail.redEntries ?? []) as Row[]).length, '退货应当追加一条红字').toBe(1);
    expect(Number(now.netAmount), '净应收应因红字而下降').toBeLessThan(Number(facts.receivable.amount));
    expect(Number(now.overAppliedAmount), '净应收下降后超额核销应当变大').toBeCloseTo(Number(paid) - Number(now.netAmount), 4);
  });

  test('5 系统收款不允许人工冲正：41144 失败关闭', async () => {
    const denied = await envelope(harness.finance, 'post', '/scm/finance/receipt/reverse', {
      receiptId: Number(onlineReceipt.receiptId),
      reason: `${harness.runTag} 试图人工冲正系统收款`,
    });
    expect(denied.code, `系统收款应被拒绝人工冲正，实际：${denied.code} ${denied.msg}`).toBe(41144);
    expect(String(denied.msg)).toContain('不能人工冲正');
    const still = (await receiptRows()).find((row) => Number(row.receiptId) === Number(onlineReceipt.receiptId)) as Row;
    expect(still !== undefined, '被拒绝的冲正请求不应留下任何痕迹').toBe(true);
  });

  test('6 应收列表页体现该超额核销，且整链无浏览器异常', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(`golden08: ${e.message}`));
    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receivables');
    const row = page.locator('#scm-finance-receivable-table tr').filter({hasText: facts.receivable.receivableNo}).first();
    await expect(row).toContainText('超额核销待处理');
    await row.getByRole('button', {name: accessibleName('明细')}).click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '应收明细'}).first();
    await expect(drawer).toContainText('超额核销');
    await page.screenshot({path: '../.runtime/golden08-receivable.png', fullPage: true});
    expect(errors, `浏览器异常：${errors.join(' | ')}`).toEqual([]);
  });
});
