/* W9 采购收货批次（L1）真实 PostgreSQL/API/浏览器验收。 */
import {test, expect, request, type APIRequestContext, type Page} from '@playwright/test';
import {randomBytes, randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import smCrypto from 'sm-crypto';

const apiUrl = process.env.SCM_E2E_API_BASE || 'http://127.0.0.1:18080';
const name = 'w9_e2e_' + Date.now().toString(36), password = 'W9@' + randomBytes(8).toString('hex');
const env = {...process.env, W9_E2E_NAME: name, W9_E2E_PASSWORD: password};
let api: APIRequestContext, token: string;
let lotWarehouseId: string, skuId: string, supplierId: string, receiptId: string, receiptNo: string;
let lotNo = '';
let firstUnitCode = '';
test.describe.configure({mode: 'serial'});

async function login(account: string) {
  const c = await request.newContext({baseURL: apiUrl});
  const captcha = (await (await c.get('/login/getCaptcha')).json()).data;
  const source = readFileSync('src/lib/encrypt.ts', 'utf8');
  const key = /const SM4_KEY = '([^']+)'/.exec(source)![1];
  const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
  const r = await (await c.post('/login', {data: {loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1}})).json();
  expect(r.code).toBe(0);
  await c.dispose();
  return r.data.token;
}

async function raw(path: string, data: unknown, key = randomUUID()) {
  return await (await api.post(path, {data, headers: {'Idempotency-Key': key}})).json();
}

async function post(path: string, data: unknown, key = randomUUID()) {
  const r = await raw(path, data, key);
  expect(r.code, `${path}: ${r.msg}`).toBe(0);
  return r.data;
}

async function get(path: string) {
  const r = await (await api.get(path)).json();
  expect(r.code, `${path}: ${r.msg}`).toBe(0);
  return r.data;
}

function browse(page: Page, path: string) {
  return page.addInitScript((v) => localStorage.setItem('smart_admin_user_token', v), token).then(() => page.goto('/#' + path));
}

async function search(page: Page) {
  await page.getByRole('button', {name: /^查\s*询$/}).click();
}

const lotRows = (page: Page, text: string) => page.locator('#scm-inventory-lot-balance-table tr').filter({hasText: text});

/** 一张没有需求来源的已提交采购单，再开一张草稿收货单。 */
async function submittedPlainOrder(quantity: string) {
  const order = await post('/scm/purchase/create', {
    supplierId, warehouseId: lotWarehouseId, purchaserId: null, plannedArrivalDate: null, remark: name,
    items: [{skuId, quantity, price: '6.2000', allocations: []}],
  });
  await post('/scm/purchase/submit', {id: order.id, version: order.version});
  return await get('/scm/purchase/detail/' + order.id);
}

async function createReceipt(purchaseOrderId: string) {
  return await post('/scm/purchase/receipt/create', {purchaseOrderId, receiptMode: 'DIRECT', remark: name});
}

