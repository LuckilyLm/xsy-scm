import {expect, test} from './scm-test-base';
import {
    accessibleName,
    apiClient,
    authenticate,
    login,
    provisionTempAccounts,
    type TempAccounts,
} from './scm-e2e-account';
import {randomUUID} from 'node:crypto';

/**
 * 物流配送 L0–L2 的浏览器验收（主线计划 P0「对 Delivery L0-L2 做最终 E2E 验收」）。
 *
 * <p>这段链路此前只有一次性人工验收报告、没有可重跑用例：L0 仓库/客户定位 →
 * L1 司机车辆主档 → L2 建线路 / 组单 / 停靠点定位 / 确认规划 / 取消释放。
 * 每条用例同时看页面、接口与按明细回读的结果，并显式钉住两条边界：
 * 确认规划不产生任何库存写入，且 L3 端点（发车 / 完成 / 签收）根本不存在。
 *
 * <p>夹具全部自建，不借候选订单池：池子是共享的，借用别人留下的单会在第二轮
 * 直接 41103（已分配）。收尾一律「取消线路」而不是删除 —— 取消保留历史并释放分配。
 *
 * <p>高德 Key 未配置，坐标一律走手工录入（GCJ02）；地图选点与地址搜索不在本 spec 断言。
 */
type Row = Record<string, any>;

test.describe.configure({mode: 'serial'});

const accounts: TempAccounts = provisionTempAccounts('w5');
const name = `W5${Date.now().toString(36).toUpperCase()}`;
// 业务日界一律 Asia/Shanghai；用 toISOString() 会在 UTC/本地跨天时拿到「昨天」，
// 导致候选订单被日期过滤器静默清空。
const today = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date());

const START_LNG = '113.93000000';
const START_LAT = '22.54000000';
const STOP_LNG = '113.94600000';
const STOP_LAT = '22.55300000';

let api: Awaited<ReturnType<typeof apiClient>>;
/**
 * 整轮复用同一个管理员令牌：Sa-Token 同账号不并发，测试内再次 login 会把
 * beforeAll 建的 API 客户端顶成「未登录」（30007），表现为随机红。
 */
let adminToken = '';
let warehouse = {} as Row;
let warehouseName = '';
const driverCode = `${name}-D`;
const vehicleNo = `${name}-V1`;
let driverId = '';
let vehicleId = '';
let routeId = '';
const orders: { id: string; no: string; customerId: string }[] = [];
let geolessOrderId = '';
let locatedCustomerIdA = '';
let geolessCustomerId = '';

async function ok<T = Row>(
    client: typeof api,
    method: 'get' | 'post' | 'put' | 'delete',
    path: string,
    data?: unknown,
    key = randomUUID(),
): Promise<T> {
    const response = method === 'get'
        ? await client.get(path)
        : await client[method](path, {data, headers: {'Idempotency-Key': key}});
    const body = await response.json();
    expect(body.code, `${method.toUpperCase()} ${path}: ${body.msg}`).toBe(0);
    return body.data as T;
}

const get = <T = Row,>(path: string, client: typeof api = api) => ok<T>(client, 'get', path);
const post = <T = Row,>(path: string, data?: unknown, client: typeof api = api) => ok<T>(client, 'post', path, data);
const put = <T = Row,>(path: string, data?: unknown, client: typeof api = api) => ok<T>(client, 'put', path, data);

/** 负向入口：返回原始信封，不因为 code != 0 就先把用例判红。 */
async function envelope(path: string, data: unknown, method: 'post' | 'put' | 'delete' = 'post',
                        key = randomUUID()) {
    const response = method === 'delete'
        ? await api.delete(path, {data, headers: {'Idempotency-Key': key}})
        : await api[method](path, {data, headers: {'Idempotency-Key': key}});
    const body = await response.json().catch(() => ({}) as Row);
    return {status: response.status(), ok: body.ok === true, code: body.code, msg: body.msg ?? ''};
}

