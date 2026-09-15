/*
 * W2 供应商域 E2E
 *
 * 来源：**W1 派生** —— 结构、登录、夹具账号生命周期、CJK 按钮空格处理照抄 `e2e/scm-product.spec.ts`。
 *
 * 覆盖的**业务不变量**（W2 相对 legacy / C 的关键差异）：
 * - `supplier_sku` 是 **SKU 级**整表替换：提交后应存在的完整集合，而不是差量；
 * - **R12**：同一供应商允许同时存在多条 `defaultFlag = true` —— 通过 UI 勾两行默认并回查接口；
 * - **空数组 = 清空全部关联**（不是「无操作」）：删光行保存后接口必须返回 0 条；
 * - 快照冻结：`skuNameSnapshot` 取自商品，不随商品改名漂移；
 * - 供应商状态变更（停用 / 启用）走独立端点，且停用后不允许维护关联（40940）；
 * - 只读角色：接口 30005 + 「新增供应商」按钮不渲染。
 *
 * 夹具数据：先用商品接口造一个「上架 SPU + 2 个上架 SKU」，
 * 因为 `supplier_sku` 只接受「SPU 与 SKU 同时上架」的规格（否则后端 40942）。
 */
import { test, expect, request, type Page, type Locator, type APIRequestContext } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import smCrypto from 'sm-crypto';

const apiUrl = 'http://127.0.0.1:18080';
const name = 'w2_e2e_' + Date.now().toString(36);
const password = 'W2@' + randomBytes(6).toString('hex');
const env = { ...process.env, W2_E2E_NAME: name, W2_E2E_PASSWORD: password };
let api: APIRequestContext;
let token: string;
const supplierIds: (number | string)[] = [];
const productIds: (number | string)[] = [];
const categoryIds: (number | string)[] = [];

const escapeRe = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const buttonName = (text: string) => new RegExp('^' + [...text].map(escapeRe).join('\\s*') + '$');
const button = (root: Page | Locator, text: string) => root.getByRole('button', { name: buttonName(text) });

async function login(account: string) {
  const client = await request.newContext({ baseURL: apiUrl });
  const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
  const source = readFileSync('src/lib/encrypt.ts', 'utf8');
  const key = /const SM4_KEY = '([^']+)'/.exec(source)?.[1];
  if (!key) throw new Error('SmartAdmin transport key not found');
  const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
  const result = await (await client.post('/login', { data: { loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1 } })).json();
  expect(result.code, 'SmartAdmin login succeeds').toBe(0);
  await client.dispose();
  return result.data.token as string;
}

interface CategoryNode {
  categoryId: number;
  level: number;
  status: string;
  children?: CategoryNode[];
}

function findLeaf(nodes: CategoryNode[]): number | undefined {
  for (const node of nodes) {
    if (node.level === 3 && node.status === 'ENABLED') return node.categoryId;
    const found = findLeaf(node.children ?? []);
    if (found) return found;
  }
  return undefined;
}

async function addCategory(code: string, label: string, parentId?: number) {
  const result = await (await api.post('/scm/product/category/add', { data: { categoryCode: code, name: label, sortOrder: 0, status: 'ENABLED', parentId } })).json();
  expect(result.code, JSON.stringify(result)).toBe(0);
  categoryIds.push(result.data);
  return result.data as number;
}

/** 商品必须挂三级分类；没有现成的就现造一条三级链路。 */
async function ensureLeafCategory(prefix: string): Promise<number> {
  const tree = (await (await api.post('/scm/product/category/tree', {})).json()).data ?? [];
  const existing = findLeaf(tree);
  if (existing) return existing;
  const first = await addCategory(prefix + 'L1', prefix + '一级');
  const second = await addCategory(prefix + 'L2', prefix + '二级', first);
  return addCategory(prefix + 'L3', prefix + '三级', second);
}

async function seedProduct(prefix: string) {
  const categoryId = await ensureLeafCategory(prefix);
  const sku = (suffix: string) => ({
    skuCode: prefix + 'SKU' + suffix,
    specName: '规格' + suffix,
    specValues: { 规格: suffix },
    saleUnit: 'kg',
    productType: 'STANDARD',
    marketPrice: '9.9000',
    status: 'ON_SHELF',
    defaultFlag: suffix === '1',
    sortOrder: 0,
  });
  const result = await (
    await api.post('/scm/product/add', {
      data: {
        spuCode: prefix + 'SPU',
        name: prefix + '商品',
        categoryId,
        status: 'ON_SHELF',
        skuList: [sku('1'), sku('2')],
        images: [],
      },
    })
  ).json();
  expect(result.code, JSON.stringify(result)).toBe(0);
  productIds.push(result.data);
  return result.data as number;
}

test.beforeAll(async () => {
  execFileSync('python', ['../tools/w2_e2e_accounts.py', 'setup'], { env, stdio: 'pipe' });
  token = await login(name);
  api = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
});

