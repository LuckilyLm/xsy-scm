/*
 * 本文件由按域拆分的 E2E 用例合并而成：每个 test.describe 对应一份原文件，
 * 块内状态与钩子互不共享，执行顺序按来源文件排列。
 */
import {test, expect} from './scm-test-base';
import {test as pwTest} from '@playwright/test';
import {accessibleName, apiClient, authenticate, login, provisionTempAccounts, type TempAccounts} from './scm-e2e-account';
import {createLocatedCustomer} from './scm-delivery-fixtures';
import {randomUUID, randomBytes} from 'node:crypto';
import {request, type APIRequestContext, type Page, type Locator} from '@playwright/test';
import {execFileSync} from 'node:child_process';
import {copyFileSync, readFileSync} from 'node:fs';
import smCrypto from 'sm-crypto';

// ---------------------------------------------------------------- 来源：e2e/scm-data-scope.spec.ts
test.describe('scm-data-scope', () => {

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
      // 改派业务员已收进操作列「更多」
      const leadRow = page.locator('.ant-table-tbody tr.ant-table-row').first();
      await leadRow.getByRole('button', {name: /更多/}).click();
      await page.locator('.ant-dropdown:visible').getByText('改派业务员', {exact: true}).click();
      await expect(page.locator('.ant-modal-title')).toContainText('改派业务员');
      await page.keyboard.press('Escape');

      const salesPage = await page.context().newPage();
      await authenticate(salesPage, tokens.SCM_SALES);
      await salesPage.goto('/#/customer/customer-list');
      // 普通销售连「更多」里的改派项都不渲染（菜单项按权限裁剪）
      const salesRow = salesPage.locator('.ant-table-tbody tr.ant-table-row').first();
      if (await salesRow.count()) {
          await salesRow.getByRole('button', {name: /更多/}).click();
          await expect(salesPage.locator('.ant-dropdown:visible').getByText('改派业务员', {exact: true}))
              .toHaveCount(0);
          await salesPage.keyboard.press('Escape');
      }
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

});