async function route(): Promise<Row> {
    return (await get<Row>(`/scm/delivery/routes/${routeId}`)).route as Row;
}

async function routeVersion(): Promise<number> {
    return Number((await route()).version);
}

/** 线路详情一次取回头、停靠点与订单三层，覆盖度与合并结果都从这里判。 */
async function detail(): Promise<Row> {
    return await get<Row>(`/scm/delivery/routes/${routeId}`);
}

function button(scope: any, text: string) {
    return scope.getByRole('button', {name: accessibleName(text)});
}

/** antd 的 label 与 input 之间没有稳定的 for 关联，按表单项文本定位再填内部控件。 */
function field(scope: any, label: string) {
    return scope.locator('.ant-form-item').filter({hasText: label}).first();
}

async function fillField(scope: any, label: string, value: string) {
    const control = field(scope, label).locator('input, textarea').first();
    await control.fill(value);
    await control.press('Enter').catch(() => undefined);
}

async function pickOption(page: any, scope: any, label: string, optionText: string) {
    await field(scope, label).locator('.ant-select-selector').first().click();
    await page.locator('.ant-select-dropdown:visible').locator('.ant-select-item-option-content')
        .filter({hasText: optionText}).first().click();
}

test.beforeAll(async () => {
    adminToken = await login(accounts, accounts.admin);
    api = await apiClient(adminToken);

    // L0：给启用仓库补齐定位。列表接口不返回坐标，因此必须按明细回读才算落库。
    const warehouses = await get<Row[]>('/scm/delivery/options/warehouses');
    expect(warehouses.length, '开发库没有启用仓库，无法验收配送 L0').toBeGreaterThan(0);
    const target = warehouses.find((w) => w.warehouseCode === 'WH001') ?? warehouses[0];
    warehouseName = String(target.name);
    const fresh = await get<Row>(`/scm/warehouse/detail/${target.id}`);
    await ok(api, 'post', '/scm/warehouse/update', {
        id: target.id,
        version: fresh.version,
        warehouseCode: target.warehouseCode,
        name: target.name,
        address: fresh.address ?? '验收路 1 号',
        longitude: START_LNG,
        latitude: START_LAT,
        geomCrs: 'GCJ02',
        remark: name,
    });
    warehouse = await get<Row>(`/scm/warehouse/detail/${target.id}`);
    warehouse.id = String(target.id);

    let parentId: string | null = null;
    for (const level of [1, 2, 3]) {
        parentId = String(await post('/scm/product/category/add', {
            parentId,
            categoryCode: `${name}-L${level}`.toUpperCase(),
            name: `${name}分类${level}`,
            sortOrder: 0,
            status: 'ENABLED',
        }));
    }
    const categoryId = parentId as string;
    const skuIds: string[] = [];
    for (const tag of ['A', 'B']) {
        const code = `${name}-${tag}`.toUpperCase();
        await post('/scm/product/add', {
            spuCode: code,
            name: `${name}商品${tag}`,
            categoryId,
            status: 'ON_SHELF',
            images: [],
            skuList: [{
                skuCode: code, specName: `散装${tag}`, specValues: {规格: '散装'}, saleUnit: 'kg',
                productType: 'NON_STANDARD', marketPrice: '3.5000', status: 'ON_SHELF', defaultFlag: true, sortOrder: 0,
            }],
        });
        const options = (await post<Row>('/scm/product/sku/option-list', {keyword: code, limit: 10})).options;
        skuIds.push(String(options.find((o: Row) => o.skuCode === code).skuId));
    }

    const typeData: any = await post('/scm/customer/type/option/list', {});
    const typeList = Array.isArray(typeData) ? typeData : (typeData.options ?? typeData.list ?? []);
    expect(typeList.length, '开发库没有客户类型，无法建客户夹具').toBeGreaterThan(0);
    const rawTypeId = typeList[0].typeId ?? typeList[0].customerTypeId ?? typeList[0].id;
    // 夹具把不存在的字段当数字传，服务端只会回 30001「参数JSON格式错误」，
    // 看不出是夹具坏了，因此在这里显性失败并带出真实字段名。
    expect(Number.isFinite(Number(rawTypeId)),
        `客户类型选项结构异常，实际字段：${JSON.stringify(Object.keys(typeList[0]))}`).toBeTruthy();
    const customerTypeId = Number(rawTypeId);

    async function newCustomer(tag: string, address: string, located: boolean): Promise<string> {
        const id = String(await post('/scm/customer/add', {
            customerCode: `${name}-C${tag}`,
            name: `${name}客户${tag}`,
            customerTypeId,
            settleMode: 'INDEPENDENT',
            contactName: '验收联系人',
            contactPhone: '13800000000',
            address,
            provinceCode: 330000, provinceName: '浙江省',
            cityCode: 330100, cityName: '杭州市',
            districtCode: 330106, districtName: '西湖区',
            ...(located
                ? {
                    longitude: tag === 'A' ? '113.94100000' : '114.07100000',
                    latitude: tag === 'A' ? '22.55100000' : '22.62100000',
                    geomCrs: 'GCJ02',
                }
                : {}),
        }));
        const created = await get<Row>(`/scm/customer/detail/${id}`);
        await post('/scm/customer/updateStatus', {customerId: id, version: created.version, status: 'COOPERATING'});
        return id;
    }

    locatedCustomerIdA = await newCustomer('A', '验收路A号', true);
    const customerB = await newCustomer('B', '验收路B号', true);
    // 第三个客户刻意不给坐标：只有这样才造得出「未定位停靠点」，41104 才是真被验到而不是被绕开。
    geolessCustomerId = await newCustomer('G', '验收路无坐标号', false);

    async function confirmedOrder(customerId: string, skuId: string, address: string) {
        let order = await post<Row>('/scm/order/create', {
            customerId,
            orderSource: 'ADMIN',
            address: {receiverName: '配送验收', receiverPhone: '13800000000', address},
            expectDeliveryTime: `${today} 10:00:00`,
            remark: name,
            items: [{skuId, orderedQuantity: '1.0000', manualPriceOverride: false}],
        });
        order = await post<Row>('/scm/order/submit', {orderId: order.orderId, version: order.version});
        const item = order.items[0];
        await post('/scm/order/item/actual-quantity', {
            orderId: order.orderId, itemId: item.itemId, version: item.version,
            actualQuantity: '1.0000', reason: '配送验收实重',
        });
        const after = await get<Row>(`/scm/order/detail/${order.orderId}`);
        const confirmed = await post<Row>('/scm/order/confirm', {orderId: order.orderId, version: after.version});
        expect(confirmed.status).toBe('CONFIRMED');
        await sortingCompleted(order.orderId, after);
        return {id: String(order.orderId), no: String(confirmed.orderNo ?? after.orderNo)};
    }

    /**
     * P1 之后「已确认」不再等于「可配送」：候选要求订单每条有效明细行都被**已完成**的分拣任务覆盖
     * （`docs/decisions.md`「P1 分拣管理裁决」第 11 条与补充第 18 条）。组单夹具因此必须走一遍
     * 真实分拣命令链（建单 → 录入 → 完成），不能靠改订单状态糊过去 —— 那正是被验收的口径本身。
     */
    async function sortingCompleted(orderId: string | number, detail: Row) {
        const itemIds = (detail.items as Row[]).map(i => Number(i.itemId));
        expect(itemIds.length, '订单至少要有一行明细').toBeGreaterThan(0);
        const created = await post<Row>('/scm/sorting/tasks', {
            warehouseId: Number(warehouse.id),
            salesOrderItemIds: itemIds,
            remark: `${name} 配送前置分拣`,
        });
        const entries = (created.items as Row[]).map(line => ({
            id: line.id,
            version: line.version,
            sortedQuantity: line.plannedQuantitySnapshot,
            result: 'NORMAL',
        }));
        await post(`/scm/sorting/tasks/${created.task.id}/entry`, {items: entries});
        const ready = await get<Row>(`/scm/sorting/tasks/${created.task.id}`);
        await post(`/scm/sorting/tasks/${created.task.id}/complete`, {version: ready.task.version});
        expect(ready.task.status, '前置分拣任务必须已完成').toBe('COMPLETED');
    }

    for (const [tag, customerId, skuId] of [
        ['A1', locatedCustomerIdA, skuIds[0]],
        ['A2', locatedCustomerIdA, skuIds[1]],
        ['B1', customerB, skuIds[0]],
    ] as const) {
        const created = await confirmedOrder(customerId, skuId, customerId === locatedCustomerIdA ? '验收路A号' : '验收路B号');
        orders.push({...created, customerId});
        void tag;
    }
    const geoless = await confirmedOrder(geolessCustomerId, skuIds[1], '验收路无坐标号');
    geolessOrderId = geoless.id;
    orders.push({...geoless, customerId: geolessCustomerId});
});

