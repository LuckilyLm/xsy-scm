import { test, expect, request, type Page, type Locator, type APIRequestContext } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
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
  await page.goto('/#/product/product-list');
  // 抽屉打开会并发拉取分类树与单位/标签字典，load() 返回时整体替换 form。a-spin 遮罩挡住了人工
  // 输入，但 Playwright 的 fill 不受 pointer-events 约束，所以要等这轮加载落地再填，否则值会被清空。
  const opened = button(page, '新增商品').click();
  const drawerLoading = page.locator('.ant-drawer .ant-spin-spinning');
  await drawerLoading.waitFor({ state: 'attached', timeout: 3000 }).catch(() => {});
  await opened; await expect(drawerLoading).toHaveCount(0);
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

// PCO-2 商品运营入口：Excel 导入 / 导出按钮、图片中心路由。用真实小 xlsx 字节只验证「选文件前禁止提交、
// 选后解禁」的结构契约，本用例刻意不点「开始导入」（无效字节必被后端拒，写库另有代价）；
// 真正以页面提交走完整链路的是文件末尾的 CREATE / UPDATE 往返两个用例。
test('PCO-2 excel entry: import modal gates submit until a file is chosen', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
  await authenticate(page); await page.goto('/#/product/product-list');
  await expect(button(page, '导入')).toBeVisible();
  await expect(button(page, '导出')).toBeVisible();
  await expect(button(page, '图片中心')).toBeVisible();
  await button(page, '导入').click();
  const modal = page.locator('.ant-modal:visible');
  await expect(modal.getByText('导入商品', { exact: true })).toBeVisible();
  const start = button(modal, '开始导入');
  // 未选文件时提交入口必须禁用，这就是「禁止重复提交」的第一道闸门
  await expect(start).toBeDisabled();
  await modal.locator('input[type=file]').setInputFiles({
    name: prefix + '.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: Buffer.from('UEsDBBQAAAAIAA==', 'base64')
  });
  await expect(modal.getByText(/已选文件/)).toBeVisible();
  await expect(start).toBeEnabled();
  await button(modal, '下载模板').click();
  // 导入弹窗的页脚只有「下载模板 / 选择文件 / 开始导入」，关闭走弹窗自身的 Close 控件
  await modal.getByRole('button', {name: 'Close'}).click();
  await expect(modal).not.toBeVisible();
  expect(errors).toEqual([]);
});

// 图片中心：无图商品筛选、单商品维护入口、批量匹配先预览（0 命中时禁止绑定）。
test('PCO-2 image center: filter no-image products, open single-SPU maintenance, preview batch before binding', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
  // 复用「live product pilot」用例已建好的三级叶子分类（workers:1 使本文件串行、模块状态共享）
  const categoryId = categoryIds[categoryIds.length - 1];
  const added = await (await api.post('/scm/product/add', { data: {
    spuCode: prefix + 'IMG', name: prefix + '无图商品', categoryId, status: 'ON_SHELF', images: [],
    skuList: [{ skuCode: prefix + 'IMGA', specName: '散装', specValues: { 规格: '散装' }, saleUnit: 'kg', productType: 'NON_STANDARD', marketPrice: '3.5000', status: 'ON_SHELF', defaultFlag: true, sortOrder: 0 }]
  } })).json();
  expect(added.code, `categoryId=${categoryId} ${JSON.stringify(added)}`).toBe(0); const spuId = added.data; productIds.push(spuId);

  await authenticate(page); await page.goto('/#/product/image-center');
  await expect(page).toHaveURL(/product-image-center|image-center/);
  const noImage = page.locator('.ant-checkbox-wrapper', { hasText: '仅无主图' });
  await noImage.click();
  await page.getByPlaceholder('SPU 编码 / 名称 / 助记码').fill(prefix + 'IMG');
  await button(page, '查询').click();
  const row = page.getByRole('row').filter({ hasText: prefix + 'IMG' });
  await expect(row).toBeVisible();
  // 「仅无主图」命中后，目标行必须显性标记为无主图
  await expect(row.getByText('无主图', { exact: true })).toBeVisible();
  await row.click();
  const batchEntry = button(page, '按文件名批量导入');
  await expect(batchEntry).toBeVisible();
  // 上传入口的可见文案含前置加号（虚线新增卡片）。a-upload 会把 children 包进同名的
  // <span class="ant-upload" role="button">，按角色选会命中两个节点，因此定位真实的那个 <button>。
  await expect(page.locator('button.add-tile')).toBeVisible();
  await expect(page.locator('button.add-tile')).toHaveText('+ 上传图片');
  await batchEntry.click();
  const modal = page.locator('.ant-modal:visible');
  await expect(modal.getByText('文件名（去扩展名）需等于目标商品的 SPU 编码')).toBeVisible();
  // 预览优先：没有选择任何文件时命中为 0，绑定按钮必须禁用，绝不静默写入
  await expect(modal.getByText(/命中 0 · 歧义 0 · 未匹配 0/)).toBeVisible();
  await expect(button(modal, '绑定 0 张')).toBeDisabled();
  await button(modal, '取消').click();
  await expect(modal).not.toBeVisible();
  expect(errors).toEqual([]);
});

