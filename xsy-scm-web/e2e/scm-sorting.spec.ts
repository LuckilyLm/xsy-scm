/*
  P1 分拣管理的浏览器验收（主线计划 P1「完成标准」：按订单与按商品同一套事实、不直接修改库存、
  分拣结果可稳定供 Delivery L3 使用、重复生成不产生重复业务事实、正式分拣角色可完整使用）。

  四条边界各自都有正反两侧的取证：
    * 不回写订单（实发量与结算额逐字段比对基线）、不写库存（流水总数前后不变）；
    * 范围 = 授权仓 ∩ 受指派人 —— 断言一律用 SCM_SORTER / SCM_STOREKEEPER_LEAD 这两个
      administrator_flag=false 的角色账号；未指派任务对分拣员必须是 30005，而不是「不存在」；
    * 差异必填原因、未全处理不能完成（41125），补全后才 COMPLETED；
    * 打印只计次：预览不计数、登记后 +1，且状态与版本都不动。

  夹具全部自建（商品 / 客户 / 已确认订单 / DRAFT 线路），不借共享候选池 —— P1 之后池子里的
  历史订单不会因为「已确认」就变成可配送，借用等于把本轮结论交给上一轮留下的数据。
  收尾显式清空 employee_warehouse_scope：删账号不会连带回收授权行。
*/
import {type APIRequestContext, type Locator, type Page} from '@playwright/test';
import {expect, test} from './scm-test-base';
import {randomUUID} from 'node:crypto';
import {
    accessibleName,
    apiClient,
    authenticate,
    login,
    provisionTempAccounts,
    type TempAccounts,
} from './scm-e2e-account';
import {createLocatedCustomer, createSku} from './scm-delivery-fixtures';

type Row = Record<string, any>;
type Envelope = {code: number; msg?: string; data?: Row; status?: number};
type Paged = {list: Row[]; total: number};
type Fixture = {
    orderId: string;
    orderNo: string;
    itemId: string;
    /** 建单前抓的基线：分拣之后必须逐字不变。 */
    actualQuantity: string;
    settlementLineAmount: string | null;
};

const accounts: TempAccounts = provisionTempAccounts('p1', undefined,
    ['SCM_SORTER', 'SCM_STOREKEEPER_LEAD']);
const tag = accounts.admin.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
const today = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date());
const REMARK = `${tag} 两句任务`;

let api: APIRequestContext;
let sorterToken = '';
let leadToken = '';
let sorterClient: APIRequestContext;
let leadClient: APIRequestContext;
let sorterEmployeeId = 0;
let leadEmployeeId = 0;
let warehouseId = 0;
let warehouseName = '';
let routeId = '';
let customerId = '';
let skuId = '';
let taskOne: Fixture = {} as Fixture;
let taskTwo: Fixture = {} as Fixture;
/** 第三张订单只用来造「未指派」任务：它全程不参与上面的两句任务。 */
let unassignedOrder: Fixture = {} as Fixture;
let taskNo = '';
let movementsAtStart = 0;

test.describe.configure({mode: 'serial'});

async function ok<T = Row>(client: APIRequestContext, method: 'get' | 'post', path: string,
                           data?: unknown, key = randomUUID()): Promise<T> {
    const response = method === 'get'
        ? await client.get(path)
        : await client.post(path, {data, headers: {'Idempotency-Key': key}});
    const body = await response.json();
    expect(body.code, `${method.toUpperCase()} ${path}: ${body.msg}`).toBe(0);
    return body.data as T;
}

const get = <T = Row>(path: string, client: APIRequestContext = api) => ok<T>(client, 'get', path);
const post = <T = Row>(path: string, data?: unknown, client: APIRequestContext = api) =>
    ok<T>(client, 'post', path, data);