test.afterAll(async () => {
    if (routeId && api) {
        const current = await route();
        if (current.status === 'DRAFT' || current.status === 'PLANNED') {
            await envelope(`/scm/delivery/routes/${current.routeId ?? routeId}/cancel`,
                {version: Number(current.version), reason: 'E2E 收尾释放'});
        }
    }
    accounts.cleanup();
});

test('1｜L0 仓库与客户定位落成结构化坐标，可按明细回读', async () => {
    expect(warehouse.geomCrs, '仓库坐标系必须回读为 GCJ02').toBe('GCJ02');
    expect(String(warehouse.longitude)).toContain('113.93');

    const located = await get<Row>(`/scm/customer/detail/${locatedCustomerIdA}`);
    // 区划编码是整型列，接口按数字下发，断言不能依赖字符串形态
    expect(String(located.cityCode)).toBe('330100');
    expect(located.geomCrs).toBe('GCJ02');

    const geoless = await get<Row>(`/scm/customer/detail/${geolessCustomerId}`);
    expect(geoless.longitude ?? null, '未定位客户不得被猜出坐标').toBeNull();
    expect(geoless.cityName, '省市区归属与坐标是两件事，解析不出坐标也仍应有归属').toBe('杭州市');
});

test('2｜L1 司机与车辆在主档页新建并落库，重复编码被服务端拒绝', async ({page}) => {
    await authenticate(page, adminToken);

    await page.goto('/#/delivery/drivers');
    await expect(page.locator('#scm-delivery-driver-table')).toBeVisible();

    // 司机现在必须绑定系统员工才能启用，而绑定用的是员工选择器（需要按姓名检索）；
    // 本用例的目的是「主档新建 + 服务端唯一性」，因此改由接口带 employeeId 建司机，
    // 页面这一段只验「新列能渲染出绑定员工」，不重复验选择器交互。
    await ok(api, 'post', '/scm/delivery/drivers', {
        driverCode, driverName: `${name}司机`, phone: '13900000000',
        employeeId: accounts.employeeIds[accounts.readOnly], status: 'ENABLED',
    });
    await page.reload();
    await expect(page.locator('#scm-delivery-driver-table tbody tr.ant-table-row')
        .filter({hasText: driverCode}), '列表应只出现这一条新建司机').toHaveCount(1);
    // 绑定关系的证据在下面按接口回读 employeeId（列表里显示的是员工姓名，
    // 由临时账号脚本决定，不该用文案去猜），页面这一段只验新行确实出现在主档列表里。

    const drivers = await get<Row>(`/scm/delivery/drivers?keyword=${driverCode}&pageNum=1&pageSize=20`);
    expect(drivers.list, '司机必须真的落库').toHaveLength(1);
    driverId = String(drivers.list[0].id);
    expect(drivers.list[0].status).toBe('ENABLED');
    expect(drivers.list[0].employeeId, '启用司机必须带上绑定的员工 id').toBeTruthy();

    // 未绑定的启用司机是新增的服务端规则，不能只靠前端必填星号
    const unbound = await envelope('/scm/delivery/drivers', {
        driverCode: `${name}-NB`, driverName: '未绑定启用司机', phone: '13900000009', status: 'ENABLED',
    });
    expect(unbound.ok, '未绑定员工的启用司机不得建立').toBe(false);
    const stillOne = await get<Row>(`/scm/delivery/drivers?keyword=${name}-NB&pageNum=1&pageSize=20`);
    expect(stillOne.list, '被拒的司机不得留下档案行').toHaveLength(0);

    await page.goto('/#/delivery/vehicles');
    await expect(page.locator('#scm-delivery-vehicle-table')).toBeVisible();
    await button(page, '新建车辆').click();
    const vehicleModal = page.locator('.ant-modal:visible').last();
    await fillField(vehicleModal, '车牌号', vehicleNo);
    await fillField(vehicleModal, '车型', '验收面包车');
    await button(vehicleModal, '确 定').click();
    await expect(vehicleModal).toBeHidden();
    const vehicles = await get<Row>(`/scm/delivery/vehicles?keyword=${vehicleNo}&pageNum=1&pageSize=20`);
    expect(vehicles.list).toHaveLength(1);
    vehicleId = String(vehicles.list[0].id);

    // 重复编码这条断言要单独成立，就不能让「启用必须绑定员工」的校验先挡下来：
    // 用 DISABLED 提交，绑定校验被跳过，服务端才会走到 driver_code 的唯一性判定。
    const duplicate = await envelope('/scm/delivery/drivers', {
        driverCode, driverName: '重复', phone: '13900000001', status: 'DISABLED',
    });
    expect(duplicate.code, '重复司机编码必须由服务端挡住').toBe(41106);
});

