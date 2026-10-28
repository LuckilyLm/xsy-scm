/*
 * 2026-10-28 现场确认后的 P0 第一批：浏览器侧验收。
 *
 * 只覆盖「后台页面上能看见、点得到」的行为；取价、负库存、核销的账务结论已由 Pg IT 覆盖，
 * 这里不重复断言后端规则，避免同一口径写两遍。
 */
import {test, expect} from './scm-test-base';
import {randomUUID} from 'node:crypto';
import type {APIRequestContext, Page} from '@playwright/test';
import {accessibleName, apiClient, authenticate, login, provisionTempAccounts, type TempAccounts} from './scm-e2e-account';
import {createLocatedCustomer, createSku} from './scm-delivery-fixtures';
import {
  createReceipt,
  createSignedOrder,
  financeDateRange,
  openFinanceHarness,
  type FinanceHarness,
  type SignedOrderFacts,
} from './scm-finance-fixtures';

type Row = Record<string, any>;

/** 负库存验收仓：仓库没有删除接口，固定名字复用一只，避免每跑一次就往开发库塞一个新仓。 */
const NEGATIVE_WAREHOUSE_NAME = 'E2E负库存仓';

async function call<T = Row>(client: APIRequestContext, method: 'get' | 'post', path: string, data?: unknown): Promise<T> {
  const response = method === 'get' ? await client.get(path) : await client[method](path, {data, headers: {'Idempotency-Key': randomUUID()}});
  const body = await response.json();
  if (body.code !== 0) throw new Error(`${method.toUpperCase()} ${path} 失败：${body.code} ${body.msg}`);
  return body.data as T;
}