/** 负向入口：返回原始信封，不因为 code != 0 就先把用例判红。 */
async function envelope(client: APIRequestContext, path: string, data?: unknown,
                        method: 'get' | 'post' = 'post'): Promise<Envelope> {
    const response = method === 'get'
        ? await client.get(path)
        : await client.post(path, {data, headers: {'Idempotency-Key': randomUUID()}});
    const body = await response.json();
    return {...body, status: response.status()} as Envelope;
}

const grantWarehouses = (employeeId: number, warehouseIds: number[]) =>
    ok(api, 'post', '/scm/warehouse/scope/update', {employeeId, warehouseIds});

const taskPage = (client: APIRequestContext) =>
    ok<Paged>(client, 'get', '/scm/sorting/tasks?pageNum=1&pageSize=100');
const taskOf = async (client: APIRequestContext, no: string) =>
    (await taskPage(client)).list.find(r => String(r.taskNo) === no);
const taskDetail = (client: APIRequestContext, id: string | number) =>
    ok<Row>(client, 'get', `/scm/sorting/tasks/${id}`);
const routeVersion = async () => Number((await get<Row>(`/scm/delivery/routes/${routeId}`)).route.version);
/** 候选池按单号精确筛：只断「不在前 N 条里」会被大池子糊过去。 */
const candidateOf = async (orderNo: string) =>
    (await ok<Paged>(api, 'get', `/scm/delivery/candidate-orders?pageNum=1&pageSize=50&keyword=${orderNo}`)).list;
const movementTotal = () =>
    ok<Paged>(api, 'post', '/scm/inventory/movement/query', {pageNum: 1, pageSize: 1}).then(p => Number(p.total));
const button = (scope: Page | Locator, text: string) => scope.getByRole('button', {name: accessibleName(text)});

/**
 * 任务列表按创建时间倒序分页，而 E2E 按既有约定只回收临时账号、不回收任务行 ——
 * 开发库累计几十条任务后目标行会落到第一页之外。所以点行内按钮前先按单号筛，
 * 断的是「筛出来唯一一行」，不是「它恰好排在第一页」。
 */
async function filterTaskRow(page: Page) {
    const keyword = page.locator('.smart-query-form input').first();
    await keyword.fill(taskNo);
    await keyword.press('Enter');
    await expect(page.locator('.ant-table-tbody tr').filter({hasText: taskNo})).toHaveCount(1);
}

async function confirmedOrder(quantity: string, seq: number): Promise<Fixture> {
    let order = await post<Row>('/scm/order/create', {
        customerId,
        orderSource: 'ADMIN',
        address: {receiverName: '分拣验收', receiverPhone: '13800000000', address: '分拣验收路1号'},
        expectDeliveryTime: `${today} 09:00:00`,
        remark: `${tag}-${seq}`,
        items: [{skuId, orderedQuantity: quantity, manualPriceOverride: false}],
    });
    order = await post<Row>('/scm/order/submit', {orderId: order.orderId, version: order.version});
    const line = order.items[0];
    await post('/scm/order/item/actual-quantity', {
        orderId: order.orderId, itemId: line.itemId, version: line.version,
        actualQuantity: quantity, reason: `${tag} 夹具实重`,
    });
    const detail = await get<Row>(`/scm/order/detail/${order.orderId}`);
    const confirmed = await post<Row>('/scm/order/confirm', {orderId: order.orderId, version: detail.version});
    // 基线必须取在确认**之后**：结算额是确认动作算出来的，取在确认前会拿到 null，
    // 于是「分拣不得改写结算额」这条断言会被自己造的时序差判红。
    const settled = await get<Row>(`/scm/order/detail/${order.orderId}`);
    const item = (settled.items as Row[]).find(i => String(i.itemId) === String(line.itemId));
    expect(item, '订单明细在确认后可回读').toBeTruthy();
    return {
        orderId: String(order.orderId),
        orderNo: String(confirmed.orderNo ?? detail.orderNo),
        itemId: String(line.itemId),
        actualQuantity: String(item.actualQuantity),
        settlementLineAmount: item.settlementLineAmount == null ? null : String(item.settlementLineAmount),
    };
}