test('3｜L2 页面新建草稿线路并快照仓库起点坐标', async ({page}) => {
    await authenticate(page, adminToken);
    await page.goto('/#/delivery/routes');
    await expect(page.locator('#scm-delivery-route-table')).toBeVisible();

    await button(page, '新建线路').click();
    // 按标题锁定要操作的那个抽屉：页面同时挂着「线路详情」抽屉的根节点，
    // 只按 .ant-drawer-open 类判断会在保存成功后仍然数到 1 个元素。
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '新建配送线路'});
    await expect(button(drawer, '保存线路')).toBeEnabled();
    await fillField(drawer, '线路名称', `${name} 1 线`);
    // 配送日期由表单默认给当天（antd DatePicker 的输入框只读，不靠 fill 硬塞值）
    await pickOption(page, drawer, '起点仓库', warehouseName);
    await button(drawer, '保存线路').click();
    const closed = await drawer.waitFor({state: 'detached', timeout: 8000}).then(() => true).catch(() => false);
    if (!closed) {
        // 抽屉不关就是保存被挡住；把页面自己的提示与表单现值一起带进失败信息，
        // 否则只剩一个 toHaveCount(0) 的红灯，看不出是校验、权限还是接口失败。
        const hints = await drawer.locator('.ant-form-item-explain-error, .ant-alert-message').allInnerTexts();
        const values = await drawer.locator('input').evaluateAll(
            (els) => els.map((e) => `${e.placeholder || e.type}=${e.value}`));
        throw new Error(`保存线路未生效；页面提示 ${JSON.stringify(hints)}；表单现值 ${JSON.stringify(values)}`);
    }

    const found = await get<Row>(`/scm/delivery/routes?keyword=${name}&pageNum=1&pageSize=20`);
    expect(found.list, '新线路必须能在列表里查到').toHaveLength(1);
    routeId = String(found.list[0].id);
    expect(String(found.list[0].deliveryDate).slice(0, 10), '表单默认配送日期应为当天')
        .toBe(today);

    const created = await route();
    expect(created.status).toBe('DRAFT');
    expect(String(created.startLongitude), '线路起点必须取仓库现值，不是页面自己填的').toContain('113.93');
    expect(created.startGeomCrs).toBe('GCJ02');
    expect(created.warehouseId != null, '线路必须绑定仓库').toBeTruthy();

    const withDriver = await envelope(`/scm/delivery/routes/${routeId}`, {
        routeName: `${name} 2 线`, deliveryDate: today, warehouseId: Number(warehouse.id),
        driverId: Number(driverId), vehicleId: Number(vehicleId), version: 999999,
    }, 'put');
    expect(withDriver.code, `过期 version 编辑必须被乐观锁拒绝，实际 ${withDriver.code} ${withDriver.msg}`)
        .toBe(40921);

    await put(`/scm/delivery/routes/${routeId}`, {
        routeName: `${name} 1 线`, deliveryDate: today, warehouseId: Number(warehouse.id),
        driverId: Number(driverId), vehicleId: Number(vehicleId), version: await routeVersion(),
    });
    const assigned = await route();
    expect(assigned.driverId != null, '司机/车辆可稍后分配，但分配后必须落库').toBeTruthy();
    expect(assigned.vehicleId != null).toBeTruthy();
});

