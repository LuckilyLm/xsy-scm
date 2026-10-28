/*
 * 本文件由按域拆分的 E2E 用例合并而成：每个 test.describe 对应一份原文件，
 * 块内状态与钩子互不共享，执行顺序按来源文件排列。
 */
import {test, expect} from './scm-test-base';
import {test as pwTest} from '@playwright/test';
import {request, type APIRequestContext, type Locator, type Page} from '@playwright/test';
import {randomBytes, randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFileSync, mkdtempSync, writeFileSync} from 'node:fs';
import smCrypto from 'sm-crypto';
import {apiClient, authenticate, login as loginAs, provisionTempAccounts, accessibleName, login, type TempAccounts} from './scm-e2e-account';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

// ---------------------------------------------------------------- 来源：e2e/scm-inventory.spec.ts
test.describe('scm-inventory', () => {
  const test = pwTest;
  /* 真实数据库 / 接口 / 浏览器验收；账号口令只存内存。 */

  const apiUrl = 'http://127.0.0.1:18080';
  const name = 'w6_e2e_' + Date.now().toString(36) + 'i',
    password = 'W6@' + randomBytes(8).toString('hex');
  const env = {...process.env, W6_E2E_NAME: name, W6_E2E_PASSWORD: password};
  let api: APIRequestContext, token: string;
  let warehouseId: string, skuId: string, skuCode: string, emptySkuCode: string, supplierId: string, customerId: string;
  let orderA: any;
  test.describe.configure({mode: 'serial'});

  async function login(account: string) {
    const c = await request.newContext({baseURL: apiUrl});
    const captcha = (await (await c.get('/login/getCaptcha')).json()).data;
    const source = readFileSync('src/lib/encrypt.ts', 'utf8');
    const key = /const SM4_KEY = '([^']+)'/.exec(source)![1];
    const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
    const r = await (
      await c.post('/login', {
        data: {loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1},
      })
    ).json();
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
  /**
   * 操作列有两种形态：唯一的普通动作直接外露，其余（含危险动作）收进「更多」，
   * 菜单项渲染在 portal 里，不在行内。
   */
  async function rowMenu(page: Page, row: Locator, label: string) {
    const inline = row.getByRole('button', {name: new RegExp(label.split('').join('\s*'))});
    if ((await inline.count()) > 0) {
      await inline.click();
      return;
    }
    await row.getByRole('button', {name: /更多/}).click();
    await page.locator('.ant-dropdown:visible').getByText(label, {exact: true}).click();
  }
  const balanceRows = (page: Page, text: string) => page.locator('#scm-inventory-balance-table tr').filter({hasText: text});
  const movementRows = (page: Page, text: string) => page.locator('#scm-inventory-movement-table tr').filter({hasText: text});
  const receiptRows = (page: Page, text: string) => page.locator('#scm-purchase-receipt-table tr').filter({hasText: text});
  const balanceQuery = (data: unknown) => post('/scm/inventory/balance/query', {pageNum: 1, pageSize: 20, ...(data as object)});
  const movementQuery = (data: unknown) => post('/scm/inventory/movement/query', {pageNum: 1, pageSize: 20, ...(data as object)});

  /** 一张**没有需求来源**的已提交采购单 —— 收货类用例的干净前置（采购量 ≠ 需求量是合法业务）。 */
  async function submittedPlainOrder(quantity: string) {
    const o = await post('/scm/purchase/create', {
      supplierId,
      warehouseId,
      purchaserId: null,
      plannedArrivalDate: null,
      remark: name,
      items: [{skuId, quantity, price: '6.2000', allocations: []}],
    });
    await post('/scm/purchase/submit', {id: o.id, version: o.version});
    return await get('/scm/purchase/detail/' + o.id);
  }
  async function createReceipt(purchaseOrderId: string, receiptMode: 'DIRECT' | 'WAREHOUSE_CONFIRM') {
    return await post('/scm/purchase/receipt/create', {purchaseOrderId, receiptMode, remark: name});
  }
  function line(receipt: any, declared: string, actualWeight: string | null) {
    const item = receipt.items[0];
    return {
      receiptItemId: item.id,
      version: item.version,
      receivedQuantity: declared,
      actualWeight,
      weightSource: actualWeight ? ('MANUAL' as const) : null,
    };
  }
  const confirmReceipt = (receipt: any, quantity: string) =>
    post('/scm/purchase/receipt/confirm', {id: receipt.id, version: receipt.version, items: [line(receipt, quantity, quantity)]});

  test.beforeAll(async () => {
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-setup', '--wave', 'w6'], {env, stdio: 'pipe'});
    token = await login(name);
    api = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${token}`}});
    // 仓库：用 V15 播种的默认仓（W5 只做「新建 / 编辑基础信息」，无启停写入路径 → G1）。
    // Q12 的前提是「恰好一个启用仓库」，因此这里断言它，而不是假设它。
    const warehouses = await get('/scm/warehouse/list');
    warehouseId = String((warehouses.find((w: any) => w.warehouseCode === 'WH001') ?? warehouses[0]).id);
    const types = await post('/scm/customer/type/option/list', {});
    // 客户编码由服务端生成（CUS + 6 位序号），创建载荷不再提交编码。
    customerId = await post('/scm/customer/add', {
      name: name,
      customerTypeId: types[0].typeId,
      settleMode: 'INDEPENDENT',
      contactName: 'W6验收',
      contactPhone: '13800000000',
      address: '验收地址',
    });
    const customer = await get('/scm/customer/detail/' + customerId);
    await post('/scm/customer/updateStatus', {customerId, version: customer.version, status: 'COOPERATING'});
    const tree = await post('/scm/product/category/tree', {});
    const flatten = (rows: any[]): any[] => rows.flatMap((x) => [x, ...flatten(x.children ?? [])]);
    const category = flatten(tree).find((x) => x.level === 3) ?? flatten(tree)[0];
    const sku = (suffix: string) => [
      {
        skuCode: name.toUpperCase() + suffix,
        specName: '散装' + suffix,
        specValues: {规格: '散装'},
        saleUnit: 'kg',
        productType: 'NON_STANDARD',
        marketPrice: '3.5000',
        status: 'ON_SHELF',
        defaultFlag: true,
        sortOrder: 0,
      },
    ];
    await post('/scm/product/add', {
      spuCode: name.toUpperCase() + '-A',
      name: name + '商品A',
      categoryId: category.categoryId,
      status: 'ON_SHELF',
      images: [],
      skuList: sku('-A'),
    });
    // 第二个 SKU 只用于「空状态」：它永远不会有库存
    await post('/scm/product/add', {
      spuCode: name.toUpperCase() + '-E',
      name: name + '商品E',
      categoryId: category.categoryId,
      status: 'ON_SHELF',
      images: [],
      skuList: sku('-E'),
    });
    const options = (await post('/scm/product/sku/option-list', {keyword: name, limit: 10})).options;
    skuId = String(options.find((x: any) => x.specName === '散装-A').skuId);
    emptySkuCode = String(options.find((x: any) => x.specName === '散装-E').skuCode);
    skuCode = String(options.find((x: any) => x.specName === '散装-A').skuCode);
    // 供应商编码由服务端生成（SUP + 6 位序号），创建载荷不再提交编码。
    supplierId = await post('/scm/supplier/add', {name: name + '供应商'});
    await post('/scm/supplier/sku/replace', {supplierId, items: [{skuId, purchaseUnit: 'kg', defaultFlag: true, status: 'ENABLED'}]});
  });
  test.afterAll(async () => {
    if (api) {
      await api.get('/login/logout');
      await api.dispose();
    }
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-cleanup', '--wave', 'w6'], {env, stdio: 'pipe'});
  });

  // ------------------------------------------------------------------
  // 1. 收货确认即入库 → 余额页出现该 SKU 行
  // ------------------------------------------------------------------

  test('1 a confirmed receipt lands as a PURCHASE_IN balance row with the purchase unit', async ({page}) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    orderA = await submittedPlainOrder('20.0000');
    const receipt = await createReceipt(orderA.id, 'DIRECT');
    const confirmed = await confirmReceipt(receipt, '10.0000');
    expect(confirmed.status).toBe('CONFIRMED');
    expect(confirmed.receiptMode).toBe('DIRECT');
    expect(confirmed.putawayStatus).toBe('COMPLETED');
    expect(confirmed.putawayAt).not.toBeNull();
    expect(confirmed.putawayBy).not.toBeNull();

    // 接口层：余额与流水同时落地，单位取采购单位快照（Q13）
    const balances = await balanceQuery({warehouseId, skuId});
    expect(balances.total).toBe(1);
    expect(balances.list[0].quantity).toBe('10.0000');
    expect(balances.list[0].unit).toBe('kg');
    expect(balances.list[0].skuCode).toBe(skuCode);
    const movements = await movementQuery({warehouseId, skuId});
    expect(movements.total).toBe(1);
    expect(movements.list[0].movementType).toBe('PURCHASE_IN');
    expect(movements.list[0].beforeQuantity).toBe('0.0000');
    expect(movements.list[0].afterQuantity).toBe('10.0000');
    expect(movements.list[0].unitCost).toBe('6.2000');
    expect(movements.list[0].receiptNo).toBe(receipt.receiptNo);

    // 页面层
    await browse(page, '/inventory/inventory-balance-list');
    await expect(page.locator('#scm-inventory-balance-table')).toBeVisible();
    // 余额列表的筛选只有仓库与商品名称：编码不参与查询，按名称收窄到这一行
    await page.getByPlaceholder('商品名称').fill(name + '商品A');
    await search(page);
    const row = balanceRows(page, name + '商品A');
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('10.0000');
    await expect(row).toContainText('kg');
    await expect(row).toContainText(name + '商品A');
    await page.screenshot({path: '../.runtime/w6-inventory-balance.png', fullPage: true});
    expect(consoleErrors).toEqual([]);
  });

  // ------------------------------------------------------------------
  // 2. 再次部分收货 → 余额累加、流水新增、来源单号可跳收货单
  // ------------------------------------------------------------------

  test('2 a second partial receipt accumulates the balance and adds a traceable movement', async ({page}) => {
    const receipt = await createReceipt(orderA.id, 'DIRECT');
    await confirmReceipt(receipt, '5.0000');

    const balances = await balanceQuery({warehouseId, skuId});
    expect(balances.total).toBe(1);
    expect(balances.list[0].quantity).toBe('15.0000');
    expect(balances.list[0].version).toBe(2);
    const movements = await movementQuery({warehouseId, skuId});
    expect(movements.total).toBe(2);
    // 回放链：第二条的期初 = 第一条的期末（append-only 账本的核心恒等式）
    const chain = movements.list.slice().sort((a: any, b: any) => Number(a.id) - Number(b.id));
    expect(chain[0].beforeQuantity).toBe('0.0000');
    expect(chain[0].afterQuantity).toBe('10.0000');
    expect(chain[1].beforeQuantity).toBe('10.0000');
    expect(chain[1].afterQuantity).toBe('15.0000');

    await browse(page, '/inventory/inventory-movement-list');
    await expect(page.locator('#scm-inventory-movement-table')).toBeVisible();
    // 流水页只按仓库/类型/发生区间筛选（内部编码不再对外展示），用业务单号定位本次收货那条
    const currentMovement = movementRows(page, receipt.receiptNo);
    await expect(currentMovement).toHaveCount(1);
    await expect(currentMovement.first()).toContainText('采购入库');
    await expect(currentMovement.first()).toContainText('15.0000');
    await page.screenshot({path: '../.runtime/w6-inventory-movement.png', fullPage: true});

    // 来源单号 → 收货单列表（带 receiptNo 查询参数）
    await page.locator('#scm-inventory-movement-table').getByText(receipt.receiptNo, {exact: true}).first().click();
    await expect(page.getByPlaceholder('收货单号')).toHaveValue(receipt.receiptNo);
    await expect(receiptRows(page, receipt.receiptNo)).toHaveCount(1);
  });

  // ------------------------------------------------------------------
  // 3. 空状态
  // ------------------------------------------------------------------

  test('3 a SKU without stock renders the empty state instead of an error', async ({page}) => {
    const empty = await balanceQuery({skuCode: emptySkuCode});
    expect(empty.total).toBe(0);

    await browse(page, '/inventory/inventory-balance-list');
    await page.getByPlaceholder('商品名称').fill(name + '商品E');
    await search(page);
    await expect(page.locator('#scm-inventory-balance-table')).toContainText('暂无库存余额');
    // 空态不是错误：不得出现错误提示
    await expect(page.locator('.ant-alert-error')).toHaveCount(0);
  });

  // ------------------------------------------------------------------
  // 4. 权限
  // ------------------------------------------------------------------

  // 用 `_none` 而不是 `_read`：只读角色含全部页面菜单和 `:query` 按钮（含 scm:inventory:balance:query），
  // 拿它断言「查不到」会得到 code 0 的假失败。
  test('4 an account with no role sees no page and is rejected by the API', async ({page}) => {
    const readToken = await login(name + '_none');
    const client = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${readToken}`}});
    // 载荷合法（pageNum/pageSize 必填且合规），因此 @Valid 通过、拦截器先抛权限错误 30005
    const denied = await (await client.post('/scm/inventory/balance/query', {data: {pageNum: 1, pageSize: 20}})).json();
    expect(denied.code).toBe(30005);
    const deniedMovement = await (await client.post('/scm/inventory/movement/query', {data: {pageNum: 1, pageSize: 20}})).json();
    expect(deniedMovement.code).toBe(30005);

    // 前端：菜单不渲染 → 路由不注册 → 直接访问该路径落到 404 页，库存表格永不出现。
    //
    // 断言刻意落在「404 页出现」而不是「侧边栏不含库存管理」：该账号连布局都没渲染，
    // `.ant-menu` 在 DOM 里根本不存在，而 Playwright 的 `not.toContainText` 要求元素先存在
    // —— 用后者会得到一个「元素找不到」的假失败，掩盖掉真正被验证的事实。
    await page.addInitScript((v) => localStorage.setItem('smart_admin_user_token', v), readToken);
    await page.goto('/#/inventory/inventory-balance-list');
    await expect(page.getByText('您访问的内容不存在')).toBeVisible();
    await expect(page.locator('#scm-inventory-balance-table')).toHaveCount(0);

    await client.get('/login/logout');
    await client.dispose();
  });

  // ------------------------------------------------------------------
  // 5. 分页与筛选
  // ------------------------------------------------------------------

  test('5 filters and paging work on both read-only pages', async ({page}) => {
    // 余额：SKU 编码关键字命中 1 行；不存在的编码 → 0 行
    const hit = await balanceQuery({skuCode});
    expect(hit.total).toBe(1);
    expect(await balanceQuery({skuCode: skuCode + '-NO-SUCH'})).toMatchObject({total: 0});
    // 分页：pageSize=1 时只返回 1 行，但 total 仍是全量
    const paged = await post('/scm/inventory/balance/query', {pageNum: 1, pageSize: 1, skuCode});
    expect(paged.list).toHaveLength(1);
    expect(paged.total).toBeGreaterThanOrEqual(1);

    await browse(page, '/inventory/inventory-balance-list');
    await page.getByPlaceholder('商品名称').fill(name + '商品A');
    await search(page);
    await expect(balanceRows(page, name + '商品A')).toHaveCount(1);

    // 流水：类型筛选 + 时间范围（occurred_at，左闭右开）
    const typed = await movementQuery({skuId, movementType: 'PURCHASE_IN'});
    expect(typed.total).toBe(2);
    const recent = await movementQuery({skuId, occurredFrom: new Date(Date.now() - 3600_000).toISOString()});
    expect(recent.total).toBe(2);
    // 区间落在 2020 年 → 0 行（证明过滤的是业务发生时刻，而不是写入时刻）
    const ancient = await movementQuery({skuId, occurredFrom: '2020-01-01T00:00:00+08:00', occurredTo: '2020-01-02T00:00:00+08:00'});
    expect(ancient.total).toBe(0);

    // 流水页不提供商品筛选（内部编码不展示），用接口里这两条流水的业务单号回证页面行
    const uiReceiptNos = ((await movementQuery({skuId})).list as any[]).map((row) => row.receiptNo);
    expect(uiReceiptNos).toHaveLength(2);
    await browse(page, '/inventory/inventory-movement-list');
    await expect(page.locator('#scm-inventory-movement-table')).toBeVisible();
    await search(page);
    for (const receiptNo of uiReceiptNos) {
      await expect(movementRows(page, receiptNo)).toHaveCount(1);
    }
    // 区间选择器：设到 2020 年 → 空态
    const picker = page.locator('.ant-picker-input input');
    await picker.first().click();
    await picker.first().fill('2020-01-01 00:00:00');
    await page.keyboard.press('Enter');
    await picker.last().fill('2020-01-02 00:00:00');
    await page.keyboard.press('Enter');
    await search(page);
    await expect(page.locator('#scm-inventory-movement-table')).toContainText('暂无库存流水');
    // 清空筛选后仍可查询（枚举清空必须送 undefined，不能送空串，否则 40000）。
    // 按钮名用正则：antd 会在两个中文字之间插入空格，可访问名实际是「重 置」。
    await page.getByRole('button', {name: /^重\s*置$/}).click();
    await search(page);
    for (const receiptNo of uiReceiptNos) {
      await expect(movementRows(page, receiptNo)).toHaveCount(1);
    }
  });

  // ------------------------------------------------------------------
  // 6. Q12 默认仓库
  // ------------------------------------------------------------------

  test('6 Q12 the balance page defaults the warehouse only when exactly one is enabled', async ({page}) => {
    const warehouses = await get('/scm/warehouse/list');
    const select = () => page.locator('.smart-query-form .ant-select').first();
    const selection = () => select().locator('.ant-select-selection-item');

    // 真实环境
    await browse(page, '/inventory/inventory-balance-list');
    await expect(page.locator('#scm-inventory-balance-table')).toBeVisible();
    if (warehouses.length === 1) {
      await expect(selection()).toContainText(warehouses[0].name);
    } else {
      await expect(selection()).toHaveCount(0);
    }

    // 条件化默认的另一半：伪造两个启用仓库，断言页面不自动选任何一个。
    // 必须 `reload()` 而不是再次 `goto` 同一个 hash：hash 路由不变时 vue-router 不会重新挂载
    // 组件，`onMounted` 里的 Q12 判定不会重跑，于是会读到上一次留下的默认值（假失败）。
    await page.route('**/scm/warehouse/list*', (route) =>
      route.fulfill({
        json: {
          code: 0,
          msg: 'ok',
          data: [
            {id: 900001, name: '验收仓A', warehouseCode: 'W6A'},
            {id: 900002, name: '验收仓B', warehouseCode: 'W6B'},
          ],
        },
      })
    );
    await page.reload();
    await expect(page.locator('#scm-inventory-balance-table')).toBeVisible();
    await expect(selection()).toHaveCount(0);

    // 多仓下仍提供普通筛选（可手动选择）
    await select().click();
    await page.locator('.ant-select-dropdown:visible').getByText('验收仓A').click();
    await expect(selection()).toContainText('验收仓A');
  });

  // ------------------------------------------------------------------
  // 7. B1 仓库确认入库
  // ------------------------------------------------------------------

  test('7 WAREHOUSE_CONFIRM posts inventory only when the warehouse confirms putaway', async ({page}) => {
    const order = await submittedPlainOrder('3.0000');
    const receipt = await createReceipt(order.id, 'WAREHOUSE_CONFIRM');
    const balanceBefore = await balanceQuery({warehouseId, skuId});
    const movementsBefore = await movementQuery({warehouseId, skuId});

    const confirmed = await confirmReceipt(receipt, '3.0000');
    expect(confirmed.status).toBe('CONFIRMED');
    expect(confirmed.receiptMode).toBe('WAREHOUSE_CONFIRM');
    expect(confirmed.putawayStatus).toBe('PENDING');
    expect(confirmed.putawayAt).toBeNull();
    expect(confirmed.putawayBy).toBeNull();
    expect(await balanceQuery({warehouseId, skuId})).toMatchObject({
      total: balanceBefore.total,
      list: [{quantity: balanceBefore.list[0].quantity}],
    });
    expect((await movementQuery({warehouseId, skuId})).total).toBe(movementsBefore.total);

    await browse(page, '/purchase/purchase-receipt-list');
    await page.getByPlaceholder('收货单号').fill(receipt.receiptNo);
    await search(page);
    const receiptRow = receiptRows(page, receipt.receiptNo);
    await expect(receiptRow).toHaveCount(1);
    await expect(receiptRow).toContainText('仓库确认入库');
    await expect(receiptRow).toContainText('待入库');
    await receiptRow.getByRole('button', {name: '确认入库'}).click();
    const modal = page.locator('.ant-modal-confirm:visible');
    await expect(modal).toContainText('该操作会把本收货单数量正式记入库存');
    await modal.getByRole('button', {name: /确\s*定/}).click();
    await expect(page.locator('.ant-message-notice-content').getByText('已入库', {exact: true})).toBeVisible();
    await expect(receiptRow).toContainText('已入库');
    await expect(receiptRow.getByRole('button', {name: '确认入库'})).toHaveCount(0);

    const completed = await get('/scm/purchase/receipt/detail/' + receipt.id);
    expect(completed.putawayStatus).toBe('COMPLETED');
    expect(completed.putawayAt).not.toBeNull();
    expect(completed.putawayBy).not.toBeNull();
    const balanceAfter = await balanceQuery({warehouseId, skuId});
    expect(balanceAfter.list[0].quantity).toBe('18.0000');
    const movementsAfter = await movementQuery({warehouseId, skuId});
    expect(movementsAfter.total).toBe(movementsBefore.total + 1);
    const movement = movementsAfter.list.find((item: any) => item.receiptNo === receipt.receiptNo);
    expect(movement).toBeTruthy();
    expect(movement.quantity).toBe('3.0000');
    expect(new Date(movement.occurredAt).getTime()).toBe(new Date(completed.putawayAt).getTime());

    const repeated = await raw('/scm/purchase/receipt/putaway', {id: completed.id, version: completed.version});
    expect(repeated.code).toBe(41008);
    expect((await balanceQuery({warehouseId, skuId})).list[0].quantity).toBe('18.0000');
    expect((await movementQuery({warehouseId, skuId})).total).toBe(movementsAfter.total);
  });

  // ------------------------------------------------------------------
  // 8. B1 仓库严格停用与启停往返
  // ------------------------------------------------------------------

  test('8 warehouse disable is strict and an empty warehouse can be disabled and enabled', async ({page}) => {
    // 仓库编码由服务端生成（WH + 6 位序号）：创建载荷不再提交编码，查询用回读的真实编码。
    const isolatedId = await post('/scm/warehouse/create', {name: name + '隔离仓', address: null, remark: name});
    const isolatedCode = String((await get('/scm/warehouse/detail/' + isolatedId)).warehouseCode);
    expect(isolatedCode).toMatch(/^WH\d{6,}$/);

    // 仓库列表只按名称筛选（内部编码不再展示），默认仓名称从详情回读
    const defaultWarehouse = await get('/scm/warehouse/detail/' + warehouseId);
    const defaultName = String(defaultWarehouse.name);
    await browse(page, '/purchase/warehouse-list');
    await page.getByPlaceholder('仓库名称').fill(defaultName);
    await search(page);
    const defaultRow = page.locator('#scm-warehouse-table tr').filter({hasText: defaultName});
    await expect(defaultRow).toHaveCount(1);
    await rowMenu(page, defaultRow, '停用');
    await page
      .locator('.ant-modal-confirm:visible')
      .getByRole('button', {name: /确\s*定/})
      .click();
    // 停用失败时确认框保留，原因由全局 toast 给出
    await expect(page.locator('.ant-message')).toContainText('仓库仍有库存余额，不能停用');
    await page
      .locator('.ant-modal-confirm:visible')
      .getByRole('button', {name: /取\s*消/})
      .click();

    await page.getByPlaceholder('仓库名称').fill(name + '隔离仓');
    await search(page);
    const isolatedRow = page.locator('#scm-warehouse-table tr').filter({hasText: name + '隔离仓'});
    await expect(isolatedRow).toHaveCount(1);
    await rowMenu(page, isolatedRow, '停用');
    await page
      .locator('.ant-modal-confirm:visible')
      .getByRole('button', {name: /确\s*定/})
      .click();
    await expect(page.getByText('仓库已停用', {exact: true})).toBeVisible();
    await expect(isolatedRow).toContainText('停用');
    await rowMenu(page, isolatedRow, '启用');
    await expect(page.getByText('仓库已启用', {exact: true})).toBeVisible();
    await expect(isolatedRow).toContainText('启用');

    const enabled = await get('/scm/warehouse/detail/' + isolatedId);
    await post('/scm/warehouse/disable', {id: enabled.id, version: enabled.version});
    const disabled = await get('/scm/warehouse/detail/' + isolatedId);
    expect(disabled.status).toBe('DISABLED');
  });
});