test.beforeAll(async () => {
    api = await apiClient(await login(accounts, accounts.admin));
    sorterToken = await login(accounts, accounts.roleAccounts.SCM_SORTER);
    leadToken = await login(accounts, accounts.roleAccounts.SCM_STOREKEEPER_LEAD);
    sorterEmployeeId = accounts.employeeIds[accounts.roleAccounts.SCM_SORTER];
    leadEmployeeId = accounts.employeeIds[accounts.roleAccounts.SCM_STOREKEEPER_LEAD];
    expect(sorterEmployeeId, '未建立 SCM_SORTER 账号，分拣范围断言将没有证据').toBeGreaterThan(0);
    expect(leadEmployeeId, '未建立 SCM_STOREKEEPER_LEAD 账号').toBeGreaterThan(0);
    sorterClient = await apiClient(sorterToken);
    leadClient = await apiClient(leadToken);

    const warehouses = await get<Row[]>('/scm/warehouse/list');
    const enabled = warehouses.find(w => w.status === 'ENABLED');
    expect(enabled, '开发库需要至少一个启用仓库').toBeTruthy();
    warehouseId = Number(enabled.id ?? enabled.warehouseId);
    warehouseName = String(enabled.name ?? '');
    expect(warehouseName, '仓库名要能作为下拉筛选关键字').not.toBe('');
    // 两维范围都要靠授权行才成立：两个角色账号都授到同一个仓。
    await grantWarehouses(sorterEmployeeId, [warehouseId]);
    await grantWarehouses(leadEmployeeId, [warehouseId]);

    skuId = await createSku(api, tag, 'S');
    customerId = await createLocatedCustomer(api, tag, 'S', '分拣验收路1号');
    taskOne = await confirmedOrder('3.0000', 1);
    taskTwo = await confirmedOrder('2.0000', 2);
    unassignedOrder = await confirmedOrder('1.0000', 3);
    movementsAtStart = await movementTotal();

    // DRAFT 线路只用来验「分拣完成才可排线」这一半交接；不规划、不发车。
    routeId = String(await post('/scm/delivery/routes',
        {routeName: `${tag} 分拣交接线路`, deliveryDate: today, warehouseId}));
});

test.afterAll(async () => {
    try {
        if (routeId && api) {
            await ok(api, 'post', `/scm/delivery/routes/${routeId}/cancel`,
                {version: await routeVersion(), reason: 'P1 收尾：释放线路'});
        }
        await grantWarehouses(sorterEmployeeId, []);
        await grantWarehouses(leadEmployeeId, []);
    } finally {
        for (const client of [api, sorterClient, leadClient]) {
            await client?.get('/login/logout');
            await client?.dispose();
        }
        accounts.cleanup();
    }
});

test('未分拣的已确认订单不是配送候选：按单号筛候选池恰好筛不到它', async () => {
    expect(await candidateOf(taskOne.orderNo), '订单已确认但没分拣，不该进候选池').toEqual([]);
    const rejected = await envelope(api, `/scm/delivery/routes/${routeId}/orders`,
        {version: await routeVersion(), orderIds: [taskOne.orderId], reason: 'P1 未分拣不得排线'});
    expect(rejected.code, '组单侧必须给出可分辨的「不符合配送条件」').toBe(41102);
});