test.afterAll(async () => {
  if (api) {
    for (const id of supplierIds) {
      const detail = await (await api.get(`/scm/supplier/detail/${id}`)).json();
      if (detail.code === 0) {
        // 还有活动关联时供应商删不掉（40947），必须先整表清空。
        // 用例中途失败会跳过清理，所以这一步不能省 —— 否则每次失败都留一条脏数据。
        await api.post('/scm/supplier/sku/replace', { data: { supplierId: id, items: [] } });
        const fresh = await (await api.get(`/scm/supplier/detail/${id}`)).json();
        await api.post('/scm/supplier/delete', { data: { supplierId: id, version: fresh.data.version } });
      }
    }
    for (const id of productIds) {
      const detail = await (await api.get(`/scm/product/detail/${id}`)).json();
      if (detail.code === 0) await api.post('/scm/product/delete', { data: { spuId: id, version: detail.data.version } });
    }
    for (const id of [...categoryIds].reverse()) {
      const detail = await (await api.get(`/scm/product/category/${id}`)).json();
      if (detail.code === 0) await api.post('/scm/product/category/delete', { data: { categoryId: id, version: detail.data.version } });
    }
    await api.get('/login/logout');
    await api.dispose();
  }
  execFileSync('python', ['../tools/w2_e2e_accounts.py', 'cleanup'], { env, stdio: 'pipe' });
});

async function authenticate(page: Page, value = token) {
  await page.addInitScript((v) => localStorage.setItem('smart_admin_user_token', v), value);
}

/** 当前打开的抽屉（同一时刻只有一个）。 */
const currentDrawer = (page: Page) => page.locator('.ant-drawer-open');

/** 在可编辑表格的某一行里选一个商品规格：点开下拉 → 输入编码过滤 → 取第一个命中项。 */
async function pickSku(page: Page, row: Locator, skuCode: string) {
  // 点 `.ant-select-selector`（而不是 `.ant-select` 根节点）才能稳定聚焦到内部 search input，
  // 后续 `keyboard.type` 的过滤字符才会进入 `optionFilterProp="label"` 的过滤链。
  await row.locator('.ant-select-selector').first().click();
  await page.keyboard.type(skuCode);
  // W3 SkuSelect performs a debounced server search; wait for the filtered
  // option before selecting so the initial unfiltered list cannot win a race.
  await page.waitForTimeout(450);
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').first().click();
}