test.describe('scm-p0-batch', () => {
  test.describe.configure({mode: 'serial'});

  let accounts: TempAccounts;
  let client: APIRequestContext;
  let runTag: string;
  let token: string;
  let customerId: string;
  let skuId: string;

  test.beforeAll(async () => {
    accounts = provisionTempAccounts('w1');
    // 同一个账号只登一次：重复登录会把上一枚 token 顶下线，页面与 API 必须共用这一枚
    token = await login(accounts, accounts.admin);
    client = await apiClient(token);
    runTag = `P0${Date.now().toString(36).toUpperCase()}`;
    customerId = await createLocatedCustomer(client, runTag, 'A', '浙江省杭州市西湖区文一西路 100 号');
    skuId = await createSku(client, runTag, 'A', 'STANDARD');
  });

  test.afterAll(async () => {
    await client?.dispose();
    accounts?.cleanup();
  });

  test('客户分组价：页面新增后列表可见，取价按客户分组命中', async ({page}) => {
    const groupName = `${runTag}分组`;
    await call(client, 'post', '/scm/customer/group/add', {
      groupCode: `${runTag}G`,
      groupName,
      status: 'ENABLED',
      customerIds: [Number(customerId)],
    });

    await authenticate(page, token);
    await page.goto('/#/pricing/customer-group-price-list');
    await expect(page.locator('.ant-table-wrapper')).toBeVisible();
    await page.getByRole('button', {name: accessibleName('新增客户分组价')}).click();

    const drawer = page.locator('.ant-drawer-open').filter({hasText: '新增客户分组价'}).first();
    await expect(drawer).toBeVisible();
    await expect(drawer.locator('.ant-form-item').filter({hasText: '客户分组'})).toBeVisible();

    // 分组下拉按名称排序，新建分组排在末尾，直接在列表里点会落在可视区外；输入名称过滤
    await drawer.locator('.ant-form-item').filter({hasText: '客户分组'}).locator('.ant-select-selector').click();
    await page.keyboard.type(groupName);
    await page.locator('.ant-select-dropdown:visible').last().getByText(groupName, {exact: true}).click();

    // 规格下拉是远程搜索：不输入时只给前 50 条，刚建的 SKU 不会出现在里面
    await drawer.locator('.ant-form-item').filter({hasText: '商品规格'}).locator('.ant-select-selector').click();
    await page.keyboard.type(`${runTag}商品A`);
    await page
      .locator('.ant-select-dropdown:visible')
      .last()
      .getByText(`${runTag}商品A / 散装A`, {exact: true})
      .click();

    await drawer.locator('.ant-form-item').filter({hasText: '分组价'}).locator('input').first().fill('2.0000');
    // 生效时间是必填；结束留空表示长期有效。带 show-time 的区间必须从面板选，直接键入日期不提交。
    await drawer.locator('.ant-picker input').first().click();
    const todayDay = new Intl.DateTimeFormat('en-CA', {timeZone: 'Asia/Shanghai', day: '2-digit'}).format(new Date());
    await page
      .locator('.ant-picker-dropdown:visible .ant-picker-cell-in-view')
      .filter({hasText: new RegExp(`^${Number(todayDay)}$`)})
      .first()
      .click();
    await page.locator('.ant-picker-dropdown:visible .ant-picker-ok button').click();
    await drawer.getByRole('button', {name: accessibleName('保存')}).click();
    await expect(drawer).toHaveCount(0);

    const row = page.locator('.ant-table-tbody tr').filter({hasText: groupName}).filter({hasText: '¥ 2.00'}).first();
    await expect(row).toBeVisible();

    const resolved = await call<Row>(client, 'post', '/scm/pricing/resolve', {
      customerId: Number(customerId),
      skuIds: [Number(skuId)],
    });
    expect(resolved.items[0].priceSource).toBe('CUSTOMER_GROUP');
    expect(resolved.items[0].unitPrice).toBe('2.0000');
  });

  test('整单粘贴：匹配行可追加，未匹配行留在预览里报错', async ({page}) => {
    await authenticate(page, token);
    await page.goto('/#/order/order-list');
    await page.getByRole('button', {name: accessibleName('新建订单')}).click();

    const drawer = page.locator('.ant-drawer-open').filter({hasText: '新建销售订单'}).first();
    await expect(drawer).toBeVisible();
    // 客户下拉只回前面若干条，刚建的客户要靠输入过滤
    await drawer.locator('.ant-form-item').filter({hasText: '客户'}).first().locator('.ant-select-selector').click();
    await page.keyboard.type(`${runTag}客户A`);
    await page.locator('.ant-select-dropdown:visible').last().getByText(`${runTag}客户A`, {exact: true}).click();

    await drawer.getByRole('button', {name: accessibleName('整单粘贴')}).click();
    const modal = page.locator('.ant-modal:visible').filter({hasText: '整单粘贴'}).last();
    await expect(modal).toBeVisible();
    // 用表头别名固定列序；无表头时第三列会被当成规格，商品与规格匹配不上
    await modal
      .locator('textarea')
      .fill(['商品名称\t规格\t单位\t数量\t销售单价', `${runTag}商品A\t散装A\tkg\t3`, `${runTag}不存在商品\t散装\tkg\t1`].join('\n'));
    await modal.getByRole('button', {name: accessibleName('解析预览')}).click();

    await expect(modal).toContainText('可追加 1 行');
    await expect(modal).toContainText('1 行需修正');
    await expect(modal.locator('.paste-error')).toHaveCount(1);

    await modal.getByRole('button', {name: accessibleName('追加到订单')}).click();
    await expect(modal).toHaveCount(0);
    // 表体里还有一行用于列宽测量的测量行（.ant-table-measure-row），只数真正的数据行
    const itemRows = drawer.locator('.ant-table-tbody tr.ant-table-row');
    await expect(itemRows).toHaveCount(1);
    await expect(itemRows.first()).toContainText('散装A');
    // 客户已选中时不能再挂「请选择客户」：校验读的是最新表单值，不是上一轮渲染的副本
    await expect(drawer.locator('.ant-form-item-explain-error')).toHaveCount(0);
  });
});

/** 下拉/表单内的选项：先点开选择器，再在浮层里按文本点，选项按名称排序时也能命中所建数据。 */
async function selectOption(page: Page, root: ReturnType<Page['locator']>, itemLabel: string, optionText: string) {
  await root.locator('.ant-form-item').filter({hasText: itemLabel}).first().locator('.ant-select-selector').click();
  await page.locator('.ant-select-dropdown:visible').last().getByText(optionText, {exact: true}).click();
}

/** 财务的资金单/应收选择弹窗：按单号找到行，点行内「选择」。 */
async function pickRecord(page: Page, title: string, documentNo: string) {
  const picker = page.locator('.ant-modal:visible').filter({hasText: title}).last();
  await expect(picker).toBeVisible();
  const row = picker.locator('tr').filter({hasText: documentNo}).first();
  await expect(row).toBeVisible();
  await row.getByRole('button', {name: accessibleName('选择')}).click();
  await expect(picker).toHaveCount(0);
}