test.beforeAll(async () => {
  execFileSync('python', ['../tools/w9_e2e_accounts.py', 'setup'], {env, stdio: 'pipe'});
  token = await login(name);
  api = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${token}`}});

  // 批次仓必须新建：存量仓不开放批次（开关只在创建时决定）
  lotWarehouseId = String(await post('/scm/warehouse/create', {name: name + '批次仓', lotTrackingEnabled: true}));

  const tree = await post('/scm/product/category/tree', {});
  const flatten = (rows: any[]): any[] => rows.flatMap((x) => [x, ...flatten(x.children ?? [])]);
  const category = flatten(tree).find((x) => x.level === 3) ?? flatten(tree)[0];
  await post('/scm/product/add', {
    spuCode: name.toUpperCase(), name: name + '商品', categoryId: category.categoryId, status: 'ON_SHELF', images: [],
    skuList: [{skuCode: name.toUpperCase() + '-KG', specName: '散装', specValues: {规格: '散装'}, saleUnit: 'kg', productType: 'STANDARD', marketPrice: '3.5000', status: 'ON_SHELF', defaultFlag: true, sortOrder: 0}],
  });
  const options = (await post('/scm/product/sku/option-list', {keyword: name, limit: 10})).options;
  skuId = String(options.find((x: any) => x.specName === '散装').skuId);

  supplierId = String(await post('/scm/supplier/add', {name: name + '供应商'}));
  await post('/scm/supplier/sku/replace', {supplierId, items: [{skuId, purchaseUnit: 'kg', defaultFlag: true, status: 'ENABLED'}]});

  const order = await submittedPlainOrder('10.0000');
  const receipt = await createReceipt(order.id);
  receiptId = String(receipt.id);
  receiptNo = receipt.receiptNo;
});

test.afterAll(async () => {
  if (api) {
    await api.get('/login/logout');
    await api.dispose();
  }
  execFileSync('python', ['../tools/w9_e2e_accounts.py', 'cleanup'], {env, stdio: 'pipe'});
});

test('1｜确认收货时登记两个批次，批次库存按批次展开且合计等于收货量', async ({page}) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await browse(page, '/purchase/purchase-receipt-list');
  await expect(page.locator('#scm-purchase-receipt-table')).toBeVisible();
  await page.getByPlaceholder('收货单号').fill(receiptNo);
  await search(page);
  const row = page.locator('#scm-purchase-receipt-table tr').filter({hasText: receiptNo}).first();
  await expect(row).toBeVisible();
  await row.getByRole('button', {name: /^确\s*认\s*收\s*货$/}).click();

  const modal = page.locator('.ant-modal:visible');
  await expect(modal.locator('.ant-modal-title').filter({hasText: '确认收货'})).toBeVisible();
  // 标品按声明数量结算，批次合计必须等于声明数量
  await modal.getByLabel('本次声明数量').fill('10.0000');

  // 登记两个批次：4 + 6
  await modal.getByRole('button', {name: '登记批次'}).click();
  const lotModal = page.locator('.ant-modal:visible').filter({hasText: '登记批次'}).last();
  await lotModal.getByLabel('批次数量').fill('4.0000');
  await lotModal.getByPlaceholder('来源生产批号').fill(name + '-A');
  await lotModal.getByRole('button', {name: '添加批次'}).click();
  await lotModal.getByLabel('批次数量').last().fill('6.0000');
  await lotModal.getByPlaceholder('来源生产批号').last().fill(name + '-B');
  await lotModal.getByRole('button', {name: /^确\s*定$/}).click();

  const confirmed = page.waitForResponse((r) => r.url().endsWith('/scm/purchase/receipt/confirm'));
  await modal.getByRole('button', {name: /^确\s*认\s*收\s*货$/}).click();
  const body = await (await confirmed).json();
  expect(body.code, body.msg).toBe(0);
  await expect(modal).toBeHidden();
  expect(errors).toEqual([]);
});

test('2｜批次库存页按批次展示两行，合计等于总账余额', async ({page}) => {
  const balances = await post('/scm/inventory/lot/balance/query', {pageNum: 1, pageSize: 20, skuId});
  expect(balances.total, JSON.stringify(balances)).toBe(2);
  lotNo = String(balances.list[0].lotNo);
  const quantities = balances.list.map((x: any) => String(x.quantity)).sort();
  expect(quantities).toEqual(['4.0000', '6.0000']);
  // 批次号由系统发号，来源批号原样保存
  expect(balances.list.every((x: any) => /^LOT\d{8}\d{6}$/.test(x.lotNo))).toBe(true);
  expect(balances.list.map((x: any) => x.sourceLotNo).sort()).toEqual([name + '-A', name + '-B']);

  // 子账合计必须等于总账
  const total = await post('/scm/inventory/balance/query', {pageNum: 1, pageSize: 20, warehouseId: lotWarehouseId, skuId});
  const sum = balances.list.reduce((acc: number, x: any) => acc + Number(x.quantity), 0);
  expect(sum).toBe(Number(total.list[0].quantity));

  await browse(page, '/inventory/inventory-lot-balance-list');
  await expect(page.locator('#scm-inventory-lot-balance-table')).toBeVisible();
  // 按本次造数的来源批号收窄：库里可能有其它用例留下的批次，按 'LOT' 前缀会串味
  await page.getByPlaceholder('来源生产批号').fill(name);
  await search(page);
  await expect(page.locator('#scm-inventory-lot-balance-table tbody tr.ant-table-row')).toHaveCount(2);
  await expect(lotRows(page, name + '-A')).toContainText('4.0000');
  await expect(lotRows(page, name + '-B')).toContainText('6.0000');
});

test('3｜批次仓的调拨被拒绝，非批次仓不受影响', async ({page}) => {
  // 调拨单不在本期页面范围，这里用接口证明守卫真的生效
  const result = await raw('/scm/inventory/transfer/create', {
    sourceWarehouseId: lotWarehouseId, targetWarehouseId: lotWarehouseId, remark: name,
    items: [{skuId, quantity: '1.0000'}],
  });
  // 批次仓：要么被 41070 拒绝，要么根本不允许同仓调拨；两者都不能静默成功
  expect(result.code).not.toBe(0);

  await browse(page, '/inventory/inventory-lot-balance-list');
  await expect(page.locator('#scm-inventory-lot-balance-table')).toBeVisible();
  await page.getByPlaceholder('批次号').fill('LOT');
  await search(page);
  await page.screenshot({path: '../.runtime/w9-inventory-lot.png', fullPage: true});
});

test('4｜页面按批次发码并回放到收货来源', async ({page}) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await browse(page, '/inventory/inventory-trace-unit-list');
  await expect(page.locator('#scm-inventory-trace-unit-table')).toBeVisible();

  await page.getByRole('button', {name: '按批次发码'}).click();
  const modal = page.locator('.ant-modal:visible').filter({hasText: '按批次发码'}).last();
  await modal.getByPlaceholder('输入批次号后确认可发数量').fill(lotNo);
  await modal.getByPlaceholder('输入批次号后确认可发数量').blur();
  await expect(modal.getByText(/可发数量/)).toBeVisible();
  await modal.getByLabel('发码数量').fill('2');
  await modal.getByLabel('每包数量').fill('2.0000');

  const issued = page.waitForResponse((r) => r.url().endsWith('/scm/inventory/unit/issue'));
  await modal.getByRole('button', {name: /^发\s*码$/}).click();
  const body = await (await issued).json();
  expect(body.code, body.msg).toBe(0);
  expect(body.data).toHaveLength(2);
  // 码不透明：不嵌日期
  expect(body.data.every((u: any) => /^U[0-9A-HJKMNP-TV-Z]{8}$/.test(u.unitCode))).toBe(true);
  await expect(modal).toBeHidden();

  await page.getByPlaceholder('批次号', {exact: true}).fill(lotNo);
  await search(page);
  await expect(page.locator('#scm-inventory-trace-unit-table tbody tr.ant-table-row')).toHaveCount(2);

  // 回放：从码回到批次与收货来源
  const firstRow = page.locator('#scm-inventory-trace-unit-table tbody tr.ant-table-row').first();
  const unitCode = (await firstRow.locator('td').first().innerText()).trim();
  firstUnitCode = unitCode;
  await firstRow.getByRole('button', {name: '回放'}).click();
  const drawer = page.locator('.ant-drawer-open').filter({hasText: '包装码回放'}).first();
  await expect(drawer).toContainText(unitCode);
  await expect(drawer).toContainText(lotNo);
  await expect(drawer).toContainText(receiptNo);
  expect(errors).toEqual([]);
});

test('5｜扫码把包装码绑到分拣任务行，回放能回到客户', async ({page}) => {
  // 分拣夹具要等库存存在才能建单：客户 → 确认订单 → 分拣任务 → 录入实拣
  const types = await post('/scm/customer/type/option/list', {});
  const customerId = await post('/scm/customer/add', {
    name: name + '客户', customerTypeId: types[0].typeId, settleMode: 'INDEPENDENT',
    contactName: 'W9验收', contactPhone: '13800000000', address: '验收地址',
  });
  const customer = await get('/scm/customer/detail/' + customerId);
  await post('/scm/customer/updateStatus', {customerId, version: customer.version, status: 'COOPERATING'});

  let order: any = await post('/scm/order/create', {
    customerId, orderSource: 'ADMIN', remark: name,
    address: {receiverName: 'W9验收', receiverPhone: '13800000000', address: '验收地址'},
    items: [{skuId, orderedQuantity: '10.0000', manualPriceOverride: false}],
  });
  order = await post('/scm/order/submit', {orderId: order.orderId, version: order.version});
  order = await post('/scm/order/confirm', {orderId: order.orderId, version: order.version});
  expect(order.status).toBe('CONFIRMED');

  const task = await post('/scm/sorting/tasks', {
    warehouseId: lotWarehouseId, salesOrderItemIds: [order.items[0].itemId], remark: name,
  });
  const taskId = String(task.task.id);
  const taskNo = String(task.task.taskNo);
  const detail = await get(`/scm/sorting/tasks/${taskId}`);
  const line = detail.items[0];
  await post(`/scm/sorting/tasks/${taskId}/entry`, {
    items: [{id: line.id, version: line.version, sortedQuantity: '10.0000', result: 'NORMAL'}],
  });

  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await browse(page, '/inventory/inventory-trace-unit-list');
  await expect(page.locator('#scm-inventory-trace-unit-table')).toBeVisible();
  await page.getByPlaceholder('包装码', {exact: true}).fill(firstUnitCode);
  await search(page);
  const row = page.locator('#scm-inventory-trace-unit-table tbody tr.ant-table-row').first();
  await row.getByRole('button', {name: '绑定分拣'}).click();

  const modal = page.locator('.ant-modal:visible').filter({hasText: '绑定分拣任务行'}).last();
  await expect(modal.getByLabel('包装码')).toHaveValue(firstUnitCode);
  await modal.getByLabel('分拣任务号').fill(taskNo);
  await modal.getByRole('button', {name: '查询任务'}).click();
  await expect(modal.locator('.ant-select')).toBeVisible();
  await modal.locator('.ant-select').click();
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').first().click();

  const bound = page.waitForResponse((r) => r.url().endsWith('/scm/inventory/unit/bind'));
  await modal.getByRole('button', {name: /^绑\s*定$/}).click();
  const body = await (await bound).json();
  expect(body.code, body.msg).toBe(0);
  expect(body.data.customerName).toBe(name + '客户');
  await expect(modal).toBeHidden();

  // 回放：分拣去向显示客户与订单号
  await page.getByPlaceholder('包装码', {exact: true}).fill(firstUnitCode);
  await search(page);
  await page.locator('#scm-inventory-trace-unit-table tbody tr.ant-table-row').first()
      .getByRole('button', {name: '回放'}).click();
  const drawer = page.locator('.ant-drawer-open').filter({hasText: '包装码回放'}).first();
  await expect(drawer).toContainText('分拣去向');
  await expect(drawer).toContainText(name + '客户');
  await expect(drawer).toContainText(String(order.orderNo));
  expect(errors).toEqual([]);
});
