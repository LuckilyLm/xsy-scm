import {expect, test} from './scm-test-base';
import {
    accessibleName,
    apiClient,
    authenticate,
    login,
    provisionTempAccounts,
    type TempAccounts,
} from './scm-e2e-account';
import {createDeliveryReadyOrder, createLocatedCustomer, createSku} from './scm-delivery-fixtures';
import {randomUUID} from 'node:crypto';

/**
 * P2 物流配送 L3 的浏览器整链验收：
 * 备货 → 订单 → 分拣完成（其中一张少拣）→ 组单 → 确认规划 → **发车** → 出库单与 SALES_OUT 取证 →
 * 签收 / 异常签收 → 完成线路，再加四条「页面照样能跑、事实却错了」的负向。
 *
 * 为什么必须跑整链而不是只看接口码：
 * 1. 实发量必须来自分拣的 {@code sorted_quantity}。两张订单刻意给不同实发量（3 与 2），
 *    再从余额与流水反推 —— 若发车偷偷回读订单行的 {@code actual_quantity}，余额差就对不上，
 *    而接口一路返回成功。
 * 2. 发车只产生一份事实：同键重放、以及对已 DISPATCHED 线路再发一次，都不许追加出库单或二次扣账。
 * 3. 发车与分拣重开互斥要双向验（重开后整线拒发、发车后任务不能重开）；
 *    这条守卫跨 sorting / delivery / inventory 三个模块。
 * 4. 取证照落在非超管账号上：没有仓库授权的调度不能发车，司机不能签别人的线路。
 *
 * 库存与订单全部自建：发车会真的扣库存，借共享余额行会让断言依赖执行顺序。
 */
type Row = Record<string, any>;
type Paged = {list: Row[]; total: number};

test.describe.configure({mode: 'serial'});

const accounts: TempAccounts = provisionTempAccounts('w5', undefined, ['SCM_DISPATCHER', 'SCM_DRIVER']);
const tag = `P2${Date.now().toString(36).toUpperCase()}`;
const today = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date());
const START_LNG = '113.93000000';
const START_LAT = '22.54000000';
const PRICE = '3.5000';
/** 发车出库的合计实发量：A 全量 3 + B 少拣 2。 */
const SHIPPED = 5;

let api: Awaited<ReturnType<typeof apiClient>>;
let adminToken = '';
let dispatcher: Awaited<ReturnType<typeof apiClient>>;
let driver: Awaited<ReturnType<typeof apiClient>>;
let dispatcherEmployeeId = 0;
let driverEmployeeId = 0;

let warehouseId = 0;
let skuId = '';
let supplierId = '';
let routeId = '';
let orderA = '';
let orderB = '';
let taskB = '';
let outboundId = 0;
let dispatchKey = '';
let balanceAfterStock = 0;

async function ok<T = Row>(client: typeof api, method: 'get' | 'post' | 'put', path: string,
                            data?: unknown, key = randomUUID()): Promise<T> {
    const response = method === 'get'
        ? await client.get(path)
        : await client[method](path, {data, headers: {'Idempotency-Key': key}});
    const body = await response.json();
    expect(body.code, `${method.toUpperCase()} ${path} 失败：${body.code} ${body.msg}`).toBe(0);
    return body.data as T;
}

const get = <T = Row,>(path: string, client: typeof api = api) => ok<T>(client, 'get', path);
const post = <T = Row,>(path: string, data?: unknown, client: typeof api = api) => ok<T>(client, 'post', path, data);

/** 负向入口：拿原始信封，不因 code != 0 先把用例判红。 */
async function envelope(path: string, data: unknown, client: typeof api = api, key = randomUUID(),
                        method: 'post' | 'delete' = 'post') {
    const response = method === 'delete'
        ? await client.delete(path, {data, headers: {'Idempotency-Key': key}})
        : await client.post(path, {data, headers: {'Idempotency-Key': key}});
    const body = await response.json().catch(() => ({}) as Row);
    return {status: response.status(), code: Number(body.code), msg: String(body.msg ?? '')};
}