test.describe('scm-p0-customer-sheets', () => {
  test.describe.configure({mode: 'serial'});

  let harness: FinanceHarness;
  let order: SignedOrderFacts;

  test.beforeAll(async () => {
    harness = await openFinanceHarness();
    // 批量打印按 Asia/Shanghai 的配送日期取已确认订单：给订单一个明天的期望配送时间，
    // 正好落在弹窗默认的「明天」。
    const tomorrow = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date(Date.now() + 86_400_000));
    order = await createSignedOrder(harness, 'SHEET', [{quantity: '5.0000'}], undefined, undefined, {
      expectDeliveryTime: `${tomorrow} 09:00:00`,
    });
  });

  test.afterAll(async () => {
    if (harness) await harness.close();
  });

  test('批量打印：客户分拣单不印价格，客户送货单带实数量与金额', async ({page}) => {
    await authenticate(page, harness.adminToken);
    await page.goto('/#/order/order-list');
    await page.getByRole('button', {name: accessibleName('打印客户分拣单 / 送货单')}).click();

    const modal = page.locator('.ant-modal:visible').filter({hasText: '批量打印客户单据'}).last();
    await expect(modal).toBeVisible();
    const preview = modal.locator('.preview');
    await expect(preview).toContainText('客户分拣单');
    await expect(preview).toContainText(order.customerName);
    await expect(preview).toContainText('散装SHEETA');
    // 分拣单是给现场手写实数量的纸：出现价格列就等于把成本价带到分拣台上
    await expect(preview).not.toContainText('销售金额');
    await expect(preview).not.toContainText('销售单价');

    await modal.locator('.ant-radio-wrapper').filter({hasText: '客户送货单'}).click();
    await expect(preview).toContainText('客户送货单');
    await expect(preview).toContainText('实数量');
    await expect(preview).toContainText('销售金额');
    // 5.0000 × 3.5000：送货单要能直接给客户签字核对
    await expect(preview).toContainText('17.5000');

    await modal.getByRole('button', {name: accessibleName('打印')}).click();
    // 打印记录落库才算真的「打过」：无头浏览器没有打印对话框可断言
    await expect
      .poll(
        async () => {
          const records = await call<Row>(harness.admin, 'post', '/scm/print/record/query', {
            documentType: 'CUSTOMER_DELIVERY_NOTE',
            businessNo: order.orderNo,
            pageNum: 1,
            pageSize: 50,
          });
          return (records.list as Row[]).some((record) => String(record.businessNo) === order.orderNo);
        },
        {timeout: 30_000, message: '客户送货单应生成打印记录'}
      )
      .toBe(true);
  });
});

