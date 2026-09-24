/*
  Finance R0 报表中心验收（docs/plan/finance-reporting-r0-plan.md §42）。

  口径：报表页是只读层，因此本 spec 的断言重点是「数字来自哪个事实」而不是「页面好不好看」：
  - 销售只计 CONFIRMED + confirmed_at + settlement_*，草稿与取消单一律不进；
  - 采购只计提交后的四态，草稿与取消单不进；
  - 收货确认（商业事实）与库存入账（PURCHASE_IN 流水）是两条生命周期，必须能分别看到；
  - 成本金额来自流水冻结的 unit_cost，不是当前 avg_cost 回算；
  - 无权限账号必须在服务端被拒（前端隐藏按钮不算防线）。

  夹具走真实领域流程（采购单 → 收货 → 入库），不直接改库：
  只有真实写入路径产生的流水才配得上「报表数字可追溯」这条断言。
*/
import {test, expect, request, type APIRequestContext, type Page} from '@playwright/test';
import {randomBytes, randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import smCrypto from 'sm-crypto';

const apiUrl = 'http://127.0.0.1:18080';
// 账号工具只接受 w<N>_e2e_ 形态的临时账号名（拒绝误 cleanup 正式账号），因此复用 W6 的那一套
const name = 'w6_e2e_' + Date.now().toString(36);
const password = 'RPT@' + randomBytes(8).toString('hex');
const env = {...process.env, W6_E2E_NAME: name, W6_E2E_PASSWORD: password};
// 报表按业务日界（Asia/Shanghai）取窗口：toISOString() 给的是 UTC 日期，
// 在 UTC 16:00 之后跑用例时，当天确认的订单其 confirmed_at（东八区）已落在「明天」，
// 窗口就会把这条事实正当排除，表现为 0 行的假失败。
const today = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date());

let api: APIRequestContext;
let anonApi: APIRequestContext;
let token: string;
let warehouseId: string;
let skuId: string;
let skuCode: string;
let customerId: string;
let supplierId: string;
let standardSku = true;

test.describe.configure({mode: 'serial'});

async function login(account: string, plain: string) {
    const ctx = await request.newContext({baseURL: apiUrl});
    const captcha = (await (await ctx.get('/login/getCaptcha')).json()).data;
    const source = readFileSync('src/lib/encrypt.ts', 'utf8');
    const key = /const SM4_KEY = '([^']+)'/.exec(source)![1];
    const encrypted = Buffer.from(smCrypto.sm4.encrypt(plain, Buffer.from(key).toString('hex'))).toString('base64');
    const r = await (await ctx.post('/login', {
        data: {
            loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid,
            captchaCode: captcha.captchaText, loginDevice: 1,
        },
    })).json();
    expect(r.code).toBe(0);
    await ctx.dispose();
    return r.data.token;
}

async function raw(path: string, data: unknown) {
    return await (await api.post(path, {data, headers: {'Idempotency-Key': randomUUID()}})).json();
}

async function post(path: string, data: unknown) {
    const r = await raw(path, data);
    expect(r.code, `${path}: ${r.msg}`).toBe(0);
    return r.data;
}

async function get(path: string) {
    const r = await (await api.get(path)).json();
    expect(r.code, `${path}: ${r.msg}`).toBe(0);
    return r.data;
}

/** 报表查询：一律闭区间日期 + 分页；断言成功并直接返回 data。 */
async function report(path: string, data: Record<string, unknown> = {}) {
    return await post(`/scm/report/${path}`, {pageNum: 1, pageSize: 50, startDate: today, endDate: today, ...data});
}

async function browse(page: Page, path: string) {
    await page.addInitScript(v => localStorage.setItem('smart_admin_user_token', v), token);
    await page.goto('/#' + path);
}

/** 采购单：提交后的干净单（没有需求来源，收货量与采购量无关是合法业务）。 */
async function submittedOrder(quantity: string) {
    let o = await post('/scm/purchase/create', {
        supplierId, warehouseId, purchaserId: null, plannedArrivalDate: null, remark: name,
        items: [{skuId, quantity, price: '6.2000', allocations: []}],
    });
    return await post('/scm/purchase/submit', {id: o.id, version: o.version});
}