const routeDetail = (id: string | number = routeId, client: typeof api = api) =>
    get<Row>(`/scm/delivery/routes/${id}`, client);
const routeRow = async () => (await routeDetail()).route as Row;
const routeVersion = async () => Number((await routeRow()).version);
const ordersOnRoute = async () => (await routeDetail()).orders as Row[];
const orderOnRoute = async (orderId: string) =>
    (await ordersOnRoute()).find(o => Number(o.orderId) === Number(orderId));

async function balanceQuantity(): Promise<number> {
    const page = await post<Paged>('/scm/inventory/balance/query',
        {pageNum: 1, pageSize: 50, warehouseId, skuId: Number(skuId)});
    const row = page.list.find(b => Number(b.skuId) === Number(skuId));
    expect(row, `仓库 ${warehouseId} / SKU ${skuId} 应有余额行`).toBeTruthy();
    return Number(row.quantity);
}

async function salesOutMovements(): Promise<Row[]> {
    const page = await post<Paged>('/scm/inventory/movement/query',
        {pageNum: 1, pageSize: 100, warehouseId, skuId: Number(skuId), movementType: 'SALES_OUT'});
    return page.list;
}

/** 真实采购链路备货：建单 → 提交 → DIRECT 收货确认（收货即入账）。 */
async function stockIn(quantity: string) {
    const order = await post<Row>('/scm/purchase/create', {
        supplierId, warehouseId, purchaserId: null, plannedArrivalDate: null,
        remark: `${tag} 发车备货`, items: [{skuId: Number(skuId), quantity, price: PRICE, allocations: []}],
    });
    await post('/scm/purchase/submit', {id: order.id, version: order.version});
    const receipt = await post<Row>('/scm/purchase/receipt/create',
        {purchaseOrderId: order.id, receiptMode: 'DIRECT', remark: `${tag} 发车备货收货`});
    const item = (receipt.items as Row[])[0];
    return post('/scm/purchase/receipt/confirm', {
        id: receipt.id, version: receipt.version,
        items: [{receiptItemId: item.id, version: item.version, receivedQuantity: quantity,
            actualWeight: quantity, weightSource: 'MANUAL'}],
    });
}

/** 一张「已分拣完成」的订单；sortedQuantity 小于 quantity 就是少拣。 */
async function sortedOrder(addressTag: string, quantity: string, sortedQuantity: string,
                           result: 'NORMAL' | 'SHORT'): Promise<{orderId: string; taskId: string}> {
    const customerId = await createLocatedCustomer(api, tag, addressTag, `${tag}${addressTag} 验收路`);
    const created = await createDeliveryReadyOrder(api, {
        runTag: tag, customerId, skuId, address: `${tag}${addressTag} 验收路`, warehouseId,
        quantity, sortedQuantity, sortedResult: result,
        ...(result === 'SHORT' ? {sortReason: `${tag} 现场缺货`} : {}),
    });
    // 夹具返回的是 sortingTaskId；本 spec 里统一叫 taskId，少拣取证要按任务回读。
    return {orderId: created.orderId, taskId: created.sortingTaskId};
}

/** 把线路推到 PLANNED：组单 + 补齐停靠点坐标 + 确认规划。 */
async function plannedRouteWith(orders: string[], routeName: string): Promise<string> {
    // 建单接口返回的 data 就是线路 id 本身（ResponseDTO<Long>），不是对象。
    const id = String(await post('/scm/delivery/routes', {
        routeName, deliveryDate: today, warehouseId, remark: tag,
    }));
    let version = Number((await routeDetail(id)).route.version);
    await post(`/scm/delivery/routes/${id}/orders`,
        {version, orderIds: orders.map(Number), reason: `${tag} 组单`});
    const detail = await routeDetail(id);
    version = Number(detail.route.version);
    for (const stop of detail.stops as Row[]) {
        if (stop.longitude != null && stop.latitude != null) continue;
        await ok(api, 'put', `/scm/delivery/routes/${id}/stops/${stop.id}`,
            {version, longitude: START_LNG, latitude: START_LAT, geomCrs: 'GCJ02'});
        version = Number((await routeDetail(id)).route.version);
    }
    await post(`/scm/delivery/routes/${id}/plan`, {version, reason: `${tag} 规划`});
    return id;
}