test('主管在页面上建单并派给分拣员，任务以 PENDING 落库', async ({page}) => {
    authenticate(page, leadToken);
    await page.goto('/#/sorting/tasks');
    await button(page, '新建分拣任务').click();
    const modal = page.locator('.ant-modal-content').last();
    // antd 的下拉在关闭后仍留在 DOM 里，`:visible` / role 都会命中上一个刚收起的下拉；
    // 因此统一取「最后一个 dropdown」并在里面按 option 文案点选，失败时把面板实际内容带进断言消息。
    const pickOption = async (label: string, keyword: string, search: boolean) => {
        const select = modal.locator(`.ant-form-item:has-text("${label}") .ant-select`).first();
        await select.click();
        const dropdown = page.locator('.ant-select-dropdown').last();
        await expect(dropdown).toBeVisible();
        if (search) {
            // 员工列表在开发库里有上百行，虚拟滚动下目标项不在 DOM 里，必须先过滤。
            await select.locator('.ant-select-selection-search-input').first().type(keyword, {delay: 20});
        }
        const option = dropdown.locator('.ant-select-item-option-content').filter({hasText: keyword}).first();
        await expect(option,
            `「${label}」下拉过滤 ${keyword} 后没有命中项；面板实际内容：${await dropdown.innerText()}`)
            .toBeVisible({timeout: 8000});
        await option.click();
    };
    await pickOption('仓库', warehouseName, false);
    await pickOption('受指派人', accounts.roleAccounts.SCM_SORTER, true);
    await modal.getByPlaceholder('可选').fill(REMARK);

    for (const orderNo of [taskOne.orderNo, taskTwo.orderNo]) {
        await modal.getByPlaceholder('订单 / 客户 / 商品').fill(orderNo);
        await button(modal, '查询候选行').click();
        const line = modal.locator('.ant-table-tbody tr').filter({hasText: orderNo}).first();
        await expect(line, `候选行里应筛到订单 ${orderNo}`).toBeVisible();
        await line.locator('input[type=checkbox]').check();
    }
    await button(modal, '创建任务（2 行）').click();
    await expect(page.locator('.ant-message-notice')).toContainText('成功', {timeout: 20000});

    const created = (await taskPage(leadClient)).list.find(r => String(r.remark) === REMARK);
    expect(created, '新任务应出现在主管的列表里').toBeTruthy();
    taskNo = String(created.taskNo);
    expect(created.status).toBe('PENDING');
    expect(Number(created.itemCount)).toBe(2);
    expect(Number(created.processedCount)).toBe(0);
    expect(Number(created.assigneeEmployeeId)).toBe(sorterEmployeeId);
    expect(String(created.taskNo)).toMatch(/^SRT\d{14}$/);
});

test('范围是授权仓 ∩ 受指派人：分拣员只看派给自己的，未指派任务回 30005', async () => {
    // 未指派任务由超管建，然后按分拣员身份读 —— 越权必须是 30005，不能伪装成「不存在」。
    const free = await post<Row>('/scm/sorting/tasks', {
        warehouseId,
        salesOrderItemIds: [unassignedOrder.itemId],
        remark: `${tag} 未指派`,
    });
    const freeTaskId = free.task.id;
    const denied = await envelope(sorterClient, `/scm/sorting/tasks/${freeTaskId}`, undefined, 'get');
    expect(denied.code, '未指派任务对分拣员必须是越权').toBe(30005);
    expect((await envelope(api, `/scm/sorting/tasks/${freeTaskId}`, undefined, 'get')).code,
        '超管按 break-glass 可读，说明上面的 30005 来自范围判定而不是数据缺失').toBe(0);

    const visible = await taskPage(sorterClient);
    expect(visible.list.length, '分拣员至少要看到派给自己的那条任务').toBeGreaterThan(0);
    expect(visible.list.every(r => Number(r.assigneeEmployeeId) === sorterEmployeeId),
        '分拣员的列表必须恒等于派给本人的任务').toBeTruthy();
    expect(visible.list.map(r => r.taskNo)).toContain(taskNo);
    const leadVisible = await taskPage(leadClient);
    expect(leadVisible.list.map(r => r.taskNo), '队列管理者应跨指派人可见')
        .toEqual(expect.arrayContaining([taskNo, String(free.task.taskNo)]));
});

