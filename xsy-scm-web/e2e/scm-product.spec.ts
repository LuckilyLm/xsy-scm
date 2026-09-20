import { test, expect, request, type Page, type Locator, type APIRequestContext } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import smCrypto from 'sm-crypto';

const apiUrl = 'http://127.0.0.1:18080';
const name = 'w1_e2e_' + Date.now().toString(36);
const prefix = name.toUpperCase();
const password = 'W1@' + randomBytes(6).toString('hex');
const env = { ...process.env, W1_E2E_NAME: name, W1_E2E_PASSWORD: password };
let api: APIRequestContext;
let token: string;
const productIds: (number | string)[] = [];
const categoryIds: (number | string)[] = [];
const uomIds: (number | string)[] = [];
const tagIds: (number | string)[] = [];

// Ant Design inserts a visual space between two CJK characters in button labels
// ("查询" renders as "查 询"). Match the accessible name with optional whitespace
// between characters instead of weakening the assertion to a substring match.
const escapeRe = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const buttonName = (text: string) => new RegExp('^' + [...text].map(escapeRe).join('\\s*') + '$');
const button = (root: Page | Locator, text: string) => root.getByRole('button', { name: buttonName(text) });

// a-select is not a native select: the value is chosen by clicking the option in the teleported
// dropdown, so every dictionary pick in these specs goes through this helper. Form items reach the
// inner search input while a bare aria-label lands on the .ant-select root, hence ancestor-or-self;
// the .ant-select-selection-* wrappers are skipped because their click point is covered by the
// rendered value. rc-virtual-list also renders a zero-height role=option node for screen readers,
// so the clickable target is the option content itself.
const selectBox = (trigger: Locator) => trigger.locator('xpath=ancestor-or-self::*[contains(@class,"ant-select") and not(contains(@class,"ant-select-selection"))][1]');
async function pick(page: Page, trigger: Locator, option: string) {
  await selectBox(trigger).click();
  await page.locator(`.ant-select-dropdown:visible .ant-select-item-option-content:text-is("${option}")`).click();
}