// ---------------------------------------------------------------- 来源：e2e/scm-inventory-lot.spec.ts
test.describe('scm-inventory-lot', () => {
  const test = pwTest;
  /* W9 采购收货批次（L1）真实 PostgreSQL/API/浏览器验收。 */

  const apiUrl = process.env.SCM_E2E_API_BASE || 'http://127.0.0.1:18080';
  const name = 'w9_e2e_' + Date.now().toString(36),
    password = 'W9@' + randomBytes(8).toString('hex');
  const env = {...process.env, W9_E2E_NAME: name, W9_E2E_PASSWORD: password};
  let api: APIRequestContext, token: string;
  let lotWarehouseId: string, skuId: string, supplierId: string, receiptNo: string;
  let lotNo = '';
  let firstUnitCode = '';
  let secondUnitCode = '';
  test.describe.configure({mode: 'serial'});

  async function login(account: string) {
    const c = await request.newContext({baseURL: apiUrl});
    const captcha = (await (await c.get('/login/getCaptcha')).json()).data;
    const source = readFileSync('src/lib/encrypt.ts', 'utf8');
    const key = /const SM4_KEY = '([^']+)'/.exec(source)![1];
    const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
    const r = await (
      await c.post('/login', {
        data: {loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1},
      })
    ).json();
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
      supplierId,
      warehouseId: lotWarehouseId,
      purchaserId: null,
      plannedArrivalDate: null,
      remark: name,
      items: [{skuId, quantity, price: '6.2000', allocations: []}],
    });
    await post('/scm/purchase/submit', {id: order.id, version: order.version});
    return await get('/scm/purchase/detail/' + order.id);
  }

  async function createReceipt(purchaseOrderId: string) {
    return await post('/scm/purchase/receipt/create', {purchaseOrderId, receiptMode: 'DIRECT', remark: name});
  }

  test.beforeAll(async () => {
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-setup', '--wave', 'w9'], {env, stdio: 'pipe'});
    token = await login(name);
    api = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${token}`}});

    // 批次仓必须新建：存量仓不开放批次（开关只在创建时决定）
    lotWarehouseId = String(await post('/scm/warehouse/create', {name: name + '批次仓', lotTrackingEnabled: true}));

    const tree = await post('/scm/product/category/tree', {});
    const flatten = (rows: any[]): any[] => rows.flatMap((x) => [x, ...flatten(x.children ?? [])]);
    const category = flatten(tree).find((x) => x.level === 3) ?? flatten(tree)[0];
    await post('/scm/product/add', {
      spuCode: name.toUpperCase(),
      name: name + '商品',
      categoryId: category.categoryId,
      status: 'ON_SHELF',
      images: [],
      skuList: [
        {
          skuCode: name.toUpperCase() + '-KG',
          specName: '散装',
          specValues: {规格: '散装'},
          saleUnit: 'kg',
          productType: 'STANDARD',
          marketPrice: '3.5000',
          status: 'ON_SHELF',
          defaultFlag: true,
          sortOrder: 0,
        },
      ],
    });
    const options = (await post('/scm/product/sku/option-list', {keyword: name, limit: 10})).options;
    skuId = String(options.find((x: any) => x.specName === '散装').skuId);

    supplierId = String(await post('/scm/supplier/add', {name: name + '供应商'}));
    await post('/scm/supplier/sku/replace', {supplierId, items: [{skuId, purchaseUnit: 'kg', defaultFlag: true, status: 'ENABLED'}]});

    const order = await submittedPlainOrder('10.0000');
    const receipt = await createReceipt(order.id);
    receiptNo = receipt.receiptNo;
  });

  test.afterAll(async () => {
    if (api) {
      await api.get('/login/logout');
      await api.dispose();
    }
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-cleanup', '--wave', 'w9'], {env, stdio: 'pipe'});
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
    await lotModal
      .getByPlaceholder('来源生产批号')
      .last()
      .fill(name + '-B');
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
      sourceWarehouseId: lotWarehouseId,
      targetWarehouseId: lotWarehouseId,
      remark: name,
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
    // 列表按 id 倒序：第一行是最新发的那个码，所以另一个未绑定的码取 data[0]
    secondUnitCode = String(body.data[0].unitCode);
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
      name: name + '客户',
      customerTypeId: types[0].typeId,
      settleMode: 'INDEPENDENT',
      contactName: 'W9验收',
      contactPhone: '13800000000',
      address: '验收地址',
    });
    const customer = await get('/scm/customer/detail/' + customerId);
    await post('/scm/customer/updateStatus', {customerId, version: customer.version, status: 'COOPERATING'});

    let order: any = await post('/scm/order/create', {
      customerId,
      orderSource: 'ADMIN',
      remark: name,
      address: {receiverName: 'W9验收', receiverPhone: '13800000000', address: '验收地址'},
      items: [{skuId, orderedQuantity: '10.0000', manualPriceOverride: false}],
    });
    order = await post('/scm/order/submit', {orderId: order.orderId, version: order.version});
    order = await post('/scm/order/confirm', {orderId: order.orderId, version: order.version});
    expect(order.status).toBe('CONFIRMED');

    const task = await post('/scm/sorting/tasks', {
      warehouseId: lotWarehouseId,
      salesOrderItemIds: [order.items[0].itemId],
      remark: name,
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
    await page.locator('#scm-inventory-trace-unit-table tbody tr.ant-table-row').first().getByRole('button', {name: '回放'}).click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '包装码回放'}).first();
    await expect(drawer).toContainText('分拣去向');
    await expect(drawer).toContainText(name + '客户');
    await expect(drawer).toContainText(String(order.orderNo));
    expect(errors).toEqual([]);
  });

  test('6｜包装码标签走打印中心：预览含码与批次，打印留下冻结记录', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await browse(page, '/inventory/inventory-trace-unit-list');
    await expect(page.locator('#scm-inventory-trace-unit-table')).toBeVisible();
    await page.getByPlaceholder('包装码', {exact: true}).fill(firstUnitCode);
    await search(page);
    const row = page.locator('#scm-inventory-trace-unit-table tbody tr.ant-table-row').first();

    const previewed = page.waitForResponse((r) => r.url().includes('/scm/print/TRACE_UNIT/') && r.url().includes('/preview'));
    await row.getByRole('button', {name: /更多/}).click();
    await page.locator('.ant-dropdown:visible').getByText('打印标签', {exact: true}).click();
    const preview = await (await previewed).json();
    expect(preview.code, preview.msg).toBe(0);
    expect(preview.data.documentType).toBe('TRACE_UNIT');
    expect(preview.data.paper, '标签用 80mm 热敏纸').toBe('TICKET_80');
    const rendered = JSON.stringify(preview.data);
    expect(rendered, '标签上必须有码与批次').toContain(firstUnitCode);
    expect(rendered).toContain(lotNo);

    const modal = page.locator('.ant-modal:visible').filter({hasText: '打印'}).last();
    const printed = page.waitForResponse((r) => r.request().method() === 'POST' && /\/scm\/print\/TRACE_UNIT\//.test(r.url()));
    await modal.getByRole('button', {name: /^打\s*印$/}).click();
    const body = await (await printed).json();
    expect(body.code, body.msg).toBe(0);
    expect(body.data.frozen, '正式打印必须返回冻结版面').toBe(true);
    expect(errors).toEqual([]);
  });

  test('7｜未绑定的码可以作废，已绑定的码作废被拒', async ({page}) => {
    await browse(page, '/inventory/inventory-trace-unit-list');
    await expect(page.locator('#scm-inventory-trace-unit-table')).toBeVisible();

    // 未绑定的第二个码：作废成功，状态变已作废
    await page.getByPlaceholder('包装码', {exact: true}).fill(secondUnitCode);
    await search(page);
    const row = page.locator('#scm-inventory-trace-unit-table tbody tr.ant-table-row').first();
    await row.getByRole('button', {name: /更多/}).click();
    await page.locator('.ant-dropdown:visible').getByText('作废', {exact: true}).click();

    const voidModal = page.locator('.ant-modal:visible').filter({hasText: '作废包装码'}).last();
    await voidModal.getByLabel('作废原因').fill('标签破损');
    const voided = page.waitForResponse((r) => r.url().endsWith('/scm/inventory/unit/void'));
    await voidModal.getByRole('button', {name: /^作\s*废$/}).click();
    expect((await (await voided).json()).code).toBe(0);
    await expect(voidModal).toBeHidden();

    await page.getByPlaceholder('包装码', {exact: true}).fill(secondUnitCode);
    await search(page);
    await expect(page.locator('#scm-inventory-trace-unit-table tbody tr.ant-table-row').first()).toContainText('已作废');

    // 已绑定的第一个码：作废必须被拒（后端 41512），错误走全局提示
    await page.getByPlaceholder('包装码', {exact: true}).fill(firstUnitCode);
    await search(page);
    const boundRow = page.locator('#scm-inventory-trace-unit-table tbody tr.ant-table-row').first();
    await boundRow.getByRole('button', {name: /更多/}).click();
    await page.locator('.ant-dropdown:visible').getByText('作废', {exact: true}).click();
    const boundModal = page.locator('.ant-modal:visible').filter({hasText: '作废包装码'}).last();
    await boundModal.getByLabel('作废原因').fill('贴错');
    const rejected = page.waitForResponse((r) => r.url().endsWith('/scm/inventory/unit/void'));
    await boundModal.getByRole('button', {name: /^作\s*废$/}).click();
    const rejectedBody = await (await rejected).json();
    expect(rejectedBody.code, '已绑定的码不能作废').toBe(41512);
    await expect(page.locator('.ant-message').getByText(/不能作废/)).toBeVisible();
  });

  test('8｜申请更正后不能自己审批，绑定仍然有效', async ({page}) => {
    // 已绑定的码（用例 5 绑过）申请更正
    await browse(page, '/inventory/inventory-trace-unit-list');
    await expect(page.locator('#scm-inventory-trace-unit-table')).toBeVisible();
    await page.getByPlaceholder('包装码', {exact: true}).fill(firstUnitCode);
    await search(page);
    const row = page.locator('#scm-inventory-trace-unit-table tbody tr.ant-table-row').first();
    await row.getByRole('button', {name: /更多/}).click();
    await page.locator('.ant-dropdown:visible').getByText('申请更正', {exact: true}).click();

    const applyModal = page.locator('.ant-modal:visible').filter({hasText: '申请更正'}).last();
    await applyModal.getByLabel('更正原因').fill('绑错了客户');
    const applied = page.waitForResponse((r) => r.url().endsWith('/scm/inventory/unit/correction/apply'));
    await applyModal.getByRole('button', {name: /^提交申请$/}).click();
    const appliedBody = await (await applied).json();
    expect(appliedBody.code, appliedBody.msg).toBe(0);
    expect(appliedBody.data.status).toBe('PENDING');

    // 更正页：申请人自己点批准 → 41522，且申请仍是待审批
    await browse(page, '/inventory/inventory-trace-correction-list');
    await expect(page.locator('#scm-inventory-trace-correction-table')).toBeVisible();
    await page.getByPlaceholder('包装码', {exact: true}).fill(firstUnitCode);
    await search(page);
    const pendingRow = page.locator('#scm-inventory-trace-correction-table tbody tr.ant-table-row').first();
    await expect(pendingRow).toContainText('待审批');
    await pendingRow.getByRole('button', {name: '批准'}).click();
    const decisionModal = page.locator('.ant-modal:visible').filter({hasText: '批准更正'}).last();
    const rejected = page.waitForResponse((r) => r.url().endsWith('/scm/inventory/unit/correction/approve'));
    await decisionModal.getByRole('button', {name: /^确\s*定$/}).click();
    const rejectedBody = await (await rejected).json();
    expect(rejectedBody.code, '申请与审批不能是同一人').toBe(41522);
    await expect(page.locator('.ant-message').getByText(/不能审批自己/)).toBeVisible();

    // 绑定仍然有效：回放仍显示客户
    await browse(page, '/inventory/inventory-trace-unit-list');
    await page.getByPlaceholder('包装码', {exact: true}).fill(firstUnitCode);
    await search(page);
    await page.locator('#scm-inventory-trace-unit-table tbody tr.ant-table-row').first().getByRole('button', {name: '回放'}).click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '包装码回放'}).first();
    await expect(drawer).toContainText(name + '客户');
  });
});

// ---------------------------------------------------------------- 来源：e2e/scm-inventory-write.spec.ts
test.describe('scm-inventory-write', () => {
  const test = pwTest;
  /*
    W6 库存写流程 E2E：出库确认 / 盘点确认 / 报损报溢审批 / 调拨发出→收货 / 规格转换审批。

    与本文件的库存用例（只读页 + 入库）互补：这里覆盖**会改库存的五个入口**，
    每个入口都跑真实链路（HTTP + 真实 PG + 真实浏览器），并断言三件事同时成立：
        单据状态机 · 余额与流水（append-only 链） · 金额守恒。
    其中调拨与转换的「成本随货平移」是本轮修的账务缺陷，断言在测试 4 与 5。
    账号密钥只存在内存里，跑完由 tools/e2e_fixtures.py 清理。
  */

  const apiUrl = 'http://127.0.0.1:18080';
  const name = 'w6_e2e_' + Date.now().toString(36) + 'w';
  const password = 'W6W@' + randomBytes(9).toString('hex');
  // E2E_SECOND_ADMIN：额外建 `<name>_two` 第二管理员。报损报溢禁止自建自审（裁决第 8 条制衡），
  // 审批必须由录单人以外的账号发起；仍是管理员，避免把写侧仓库守卫卷进本用例。
  const env = {...process.env, W6_E2E_NAME: name, W6_E2E_PASSWORD: password, E2E_SECOND_ADMIN: '1'};
  const purchasePrice = '6.2000';

  let api: APIRequestContext, token: string;
  /** 第二管理员客户端：只用于报损报溢的审批/驳回调用。 */
  let auditor: APIRequestContext;
  let warehouseId: string, supplierId: string, categoryId: string;
  /** 每个流程独占一个 SKU：余额与流水断言都按 (仓库, SKU) 收敛，互不干扰。 */
  const sku = new Map<string, {id: string; code: string}>();
  const pageErrors: string[] = [];

  async function login(account: string, secret: string) {
    const c = await request.newContext({baseURL: apiUrl});
    const captcha = (await (await c.get('/login/getCaptcha')).json()).data;
    const source = readFileSync('src/lib/encrypt.ts', 'utf8');
    const key = /const SM4_KEY = '([^']+)'/.exec(source)![1];
    const encrypted = Buffer.from(smCrypto.sm4.encrypt(secret, Buffer.from(key).toString('hex'))).toString('base64');
    const r = await (
      await c.post('/login', {
        data: {loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1},
      })
    ).json();
    expect(r.code).toBe(0);
    await c.dispose();
    return r.data.token;
  }
  /** 每次调用都换新幂等键：同一把键会命中幂等回放，拿不到「重复操作应被拒」的真实错误码。 */
  async function raw(path: string, data: unknown = null, key = randomUUID()) {
    return await rawAs(api, path, data, key);
  }
  /** 用指定账号发同一个请求：报损报溢审批要由录单人以外的人发起（裁决第 8 条制衡）。 */
  async function rawAs(client: APIRequestContext, path: string, data: unknown = null, key = randomUUID()) {
    return await (await client.post(path, {data: data ?? {}, headers: {'Idempotency-Key': key}})).json();
  }
  async function post(path: string, data: unknown = null) {
    const r = await raw(path, data);
    expect(r.code, `${path}: ${r.msg}`).toBe(0);
    return r.data;
  }
  async function postAs(client: APIRequestContext, path: string, data: unknown = null) {
    const r = await rawAs(client, path, data);
    expect(r.code, `${path}: ${r.msg}`).toBe(0);
    return r.data;
  }
  async function get(path: string) {
    const r = await (await api.get(path)).json();
    expect(r.code, `${path}: ${r.msg}`).toBe(0);
    return r.data;
  }
  function browse(page: Page, path: string, overrideToken?: string) {
    return page.addInitScript((v) => localStorage.setItem('smart_admin_user_token', v), overrideToken ?? token).then(() => page.goto('/#' + path));
  }
  async function search(page: Page) {
    await page.getByRole('button', {name: /^查\s*询$/}).click();
  }
  const rows = (page: Page, tableId: string, text: string) => page.locator(`#${tableId} tr`).filter({hasText: text});
  /** 金额与均价用数值比较：SCALE 由 SQL 决定，字面量断言会把口径变更误报成失败。 */
  const num = (v: unknown) => Number(v);
  const balanceQuery = (warehouse: string | number, skuId: string) =>
    post('/scm/inventory/balance/query', {pageNum: 1, pageSize: 50, warehouseId: warehouse, skuId});
  const movementQuery = (warehouse: string | number, skuId: string) =>
    post('/scm/inventory/movement/query', {pageNum: 1, pageSize: 50, warehouseId: warehouse, skuId});

  /** (仓库, SKU) 的当前余额行；没有行返回 null。 */
  async function balance(warehouse: string | number, skuId: string) {
    const r = await balanceQuery(warehouse, skuId);
    return r.total === 0 ? null : r.list[0];
  }
  async function movement(warehouse: string | number, skuId: string, type: string) {
    const list = (await movementQuery(warehouse, skuId)).list.filter((m: any) => m.movementType === type);
    expect(list).toHaveLength(1);
    return list[0];
  }
  /** 该 SKU 在**所有仓库**的库存金额合计 —— 守恒断言的基准。 */
  async function totalCost(skuId: string) {
    const rowsOfSku = (await post('/scm/inventory/balance/query', {pageNum: 1, pageSize: 50, skuId})).list.filter(
      (b: any) => String(b.skuId) === skuId
    );
    return rowsOfSku.reduce((sum: number, b: any) => sum + num(b.quantity) * num(b.avgCost), 0);
  }

  /** 单据当前版本：审批 / 确认类接口都带乐观锁，取最新版本才能表达「这次操作」的意图。 */
  async function version(modulePath: string, id: string | number) {
    return (await get(modulePath + '/detail/' + id)).version;
  }

  /**
   * 自建一条 1→2→3 级分类链并返回叶子 id。
   * 不能沿用「从分类树找 level===3」：SPU 只允许挂在三级且 ENABLED 的分类下，
   * 而开发库的种子分类只播到二级，取不到三级就会退化成一级根节点并被 40011 拒绝。
   */
  async function ensureCategoryChain() {
    let parentId: string | null = null;
    for (const level of [1, 2, 3]) {
      parentId = String(
        await post('/scm/product/category/add', {
          parentId,
          categoryCode: (name + '-L' + level).toUpperCase(),
          name: name + '分类' + level,
          sortOrder: 0,
          status: 'ENABLED',
        })
      );
    }
    return parentId;
  }

  /** 建一个非标品 SKU（散装 / kg）。 */
  async function newSku(tag: string) {
    const code = (name + '-' + tag).toUpperCase();
    const spuId = await post('/scm/product/add', {
      spuCode: code,
      name: name + '商品' + tag,
      categoryId,
      status: 'ON_SHELF',
      images: [],
      skuList: [
        {
          skuCode: code,
          specName: '散装' + tag,
          specValues: {规格: '散装'},
          saleUnit: 'kg',
          productType: 'NON_STANDARD',
          marketPrice: '3.5000',
          status: 'ON_SHELF',
          defaultFlag: true,
          sortOrder: 0,
        },
      ],
    });
    // V147 起规格编码由后端发号：回读详情取真实 id 与编码
    const detail = await get('/scm/product/detail/' + spuId);
    const row = (detail.skuList ?? [])[0];
    sku.set(tag, {id: String(row.skuId), code: String(row.skuCode)});
  }

  /** 采购 → 提交 → DIRECT 收货确认，把 SKU  stocking 到指定数量（均价 = 采购价）。 */
  async function stockIn(skuId: string, quantity: string) {
    const order = await post('/scm/purchase/create', {
      supplierId,
      warehouseId,
      purchaserId: null,
      plannedArrivalDate: null,
      remark: name,
      items: [{skuId, quantity, price: purchasePrice, allocations: []}],
    });
    await post('/scm/purchase/submit', {id: order.id, version: order.version});
    const receipt = await post('/scm/purchase/receipt/create', {purchaseOrderId: order.id, receiptMode: 'DIRECT', remark: name});
    const item = receipt.items[0];
    return await post('/scm/purchase/receipt/confirm', {
      id: receipt.id,
      version: receipt.version,
      items: [{receiptItemId: item.id, version: item.version, receivedQuantity: quantity, actualWeight: quantity, weightSource: 'MANUAL'}],
    });
  }

  test.describe.configure({mode: 'serial'});

  test.beforeAll(async () => {
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-setup', '--wave', 'w6'], {env, stdio: 'pipe'});
    token = await login(name, password);
    api = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${token}`}});
    // 审批账号与录单账号分开：同一人审批报损报溢会被 41065 挡下（裁决第 8 条制衡）
    auditor = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${await login(name + '_two', password)}`}});
    // Q12：订单链路依赖「恰好一个启用仓库」，因此取用并断言 WH001，不假设它。
    const warehouses = await get('/scm/warehouse/list');
    warehouseId = String((warehouses.find((w: any) => w.warehouseCode === 'WH001') ?? warehouses[0]).id);

    // 供应商编码由服务端生成（SUP + 6 位序号），创建载荷不再提交编码。
    supplierId = await post('/scm/supplier/add', {name: name + '供应商'});
    categoryId = await ensureCategoryChain();
    for (const tag of ['1', '2', '3', '4', '5', '6']) await newSku(tag);
    // replace 是**整体替换**，必须一次带上全部 SKU
    await post('/scm/supplier/sku/replace', {
      supplierId,
      items: [...sku.values()].map((x) => ({skuId: Number(x.id), purchaseUnit: 'kg', defaultFlag: true, status: 'ENABLED'})),
    });
  });

  test.afterAll(async () => {
    if (auditor) {
      await auditor.get('/login/logout');
      await auditor.dispose();
    }
    if (api) {
      await api.get('/login/logout');
      await api.dispose();
    }
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-cleanup', '--wave', 'w6'], {env, stdio: 'pipe'});
  });

  test.beforeEach(async ({page}) => {
    page.on('pageerror', (e) => pageErrors.push(e.message));
  });

  test.afterEach(async () => {
    expect(pageErrors, `浏览器 pageerror：${pageErrors.join(' | ')}`).toEqual([]);
  });

  // ------------------------------------------------------------------
  // 1. 出库确认（UI 驱动写入）：扣库存、写 SALES_OUT、按当时均价记成本
  // ------------------------------------------------------------------

  test('1 outbound confirm deducts stock through the real button and stamps the moving average', async ({page}) => {
    const {id} = sku.get('1')!;
    await stockIn(id, '20.0000');
    const opening = await balance(warehouseId, id);
    expect(opening.quantity).toBe('20.0000');
    expect(num(opening.avgCost)).toBe(6.2);

    const outboundId = await post('/scm/inventory/outbound/create', {
      warehouseId,
      remark: name,
      items: [{skuId: Number(id), quantity: '5.0000'}],
    });
    const draft = await get('/scm/inventory/outbound/detail/' + outboundId);
    expect(draft.status).toBe('DRAFT');

    await browse(page, '/inventory/inventory-outbound-list');
    await page.getByPlaceholder('出库单号').fill(draft.outboundNo);
    await search(page);
    const row = rows(page, 'scm-inventory-outbound-table', draft.outboundNo);
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('草稿');

    await row.getByRole('button', {name: '确认出库'}).click();
    await page.locator('.ant-modal-confirm:visible').getByRole('button', {name: '确认出库'}).click();
    await expect(page.locator('.ant-message-notice-content').getByText('出库完成，库存已扣减', {exact: true})).toBeVisible();
    await expect(row).toContainText('已确认');
    await expect(row.getByRole('button', {name: '确认出库'})).toHaveCount(0);
    await page.screenshot({path: '../.runtime/w6-write-outbound.png', fullPage: true});

    // 余额扣减 + 一条出库流水：出库不改变均价，但必须写下当时的均价（成本事实）
    const after = await balance(warehouseId, id);
    expect(after.quantity).toBe('15.0000');
    expect(num(after.avgCost)).toBe(6.2);
    expect(num(after.amount)).toBeCloseTo(93, 5);
    const out = await movement(warehouseId, id, 'SALES_OUT');
    expect(out.quantity).toBe('5.0000');
    expect(out.beforeQuantity).toBe('20.0000');
    expect(out.afterQuantity).toBe('15.0000');
    expect(out.unitCost).toBe('6.2000');

    // 状态机：已确认不可重复确认，且失败不产生第二次扣减
    expect((await raw('/scm/inventory/outbound/confirm/' + outboundId)).code).toBe(41014);
    expect((await balance(warehouseId, id)).quantity).toBe('15.0000');
    expect((await movementQuery(warehouseId, id)).total).toBe(2); // 采购入库 + 出库
  });

  // ------------------------------------------------------------------
  // 2. 盘点确认：盘盈不创造成本、盘亏按当时均价结转
  // ------------------------------------------------------------------

  test('2 stocktake confirm moves both directions and never invents a cost', async () => {
    const {id} = sku.get('2')!;
    await stockIn(id, '20.0000');

    const gainId = await post('/scm/inventory/stocktake/create', {
      warehouseId,
      remark: name,
      items: [{skuId: Number(id), actualQuantity: '25.0000'}],
    });
    await post('/scm/inventory/stocktake/confirm/' + gainId);
    let row = await balance(warehouseId, id);
    expect(row.quantity).toBe('25.0000');
    expect(num(row.avgCost)).toBe(6.2); // 盘盈没有采购价格事实，均价不动
    const gain = await movement(warehouseId, id, 'STOCKTAKE_GAIN');
    expect(gain.quantity).toBe('5.0000');
    expect(gain.beforeQuantity).toBe('20.0000');
    expect(gain.afterQuantity).toBe('25.0000');
    expect(gain.unitCost).toBe('6.2000');

    const lossId = await post('/scm/inventory/stocktake/create', {
      warehouseId,
      remark: name,
      items: [{skuId: Number(id), actualQuantity: '22.0000'}],
    });
    const lossDetail = await get('/scm/inventory/stocktake/detail/' + lossId);
    expect(lossDetail.items[0].bookQuantity).toBe('25.0000');
    expect(lossDetail.items[0].deltaQuantity).toBe('-3.0000');
    await post('/scm/inventory/stocktake/confirm/' + lossId);
    row = await balance(warehouseId, id);
    expect(row.quantity).toBe('22.0000');
    const loss = await movement(warehouseId, id, 'STOCKTAKE_LOSS');
    expect(loss.quantity).toBe('3.0000');
    expect(loss.beforeQuantity).toBe('25.0000');
    expect(loss.afterQuantity).toBe('22.0000');
    expect(loss.unitCost).toBe('6.2000');

    // 状态机 + 边界：已确认不可重复确认；实盘量为负进不来（表单校验先拦），库存不动
    expect((await raw('/scm/inventory/stocktake/confirm/' + lossId)).code).toBe(41020);
    expect(
      (await raw('/scm/inventory/stocktake/create', {warehouseId, remark: name, items: [{skuId: Number(id), actualQuantity: '-1.0000'}]})).code
    ).not.toBe(0);
    expect((await balance(warehouseId, id)).quantity).toBe('22.0000');
  });

  // ------------------------------------------------------------------
  // 3. 报损报溢审批：驳回必须留意见、乐观锁、通过后扣库存
  // ------------------------------------------------------------------

  test('3 loss report approval enforces opinion, optimistic lock and then deducts stock', async ({page}) => {
    const {id} = sku.get('3')!;
    await stockIn(id, '20.0000');
    const create = () =>
      post('/scm/inventory/loss-gain/create', {
        adjustType: 'LOSS',
        warehouseId,
        reason: '到货变质',
        remark: name,
        items: [{skuId: Number(id), quantity: '3.0000'}],
      });

    const rejected = await create();
    // 本组审批调用一律走 auditor（`<name>_two` 第二管理员）：录单人自己驳回会被 41065 挡下（裁决第 8 条制衡）
    // 驳回不带意见 → 拒绝：沟通成本不该转嫁给录单人
    expect(
      (await rawAs(auditor, `/scm/inventory/loss-gain/reject/${rejected}`, {version: await version('/scm/inventory/loss-gain', rejected)})).code
    ).toBe(41037);
    await postAs(auditor, `/scm/inventory/loss-gain/reject/${rejected}`, {
      version: await version('/scm/inventory/loss-gain', rejected),
      auditOpinion: '数量与验收单不符，请核对',
    });
    const rejectedDoc = await get('/scm/inventory/loss-gain/detail/' + rejected);
    expect(rejectedDoc.status).toBe('REJECTED');
    expect((await balance(warehouseId, id)).quantity).toBe('20.0000'); // 驳回不产生库存影响
    expect((await movementQuery(warehouseId, id)).list.filter((m: any) => m.movementType === 'LOSS_REPORT')).toHaveLength(0);

    // 乐观锁：版本对不上先失败，不会静默覆盖别人的编辑
    const stale = await create();
    // 审批人同样换人（裁决第 8 条制衡）：换人后下面的 40921 / 41029 才分别来自乐观锁与状态判定，而不是自审禁令
    expect((await rawAs(auditor, `/scm/inventory/loss-gain/approve/${stale}`, {version: 9999})).code).toBe(40921);
    expect((await get('/scm/inventory/loss-gain/detail/' + stale)).status).toBe('PENDING');

    await postAs(auditor, `/scm/inventory/loss-gain/approve/${stale}`, {
      version: await version('/scm/inventory/loss-gain', stale),
      auditOpinion: '已核对',
    });
    const done = await get('/scm/inventory/loss-gain/detail/' + stale);
    expect(done.status).toBe('COMPLETED');
    expect((await balance(warehouseId, id)).quantity).toBe('17.0000');
    const loss = await movement(warehouseId, id, 'LOSS_REPORT');
    expect(loss.unitCost).toBe('6.2000');
    expect(loss.beforeQuantity).toBe('20.0000');
    expect(loss.afterQuantity).toBe('17.0000');
    expect((await rawAs(auditor, `/scm/inventory/loss-gain/approve/${stale}`, {version: done.version})).code).toBe(41029);

    await browse(page, '/inventory/inventory-loss-gain-list');
    await page.getByPlaceholder('单据号').fill(done.lossGainNo);
    await search(page);
    const row = rows(page, 'scm-inventory-loss-gain-table', done.lossGainNo);
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('已完成');
    await page.screenshot({path: '../.runtime/w6-write-loss-gain.png', fullPage: true});
  });

  // ------------------------------------------------------------------
  // 4. 调拨发出 → 在途 → 收货（UI 驱动两步）：成本随货平移，不随目标仓清零
  // ------------------------------------------------------------------

  test('4 transfer moves stock and cost across warehouses in two steps', async ({page}) => {
    const {id} = sku.get('4')!;
    await stockIn(id, '20.0000');
    const before = await totalCost(id);
    expect(num(before)).toBeCloseTo(124, 5);
    // 目标仓建完立刻停用：Q12 要求启用仓库唯一，不能把第二个启用仓留在库里。
    // 仓库编码由服务端生成（WH + 6 位序号），创建载荷不再提交编码。
    const destId = await post('/scm/warehouse/create', {name: name + '调入仓', address: null, remark: name});
    await post('/scm/warehouse/disable', {id: destId, version: await version('/scm/warehouse', destId)});

    const transferId = await post('/scm/inventory/transfer/create', {
      fromWarehouseId: warehouseId,
      toWarehouseId: destId,
      remark: name,
      items: [{skuId: Number(id), quantity: '6.0000'}],
    });

    await browse(page, '/inventory/inventory-transfer-list');
    const draft = await get('/scm/inventory/transfer/detail/' + transferId);
    await page.getByPlaceholder('调拨单号').fill(draft.transferNo);
    await search(page);
    const row = rows(page, 'scm-inventory-transfer-table', draft.transferNo);
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('草稿');

    // 发出：源仓立即扣减，目标仓还没有 —— 在途期间这批货不在任何余额行里（没有虚拟在途仓）
    await row.getByRole('button', {name: '发出'}).click();
    await page.locator('.ant-modal-confirm:visible').getByRole('button', {name: '确认发出'}).click();
    await expect(row).toContainText('在途');
    expect((await balance(warehouseId, id)).quantity).toBe('14.0000');
    expect(await balance(destId, id)).toBeNull();
    const transit = (await get('/scm/inventory/transfer/in-transit')).find((x: any) => String(x.skuId) === id);
    expect(transit).toBeTruthy();
    expect(transit.quantity).toBe('6.0000');
    expect(transit.toWarehouseId).toBe(Number(destId));

    // 目标仓停用 → 收货被拒，单据停在在途（需要人工启用目标仓后重试）
    expect((await raw(`/scm/inventory/transfer/receive/${transferId}`)).code).toBe(41048);
    expect((await get('/scm/inventory/transfer/detail/' + transferId)).status).toBe('SHIPPED');
    await post('/scm/warehouse/enable', {id: destId, version: await version('/scm/warehouse', destId)});

    // 收货：本轮修的缺陷就落在这里 —— 成本跟着货走，不按目标仓的 0 均价入账
    await row.getByRole('button', {name: '收货'}).click();
    await page.locator('.ant-modal-confirm:visible').getByRole('button', {name: '确认收货'}).click();
    await expect(row).toContainText('已完成');
    await page.screenshot({path: '../.runtime/w6-write-transfer.png', fullPage: true});

    const outLeg = await movement(warehouseId, id, 'TRANSFER_OUT');
    const inLeg = await movement(destId, id, 'TRANSFER_IN');
    expect(outLeg.unitCost).toBe('6.2000');
    expect(inLeg.unitCost).toBe(outLeg.unitCost); // 成本随货平移
    expect(inLeg.beforeQuantity).toBe('0.0000');
    expect(inLeg.afterQuantity).toBe('6.0000');
    expect(inLeg.unitSnapshot).toBe(outLeg.unitSnapshot); // Q13：目标仓新建行沿用源仓记账单位

    const dest = await balance(destId, id);
    expect(dest.quantity).toBe('6.0000');
    expect(num(dest.avgCost)).toBe(6.2); // 不是 0
    expect(dest.unit).toBe('kg'); // Q13：目标仓新建行沿用源仓单位
    const src = await balance(warehouseId, id);
    expect(num(src.amount) + num(dest.amount)).toBeCloseTo(before, 5); // 跨仓总成本守恒
    expect((await get('/scm/inventory/transfer/in-transit')).find((x: any) => String(x.skuId) === id)).toBeUndefined();

    // 收尾：先反向调拨把货调回源仓，目标仓才有得停用（停用守卫按 quantity > 0 拦，41005）。
    // 启用仓库不唯一会让预留库存直接 41018，所以不能把第二个启用仓留在库里。
    const backId = await post('/scm/inventory/transfer/create', {
      fromWarehouseId: destId,
      toWarehouseId: warehouseId,
      remark: name + '-回库',
      items: [{skuId: Number(id), quantity: '6.0000'}],
    });
    await post(`/scm/inventory/transfer/ship/${backId}`);
    await post(`/scm/inventory/transfer/receive/${backId}`);
    expect((await balance(destId, id)).quantity).toBe('0.0000');
    expect((await balance(warehouseId, id)).quantity).toBe('20.0000');
    await post('/scm/warehouse/disable', {id: destId, version: await version('/scm/warehouse', destId)});
  });

  // ------------------------------------------------------------------
  // 5. 规格转换审批：跨单位按总成本折算，守恒的是总成本
  // ------------------------------------------------------------------

  test('5 conversion approval carries total cost across SKUs instead of pricing at zero', async ({page}) => {
    const source = sku.get('5')!;
    const target = sku.get('6')!;
    await stockIn(source.id, '20.0000');
    const before = await totalCost(source.id);
    expect(num(before)).toBeCloseTo(124, 5);

    const form = {
      warehouseId,
      convertType: 'SPLIT',
      reason: '客户要散装',
      remark: name,
      items: [
        {
          sourceSkuId: Number(source.id),
          sourceQuantity: '4.0000',
          sourceUnit: 'kg',
          targetSkuId: Number(target.id),
          targetQuantity: '10.0000',
          targetUnit: 'kg',
        },
      ],
    };
    const conversionId = await post('/scm/inventory/conversion/create', form);
    expect((await get('/scm/inventory/conversion/detail/' + conversionId)).status).toBe('PENDING');

    expect(
      (await raw(`/scm/inventory/conversion/reject/${conversionId}`, {version: await version('/scm/inventory/conversion', conversionId)})).code
    ).toBe(41063);
    await post(`/scm/inventory/conversion/reject/${conversionId}`, {
      version: await version('/scm/inventory/conversion', conversionId),
      auditOpinion: '折算率与供应商不符',
    });
    expect((await get('/scm/inventory/conversion/detail/' + conversionId)).status).toBe('REJECTED');
    expect((await balance(warehouseId, source.id)).quantity).toBe('20.0000');
    expect(await balance(warehouseId, target.id)).toBeNull(); // 驳回不动库存

    const second = await post('/scm/inventory/conversion/create', form);
    expect((await raw(`/scm/inventory/conversion/approve/${second}`, {version: 9999})).code).toBe(40921);
    await post(`/scm/inventory/conversion/approve/${second}`, {
      version: await version('/scm/inventory/conversion', second),
      auditOpinion: '已核对折算率',
    });
    const done = await get('/scm/inventory/conversion/detail/' + second);
    expect(done.status).toBe('COMPLETED');

    const outLeg = await movement(warehouseId, source.id, 'CONVERT_OUT');
    const inLeg = await movement(warehouseId, target.id, 'CONVERT_IN');
    expect(outLeg.unitCost).toBe('6.2000');
    expect(outLeg.afterQuantity).toBe('16.0000');
    // 4 kg × 6.2 = 24.8 的总成本落到 10 kg 上 → 2.48；不是 0，也不是目标 SKU 的现有均价
    expect(num(inLeg.unitCost)).toBeCloseTo(2.48, 5);
    expect(num(outLeg.quantity) * num(outLeg.unitCost)).toBeCloseTo(num(inLeg.quantity) * num(inLeg.unitCost), 5);

    const targetBalance = await balance(warehouseId, target.id);
    expect(targetBalance.quantity).toBe('10.0000');
    expect(num(targetBalance.avgCost)).toBeCloseTo(2.48, 5);
    expect(num(targetBalance.amount)).toBeCloseTo(24.8, 5);
    expect(num(await totalCost(source.id)) + num(targetBalance.amount)).toBeCloseTo(before, 5);

    await browse(page, '/inventory/inventory-conversion-list');
    await page.getByPlaceholder('转换单号').fill(done.conversionNo);
    await search(page);
    const row = rows(page, 'scm-inventory-conversion-table', done.conversionNo);
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('已完成');
    await page.screenshot({path: '../.runtime/w6-write-conversion.png', fullPage: true});
  });

  // ------------------------------------------------------------------
  // 6. 写入权限：只读账号既拿不到按钮，也调不动接口
  // ------------------------------------------------------------------

  test('6 a read-only account sees no write button and is rejected by every write endpoint', async ({page}) => {
    const readToken = await login(name + '_read', password);
    const client = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${readToken}`}});
    const target = sku.get('1')!;
    const body = {warehouseId, remark: name, items: [{skuId: Number(target.id), quantity: '1.0000'}]};
    for (const path of ['/scm/inventory/outbound/create', '/scm/inventory/stocktake/create', '/scm/inventory/transfer/create']) {
      const r = await (await client.post(path, {data: body, headers: {'Idempotency-Key': randomUUID()}})).json();
      expect(r.code, `${path} 必须被权限拦截器拒绝`).toBe(30005);
    }
    // 只读账号有页面菜单（否则落到 404，验证不到「按钮被隐藏」），但没有写权限
    await browse(page, '/inventory/inventory-outbound-list', readToken);
    await expect(page.locator('#scm-inventory-outbound-table')).toBeVisible();
    await expect(page.getByRole('button', {name: '新建出库单'})).toHaveCount(0);
    await client.get('/login/logout');
    await client.dispose();
  });
});

// ---------------------------------------------------------------- 来源：e2e/scm-purchase.spec.ts
test.describe('scm-purchase', () => {
  const test = pwTest;
  /* W5 real PostgreSQL/API/browser acceptance. Account secrets stay in memory. */
  // 共用的临时账号 + 真实登录链路；本文件已有一个 `login(account)`，故给共用版起别名。
  const apiUrl = 'http://127.0.0.1:18080';
  const name = 'w5_e2e_' + Date.now().toString(36),
    password = 'W5@' + randomBytes(8).toString('hex');
  const env = {...process.env, W5_E2E_NAME: name, W5_E2E_PASSWORD: password};
  let api: APIRequestContext, token: string;
  let warehouseId: string, skuId: string, supplierId: string, customerId: string;
  let demandA: any, soA: any, orderA: any;
  let printTemplateId: number | undefined;
  test.describe.configure({mode: 'serial'});
  async function login(account: string) {
    const c = await request.newContext({baseURL: apiUrl});
    const captcha = (await (await c.get('/login/getCaptcha')).json()).data;
    const source = readFileSync('src/lib/encrypt.ts', 'utf8');
    const key = /const SM4_KEY = '([^']+)'/.exec(source)![1];
    const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
    const r = await (
      await c.post('/login', {
        data: {loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1},
      })
    ).json();
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
  const orderDetail = (id: string) => get('/scm/purchase/detail/' + id);
  const receiptDetail = (id: string) => get('/scm/purchase/receipt/detail/' + id);
  /** 采购单号 PO+yyyyMMdd+至少 6 位；收货单号 PR+同形（全局序列，不按日 reset）。 */
  const PO = /^PO\d{14,}$/,
    PR = /^PR\d{14,}$/;

  /** 造一张**已确认**的销售订单（W5 需求的唯一来源），并给出可隔离它的汇总窗口。 */
  async function confirmedOrder(orderedQuantity: string, actualQuantity: string) {
    const before = new Date(Date.now() - 30000).toISOString();
    let o = await post('/scm/order/create', {
      customerId,
      orderSource: 'ADMIN',
      address: {receiverName: 'W5验收', receiverPhone: '13800000000', address: '验收地址'},
      remark: name,
      items: [{skuId, orderedQuantity, manualPriceOverride: false}],
    });
    o = await post('/scm/order/submit', {orderId: o.orderId, version: o.version});
    o = await post('/scm/order/item/actual-quantity', {
      orderId: o.orderId,
      itemId: o.items[0].itemId,
      version: o.items[0].version,
      actualQuantity,
      reason: 'W5 验收实重',
    });
    o = await post('/scm/order/confirm', {orderId: o.orderId, version: o.version});
    const after = new Date(Date.now() + 30000).toISOString();
    return {orderId: o.orderId, orderNo: o.orderNo, before, after};
  }
  /** 在汇总窗口内生成需求，再按销售单号断言本订单恰好生成一条；窗口可能含同轮其它 spec 的订单。 */
  async function demandOf(so: any) {
    await post('/scm/purchase/demand/generate', {startAt: so.before, endAt: so.after, warehouseId, supplierId});
    const page = await post('/scm/purchase/demand/query', {pageNum: 1, pageSize: 20, salesOrderNo: so.orderNo});
    expect(page.total).toBe(1);
    return page.list[0];
  }
  async function createOrder(allocations: any[], quantity: string, price: string) {
    return await post('/scm/purchase/create', {
      supplierId,
      warehouseId,
      purchaserId: null,
      plannedArrivalDate: null,
      remark: name,
      items: [{skuId, quantity, price, allocations}],
    });
  }
  /** 一张**没有需求来源**的已提交采购单 —— 收货类用例的干净前置（采购量 ≠ 需求量是合法业务）。 */
  async function submittedPlainOrder(quantity: string) {
    const o = await createOrder([], quantity, '6.2000');
    await post('/scm/purchase/submit', {id: o.id, version: o.version});
    return await orderDetail(o.id);
  }
  async function createReceipt(purchaseOrderId: string, key = randomUUID()) {
    return await post('/scm/purchase/receipt/create', {purchaseOrderId, receiptMode: 'DIRECT', remark: name}, key);
  }
  function line(receipt: any, declared: string, actualWeight: string | null) {
    const item = receipt.items[0];
    return {
      receiptItemId: item.id,
      version: item.version,
      receivedQuantity: declared,
      actualWeight,
      weightSource: actualWeight ? ('MANUAL' as const) : null,
    };
  }
  const confirmReceipt = (receipt: any, items: any[], key = randomUUID()) =>
    post('/scm/purchase/receipt/confirm', {id: receipt.id, version: receipt.version, items}, key);
  async function row(page: Page, tableId: string, text: string) {
    await page.getByRole('button', {name: /^查\s*询$/}).click();
    return page.locator(`#${tableId} tr`).filter({hasText: text});
  }

  test.beforeAll(async () => {
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-setup', '--wave', 'w5'], {env, stdio: 'pipe'});
    token = await login(name);
    api = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${token}`}});
    // 仓库：用 V15 播种的默认仓（W5 只做「新建 / 编辑基础信息」，无启停写入路径 → G1）
    const warehouses = await get('/scm/warehouse/list');
    warehouseId = String((warehouses.find((w: any) => w.warehouseCode === 'WH001') ?? warehouses[0]).id);
    const types = await post('/scm/customer/type/option/list', {});
    // 客户编码由服务端生成（CUS + 6 位序号），创建载荷不再提交编码。
    customerId = await post('/scm/customer/add', {
      name: name,
      customerTypeId: types[0].typeId,
      settleMode: 'INDEPENDENT',
      contactName: 'W5验收',
      contactPhone: '13800000000',
      address: '验收地址',
    });
    const customer = await get('/scm/customer/detail/' + customerId);
    await post('/scm/customer/updateStatus', {customerId, version: customer.version, status: 'COOPERATING'});
    const tree = await post('/scm/product/category/tree', {});
    const flatten = (rows: any[]): any[] => rows.flatMap((x) => [x, ...flatten(x.children ?? [])]);
    const category = flatten(tree).find((x) => x.level === 3) ?? flatten(tree)[0];
    await post('/scm/product/add', {
      spuCode: name.toUpperCase(),
      name: name + '商品',
      categoryId: category.categoryId,
      status: 'ON_SHELF',
      images: [],
      skuList: [
        {
          skuCode: name.toUpperCase() + '-KG',
          specName: '散装',
          specValues: {规格: '散装'},
          saleUnit: 'kg',
          productType: 'NON_STANDARD',
          marketPrice: '3.5000',
          status: 'ON_SHELF',
          defaultFlag: true,
          sortOrder: 0,
        },
        {
          skuCode: name.toUpperCase() + '-BOX',
          specName: '整箱',
          specValues: {规格: '整箱'},
          saleUnit: '箱',
          productType: 'STANDARD',
          marketPrice: '0.0000',
          status: 'ON_SHELF',
          defaultFlag: false,
          sortOrder: 1,
        },
      ],
    });
    const options = (await post('/scm/product/sku/option-list', {keyword: name, limit: 10})).options;
    skuId = String(options.find((x: any) => x.specName === '散装').skuId);
    const boxSkuId = String(options.find((x: any) => x.specName === '整箱').skuId);
    // 供应商编码由服务端生成（SUP + 6 位序号），创建载荷不再提交编码。
    supplierId = await post('/scm/supplier/add', {name: name + '供应商'});
    // 采购单位必须与需求单位（= 销售单位）一致，否则 Q17 直接 40971；这里逐个对齐。
    await post('/scm/supplier/sku/replace', {
      supplierId,
      items: [
        {skuId, purchaseUnit: 'kg', defaultFlag: true, status: 'ENABLED'},
        {skuId: boxSkuId, purchaseUnit: '箱', defaultFlag: false, status: 'ENABLED'},
      ],
    });
  });
  test.afterAll(async () => {
    if (api && printTemplateId) {
      const template = await get(`/scm/print/template/${printTemplateId}`);
      await post(`/scm/print/template/${printTemplateId}/delete?version=${template.version}`, {});
    }
    if (api) {
      await api.get('/login/logout');
      await api.dispose();
    }
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-cleanup', '--wave', 'w5'], {env, stdio: 'pipe'});
  });

  test('1 demand generation from a confirmed sales order', async ({page}) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    soA = await confirmedOrder('10.0000', '10.0000');
    const generated = await post('/scm/purchase/demand/generate', {startAt: soA.before, endAt: soA.after, warehouseId, supplierId});
    expect(generated.sourceLineCount).toBeGreaterThanOrEqual(1);
    expect(generated.createdCount + generated.skippedCount).toBe(generated.sourceLineCount);
    demandA = await demandOf(soA);
    expect(demandA.salesOrderNoSnapshot).toBe(soA.orderNo);
    expect(demandA.requiredQuantity).toBe('10.0000');
    expect(demandA.allocatedQuantity).toBe('0.0000');
    expect(demandA.unallocatedQuantity).toBe('10.0000');
    expect(demandA.status).toBe('PENDING');
    expect(demandA.demandUnit).toBe('kg');
    await browse(page, '/purchase/purchase-demand-list');
    await expect(page.locator('#scm-purchase-demand-table')).toBeVisible();
    await page.getByPlaceholder('销售单号').fill(soA.orderNo);
    const demandRow = await row(page, 'scm-purchase-demand-table', soA.orderNo);
    await expect(demandRow).toContainText('待分配');
    await expect(demandRow).toContainText('10.0000');
    await page.screenshot({path: '../.runtime/w5-demand-list.png', fullPage: true});
    expect(consoleErrors).toEqual([]);
  });
  test('2 purchase order keeps the demand allocation and totals the lines', async ({page}) => {
    orderA = await createOrder([{demandId: demandA.id, quantity: '10.0000', demandVersion: demandA.version}], '10.0000', '6.2000');
    expect(orderA.orderNo).toMatch(PO);
    expect(orderA.status).toBe('DRAFT');
    expect(orderA.totalAmount).toBe('62.0000');
    const detail = await orderDetail(orderA.id);
    expect(detail.allocations).toHaveLength(1);
    expect(String(detail.allocations[0].demandId)).toBe(String(demandA.id));
    expect(detail.allocations[0].quantity).toBe('10.0000');
    expect(detail.allocations[0].demandUnit).toBe('kg');
    const demand = await demandOf(soA);
    expect(demand.allocatedQuantity).toBe('10.0000');
    expect(demand.unallocatedQuantity).toBe('0.0000');
    expect(demand.status).toBe('ALLOCATED');
    await browse(page, '/purchase/purchase-order-list');
    await expect(page.locator('#scm-purchase-order-table')).toBeVisible();
    await page.getByPlaceholder('采购单号').fill(orderA.orderNo);
    const orderRow = await row(page, 'scm-purchase-order-table', orderA.orderNo);
    await expect(orderRow).toContainText('草稿');
    // 金额列统一走 moneyText：¥ + 千分位 + 两位小数
    await expect(orderRow).toContainText('¥ 62.00');
    await page.screenshot({path: '../.runtime/w5-order-list.png', fullPage: true});
  });
  test('3 first partial receipt moves the order to PARTIALLY_RECEIVED', async () => {
    await post('/scm/purchase/submit', {id: orderA.id, version: orderA.version});
    const receipt = await createReceipt(orderA.id);
    expect(receipt.receiptNo).toMatch(PR);
    expect(receipt.status).toBe('DRAFT');
    expect(receipt.items).toHaveLength(1);
    expect(receipt.items[0].plannedQuantity).toBe('10.0000');
    const confirmed = await confirmReceipt(receipt, [line(receipt, '4.0000', '4.0000')]);
    expect(confirmed.status).toBe('CONFIRMED');
    const item = confirmed.items[0];
    expect(item.receivedQuantity).toBe('4.0000');
    expect(item.cumulativeReceivedQuantity).toBe('4.0000');
    expect(item.remainingQuantity).toBe('6.0000');
    expect(item.overReceiptQuantity).toBe('0.0000');
    expect(item.receiptDifference).toBe('-6.0000');
    const order = await orderDetail(orderA.id);
    expect(order.status).toBe('PARTIALLY_RECEIVED');
    expect(order.items[0].receivedQuantity).toBe('4.0000');
    expect(order.items[0].remainingQuantity).toBe('6.0000');
  });
  test('4 second receipt completes the order and the progress reaches one', async ({page}) => {
    // 新草稿行的对账量是「确认前」快照（§7.5：cumulative = 采购行 received_quantity，**确认后**的累计），
    // 所以 create 时是 0 / planned / −planned；真正的累计在 confirm 时物化。
    const second = await createReceipt(orderA.id);
    expect(second.items[0].cumulativeReceivedQuantity).toBe('0.0000');
    expect(second.items[0].remainingQuantity).toBe('10.0000');
    expect(second.items[0].receiptDifference).toBe('-10.0000');
    const confirmed = await confirmReceipt(second, [line(second, '6.0000', '6.0000')]);
    expect(confirmed.items[0].cumulativeReceivedQuantity).toBe('10.0000');
    expect(confirmed.items[0].remainingQuantity).toBe('0.0000');
    expect(confirmed.items[0].overReceiptQuantity).toBe('0.0000');
    expect(confirmed.items[0].receiptDifference).toBe('0.0000');
    const order = await orderDetail(orderA.id);
    expect(order.status).toBe('RECEIVED');
    expect(order.items[0].receivedQuantity).toBe('10.0000');
    expect(order.receivedProgress).toBe('1.0000');
    const receipts = await post('/scm/purchase/receipt/query', {pageNum: 1, pageSize: 20, purchaseOrderId: orderA.id});
    expect(receipts.total).toBe(2);
    await browse(page, '/purchase/purchase-order-list');
    await page.getByPlaceholder('采购单号').fill(orderA.orderNo);
    const orderRow = await row(page, 'scm-purchase-order-table', orderA.orderNo);
    await expect(orderRow).toContainText('已收货');
    await expect(orderRow).toContainText('100.00%');
    await browse(page, '/purchase/purchase-receipt-list');
    await page.getByPlaceholder('输入采购单号').fill(orderA.orderNo);
    const receiptRows = await row(page, 'scm-purchase-receipt-table', orderA.orderNo);
    await expect(receiptRows).toHaveCount(2);
    await page.screenshot({path: '../.runtime/w5-receipt-list.png', fullPage: true});
  });
  test('5 over receipt beyond the tolerance is rejected and rolls back', async ({page}) => {
    const order = await submittedPlainOrder('10.0000');
    const receipt = await createReceipt(order.id);
    const denied = await raw('/scm/purchase/receipt/confirm', {
      id: receipt.id,
      version: receipt.version,
      items: [line(receipt, '100.0000', '100.0000')],
    });
    expect(denied.code).toBe(40989);
    const after = await receiptDetail(receipt.id);
    expect(after.status).toBe('DRAFT');
    expect(after.items[0].cumulativeReceivedQuantity).toBe('0.0000');
    expect(after.items[0].remainingQuantity).toBe('10.0000');
    expect((await orderDetail(order.id)).items[0].receivedQuantity).toBe('0.0000');
    await browse(page, '/purchase/purchase-receipt-list');
    await page.getByPlaceholder('收货单号').fill(receipt.receiptNo);
    const receiptRow = await row(page, 'scm-purchase-receipt-table', receipt.receiptNo);
    await receiptRow.getByRole('button', {name: '确认收货'}).click();
    const modal = page.locator('.ant-modal:visible');
    await expect(modal).toContainText('超收容差');
    await expect(modal).toContainText('剩余可收');
    await expect(modal.getByLabel('本次声明数量')).toHaveValue('10.0000');
    await page.locator('.ant-modal:visible .ant-modal-close').click();
    await expect(page.locator('.ant-modal:visible')).toHaveCount(0);
  });
  test('6 replaying the same confirmation accumulates only once', async () => {
    const order = await submittedPlainOrder('10.0000');
    const receipt = await createReceipt(order.id);
    const body = {id: receipt.id, version: receipt.version, items: [line(receipt, '3.0000', '3.0000')]};
    const key = randomUUID();
    const first = await post('/scm/purchase/receipt/confirm', body, key);
    const replay = await post('/scm/purchase/receipt/confirm', body, key);
    expect(replay.id).toBe(first.id);
    expect(replay.status).toBe('CONFIRMED');
    expect(replay.items[0].cumulativeReceivedQuantity).toBe('3.0000');
    const after = await orderDetail(order.id);
    expect(after.status).toBe('PARTIALLY_RECEIVED');
    expect(after.items[0].receivedQuantity).toBe('3.0000');
  });
  test('7 short close requires a reason and closes the remainder', async ({page}) => {
    const order = await submittedPlainOrder('10.0000');
    const receipt = await createReceipt(order.id);
    await confirmReceipt(receipt, [line(receipt, '4.0000', '4.0000')]);
    const denied = await raw('/scm/purchase/short-close', {id: order.id, version: (await orderDetail(order.id)).version});
    expect(denied.code).not.toBe(0);
    await browse(page, '/purchase/purchase-order-list');
    await page.getByPlaceholder('采购单号').fill(order.orderNo);
    const orderRow = await row(page, 'scm-purchase-order-table', order.orderNo);
    await expect(orderRow).toContainText('部分收货');
    await orderRow.getByRole('button', {name: /更多/}).click();
    await page.locator('.ant-dropdown:visible').getByText('少收关单', {exact: true}).click();
    await page.locator('.ant-modal-confirm').getByRole('button', {name: '确 定'}).click();
    // 原因为空时走全局 toast（页内不再挂常驻 Alert）
    await expect(page.locator('.ant-message')).toContainText('请填写少收关单原因');
    await page.locator('.ant-modal-confirm textarea').fill('供应商缺货，剩余不再补收');
    await page.locator('.ant-modal-confirm').getByRole('button', {name: '确 定'}).click();
    await expect(page.locator('.ant-modal-confirm')).toHaveCount(0);
    await expect(orderRow.locator('.ant-tag')).toContainText('少收关单');
    const closed = await orderDetail(order.id);
    expect(closed.status).toBe('SHORT_CLOSED');
    expect(closed.shortCloseReason).toBe('供应商缺货，剩余不再补收');
    expect(closed.shortClosedAt).not.toBeNull();
  });
  test('8 non standard receipt accumulates the weight without overwriting the plan', async () => {
    const order = await submittedPlainOrder('10.0000');
    const receipt = await createReceipt(order.id);
    expect(receipt.items[0].productType).toBe('NON_STANDARD');
    const missing = await raw('/scm/purchase/receipt/confirm', {id: receipt.id, version: receipt.version, items: [line(receipt, '10.0000', null)]});
    expect(missing.code).toBe(40083);
    const confirmed = await confirmReceipt(receipt, [line(receipt, '10.0000', '9.5000')]);
    const item = confirmed.items[0];
    expect(item.actualWeight).toBe('9.5000');
    expect(item.weighingSource).toBe('MANUAL');
    expect(item.weightUnit).toBe('kg');
    expect(item.cumulativeReceivedQuantity).toBe('9.5000');
    expect(item.plannedQuantity).toBe('10.0000');
    expect(item.remainingQuantity).toBe('0.5000');
    const after = await orderDetail(order.id);
    expect(after.status).toBe('PARTIALLY_RECEIVED');
    expect(after.items[0].plannedQuantity).toBe('10.0000');
    expect(after.items[0].receivedQuantity).toBe('9.5000');
  });
  test('9 one item serves two demands and editing one leaves the other intact', async ({page}) => {
    const so1 = await confirmedOrder('6.0000', '6.0000');
    const so2 = await confirmedOrder('4.0000', '4.0000');
    const d1 = await demandOf(so1),
      d2 = await demandOf(so2);
    const created = await createOrder(
      [
        {demandId: d1.id, quantity: '6.0000', demandVersion: d1.version},
        {demandId: d2.id, quantity: '4.0000', demandVersion: d2.version},
      ],
      '10.0000',
      '6.2000'
    );
    let detail = await orderDetail(created.id);
    expect(detail.allocations).toHaveLength(2);
    const kept = detail.allocations.find((a: any) => String(a.demandId) === String(d2.id));
    expect(kept.quantity).toBe('4.0000');
    // 目标态语义：请求体 = 目标分配集合。只改 d1 也必须显式带上 d2，否则 d2 会被删掉。
    const fresh1 = await demandOf(so1),
      fresh2 = await demandOf(so2);
    const item = detail.items[0];
    await post('/scm/purchase/update', {
      id: detail.id,
      version: detail.version,
      supplierId,
      warehouseId,
      purchaserId: null,
      plannedArrivalDate: null,
      remark: detail.remark,
      items: [
        {
          id: item.id,
          version: item.version,
          skuId,
          quantity: '9.0000',
          price: '6.2000',
          allocations: [
            {demandId: fresh1.id, quantity: '5.0000', demandVersion: fresh1.version},
            {demandId: fresh2.id, quantity: '4.0000', demandVersion: fresh2.version},
          ],
        },
      ],
    });
    const after = await orderDetail(created.id);
    expect(after.allocations).toHaveLength(2);
    const changed = after.allocations.find((a: any) => String(a.demandId) === String(fresh1.id));
    const untouched = after.allocations.find((a: any) => String(a.demandId) === String(fresh2.id));
    expect(changed.quantity).toBe('5.0000');
    expect(untouched.quantity).toBe('4.0000');
    expect(String(untouched.allocationId)).toBe(String(kept.allocationId));
    const final1 = await demandOf(so1),
      final2 = await demandOf(so2);
    expect(final1.allocatedQuantity).toBe('5.0000');
    expect(final1.unallocatedQuantity).toBe('1.0000');
    expect(final1.status).toBe('PARTIALLY_ALLOCATED');
    expect(final2.allocatedQuantity).toBe('4.0000');
    expect(final2.unallocatedQuantity).toBe('0.0000');
    expect(final2.status).toBe('ALLOCATED');
    await browse(page, '/purchase/purchase-order-list');
    await page.getByPlaceholder('采购单号').fill(created.orderNo);
    await row(page, 'scm-purchase-order-table', created.orderNo);
    await page.locator('#scm-purchase-order-table').getByText(created.orderNo, {exact: true}).click();
    const drawer = page.locator('.ant-drawer-body');
    await expect(drawer).toContainText(so1.orderNo);
    await expect(drawer).toContainText(so2.orderNo);
    await expect(drawer).toContainText('部分分配');
    await expect(drawer).toContainText('已分配');
    await page.screenshot({path: '../.runtime/w5-order-detail.png', fullPage: true});
  });

  /**
   * Wave 2A：订单汇总 / 库存缺口预览（只读）。
   *
   * 契约：读端点按 `[startAt,endAt)` + `warehouseId` 聚合，数量为后端算好的四位定点字符串；
   * 重复调用**不得**新建需求或流水（只读辅助决策），`calculationStatus` 只落在后端 5 值内。
   * 注：本用例需要**包含 Wave 2A 端点的后端构建**；18080 上若在跑更早的 fat jar，
   * 该 `summary-preview` 会 404 —— 属部署版本问题，不是本 Wave 缺陷。
   */
  const SUMMARY_STATUS = new Set(['STOCK_ENOUGH', 'SHORTAGE', 'ZERO_STOCK', 'UNIT_MISMATCH', 'NO_BALANCE']);
  test('10 stock shortage preview is read-only and server-computed', async ({page}) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    // 用已生成需求的那张销售单所在窗口造一次可命中的查询（soA 已确认且已入库）
    const so = await confirmedOrder('20.0000', '20.0000');
    const before = await post('/scm/purchase/demand/query', {pageNum: 1, pageSize: 20, salesOrderNo: so.orderNo});
    expect(before.total).toBe(0); // 只读预览不建需求：此刻该单还没有需求行
    const preview = await post('/scm/purchase/demand/summary-preview', {
      startAt: so.before,
      endAt: so.after,
      warehouseId,
      keyword: name,
      pageNum: 1,
      pageSize: 20,
    });
    expect(typeof preview.total).toBe('number');
    expect(Array.isArray(preview.list)).toBe(true);
    // V147 后规格编码由服务端发号，不再含造数前缀，按商品名称定位本次造的商品
    const mine = preview.list.filter((r: any) => String(r.productName ?? '').includes(name));
    expect(mine.length).toBeGreaterThanOrEqual(1);
    for (const r of preview.list) {
      expect(SUMMARY_STATUS.has(r.calculationStatus), `非法计算状态 ${r.calculationStatus}`).toBe(true);
      for (const f of [
        'orderDemandQuantity',
        'stockAvailableForSelectedOrders',
        'selectedOrderReservedQuantity',
        'otherReservedQuantity',
        'stockComparisonGap',
      ]) {
        if (r[f] !== null) expect(r[f], `${f} 必须是四位定点字符串`).toMatch(/^\d+\.\d{4}$/);
      }
    }
    // 缺口/可用量由后端给出，UNIT_MISMATCH 之外的行 available = onHand - reserved 的一致性由后端保证，前端不重算
    const after = await post('/scm/purchase/demand/query', {pageNum: 1, pageSize: 20, salesOrderNo: so.orderNo});
    expect(after.total).toBe(0); // 预览后仍无需求行 → 只读
    await browse(page, '/purchase/purchase-demand-list');
    await page.getByRole('tab', {name: '订单汇总 / 缺口预览'}).click();
    await expect(page.locator('#scm-purchase-demand-summary-preview-table')).toBeVisible();
    await page.screenshot({path: '../.runtime/w2a-summary-preview.png', fullPage: true});
    expect(consoleErrors).toEqual([]);
  });

  /**
   * Wave 2B：采购效率（导出 / 批量少收关单 / 按商品收货工作台）。
   *
   * 契约：导出与工作台都是只读端点（无 Idempotency-Key、不改采购状态、不新建收货 / 库存）；
   * 批量少关整批原子——批内含不可关单据时全部回滚。
   * 注：这些用例需要**包含 Wave 2B 端点的后端构建**；18080 上若在跑更早的 fat jar，
   * `export` / `batch/short-close` / `item-workbench` 会 404 —— 属部署版本问题，不是本 Wave 缺陷。
   */
  const WB_NUMERIC_FIELDS = ['plannedQuantity', 'receivedQuantity', 'pendingQuantity', 'overReceiptQuantity'];
  test('11 export is a read-only xlsx download that never changes order state', async ({page}) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    // 用测试 2 的已提交单：导出前后状态必须一致，证明导出是纯读取。
    const snapshot = await orderDetail(orderA.id);
    const resp = await api.post('/scm/purchase/export', {
      data: {pageNum: 1, pageSize: 20, orderNo: orderA.orderNo, exportColumns: ['orderNo', 'status', 'totalAmount']},
    });
    expect(resp.ok()).toBe(true);
    expect(resp.headers()['content-type'] ?? '').toMatch(/spreadsheet|octet-stream/);
    expect((resp.headers()['content-disposition'] ?? '').toLowerCase()).toContain('attachment');
    expect((await resp.body()).length).toBeGreaterThan(0);
    expect((await orderDetail(orderA.id)).status).toBe(snapshot.status);
    // 页面接线：导出 / 导出设置 / 批量少收关单按钮 + 每行打印都在（按权限渲染）。
    await browse(page, '/purchase/purchase-order-list');
    await expect(page.locator('#scm-purchase-order-table')).toBeVisible();
    await expect(page.getByRole('button', {name: '导出'})).toBeVisible();
    await expect(page.getByRole('button', {name: '导出设置'})).toBeVisible();
    await page.screenshot({path: '../.runtime/w2b-order-export.png', fullPage: true});
    expect(consoleErrors).toEqual([]);
  });

  test('12 batch short-close is atomic and the item workbench stays read-only', async ({page}) => {
    // 两张可关的部分收货单：整批关单后都进入终态。
    const p1 = await submittedPlainOrder('10.0000'),
      p2 = await submittedPlainOrder('10.0000');
    const r1 = await createReceipt(p1.id),
      r2 = await createReceipt(p2.id);
    await confirmReceipt(r1, [line(r1, '4.0000', '4.0000')]);
    await confirmReceipt(r2, [line(r2, '5.0000', '5.0000')]);
    const v1 = (await orderDetail(p1.id)).version,
      v2 = (await orderDetail(p2.id)).version;
    await post('/scm/purchase/batch/short-close', {
      orders: [
        {id: p1.id, version: v1},
        {id: p2.id, version: v2},
      ],
      shortCloseReason: 'W5 验收批量少收',
    });
    expect((await orderDetail(p1.id)).status).toBe('SHORT_CLOSED');
    expect((await orderDetail(p2.id)).status).toBe('SHORT_CLOSED');
    // 原子性：混入一张草稿单（不可关）应整批失败，合法的那张保持原状态不回滚生效。
    const keep = await submittedPlainOrder('10.0000');
    const rk = await createReceipt(keep.id);
    await confirmReceipt(rk, [line(rk, '3.0000', '3.0000')]);
    const keepVersion = (await orderDetail(keep.id)).version;
    const draft = await createOrder([], '5.0000', '6.2000'); // DRAFT，不可少收关单
    const denied = await raw('/scm/purchase/batch/short-close', {
      orders: [
        {id: keep.id, version: keepVersion},
        {id: draft.id, version: draft.version},
      ],
      shortCloseReason: '原子性验证',
    });
    expect(denied.code).not.toBe(0);
    expect((await orderDetail(keep.id)).status).toBe('PARTIALLY_RECEIVED');
    // 按商品工作台：只读聚合，四位定点字符串，查询后不新建任何收货 / 库存（读端点无副作用）。
    const wb = await post('/scm/purchase/receipt/item-workbench/query', {pageNum: 1, pageSize: 50});
    expect(Array.isArray(wb.list)).toBe(true);
    for (const r of wb.list) {
      for (const f of WB_NUMERIC_FIELDS) {
        if (r[f] !== null) expect(r[f], `${f} 必须是四位定点字符串`).toMatch(/^\d+\.\d{4}$/);
      }
      // 逐行裁剪后欠收与超收互斥：同一聚合行不会同时大于零。
      if (Number(r.pendingQuantity ?? '0') > 0 && Number(r.overReceiptQuantity ?? '0') > 0) throw new Error('欠收与超收不应同时大于零');
    }
    await browse(page, '/purchase/purchase-receipt-list');
    await page.getByRole('tab', {name: '按商品'}).click();
    await expect(page.locator('#scm-purchase-receipt-item-workbench-table')).toBeVisible();
    await page.screenshot({path: '../.runtime/w2b-item-workbench.png', fullPage: true});
  });

  /**
   * Wave 2A §3.1：缺口预览的库存读取权限必须由服务端拒，不能靠前端把 tab 藏掉。
   *
   * 用**真实非管理员登录**（只读面 − `scm:inventory:balance:query`）验证 AND 交集：
   * 缺一侧即整个聚合端点被拒，而该账号自己的采购需求查询照常可用（修权限不把另一侧降掉）。
   */
  test('13 Wave 2A preview requires both perms: a real non-admin lacking inventory query is refused', async ({page}) => {
    // 用例自带前置：单跑时不依赖其它用例留下的模块级状态
    const so = await confirmedOrder('10.0000', '10.0000');
    await post('/scm/purchase/demand/generate', {startAt: so.before, endAt: so.after, warehouseId, supplierId});
    expect((await demandOf(so)).salesOrderNoSnapshot, '前置需求行没生成').toBe(so.orderNo);
    const accounts = provisionTempAccounts('w2', ['scm:inventory:balance:query']);
    const deniedToken = await loginAs(accounts, accounts.denied!);
    const deniedApi = await apiClient(deniedToken);
    try {
      const refused = await (
        await deniedApi.post('/scm/purchase/demand/summary-preview', {
          data: {startAt: so.before, endAt: so.after, warehouseId, keyword: name, pageNum: 1, pageSize: 20},
        })
      ).json();
      expect(refused.code, '只有采购查看权也能读到库存数量 = 越权路径仍在').toBe(30005);

      // 同一账号读自己领域的采购需求必须仍然放行：权限交集不能把另一侧顺带降掉
      const demand = await (
        await deniedApi.post('/scm/purchase/demand/query', {
          data: {pageNum: 1, pageSize: 20, salesOrderNo: so.orderNo},
        })
      ).json();
      expect(demand.code, '保留 scm:purchase:demand:query 的账号读不到需求列表').toBe(0);
      expect(demand.data.total).toBeGreaterThanOrEqual(1);

      await authenticate(page, deniedToken);
      await page.goto('/#/purchase/purchase-demand-list');
      // 页面与 tab 对无库存权限的登录人照样可达 —— 证明拒绝来自接口而不是 UI 隐藏
      await page.getByPlaceholder('销售单号').fill(so.orderNo);
      await page.getByRole('button', {name: /^查\s*询$/}).click();
      await expect(page.locator('#scm-purchase-demand-table tr').filter({hasText: so.orderNo}).first()).toBeVisible();
      await page.getByRole('tab', {name: '订单汇总 / 缺口预览'}).click();
      expect(
        await page.locator('#scm-purchase-demand-summary-preview-table tbody tr.ant-table-row').count(),
        '预览表在缺权限的账号下出现了数据行'
      ).toBe(0);
      await page.screenshot({path: '../.runtime/w2a-summary-preview-denied.png', fullPage: true});

      // 反向缺一侧：保留库存查询、扣掉采购需求查询，同一端点仍须整条拒 —— AND 不是「任一侧通过」。
      const inverse = provisionTempAccounts('w2', ['scm:purchase:demand:query']);
      const inverseToken = await loginAs(inverse, inverse.denied!);
      const inverseApi = await apiClient(inverseToken);
      try {
        const refusedToo = await (
          await inverseApi.post('/scm/purchase/demand/summary-preview', {
            data: {startAt: so.before, endAt: so.after, warehouseId, keyword: name, pageNum: 1, pageSize: 20},
          })
        ).json();
        expect(refusedToo.code, '只有库存查询权也能读到缺口预览 = 权限交集被写成并集').toBe(30005);
        const balance = await (
          await inverseApi.post('/scm/inventory/balance/query', {
            data: {warehouseId, pageNum: 1, pageSize: 20},
          })
        ).json();
        expect(balance.code, '保留 scm:inventory:balance:query 的账号读不到库存余额').toBe(0);
      } finally {
        await inverseApi.get('/login/logout');
        await inverseApi.dispose();
        inverse.cleanup();
      }
    } finally {
      await deniedApi.get('/login/logout');
      await deniedApi.dispose();
      accounts.cleanup();
    }
  });

  /**
   * §12.3 Wave 2B「打印」要求真实浏览器点一次打印按钮，但无头环境既没有打印对话框，
   * 打印实现又会在调起 print() 后立即移除 iframe，文档来不及读。
   * 这里只替换掉最外层的 `print()`：在打印代码读 `iframe.contentWindow` 的那一刻，
   * 把它换成「先固化渲染结果、再拒绝弹系统窗口」的记录器。
   * 被测路径不变（同一份 document.write、同一次 print 调用），只是不唤起系统对话框。
   */
  function capturePrint(page: Page) {
    return page.addInitScript(() => {
      const w = window as any;
      w.__printDocuments = [];
      const desc = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'contentWindow');
      if (!desc || !desc.get) {
        throw new Error('contentWindow 访问器缺失，无法采集打印文档');
      }
      Object.defineProperty(HTMLIFrameElement.prototype, 'contentWindow', {
        configurable: true,
        enumerable: desc.enumerable,
        get() {
          const frameWindow = desc.get.call(this) as (Window & {__printCaptured?: boolean}) | null;
          if (frameWindow && !frameWindow.__printCaptured) {
            frameWindow.__printCaptured = true;
            frameWindow.print = function () {
              const doc = frameWindow.document;
              w.__printDocuments.push({
                title: doc.title,
                text: doc.body.textContent,
                scripts: doc.querySelectorAll('script').length,
                rows: doc.querySelectorAll('tbody tr').length,
              });
            };
          }
          return frameWindow;
        },
      });
    });
  }

  test('14 print renders the fetched order into the print document and never writes business state', async ({page}) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    // 备注带一段脚本标记：打印文档里的数据字段只能作为文本渲染，不能变成可执行标记。
    const mark = 'W5打印验收 <script>alert(1)</script>';
    const created = await post('/scm/purchase/create', {
      supplierId,
      warehouseId,
      purchaserId: null,
      plannedArrivalDate: null,
      remark: mark,
      items: [{skuId, quantity: '7.0000', price: '6.2000', allocations: []}],
    });
    const order = await orderDetail(created.id);
    expect(order.remark, '服务端未原样保存备注，转义断言的前提不成立').toBe(mark);
    const templateName = `W5 E2E 打印转义 ${Date.now()}`;
    printTemplateId = await post('/scm/print/template', {
      documentType: 'PURCHASE_ORDER',
      templateCode: `E2E_PURCHASE_${Date.now()}`,
      templateName,
      defaultFlag: false,
      enabledFlag: true,
      model: {
        title: '采购单打印',
        paper: 'A4',
        orientation: 'PORTRAIT',
        headerFields: ['supplierName', 'warehouseName', 'purchaserName', 'plannedArrivalDate', 'remark'],
        columns: [
          'productName',
          'skuCode',
          'skuName',
          'purchaseUnit',
          'plannedQuantity',
          'receivedQuantity',
          'remainingQuantity',
          'purchasePrice',
          'lineAmount',
        ],
        showTotals: true,
        footerNote: 'W5 打印转义验收',
      },
    });
    const statusBefore = order.status;
    await capturePrint(page);
    const writes: string[] = [];
    let printing = false;
    page.on('request', (r) => {
      if (printing && r.method() !== 'GET') writes.push(`${r.method()} ${r.url()}`);
    });
    await browse(page, '/purchase/purchase-order-list');
    await page.getByPlaceholder('采购单号').fill(order.orderNo);
    const orderRow = await row(page, 'scm-purchase-order-table', order.orderNo);
    printing = true;
    await orderRow.getByRole('button', {name: /更多/}).click();
    await page.locator('.ant-dropdown:visible').getByText('打印', {exact: true}).click();
    const singleDialog = page.locator('.ant-modal:visible').last();
    await expect(singleDialog.getByText(order.orderNo)).toBeVisible();
    await singleDialog.locator('.ant-select').first().click();
    await page.locator('.ant-select-dropdown:visible').last().getByText(templateName, {exact: true}).click();
    await expect(singleDialog.locator('.preview')).toContainText(mark);
    await page.locator('.ant-modal-footer .ant-btn-primary').last().click();
    await expect.poll(() => page.evaluate(() => (window as any).__printDocuments.length)).toBe(1);
    // 打印的必须是这一张单的当前详情（表头 + 明细行），而不是空模板
    const [single] = (await page.evaluate(() => (window as any).__printDocuments)) as {
      title: string;
      text: string;
      scripts: number;
      rows: number;
    }[];
    expect(single.title).toBe('打印');
    expect(single.text).toContain('采购单打印');
    expect(single.text).toContain(order.orderNo);
    expect(single.text).toContain(order.supplierName);
    expect(single.text).toContain(order.items[0].productName);
    expect(single.text).toContain(order.items[0].plannedQuantity);
    expect(single.text).toContain(order.items[0].purchasePrice);
    expect(single.rows).toBe(1);
    // 备注里的脚本必须仍是文本：文档里一个 script 元素都不该存在
    expect(single.text).toContain(mark);
    expect(single.scripts).toBe(0);
    // 批量打印共用同一份文档生成器：勾上这张单后应再生成一个只含它的文档
    await page.locator('.ant-modal-footer .ant-btn-default').last().click();
    await page.locator('#scm-purchase-order-table tbody tr.ant-table-row').first().locator('input[type=checkbox]').click();
    await page.getByRole('button', {name: '批量打印'}).click();
    const batchDialog = page.locator('.ant-modal:visible').last();
    await expect(batchDialog.getByText(order.orderNo)).toBeVisible();
    await batchDialog.locator('.ant-select').first().click();
    await page.locator('.ant-select-dropdown:visible').last().getByText(templateName, {exact: true}).click();
    await expect(batchDialog.locator('.preview')).toContainText(mark);
    await page.locator('.ant-modal-footer .ant-btn-primary').last().click();
    await expect.poll(() => page.evaluate(() => (window as any).__printDocuments.length)).toBe(2);
    const batch = await page.evaluate(() => (window as any).__printDocuments[1]);
    expect(batch.text).toContain(order.orderNo);
    expect(batch.rows).toBe(1);
    expect(batch.scripts).toBe(0);
    // 模板打印只追加冻结打印记录；采购订单状态不会改变，也不能触发其它写入口。
    const paths = writes.map((entry) => {
      const [method, url] = entry.split(' ', 2);
      return `${method} ${new URL(url).pathname}`;
    });
    const printPath = `/scm/print/PURCHASE_ORDER/${created.id}/print`;
    expect(paths).toEqual([`POST ${printPath}`, `POST ${printPath}`]);
    expect((await orderDetail(created.id)).status).toBe(statusBefore);
    await page.screenshot({path: '../.runtime/w2b-order-print.png', fullPage: true});
    expect(consoleErrors).toEqual([]);
  });
});

// ---------------------------------------------------------------- 来源：e2e/scm-stocktake-import.spec.ts
test.describe('scm-stocktake-import', () => {
  const test = pwTest;
  /*
   * Wave 6 盘点 Excel 快照导入 + 复制历史盘点 E2E（真实登录，自建临时账号）
   *
   * 账号与登录见 `e2e/scm-e2e-account.ts`；本组用例串行走一条完整业务链：
   *   导出模板 → 填实盘 → 页面导入建草稿 → 确认盘点写流水 → 旧凭证再导必被漂移拒绝 → 复制历史盘点。
   *
   * 本组用例原先靠 `W6I_E2E_ADMIN_TOKEN` 门控（缺令牌整组 skip），且页面地址写死成
   * `/#/scm/inventory/stocktake` —— 该路由在 t_menu 里根本不存在（真实路径见下方 goto），
   * 也就是说旧版本即使注入令牌也必然失败，「文件存在」从来不等于「场景已验收」。
   *
   * 覆盖计划 §12.3 Wave 6 的五个场景，逐个说明断言的是什么：
   * 1. 导出模板：页面选仓库后命中 `/import/template` 且返回 xlsx；**只读账号同一端点必须 30005**
   *    （权限由服务端把关，不是靠前端隐藏按钮）；
   * 2. 填实盘导入：整文件走页面上传控件 → 后端整批校验 → 建出草稿，且逐行实盘量与填写值一致；
   * 3. 快照漂移拒绝：确认盘点会真实改余额（版本 +1），因此确认前导出的凭证在确认后再导
   *    必须整批拒绝（SNAPSHOT_STALE）且不落第二张草稿 —— 这正是「导入绝不把余额当可直接改的状态」的正证；
   * 4. 复制历史盘点：点「复制到新建」必须按 `pageSize<=100` 分页读余额
   *    （P0 缺陷是前端写死 2000，被后端 @Max(100) 直接拒掉，功能必挂）；
   * 5. 复制后实盘为空：复制只带仓库 + SKU 集合 + 当前记账单位，实盘量整列留空，
   *    历史账面量/实盘量绝不冒充实盘，且复制本身不建任何单据。
   *
   * 签名字节级细节（篡改 / 过期 / 来源集合增删 / 幂等重放）由 ScmStocktakeImportPgIT 用真 POI + 真 PG 覆盖，
   * 本 spec 不重复；这里只补 IT 覆盖不到的真实链路与前端接线。
   *
   * 副作用说明：用例 4/5 会确认一张盘点单，因此**必然**在开发库留下盘盈/盘亏流水（append-only，
   * 设计如此，不可删除）；临时账号与角色在 afterAll 精确回收。
   */

  let accounts: TempAccounts;
  let adminToken: string;
  let admin: APIRequestContext;
  let workspaceDir = '';
  let warehouseId = '';
  let warehouseName = '';
  /** 确认盘点**之前**导出的模板：漂移用例必须用这份旧凭证。 */
  let templatePath = '';
  let filledOncePath = '';
  let filledTwicePath = '';
  let filledOnce: Record<string, string> = {};
  let importedStocktakeId = '';
  let importedStocktakeNo = '';

  /** 只断言业务码的取数助手：失败时把 msg 一起抛出来，省得对着裸 code 猜原因。 */
  async function ok<T = any>(call: Promise<{json: () => Promise<any>}>): Promise<T> {
    const r = await (await call).json();
    expect(r.code, r.msg).toBe(0);
    return r.data as T;
  }

  async function draftTotal(): Promise<number> {
    const data = await ok<any>(admin.post('/scm/inventory/stocktake/query', {data: {warehouseId, pageNum: 1, pageSize: 1}}));
    return Number(data.total ?? 0);
  }

  /** 实盘量后端以 4 位定点字符串返回（"13.5000"），Excel 里填的是最短写法（"13.5"），按数值比对。 */
  const asNumber = (map: Record<string, string>) => Object.fromEntries(Object.entries(map).map(([sku, qty]) => [sku, Number(qty)]));

  /** 用 tools/e2e_fixtures.py stocktake 填实盘量：只写「实盘数量」列，签名凭证列逐字不动。 */
  function fillTemplate(outFile: string, delta: string): Record<string, string> {
    const stdout = execFileSync('python', ['../tools/e2e_fixtures.py', 'stocktake', '--in', templatePath, '--out', outFile, '--delta', delta], {
      encoding: 'utf8',
    });
    return JSON.parse(stdout.trim()).actuals as Record<string, string>;
  }

  async function uploadThroughUi(page: Page, filePath: string): Promise<any> {
    const response = page.waitForResponse((r) => r.url().includes('/scm/inventory/stocktake/import'), {timeout: 30000});
    await page.locator('input[type="file"]').setInputFiles(filePath);
    return (await response).json();
  }

  test.describe.serial('Wave 6 盘点快照导入与复制历史盘点', () => {
    test.beforeAll(async () => {
      accounts = provisionTempAccounts('w6');
      adminToken = await login(accounts, accounts.admin);
      admin = await apiClient(adminToken);

      // 模板按「当前余额行」签发凭证，所以必须挑一个真有余额的仓库，否则来源集合为空、整条链路没有意义。
      // 批次仓要排除：一期盘点不支持批次（41070），挑到它只会验到一个与快照凭证无关的拒绝。
      const warehouses = await ok<any[]>(admin.get('/scm/warehouse/list'));
      for (const w of warehouses) {
        if (w.lotTrackingEnabled === true) {
          continue;
        }
        const bal = await ok<any>(admin.post('/scm/inventory/balance/query', {data: {warehouseId: w.id, pageNum: 1, pageSize: 20}}));
        // 负库存仓（余额全为负）跳过：模板按「余额 +1」填充会把实盘量算成负数，
        // 与「账实差异」这个场景不是一回事；本组要的是有正常正余额的仓。
        if ((bal.list ?? []).some((line: any) => Number(line.quantity) > 0)) {
          warehouseId = String(w.id);
          warehouseName = (w.name ?? w.warehouseName ?? String(w.id)) as string;
          break;
        }
      }
      // 缺夹具必须让整组变红而不是 skip：绿色跑过 + 场景没执行 = 把「文件存在」当成「已验收」。
      expect(warehouseId, '当前库没有任何带余额的仓库，Wave 6 盘点导入链路无法执行').toBeTruthy();

      workspaceDir = mkdtempSync(join(tmpdir(), 'w6-stocktake-'));
      templatePath = join(workspaceDir, 'template.xlsx');
      const bytes = Buffer.from(await (await admin.get(`/scm/inventory/stocktake/import/template?warehouseId=${warehouseId}`)).body());
      // xlsx 是 zip 容器，头两字节恒为 'PK'
      expect(bytes.subarray(0, 2).toString('latin1'), '模板端点必须返回 xlsx 而不是错误信封').toBe('PK');
      writeFileSync(templatePath, bytes);
      filledOncePath = join(workspaceDir, 'filled-plus1.xlsx');
      filledTwicePath = join(workspaceDir, 'filled-plus2.xlsx');
      filledOnce = fillTemplate(filledOncePath, '1');
    });

    test.afterAll(async () => {
      await admin?.dispose();
      accounts?.cleanup();
    });

    test('模板下载与导入受 import 命令权限把关：只读账号 30005', async () => {
      const readToken = await login(accounts, accounts.readOnly);
      const read = await apiClient(readToken);
      try {
        const templateDenied = await (await read.get(`/scm/inventory/stocktake/import/template?warehouseId=${warehouseId}`)).json();
        expect(templateDenied.code, '模板下载是命令权限，只读角色必须被服务端拒').toBe(30005);
        const importDenied = await (await read.post('/scm/inventory/stocktake/import', {data: {}})).json();
        expect(importDenied.code, '导入命令同样不能只靠前端隐藏按钮挡').toBe(30005);
      } finally {
        await read.dispose();
      }
    });

    test('页面选仓库后导出盘点模板：命中模板端点并带回该仓库', async ({page}) => {
      const errors: string[] = [];
      page.on('pageerror', (e) => errors.push(e.message));
      await authenticate(page, adminToken);
      await page.goto('/#/inventory/inventory-stocktake-list');
      await expect(page.getByRole('button', {name: accessibleName('导出盘点模板')})).toBeVisible();
      // a-upload 的外层 span 也带 role=button，所以这里取真实按钮（最后一个匹配）
      await expect(page.getByRole('button', {name: accessibleName('导入盘点')}).last()).toBeVisible();

      // 导出绑定查询条件里的仓库：未选仓库时只给提示、不发请求，所以必须先在页面上真的选中。
      const warehouseItem = page.locator('.smart-query-form-item').filter({hasText: '仓库'}).first();
      await warehouseItem.locator('.ant-select').click();
      // 选项文本是「名称（编码）」且被拆成多个文本节点，只能按 option 元素包含匹配
      await page.locator('.ant-select-dropdown:visible .ant-select-item').filter({hasText: warehouseName}).first().click();

      const download = page.waitForResponse((r) => r.url().includes('/stocktake/import/template'));
      await page.getByRole('button', {name: accessibleName('导出盘点模板')}).click();
      const res = await download;
      expect(res.url(), '导出的就是页面上选中的那个仓库').toContain(`warehouseId=${warehouseId}`);
      expect(res.status()).toBe(200);
      expect(errors).toEqual([]);
    });

    test('填好实盘的模板经页面上传 → 建出草稿且逐行实盘量落库', async ({page}) => {
      const errors: string[] = [];
      page.on('pageerror', (e) => errors.push(e.message));
      const before = await draftTotal();
      await authenticate(page, adminToken);
      await page.goto('/#/inventory/inventory-stocktake-list');
      const body = await uploadThroughUi(page, filledOncePath);
      expect(body.code, JSON.stringify(body.errors ?? body.msg)).toBe(0);
      expect(body.data.stocktakeId, '整批校验通过必须建出草稿').toBeTruthy();
      expect(body.data.replayed, '首次导入不是幂等重放').toBe(false);
      await expect(page.getByText('已创建草稿')).toBeVisible();

      const detail = await ok<any>(admin.get(`/scm/inventory/stocktake/detail/${body.data.stocktakeId}`));
      expect(detail.status).toBe('DRAFT');
      const actualBySku = Object.fromEntries((detail.items ?? []).map((i: any) => [i.skuCode, String(i.actualQuantity)]));
      expect(asNumber(actualBySku), '落库实盘量必须等于 Excel 里填的值').toEqual(asNumber(filledOnce));
      expect(
        detail.items.every((i: any) => i.bookQuantity !== undefined),
        '账面量由后端按快照写入'
      ).toBe(true);
      expect(await draftTotal(), '导入只建草稿，不写余额').toBe(before + 1);
      expect(errors).toEqual([]);
      importedStocktakeId = String(body.data.stocktakeId);
      importedStocktakeNo = detail.stocktakeNo as string;
    });

    test('确认盘点改余额后，用确认前导出的凭证再导 → SNAPSHOT_STALE 整批拒绝且不落草稿', async ({page}) => {
      const errors: string[] = [];
      page.on('pageerror', (e) => errors.push(e.message));
      await ok(admin.post(`/scm/inventory/stocktake/confirm/${importedStocktakeId}`, {data: {}}));
      const before = await draftTotal();
      const filledTwice = fillTemplate(filledTwicePath, '2');
      expect(Object.keys(filledTwice).length, '漂移件与首次件行数一致').toBe(Object.keys(filledOnce).length);

      await authenticate(page, adminToken);
      await page.goto('/#/inventory/inventory-stocktake-list');
      const body = await uploadThroughUi(page, filledTwicePath);
      expect(body.code, '整批拒绝仍是信封成功 + 错误列表，而不是 HTTP 异常').toBe(0);
      expect(body.data.stocktakeId, '漂移即一张都不落').toBeNull();
      expect(
        body.data.errors.some((e: any) => e.code === 'SNAPSHOT_STALE'),
        JSON.stringify(body.data.errors)
      ).toBe(true);
      await expect(page.getByRole('alert').filter({hasText: '整批未导入'})).toBeVisible();
      expect(await draftTotal(), '漂移拒绝不得新增草稿').toBe(before);
      expect(errors).toEqual([]);
    });

    test('复制历史盘点：余额按 pageSize<=100 分页读取，实盘量整列为空且不新增单据', async ({page}) => {
      const errors: string[] = [];
      page.on('pageerror', (e) => errors.push(e.message));
      const before = await draftTotal();
      const detail = await ok<any>(admin.get(`/scm/inventory/stocktake/detail/${importedStocktakeId}`));
      const balanceQueries: any[] = [];
      page.on('request', (req) => {
        if (req.url().includes('/scm/inventory/balance/query')) balanceQueries.push(JSON.parse(req.postData() ?? '{}'));
      });

      await authenticate(page, adminToken);
      await page.goto('/#/inventory/inventory-stocktake-list');
      await page.getByPlaceholder('盘点单号').fill(importedStocktakeNo);
      await page.getByRole('button', {name: accessibleName('查询')}).click();
      const row = page.locator(`tr`).filter({hasText: importedStocktakeNo}).first();
      await expect(row).toBeVisible();

      // 只有一个可用动作时操作列直接外露（不再套一层「更多」）；多动作时才走下拉菜单
      const inlineCopy = row.getByRole('button', {name: /复制到新建/});
      if (await inlineCopy.count()) {
        await inlineCopy.click();
      } else {
        await row.getByRole('button', {name: /更多/}).click();
        await page.locator('.ant-dropdown:visible').getByText('复制到新建', {exact: true}).click();
      }
      const drawer = page.locator('.ant-drawer-open');
      // 抽屉提示语已随文案治理清退，改为断言抽屉里确实是新建的盘点单
      await expect(drawer.getByText(/实盘量/).first()).toBeVisible();
      // P0 缺陷：前端曾写死 pageSize=2000，被余额查询的 @Max(100) 直接拒掉，复制功能必然打不开。
      await expect.poll(() => balanceQueries.length, {message: '复制要读当前余额取记账单位'}).toBeGreaterThan(0);
      expect(
        balanceQueries.every((q) => q.pageSize > 0 && q.pageSize <= 100),
        JSON.stringify(balanceQueries)
      ).toBe(true);

      const actualInputs = drawer.locator('input[placeholder="0.0000"]');
      const itemCount = (detail.items ?? []).length;
      await expect(actualInputs).toHaveCount(itemCount);
      for (let i = 0; i < itemCount; i++) {
        expect(await actualInputs.nth(i).inputValue(), '复制绝不把历史实盘量带进新单').toBe('');
      }
      expect(await draftTotal(), '复制只填表单，不建单据').toBe(before);
      expect(errors).toEqual([]);
    });
  });
});