test.describe('scm-p0-negative-stock', () => {
  test.describe.configure({mode: 'serial'});

  let harness: FinanceHarness;
  let warehouseId: number;
  let skuName: string;

  test.beforeAll(async () => {
    harness = await openFinanceHarness();
    // 允许负库存的仓按固定名复用：仓库没有删除接口，每次跑都新建会让开发库越攒越多。
    // 用完即停用（见 afterAll）：其它用例按「第一个启用仓」选仓，多一个启用仓会把它们带偏。
    const existing = await call<Row>(harness.admin, 'post', '/scm/warehouse/query', {
      name: NEGATIVE_WAREHOUSE_NAME,
      pageNum: 1,
      pageSize: 10,
    });
    const reused = (existing.list as Row[]).find((row) => row.name === NEGATIVE_WAREHOUSE_NAME);
    if (reused) {
      warehouseId = Number(reused.id);
      if (reused.status !== 'ENABLED') {
        await call(harness.admin, 'post', '/scm/warehouse/enable', {id: warehouseId, version: Number(reused.version)});
      }
    } else {
      warehouseId = Number(
        await call(harness.admin, 'post', '/scm/warehouse/create', {
          name: NEGATIVE_WAREHOUSE_NAME,
          address: 'E2E 负库存验收路 1 号',
          provinceCode: 330000,
          provinceName: '浙江省',
          cityCode: 330100,
          cityName: '杭州市',
          districtCode: 330106,
          districtName: '西湖区',
          longitude: '113.94000000',
          latitude: '22.54000000',
          geomCrs: 'GCJ02',
          allowNegativeStock: true,
        })
      );
    }
    // 故意不备货：允许负库存的仓里直接发车出库，余额被打成负数，报表才有东西可看
    await createSignedOrder({...harness, warehouseId}, 'NEG', [{quantity: '3.0000'}], undefined, undefined, {skipStock: true});
    skuName = `${harness.runTag}商品NEGA`;
  });

  test.afterAll(async () => {
    if (!harness) return;
    try {
      // 负余额还在，允许负库存关不掉；停用同样能让它退出选仓下拉与「第一个启用仓」
      const response = await harness.admin.get(`/scm/warehouse/detail/${warehouseId}`);
      const version = Number((await response.json())?.data?.version);
      if (Number.isFinite(version)) {
        await harness.admin.post('/scm/warehouse/disable', {data: {id: warehouseId, version}});
      }
    } catch {
      // 停用只是清理，不改变验收结论
    }
    await harness.close();
  });

  test('负库存损耗：按仓库列出负数量，并提示零成本行让金额偏低', async ({page}) => {
    const report = await call<Row>(harness.admin, 'post', '/scm/report/inventory/negative-stock/query', {
      warehouseId,
      pageNum: 1,
      pageSize: 20,
    });
    const row = (report.list as Row[]).find(
      (item) => item.productName === skuName && Number(item.warehouseId) === warehouseId
    );
    expect(row?.quantity, '负库存仓的余额应为负数').toBe('-3.0000');
    expect(row?.unit).toBe('kg');

    await authenticate(page, harness.adminToken);
    await page.goto('/#/report/report-inventory-list');
    await page.getByRole('tab', {name: '负库存损耗'}).click();
    const table = page.locator('#scm-report-inventory-negative-stock-table');
    await expect(table).toBeVisible();
    const tableRow = table.locator('.ant-table-row').filter({hasText: skuName}).first();
    await expect(tableRow).toContainText('-3.0000');
    await expect(tableRow).toContainText('kg');
    // 从未入库就卖出的规格余额均价是 0：页面必须自己说明金额偏低，不能让人把 0 当损耗
    await expect(page.locator('.ant-alert-warning')).toContainText('余额均价为 0');
  });
});