function button(scope: any, text: string) {
    return scope.getByRole('button', {name: accessibleName(text)});
}

test.beforeAll(async () => {
    adminToken = await login(accounts, accounts.admin);
    api = await apiClient(adminToken);
    dispatcherEmployeeId = accounts.employeeIds[accounts.roleAccounts.SCM_DISPATCHER];
    driverEmployeeId = accounts.employeeIds[accounts.roleAccounts.SCM_DRIVER];
    expect(dispatcherEmployeeId, '未建立 SCM_DISPATCHER 账号，发车权限取证没有证据').toBeGreaterThan(0);
    expect(driverEmployeeId, '未建立 SCM_DRIVER 账号，签收范围取证没有证据').toBeGreaterThan(0);
    dispatcher = await apiClient(await login(accounts, accounts.roleAccounts.SCM_DISPATCHER));
    driver = await apiClient(await login(accounts, accounts.roleAccounts.SCM_DRIVER));

    const options = await get<Row[]>('/scm/delivery/options/warehouses');
    const target = options.find((w) => w.warehouseCode === 'WH001') ?? options[0];
    expect(target, '开发库没有启用仓库').toBeTruthy();
    warehouseId = Number(target.id);
    const fresh = await get<Row>(`/scm/warehouse/detail/${warehouseId}`);
    if (fresh.longitude == null) {
        // 规划要求仓库起点带坐标；列表接口不返回坐标，因此按明细回读后补齐再写回。
        await ok(api, 'post', '/scm/warehouse/update', {
            id: warehouseId, version: fresh.version, warehouseCode: target.warehouseCode, name: target.name,
            address: fresh.address ?? `${tag} 仓`, longitude: START_LNG, latitude: START_LAT, geomCrs: 'GCJ02',
            remark: tag,
        });
    }

    skuId = await createSku(api, tag, 'L3');
    supplierId = String(await post('/scm/supplier/add',
        {supplierCode: `${tag}-SUP`.toUpperCase(), name: `${tag}供应商`}));
    await post('/scm/supplier/sku/replace', {supplierId,
        items: [{skuId: Number(skuId), purchaseUnit: 'kg', defaultFlag: true, status: 'ENABLED'}]});
    await stockIn('20.0000');
    balanceAfterStock = await balanceQuantity();

    // 发车走库存域既有的仓库范围守卫，所以调度岗必须持有本仓授权行 —— 这是配置要求，
    // 不是放宽守卫的理由。缺授权的那条反例在第 8 条用例里现证。
    await post('/scm/warehouse/scope/update', {employeeId: dispatcherEmployeeId, warehouseIds: [warehouseId]}, api);
});

test.afterAll(async () => {
    // 授权行不随账号回收，必须显式清空，否则下一轮仍带着上一轮的仓库范围。
    if (dispatcherEmployeeId && api) {
        await post('/scm/warehouse/scope/update', {employeeId: dispatcherEmployeeId, warehouseIds: []}, api);
    }
    if (driverEmployeeId && api) {
        await post('/scm/warehouse/scope/update', {employeeId: driverEmployeeId, warehouseIds: []}, api);
    }
    for (const client of [dispatcher, driver, api]) {
        if (!client) continue;
        await client.get('/login/logout').catch(() => undefined);
        await client.dispose();
    }
    accounts.cleanup();
});