async function login(account: string) {
  const client = await request.newContext({ baseURL: apiUrl });
  const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
  const source = readFileSync('src/lib/encrypt.ts', 'utf8');
  const key = /const SM4_KEY = '([^']+)'/.exec(source)?.[1];
  if (!key) throw new Error('SmartAdmin transport key not found');
  const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
  const result = await (await client.post('/login', { data: { loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1 } })).json();
  expect(result.code, 'SmartAdmin login succeeds').toBe(0);
  await client.dispose(); return result.data.token as string;
}
/** 字典行按主键删除前先查列表拿乐观锁版本；绑定关系已由商品删除解除。 */
async function dropAssistant(kind: 'uom' | 'tag', keyName: 'uomId' | 'tagId', ids: (number | string)[]) {
  for (const id of ids) {
    const list = await (await api.post(`/scm/product/${kind}/list`, { data: {} })).json();
    const row = (list.data as Record<string, unknown>[]).find((item) => String(item[keyName]) === String(id));
    if (row) await api.post(`/scm/product/${kind}/delete`, { data: { [keyName]: id, version: row.version } });
  }
}
test.beforeAll(async () => {
  execFileSync('python', ['../tools/w1_e2e_accounts.py', 'setup'], { env, stdio: 'pipe' });
  token = await login(name); api = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
});
test.afterAll(async () => {
  if (api) {
    for (const id of productIds) {
      const detail = await (await api.get(`/scm/product/detail/${id}`)).json();
      if (detail.code === 0) await api.post('/scm/product/delete', { data: { spuId: id, version: detail.data.version } });
    }
    for (const id of [...categoryIds].reverse()) {
      const detail = await (await api.get(`/scm/product/category/${id}`)).json();
      if (detail.code === 0) await api.post('/scm/product/category/delete', { data: { categoryId: id, version: detail.data.version } });
    }
    // 标签必须在商品删除之后回收，否则仍被绑定的标签删不掉。
    await dropAssistant('tag', 'tagId', tagIds); await dropAssistant('uom', 'uomId', uomIds);
    await api.get('/login/logout'); await api.dispose();
  }
  execFileSync('python', ['../tools/w1_e2e_accounts.py', 'cleanup'], { env, stdio: 'pipe' });
});
async function authenticate(page: Page, value = token) {
  await page.addInitScript(value => localStorage.setItem('smart_admin_user_token', value), value);
}
async function saveCategory(page: Page, code: string, label: string) {
  const modal = page.locator('.ant-modal:visible');
  await modal.getByLabel('分类编码').fill(code); await modal.getByLabel('分类名称').fill(label);
  const response = page.waitForResponse(r => r.url().endsWith('/scm/product/category/add'));
  await modal.getByRole('button', { name: /确.*定/ }).click();
  const body = await (await response).json(); expect(body.code).toBe(0); categoryIds.push(body.data);
  await expect(modal).not.toBeVisible();
}
test('live product pilot: categories, SKU delta, SPU images, search, deep link and deletion', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
  await authenticate(page); await page.goto('/#/product/category-list');
  await button(page, '新增分类').click(); await saveCategory(page, prefix + '1', prefix + '一级');
  let row = page.getByRole('row').filter({ hasText: prefix + '一级' });
  await button(row, '新增子分类').click(); await saveCategory(page, prefix + '2', prefix + '二级');
  row = page.getByRole('row').filter({ hasText: prefix + '一级' }); await row.locator('.ant-table-row-expand-icon').click();
  row = page.getByRole('row').filter({ hasText: prefix + '二级' });
  await button(row, '新增子分类').click(); await saveCategory(page, prefix + '3', prefix + '三级');
  await page.goto('/#/product/product-list'); await button(page, '新增商品').click();
  // a-drawer keeps its root element mounted after closing (destroy-on-close only drops
  // the body), so "closed" is asserted through the ant-drawer-open state class.
  const drawer = page.locator('.ant-drawer');
  const openDrawer = page.locator('.ant-drawer-open');
  const expectDrawerClosed = () => expect(openDrawer).toHaveCount(0);
  await drawer.getByLabel('商品名称', { exact: true }).fill(prefix + '苹果'); await drawer.getByLabel('SPU 编码', { exact: true }).fill(prefix + 'SPU');
  // 分类树用虚拟列表渲染，节点一多目标行根本不在 DOM 里，只能先按标题搜出唯一节点再点
  const category = drawer.getByLabel('商品分类', { exact: true });
  await selectBox(category).click();
  await selectBox(category).locator('input').fill(prefix + '三级');
  await page.locator(`.ant-select-dropdown:visible .ant-select-tree-title:text-is("${prefix}三级")`).click();
  // PCO-1 主档扩展：助记码由运营自维护，储存方式与标签一起进入同一个抽屉
  await drawer.getByLabel('助记码', { exact: true }).fill(prefix + 'PG');
  await pick(page, drawer.getByLabel('储存方式', { exact: true }), '冷藏');
  await drawer.getByLabel('SKU 1 编码', { exact: true }).fill(prefix + 'A'); await pick(page, drawer.getByLabel('SKU 1 单位', { exact: true }), 'kg');
  await button(drawer, '添加属性').click(); await drawer.getByLabel('SKU 1 属性值 1', { exact: true }).fill('大果');
  await button(drawer, '添加 SKU').click();
  await drawer.getByLabel('SKU 2 编码', { exact: true }).fill(prefix + 'B'); await pick(page, drawer.getByLabel('SKU 2 单位', { exact: true }), 'kg');
  await drawer.getByLabel('SKU 2 规格名称', { exact: true }).fill('小果'); await drawer.getByLabel('SKU 2 条码', { exact: true }).fill(prefix + 'BAR');
  await drawer.locator('input[type=file]').setInputFiles({ name: 'pilot.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII=', 'base64') });
  await expect(drawer.getByText('主图', { exact: true })).toBeVisible();
  const created = page.waitForResponse(r => r.url().endsWith('/scm/product/add'));
  await button(drawer, '保存商品').click(); const body = await (await created).json(); expect(body.code).toBe(0); const id = body.data; productIds.push(id);
  await expectDrawerClosed(); row = page.getByRole('row').filter({ hasText: prefix + 'SPU' });
  await expect(row).toBeVisible(); await expect(row.getByText('可用', { exact: true })).toBeVisible();
  await row.locator('.ant-table-row-expand-icon').click(); await expect(page.getByText(prefix + 'BAR', { exact: true })).toBeVisible();
  const before = (await (await api.get(`/scm/product/detail/${id}`)).json()).data;
  expect(before.mnemonicCode).toBe(prefix + 'PG'); expect(before.storageMethod).toBe('CHILLED'); expect(before.masterStatus).toBe('ENABLED');
  await button(row, '编辑').click(); await expect(drawer.getByLabel('SKU 1 编码', { exact: true })).toHaveValue(prefix + 'A');
  // 编辑态必须回显字典值，否则停用单位会让历史商品看不出原值
  await expect(selectBox(drawer.getByLabel('SKU 1 单位', { exact: true }))).toHaveText(/kg/);
  await drawer.getByLabel('SKU 1 市场价', { exact: true }).fill('12.3456'); await drawer.getByLabel('将第 2 个 SKU 设为默认').check();
  await button(drawer, '保存商品').click(); await expectDrawerClosed();
  const after = (await (await api.get(`/scm/product/detail/${id}`)).json()).data;
  expect(after.skuList.map((s: { skuId: string }) => s.skuId)).toEqual(before.skuList.map((s: { skuId: string }) => s.skuId));
  expect(after.defaultSku.skuCode).toBe(prefix + 'B'); expect(after.skuList[0].marketPrice).toBe('12.3456');
  row = page.getByRole('row').filter({ hasText: prefix + 'SPU' }); await button(row, '下架').click();
  await page.locator('.ant-popover:visible').getByRole('button', { name: /确.*定/ }).click();
  await expect(button(row, '上架')).toBeVisible();
  await page.getByPlaceholder('商品名 / 编码 / 条码 / 助记码').fill(prefix + 'BAR'); await button(page, '查询').click();
  await expect(row).toBeVisible();
  // 助记码进入同一个关键字模糊搜索，运营不需要记住编码
  await page.getByPlaceholder('商品名 / 编码 / 条码 / 助记码').fill(prefix + 'PG'); await button(page, '查询').click();
  await expect(row).toBeVisible();
  await button(page, '高级筛选').click();
  // 查询区的 a-form-item 只有 label 没有 name，AntD 不会生成 label/for 关联，只能按结构定位
  const queryForm = page.locator('form.smart-query-form');
  const querySelect = (label: string) => queryForm.locator('.ant-form-item').filter({ hasText: label }).locator('.ant-select').first();
  await pick(page, querySelect('储存方式'), '冷藏');
  await pick(page, querySelect('主档状态'), '可用');
  await button(page, '查询').click(); await expect(row).toBeVisible();
  await button(row, prefix + '苹果').click();
  await expect(page).toHaveURL(new RegExp(`product-detail.*spuId=${id}`)); await page.reload(); await expect(page.getByText(prefix + 'SPU', { exact: true })).toBeVisible();
  // 详情页的「主档扩展信息」块是这些字段的唯一展示入口
  await expect(page.getByText(prefix + 'PG', { exact: true })).toBeVisible(); await expect(page.getByText('冷藏', { exact: true })).toBeVisible();
  const image = page.locator('.ant-image-img').first(); await expect(image).toBeVisible(); await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);
  await page.screenshot({ path: '../.runtime/w1-product-detail.png', fullPage: true });
  await button(page, '返回商品列表').click();
  await button(page, '重置').click();
  await page.getByPlaceholder('商品名 / 编码 / 条码 / 助记码').fill(prefix + 'SPU'); await button(page, '查询').click();
  row = page.getByRole('row').filter({ hasText: prefix + 'SPU' });

  // PCO-1 批量维护：标签先由接口建好，再刷新列表让下拉选项重新加载
  const tag = await (await api.post('/scm/product/tag/add', { data: { tagCode: prefix + 'TAG', name: prefix + '礼盒', sortOrder: 99, status: 'ENABLED' } })).json();
  expect(tag.code).toBe(0); tagIds.push(tag.data);
  await page.reload();
  await page.getByPlaceholder('商品名 / 编码 / 条码 / 助记码').fill(prefix + 'SPU'); await button(page, '查询').click();
  row = page.getByRole('row').filter({ hasText: prefix + 'SPU' });
  await row.getByRole('checkbox').check(); await expect(page.getByText(/已选\s*1\s*个/)).toBeVisible();
  const modal = page.locator('.ant-modal:visible');
  await button(page, '批量改状态').click();
  await modal.getByRole('button', { name: /确.*定/ }).click();
  await expect(modal.getByText('请至少选择一个要修改的状态')).toBeVisible();
  await pick(page, modal.getByLabel('主档状态', { exact: true }), '停止引用');
  const statusChanged = page.waitForResponse(r => r.url().endsWith('/scm/product/batch/updateStatus'));
  await modal.getByRole('button', { name: /确.*定/ }).click();
  expect((await (await statusChanged).json()).data.updatedCount).toBe(1);
  await expect(row.getByText('停止引用', { exact: true })).toBeVisible();
  // 每次批量命令都会重载列表并清空选择，所以第二条命令要重新勾选
  await row.getByRole('checkbox').check();
  await button(page, '批量打标签').click();
  const tagged = page.waitForResponse(r => r.url().endsWith('/scm/product/batch/updateTags'));
  await pick(page, modal.getByLabel('商品标签', { exact: true }), prefix + '礼盒');
  // 多选下拉选完不自动收起，展开的面板会挡住底部按钮
  await modal.locator('.ant-modal-title').click();
  await modal.getByRole('button', { name: /确.*定/ }).click();
  expect((await (await tagged).json()).data.updatedCount).toBe(1);
  await expect(row.getByText(prefix + '礼盒', { exact: true })).toBeVisible();
  expect(((await (await api.get(`/scm/product/detail/${id}`)).json()).data).masterStatus).toBe('DISABLED');

  await button(row, '删除').click(); await page.locator('.ant-popover:visible').getByRole('button', { name: /确.*定/ }).click();
  await expect(row).toHaveCount(0); expect(errors).toEqual([]);
});
test('assistant dictionaries: unit codes lock after creation and only unreferenced rows delete', async ({ page }) => {
  await authenticate(page); await page.goto('/#/product/assistant-list');
  const modal = page.locator('.ant-modal:visible');
  await button(page, '新增单位').click();
  await modal.getByLabel('单位编码', { exact: true }).fill(prefix + 'UOM');
  await modal.getByLabel('单位名称', { exact: true }).fill(prefix + '筐');
  await pick(page, modal.getByLabel('量纲', { exact: true }), '计数');
  const added = page.waitForResponse(r => r.url().endsWith('/scm/product/uom/add'));
  await modal.getByRole('button', { name: /确.*定/ }).click();
  const addedBody = await (await added).json(); expect(addedBody.code).toBe(0); uomIds.push(addedBody.data);
  await expect(modal).not.toBeVisible();
  const uomRow = page.getByRole('row').filter({ hasText: prefix + '筐' });
  await expect(uomRow).toBeVisible();
  // 编辑态锁定编码与名称：业务表按名称记账，改名会让历史数据失去真值来源
  await button(uomRow, '编辑').click();
  await expect(modal.getByLabel('单位编码', { exact: true })).toBeDisabled();
  await expect(modal.getByLabel('单位名称', { exact: true })).toBeDisabled();
  await modal.getByRole('button', { name: /取.*消/ }).click();
  await page.getByPlaceholder('编码 / 名称').fill('kg'); await button(page, '查询').click();
  const kgRow = page.getByRole('row').filter({ hasText: 'kg' });
  await expect(button(kgRow, '删除')).toBeDisabled();

  await page.getByRole('tab', { name: '商品标签' }).click();
  await button(page, '新增标签').click();
  await modal.getByLabel('标签编码', { exact: true }).fill(prefix + 'TAG2');
  await modal.getByLabel('标签名称', { exact: true }).fill(prefix + '礼盒装');
  const createdTag = page.waitForResponse(r => r.url().endsWith('/scm/product/tag/add'));
  await modal.getByRole('button', { name: /确.*定/ }).click();
  const createdTagBody = await (await createdTag).json(); expect(createdTagBody.code).toBe(0); tagIds.push(createdTagBody.data);
  const tagRow = page.getByRole('row').filter({ hasText: prefix + '礼盒装' });
  // 未绑定任何商品的标签可以直接删掉，标签改名不影响已绑定商品的展示
  await expect(button(tagRow, '删除')).toBeEnabled();
  await button(tagRow, '编辑').click(); await modal.getByLabel('标签名称', { exact: true }).fill(prefix + '礼盒装2');
  const renamed = page.waitForResponse(r => r.url().endsWith('/scm/product/tag/update'));
  await modal.getByRole('button', { name: /确.*定/ }).click();
  expect((await (await renamed).json()).code).toBe(0);
  const renamedRow = page.getByRole('row').filter({ hasText: prefix + '礼盒装2' });
  await expect(renamedRow).toBeVisible();
  await button(renamedRow, '删除').click(); await page.locator('.ant-popover:visible').getByRole('button', { name: /确.*定/ }).click();
  await expect(renamedRow).toHaveCount(0);
});
test('read-only role cannot mutate products and buttons are hidden', async ({ page }) => {
  const readToken = await login(name + '_read');
  const client = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${readToken}` } });
  const result = await (await client.post('/scm/product/delete', { data: { spuId: 1, version: 0 } })).json();
  expect(result.code).toBe(30005);
  // 批量命令的权限码里没有 add/update/delete 之类关键词，只读授权必须单独排除它
  const batch = await (await client.post('/scm/product/batch/updateStatus', { data: { items: [{ spuId: 1, version: 0 }], masterStatus: 'DISABLED' } })).json();
  expect(batch.code).toBe(30005);
  await authenticate(page, readToken); await page.goto('/#/product/product-list'); await expect(button(page, '查询')).toBeVisible();
  await expect(button(page, '新增商品')).toHaveCount(0);
  await expect(button(page, '批量改状态')).toHaveCount(0);
  await client.get('/login/logout'); await client.dispose();
});