test('差异必填原因、未全处理不能完成；补全后才 COMPLETED', async () => {
    const before = await taskDetail(sorterClient, (await taskOf(sorterClient, taskNo)).id);
    const [lineA, lineB] = before.items;

    const missingReason = await envelope(sorterClient, `/scm/sorting/tasks/${before.task.id}/entry`, {
        items: [{id: lineA.id, version: lineA.version, sortedQuantity: '2.0000', result: 'SHORT'}],
    });
    expect(missingReason.code, '少拣不写原因必须被拒').not.toBe(0);
    expect((await taskDetail(sorterClient, before.task.id)).task.status,
        '被拒的录入不得留下任何痕迹，也不得把任务推成 SORTING').toBe('PENDING');

    await ok(sorterClient, 'post', `/scm/sorting/tasks/${before.task.id}/entry`, {
        items: [{id: lineA.id, version: lineA.version, sortedQuantity: '2.0000', result: 'SHORT',
            reason: '当日到货不足'}],
    });
    const started = await taskDetail(sorterClient, before.task.id);
    expect(started.task.status).toBe('SORTING');
    expect(Number(started.task.processedCount)).toBe(1);
    expect(Number(started.task.version)).toBeGreaterThan(Number(before.task.version));

    const incomplete = await envelope(sorterClient, `/scm/sorting/tasks/${before.task.id}/complete`,
        {version: started.task.version});
    expect(incomplete.code, '还有未处理明细时不能完成').toBe(41125);

    await ok(sorterClient, 'post', `/scm/sorting/tasks/${before.task.id}/entry`, {
        items: [{id: lineB.id, version: lineB.version, sortedQuantity: lineB.plannedQuantitySnapshot,
            result: 'NORMAL'}],
    });
    const ready = await taskDetail(sorterClient, before.task.id);
    await ok(sorterClient, 'post', `/scm/sorting/tasks/${before.task.id}/complete`,
        {version: ready.task.version});
    const done = await taskDetail(sorterClient, before.task.id);
    expect(done.task.status).toBe('COMPLETED');
    expect(Number(done.task.processedCount)).toBe(2);

    // 重复提交同一行（带过期版本）必须被乐观锁拦住，而不是覆盖成第三条数。
    const stale = await envelope(sorterClient, `/scm/sorting/tasks/${before.task.id}/entry`, {
        items: [{id: lineA.id, version: lineA.version, sortedQuantity: '9.0000', result: 'OVER',
            reason: '过期版本重放'}],
    });
    expect(stale.code, '已完成任务不接受再录入，且过期版本必被拒').not.toBe(0);
});

test('分拣不回写订单、不写库存；完成后的订单成为配送候选并可排线', async () => {
    for (const order of [taskOne, taskTwo]) {
        const detail = await get<Row>(`/scm/order/detail/${order.orderId}`);
        const line = (detail.items as Row[]).find(i => String(i.itemId) === order.itemId);
        expect(String(line.actualQuantity), `订单 ${order.orderNo} 实发量不得被分拣改写`)
            .toBe(order.actualQuantity);
        expect(String(line.settlementLineAmount ?? null), '结算额同样不得被分拣改写')
            .toBe(order.settlementLineAmount ?? null);
    }
    expect(await movementTotal(), '整条分拣链不得产生任何库存流水').toBe(movementsAtStart);

    expect(await candidateOf(taskOne.orderNo), '分拣完成后应成为候选').toHaveLength(1);
    await ok(api, 'post', `/scm/delivery/routes/${routeId}/orders`,
        {version: await routeVersion(), orderIds: [taskOne.orderId, taskTwo.orderId], reason: 'P1 完成后排线'});
    const route = await get<Row>(`/scm/delivery/routes/${routeId}`);
    expect(Number(route.route.orderCount ?? route.orders.length)).toBe(2);
});