// 无导入/导出授权的账号看不到这两个入口（图片中心仍可达，但批量写入口按权限隐藏）。
test('PCO-2 read-only role cannot see import or export entry', async ({ page }) => {
  const readToken = await login(name + '_read');
  await authenticate(page, readToken); await page.goto('/#/product/product-list');
  await expect(button(page, '查询')).toBeVisible();
  await expect(button(page, '导入')).toHaveCount(0);
  await expect(button(page, '导出')).toHaveCount(0);
});

// Wave 1 §12.3「商品 Excel UPDATE（修复后）」的真实浏览器闭环。此前的 PCO-2 用例刻意只验到
// 「选文件前禁止提交」，从没真的用更新模式写过一行，所以计划把它记为验收缺口。
// 本用例走完整链路：下载更新模板并填入真实定位键 → 只改一格、另一格留空 → 页面以更新模式上传 →
// 库内见新值且留空列原值仍在 → 同一份旧文件重放必被乐观锁整批拒（不改写、不新增第二条）。
test('Wave 1 UPDATE import: real page round trip preserves blank columns and rejects a stale file', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
  // 复用「live product pilot」建的三级分类：本文件 workers:1 串行，模块状态按用例顺序共享
  const categoryId = categoryIds[categoryIds.length - 1];
  expect(categoryIds.length, '更新导入需要同文件首个用例建好的三级分类，请整文件跑本 spec 而不是 -g 单跑').toBeGreaterThan(0);
  const added = await (await api.post('/scm/product/add', { data: {
    spuCode: prefix + 'UPD', name: prefix + '更新导入商品', categoryId, status: 'ON_SHELF',
    alias: '原别名', brandName: '原品牌', images: [],
    skuList: [{ skuCode: prefix + 'UPDA', specName: '统一', specValues: { 规格: '统一' }, saleUnit: 'kg', productType: 'STANDARD', marketPrice: '8.0000', status: 'ON_SHELF', defaultFlag: true, sortOrder: 0 }]
  } })).json();
  expect(added.code, JSON.stringify(added)).toBe(0); const spuId = added.data; productIds.push(spuId);

  // 更新导入的定位键（SPU ID / SPU版本 / SKU ID / SKU版本）必须取自库里真值，测试不自己编乐观锁版本。
  // 输入用服务端的更新模板而不是列表导出：两者列集合刻意不同（导出给人看，含分类路径与标签名称；
  // 更新模板要分类编码与标签编码），把导出喂给更新导入会被 HEADER_INVALID 整批拒绝。
  const before = await (await api.get(`/scm/product/detail/${spuId}`)).json();
  expect(before.code).toBe(0);
  // 被留空的那一列必须在更新前有真原值，否则「空白列保持原值」会退化成空转断言
  expect(before.data.brandName, '更新导入的商品没有品牌原值').toBe('原品牌');
  const template = await api.get('/scm/product/import/template', { params: { mode: 'UPDATE' } });
  expect(template.status()).toBe(200);
  const dir = mkdtempSync(join(tmpdir(), 'w1-update-'));
  try {
    const templatePath = join(dir, 'template.xlsx'); writeFileSync(templatePath, await template.body());
    const updatePath = join(dir, 'update.xlsx');
    const newAlias = prefix + '新别名';
    const sku = before.data.skuList[0];
    const patched = JSON.parse(execFileSync('python', ['../tools/patch_product_update_xlsx.py', '--template',
      '--in', templatePath, '--out', updatePath,
      '--set', `SPU ID=${before.data.spuId}`, '--set', `SPU版本=${before.data.version}`,
      '--set', `SKU ID=${sku.skuId}`, '--set', `SKU版本=${sku.version}`, '--set', `别名=${newAlias}`],
      { encoding: 'utf8' }).trim()) as { rows: number; templateVersion: string };
    expect(patched.rows).toBe(1);
    expect(patched.templateVersion, '更新模板没有自带模板版本').toBeTruthy();

    await authenticate(page); await page.goto('/#/product/product-list');
    await button(page, '导入').click();
    const modal = page.locator('.ant-modal:visible');
    await modal.locator('.ant-radio-button-wrapper', { hasText: '更新既存商品' }).click();
    // 提示语必须把「留空=不改写」讲清楚，否则更新语义只存在于后端
    await expect(modal.getByText('空白列保持原值')).toBeVisible();
    let responded = page.waitForResponse(r => r.url().includes('/scm/product/import'), { timeout: 30000 });
    await modal.locator('input[type=file]').setInputFiles(updatePath);
    await expect(modal.getByText(/已选文件/)).toBeVisible();
    await button(modal, '开始更新').click();
    const vo = await (await responded).json();
    expect(vo.code, vo.msg).toBe(0);
    expect(vo.data.mode).toBe('UPDATE');
    expect(vo.data.totalErrors, JSON.stringify(vo.data.errors)).toBe(0);
    expect(vo.data.updatedProducts).toBe(1);
    await expect(modal.getByText('成功更新 1 个商品')).toBeVisible();

    const detail = await (await api.get(`/scm/product/detail/${spuId}`)).json();
    expect(detail.code).toBe(0);
    expect(detail.data.alias).toBe(newAlias);
    expect(detail.data.brandName, '更新导入把留空列当成清空指令了').toBe('原品牌');

    // 重放同一份文件：更新已经推进过版本，旧定位键必须整批被拒，且一条都不写
    await modal.getByRole('button', { name: 'Close' }).click();
    await expect(modal).not.toBeVisible();
    await button(page, '导入').click();
    await modal.locator('.ant-radio-button-wrapper', { hasText: '更新既存商品' }).click();
    responded = page.waitForResponse(r => r.url().includes('/scm/product/import'), { timeout: 30000 });
    await modal.locator('input[type=file]').setInputFiles(updatePath);
    await button(modal, '开始更新').click();
    const stale = await (await responded).json();
    expect(stale.code).toBe(0);
    expect(stale.data.updatedProducts, '过期版本仍被写入了').toBe(0);
    expect((stale.data.errors as { code: string }[]).map(e => e.code)).toContain('VERSION_CONFLICT');
    await expect(modal.getByText('本次没有任何商品写入')).toBeVisible();
    const afterStale = await (await api.get(`/scm/product/detail/${spuId}`)).json();
    expect(afterStale.data.alias).toBe(newAlias);
    expect(afterStale.data.version, '被拒的更新仍然推进了乐观锁版本').toBe(detail.data.version);
    await modal.getByRole('button', { name: 'Close' }).click();
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
  expect(errors).toEqual([]);
});