test.describe('scm-p0-finance-fifo-waiver', () => {
  test.describe.configure({mode: 'serial'});

  let harness: FinanceHarness;
  let first: SignedOrderFacts;
  let second: SignedOrderFacts;

  test.beforeAll(async () => {
    harness = await openFinanceHarness();
    first = await createSignedOrder(harness, 'FIFOA', [{quantity: '10.0000'}]);
    second = await createSignedOrder(harness, 'FIFOB', [{quantity: '10.0000'}], first.customerId);
    expect(first.receivable.openAmount).toBe('35.0000');
    expect(second.receivable.openAmount).toBe('35.0000');
  });

  test.afterAll(async () => {
    if (harness) await harness.close();
  });

  test('自动 FIFO：先冲最早的应收，剩余金额留在收款单', async ({page}) => {
    const receipt = await createReceipt(harness, first.customerId, '40.0000', 'FIFO');

    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/write-offs');
    await expect(page.locator('#scm-finance-write-off-table')).toBeVisible();
    await page.getByRole('button', {name: accessibleName('登记核销')}).click();

    const drawer = page.locator('.ant-drawer-open').filter({hasText: '登记核销'}).first();
    await expect(drawer).toBeVisible();
    await selectOption(page, drawer, '资金类型', '收款');
    await drawer
      .locator('.ant-form-item')
      .filter({hasText: '资金单'})
      .getByRole('button', {name: accessibleName('选择')})
      .click();
    await pickRecord(page, '选择待核销收款', receipt.receiptNo);

    // 分配方式默认就是自动 FIFO：先预览再确认，避免直接按库里的顺序扣钱
    await drawer.getByRole('button', {name: accessibleName('预览分配')}).click();
    const plan = drawer.locator('.plan-table');
    const planRows = plan.locator('.ant-table-row');
    await expect(planRows).toHaveCount(2);
    await expect(planRows.nth(0)).toContainText(first.receivable.receivableNo);
    await expect(planRows.nth(0)).toContainText('35.0000');
    await expect(planRows.nth(1)).toContainText(second.receivable.receivableNo);
    await expect(planRows.nth(1)).toContainText('5.0000');

    await drawer.getByRole('button', {name: accessibleName('确认自动核销')}).click();
    await expect(page.locator('.ant-drawer-open')).toHaveCount(0);

    const applied = await call<Row>(harness.finance, 'post', '/scm/finance/write-off/query', {
      ...financeDateRange(),
      pageNum: 1,
      pageSize: 100,
      sourceNo: receipt.receiptNo,
    });
    const byTarget = new Map((applied.list as Row[]).map((item) => [String(item.targetNo), String(item.amount)]));
    expect(byTarget.get(first.receivable.receivableNo)).toBe('35.0000');
    expect(byTarget.get(second.receivable.receivableNo)).toBe('5.0000');

    // 剩余 5.0000 留在收款单待核销额，没有被摊到第二张应收上
    const remaining = await call<Row>(harness.finance, 'post', '/scm/finance/receivable/query', {
      ...financeDateRange(),
      pageNum: 1,
      pageSize: 100,
      customerId: first.customerId,
    });
    const openOf = (receivableNo: string) =>
      (remaining.list as Row[]).find((item) => String(item.receivableNo) === receivableNo)?.openAmount;
    expect(openOf(first.receivable.receivableNo)).toBe('0.0000');
    expect(openOf(second.receivable.receivableNo)).toBe('30.0000');
  });

  test('抹零：登记后冲减应收未结，反向抹零把金额退回', async ({page}) => {
    const openBefore = '30.0000';
    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receivables');
    const table = page.locator('#scm-finance-receivable-table');
    await expect(table).toBeVisible();
    const row = table.locator('tr').filter({hasText: second.receivable.receivableNo}).first();
    await expect(row).toContainText(openBefore);

    await row.getByRole('button', {name: accessibleName('抹零')}).click();
    const modal = page.locator('.ant-modal:visible').filter({hasText: /^抹零/}).last();
    await expect(modal).toContainText(second.receivable.receivableNo);
    await modal.locator('.ant-input-number input').fill('0.5000');
    await modal.locator('textarea').fill(`${harness.runTag} 对账抹零验收`);
    await modal.getByRole('button', {name: /确\s*定/}).click();
    await expect(modal).toHaveCount(0);

    const afterWaive = await call<Row>(harness.finance, 'post', '/scm/finance/receivable/query', {
      ...financeDateRange(),
      pageNum: 1,
      pageSize: 100,
      customerId: second.customerId,
    });
    expect((afterWaive.list as Row[]).find((item) => String(item.receivableNo) === second.receivable.receivableNo)?.openAmount).toBe(
      '29.5000'
    );

    const writeOffs = await call<Row>(harness.finance, 'post', '/scm/finance/write-off/query', {
      ...financeDateRange(),
      pageNum: 1,
      pageSize: 100,
      targetNo: second.receivable.receivableNo,
    });
    const waiverLine = (writeOffs.list as Row[]).find((item) => item.sourceType === 'WAIVER');
    expect(waiverLine?.amount).toBe('0.5000');

    // 撤销抹零只能追加反向记录：金额回到 30.0000，原抹零行保留
    await call(harness.finance, 'post', '/scm/finance/waiver/reverse', {
      waiverId: Number(waiverLine?.sourceId),
      reason: `${harness.runTag} 抹零登记有误`,
    });
    const afterReverse = await call<Row>(harness.finance, 'post', '/scm/finance/receivable/query', {
      ...financeDateRange(),
      pageNum: 1,
      pageSize: 100,
      customerId: second.customerId,
    });
    expect(
      (afterReverse.list as Row[]).find((item) => String(item.receivableNo) === second.receivable.receivableNo)?.openAmount
    ).toBe('30.0000');
    const reversed = await call<Row>(harness.finance, 'post', '/scm/finance/write-off/query', {
      ...financeDateRange(),
      pageNum: 1,
      pageSize: 100,
      targetNo: second.receivable.receivableNo,
    });
    expect((reversed.list as Row[]).filter((item) => item.sourceType === 'WAIVER')).toHaveLength(2);
  });
});