// ---------------------------------------------------------------- 来源：e2e/scm-order.spec.ts
test.describe('scm-order', () => {
  const test = pwTest;
  /* W4 real PostgreSQL/API/browser acceptance. Account secrets stay in memory. */
  const apiUrl='http://127.0.0.1:18080';
  const name='w4_e2e_'+Date.now().toString(36),password='W4@'+randomBytes(8).toString('hex');
  const env={...process.env,W4_E2E_NAME:name,W4_E2E_PASSWORD:password};
  let api:APIRequestContext,token:string,customerId:string,customerCode:string,skuId:string,standardSkuId:string,standardSkuCode:string,weightSkuCode:string;
  let confirmedOrder:any;
  test.describe.configure({mode:'serial'});
  async function login(account:string){const c=await request.newContext({baseURL:apiUrl});const captcha=(await(await c.get('/login/getCaptcha')).json()).data;const source=readFileSync('src/lib/encrypt.ts','utf8');const key=/const SM4_KEY = '([^']+)'/.exec(source)![1];const encrypted=Buffer.from(smCrypto.sm4.encrypt(password,Buffer.from(key).toString('hex'))).toString('base64');const r=await(await c.post('/login',{data:{loginName:account,password:encrypted,captchaUuid:captcha.captchaUuid,captchaCode:captcha.captchaText,loginDevice:1}})).json();expect(r.code).toBe(0);await c.dispose();return r.data.token;}
  async function post(path:string,data:unknown,key=randomUUID()){const r=await(await api.post(path,{data,headers:{'Idempotency-Key':key}})).json();expect(r.code,`${path}: ${r.msg}`).toBe(0);return r.data;}
  function draft(sku=skuId){return {customerId,orderSource:'ADMIN',address:{receiverName:'W4验收',receiverPhone:'13800000000',address:'验收地址'},remark:name,items:[{skuId:sku,orderedQuantity:'2.0000',manualPriceOverride:false}]};}
  async function detail(id:string){const r=await(await api.get('/scm/order/detail/'+id)).json();expect(r.code).toBe(0);return r.data;}
  async function browse(page:Page,path='/order/order-list'){await page.addInitScript(v=>localStorage.setItem('smart_admin_user_token',v),token);await page.goto('/#'+path);}
  /**
   * 用模板自身作为载体写数据行：保留 C/E/H 列的文本格式，
   * 否则 Excel/openpyxl 会把客户编码、SKU 编码、手机号转成数值。
   */
  const writeRowsScript=`from openpyxl import load_workbook\nimport sys,json\np=sys.argv[1]; rows=json.loads(sys.argv[2])\nwb=load_workbook(p); ws=wb.active\nfor r in range(2,ws.max_row+1):\n    for c in range(1,13): ws.cell(r,c).value=None\nfor i,row in enumerate(rows,2):\n    for j,v in enumerate(row,1): ws.cell(i,j).value=v\nwb.save(p)`;
  function fillRows(path:string,rows:unknown[][]){execFileSync('python',['-c',writeRowsScript,path,JSON.stringify(rows)]);}
  async function select(page:Page,label:string,value:string){const box=page.locator(`.ant-form-item:has(#form_item_${label}) .ant-select-selector`);await box.click();await box.locator('input').fill(value);await page.locator('.ant-select-dropdown:visible').getByText(value,{exact:false}).first().click();}
  test.beforeAll(async()=>{
   execFileSync('python',['../tools/e2e_fixtures.py', 'account-setup', '--wave', 'w4'],{env,stdio:'pipe'});token=await login(name);api=await request.newContext({baseURL:apiUrl,extraHTTPHeaders:{Authorization:`Bearer ${token}`}});
   const types=(await(await api.post('/scm/customer/type/option/list',{data:{}})).json()).data;
   customerId=await post('/scm/customer/add',{name:name,customerTypeId:types[0].typeId,settleMode:'INDEPENDENT',contactName:'W4验收',contactPhone:'13800000000',address:'验收地址'});
   // 客户编码由服务端生成（CUS + 6 位序号）：后面的 Excel 导入必须以回读的真实编码为匹配键。
   const customer=(await(await api.get('/scm/customer/detail/'+customerId)).json()).data;customerCode=String(customer.customerCode);expect(customerCode).toMatch(/^CUS\d{6,}$/);await post('/scm/customer/updateStatus',{customerId,version:customer.version,status:'COOPERATING'});
   const tree=(await(await api.post('/scm/product/category/tree',{data:{}})).json()).data;const flatten=(rows:any[]):any[]=>rows.flatMap(x=>[x,...flatten(x.children??[])]);const category=flatten(tree).find(x=>x.level===3)??flatten(tree)[0];
   await post('/scm/product/add',{spuCode:name.toUpperCase(),name:name+'商品',categoryId:category.categoryId,status:'ON_SHELF',images:[],skuList:[{skuCode:name.toUpperCase()+'-KG',specName:'散装',specValues:{规格:'散装'},saleUnit:'kg',productType:'NON_STANDARD',marketPrice:'3.5000',status:'ON_SHELF',defaultFlag:true,sortOrder:0},{skuCode:name.toUpperCase()+'-BOX',specName:'整箱',specValues:{规格:'整箱'},saleUnit:'箱',productType:'STANDARD',marketPrice:'0.0000',status:'ON_SHELF',defaultFlag:false,sortOrder:1}]});
   const options=(await(await api.post('/scm/product/sku/option-list',{data:{keyword:name,limit:10}})).json()).data.options;skuId=options.find((x:any)=>x.specName==='散装').skuId;standardSkuId=options.find((x:any)=>x.specName==='整箱').skuId;weightSkuCode=String(options.find((x:any)=>x.specName==='散装').skuCode);standardSkuCode=String(options.find((x:any)=>x.specName==='整箱').skuCode);
  });
  test.afterAll(async()=>{if(api){await api.get('/login/logout');await api.dispose();}execFileSync('python',['../tools/e2e_fixtures.py', 'account-cleanup', '--wave', 'w4'],{env,stdio:'pipe'});});

  test('1 UI draft entry, diff identity, submit locks price',async({page})=>{
   const consoleErrors:string[]=[];page.on('pageerror',e=>consoleErrors.push(e.message));await browse(page);await expect(page.locator('#order-table')).toBeVisible();await page.getByRole('button',{name:'新建订单',exact:true}).click();await select(page,'customerId',name);
   await page.getByRole('button',{name:'添加商品',exact:true}).click();const skuSelect=page.locator('#order-item-table .ant-select-selector');await skuSelect.click();await skuSelect.locator('input').fill(name);await page.locator('.ant-select-dropdown:visible').getByText('散装',{exact:false}).first().click();await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(page.getByText('草稿已保存',{exact:true})).toBeVisible();
   const result=await post('/scm/order/query',{pageNum:1,pageSize:20,customerId});let o=await detail(result.list[0].orderId);const item=o.items[0];expect(item.draftUnitPrice).toBe('3.5000');
   o=await post('/scm/order/update',{...draft(),orderId:o.orderId,version:o.version,items:[{itemId:item.itemId,version:item.version,skuId,orderedQuantity:'3.0000',manualPriceOverride:false}]});expect(o.items[0].itemId).toBe(item.itemId);
   o=await post('/scm/order/submit',{orderId:o.orderId,version:o.version});expect(o.status).toBe('PENDING');expect(o.items[0].lockedUnitPrice).toBe('3.5000');expect(o.orderedTotalAmount).toBe('10.5000');await page.getByRole('button',{name:/^查\s*询$/}).click();await page.screenshot({path:'../.runtime/w4-order-list.png',fullPage:true});expect(consoleErrors).toEqual([]);
  });
  test('2 nonstandard weight UI, reason and final settlement',async({page})=>{
   let o=await post('/scm/order/create',draft());o=await post('/scm/order/submit',{orderId:o.orderId,version:o.version});expect(o.items[0].actualQuantity).toBeNull();const denied=await(await api.post('/scm/order/confirm',{data:{orderId:o.orderId,version:o.version},headers:{'Idempotency-Key':randomUUID()}})).json();expect(denied.code).toBe(40963);
   // 确认前弹的是 ADM-04 的「确认订单与授信检查」业务弹窗（根类名 .ant-modal、按钮「确定」），
   // 不是 antd 的 Modal.confirm，所以 .ant-modal-confirm 永远等不到；这里连标题一起断言，
   // 保证改的只是选择器，「确认前必须看到授信影响」这条语义反而更严。
   await browse(page);await page.getByPlaceholder('名称或订单号').fill(o.orderNo);await page.getByRole('button',{name:/^查\s*询$/}).click();await page.getByText(o.orderNo,{exact:true}).click();// 订单详情已是独立页面，实重等低频动作收在页头的「订单操作」抽屉里
  await page.getByRole('button',{name:'订单操作',exact:true}).click();await page.getByRole('button',{name:'录入实重',exact:true}).click();await page.locator('#form_item_actualQuantity').fill('1.2500');await page.locator('#form_item_actualQuantityReason').fill('电子秤人工录入');await page.locator('.ant-modal:visible').getByRole('button',{name:'确 定'}).click();await expect(page.locator('.ant-modal:visible')).toHaveCount(0);await page.getByRole('button',{name:'确认订单',exact:true}).click();const confirmModal=page.locator('.ant-modal-content:visible').last();await expect(confirmModal).toContainText('确认订单与授信检查');await expect(confirmModal).toContainText('本次实重结算金额');await confirmModal.getByRole('button',{name:/^确\s*定$/}).click();await expect(page.locator('.ant-drawer-body').getByText('已确认',{exact:true})).toBeVisible();confirmedOrder=await detail(o.orderId);expect(confirmedOrder.settlementTotalAmount).toBe('4.3750');await expect(page.locator('.ant-modal-content:visible')).toHaveCount(0);await page.screenshot({path:'../.runtime/w4-order-detail.png',fullPage:true});
  });
  test('3 cancellation preserves terminal rules and zero-price standard order',async({page})=>{
   const o=await post('/scm/order/create',draft(standardSkuId));expect(o.orderedTotalAmount).toBe('0.0000');await browse(page);await page.getByPlaceholder('名称或订单号').fill(o.orderNo);await page.getByRole('button',{name:/^查\s*询$/}).click();await page.getByText(o.orderNo,{exact:true}).click();await page.getByRole('button',{name:'订单操作',exact:true}).click();await page.getByRole('button',{name:'取消订单',exact:true}).click();await page.locator('#form_item_cancelReason').fill('客户取消');await page.locator('.ant-modal:visible').getByRole('button',{name:'确 定'}).click();await expect(page.locator('.ant-drawer-body').getByText('已取消',{exact:true})).toBeVisible();const denied=await(await api.post('/scm/order/cancel',{data:{orderId:confirmedOrder.orderId,version:confirmedOrder.version,reason:'不可取消'},headers:{'Idempotency-Key':randomUUID()}})).json();expect(denied.code).toBe(40960);
  });
  test('4 supplement reason, original identity and duplicate request',async()=>{
   const data={...draft(),orderSource:'SUPPLEMENT',originalOrderId:confirmedOrder.orderId};let r=await(await api.post('/scm/order/create',{data,headers:{'Idempotency-Key':randomUUID()}})).json();expect(r.code).toBe(40060);const key=randomUUID();const accepted={...data,supplementReason:'漏录补单'};const first=await post('/scm/order/create',accepted,key);const second=await post('/scm/order/create',accepted,key);expect(first.orderId).toBe(second.orderId);r=await(await api.post('/scm/order/create',{data:{...accepted,remark:'changed'},headers:{'Idempotency-Key':key}})).json();expect(r.code).toBe(40966);
  });
  test('5 audit page, error retry, empty results and read-only permission',async({page})=>{
   await browse(page,'/order/order-log-list');await expect(page.locator('#order-log-table')).toBeVisible();const changedLog=page.locator('#order-log-table tr').filter({hasText:'修改'}).first();await changedLog.getByRole('button',{name:'变更前后'}).click();const diff=page.locator('.ant-modal:visible .scm-diff');// 审计快照按「旧值 → 新值」逐字段展示，不再有「变更前 / 变更后」两块表头
  await expect(diff).toContainText('已变更');await expect(diff).toContainText('3.5000→10.5000');await expect(diff).toContainText('显示未变更');await page.locator('.ant-modal-close').click();
   const readToken=await login(name+'_read');const read=await request.newContext({baseURL:apiUrl,extraHTTPHeaders:{Authorization:`Bearer ${readToken}`}});const denied=await(await read.post('/scm/order/create',{data:draft(),headers:{'Idempotency-Key':randomUUID()}})).json();expect(denied.code).toBe(30005);await read.get('/login/logout');await read.dispose();
   await browse(page);await page.route('**/scm/order/query',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:50000,msg:'验收模拟错误',ok:false})}));await page.getByRole('button',{name:/^查\s*询$/}).click();await expect(page.locator('.ant-alert-error')).toContainText('验收模拟错误');await page.unroute('**/scm/order/query');const recovered=page.waitForResponse(r=>r.url().endsWith('/scm/order/query')&&r.request().method()==='POST');await page.locator('.ant-alert').getByRole('button',{name:/^重\s*试$/}).click();await recovered;await expect(page.locator('.ant-alert-error')).toHaveCount(0);await expect(page.locator('#order-table .ant-spin-spinning')).toHaveCount(0);await page.getByPlaceholder('名称或订单号').fill('NO-SUCH-W4-ORDER');await page.getByPlaceholder('名称或订单号').press('Enter');await expect(page.locator('#order-table .ant-empty')).toBeVisible();await page.setViewportSize({width:1920,height:1080});await page.screenshot({path:'../.runtime/w4-order-empty-1920.png',fullPage:true});
  });
  test('6 return approval atomically generates refund and completes in UI',async({page})=>{
   const f={orderId:confirmedOrder.orderId,reason:'质量问题',items:[{orderItemId:confirmedOrder.items[0].itemId,requestedQuantity:'1.0000'}]};const r=await post('/scm/order/return/create',f);const excess=await(await api.post('/scm/order/return/create',{data:f,headers:{'Idempotency-Key':randomUUID()}})).json();expect(excess.code).toBe(40969);
   await browse(page,'/order/order-return-list');await expect(page.locator('#order-return-table')).toBeVisible();const row=page.locator('tr').filter({hasText:r.returnNo});await row.getByRole('button',{name:/^批\s*准$/}).click();await page.locator('.ant-modal:visible').getByRole('button',{name:/^确\s*定$/}).click();await expect(row).toContainText('已批准');
   const refunds=await post('/scm/order/refund/query',{pageNum:1,pageSize:20,orderId:confirmedOrder.orderId});expect(refunds.list).toHaveLength(1);await browse(page,'/order/order-refund-list');const refund=page.locator('tr').filter({hasText:refunds.list[0].refundNo});await refund.getByRole('button',{name:/^登\s*记\s*退\s*款\s*完\s*成$/}).click();await page.locator('.ant-modal:visible input').fill(name+'-refund');await page.locator('.ant-modal:visible').getByRole('button',{name:/^确\s*定$/}).click();await expect(refund).toContainText('已完成');await expect(page.locator('.ant-modal:visible')).toHaveCount(0);await page.screenshot({path:'../.runtime/w4-refund-list.png',fullPage:true});expect((await detail(confirmedOrder.orderId)).status).toBe('CONFIRMED');
  });

  test('7 download template and import standard/nonstandard orders atomically',async({page})=>{
   await browse(page);await page.getByRole('button',{name:'导入订单',exact:true}).click();
   const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:/下载\s*Excel\s*模板/}).click();const download=await downloadPromise;
   expect(download.suggestedFilename()).toBe('销售订单导入模板.xlsx');const valid='../.runtime/w4-order-import-valid.xlsx';await download.saveAs(valid);
   // 客户编码列必须填服务端回读的真实编码；SKU 编码仍是人工码，可继续拼前缀。
   const standardCode=standardSkuCode,weightCode=weightSkuCode;
   const fillScript=`from openpyxl import load_workbook\nimport sys\np=sys.argv[1]; customer=sys.argv[2]; standard=sys.argv[3]; weight=sys.argv[4]; bad=sys.argv[5]=='bad'\nwb=load_workbook(p); ws=wb.active\nfor row in range(2,ws.max_row+1):\n    for col in range(1,13): ws.cell(row,col).value=None\nrows=[['1.0','IMPORT-STANDARD',customer,'W4验收','13800000000','验收地址',None,standard,'2.0000',None,None,'页面联调'],['1.0','IMPORT-WEIGHT',customer,'W4验收','13800000000','验收地址',None,weight,'3.0000',None,None,'页面联调']]\nif bad: rows.append(['1.0','IMPORT-BAD',customer,'W4验收','13800000000','验收地址',None,'SKU-NOT-FOUND','1.0000',None,None,'错误行'])\nfor r,row in enumerate(rows,2):\n    for c,v in enumerate(row,1): ws.cell(r,c).value=v\nwb.save(p)`;
   execFileSync('python',['-c',fillScript,valid,customerCode,standardCode,weightCode,'valid']);
   await page.locator('.ant-modal:visible input[type=file]').setInputFiles(valid);await page.getByRole('button',{name:'开始导入',exact:true}).click();
   await expect(page.locator('.ant-modal:visible .ant-result-title')).toHaveText('订单导入完成');await expect(page.locator('.ant-modal:visible .ant-result-subtitle')).toContainText('已确认 1 张，待称重 1 张');
   const imported=await post('/scm/order/query',{pageNum:1,pageSize:20,customerId,orderSource:'IMPORT'});const importedRows=imported.list.filter((x:any)=>x.remark==='页面联调');expect(importedRows.map((x:any)=>x.status).sort()).toEqual(['CONFIRMED','PENDING']);
   const bad='../.runtime/w4-order-import-bad.xlsx';copyFileSync(valid,bad);
   execFileSync('python',['-c',fillScript,bad,customerCode,standardCode,weightCode,'bad']);await page.getByRole('button',{name:'取 消'}).click();await page.getByRole('button',{name:'导入订单',exact:true}).click();
   const before=(await post('/scm/order/query',{pageNum:1,pageSize:100,customerId,orderSource:'IMPORT'})).total;await page.locator('.ant-modal:visible input[type=file]').setInputFiles(bad);await page.getByRole('button',{name:'开始导入',exact:true}).click();
   await expect(page.getByText(/发现 1 个问题，订单未写入/)).toBeVisible();await expect(page.locator('.ant-modal:visible')).toContainText('商品规格编码不存在');const after=(await post('/scm/order/query',{pageNum:1,pageSize:100,customerId,orderSource:'IMPORT'})).total;expect(after).toBe(before);
   const bytes=readFileSync(valid);const key=randomUUID();const first=await api.post('/scm/order/import',{multipart:{file:{name:'retry.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:bytes}},headers:{'Idempotency-Key':key}});const second=await api.post('/scm/order/import',{multipart:{file:{name:'retry.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:bytes}},headers:{'Idempotency-Key':key}});const one=(await first.json()).data.orders.map((x:any)=>x.orderId),two=(await second.json()).data.orders.map((x:any)=>x.orderId);expect(two).toEqual(one);
  });

  test('8 sample row and manual override rules block the whole batch',async({page})=>{
   const standardCode=standardSkuCode;
   const importFile=async(path:string)=>{await page.locator('.ant-modal:visible input[type=file]').setInputFiles(path);await page.getByRole('button',{name:'开始导入',exact:true}).click();};
   const reopen=async()=>{await page.getByRole('button',{name:'取 消'}).click();await page.getByRole('button',{name:'导入订单',exact:true}).click();};
   const importedTotal=async()=>(await post('/scm/order/query',{pageNum:1,pageSize:100,customerId,orderSource:'IMPORT'})).total;
   await browse(page);await page.getByRole('button',{name:'导入订单',exact:true}).click();

   // A) 原样上传刚下载的模板（示例行未替换）→ 整批失败、零写入，错误指到具体字段
   const raw='../.runtime/w4-order-import-raw.xlsx';
   const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:/下载\s*Excel\s*模板/}).click();await (await downloadPromise).saveAs(raw);
   const baseline=await importedTotal();
   await importFile(raw);
   await expect(page.locator('.ant-modal:visible')).toContainText('发现 2 个问题，订单未写入');
   const sampleErrors=page.locator('.ant-modal:visible .ant-table tbody tr.ant-table-row');
   await expect(sampleErrors).toHaveCount(2);
   // 实际行号（Excel 第 2 行）+ 订单标识 + 字段 + 原因
   await expect(sampleErrors.first().locator('td').first()).toHaveText('2');
   await expect(sampleErrors.first()).toContainText('ORDER-001');
   await expect(sampleErrors.first()).toContainText('客户编码');
   await expect(sampleErrors.first()).toContainText('客户编码不存在');
   await expect(sampleErrors.nth(1)).toContainText('商品规格编码');
   await expect(sampleErrors.nth(1)).toContainText('商品规格编码不存在');
   expect(await importedTotal()).toBe(baseline);

   // B) 人工单价 + 改价原因（账号同时持有 scm:order:import 与 scm:order:price-override）→ 成功且按人工价成交
   const manual='../.runtime/w4-order-import-manual.xlsx';copyFileSync(raw,manual);
   fillRows(manual,[['1.0','IMPORT-MANUAL',customerCode,'W4验收','13800000000','验收地址',null,standardCode,'2.0000','1.2345','客户议价','人工改价']]);
   await reopen();await importFile(manual);
   await expect(page.locator('.ant-modal:visible .ant-result-title')).toHaveText('订单导入完成');
   const manualOrder=(await post('/scm/order/query',{pageNum:1,pageSize:20,customerId,orderSource:'IMPORT'})).list.find((x:any)=>x.remark==='人工改价');
   const manualDetail=await detail(manualOrder.orderId);expect(manualDetail.status).toBe('CONFIRMED');expect(manualDetail.items[0].lockedUnitPrice).toBe('1.2345');expect(manualDetail.orderedTotalAmount).toBe('2.4690');

   // C) 填了人工单价却不填改价原因 → 整批失败、零写入
   const noReason='../.runtime/w4-order-import-noreason.xlsx';copyFileSync(raw,noReason);
   fillRows(noReason,[['1.0','IMPORT-NOREASON',customerCode,'W4验收','13800000000','验收地址',null,standardCode,'2.0000','1.2345',null,'缺原因']]);
   const beforeC=await importedTotal();await reopen();await importFile(noReason);
   await expect(page.locator('.ant-modal:visible')).toContainText('发现 1 个问题，订单未写入');
   await expect(page.locator('.ant-modal:visible .ant-table')).toContainText('填写人工单价时必须填写改价原因');
   expect(await importedTotal()).toBe(beforeC);
  });

  test('9 history reuse prefills a new draft and the recent-price popover shows the locked price',async({page})=>{
   const consoleErrors:string[]=[];page.on('pageerror',e=>consoleErrors.push(e.message));
   await browse(page);
   await page.getByPlaceholder('名称或订单号').fill(confirmedOrder.orderNo);
   await page.getByRole('button',{name:/^查\s*询$/}).click();
   const row=page.locator('#order-table tbody tr').filter({hasText:confirmedOrder.orderNo}).first();
   await row.getByRole('button',{name:/更多/}).click();
   await page.getByRole('menuitem',{name:'复用为新单'}).click();
   const drawer=page.locator('.ant-drawer:visible');
   // 复用生成的是「新建」态：只带一行历史商品，价格按当前价重新解析，绝不沿用历史锁价
   await expect(drawer.getByRole('button',{name:'创建订单'})).toBeVisible();
   // 横向滚动表格的 tbody 还含一行零高度测量行，只数真实数据行
   await expect(page.locator('#order-item-table tbody tr.ant-table-row')).toHaveCount(1);
   // 历史价只读旁证：点开后展示该客户该 SKU 的最近已确认订单价与来源订单
   await page.locator('#order-item-table .recent-btn').click();
   const pop=page.locator('.ant-popover:visible');
   await expect(pop).toContainText('3.5000');
   await expect(pop.locator('.recent-meta')).toContainText(confirmedOrder.orderNo);
   // 价签必须挂在抽屉内部：浮层此前挂在 body 上，抽屉关了它还飘在列表页。
   // 这里刻意在浮层开着的状态下关抽屉，直接复现该缺陷。
   await drawer.locator('.ant-drawer-footer').getByRole('button',{name:/^关\s*闭$/}).click();
   // 关掉的抽屉根节点仍挂在文档里（fixed 定位，:visible 恒真），开合状态只能看 ant-drawer-open。
   await expect(page.locator('.ant-drawer-open')).toHaveCount(0);
   await expect(page.locator('.ant-popover:visible')).toHaveCount(0);
   // 组件实例不销毁，重开抽屉时不能直接冒出上次的价签。仍走复用入口：
   // 上面那次关闭落了本地草稿，走「新建订单」会先弹草稿恢复框，与本用例要验的东西无关。
   await row.getByRole('button',{name:/更多/}).click();
   await page.getByRole('menuitem',{name:'复用为新单'}).click();
   await expect(drawer.getByRole('button',{name:'创建订单'})).toBeVisible();
   await expect(page.locator('.ant-popover:visible')).toHaveCount(0);
   expect(consoleErrors).toEqual([]);
  });

  test('10 unsaved new-order draft is kept locally and offered for recovery on reopen',async({page})=>{
   await browse(page);
   await page.getByRole('button',{name:'新建订单',exact:true}).click();
   await select(page,'customerId',name);
   await page.getByRole('button',{name:'添加商品',exact:true}).click();
   const skuSelect=page.locator('#order-item-table .ant-select-selector');
   await skuSelect.click();await skuSelect.locator('input').fill(name);
   await page.locator('.ant-select-dropdown:visible').getByText('散装',{exact:false}).first().click();
   await page.locator('.ant-drawer:visible').locator('.ant-drawer-footer').getByRole('button',{name:/^关\s*闭$/}).click();
   await expect(page.locator('.ant-drawer-open')).toHaveCount(0);
   // 重新新建：命中本地草稿恢复提示，选「恢复」回填并按当前价格重新解析
   await page.getByRole('button',{name:'新建订单',exact:true}).click();
   const confirmBox=page.locator('.ant-modal-confirm');
   await expect(confirmBox).toContainText('未提交的订单草稿');
   await confirmBox.getByRole('button',{name:/^恢\s*复$/}).click();
   await expect(page.locator('#order-item-table tbody tr.ant-table-row')).toHaveCount(1);
   await expect(page.locator('#order-item-table .price-cell').first()).toContainText('3.5000');
   await page.locator('.ant-drawer:visible').locator('.ant-drawer-footer').getByRole('button',{name:/^关\s*闭$/}).click();
  });

  /** §5.2 离页防丢：两条通道都不经过「关闭」按钮，且各自用不同证据归因，避免把 keep-alive 当成防丢。 */
  test('12 unsaved draft survives a page reload and a route switch',async({page})=>{
   // 存储键由 order-form-model.draftKey 按登录用户拼出；E2E 不 import 生产代码，按前缀取回唯一一条。
   const drafts=()=>page.evaluate(()=>Object.entries(localStorage)
    .filter(([k])=>k.startsWith('xsy-scm:order-draft:'))
    .map(([,v])=>JSON.parse(v)));
   const fillDraft=async()=>{
    await page.getByRole('button',{name:'新建订单',exact:true}).click();
    await select(page,'customerId',name);
    await page.getByRole('button',{name:'添加商品',exact:true}).click();
    const skuSelect=page.locator('#order-item-table .ant-select-selector');
    await skuSelect.click();await skuSelect.locator('input').fill(name);
    await page.locator('.ant-select-dropdown:visible').getByText('散装',{exact:false}).first().click();
    await expect(page.locator('#order-item-table tbody tr.ant-table-row')).toHaveCount(1);
   };
   const recover=async()=>{
    await page.getByRole('button',{name:'新建订单',exact:true}).click();
    const confirmBox=page.locator('.ant-modal-confirm');
    await expect(confirmBox).toContainText('未提交的订单草稿');
    // 刷新后这是本次会话第一次挂载抽屉：确认框必须浮在抽屉之上，否则用户只看到空白抽屉且点不到「恢复」。
    await confirmBox.getByRole('button',{name:/^恢\s*复$/}).click();
    // 恢复后按当前价重新解析，价格与明细行都在
    await expect(page.locator('#order-item-table tbody tr.ant-table-row')).toHaveCount(1);
    await expect(page.locator('#order-item-table .price-cell').first()).toContainText('3.5000');
   };
   await browse(page);
   await fillDraft();
   // 通道一（路由切换）：抽屉开着直接切走，全程没点「关闭」，本地草稿只可能由 onBeforeRouteLeave 写入
   await browse(page,'/purchase/purchase-demand-list');
   const left=await drafts();
   expect(left,'切走路由后本地没有草稿 = onBeforeRouteLeave 未落盘').toHaveLength(1);
   expect(left[0].items).toHaveLength(1);
   // 通道二（刷新）：改一个只存在于这一版的可辨识字段再刷新，草稿里出现它才说明是 beforeunload 写的
   await browse(page);
   const reloadMark='reload_'+Date.now().toString(36);
   await page.locator('#form_item_remark').fill(reloadMark);
   await page.reload();
   await expect(page.getByRole('button',{name:'新建订单',exact:true})).toBeVisible();
   const reloaded=await drafts();
   expect(reloaded).toHaveLength(1);
   expect(reloaded[0].remark,'刷新后草稿仍是切换路由那一版 = beforeunload 未落盘').toBe(reloadMark);
   await recover();
   await expect(page.locator('#form_item_remark')).toHaveValue(reloadMark);
   await page.locator('.ant-drawer:visible').locator('.ant-drawer-footer').getByRole('button',{name:/^关\s*闭$/}).click();
  });

  /** 客户或 SKU 改变后组件会关闭旧浮层；等它完全关闭后再点，检查新键对应的历史价。 */
  async function openRecentPopover(page:Page){
   const pop=page.locator('.ant-popover:visible');
   await expect(pop).toHaveCount(0);
   await page.locator('#order-item-table .recent-btn').first().click();
   await expect(pop).toHaveCount(1);
  }
  async function pickSku(page:Page,spec:string){
   const box=page.locator('#order-item-table .ant-select-selector').first();
   await box.click();await box.locator('input').fill(name);
   await page.locator('.ant-select-dropdown:visible').getByText(spec,{exact:false}).first().click();
  }
  /** 自建合作中客户；名称带独立后缀，保证前端按整名搜索时只命中它自己。 */
  async function cooperatingCustomer(suffix:string){
   const types=(await(await api.post('/scm/customer/type/option/list',{data:{}})).json()).data;
   const customerName=name+'_'+suffix;
   // 客户编码由服务端生成：创建载荷不再提交编码（列表/选择器都按名称定位）。
   const id=await post('/scm/customer/add',{name:customerName,customerTypeId:types[0].typeId,settleMode:'INDEPENDENT',contactName:'W4验收',contactPhone:'13800000000',address:'验收地址'});
   const r=await(await api.get('/scm/customer/detail/'+id)).json();
   await post('/scm/customer/updateStatus',{customerId:id,version:r.data.version,status:'COOPERATING'});
   return {customerName,customerId:id};
  }
  /** 落一张该客户该 SKU 的已确认单：非标品必须先录入实重才允许确认，改价行必须带原因。 */
  async function confirmedOrderOf(options:{customer:string,sku:string,price?:string,nonStandard?:boolean}){
   const {customer,sku,price,nonStandard}=options;
   const item:{skuId:string,orderedQuantity:string,manualPriceOverride:boolean,unitPrice?:string,overrideReason?:string}=
    {skuId:sku,orderedQuantity:'1.0000',manualPriceOverride:price!==undefined};
   if(price!==undefined){item.unitPrice=price;item.overrideReason='验收议价';}
   let o=await post('/scm/order/create',{...draft(sku),customerId:customer,items:[item]});
   o=await post('/scm/order/submit',{orderId:o.orderId,version:o.version});
   if(nonStandard)o=await post('/scm/order/item/actual-quantity',{orderId:o.orderId,itemId:o.items[0].itemId,version:o.items[0].version,actualQuantity:'1.0000',reason:'电子秤人工录入'});
   if(o.status!=='CONFIRMED')o=await post('/scm/order/confirm',{orderId:o.orderId,version:o.version});
   expect(o.status).toBe('CONFIRMED');
   return o;
  }
  test('11 recent-price panel is keyed by customer+SKU: switching either never bleeds the other price',async({page})=>{
   // §12.3 Wave 3「历史价切 SKU 不串 / 切客户不串」。缓存键曾含明细行序号，
   // 换商品或换客户后重开浮层会直接吐出上一档的价，且不再发请求。
   // 本用例自建客户与订单，不借用其他用例留下的数据，因此单独跑也成立。
   const consoleErrors:string[]=[];page.on('pageerror',e=>consoleErrors.push(e.message));
   const customerA=await cooperatingCustomer('r3a');
   const customerB=await cooperatingCustomer('r3b');
   const boxA=await confirmedOrderOf({customer:customerA.customerId,sku:standardSkuId,price:'1.3579'});
   const kgA=await confirmedOrderOf({customer:customerA.customerId,sku:skuId,nonStandard:true});
   const boxB=await confirmedOrderOf({customer:customerB.customerId,sku:standardSkuId,price:'9.8700'});

   await browse(page);
   await page.getByRole('button',{name:'新建订单',exact:true}).click();
   await select(page,'customerId',customerA.customerName);
   await page.getByRole('button',{name:'添加商品',exact:true}).click();
   const pop=page.locator('.ant-popover:visible');
   await pickSku(page,'整箱');
   await openRecentPopover(page);
   // 客户 A + 整箱：只有自建的那一档，不能出现同客户散装档的价
   await expect(pop).toContainText('1.3579');
   await expect(pop.locator('.recent-meta')).toContainText(boxA.orderNo);
   await expect(pop).not.toContainText('3.5000');
   await expect(pop).not.toContainText(kgA.orderNo);
   // 同一行换商品 → 必须按新 SKU 重新取数，不能沿用行序号缓存
   await pickSku(page,'散装');
   await openRecentPopover(page);
   await expect(pop).toContainText('3.5000');
   await expect(pop.locator('.recent-meta')).toContainText(kgA.orderNo);
   await expect(pop).not.toContainText('1.3579');
   // 换回整箱 → 同一 (客户,SKU) 组合仍然命中，且不是「还没查」的空态
   await pickSku(page,'整箱');
   await openRecentPopover(page);
   await expect(pop).toContainText('1.3579');
   // 换客户、SKU 不动 → 只能看到该客户自己的历史成交价
   await select(page,'customerId',customerB.customerName);
   await openRecentPopover(page);
   await expect(pop).toContainText('9.8700');
   await expect(pop.locator('.recent-meta')).toContainText(boxB.orderNo);
   await expect(pop).not.toContainText('1.3579');
   await expect(pop).not.toContainText('3.5000');
   await expect(pop).not.toContainText(boxA.orderNo);
   await expect(pop).not.toContainText(kgA.orderNo);
   await page.screenshot({path:'../.runtime/w4-recent-price.png',fullPage:true});
   expect(consoleErrors).toEqual([]);
  });

});

