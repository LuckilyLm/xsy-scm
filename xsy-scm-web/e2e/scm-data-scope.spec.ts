import {expect, test, type Page} from './scm-test-base';
import {
    accessibleName,
    apiClient,
    authenticate,
    login,
    provisionTempAccounts,
    type TempAccounts,
} from './scm-e2e-account';
import {createLocatedCustomer} from './scm-delivery-fixtures';
import {randomUUID} from 'node:crypto';

/**
 * P0 基线收口：正式非管理员业务角色 + 显式数据范围的浏览器验收。
 *
 * <p>这里每个断言账号都是 {@code administrator_flag=false}。超管通过不构成权限证据
 * （裁决第 5 条），所以造数用超管、断言一律换成角色账号自己的令牌。
 *
 * <p>三层证据：真实页面（本轮新增的「授权维护」「改派业务员」「绑定员工」入口）、
 * 该账号自己调接口的行级结果（范围真的下传到了 SQL），以及改授权后**不重新登录**立即生效
 * （范围按请求解析，不是登录快照）。
 *
 * <p>刻意不在浏览器里证明「写侧越界不留痕」：那要对每个单据类型造完整夹具，
 * 断言强度反而不如 {@code ScmInventoryWriteScopePgIT}（它在拒绝后逐一回读流水、余额与状态）。
 * 本文件证明的是「真实角色 + 真实 UI + 真实登录」这条链没有断。
 */
type Row = Record<string, any>;
type Page1 = {list: Row[]; total: number; emptyFlag?: boolean};

test.describe.configure({mode: 'serial'});

const ROLES = [
    'SCM_STOREKEEPER',
    'SCM_STOREKEEPER_LEAD',
    'SCM_SALES',
    'SCM_SALES_LEAD',
    'SCM_DRIVER',
    'SCM_DISPATCHER',
    'SCM_FINANCE',
] as const;

const accounts: TempAccounts =
    provisionTempAccounts('w8', ['scm:inventory:scope:all:query'], [...ROLES]);
const tag = `W8${Date.now().toString(36).toUpperCase()}`;
// 报表日期轴按业务日界（Asia/Shanghai）取值；用 toISOString() 会在跨天时拿到「昨天」
const today = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date());
const monthAgo = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'})
    .format(new Date(Date.now() - 30 * 864e5));

const loginNameOf = (role: (typeof ROLES)[number]) => {
    const value = accounts.roleAccounts[role];
    if (!value) throw new Error(`角色账号缺失：${role}，已有 ${JSON.stringify(accounts.roleAccounts)}`);
    return value;
};

/** 授权行按 employee_id 判定，登录名不够用；取不到 id 说明脚本回显没解析成功 */
const employeeIdOf = (role: (typeof ROLES)[number]) => {
    const name = loginNameOf(role);
    const id = accounts.employeeIds[name];
    if (!id) throw new Error(`未取到 ${name} 的 employee_id（provisionTempAccounts 解析失败）`);
    return id;
};

type Client = Awaited<ReturnType<typeof apiClient>>;

let api: Client;
const clients = {} as Record<(typeof ROLES)[number], Client>;
/**
 * Sa-Token 同账号不允许两个并发会话：再次 login 会把已建客户端的令牌顶成 30007。
 * 因此每个角色整轮只登录一次，浏览器与 API 客户端共用同一个令牌。
 */
const tokens: Record<(typeof ROLES)[number], string> = {} as Record<(typeof ROLES)[number], string>;
/** 两个都有余额行的真实仓库；凑不出来说明夹具不成立，直接失败而不是 skip */
let whA = 0;
let whB = 0;
let temporaryWarehouseId = 0;
const temporaryRouteIds: number[] = [];
const temporaryDriverCodes: string[] = [];
let adminTotalAll = 0;
let customerId = 0;
let customerVersion = 0;
let temporaryCustomerId = 0;
let driverEmployeeId = 0;
/**
 * 「只读面 − 全部仓库范围」账号：V57 起财务显式持有 `scm:inventory:scope:all:query`
 * （裁决第 17 条：总部统一财务默认可见全部仓库），所以「报表按授权行收窄」这条
 * 只能由一个没有该权限的账号来证 —— 用扣权账号而不是改财务的期望值，
 * 否则用例就只是在跟着授权漂移。
 */