test('1｜夹具：两张已分拣完成订单，其中一张少拣 2（计划 3）', async () => {
    expect(balanceAfterStock, '备货必须真实入账，否则发车会因库存不足而失败').toBeGreaterThan(SHIPPED);
    const a = await sortedOrder('A', '3.0000', '3.0000', 'NORMAL');
    const b = await sortedOrder('B', '3.0000', '2.0000', 'SHORT');
    orderA = a.orderId;
    orderB = b.orderId;
    taskB = b.taskId;
    expect(orderA).toBeTruthy();
    expect(orderB).toBeTruthy();

    const sortedB = await get<Row>(`/scm/sorting/tasks/${taskB}`);
    expect(sortedB.task.status).toBe('COMPLETED');
    const activeB = (sortedB.items as Row[]).filter(i => i.occupationStatus === 'ACTIVE');
    expect(activeB.length, '任务里应有一条活动明细').toBe(1);
    expect(Number(activeB[0].sortedQuantity), '少拣事实必须落在分拣行上，发车才有唯一来源').toBe(2);
    expect(activeB[0].result).toBe('SHORT');
});

test('2｜组单与确认规划：线路 PLANNED，一行库存都不扣', async () => {
    routeId = await plannedRouteWith([orderA, orderB], `${tag} 发车验收线路`);

    expect((await routeRow()).status).toBe('PLANNED');
    expect(await balanceQuantity(), '确认规划不产生任何库存写入').toBe(balanceAfterStock);
    expect((await salesOutMovements()).length, '规划不产生 SALES_OUT').toBe(0);
    expect((await routeRow()).outboundId ?? null, '规划不生成出库单').toBeNull();
});

test('3｜发车：DISPATCHED + 出库单 + 库存只按分拣实发量扣一次', async () => {
    const before = await balanceQuantity();
    dispatchKey = randomUUID();
    const body = {version: await routeVersion()};
    const dispatched = await ok<Row>(dispatcher, 'post', `/scm/delivery/routes/${routeId}/dispatch`,
        body, dispatchKey);
    outboundId = Number(dispatched.outboundId);

    expect(dispatched.status).toBe('DISPATCHED');
    expect(outboundId, '有实物离仓就必须有出库单').toBeGreaterThan(0);
    expect(Number(dispatched.shippedLineCount), '两个订单行各一条出库明细').toBe(2);

    const route = await routeRow();
    expect(route.status).toBe('DISPATCHED');
    expect(Number(route.outboundId)).toBe(outboundId);
    expect(route.outboundNo, '线路详情要能直接读回出库单号').toBeTruthy();
    expect(route.dispatchedAt, '发车时点与操作人成对落库').toBeTruthy();
    expect(route.dispatchedBy).toBeTruthy();

    expect(before - (await balanceQuantity()),
        `扣的必须是分拣实发量 ${SHIPPED}（3 + 2），不是订单行数量 6`).toBe(SHIPPED);

    const outbound = await get<Row>(`/scm/inventory/outbound/detail/${outboundId}`);
    expect(outbound.status, '发车出库直接是 CONFIRMED，不经过 DRAFT').toBe('CONFIRMED');
    expect((outbound.items as Row[]).length).toBe(2);
    const movements = await salesOutMovements();
    expect(movements.length, '两条 SALES_OUT，一订单行一条').toBe(2);
    expect(movements.reduce((sum, m) => sum + Number(m.quantity), 0)).toBe(SHIPPED);

    for (const orderId of [orderA, orderB]) {
        expect((await orderOnRoute(orderId)).fulfillmentStatus, '发车把活动订单全部推进在途')
            .toBe('IN_TRANSIT');
    }

    // 同一把键 + 同一份载荷必须走回放分支：拿回同一张出库单，账上不再动一次。
    // 真实客户端的重试就是 axios 原样重发，所以载荷复用同一个对象，不重新读 version。
    const afterFirst = await balanceQuantity();
    const replay = await ok<Row>(dispatcher, 'post', `/scm/delivery/routes/${routeId}/dispatch`,
        body, dispatchKey);
    expect(Number(replay.outboundId), '重放不能再生成一张出库单').toBe(outboundId);
    // 基线是**首发之后**的余额：首发扣掉 5 是事实，重放必须在同一个数上不动。
    expect(await balanceQuantity(), '重放不再扣账').toBe(afterFirst);
    expect(afterFirst, '首发只扣了一个实发量合计').toBe(before - SHIPPED);
});