// §12.3 Wave 1「商品 Excel CREATE」此前只有弹窗闸门（见 PCO-2 excel entry 用例），新增模式从没真的
// 用页面写过一行商品。本用例自建分类与文件，不借用其他用例的数据，单独执行亦成立：
// 页面以新增模式上传「一个 SPU 两行 SKU」→ 库内见两个 SKU 且默认 SKU 只有一个 → 列表能查到该商品 →
// 再上传「两行好 + 一行错分类编码」必须整批 0 写入，既存商品不会多出第三个 SKU。
test('Wave 1 CREATE import: real page round trip writes a multi-SKU product and rejects a batch with one bad row', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
  // 新增导入要求分类编码存在且 ENABLED，而写入路径还要求商品只能挂在三级分类
  // （`ProductCategoryService.lockParent` 对 1/2 级回 40011），所以这里自建一条完整的三级链
  const categoryCode = prefix + 'CR';
  let parentId: number | string | undefined;
  for (const [index, name] of [prefix + '导入一级', prefix + '导入二级', prefix + '导入三级'].entries()) {
    const created = await (await api.post('/scm/product/category/add', { data: {
      parentId, categoryCode: categoryCode + (index + 1), name, sortOrder: 0, status: 'ENABLED'
    } })).json();
    expect(created.code, JSON.stringify(created)).toBe(0);
    categoryIds.push(created.data); parentId = created.data;
  }

  const spuCode = prefix + 'CRE';
  const leafCategoryCode = categoryCode + 3;
  const goodRows = [
    { SPU编码: spuCode, 商品名称: prefix + '导入商品', 分类编码: leafCategoryCode, 品牌: '导入品牌',
      商品上下架: 'ON_SHELF', SKU编码: spuCode + 'A', 规格名称: '散装', 销售单位: 'kg',
      商品类型: 'NON_STANDARD', 市场价: '8.0000', SKU上下架: 'ON_SHELF', 默认SKU: '是' },
    { SPU编码: spuCode, 商品名称: prefix + '导入商品', 分类编码: leafCategoryCode, 商品上下架: 'ON_SHELF',
      SKU编码: spuCode + 'B', 规格名称: '整箱', 销售单位: 'kg', 商品类型: 'STANDARD',
      市场价: '96.0000', SKU上下架: 'ON_SHELF', 默认SKU: '否' }
  ];
  // 输入用服务端的新增模板而不是更新模板：两者列集合刻意不同，喂错模板由生成器显性拒绝
  const template = await api.get('/scm/product/import/template', { params: { mode: 'CREATE' } });
  expect(template.status()).toBe(200);
  const dir = mkdtempSync(join(tmpdir(), 'w1-create-'));
  try {
    const templatePath = join(dir, 'template.xlsx'); writeFileSync(templatePath, await template.body());
    const fill = (out: string, rows: Record<string, string>[]) => JSON.parse(execFileSync('python',
      ['../tools/patch_product_create_xlsx.py', '--in', templatePath, '--out', out,
        ...rows.flatMap(row => ['--row', Object.entries(row).map(([column, value]) => `${column}=${value}`).join(';')])],
      { encoding: 'utf8' }).trim()) as { rows: number; templateVersion: string };

    const goodPath = join(dir, 'create.xlsx');
    const filled = fill(goodPath, goodRows);
    expect(filled.rows).toBe(2);
    expect(filled.templateVersion, '新增模板没有自带模板版本').toBeTruthy();

    await authenticate(page); await page.goto('/#/product/product-list');
    await button(page, '导入').click();
    const modal = page.locator('.ant-modal:visible');
    // 默认即新增模式，且提示语必须把「整批事务」讲清楚，不能只存在于后端
    await expect(modal.getByText('任意一行有错都不会写入任何商品')).toBeVisible();
    let responded = page.waitForResponse(r => r.url().includes('/scm/product/import'), { timeout: 30000 });
    await modal.locator('input[type=file]').setInputFiles(goodPath);
    await expect(modal.getByText(/已选文件/)).toBeVisible();
    await button(modal, '开始导入').click();
    const vo = await (await responded).json();
    expect(vo.code, vo.msg).toBe(0);
    expect(vo.data.mode).toBe('CREATE');
    expect(vo.data.totalErrors, JSON.stringify(vo.data.errors)).toBe(0);
    expect(vo.data.totalRows).toBe(2);
    expect(vo.data.totalProducts).toBe(1);
    expect(vo.data.importedProducts, '一个 SPU 的两行 SKU 应只新增一个商品').toBe(1);
    expect(vo.data.spuIds).toHaveLength(1);
    const spuId = vo.data.spuIds[0]; productIds.push(spuId);
    await expect(modal.getByText('成功导入 1 个商品')).toBeVisible();

    const detail = await (await api.get(`/scm/product/detail/${spuId}`)).json();
    expect(detail.code).toBe(0);
    expect(detail.data.spuCode).toBe(spuCode);
    expect(detail.data.brandName).toBe('导入品牌');
    const skus = detail.data.skuList as { skuCode: string; defaultFlag: boolean }[];
    expect(skus.map(s => s.skuCode).sort()).toEqual([spuCode + 'A', spuCode + 'B']);
    expect(skus.filter(s => s.defaultFlag)).toHaveLength(1);

    // 页面闭环：新增结果必须能在列表里查到，而不只是接口回一个 id
    await modal.getByRole('button', { name: 'Close' }).click();
    await expect(modal).not.toBeVisible();
    await page.getByPlaceholder('商品名 / 编码 / 条码 / 助记码').fill(spuCode);
    await button(page, '查询').click();
    await expect(page.getByRole('row').filter({ hasText: spuCode })).toHaveCount(1);

    // 同一商品再加一行，但那行的分类编码不存在：整批必须 0 写入，既存商品不会多出第三个 SKU
    const badPath = join(dir, 'create-bad.xlsx');
    expect(fill(badPath, [...goodRows, { SPU编码: spuCode, 商品名称: prefix + '导入商品',
      分类编码: prefix + 'NOCAT', 商品上下架: 'ON_SHELF', SKU编码: spuCode + 'C', 规格名称: '礼盒',
      销售单位: 'kg', 商品类型: 'STANDARD', 市场价: '199.0000', SKU上下架: 'ON_SHELF', 默认SKU: '否' }
    ]).rows).toBe(3);
    await button(page, '导入').click();
    responded = page.waitForResponse(r => r.url().includes('/scm/product/import'), { timeout: 30000 });
    await modal.locator('input[type=file]').setInputFiles(badPath);
    await button(modal, '开始导入').click();
    const rejected = await (await responded).json();
    expect(rejected.code).toBe(0);
    expect(rejected.data.importedProducts, '含错行的批次仍写入了商品').toBe(0);
    expect((rejected.data.errors as { code: string }[]).map(e => e.code)).toContain('CATEGORY_NOT_FOUND');
    await expect(modal.getByText('本次没有任何商品写入')).toBeVisible();
    const afterBad = await (await api.get(`/scm/product/detail/${spuId}`)).json();
    expect(afterBad.data.skuList).toHaveLength(2);
    expect(afterBad.data.version, '被拒的导入仍然推进了乐观锁版本').toBe(detail.data.version);
    await modal.getByRole('button', { name: 'Close' }).click();
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
  expect(errors).toEqual([]);
});