test('4｜L2 组单：同客户同地址合并停靠点，未定位客户单照旧入线', async ({page}) => {
    await authenticate(page, adminToken);
    await page.goto('/#/delivery/routes');
    await page.locator('#scm-delivery-route-table tbody tr.ant-table-row').filter({hasText: name}).first()
        .getByText(accessibleName('详情')).click();
    const drawer = page.locator('.ant-drawer-open');
    await expect(drawer).toHaveCount(1);
    await drawer.getByRole('tab', {name: accessibleName('线路订单')}).click();

    // 候选清单是共享池且分页，且重新查询会重置勾选，因此按「一个订单一趟往返」驱动：
    // 关键字筛到该单 → 勾选 → 填组单原因 → 加入线路。这同时验到关键字过滤与逐次组单后的版本推进。
    for (const order of orders) {
        await button(drawer, '加入订单').click();
        const modal = page.locator('.ant-modal:visible').last();
        await expect(modal).toContainText('选择待配送订单');
        const keyword = field(modal, '客户').locator('input').first();
        await keyword.fill(order.no);
        await button(modal, '查询').click();
        const row = modal.locator('tbody tr.ant-table-row').filter({hasText: order.no}).first();
        await expect(row, `按订单号 ${order.no} 应筛到该单`).toBeVisible();
        await row.locator('input[type=checkbox]').check();
        await fillField(modal, '组单原因', '本次城区配送安排');
        await modal.getByRole('button', {name: /加\s*入\s*线\s*路/}).click();
        await expect(modal).toBeHidden({timeout: 15000});
    }

    const after = await detail();
    expect(after.orders, `4 张订单全部入线，实际 ${JSON.stringify((after.orders ?? []).map((o: Row) => o.orderId))
        }，期望 ${JSON.stringify(orders.map((o) => o.id))}`)
        .toHaveLength(4);
    // A 客户两张单同地址 → 1 个停靠点；B、无坐标客户各 1 个 → 共 3 个停靠点
    expect(after.stops).toHaveLength(3);
    expect(after.route.stopCount).toBe(3);
    expect(after.route.locatedCount, '未定位客户的停靠点不得被猜成已定位').toBe(2);

    const duplicate = await envelope(`/scm/delivery/routes/${routeId}/orders`, {
        version: await routeVersion(), orderIds: [Number(orders[0].id)], reason: '重复组单',
    });
    expect(duplicate.code, '已分配订单不能重复加入').toBe(41103);
});