// ---------------------------------------------------------------- 来源：e2e/scm-wave8-log-context.spec.ts
test.describe('scm-wave8-log-context', () => {
  const test = pwTest;
  /*
    Wave 8 操作日志业务上下文 + 列表查询条件本地记忆 E2E。

    覆盖 §12.3 Wave 8 的四件事，并且**全部用真实非管理员登录**（只读角色 / 零权限角色），
    避免「只有 SUPER_ADMIN 走过链路」这种验收口径：
      1. 操作日志页按业务类型与 ID 下钻（查询载荷按下钻而不是扫全表）——
         原先由「商品详情的操作日志入口」触发，该入口已按产品要求移除，
         现在直接打开深链验证同一条口径；
      2. 日志页刷新与「重置」都不丢业务上下文（重置只清普通筛选），返回行确实落在该对象上；
      3. 零权限账号按业务对象查询日志被服务端拒（30005）——前端隐藏按钮不是权限；
      4. 客户列表查询条件按登录人分别记忆：查询落键、刷新恢复、重置清除，且换人不共用同一键。

    来源：结构与登录链路照抄 `e2e/scm-customer.spec.ts`（复用同一账号脚本与 CJK 按钮处理）。
    STRPOS 精确匹配口径、脏行退化、写库前脱敏已由后端 PgIT / 前端契约测试覆盖，本 spec 只补真实页面接线。
  */

  const apiUrl = process.env.W8_E2E_API_BASE || 'http://127.0.0.1:18080';
  const account = 'w2_e2e_' + Date.now().toString(36);
  const password = 'W8@' + randomBytes(6).toString('hex');
  const env = {...process.env, W2_E2E_NAME: account, W2_E2E_PASSWORD: password};
  const ACCOUNT_SCRIPT = '../tools/e2e_fixtures.py';

  // antd 在两个 CJK 字符间插入视觉空格（"查询" → "查 询"），按字符间可选空白精确匹配。
  const escapeRe = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const buttonName = (text: string) => new RegExp('^' + [...text].map(escapeRe).join('\\s*') + '$');
  const button = (root: Page | Locator, text: string) => root.getByRole('button', {name: buttonName(text)});

  /** 真实登录：Sa-Token + SM4 传输加密 + 验证码，不能伪造 token。 */
  async function login(loginName: string): Promise<string> {
    const client = await request.newContext({baseURL: apiUrl});
    const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
    const key = /const SM4_KEY = '([^']+)'/.exec(readFileSync('src/lib/encrypt.ts', 'utf8'))?.[1];
    if (!key) throw new Error('SmartAdmin transport key not found');
    const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
    const result = await (await client.post('/login', {
      data: {loginName, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1},
    })).json();
    expect(result.code, `登录 ${loginName} 应成功`).toBe(0);
    await client.dispose();
    return result.data.token as string;
  }

  function authenticate(page: Page, token: string) {
    return page.addInitScript((t) => localStorage.setItem('smart_admin_user_token', t), token);
  }

  /** 日志分页请求的载荷：上下文是否真的下钻，只能看发出去的字节。 */
  function logQueryRequest(page: Page) {
    return page.waitForRequest((r) => r.url().includes('/support/operateLog/page/query') && r.method() === 'POST');
  }

  const queryPayload = async (page: Page) => JSON.parse((await logQueryRequest(page)).postData() ?? '{}');

  let adminApi: APIRequestContext;
  let adminToken: string;
  let readToken: string;
  /** 本用例临时建的客户（updateStatus 会往 param 里写入 customerId，才是可下钻的日志）。 */
  let pilotCustomer: {customerId: number; version: number} | undefined;

  async function createPilotCustomer() {
    const typeList = await (await adminApi.post('/scm/customer/type/query', {data: {pageSize: 1, pageNum: 1}})).json();
    const employee = await (await adminApi.post('/scm/employee/query', {data: {pageNum: 1, pageSize: 1}})).json();
    // 客户编码由服务端生成（CUS + 6 位序号），创建载荷不再提交编码。
    const form = {
      name: account.toUpperCase() + '日志客户',
      customerTypeId: typeList.code === 0 ? typeList.data.list[0].typeId : 1,
      settleMode: 'INDEPENDENT',
      sellerId: employee.code === 0 ? employee.data.list[0].employeeId : 1,
    };
    const added = await (await adminApi.post('/scm/customer/add', {data: form})).json();
    expect(added.code, `建临时客户失败：${added.msg}`).toBe(0);
    const customerId = added.data as number;
    const status = await (await adminApi.post('/scm/customer/updateStatus', {
      data: {customerId, version: 0, status: 'COOPERATING'},
    })).json();
    expect(status.code, `改临时客户状态失败：${status.msg}`).toBe(0);
    const detail = await (await adminApi.get(`/scm/customer/detail/${customerId}`)).json();
    pilotCustomer = {customerId, version: detail.data.version};
  }

  test.beforeAll(async () => {
    execFileSync('python', [ACCOUNT_SCRIPT, 'account-setup', '--wave', 'w2'], {env, stdio: 'pipe'});
    adminToken = await login(account);
    adminApi = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${adminToken}`}});
    readToken = await login(account + '_read');
    await createPilotCustomer();
  });

  test.afterAll(async () => {
    if (adminApi && pilotCustomer) {
      await adminApi.post('/scm/customer/delete', {data: {customerId: pilotCustomer.customerId, version: pilotCustomer.version}});
    }
    await adminApi?.dispose();
    execFileSync('python', [ACCOUNT_SCRIPT, 'account-cleanup', '--wave', 'w2'], {env, stdio: 'pipe'});
  });

  test('操作日志页按 PRODUCT 业务上下文下钻（原商品详情入口已移除）', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const query = await (await adminApi.post('/scm/product/query', {data: {pageNum: 1, pageSize: 1}})).json();
    const spuId = query.code === 0 ? query.data?.list?.[0]?.spuId : undefined;
    // 缺夹具必须是**失败**而不是 skip：绿色跑过 + 场景没执行 = 把「文件存在」当成「已验收」
    expect(spuId, '当前库没有任何商品，Wave 8 下钻场景无法执行').toBeTruthy();

    await authenticate(page, readToken);
    // 商品详情的「操作日志」入口已按产品要求移除，因此这里直接验证深链本身：
    // 只要日志页仍按 PRODUCT + businessId 精确下钻，入口挂在哪个页面都不改变这条口径。
    await page.goto(`/#/support/operate-log/operate-log-list?businessType=PRODUCT&businessId=${spuId}`);
    const payload = await queryPayload(page);
    expect(payload.businessType).toBe('PRODUCT');
    expect(payload.businessId, '业务 ID 必须以数值下钻，不能退化成文本筛选').toBe(Number(spuId));
    expect(page.url()).toContain('businessType=PRODUCT');
    expect(page.url()).toContain(`businessId=${spuId}`);
    // 下钻视图必须自带口径说明：它不是该对象的全部历史。
    await expect(page.getByText('正在按当前业务对象查看操作记录')).toBeVisible();
    expect(errors).toEqual([]);
  });

  test('日志页刷新与重置都不丢业务上下文，返回行落在该客户', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const url = `/#/support/operate-log/operate-log-list?businessType=CUSTOMER&businessId=${pilotCustomer!.customerId}`;

    await authenticate(page, readToken);
    await page.goto(url);
    const first = await queryPayload(page);
    expect(first.businessType).toBe('CUSTOMER');
    expect(first.businessId).toBe(pilotCustomer!.customerId);

    // 刷新：路由查询仍在，必须重新套上上下文而不是退回全表。
    const reload = queryPayload(page);
    await page.reload();
    expect((await reload).businessId).toBe(pilotCustomer!.customerId);

    // 重置：清掉普通筛选，业务上下文按当前路由重算后仍保留。
    // 本页按钮带图标（图标自带 aria-label），可及名不是纯文案，故用包含式匹配。
    await page.getByPlaceholder('模块/操作内容').fill('噪声关键字');
    const afterReset = queryPayload(page);
    await page.getByRole('button', {name: /重\s*置/}).click();
    const third = await afterReset;
    expect(third.businessType).toBe('CUSTOMER');
    expect(third.businessId).toBe(pilotCustomer!.customerId);
    expect(third.keywords, '重置必须清掉普通筛选').toBeFalsy();

    // 精确匹配的真实链路结果：临时客户 updateStatus 必然留下一条含其 ID 的日志，且返回行都含该 ID。
    const rows = await (await adminApi.post('/support/operateLog/page/query', {
      data: {pageNum: 1, pageSize: 20, businessType: 'CUSTOMER', businessId: pilotCustomer!.customerId},
    })).json();
    expect(rows.code).toBe(0);
    expect(rows.data.list.length, '刚改过状态的客户应能查到自己的操作日志').toBeGreaterThan(0);
    for (const row of rows.data.list) {
      expect(JSON.stringify(row), '返回行必须含该业务 ID').toContain(String(pilotCustomer!.customerId));
    }
    expect(errors).toEqual([]);
  });

  test('零权限账号按业务对象查询日志被服务端拒绝，不泄露任何行', async () => {
    const noneToken = await login(account + '_none');
    const client = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${noneToken}`}});
    const body = await (await client.post('/support/operateLog/page/query', {
      data: {pageNum: 1, pageSize: 10, businessType: 'PRODUCT', businessId: 1},
    })).json();
    expect(body.code, '无 support:operateLog:query 必须被服务端拒绝').toBe(30005);
    expect(body.data?.list ?? [], '被拒响应不得带出任何日志行').toEqual([]);
    await client.dispose();
  });

  test('客户列表查询条件按登录人分别记忆：查询落键、刷新恢复、重置清除', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const keyword = '记忆' + Date.now().toString(36);
    const adminKeyword = '他人' + Date.now().toString(36);
    const placeholder = '客户名称 / 联系人 / 电话';

    // 不拼 employeeId（登录响应不带该字段），直接枚举本页的真实偏好键：
    // 键里那段数字就是隔离用的登录用户，换人必然落到另一枚键上。
    const listed = (root: Page) => root.evaluate(() =>
      Object.keys(localStorage)
        .filter((k) => /^xsy-scm:query-filter:\d+:scm:customer:list$/.test(k))
        .map((k) => ({key: k, value: String(localStorage.getItem(k))})));

    await authenticate(page, readToken);
    await page.goto('/#/customer/customer-list');
    const input = page.getByPlaceholder(placeholder);
    await expect(input).toBeVisible();
    // 首次进入不应带着别人（或上一次）的条件。
    await expect(input).toHaveValue('');
    await input.fill(keyword);
    const queryRequest = page.waitForRequest((r) => r.url().includes('/scm/customer/query'), {timeout: 5000});
    await button(page, '查询').click();
    // 「查询」必须真的发起一次带关键字的下钻请求：曾经 a-form 无 model 时 @finish 永不触发，
    // 按钮点了什么也不做，偏好键自然也不会有。
    expect(JSON.parse((await queryRequest).postData() ?? '{}').keyword).toBe(keyword);

    const readEntries = await listed(page);
    expect(readEntries.length, '查询后应恰落一枚本页偏好键').toBe(1);
    expect(readEntries[0].value, '偏好键里必须存了本次筛选').toContain(keyword);

    // 刷新恢复：条件回到输入框。
    await page.reload();
    await expect(page.getByPlaceholder(placeholder)).toHaveValue(keyword);

    // 换登录人：管理员进入同一页面读不到只读账号的偏好；他查询后落的是另一枚键。
    const adminPage = await page.context().newPage();
    await authenticate(adminPage, adminToken);
    await adminPage.goto('/#/customer/customer-list');
    await expect(adminPage.getByPlaceholder(placeholder)).toHaveValue('');
    await adminPage.getByPlaceholder(placeholder).fill(adminKeyword);
    await button(adminPage, '查询').click();
    const adminEntries = await listed(adminPage);
    expect(adminEntries.length, '两个登录人各一枚键').toBe(2);
    const adminOwn = adminEntries.find((e) => e.value.includes(adminKeyword));
    expect(adminOwn, '管理员必须落到自己的键').toBeTruthy();
    expect(adminOwn!.key).not.toBe(readEntries[0].key);
    expect(adminOwn!.value, '他人的筛选不得写进同一键').not.toContain(keyword);
    await adminPage.close();

    // 重置即清除本人偏好，且不能顺手删掉别人的键。
    await button(page, '重置').click();
    await expect(page.getByPlaceholder(placeholder)).toHaveValue('');
    const afterReset = await listed(page);
    expect(afterReset.some((e) => e.key === readEntries[0].key), '重置必须删除本人偏好键').toBe(false);
    expect(afterReset.map((e) => e.key), '重置不得越界删除他人键').toContain(adminOwn!.key);
    expect(errors).toEqual([]);
  });

});