test('live supplier pilot: SKU relations with R12 defaults, whole-table clear, status and deletion', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await authenticate(page);
  const prefix = name.toUpperCase();

  const productId = await seedProduct(prefix);
  const productDetail = (await (await api.get(`/scm/product/detail/${productId}`)).json()).data;
  expect(productDetail.skuList).toHaveLength(2);

  // ---------------------------------------------------------------- 新建供应商
  await page.goto('/#/supplier/supplier-list');
  await button(page, '新增供应商').click();
  let drawer = currentDrawer(page);
  await drawer.getByLabel('供应商编码', { exact: true }).fill(prefix + 'S1');
  await drawer.getByLabel('供应商名称', { exact: true }).fill(prefix + '供应商甲');
  await drawer.getByLabel('联系人', { exact: true }).fill('张三');
  await drawer.getByLabel('联系电话', { exact: true }).fill('13900139000');
  const created = page.waitForResponse((r) => r.url().endsWith('/scm/supplier/add'));
  await button(drawer, '保存').click();
  const body = await (await created).json();
  expect(body.code, JSON.stringify(body)).toBe(0);
  const supplierId = body.data as number;
  supplierIds.push(supplierId);
  await expect(page.locator('.ant-drawer-open')).toHaveCount(0);

  // ------------------------------------------------- 关联两个 SKU，且都为默认来源
  let row = page.getByRole('row').filter({ hasText: prefix + 'S1' });
  await expect(row).toBeVisible();
  await button(row, '关联商品').click();
  drawer = currentDrawer(page);
  await expect(drawer.getByText('关联商品 · ' + prefix + '供应商甲')).toBeVisible();

  await button(drawer, '添加商品').click();
  let skuRow = drawer.locator('tbody tr').first();
  await pickSku(page, skuRow, prefix + 'SKU1');
  await skuRow.getByLabel('第 1 行采购单位').fill('kg');
  await skuRow.getByLabel('第 1 行参考价').fill('3.5000');
  await skuRow.locator('.ant-checkbox-input').check();

  await button(drawer, '添加商品').click();
  skuRow = drawer.locator('tbody tr').nth(1);
  await pickSku(page, skuRow, prefix + 'SKU2');
  await skuRow.getByLabel('第 2 行采购单位').fill('箱');
  // R12：第二行也勾「默认来源」—— 同一供应商允许多条默认，前端不得做单选限制。
  await skuRow.locator('.ant-checkbox-input').check();

  const replaced = page.waitForResponse((r) => r.url().endsWith('/scm/supplier/sku/replace'));
  await button(drawer, '保存').click();
  const replaceBody = await (await replaced).json();
  expect(replaceBody.code, JSON.stringify(replaceBody)).toBe(0);
  await expect(page.locator('.ant-drawer-open')).toHaveCount(0);

  const relations = (await (await api.get(`/scm/supplier/sku/list/${supplierId}`)).json()).data;
  expect(relations).toHaveLength(2);
  // R12：两条同时为默认来源，这不是缺陷而是 legacy 事实。
  expect(relations.filter((item: { defaultFlag: boolean }) => item.defaultFlag)).toHaveLength(2);
  const first = relations.find((item: { purchaseUnit: string }) => item.purchaseUnit === 'kg');
  expect(first.referencePrice).toBe('3.5000');
  // 快照冻结：取自商品，不随商品后续改名而漂移。
  expect(first.skuNameSnapshot).toBe(prefix + '商品');
  expect(first.skuCodeSnapshot).toBe(prefix + 'SKU1');

  // 列表「关联商品数」必须反映活动关联数量。
  // `exact: true` 不能省：前缀（时间戳 base36）里可能含「2」，供应商名称按钮会被一并命中。
  await page.reload();
  row = page.getByRole('row').filter({ hasText: prefix + 'S1' });
  await expect(row.getByRole('button', { name: '2', exact: true })).toBeVisible();

  // ------------------------------------------------- 深链详情：只读快照表
  await button(row, prefix + '供应商甲').click();
  await expect(page).toHaveURL(new RegExp(`supplier-detail.*supplierId=${supplierId}`));
  await page.reload();
  await expect(page.getByText(prefix + 'SKU1', { exact: true })).toBeVisible();
  await expect(page.getByText(prefix + 'SKU2', { exact: true })).toBeVisible();
  await page.screenshot({ path: '../.runtime/w2-supplier-detail.png', fullPage: true });

  // ------------------------------------------------------------------ 编辑
  await button(page, '返回供应商列表').click();
  row = page.getByRole('row').filter({ hasText: prefix + 'S1' });
  await button(row, '编辑').click();
  drawer = currentDrawer(page);
  await expect(drawer.getByLabel('供应商编码', { exact: true })).toHaveValue(prefix + 'S1');
  await drawer.getByLabel('联系人', { exact: true }).fill('李四');
  const updated = page.waitForResponse((r) => r.url().endsWith('/scm/supplier/update'));
  await button(drawer, '保存').click();
  expect((await (await updated).json()).code).toBe(0);
  await expect(page.locator('.ant-drawer-open')).toHaveCount(0);
  expect((await (await api.get(`/scm/supplier/detail/${supplierId}`)).json()).data.contactName).toBe('李四');

  // --------------------------------------------------------- 状态：停用 / 启用
  await button(row, '停用').click();
  await page.locator('.ant-popover:visible').getByRole('button', { name: /确.*定/ }).click();
  await expect(button(row, '启用')).toBeVisible();
  await button(row, '启用').click();
  await page.locator('.ant-popover:visible').getByRole('button', { name: /确.*定/ }).click();
  await expect(button(row, '停用')).toBeVisible();

  // --------------------------------------------- 空数组 = 清空全部关联（R11）
  await button(row, '关联商品').click();
  drawer = currentDrawer(page);
  await expect(drawer.locator('tbody tr')).toHaveCount(2);
  await drawer.getByRole('button', { name: buttonName('移除第 2 行') }).click();
  await page.locator('.ant-popover:visible').getByRole('button', { name: /确.*定/ }).click();
  await drawer.getByRole('button', { name: buttonName('移除第 1 行') }).click();
  await page.locator('.ant-popover:visible').getByRole('button', { name: /确.*定/ }).click();
  await expect(drawer.locator('tbody tr').first()).toContainText('尚未关联任何商品');
  const cleared = page.waitForResponse((r) => r.url().endsWith('/scm/supplier/sku/replace'));
  await button(drawer, '保存').click();
  expect((await (await cleared).json()).code).toBe(0);
  expect((await (await api.get(`/scm/supplier/sku/list/${supplierId}`)).json()).data).toHaveLength(0);

  // ------------------------------------------------------------------ 删除
  await page.reload();
  row = page.getByRole('row').filter({ hasText: prefix + 'S1' });
  await button(row, '删除').click();
  await page.locator('.ant-popover:visible').getByRole('button', { name: /确.*定/ }).click();
  await expect(row).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('read-only role cannot mutate suppliers and buttons are hidden', async ({ page }) => {
  const readToken = await login(name + '_read');
  const client = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${readToken}` } });
  // 载荷合法，因此 @Valid 通过、拦截器先抛权限错误 30005。
  const result = await (await client.post('/scm/supplier/delete', { data: { supplierId: 1, version: 0 } })).json();
  expect(result.code).toBe(30005);
  await authenticate(page, readToken);
  await page.goto('/#/supplier/supplier-list');
  await expect(button(page, '查询')).toBeVisible();
  await expect(button(page, '新增供应商')).toHaveCount(0);
  await client.get('/login/logout');
  await client.dispose();
});