test('5｜L2 未定位时确认规划被拒，页面补定位后放行', async ({page}) => {
    await authenticate(page, adminToken);

    const stale = await envelope(`/scm/delivery/routes/${routeId}/plan`, {version: 1});
    expect(stale.code, '过期 version 的规划请求必须先被乐观锁挡住').toBe(40921);

    const blocked = await envelope(`/scm/delivery/routes/${routeId}/plan`, {version: await routeVersion()});
    expect(blocked.code, '尚有停靠点未定位时不得确认规划').toBe(41104);
    expect(await (await route()).status).toBe('DRAFT');

    await page.goto('/#/delivery/routes');
    await page.locator('#scm-delivery-route-table tbody tr.ant-table-row').filter({hasText: name}).first()
        .getByText(accessibleName('路线')).click();
    const drawer = page.locator('.ant-drawer-open');
    await expect(drawer).toContainText('尚有 1 个停靠点未定位');
    // 只有「未定位」那个停靠点需要补坐标：按列表项文本筛，避免点到已定位项的同类按钮。
    await drawer.locator('li').filter({hasText: '未定位'}).first()
        .getByRole('button', {name: /定\s*位\s*\/\s*备\s*注/}).click();
    // 经纬度与坐标系是「停靠点定位与备注」弹窗里的内联输入，直接改字段值；
    // 只有需要地图选点时才会再叠一层选择器弹窗，因此必须按标题锁定外层弹窗。
    const modal = page.locator('.ant-modal:visible').filter({hasText: '停靠点定位与备注'}).last();
    await modal.getByPlaceholder('经度').fill(STOP_LNG);
    await modal.getByPlaceholder('纬度').fill(STOP_LAT);
    await modal.locator('.ant-select').last().click();
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option-content')
        .filter({hasText: 'GCJ02'}).first().click();
    await expect(modal.getByRole('button', {name: /已\s*定\s*位/})).toBeVisible()
        .catch(() => undefined);
    await modal.getByRole('button', {name: accessibleName('确 定')}).click();
    await expect(modal).toBeHidden({timeout: 15000});

    const located = await detail();
    const patched = located.stops.find((s: Row) => s.geomCrs === 'GCJ02' && String(s.longitude ?? '').includes('113.946'));
    expect(patched, '页面补的坐标必须落库').toBeTruthy();
    expect((await route()).locatedCount).toBe(3);
});