test('4｜重复发车：同键回放同一张出库单，换键再发被状态拒绝，账上不多一份', async () => {
    const before = await balanceQuantity();
    const again = await envelope(`/scm/delivery/routes/${routeId}/dispatch`,
        {version: await routeVersion()}, dispatcher);
    expect(again.code, '已发车线路换一把键也不能再发一次').toBe(41101);
    expect(Number((await routeRow()).outboundId)).toBe(outboundId);
    expect(await balanceQuantity(), '被拒的重复发车一次账都不许多扣').toBe(before);

    const page = await post<Paged>('/scm/inventory/outbound/query', {pageNum: 1, pageSize: 100, warehouseId});
    expect(page.list.filter(o => Number(o.id) === outboundId).length).toBe(1);
    expect((await salesOutMovements()).length, '流水不许追加').toBe(2);
});

test('5｜签收与完成：异常必填原因，全部终态后线路才能完成', async () => {
    const bVersion = Number((await orderOnRoute(orderB)).version);
    const missing = await envelope(`/scm/delivery/routes/${routeId}/orders/${orderB}/sign`,
        {version: bVersion, result: 'EXCEPTION'}, dispatcher);
    expect(missing.code, '异常签收没有原因必须被拒').toBe(41118);

    const early = await envelope(`/scm/delivery/routes/${routeId}/complete`,
        {version: await routeVersion()}, dispatcher);
    expect(early.code, '仍有订单在途时不能完成线路').toBe(41117);

    await post(`/scm/delivery/routes/${routeId}/orders/${orderB}/sign`,
        {version: Number((await orderOnRoute(orderB)).version), result: 'EXCEPTION', reason: `${tag} 客户拒收破损`},
        dispatcher);
    const signedB = await orderOnRoute(orderB);
    expect(signedB.fulfillmentStatus).toBe('EXCEPTION');
    expect(String(signedB.signReason), '异常原因要读得回来').toContain(tag);
    expect(signedB.signedAt, '终态必须留下签收时点与签收人').toBeTruthy();
    expect(signedB.signedBy).toBeTruthy();

    const stillOpen = await envelope(`/scm/delivery/routes/${routeId}/complete`,
        {version: await routeVersion()}, dispatcher);
    expect(stillOpen.code, '只签了一单仍不能完成').toBe(41117);

    await post(`/scm/delivery/routes/${routeId}/orders/${orderA}/sign`,
        {version: Number((await orderOnRoute(orderA)).version), result: 'SIGNED'}, dispatcher);
    await post(`/scm/delivery/routes/${routeId}/complete`, {version: await routeVersion()}, dispatcher);

    const route = await routeRow();
    expect(route.status).toBe('COMPLETED');
    expect(route.completedAt).toBeTruthy();
    expect(route.completedBy).toBeTruthy();
    expect((await orderOnRoute(orderA)).fulfillmentStatus).toBe('SIGNED');
    expect(await balanceQuantity(), '签收与完成都不产生库存事实').toBe(balanceAfterStock - SHIPPED);
});

test('6｜发车后不可逆：线路不能取消，订单也不能被摘掉', async () => {
    const cancel = await envelope(`/scm/delivery/routes/${routeId}/cancel`,
        {version: await routeVersion(), reason: `${tag} 发车后取消`}, dispatcher);
    expect(cancel.code, '已发车的线路不允许取消').toBe(41101);

    const remove = await envelope(`/scm/delivery/routes/${routeId}/orders/${orderA}`,
        {version: await routeVersion(), reason: `${tag} 发车后摘单`}, dispatcher, randomUUID(), 'delete');
    expect(remove.code, '线路上的订单归属在发车后不可变更').toBe(41101);
    expect((await ordersOnRoute()).length).toBe(2);
});