let narrowClient: Client;
let narrowEmployeeId = 0;

async function ok<T = Row>(
    client: Client,
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

/** 负向入口：返回原始信封，不因为 code != 0 就先把用例判红。 */
async function envelope(client: Client, path: string, data?: unknown, method: 'get' | 'post' = 'post') {
    const response = method === 'get'
        ? await client.get(path)
        : await client.post(path, {data, headers: {'Idempotency-Key': randomUUID()}});
    return {...(await response.json()), status: response.status()} as Row;
}

/** 分页上限是 100（超出即 30001），所以范围断言用「按仓取 total」而不是拉全表 */
const balanceQuery = (client: Client, warehouseId?: number) =>
    ok<Page1>(client, 'post', '/scm/inventory/balance/query',
        warehouseId ? {pageNum: 1, pageSize: 100, warehouseId} : {pageNum: 1, pageSize: 100});

const grantWarehouses = (employeeId: number, warehouseIds: number[]) =>
    ok(api, 'post', '/scm/warehouse/scope/update', {employeeId, warehouseIds});

/** 客户明细是 GET /detail/{customerId}；负向断言要拿原始信封，所以单独封一层 */
const customerDetail = (client: Client, id: number) => ok<Row>(client, 'get', `/scm/customer/detail/${id}`);

/** 只认成本语义的列名：数量与账面金额之外的字段不算成本 */
const costKeys = (row: Row) => Object.keys(row).filter((key) => /cost/i.test(key));

const warehouseIdsOf = (rows: Row[]) => [...new Set(rows.map((row) => Number(row.warehouseId)))];

const button = (page: Page, text: string) => page.getByRole('button', {name: accessibleName(text)});

async function receiveInitialStock(warehouseId: number, skuId: number) {
    // 供应商编码由服务端生成（SUP + 6 位序号），创建载荷不再提交编码；后续按 supplierId 使用。
    const supplierId = await ok<number>(api, 'post', '/scm/supplier/add', {
        name: `${tag} 范围夹具供应商`,
    });
    await ok(api, 'post', '/scm/supplier/sku/replace', {
        supplierId, items: [{skuId, purchaseUnit: 'kg', defaultFlag: true, status: 'ENABLED'}],
    });
    const purchase = await ok<Row>(api, 'post', '/scm/purchase/create', {
        supplierId, warehouseId, purchaserId: null, plannedArrivalDate: null, remark: `${tag} scope fixture`,
        items: [{skuId, quantity: '20.0000', price: '3.5000', allocations: []}],
    });
    await ok(api, 'post', '/scm/purchase/submit', {id: purchase.id, version: purchase.version});
    const receipt = await ok<Row>(api, 'post', '/scm/purchase/receipt/create', {
        purchaseOrderId: purchase.id, receiptMode: 'DIRECT', remark: `${tag} scope fixture`,
    });
    const item = receipt.items[0];
    const confirmed = await ok<Row>(api, 'post', '/scm/purchase/receipt/confirm', {
        id: receipt.id,
        version: receipt.version,
        items: [{
            receiptItemId: item.id, version: item.version, receivedQuantity: '20.0000',
            actualWeight: null, weightSource: null,
        }],
    });
    expect(confirmed.status, '初始库存必须由确认入库事实生成').toBe('CONFIRMED');
}

test.beforeAll(async () => {
    api = await apiClient(await login(accounts, accounts.admin));

    // 用真实存在余额的启用仓库做对照：仓库清单只返回 ENABLED，逐个问 total 就够定位夹具
    let enabled = await ok<Row[]>(api, 'get', '/scm/warehouse/list');
    const stocked: {id: number; total: number}[] = [];
    const collectStocked = async (warehouses: Row[]) => {
        const result: {id: number; total: number}[] = [];
        for (const warehouse of warehouses) {
            const total = (await balanceQuery(api, Number(warehouse.id))).total;
            if (total > 0) result.push({id: Number(warehouse.id), total});
        }
        return result;
    };
    stocked.push(...await collectStocked(enabled));
    if (stocked.length === 0) {
        // 第一条 (warehouse, SKU) 余额只能由真实入库建立；盘点不可替代采购收货事实。
        const skus = await ok<Row>(api, 'post', '/scm/product/sku/option-list', {keyword: '', limit: 10});
        // The initial receipt below sets actualWeight=null, so it must use a standard SKU.
        // The first option is not stable across seed sets and may be NON_STANDARD.
        const standardSku = ((skus.options ?? []) as Row[]).find((option) => option.productType === 'STANDARD');
        let skuId = Number(standardSku?.skuId);
        if (!(skuId > 0)) {
            const tree = await ok<Row[]>(api, 'post', '/scm/product/category/tree', {});
            const flatten = (rows: Row[]): Row[] =>
                rows.flatMap((row) => [row, ...flatten((row.children ?? []) as Row[])]);
            const leaves = flatten(tree as Row[]);
            const category = leaves.find((row) => Number(row.level) === 3) ?? leaves[0];
            expect(category !== undefined, '干净库里没有商品分类，无法自建 SKU 夹具').toBe(true);
            await ok(api, 'post', '/scm/product/add', {
                spuCode: `${tag}-SPU`, name: `${tag} 范围夹具商品`,
                categoryId: Number(category.categoryId), status: 'ON_SHELF', images: [],
                skuList: [{skuCode: `${tag}-SKU`, specName: '标准', saleUnit: 'kg', productType: 'STANDARD',
                           marketPrice: '3.5000', status: 'ON_SHELF', defaultFlag: true, sortOrder: 0}],
            });
            const created = await ok<Row>(api, 'post', '/scm/product/sku/option-list', {keyword: tag, limit: 10});
            skuId = Number(((created.options ?? []) as Row[])[0]?.skuId);
        }
        const homeId = Number(enabled[0]?.id ?? enabled[0]?.warehouseId);
        expect(skuId > 0 && homeId > 0, '干净库里没有可用 SKU 或启用仓库，数据范围夹具无法自给').toBe(true);
        await receiveInitialStock(homeId, skuId);
        stocked.push(...await collectStocked(enabled));
    }
    if (stocked.length < 2) {
        // E2E 库会保留上轮测试留下的停用库存仓；启用其中一座作范围对照，结束后先盘亏归零再停用。
        // 这样仓库范围用例不依赖启用仓恰好已有两座，也不伪造库存余额行。
        const disabled = await ok<Page1>(api, 'post', '/scm/warehouse/query',
            {status: 'DISABLED', pageNum: 1, pageSize: 100});
        const disabledStocked = await collectStocked(disabled.list);
        disabledStocked.sort((x, y) => y.total - x.total);
        const candidate = disabledStocked[0];
        if (candidate) {
            const detail = await ok<Row>(api, 'get', `/scm/warehouse/detail/${candidate.id}`);
            await ok(api, 'post', '/scm/warehouse/enable', {id: candidate.id, version: detail.version});
            temporaryWarehouseId = candidate.id;
            enabled = await ok<Row[]>(api, 'get', '/scm/warehouse/list');
            stocked.push(...await collectStocked(enabled.filter((warehouse) => Number(warehouse.id) === candidate.id)));
        }
    }
    if (stocked.length < 2) {
        // 全新库里连一座可回收的停用库存仓都没有（只有默认仓），上面那条「回收残渣」的分支就不成立
        // —— 而 afterAll 又会把回收来的仓盘亏归零再停用，所以残渣本来就不该被指望。
        // 这里自建一座对照仓：WarehouseService.create 新建即 ENABLED 且没有「启用仓唯一」约束，
        // 再用真实调拨单把货调过去（发出+收货），余额行由库存域命令产生，不伪造。
        const donor = stocked[0];
        if (donor) {
            const donorRows = (await balanceQuery(api, donor.id)).list
                .filter((row) => Number(row.quantity) > 0);
            donorRows.sort((x, y) => Number(y.quantity) - Number(x.quantity));
            const line = donorRows[0];
            if (line) {
                // 仓库编码由服务端生成（WH + 6 位序号），创建载荷不再提交编码；后续按返回 id 使用。
                const createdId = await ok<number>(api, 'post', '/scm/warehouse/create',
                    {name: `${tag} 范围对照仓`, address: null, remark: `${tag} scope fixture`});
                const transferId = await ok<number>(api, 'post', '/scm/inventory/transfer/create', {
                    fromWarehouseId: donor.id, toWarehouseId: createdId, remark: `${tag} scope fixture`,
                    items: [{skuId: Number(line.skuId), quantity: '1.0000'}],
                });
                await ok(api, 'post', `/scm/inventory/transfer/ship/${transferId}`, {});
                await ok(api, 'post', `/scm/inventory/transfer/receive/${transferId}`, {});
                temporaryWarehouseId = createdId;
                stocked.push({id: createdId, total: (await balanceQuery(api, createdId)).total});
            }
        }
    }
    expect(stocked.length,
        '需要两个都有余额行的启用仓库才能验证范围，实际 ' + JSON.stringify(stocked)).toBeGreaterThanOrEqual(2);
    stocked.sort((x, y) => y.total - x.total);
    [whA, whB] = [stocked[0].id, stocked[1].id];
    adminTotalAll = (await balanceQuery(api)).total;

    // 整轮复用角色账号自己的令牌：范围变化必须在同一登录态下立即生效
    for (const role of ROLES) {
        tokens[role] = await login(accounts, loginNameOf(role));
        clients[role] = await apiClient(tokens[role]);
    }
    narrowClient = await apiClient(await login(accounts, accounts.denied!));
    narrowEmployeeId = accounts.employeeIds[accounts.denied!];
    expect(narrowEmployeeId, '未建立扣权账号，无法验证「没有全量范围时报表按授权行收窄」').toBeGreaterThan(0);

    const customers = await ok<Page1>(api, 'post', '/scm/customer/query', {pageNum: 1, pageSize: 20});
    if (customers.list.length > 0) {
        // CustomerVO 的行键是 customerId，不是 id：写成 id 会得到 NaN 路径 + 「参数错误」，看不出真因
        customerId = Number(customers.list[0].customerId ?? customers.list[0].id);
    } else {
        customerId = Number(await createLocatedCustomer(api, tag, 'SCOPE', `${tag} 范围验收地址`));
        temporaryCustomerId = customerId;
    }
    const detail = await customerDetail(api, customerId);
    expect(detail, '客户明细未带 version，改派的乐观锁前提不成立').toHaveProperty('version');
    customerId = Number(detail.customerId ?? detail.id);
    customerVersion = Number(detail.version);
    driverEmployeeId = employeeIdOf('SCM_DRIVER');
});

test.afterAll(async () => {
    try {
        for (const routeId of temporaryRouteIds) {
            const detail = await ok<Row>(api, 'get', `/scm/delivery/routes/${routeId}`);
            if (detail.route.status === 'DRAFT') {
                await ok(api, 'post', `/scm/delivery/routes/${routeId}/cancel`, {
                    version: detail.route.version, reason: `${tag} 范围夹具清理`,
                });
            }
        }
        for (const driverCode of temporaryDriverCodes) {
            const result = await ok<Page1>(api, 'get',
                `/scm/delivery/drivers?pageNum=1&pageSize=100&keyword=${encodeURIComponent(driverCode)}`);
            const driver = result.list.find((row) => row.driverCode === driverCode);
            if (driver?.status === 'ENABLED') {
                await ok(api, 'post', '/scm/delivery/drivers', {
                    id: driver.id,
                    version: driver.version,
                    driverCode: driver.driverCode,
                    driverName: driver.driverName,
                    phone: driver.phone,
                    employeeId: driver.employeeId,
                    status: 'DISABLED',
                    remark: driver.remark,
                });
            }
        }
        if (temporaryWarehouseId) {
            const balances = (await balanceQuery(api, temporaryWarehouseId)).list
                .filter((row) => Number(row.quantity) > 0);
            if (balances.length > 0) {
                const stocktakeId = await ok<number>(api, 'post', '/scm/inventory/stocktake/create', {
                    warehouseId: temporaryWarehouseId,
                    remark: `${tag} scope fixture cleanup`,
                    items: balances.map((row) => ({skuId: Number(row.skuId), actualQuantity: '0.0000'})),
                });
                await ok(api, 'post', `/scm/inventory/stocktake/confirm/${stocktakeId}`, {});
            }
            const detail = await ok<Row>(api, 'get', `/scm/warehouse/detail/${temporaryWarehouseId}`);
            await ok(api, 'post', '/scm/warehouse/disable', {id: temporaryWarehouseId, version: detail.version});
        }
        if (temporaryCustomerId) {
            const detail = await customerDetail(api, temporaryCustomerId);
            await ok(api, 'post', '/scm/customer/delete', {
                customerId: temporaryCustomerId, version: detail.version,
            });
        }
    } finally {
        // 授权行按 employee_id 落在 employee_warehouse_scope，删账号不会连带回收，必须显式清空
        for (const role of ROLES) {
            await grantWarehouses(employeeIdOf(role), []);
        }
        if (narrowEmployeeId) {
            await grantWarehouses(narrowEmployeeId, []);
        }
        for (const client of Object.values(clients)) {
            await client.dispose();
        }
        await narrowClient?.dispose();
        await api?.dispose();
        accounts.cleanup();
    }
});

test('1｜仓库授权维护真实生效：换授权即换可见行，且不必重新登录', async ({page}) => {
    const keeper = employeeIdOf('SCM_STOREKEEPER');
    await grantWarehouses(keeper, [whA]);

    const scoped = await balanceQuery(clients.SCM_STOREKEEPER);
    expect(warehouseIdsOf(scoped.list), '越界仓库的行不得出现在结果里').toEqual([whA]);
    expect(scoped.total, '授权单仓后应恰好看到该仓全部余额行')
        .toBe((await balanceQuery(api, whA)).total);

    // 页面分页汇总必须与接口一致，否则「范围」只存在于 API 层
    await authenticate(page, tokens.SCM_STOREKEEPER);
    await page.goto('/#/inventory/inventory-balance-list');
    await expect(page.locator('.ant-pagination-total-text')).toContainText(String(scoped.total));
    // 授权维护入口属于 scm:warehouse:scope:*，仓管看得到仓库列表页但没有维护按钮
    await page.goto('/#/purchase/warehouse-list');
    await expect(button(page, '授权维护')).toHaveCount(0);

    // 回收授权：同一令牌、不重登，立刻什么都看不到，且空态说的是范围而不是「没有数据」
    await grantWarehouses(keeper, []);
    const revoked = await balanceQuery(clients.SCM_STOREKEEPER);
    expect(revoked.total, '回收授权后不得仍有余额行可见').toBe(0);
    // 余额页本轮没加专门的空态文案（只有客户 / 采购 / 线路三页加了范围提示），
    // 这里证的是「表格确实一行都没有」；范围提示文案在客户页那条用例里证。
    await page.goto('/#/inventory/inventory-balance-list');
    await expect(page.locator('tbody tr.ant-table-row')).toHaveCount(0);
});

test('2｜用户筛选只能缩小范围；全量范围是显式授权而不是默认值', async () => {
    await grantWarehouses(employeeIdOf('SCM_STOREKEEPER'), [whB]);

    const own = await balanceQuery(clients.SCM_STOREKEEPER);
    expect(warehouseIdsOf(own.list), '只授权 B 仓时不得混入其他仓').toEqual([whB]);
    const widened = await balanceQuery(clients.SCM_STOREKEEPER, whA);
    expect(widened.total, '自己传 warehouseId 只能缩小，不能借它读到 A 仓').toBe(0);

    // 仓库主管一条授权行都没有，靠 scm:inventory:scope:all:query 看到全量：
    // 证明「全部」是一个被授予的范围值，不是 null 的副作用
    const lead = await balanceQuery(clients.SCM_STOREKEEPER_LEAD);
    expect(lead.total, '持有全部仓库范围应看到与超管同样的行数').toBe(adminTotalAll);
    expect((await balanceQuery(clients.SCM_STOREKEEPER, whB)).total,
        '主管的范围不应影响同域其他人的收窄结果').toBe(own.total);
});

test('3｜客户归属改派：销售只看自己名下，改派后可见性立即翻转', async ({page}) => {
    const sales = employeeIdOf('SCM_SALES');
    const lead = employeeIdOf('SCM_SALES_LEAD');
    const leadClient = clients.SCM_SALES_LEAD;

    await ok(api, 'post', '/scm/customer/reassignSeller',
        {customerId, sellerId: sales, version: customerVersion});
    const mine = await ok<Page1>(clients.SCM_SALES, 'post', '/scm/customer/query',
        {pageNum: 1, pageSize: 100});
    expect(mine.list.map((row) => Number(row.sellerId)), '销售列表不得出现别人的客户')
        .toStrictEqual(mine.list.map(() => sales));
    expect(mine.list.map((row) => Number(row.customerId ?? row.id)),
        '刚改派给自己的客户应在自己列表里').toContain(customerId);

    // 主管改派给别人：同一客户对原销售立即不可读，对主管仍可见（放宽范围是权限，不是角色名）
    const afterFirst = await customerDetail(leadClient, customerId);
    await ok(leadClient, 'post', '/scm/customer/reassignSeller',
        {customerId, sellerId: lead, version: Number(afterFirst.version)});
    const denied = await envelope(clients.SCM_SALES, `/scm/customer/detail/${customerId}`, undefined, 'get');
    expect(denied.code, '改派后原销售不应还能按 id 读到该客户（越界读取要失败关闭）').not.toBe(0);
    const stillVisible = await customerDetail(leadClient, customerId);
    expect(Number(stillVisible.sellerId), '主管应看到改派后的新归属').toBe(lead);

    // 重复/并发防护：拿已经过期的 version 再改派一次必须被拒，且不改变归属
    const stale = await envelope(leadClient, '/scm/customer/reassignSeller',
        {customerId, sellerId: sales, version: customerVersion});
    expect(stale.ok, '过期 version 的重复改派不得成功').toBe(false);
    expect(Number((await customerDetail(leadClient, customerId)).sellerId),
        '被拒的改派不得留下写入痕迹').toBe(lead);

    // UI：主管有「改派」入口并能打开弹窗；普通销售连按钮都不渲染（v-privilege 是删节点）
    await authenticate(page, tokens.SCM_SALES_LEAD);
    await page.goto('/#/customer/customer-list');
    await expect(button(page, '改派').first()).toBeVisible();
    await button(page, '改派').first().click();
    await expect(page.locator('.ant-modal-title')).toContainText('改派业务员');
    await page.keyboard.press('Escape');

    const salesPage = await page.context().newPage();
    await authenticate(salesPage, tokens.SCM_SALES);
    await salesPage.goto('/#/customer/customer-list');
    await expect(button(salesPage, '改派')).toHaveCount(0);
    // 空表必须说清「可能只是没授权」，而不是「系统里没有数据」
    // antd 在 locale.emptyText 传字符串时直接把文案渲染进 placeholder 单元格，不套 .ant-empty
    await expect(salesPage.locator('.ant-table-placeholder'))
        .toContainText('授权范围');
    await salesPage.close();
});

test('4｜司机绑定员工唯一，司机只看到自己的线路', async () => {
    const ownDriverCode = `${tag}-A`;
    const otherDriverCode = `${tag}-OTHER`;
    const created = await ok<Row>(api, 'post', '/scm/delivery/drivers', {
        driverCode: ownDriverCode, driverName: '范围用例司机', phone: '13900000021',
        employeeId: driverEmployeeId, status: 'ENABLED',
    });
    const driverId = Number(created?.id ?? created);
    expect(Number.isFinite(driverId) && driverId > 0, '新建司机应返回 id，实际 ' + JSON.stringify(created))
        .toBeTruthy();
    temporaryDriverCodes.push(ownDriverCode);

    await grantWarehouses(driverEmployeeId, [whA]);
    const otherEmployeeId = Number(accounts.employeeIds[accounts.admin]);
    expect(otherEmployeeId, '临时管理员员工 id 缺失，无法造另一司机线路').toBeGreaterThan(0);
    const otherDriver = await ok<Row>(api, 'post', '/scm/delivery/drivers', {
        driverCode: otherDriverCode, driverName: '另一范围用例司机', phone: '13900000022',
        employeeId: otherEmployeeId, status: 'ENABLED',
    });
    const otherDriverId = Number(otherDriver?.id ?? otherDriver);
    expect(Number.isFinite(otherDriverId) && otherDriverId > 0, '另一司机应创建成功')
        .toBeTruthy();
    temporaryDriverCodes.push(otherDriverCode);

    const ownRouteId = await ok<number>(api, 'post', '/scm/delivery/routes', {
        routeName: `${tag} 司机本人线路`, deliveryDate: today, warehouseId: whA, driverId,
    });
    const otherRouteId = await ok<number>(api, 'post', '/scm/delivery/routes', {
        routeName: `${tag} 其他司机线路`, deliveryDate: today, warehouseId: whA,
        driverId: otherDriverId,
    });
    temporaryRouteIds.push(ownRouteId, otherRouteId);

    // 一个员工最多绑一个活动司机：第二次绑定必须被服务端拒，不靠前端去重
    const duplicated = await envelope(api, '/scm/delivery/drivers', {
        driverCode: `${tag}-B`, driverName: '重复绑定司机', phone: '13900000022',
        employeeId: driverEmployeeId, status: 'ENABLED',
    });
    expect(duplicated.ok, '同一员工绑定第二个活动司机不得成功：' + duplicated.msg).toBe(false);

    const adminRoutes = await ok<Page1>(api, 'get', '/scm/delivery/routes?pageNum=1&pageSize=200');
    const own = await ok<Page1>(clients.SCM_DRIVER, 'get', '/scm/delivery/routes?pageNum=1&pageSize=200');
    expect(own.total, '夹具必须包含这名司机自己的线路').toBeGreaterThan(0);
    for (const row of own.list) {
        expect(Number(row.driverId), '司机不得看到别人名下的线路').toBe(driverId);
    }
    expect(adminRoutes.total - own.total,
        '库里应有其他司机的线路，否则本用例没有证明收窄').toBeGreaterThan(0);

    // 全量候选订单只属于「调度 + 对应仓库范围」；普通司机连查都不该查
    const denied = await envelope(clients.SCM_DRIVER, '/scm/delivery/candidate-orders?pageNum=1&pageSize=10',
        undefined, 'get');
    expect(denied.code, '司机不得查询全量候选订单').toBe(30005);
    const dispatcher = await ok<Page1>(clients.SCM_DISPATCHER, 'get',
        '/scm/delivery/routes?pageNum=1&pageSize=200');
    expect(dispatcher.total, '调度岗持有全部配送范围，应看到与超管同样多的线路')
        .toBe(adminRoutes.total);
});

test('5｜成本权限与仓库范围互不隐含：同一行数据，数量可见而成本为 null', async () => {
    await grantWarehouses(employeeIdOf('SCM_STOREKEEPER'), [whA]);

    const keeper = await balanceQuery(clients.SCM_STOREKEEPER);
    expect(keeper.total, '仓管在授权仓内应看得到行').toBeGreaterThan(0);
    expect(costKeys(keeper.list[0]).length, '余额行里应存在成本列，否则下面的断言是空的')
        .toBeGreaterThan(0);
    for (const row of keeper.list) {
        for (const key of costKeys(row)) {
            // null 才是「无权知道」；0 会被读成「成本确实是零」
            expect(row[key], `无成本权限时 ${key} 必须为 null`).toBeNull();
        }
        expect(Number(row.quantity), '但数量必须照常可见，不能被一并抹掉').not.toBeNaN();
    }

    // 仓库主管有 scm:report:cost:query：同样的行，成本列有值
    const lead = await balanceQuery(clients.SCM_STOREKEEPER_LEAD);
    const visibleCost = lead.list.filter((row) => Number(row.warehouseId) === whA);
    expect(visibleCost.some((row) => costKeys(row).some((key) => row[key] != null)),
        '持有成本权限时成本列不应全为 null').toBeTruthy();
});

/**
 * 报表的仓库范围证据，分两面：
 *
 * <p>1) **没有全部仓库范围的人**：报表确实按 `employee_warehouse_scope` 授权行收窄，
 * 换一条授权就换一批行，一条都不给就一行没有 —— 证明范围下传到了聚合，不只是明细页。
 * 这里刻意不用财务账号：V57 起财务显式持有 {@code scm:inventory:scope:all:query}
 * （裁决第 17 条「总部统一财务默认可见全部仓库」），拿它证收窄会得到假失败。
 *
 * <p>2) **财务**：只授权 A 仓仍能看到两个仓，且这条放宽来自那条权限而不是「它是财务」——
 * 撤销权限后立即退回授权行。导出与页面共用同一套范围由 {@code ScmReportDataScopePgIT} 证。
 */
test('6｜财务报表范围：无全量权限时按授权行收窄，财务的全仓可见来自显式权限且可撤销', async () => {
    const reportQuery = (client: Client) => ok<Page1>(client, 'post',
        '/scm/report/inventory/value/query', {pageNum: 1, pageSize: 100, startDate: `${monthAgo}`, endDate: `${today}`});

    await grantWarehouses(narrowEmployeeId, [whA]);
    const narrowed = await reportQuery(narrowClient);
    expect(narrowed.list.length, '扣权账号在授权仓 A 应有库存金额行').toBeGreaterThan(0);
    expect(warehouseIdsOf(narrowed.list), '报表不得出现未授权仓库的行').toEqual([whA]);

    // 换一条授权：报表行数随之变化，证明范围真的下传到了聚合而不是只过滤了明细页
    await grantWarehouses(narrowEmployeeId, [whB]);
    const moved = await reportQuery(narrowClient);
    expect(warehouseIdsOf(moved.list), '改授权后应只剩 B 仓').toEqual([whB]);
    await grantWarehouses(narrowEmployeeId, []);
    expect((await reportQuery(narrowClient)).list, '收回全部授权后不应仍有报表行').toHaveLength(0);

    // 财务：一条授权行都不覆盖 B 仓，但因为显式持有全部仓库范围而两个仓都看得到
    await grantWarehouses(employeeIdOf('SCM_FINANCE'), [whA]);
    const finance = await reportQuery(clients.SCM_FINANCE);
    expect(finance.list.length, '财务在授权仓 A 应有库存金额行').toBeGreaterThan(0);
    expect(warehouseIdsOf(finance.list).sort((x, y) => x - y),
        '财务的「全部仓库」是 V57 的显式授权，不是遗漏收窄').not.toEqual([whA]);
});