test('6｜L2 确认规划：状态迁移、重复请求与后续编辑全部封死', async ({page}) => {
    await authenticate(page, adminToken);
    await page.goto('/#/delivery/routes');
    await page.locator('#scm-delivery-route-table tbody tr.ant-table-row').filter({hasText: name}).first()
        .getByText(accessibleName('详情')).click();
    const drawer = page.locator('.ant-drawer-open');
    await button(drawer, '确认规划').click();
    await page.locator('.ant-modal-confirm:visible').getByRole('button', {name: accessibleName('确 定')}).click();
    await expect(drawer).toContainText('线路已规划', {timeout: 15000}).catch(async () => {
        // toast 可能已经消失；状态迁移本身才是承重断言，回读接口确认
        expect((await route()).status).toBe('PLANNED');
    });

    expect((await route()).status).toBe('PLANNED');

    const again = await envelope(`/scm/delivery/routes/${routeId}/plan`, {version: await routeVersion()});
    expect(again.code, '重复确认规划不能再次成功，必须是状态拒绝').toBe(41101);

    const addOrders = await envelope(`/scm/delivery/routes/${routeId}/orders`, {
        version: await routeVersion(), orderIds: [Number(geolessOrderId)], reason: '规划后加单',
    });
    expect(addOrders.code, '规划后不得再加单').toBe(41101);

    const remove = await envelope(`/scm/delivery/routes/${routeId}/orders/${orders[0].id}`,
        {version: await routeVersion(), reason: '规划后移除'}, 'delete');
    expect(remove.code, '规划后不得再移除订单').toBe(41101);

    const edit = await envelope(`/scm/delivery/routes/${routeId}`, {
        routeName: '改名', deliveryDate: today, warehouseId: Number(warehouse.id), version: await routeVersion(),
    }, 'put');
    expect(edit.code, '规划后不得改头信息').toBe(41101);

    for (const path of ['dispatch', 'complete', 'sign']) {
        const absent = await envelope(`/scm/delivery/routes/${routeId}/${path}`, {version: await routeVersion()});
        // L3 未实现：这些路径必须不存在（不是「存在但被权限挡住」），
        // 否则等于悄悄开了一条没有实发量口径的发货入口。
        expect(absent.ok, `L3 端点 /${path} 不该可用：${absent.status} code=${absent.code} ${absent.msg}`)
            .toBe(false);
        expect([404, 405].includes(absent.status) || ![0, 30005].includes(Number(absent.code)),
            `L3 端点 /${path} 必须是「不存在」，实际 ${absent.status} code=${absent.code} ${absent.msg}`)
            .toBe(true);
    }

    const order = await get<Row>(`/scm/order/detail/${orders[0].id}`);
    expect(order.status, '确认规划不改变订单状态').toBe('CONFIRMED');
});

