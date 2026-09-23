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
import {test, expect, type APIRequestContext, type Page} from '@playwright/test';
import {execFileSync} from 'node:child_process';
import {mkdtempSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {accessibleName, apiClient, authenticate, login, provisionTempAccounts, type TempAccounts} from './scm-e2e-account';

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
const asNumber = (map: Record<string, string>) =>
  Object.fromEntries(Object.entries(map).map(([sku, qty]) => [sku, Number(qty)]));

/** 用 tools/fill_stocktake_template.py 填实盘量：只写「实盘数量」列，签名凭证列逐字不动。 */
function fillTemplate(outFile: string, delta: string): Record<string, string> {
  const stdout = execFileSync('python', ['../tools/fill_stocktake_template.py', '--in', templatePath, '--out', outFile, '--delta', delta], {
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
    const warehouses = await ok<any[]>(admin.get('/scm/warehouse/list'));
    for (const w of warehouses) {
      const bal = await ok<any>(admin.post('/scm/inventory/balance/query', {data: {warehouseId: w.id, pageNum: 1, pageSize: 1}}));
      if ((bal.list ?? []).length > 0) {
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

  test('页面选仓库后导出快照模板：命中模板端点并带回该仓库', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await authenticate(page, adminToken);
    await page.goto('/#/inventory/inventory-stocktake-list');
    await expect(page.getByRole('button', {name: accessibleName('导出快照模板')})).toBeVisible();
    // a-upload 的外层 span 也带 role=button，所以这里取真实按钮（最后一个匹配）
    await expect(page.getByRole('button', {name: accessibleName('导入盘点')}).last()).toBeVisible();

    // 导出绑定查询条件里的仓库：未选仓库时只给提示、不发请求，所以必须先在页面上真的选中。
    const warehouseItem = page.locator('.smart-query-form-item').filter({hasText: '仓库'}).first();
    await warehouseItem.locator('.ant-select').click();
    // 选项文本是「名称（编码）」且被拆成多个文本节点，只能按 option 元素包含匹配
    await page.locator('.ant-select-dropdown:visible .ant-select-item').filter({hasText: warehouseName}).first().click();

    const download = page.waitForResponse((r) => r.url().includes('/stocktake/import/template'));
    await page.getByRole('button', {name: accessibleName('导出快照模板')}).click();
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
    expect(detail.items.every((i: any) => i.bookQuantity !== undefined), '账面量由后端按快照写入').toBe(true);
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
    expect(body.data.errors.some((e: any) => e.code === 'SNAPSHOT_STALE'), JSON.stringify(body.data.errors)).toBe(true);
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

    await row.getByRole('button', {name: accessibleName('复制到新建')}).click();
    const drawer = page.locator('.ant-drawer-open');
    await expect(drawer.getByText('本表单尚未保存')).toBeVisible();
    // P0 缺陷：前端曾写死 pageSize=2000，被余额查询的 @Max(100) 直接拒掉，复制功能必然打不开。
    await expect.poll(() => balanceQueries.length, {message: '复制要读当前余额取记账单位'}).toBeGreaterThan(0);
    expect(balanceQueries.every((q) => q.pageSize > 0 && q.pageSize <= 100), JSON.stringify(balanceQueries)).toBe(true);

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