test('重开保留已录内容并让订单立刻掉出配送候选', async ({page}) => {
    authenticate(page, leadToken);
    await page.goto('/#/sorting/tasks');
    await filterTaskRow(page);
    await page.locator('.ant-table-tbody tr').filter({hasText: taskNo}).first()
        .getByRole('button', {name: accessibleName('重开')}).click();
    const modal = page.locator('.ant-modal-content').last();
    await modal.locator('textarea, input[type=text]').last().fill('客户改量，需要重分拣');
    await button(modal, '确认').click();
    await expect(page.locator('.ant-message-notice')).toContainText('成功', {timeout: 20000});

    const reopened = await taskOf(sorterClient, taskNo);
    const detail = await taskDetail(sorterClient, reopened.id);
    expect(detail.task.status).toBe('SORTING');
    expect(detail.task.completedAt, '曾完成的时间要留下来').toBeTruthy();
    expect(detail.items.map((i: Row) => i.result)).toEqual(['SHORT', 'NORMAL']);
    expect(detail.items.map(i => i.reason).filter(Boolean), '只有差异行才带原因').toEqual(['当日到货不足']);

    expect(await candidateOf(taskOne.orderNo), '重开只改任务状态就应让订单掉出候选').toEqual([]);
});

test('打印只计次：预览不计数，登记后累加且状态与版本都不动', async ({page}) => {
    const target = await taskOf(sorterClient, taskNo);
    const before = await taskDetail(sorterClient, target.id);
    expect(before.task.status, '打印要求 SORTING 或 COMPLETED').toBe('SORTING');

    authenticate(page, sorterToken);
    await page.goto('/#/sorting/tasks');
    await filterTaskRow(page);
    await page.locator('.ant-table-tbody tr').filter({hasText: taskNo}).first()
        .getByRole('button', {name: accessibleName('打印')}).click();
    const preview = page.locator('.ant-modal-content').last();
    await expect(preview).toContainText(taskNo);
    expect((await taskDetail(sorterClient, target.id)).task.printCount, '预览不得计次')
        .toBe(before.task.printCount);

    await button(preview, '登记打印').click();
    await expect(page.locator('.ant-message-notice')).toContainText('已登记打印', {timeout: 20000});
    const after = await taskDetail(sorterClient, target.id);
    expect(Number(after.task.printCount)).toBe(Number(before.task.printCount) + 1);
    expect(after.task.status).toBe('SORTING');
    expect(Number(after.task.version), '打印不改版本，否则会把别人的编辑顶成假冲突')
        .toBe(Number(before.task.version));
});

test('按商品汇总是同一套事实的只读视角，页面不提供任何录入口', async ({page}) => {
    authenticate(page, leadToken);
    await page.goto('/#/sorting/summary');
    await expect(page.locator('.ant-table-tbody')).toContainText('kg', {timeout: 20000});
    expect(await page.locator('.ant-input-number').count(), '汇总视角不许承载录入').toBe(0);
    expect(await page.getByRole('button').filter({hasText: /提交|录入|新建|完成|重开|取消/}).count(),
        '汇总视角不得出现写动作入口').toBe(0);

    const summary = await ok<Paged>(leadClient, 'get', '/scm/sorting/summary?pageNum=1&pageSize=100');
    const row = summary.list.find(r => String(r.saleUnitSnapshot) === 'kg' && Number(r.lineCount) >= 2);
    expect(row, '两句任务应在汇总里并成一行同单位的聚合').toBeTruthy();
    // 汇总量与逐行事实必须一致：同一 SKU + 单位下，计划合计 = 各行计划量之和。
    const items = (await taskPage(leadClient)).list.filter(t => Number(t.warehouseId) === warehouseId);
    const lines = [];
    for (const task of items) {
        lines.push(...(await taskDetail(leadClient, task.id)).items.filter((i: Row) => i.occupationStatus === 'ACTIVE'));
    }
    const planned = lines.filter(i => i.saleUnitSnapshot === 'kg' && i.skuId === row.skuId)
        .reduce((sum, i) => sum + Number(i.plannedQuantitySnapshot), 0);
    expect(Number(row.plannedQuantity)).toBeCloseTo(planned, 4);
});