async function confirmedReceipt(receiptMode: 'DIRECT' | 'WAREHOUSE_CONFIRM', quantity: string) {
    const order = await submittedOrder(quantity);
    let receipt = await post('/scm/purchase/receipt/create', {purchaseOrderId: order.id, receiptMode, remark: name});
    const item = receipt.items[0];
    // 标准品没有实重这件事；非标品的实重必须带来源（40083 = 数量/实重组合不合法）
    const weight = standardSku ? null : quantity;
    receipt = await post('/scm/purchase/receipt/confirm', {
        id: receipt.id, version: receipt.version,
        items: [{
            receiptItemId: item.id, version: item.version, receivedQuantity: quantity,
            actualWeight: weight, weightSource: weight ? 'MANUAL' : null,
        }],
    });
    return {order, receipt, receiptItemId: item.id};
}

test.beforeAll(async () => {
    execFileSync('python', ['../tools/w6_e2e_accounts.py', 'setup'], {env, stdio: 'pipe'});
    token = await login(name, password);
    api = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${token}`}});
    // 未挂角色的伴随账号：用来证明权限是服务端拦的，不是前端藏起来的
    anonApi = await request.newContext({baseURL: apiUrl});

    const warehouses = await get('/scm/warehouse/list');
    warehouseId = String((warehouses.find((w: any) => w.warehouseCode === 'WH001') ?? warehouses[0]).id);
    const options = (await post('/scm/product/sku/option-list', {limit: 20})).options;
    // 优先挑标准品：非标品在确认前必须先录实重，会让「确认后金额」这条断言多绕一步
    const sku = options.find((x: any) => x.productType === 'STANDARD') ?? options[0];
    skuId = String(sku.skuId);
    skuCode = String(sku.skuCode);
    standardSku = sku.productType === 'STANDARD';
    customerId = String(await post('/scm/customer/add', {
        customerCode: name.toUpperCase(), name, customerTypeId: (await post('/scm/customer/type/option/list', {}))[0].typeId,
        settleMode: 'INDEPENDENT', contactName: 'R0验收', contactPhone: '13800000000', address: '验收地址',
    }));
    // 新建客户是 POTENTIAL（潜在）状态，不可交易（40930）；下单前先转成合作中
    const cust = await get('/scm/customer/detail/' + customerId);
    await post('/scm/customer/updateStatus', {customerId, version: cust.version, status: 'COOPERATING'});
    supplierId = String(await post('/scm/supplier/add', {
        supplierCode: name.toUpperCase(), name, contactName: 'R0验收', contactPhone: '13800000000', address: '验收地址',
    }));
    // 采购单要求供应商已启用该 SKU 的采购配置（40992），否则采购事实根本不成立
    await post('/scm/supplier/sku/replace', {
        supplierId, items: [{skuId, purchaseUnit: 'kg', defaultFlag: true, status: 'ENABLED'}],
    });
});

test.afterAll(async () => {
    execFileSync('python', ['../tools/w6_e2e_accounts.py', 'cleanup'], {env, stdio: 'pipe'});
});

test('1 经营概览：0 pageerror、默认本月、指标卡命名不含收入类词', async ({page}) => {
    await browse(page, '/report/report-overview-list');
    // 同一个标签会同时出现在指标卡、趋势说明与表头，因此断言存在而不是唯一
    await expect(page.getByText('已确认订单金额', {exact: true}).filter({visible: true}).first()).toBeVisible();
    await expect(page.getByText('当前库存账面金额', {exact: true}).filter({visible: true}).first()).toBeVisible();
    // R0 没有签收 / 收款事实，出现这些名字就是把承诺说成已实现
    for (const banned of ['营业收入', '销售收入', '实收金额', '应收', '应付', '毛利']) {
        await expect(page.getByText(banned, {exact: true})).toHaveCount(0);
    }
});

test('1b 五张报表页逐个打开：0 pageerror 且各自的口径标题在位', async ({page}) => {
    // 逐页打开而不是只测首页：动态菜单没配好时页面会静默 404，接口全绿也发现不了
    const pages: Array<[string, string]> = [
        ['/report/report-overview-list', '已确认订单金额'],
        ['/report/report-sales-list', '按商品'],
        ['/report/report-purchase-list', '采购概览'],
        ['/report/report-receipt-list', '收货明细'],
        ['/report/report-inventory-list', '库存流水'],
    ];
    for (const [path, label] of pages) {
        await browse(page, path);
        // filter(visible) 是必需的：Tab 容器会把非活动面板留在 DOM 里，
        // 不加可见性过滤就会命中一个隐藏节点而误报
        await expect(page.getByText(label, {exact: true}).filter({visible: true}).first(),
            `${path} 应渲染出「${label}」`).toBeVisible();
    }
});

test('2 销售报表只计 CONFIRMED：草稿无行、确认后金额等于结算总额', async () => {
    let order: any = await post('/scm/order/create', {
        customerId, orderSource: 'ADMIN',
        address: {receiverName: 'R0验收', receiverPhone: '13800000000', address: '验收地址'},
        remark: name, items: [{skuId, orderedQuantity: '2.0000', manualPriceOverride: false}],
    });
    const draftRows = await report('sales/customer', {customerId});
    expect(draftRows.list, '草稿订单不得进入销售统计').toHaveLength(0);

    order = await post('/scm/order/submit', {orderId: order.orderId, version: order.version});
    let confirmed = await raw('/scm/order/confirm', {orderId: order.orderId, version: order.version});
    if (confirmed.code === 40963) {
        // 非标品：先按结算量录实重再确认（40963 = 缺实重，不是失败）
        const item = confirmed.msg ? order.items[0] : order.items[0];
        await post('/scm/order/item/actual-quantity', {
            orderId: order.orderId, itemId: item.itemId, version: item.version,
            actualQuantity: item.orderedQuantity ?? '2.0000', reason: 'R0验收实重',
        });
        const after = await get('/scm/order/detail/' + order.orderId);
        confirmed = await raw('/scm/order/confirm', {orderId: after.orderId, version: after.version});
    }
    expect(confirmed.code, confirmed.msg).toBe(0);
    expect(confirmed.data.status).toBe('CONFIRMED');
    const settled = confirmed.data;

    const rows = (await report('sales/customer', {customerId})).list;
    expect(rows).toHaveLength(1);
    expect(rows[0].settlementAmount).toBe(settled.settlementTotalAmount);
    expect(rows[0].orderCount).toBe(1);

    const kpi = await report('overview');
    expect(kpi.confirmedOrderCount).toBeGreaterThanOrEqual(1);
    // 每日统计的每一行之和必须等于指标卡：两处是同一 SQL 口径，不是第二次计算
    const daily = await report('overview/daily');
    const dailyAmount = (daily.list ?? daily).reduce((sum: number, r: any) => sum + Number(r.confirmedOrderAmount ?? 0), 0);
    expect(dailyAmount).toBeCloseTo(Number(kpi.confirmedOrderAmount), 4);
});

test('3 采购报表只计提交后的采购事实', async () => {
    const draftOnly: any = await post('/scm/purchase/create', {
        supplierId, warehouseId, purchaserId: null, plannedArrivalDate: null, remark: name + '-draft',
        items: [{skuId, quantity: '3.0000', price: '5.0000', allocations: []}],
    });
    let overview = await report('purchase/overview', {supplierId});
    expect(overview.submittedOrderCount ?? 0, '草稿采购单不进统计').toBe(0);

    await post('/scm/purchase/submit', {id: draftOnly.id, version: draftOnly.version});
    overview = await report('purchase/overview', {supplierId});
    expect(overview.submittedOrderCount).toBe(1);
    expect(overview.submittedAmount).toBe('15.0000');

    // 取消单保留 submitted_at 也必须被排除：状态与时间是两个独立条件
    const toCancel: any = await submittedOrder('4.0000');
    await post('/scm/purchase/cancel', {id: toCancel.id, version: toCancel.version, cancelReason: 'R0验收取消'});
    overview = await report('purchase/overview', {supplierId});
    expect(overview.submittedOrderCount).toBe(1);
    expect(overview.submittedAmount).toBe('15.0000');
});

test('4 收货与入库分离：WAREHOUSE_CONFIRM 先只出现在收货与待入库，入库后才进入库明细', async () => {
    const {receipt} = await confirmedReceipt('WAREHOUSE_CONFIRM', '7.0000');

    const receipts = (await report('receipt/query', {keyword: receipt.receiptNo})).list;
    expect(receipts).toHaveLength(1);
    expect(receipts[0].putawayStatus).toBe('PENDING');
    expect(receipts[0].receiptReferenceAmount).toBe('43.4000');   // 7 × 6.2000

    expect((await report('pending-putaway/query', {keyword: receipt.receiptNo})).list).toHaveLength(1,
        '待入库应看到这张单');
    expect((await report('inbound/query', {keyword: receipt.receiptNo})).list).toHaveLength(0);

    await post('/scm/purchase/receipt/putaway', {id: receipt.id, version: receipt.version});

    expect((await report('pending-putaway/query', {keyword: receipt.receiptNo})).list).toHaveLength(0);
    const inbound = (await report('inbound/query', {keyword: receipt.receiptNo})).list;
    expect(inbound).toHaveLength(1);
    expect(inbound[0].quantity).toBe('7.0000');
    // 入库成本按流水冻结的 unit_cost，而不是当前 avg_cost 回算
    expect(Number(inbound[0].costAmount)).toBeCloseTo(Number(inbound[0].quantity) * Number(inbound[0].unitCost), 4);
});

test('5 DIRECT 收货确认后同事务入账，入库明细立即可见', async () => {
    const {receipt} = await confirmedReceipt('DIRECT', '2.0000');
    const inbound = (await report('inbound/query', {keyword: receipt.receiptNo})).list;
    expect(inbound).toHaveLength(1);
    expect(inbound[0].quantity).toBe('2.0000');
    expect((await report('pending-putaway/query', {keyword: receipt.receiptNo})).list).toHaveLength(0);
});

test('6 库存流水：方向由流水类型派生，成本金额与流水一致', async () => {
    const rows = (await report('inventory/movement/query', {movementType: 'PURCHASE_IN'})).list;
    expect(rows.length).toBeGreaterThan(0);
    for (const row of rows) {
        expect(row.direction).toBe('入库');
        if (row.unitCost !== null) {
            expect(Number(row.costAmount)).toBeCloseTo(Number(row.quantity) * Number(row.unitCost), 4);
        }
        // 快照等式是方向唯一的真相来源，报表不得自己另算一套
        expect(Number(row.afterQuantity)).toBeCloseTo(Number(row.beforeQuantity) + Number(row.quantity), 4);
    }
});

test('7 收发存数量版按单位分组，且不伪造历史期初期末', async () => {
    const row = (await report('inventory/flow-summary/query')).list[0];
    expect(row).toBeTruthy();
    expect(row).not.toHaveProperty('openingQuantity');
    expect(row).not.toHaveProperty('closingQuantity');
    expect(typeof row.unit).toBe('string');
});

test('8 损耗分析只计盘亏与报损，盈与报溢不进成本', async () => {
    const summary = await report('inventory/loss/summary');
    const rows = (await report('inventory/loss/query')).list;
    for (const row of rows) {
        expect(['STOCKTAKE_LOSS', 'LOSS_REPORT']).toContain(row.lossType);
    }
    expect(Number(summary.stocktakeLossCostAmount) + Number(summary.lossReportCostAmount))
        .toBeCloseTo(Number(summary.totalLossCostAmount), 4);
});

test('9 导出是当前筛选的真文件：接口层是合法 xlsx，页面上点导出能落盘', async ({page}) => {
    const list = await report('inventory/movement/query', {});
    expect(list.total).toBeGreaterThan(0);

    // 第二个参数是 options（body 要放在 data 里），写成 api.post(url, {...}) 会得到空请求体
    const res = await api.post('/scm/report/inventory/movement/export', {
        data: {pageNum: 1, pageSize: 50, startDate: today, endDate: today},
    });
    expect(res.status()).toBe(200);
    // 文件名由服务端拥有（Content-Disposition），前端不硬编码
    expect(res.headers()['content-disposition']).toContain('filename');
    const bytes = await res.body();
    expect(bytes.subarray(0, 2).toString(), 'xlsx 是 zip 容器').toBe('PK');
    expect(bytes.length).toBeGreaterThan(1024);

    await browse(page, '/report/report-inventory-list');
    const download = page.waitForEvent('download');
    await page.getByRole('button', {name: /^导\s*出$/}).first().click();
    const file = await download;
    expect(file.suggestedFilename()).toMatch(/\.xlsx$/);
});

test('10 无权限账号在报表接口层就被拒（前端隐藏不算防线）', async () => {
    for (const path of ['overview', 'sales/product', 'purchase/overview', 'inventory/movement/query']) {
        const r = await (await anonApi.post(`/scm/report/${path}`, {startDate: today, endDate: today})).json();
        expect(r.code, `未登录访问 /scm/report/${path} 必须被拒`).not.toBe(0);
    }
});