test('7｜L2 取消线路：历史保留、分配释放回池', async () => {
    const cancelled = await envelope(`/scm/delivery/routes/${routeId}/cancel`,
        {version: await routeVersion(), reason: '验收收尾取消'});
    expect(cancelled.code, `取消失败：${cancelled.msg}`).toBe(0);

    const after = await detail();
    expect(after.route.status).toBe('CANCELLED');
    expect(after.orders, '取消保留历史订单行，不是删掉').toHaveLength(4);

    const candidates = await get<Row[]>('/scm/delivery/candidate-orders?deliveryDate=' + today + '&pageNum=1&pageSize=100');
    const back = (Array.isArray(candidates) ? candidates : (candidates as any).list ?? []).map((o: Row) => String(o.orderId));
    for (const order of orders) {
        expect(back, `取消后订单 ${order.id} 必须重新可分配`).toContain(order.id);
    }

    const replan = await envelope(`/scm/delivery/routes/${routeId}/plan`, {version: await routeVersion()});
    expect(replan.code, '已取消是终态，不得再规划').toBe(41101);
});

test('8｜权限负向：只读账号看不到写入口、写接口一律 30005', async ({page}) => {
    const readOnlyToken = await login(accounts, accounts.readOnly);
    const readOnly = await apiClient(readOnlyToken);
    await authenticate(page, readOnlyToken);

    const readable = await ok<Row>(readOnly, 'get', `/scm/delivery/routes/${routeId}`);
    // 独立账号回读：既证明 :query 读权限放行，也证明「取消保留历史」不是只有管理员看得见
    expect(readable.route.status, '只读账号应看到已取消线路的历史').toBe('CANCELLED');
    expect(readable.orders.length, '取消后线路订单明细仍可查看').toBeGreaterThan(0);
    for (const [path, body] of [
        [`/scm/delivery/routes/${routeId || 1}/plan`, {version: 1}],
        [`/scm/delivery/routes/${routeId || 1}/cancel`, {version: 1, reason: 'x'}],
        [`/scm/delivery/routes/${routeId || 1}/orders`, {version: 1, orderIds: [1], reason: 'x'}],
    ] as const) {
        const denied = await readOnly.post(path, {data: body, headers: {'Idempotency-Key': randomUUID()}});
        expect((await denied.json()).code, `${path} 必须由服务端把关`).toBe(30005);
    }
    const saveDriver = await readOnly.post('/scm/delivery/drivers', {
        data: {driverCode: `${name}-X`, driverName: '越权', phone: '13900000002', status: 'ENABLED'},
        headers: {'Idempotency-Key': randomUUID()},
    });
    expect((await saveDriver.json()).code).toBe(30005);

    await page.goto('/#/delivery/routes');
    await expect(page.locator('#scm-delivery-route-table')).toBeVisible();
    await expect(button(page, '新建线路')).toHaveCount(0);
    await page.goto('/#/delivery/drivers');
    await expect(button(page, '新建司机')).toHaveCount(0);

    const noRole = await apiClient(await login(accounts, accounts.noRole));
    const deniedRead = await noRole.get('/scm/delivery/routes?pageNum=1&pageSize=20');
    expect((await deniedRead.json()).code, '无角色连查询都不该通过').toBe(30005);
});