test('7｜已真实出库的分拣任务不能重开（P1 第 21 条守卫）', async () => {
    const detail = await get<Row>(`/scm/sorting/tasks/${taskB}`);
    const blocked = await envelope(`/scm/sorting/tasks/${taskB}/reopen`,
        {version: detail.task.version, reason: `${tag} 出库后重开`});
    expect(blocked.code, `发车后重开必须被拒：${blocked.msg}`).toBe(41128);
    expect((await get<Row>(`/scm/sorting/tasks/${taskB}`)).task.status,
        '被拒的重开不许改任务状态').toBe('COMPLETED');
});

test('8｜非超管取证：缺仓库授权不能发车，司机签不了别人的线路', async ({page}) => {
    const extra = await sortedOrder('C', '1.0000', '1.0000', 'NORMAL');
    const scopedRouteId = await plannedRouteWith([extra.orderId], `${tag} 范围验收线路`);
    const version = () => routeVersionOf(scopedRouteId);

    // 先撤掉授权行再看同一把钥匙：功能权限 V64 已给到 SCM_DISPATCHER，
    // 被拒只能是因为仓库维度 —— 这样这条用例证的才是范围守卫，而不是功能权限缺失。
    await post('/scm/warehouse/scope/update', {employeeId: dispatcherEmployeeId, warehouseIds: []}, api);
    const denied = await envelope(`/scm/delivery/routes/${scopedRouteId}/dispatch`,
        {version: await version()}, dispatcher);
    expect(denied.code, '发车走库存域既有的仓库范围守卫，缺授权必须 30005').toBe(30005);
    expect((await routeDetail(scopedRouteId)).route.status, '被拒的发车不留任何痕迹').toBe('PLANNED');

    const signDenied = await envelope(
        `/scm/delivery/routes/${scopedRouteId}/orders/${extra.orderId}/sign`,
        {version: 0, result: 'SIGNED'}, driver);
    expect(signDenied.code, '司机不能凭 id 替不属于自己的线路签收').toBe(30005);

    await post('/scm/warehouse/scope/update', {employeeId: dispatcherEmployeeId, warehouseIds: [warehouseId]}, api);
    const allowed = await ok<Row>(dispatcher, 'post', `/scm/delivery/routes/${scopedRouteId}/dispatch`,
        {version: await version()});
    expect(allowed.status).toBe('DISPATCHED');

    // 页面上：已完成的线路不再提供发车动作，出库单线索在抽屉里读得到。
    // 令牌必须在 goto 之前注入 localStorage，否则页面停在登录路由上什么都找不到。
    await authenticate(page, adminToken);
    await page.goto('/#/delivery/routes');
    const row = page.locator('tr').filter({hasText: `${tag} 发车验收线路`}).first();
    await expect(row, '线路列表要能按名称找到本轮线路').toBeVisible({timeout: 20000});
    await button(row, '详情').click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '线路详情'}).first();
    await expect(drawer).toBeVisible({timeout: 10000});
    await expect(button(drawer, '发车'), '已完成线路不应再提供发车动作').toHaveCount(0);
    await expect(drawer.getByText('出库单').first(), '发车后的线路要能看出库单线索')
        .toBeVisible({timeout: 10000});

    // 履约标签页是这次新增的 UI 主体：状态必须来自后端返回值，签收/异常两行都要看得见，
    // 且已进入终态的行不能再有签收动作（按钮留在页上就是「可以再签一次」的错觉）。
    await drawer.getByRole('tab', {name: accessibleName('履约')}).click();
    const pane = drawer.locator('#rc-tabs-0-panel-fulfillment, .ant-tabs-tabpane-active').last();
    await expect(pane.getByText('已签收').first(), '正常签收行要渲染出终态').toBeVisible({timeout: 10000});
    await expect(pane.getByText('异常签收').first().or(pane.getByText('异常').first()),
        '异常签收行要渲染出终态').toBeVisible();
    await expect(pane.getByText(`${tag} 客户拒收破损`).first(), '异常原因要看得见面').toBeVisible();
    await expect(button(pane, '签收'), '终态行不得再提供签收动作').toHaveCount(0);
    await page.keyboard.press('Escape');
});

async function routeVersionOf(id: string | number): Promise<number> {
    return Number((await routeDetail(id)).route.version);
}
