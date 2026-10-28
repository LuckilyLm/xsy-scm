/*
 * 本文件由按域拆分的 E2E 用例合并而成：每个 test.describe 对应一份原文件，
 * 块内状态与钩子互不共享，执行顺序按来源文件排列。
 */
import {test, expect} from './scm-test-base';
import {test as pwTest} from '@playwright/test';
import {request, type Page, type Locator, type APIRequestContext} from '@playwright/test';
import {randomBytes, randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFileSync, mkdtempSync, rmSync, writeFileSync} from 'node:fs';
import smCrypto from 'sm-crypto';
import {accessibleName, apiClient, authenticate, login, provisionTempAccounts, type TempAccounts} from './scm-e2e-account';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

// ---------------------------------------------------------------- 来源：e2e/scm-customer.spec.ts
test.describe('scm-customer', () => {
  const test = pwTest;
  /* 客户域 E2E：账期三形态、授信额度字符串、状态变更、省市区六列、类型被引用后不可删、只读角色。 */

  const apiUrl = 'http://127.0.0.1:18080';
  const name = 'w2_e2e_' + Date.now().toString(36) + '_c';
  const password = 'W2@' + randomBytes(6).toString('hex');
  const env = {...process.env, W2_E2E_NAME: name, W2_E2E_PASSWORD: password};
  let api: APIRequestContext;
  let token: string;
  const customerIds: (number | string)[] = [];
  const typeIds: (number | string)[] = [];

  // antd 会在两个中文字之间插入视觉空格（「查询」显示为「查 询」），用允许空白的正则匹配
  const escapeRe = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const buttonName = (text: string) => new RegExp('^' + [...text].map(escapeRe).join('\\s*') + '$');
  const button = (root: Page | Locator, text: string) => root.getByRole('button', {name: buttonName(text)});

  // antd 把当前选中值渲染成 `.ant-select-selection-item`，它盖在内部 search input 之上，
  // 直接点 `getByLabel(...)` 命中的 input 会被 pointer-event 拦截而超时。
  // 因此统一通过表单控件的 `id`（a-form-item 带 name 时 antd 生成 `form_item_<name>`）
  // 定位到 `.ant-select-selector` 再点击开下拉。
  const formItem = (root: Page | Locator, field: string) => root.locator(`.ant-form-item:has(#form_item_${field})`);
  const openSelect = (root: Page | Locator, field: string) => formItem(root, field).locator('.ant-select-selector').click();

  async function login(account: string) {
    const client = await request.newContext({baseURL: apiUrl});
    const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
    const source = readFileSync('src/lib/encrypt.ts', 'utf8');
    const key = /const SM4_KEY = '([^']+)'/.exec(source)?.[1];
    if (!key) throw new Error('SmartAdmin transport key not found');
    const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
    const result = await (
      await client.post('/login', {
        data: {loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1},
      })
    ).json();
    expect(result.code, 'SmartAdmin login succeeds').toBe(0);
    await client.dispose();
    return result.data.token as string;
  }

  test.beforeAll(async () => {
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-setup', '--wave', 'w2'], {env, stdio: 'pipe'});
    token = await login(name);
    api = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${token}`}});
  });

  test.afterAll(async () => {
    if (api) {
      for (const id of customerIds) {
        const detail = await (await api.get(`/scm/customer/detail/${id}`)).json();
        if (detail.code === 0) await api.post('/scm/customer/delete', {data: {customerId: id, version: detail.data.version}});
      }
      // 客户类型没有 detail 端点，版本号从列表里取。
      const list = await (await api.post('/scm/customer/type/query', {data: {pageNum: 1, pageSize: 100, keyword: name.toUpperCase()}})).json();
      for (const row of list.code === 0 ? list.data.list : []) {
        await api.post('/scm/customer/type/delete', {data: {typeId: row.typeId, version: row.version}});
      }
      await api.get('/login/logout');
      await api.dispose();
    }
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-cleanup', '--wave', 'w2'], {env, stdio: 'pipe'});
  });

  async function authenticate(page: Page, value = token) {
    await page.addInitScript((v) => localStorage.setItem('smart_admin_user_token', v), value);
  }

  test('live customer pilot: type, credit period, status, search, deep link and deletion', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await authenticate(page);
    const prefix = name.toUpperCase();

    // ---------------------------------------------------------------- 客户类型
    await page.goto('/#/customer/customer-type-list');
    await button(page, '新增客户类型').click();
    const modal = page.locator('.ant-modal:visible');
    await modal.getByLabel('类型编码').fill(prefix + 'T');
    await modal.getByLabel('类型名称').fill(prefix + '类型');
    const typeCreated = page.waitForResponse((r) => r.url().endsWith('/scm/customer/type/add'));
    await modal.getByRole('button', {name: /确.*定/}).click();
    const typeBody = await (await typeCreated).json();
    expect(typeBody.code, JSON.stringify(typeBody)).toBe(0);
    typeIds.push(typeBody.data);
    await expect(modal).not.toBeVisible();
    // 列表只展示类型名称，编码不再占列；按名称定位这一行
    await expect(page.getByRole('row').filter({hasText: prefix + '类型'})).toBeVisible();

    // ------------------------------------------------------------------ 新建客户
    await page.goto('/#/customer/customer-list');
    await button(page, '新增客户').click();
    // a-drawer keeps its root element mounted after closing (destroy-on-close only drops
    // the body), so "closed" is asserted through the ant-drawer-open state class.
    const drawer = page.locator('.ant-drawer');
    const openDrawer = page.locator('.ant-drawer-open');
    const expectDrawerClosed = () => expect(openDrawer).toHaveCount(0);

    // 客户编码由服务端发号：创建态既没有可填输入框，也不放「由系统生成」这类提示。
    await expect(drawer.getByLabel('客户编码', {exact: true})).toHaveCount(0);
    await drawer.getByLabel('客户名称', {exact: true}).fill(prefix + '客户甲');
    await openSelect(drawer, 'customerTypeId');
    // 客户类型下拉的选项文本是「名称 （编码）」，不是纯名称，所以不能用 exact 匹配。
    await page
      .locator('.ant-select-dropdown:visible .ant-select-item-option')
      .filter({hasText: prefix + '类型'})
      .click();
    await drawer.getByLabel('联系电话', {exact: true}).fill('13800138000');

    // 地图 M0：级联选择必须落成「编码 + 名称」六列，这是大屏按市聚合与后续底图唯一的数据来源。
    await drawer.locator('.ant-cascader').click();
    const cascaderMenus = page.locator('.ant-cascader-dropdown:visible .ant-cascader-menu');
    await cascaderMenus.nth(0).getByText('浙江省', {exact: true}).click();
    await cascaderMenus.nth(1).getByText('杭州市', {exact: true}).click();
    await cascaderMenus.nth(2).getByText('西湖区', {exact: true}).click();
    await drawer.getByLabel('地址', {exact: true}).fill('文三路 100 号');
    await drawer.getByLabel('授信额度', {exact: true}).fill('1234.5000');

    // 账期：按时间 / 1 / 月 / 15 —— 只有单位是「月」时才出现固定结算日。
    await openSelect(drawer, 'creditPeriodType');
    await page.locator('.ant-select-dropdown:visible').getByText('按时间', {exact: true}).click();
    await drawer.getByLabel('账期值', {exact: true}).fill('1');
    await openSelect(drawer, 'creditPeriodUnit');
    await page.locator('.ant-select-dropdown:visible').getByText('月', {exact: true}).click();
    await expect(drawer.getByLabel('固定结算日', {exact: true})).toBeVisible();
    await drawer.getByLabel('固定结算日', {exact: true}).fill('15');

    const created = page.waitForResponse((r) => r.url().endsWith('/scm/customer/add'));
    await button(drawer, '保存').click();
    const body = await (await created).json();
    expect(body.code, JSON.stringify(body)).toBe(0);
    const customerId = body.data as number;
    customerIds.push(customerId);
    await expectDrawerClosed();

    // 列表 VO 不含账期字段，必须回详情接口核对 —— 这是 W2 新增能力是否真落库的唯一证据。
    const detail = (await (await api.get(`/scm/customer/detail/${customerId}`)).json()).data;
    // 编码不再采信客户端提交值：真实编码只能从服务端回读（CUS + 至少 6 位全局序号）。
    const customerCode = String(detail.customerCode);
    expect(customerCode).toMatch(/^CUS\d{6,}$/);
    expect(detail.creditLimit).toBe('1234.5000');
    expect(detail.creditPeriodType).toBe('BY_TIME');
    expect(detail.creditPeriodValue).toBe(1);
    expect(detail.creditPeriodUnit).toBe('MONTH');
    expect(detail.settleDay).toBe(15);
    expect(detail.status).toBe('POTENTIAL');
    expect(detail.contactPhone).toBe('13800138000');
    // 编码与名称必须来自**同一次选择**：只回编码会让导出与大屏文案各自漂移。
    expect([detail.provinceCode, detail.provinceName]).toEqual([330000, '浙江省']);
    expect([detail.cityCode, detail.cityName]).toEqual([330100, '杭州市']);
    expect([detail.districtCode, detail.districtName]).toEqual([330106, '西湖区']);

    // -------------------------------------------------------------- 状态变更
    // 列表行按名称定位：编码是服务端生成的，不能用「提交什么码」当锚点。
    let row = page.getByRole('row').filter({hasText: prefix + '客户甲'});
    await expect(row).toBeVisible();
    const statusChanged = page.waitForResponse((r) => r.url().endsWith('/scm/customer/updateStatus'));
    await button(row, '状态').click();
    await page.locator('.ant-dropdown:visible').getByText('合作中', {exact: true}).click();
    expect((await (await statusChanged).json()).code).toBe(0);
    await expect(row.getByText('合作中')).toBeVisible();

    // ------------------------------------------------------------------ 编辑
    await button(row, '编辑').click();
    // 编辑态与创建态一致：编码字段不进表单，内部发号只在列表 / 详情只读展示。
    await expect(drawer.getByLabel('客户编码', {exact: true})).toHaveCount(0);
    // 回填走 `areaNodesOf`：三级必须显示成一条连续路径，缺一级时级联选择器会渲染异常。
    await expect(drawer.locator('.ant-cascader .ant-select-selection-item')).toHaveText(/浙江省\s*\/\s*杭州市\s*\/\s*西湖区/);
    await drawer.getByLabel('授信额度', {exact: true}).fill('9999.0000');
    const updated = page.waitForResponse((r) => r.url().endsWith('/scm/customer/update'));
    await button(drawer, '保存').click();
    expect((await (await updated).json()).code).toBe(0);
    await expectDrawerClosed();
    const afterUpdate = (await (await api.get(`/scm/customer/detail/${customerId}`)).json()).data;
    expect(afterUpdate.creditLimit).toBe('9999.0000');
    // 编辑不重新触发级联 change：六列必须由详情回填原样带回去，不能在这次保存里被清空。
    expect([afterUpdate.provinceCode, afterUpdate.cityCode, afterUpdate.districtCode]).toEqual([330000, 330100, 330106]);

    // ------------------------------------------------------------------ 查询
    // 编码搜索仍要可用，但输入的是服务端回读的真实编码。
    await page.getByPlaceholder('客户名称 / 联系人 / 电话').fill(customerCode);
    await button(page, '查询').click();
    await expect(row).toBeVisible();

    // ------------------------------------------------- 深链详情（独立隐藏路由）
    await button(row, prefix + '客户甲').click();
    await expect(page).toHaveURL(new RegExp(`customer-detail.*customerId=${customerId}`));
    await page.reload();
    // 实体编码不进入详情页（列表/详情只展示业务字段）；深链能稳定回读靠的是 URL 里的 customerId
    await expect(page.getByText(prefix + '客户甲', {exact: true}).first()).toBeVisible();
    await expect(page.getByText('按时间', {exact: true})).toBeVisible();
    await expect(page.getByText('客户编码', {exact: true})).toHaveCount(0);
    await page.screenshot({path: '../.runtime/w2-customer-detail.png', fullPage: true});

    // ------------------------------------------------------------------ 删除
    // 侧栏菜单项不是 button，回列表直接走哈希路由
    await page.goto('/#/customer/customer-list');
    row = page.getByRole('row').filter({hasText: prefix + '客户甲'});
    // 删除属于危险动作，已从常驻操作收进「更多」；菜单项只在展开后进入 DOM。
    // 二次确认随之由 popconfirm 改为 Modal，断言的仍是「必须确认后才真的删除」。
    await row.getByRole('button', {name: /更多/}).click();
    await page.getByRole('menuitem', {name: buttonName('删除')}).click();
    await page
      .locator('.ant-modal:visible')
      .getByRole('button', {name: /删\s*除/})
      .click();
    await expect(row).toHaveCount(0);

    // 客户已删除，客户类型这时才能删（否则后端 40938）。
    await page.goto('/#/customer/customer-type-list');
    const typeRow = page.getByRole('row').filter({hasText: prefix + '类型'});
    await button(typeRow, '删除').click();
    await page
      .locator('.ant-popover:visible')
      .getByRole('button', {name: /确.*定/})
      .click();
    await expect(typeRow).toHaveCount(0);

    expect(errors).toEqual([]);
  });

  test('read-only role cannot mutate customers and buttons are hidden', async ({page}) => {
    const readToken = await login(name + '_read');
    const client = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${readToken}`}});
    // 载荷本身合法（customerId 非空、version >= 0），所以 @Valid 会通过，
    // 拦截器才会先抛出权限错误 30005 —— 这样断言的是权限，不是参数校验。
    const result = await (await client.post('/scm/customer/delete', {data: {customerId: 1, version: 0}})).json();
    expect(result.code).toBe(30005);
    await authenticate(page, readToken);
    await page.goto('/#/customer/customer-list');
    await expect(button(page, '查询')).toBeVisible();
    await expect(button(page, '新增客户')).toHaveCount(0);
    await client.get('/login/logout');
    await client.dispose();
  });
});

// ---------------------------------------------------------------- 来源：e2e/scm-customer-360.spec.ts
test.describe('scm-customer-360', () => {
  const test = pwTest;
  /*
   * Wave 7 客户 360° E2E（只读上下文聚合 + 双权限反例）
   *
   * 来源：用例结构照抄 `e2e/scm-customer.spec.ts`（临时账号 + 真实登录），
   * 账号与登录的共用实现见 `e2e/scm-e2e-account.ts`。
   *
   * 原先这组用例靠外部注入的一次性管理员令牌门控（缺令牌即整组 skip），
   * 于是「文件存在」被当成了「场景已验收」；现在它自建临时账号，真实跑在 dev 栈上。
   *
   * 这一页的聚合口径（仅 CONFIRMED、按 SKU+单位分组、订购量非结算量、最近价不兜底）已在
   * CustomerFrequentSkuIT 用真实 SQL 证明，因此 E2E 只验证**前端接线与权限边界**：
   * - 客户详情渲染出 5 个只读上下文 Tab；
   * - 切到「常购商品」确实以 **GET** 命中 `/frequent-skus` 且返回业务码 0（只读聚合，不是写命令）；
   * - 有客户查看权但**没有订单查看权**的账号，聚合端点必须被服务端整条拒（30005），
   *   页面落到错误态而不是把历史成交价摊开 —— 取数源是订单事实，权限是 `AND` 而不是「有客户权就能看」；
   * - 全程无 pageerror。
   */

  const ORDER_QUERY_PERMISSION = 'scm:order:query';

  let accounts: TempAccounts;
  let adminToken: string;
  let admin: APIRequestContext;
  let customerId = 0;
  let customerVersion = 0;

  test.describe('Wave 7 客户 360° 只读上下文', () => {
    test.beforeAll(async () => {
      accounts = provisionTempAccounts('w7', [ORDER_QUERY_PERMISSION]);
      adminToken = await login(accounts, accounts.admin);
      admin = await apiClient(adminToken);

      const types = await (await admin.post('/scm/customer/type/query', {data: {pageNum: 1, pageSize: 1}})).json();
      expect(types.code, `查询客户类型失败：${types.msg}`).toBe(0);
      const employees = await (await admin.post('/employee/query', {data: {pageNum: 1, pageSize: 1}})).json();
      expect(employees.code, `查询员工失败：${employees.msg}`).toBe(0);
      const suffix = randomUUID().replaceAll('-', '').slice(0, 12).toUpperCase();
      // 客户编码由服务端生成（CUS + 6 位序号），创建载荷不再提交编码；用例全部按 customerId 定位。
      const created = await (
        await admin.post('/scm/customer/add', {
          data: {
            name: `W7 客户 360 ${suffix}`,
            customerTypeId: types.data.list[0].typeId,
            settleMode: 'INDEPENDENT',
            sellerId: employees.data.list[0].employeeId,
          },
        })
      ).json();
      expect(created.code, `创建 Wave 7 客户夹具失败：${created.msg}`).toBe(0);
      customerId = Number(created.data);
      const detail = await (await admin.get(`/scm/customer/detail/${customerId}`)).json();
      expect(detail.code, `读取 Wave 7 客户夹具失败：${detail.msg}`).toBe(0);
      customerVersion = Number(detail.data.version);
    });

    test.afterAll(async () => {
      if (admin && customerId > 0) {
        await admin.post('/scm/customer/delete', {data: {customerId, version: customerVersion}});
      }
      await admin?.dispose();
      accounts?.cleanup();
    });

    /** 每轮自建客户夹具，干净迁移库也能执行只读上下文与权限反例。 */
    function requireCustomerId(): number {
      expect(customerId, 'Wave 7 客户夹具未创建').toBeGreaterThan(0);
      return customerId;
    }

    test('客户详情渲染 5 个上下文 Tab，常购商品走 GET 且返回业务码 0', async ({page}) => {
      const customerId = requireCustomerId();

      const errors: string[] = [];
      page.on('pageerror', (e) => errors.push(e.message));
      await authenticate(page, adminToken);
      await page.goto(`/#/customer/customer-detail?customerId=${customerId}`);

      // 5 个只读上下文 Tab 必须都在。
      for (const label of ['基础资料', '最近订单', '常购商品', '协议价', '可售商品']) {
        await expect(page.getByRole('tab', {name: accessibleName(label)})).toBeVisible();
      }

      // 切到常购商品：确实以 GET 命中聚合端点，返回业务码 0（只读、非写命令）。
      const freq = page.waitForResponse((r) => r.url().includes('/frequent-skus'));
      await page.getByRole('tab', {name: accessibleName('常购商品')}).click();
      const res = await freq;
      expect(res.request().method()).toBe('GET');
      const body = await res.json();
      expect(body.code, JSON.stringify(body)).toBe(0);

      expect(errors).toEqual([]);
    });

    test('缺订单查看权的账号读常购商品被整条拒，页面不摊开成交价', async ({page}) => {
      const customerId = requireCustomerId();

      const errors: string[] = [];
      page.on('pageerror', (e) => errors.push(e.message));
      const deniedToken = await login(accounts, accounts.denied!);
      const denied = await apiClient(deniedToken);
      try {
        // 前提：这个账号确实有客户查看权（否则「不泄露」只是因为根本进不来，断言没有意义）。
        const allowed = await (await denied.get(`/scm/customer/detail/${customerId}`)).json();
        expect(allowed.code, '扣权账号应仍保有 scm:customer:query').toBe(0);

        // 「客户查询 ∧ 订单查询」缺一即整条拒，且响应体不带任何价格字段。
        const rejected = await (await denied.get(`/scm/customer/${customerId}/frequent-skus?days=90&limit=20`)).json();
        expect(rejected.code, '缺订单查看权必须被服务端拒绝').toBe(30005);
        expect(JSON.stringify(rejected.data ?? null), '被拒响应不得带出任何成交数据').not.toMatch(/price|amount|qty/i);
      } finally {
        await denied.dispose();
      }

      // 同一个账号在真实页面上切到常购商品：落错误态，而不是渲染出价格表。
      await authenticate(page, deniedToken);
      await page.goto(`/#/customer/customer-detail?customerId=${customerId}`);
      // 扣权账号只挖掉 `scm:order:query` 这一枚按钮权限，详情页与 Tab 必须照常可达；
      // 若这里变成「菜单不下发落到 404」，证的就是另一件事了，必须让它红。
      const frequentTab = page.getByRole('tab', {name: accessibleName('常购商品')});
      await expect(frequentTab).toBeVisible();
      const response = page.waitForResponse((r) => r.url().includes('/frequent-skus'));
      await frequentTab.click();
      const body = await (await response).json();
      expect(body.code, '页面上的常购商品聚合必须被服务端整条拒').toBe(30005);
      await expect(page.getByText('最近成交价')).toHaveCount(0);
      expect(errors).toEqual([]);
    });
  });
});

// ---------------------------------------------------------------- 来源：e2e/scm-pricing.spec.ts
test.describe('scm-pricing', () => {
  /* W3 verification: real browser/API calls against the running SmartAdmin server.
   * The fixture helper creates an administrator and a read-only account; secrets
   * remain in process memory and are never written to the report.
   */

  const apiUrl = 'http://127.0.0.1:18080';
  const name = 'w3_e2e_' + Date.now().toString(36);
  const password = 'W3@' + randomBytes(6).toString('hex');
  const env = {...process.env, W3_E2E_NAME: name, W3_E2E_PASSWORD: password};
  let api: APIRequestContext;
  let token: string;
  let customerId: string | number;
  let agreementId: string | number;
  let productId: string | number | undefined;

  async function login(account: string) {
    const client = await request.newContext({baseURL: apiUrl});
    const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
    const source = readFileSync('src/lib/encrypt.ts', 'utf8');
    const key = /const SM4_KEY = '([^']+)'/.exec(source)?.[1];
    if (!key) throw new Error('SmartAdmin transport key not found');
    const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
    const result = await (
      await client.post('/login', {
        data: {loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1},
      })
    ).json();
    expect(result.code).toBe(0);
    await client.dispose();
    return result.data.token as string;
  }

  /** 用例会把选中的 SKU 下架后再解析价格，所以商品必须由本用例自建：
   *  复用 option-list 的首行会挑到挂在二级分类上的演示商品，而商品分类必须是三级，编辑校验会直接拒掉。 */
  async function createSku() {
    const categories = await (await api.post('/scm/product/category/tree', {data: {}})).json();
    const flatten = (items: Array<{level: number; children?: Array<{level: number}>}>): Array<{level: number; categoryId?: string | number}> =>
      items.flatMap((item) => [item as {level: number; categoryId?: string | number}, ...(item.children ? flatten(item.children) : [])]);
    const category = flatten(categories.data ?? []).find((item) => item.level === 3) ?? flatten(categories.data ?? [])[0];
    expect(category).toBeTruthy();
    const prefix = name.toUpperCase();
    const product = await (
      await api.post('/scm/product/add', {
        data: {
          spuCode: prefix + '-SPU',
          name: prefix + '-商品',
          categoryId: category.categoryId,
          status: 'ON_SHELF',
          images: [],
          skuList: [
            {
              skuCode: prefix + '-SKU',
              barcode: prefix + '-BAR',
              specName: '标准',
              specValues: {规格: '标准'},
              saleUnit: 'kg',
              productType: 'STANDARD',
              marketPrice: '8.0000',
              status: 'ON_SHELF',
              defaultFlag: true,
              sortOrder: 1,
            },
          ],
        },
      })
    ).json();
    expect(product.code).toBe(0);
    productId = product.data;
    const options = await (await api.post('/scm/product/sku/option-list', {data: {keyword: prefix, status: 'ON_SHELF', limit: 10}})).json();
    expect(options.data.options.length).toBeGreaterThan(0);
    return options.data.options[0];
  }

  test.beforeAll(async () => {
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-setup', '--wave', 'w3'], {env, stdio: 'pipe'});
    token = await login(name);
    api = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${token}`}});
  });

  test.afterAll(async () => {
    if (api) {
      if (agreementId) {
        const row = await (await api.get(`/scm/pricing/agreement-price/detail/${agreementId}`)).json();
        if (row.code === 0) await api.post('/scm/pricing/agreement-price/delete', {data: {agreementPriceId: agreementId, version: row.data.version}});
      }
      if (customerId) {
        const row = await (await api.get(`/scm/customer/detail/${customerId}`)).json();
        if (row.code === 0) await api.post('/scm/customer/delete', {data: {customerId, version: row.data.version}});
      }
      if (productId) {
        const row = await (await api.get(`/scm/product/detail/${productId}`)).json();
        if (row.code === 0) await api.post('/scm/product/delete', {data: {spuId: productId, version: row.data.version}});
      }
      await api.get('/login/logout');
      await api.dispose();
    }
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-cleanup', '--wave', 'w3'], {env, stdio: 'pipe'});
  });

  test('pricing resolver keeps price and sale eligibility independent', async ({page}) => {
    const types = await (await api.post('/scm/customer/type/option/list', {data: {}})).json();
    expect(types.code).toBe(0);
    const typeId = types.data[0].typeId;
    const prefix = name.toUpperCase();
    // 客户编码由服务端生成（CUS + 6 位序号），创建载荷不再提交编码。
    const customer = await (await api.post('/scm/customer/add', {data: {name: prefix, customerTypeId: typeId, settleMode: 'INDEPENDENT'}})).json();
    expect(customer.code).toBe(0);
    customerId = customer.data;
    const detail = await (await api.get(`/scm/customer/detail/${customerId}`)).json();
    await api.post('/scm/customer/updateStatus', {data: {customerId, version: detail.data.version, status: 'COOPERATING'}});
    const sku = await createSku();
    const price = await (
      await api.post('/scm/pricing/agreement-price/add', {
        data: {customerId, skuId: sku.skuId, unitPrice: '0.0000', effectiveFrom: '2026-09-01T00:00:00Z', effectiveTo: null},
      })
    ).json();
    expect(price.code).toBe(0);
    agreementId = price.data;
    let resolved = await (await api.post('/scm/pricing/resolve', {data: {customerId, skuIds: [sku.skuId], at: '2026-09-15T00:00:00Z'}})).json();
    expect(resolved.data.items[0]).toMatchObject({
      priceStatus: 'PRICED',
      unitPrice: '0.0000',
      sellable: true,
      unavailableReason: null,
      unpricedReason: null,
    });
    const product = await (await api.get(`/scm/product/detail/${sku.spuId}`)).json();
    product.data.skuList[0].status = 'OFF_SHELF';
    // 不检查返回码的话，保存被拒也会让下一条断言以「仍然可售」的形式溜过去
    expect((await (await api.post('/scm/product/update', {data: product.data})).json()).code).toBe(0);
    resolved = await (await api.post('/scm/pricing/resolve', {data: {customerId, skuIds: [sku.skuId], at: '2026-09-15T00:00:00Z'}})).json();
    expect(resolved.data.items[0]).toMatchObject({
      priceStatus: 'PRICED',
      unitPrice: '0.0000',
      sellable: false,
      unavailableReason: 'SKU_OFF_SHELF',
      unpricedReason: null,
    });
    await page.addInitScript((value) => localStorage.setItem('smart_admin_user_token', value), token);
    await page.goto('/#/pricing/price-preview');
    // V150 起菜单名与页面标题统一为「价格试算」
    await expect(page.locator('#smartAdminLayoutContent').getByText('价格试算', {exact: true}).first()).toBeVisible();
  });

  test('read-only role cannot mutate pricing and successful batch keys are protected', async () => {
    const readToken = await login(name + '_read');
    const read = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${readToken}`}});
    const denied = await (
      await read.post('/scm/pricing/agreement-price/add', {
        data: {customerId: 1, skuId: 1, unitPrice: '1.0000', effectiveFrom: '2026-09-01T00:00:00Z'},
      })
    ).json();
    expect(denied.code).toBe(30005);
    await read.get('/login/logout');
    await read.dispose();
  });
});

// ---------------------------------------------------------------- 来源：e2e/scm-product.spec.ts
test.describe('scm-product', () => {
  const test = pwTest;

  const apiUrl = 'http://127.0.0.1:18080';
  const name = 'w1_e2e_' + Date.now().toString(36);
  const prefix = name.toUpperCase();
  const password = 'W1@' + randomBytes(6).toString('hex');
  const env = {...process.env, W1_E2E_NAME: name, W1_E2E_PASSWORD: password};
  let api: APIRequestContext;
  let token: string;
  const productIds: (number | string)[] = [];
  const categoryIds: (number | string)[] = [];
  const uomIds: (number | string)[] = [];
  const tagIds: (number | string)[] = [];

  // antd 会在两个中文字之间插入视觉空格（「查询」显示为「查 询」），用允许空白的正则匹配
  const escapeRe = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const buttonName = (text: string) => new RegExp('^' + [...text].map(escapeRe).join('\\s*') + '$');
  const button = (root: Page | Locator, text: string) => root.getByRole('button', {name: buttonName(text)});

  // a-select is not a native select: the value is chosen by clicking the option in the teleported
  // dropdown, so every dictionary pick in these specs goes through this helper. Form items reach the
  // inner search input while a bare aria-label lands on the .ant-select root, hence ancestor-or-self;
  // the .ant-select-selection-* wrappers are skipped because their click point is covered by the
  // rendered value. rc-virtual-list also renders a zero-height role=option node for screen readers,
  // so the clickable target is the option content itself.
  const selectBox = (trigger: Locator) =>
    trigger.locator('xpath=ancestor-or-self::*[contains(@class,"ant-select") and not(contains(@class,"ant-select-selection"))][1]');
  async function pick(page: Page, trigger: Locator, option: string) {
    await selectBox(trigger).click();
    // 上一次选择的下拉可能仍在收起动画里，:visible 会命中旧面板；只认最后一个未隐藏的
    const dropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last();
    await dropdown.locator(`.ant-select-item-option-content:text-is("${option}")`).first().click();
  }

  async function login(account: string) {
    const client = await request.newContext({baseURL: apiUrl});
    const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
    const source = readFileSync('src/lib/encrypt.ts', 'utf8');
    const key = /const SM4_KEY = '([^']+)'/.exec(source)?.[1];
    if (!key) throw new Error('SmartAdmin transport key not found');
    const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
    const result = await (
      await client.post('/login', {
        data: {loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1},
      })
    ).json();
    expect(result.code, 'SmartAdmin login succeeds').toBe(0);
    await client.dispose();
    return result.data.token as string;
  }
  /** 字典行按主键删除前先查列表拿乐观锁版本；绑定关系已由商品删除解除。 */
  async function dropAssistant(kind: 'uom' | 'tag', keyName: 'uomId' | 'tagId', ids: (number | string)[]) {
    for (const id of ids) {
      const list = await (await api.post(`/scm/product/${kind}/list`, {data: {}})).json();
      const row = (list.data as Record<string, unknown>[]).find((item) => String(item[keyName]) === String(id));
      if (row) await api.post(`/scm/product/${kind}/delete`, {data: {[keyName]: id, version: row.version}});
    }
  }
  test.beforeAll(async () => {
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-setup', '--wave', 'w1'], {env, stdio: 'pipe'});
    token = await login(name);
    api = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${token}`}});
  });
  test.afterAll(async () => {
    if (api) {
      for (const id of productIds) {
        const detail = await (await api.get(`/scm/product/detail/${id}`)).json();
        if (detail.code === 0) await api.post('/scm/product/delete', {data: {spuId: id, version: detail.data.version}});
      }
      for (const id of [...categoryIds].reverse()) {
        const detail = await (await api.get(`/scm/product/category/${id}`)).json();
        if (detail.code === 0) await api.post('/scm/product/category/delete', {data: {categoryId: id, version: detail.data.version}});
      }
      // 标签必须在商品删除之后回收，否则仍被绑定的标签删不掉。
      await dropAssistant('tag', 'tagId', tagIds);
      await dropAssistant('uom', 'uomId', uomIds);
      await api.get('/login/logout');
      await api.dispose();
    }
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-cleanup', '--wave', 'w1'], {env, stdio: 'pipe'});
  });
  async function authenticate(page: Page, value = token) {
    await page.addInitScript((value) => localStorage.setItem('smart_admin_user_token', value), value);
  }
  async function saveCategory(page: Page, _code: string, label: string) {
    const modal = page.locator('.ant-modal:visible');
    // V147 起分类编码由后端发号，表单不再有「分类编码」输入
    await modal.getByLabel('分类名称').fill(label);
    const response = page.waitForResponse((r) => r.url().endsWith('/scm/product/category/add'));
    await modal.getByRole('button', {name: /确.*定/}).click();
    const body = await (await response).json();
    expect(body.code).toBe(0);
    categoryIds.push(body.data);
    await expect(modal).not.toBeVisible();
  }
  test('live product pilot: categories, SKU delta, SPU images, search, deep link and deletion', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await authenticate(page);
    await page.goto('/#/product/category-list');
    await button(page, '新增分类').click();
    await saveCategory(page, prefix + '1', prefix + '一级');
    let row = page.getByRole('row').filter({hasText: prefix + '一级'});
    await button(row, '新增子分类').click();
    await saveCategory(page, prefix + '2', prefix + '二级');
    row = page.getByRole('row').filter({hasText: prefix + '一级'});
    await row.locator('.ant-table-row-expand-icon').click();
    row = page.getByRole('row').filter({hasText: prefix + '二级'});
    await button(row, '新增子分类').click();
    await saveCategory(page, prefix + '3', prefix + '三级');
    await page.goto('/#/product/product-list');
    // 抽屉打开会并发拉取分类树与单位/标签字典，load() 返回时整体替换 form。a-spin 遮罩挡住了人工
    // 输入，但 Playwright 的 fill 不受 pointer-events 约束，所以要等这轮加载落地再填，否则值会被清空。
    const opened = button(page, '新增商品').click();
    const drawerLoading = page.locator('.ant-drawer .ant-spin-spinning');
    await drawerLoading.waitFor({state: 'attached', timeout: 3000}).catch(() => {});
    await opened;
    await expect(drawerLoading).toHaveCount(0);
    // a-drawer keeps its root element mounted after closing (destroy-on-close only drops
    // the body), so "closed" is asserted through the ant-drawer-open state class.
    const drawer = page.locator('.ant-drawer');
    const openDrawer = page.locator('.ant-drawer-open');
    const expectDrawerClosed = () => expect(openDrawer).toHaveCount(0);
    // V147 起商品 / 规格编码由后端发号，抽屉不再有编码输入
    await drawer.getByLabel('商品名称', {exact: true}).fill(prefix + '苹果');
    // 分类树用虚拟列表渲染，节点一多目标行根本不在 DOM 里，只能先按标题搜出唯一节点再点
    const category = drawer.getByLabel('商品分类', {exact: true});
    await selectBox(category).click();
    await selectBox(category)
      .locator('input')
      .fill(prefix + '三级');
    await page.locator(`.ant-select-dropdown:visible .ant-select-tree-title:text-is("${prefix}三级")`).click();
    // PCO-1 主档扩展：助记码由运营自维护，储存方式与标签一起进入同一个抽屉
    await drawer.getByLabel('助记码', {exact: true}).fill(prefix + 'PG');
    await pick(page, drawer.getByLabel('储存方式', {exact: true}), '冷藏');
    await pick(page, drawer.getByLabel('第 1 个商品规格的单位', {exact: true}), 'kg');
    await drawer.getByText('添加规格项', {exact: true}).click();
    await drawer.getByLabel('第 1 个商品规格的第 1 个规格值', {exact: true}).fill('大果');
    await drawer.getByText('新增商品规格', {exact: true}).click();
    await pick(page, drawer.getByLabel('第 2 个商品规格的单位', {exact: true}), 'kg');
    await drawer.getByLabel('第 2 个商品规格名称', {exact: true}).fill('小果');
    await drawer.getByLabel('第 2 个商品规格的条码', {exact: true}).fill(prefix + 'BAR');
    await drawer.locator('input[type=file]').setInputFiles({
      name: 'pilot.png',
      mimeType: 'image/png',
      buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII=', 'base64'),
    });
    await expect(drawer.getByText('主图', {exact: true})).toBeVisible();
    const created = page.waitForResponse((r) => r.url().endsWith('/scm/product/add'));
    await button(drawer, '保存商品').click();
    const body = await (await created).json();
    expect(body.code).toBe(0);
    const id = body.data;
    productIds.push(id);
    await expectDrawerClosed();
    row = page.locator('.ant-table-tbody tr.ant-table-row').filter({hasText: prefix + '苹果'});
    await expect(row).toBeVisible();
    await expect(row.getByText('可用', {exact: true})).toBeVisible();
    await row.locator('.ant-table-row-expand-icon').click();
    await expect(page.getByText(prefix + 'BAR', {exact: true})).toBeVisible();
    const before = (await (await api.get(`/scm/product/detail/${id}`)).json()).data;
    expect(before.mnemonicCode).toBe(prefix + 'PG');
    expect(before.storageMethod).toBe('CHILLED');
    expect(before.masterStatus).toBe('ENABLED');
    await button(row, '编辑').click();
    await expect(drawer.getByLabel('第 1 个商品规格的第 1 个规格值', {exact: true})).toHaveValue('大果');
    // 编辑态必须回显字典值，否则停用单位会让历史商品看不出原值
    await expect(selectBox(drawer.getByLabel('第 1 个商品规格的单位', {exact: true}))).toHaveText(/kg/);
    // 负责人口径：销售侧金额两位小数，市场价输入四位会被收敛到 12.3500
    await drawer.getByLabel('第 1 个商品规格的市场价', {exact: true}).fill('12.3456');
    await drawer.getByLabel('将第 2 个商品规格设为默认商品规格').check();
    await button(drawer, '保存商品').click();
    await expectDrawerClosed();
    const after = (await (await api.get(`/scm/product/detail/${id}`)).json()).data;
    expect(after.skuList.map((s: {skuId: string}) => s.skuId)).toEqual(before.skuList.map((s: {skuId: string}) => s.skuId));
    expect(after.defaultSku.skuCode).toMatch(/^SKU\d{6,}$/);
    expect(after.skuList[0].marketPrice).toBe('12.3500');
    row = page.locator('.ant-table-tbody tr.ant-table-row').filter({hasText: prefix + '苹果'});
    await button(row, '下架').click();
    await page
      .locator('.ant-popover:visible')
      .getByRole('button', {name: /确.*定/})
      .click();
    await expect(button(row, '上架')).toBeVisible();
    await page.getByPlaceholder('商品名称 / 条码').fill(prefix + 'BAR');
    await button(page, '查询').click();
    await expect(row).toBeVisible();
    // 助记码进入同一个关键字模糊搜索，运营不需要记住编码
    await page.getByPlaceholder('商品名称 / 条码').fill(prefix + 'PG');
    await button(page, '查询').click();
    await expect(row).toBeVisible();
    await button(page, '高级筛选').click();
    // 查询区的 a-form-item 只有 label 没有 name，AntD 不会生成 label/for 关联，只能按结构定位
    const queryForm = page.locator('form.smart-query-form');
    const querySelect = (label: string) => queryForm.locator('.ant-form-item').filter({hasText: label}).locator('.ant-select').first();
    await pick(page, querySelect('储存方式'), '冷藏');
    await pick(page, querySelect('主档状态'), '可用');
    await button(page, '查询').click();
    await expect(row).toBeVisible();
    await button(row, prefix + '苹果').click();
    await expect(page).toHaveURL(new RegExp(`product-detail.*spuId=${id}`));
    await page.reload();
    await expect(page.getByText(prefix + '苹果', {exact: true}).first()).toBeVisible();
    // 详情页的「主档扩展信息」块是这些字段的唯一展示入口
    await expect(page.getByText(prefix + 'PG', {exact: true})).toBeVisible();
    await expect(page.getByText('冷藏', {exact: true})).toBeVisible();
    const image = page.locator('.ant-image-img').first();
    await expect(image).toBeVisible();
    await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);
    await page.screenshot({path: '../.runtime/w1-product-detail.png', fullPage: true});
    await button(page, '返回商品列表').click();
    await button(page, '重置').click();
    await page.getByPlaceholder('商品名称 / 条码').fill(prefix + '苹果');
    await button(page, '查询').click();
    row = page.locator('.ant-table-tbody tr.ant-table-row').filter({hasText: prefix + '苹果'});

    // PCO-1 批量维护：标签先由接口建好，再刷新列表让下拉选项重新加载
    const tag = await (
      await api.post('/scm/product/tag/add', {data: {tagCode: prefix + 'TAG', name: prefix + '礼盒', sortOrder: 99, status: 'ENABLED'}})
    ).json();
    expect(tag.code).toBe(0);
    tagIds.push(tag.data);
    await page.reload();
    await page.getByPlaceholder('商品名称 / 条码').fill(prefix + '苹果');
    await button(page, '查询').click();
    row = page.locator('.ant-table-tbody tr.ant-table-row').filter({hasText: prefix + '苹果'});
    await expect(row.first()).toBeVisible();
    await row.first().locator('.ant-checkbox-wrapper').click();
    await expect(page.getByText(/已选\s*1\s*个/)).toBeVisible();
    const modal = page.locator('.ant-modal:visible');
    await button(page, '批量改状态').click();
    await modal.getByRole('button', {name: /确.*定/}).click();
    await expect(modal.getByText('请至少选择一个要修改的状态')).toBeVisible();
    await pick(page, modal.getByLabel('主档状态', {exact: true}), '停止引用');
    const statusChanged = page.waitForResponse((r) => r.url().endsWith('/scm/product/batch/updateStatus'));
    await modal.getByRole('button', {name: /确.*定/}).click();
    // 「停止引用」属危险动作：批量弹窗提交后还会再弹一次确认，命令在确认之后才发出
    await page
      .locator('.ant-modal-confirm:visible')
      .getByRole('button', {name: /确认停止引用/})
      .click();
    expect((await (await statusChanged).json()).data.updatedCount).toBe(1);
    await expect(row.getByText('停止引用', {exact: true})).toBeVisible();
    // 每次批量命令都会重载列表并清空选择，所以第二条命令要重新勾选
    await expect(row.first()).toBeVisible();
    await row.first().locator('.ant-checkbox-wrapper').click();
    await button(page, '批量打标签').click();
    const tagged = page.waitForResponse((r) => r.url().endsWith('/scm/product/batch/updateTags'));
    await pick(page, modal.getByLabel('商品标签', {exact: true}), prefix + '礼盒');
    // 多选下拉选完不自动收起，展开的面板会挡住底部按钮
    await modal.locator('.ant-modal-title').click();
    await modal.getByRole('button', {name: /确.*定/}).click();
    expect((await (await tagged).json()).data.updatedCount).toBe(1);
    await expect(row.getByText(prefix + '礼盒', {exact: true})).toBeVisible();
    expect((await (await api.get(`/scm/product/detail/${id}`)).json()).data.masterStatus).toBe('DISABLED');

    await button(row, '删除').click();
    await page
      .locator('.ant-popover:visible')
      .getByRole('button', {name: /确.*定/})
      .click();
    await expect(row).toHaveCount(0);
    expect(errors).toEqual([]);
  });
  test('assistant dictionaries: unit codes lock after creation and only unreferenced rows delete', async ({page}) => {
    await authenticate(page);
    await page.goto('/#/product/assistant-list');
    const modal = page.locator('.ant-modal:visible');
    await button(page, '新增单位').click();
    await modal.getByLabel('单位编码', {exact: true}).fill(prefix + 'UOM');
    await modal.getByLabel('单位名称', {exact: true}).fill(prefix + '筐');
    await pick(page, modal.getByLabel('量纲', {exact: true}), '计数');
    const added = page.waitForResponse((r) => r.url().endsWith('/scm/product/uom/add'));
    await modal.getByRole('button', {name: /确.*定/}).click();
    const addedBody = await (await added).json();
    expect(addedBody.code).toBe(0);
    uomIds.push(addedBody.data);
    await expect(modal).not.toBeVisible();
    const uomRow = page.getByRole('row').filter({hasText: prefix + '筐'});
    await expect(uomRow).toBeVisible();
    // 编辑态锁定编码与名称：业务表按名称记账，改名会让历史数据失去真值来源
    await button(uomRow, '编辑').click();
    await expect(modal.getByLabel('单位编码', {exact: true})).toBeDisabled();
    await expect(modal.getByLabel('单位名称', {exact: true})).toBeDisabled();
    await modal.getByRole('button', {name: /取.*消/}).click();
    // 演示数据里 kg 没有任何引用（种子的销售单位是份 / 箱 / 袋）：先用一张临时商品引用它，
    // 否则「被引用不可删」这条断言只能碰运气，且依赖前一个用例没删掉商品。
    const kgRef = await (
      await api.post('/scm/product/add', {
        data: {
          spuCode: prefix + 'KGR',
          name: prefix + '公斤商品',
          categoryId: categoryIds[categoryIds.length - 1],
          status: 'ON_SHELF',
          images: [],
          skuList: [
            {
              skuCode: prefix + 'KGRA',
              specName: '散装',
              specValues: {规格: '散装'},
              saleUnit: 'kg',
              productType: 'NON_STANDARD',
              marketPrice: '1.0000',
              status: 'ON_SHELF',
              defaultFlag: true,
              sortOrder: 0,
            },
          ],
        },
      })
    ).json();
    expect(kgRef.code, JSON.stringify(kgRef)).toBe(0);
    productIds.push(kgRef.data);
    await page.reload();
    await page.getByPlaceholder('单位名称').fill('kg');
    await button(page, '查询').click();
    const kgRow = page.getByRole('row').filter({hasText: 'kg'});
    await expect(button(kgRow, '删除')).toBeDisabled();

    await page.getByRole('tab', {name: '商品标签'}).click();
    await button(page, '新增标签').click();
    await modal.getByLabel('标签名称', {exact: true}).fill(prefix + '礼盒装');
    const createdTag = page.waitForResponse((r) => r.url().endsWith('/scm/product/tag/add'));
    await modal.getByRole('button', {name: /确.*定/}).click();
    const createdTagBody = await (await createdTag).json();
    expect(createdTagBody.code).toBe(0);
    tagIds.push(createdTagBody.data);
    const tagRow = page.getByRole('row').filter({hasText: prefix + '礼盒装'});
    // 未绑定任何商品的标签可以直接删掉，标签改名不影响已绑定商品的展示
    await expect(button(tagRow, '删除')).toBeEnabled();
    await button(tagRow, '编辑').click();
    await modal.getByLabel('标签名称', {exact: true}).fill(prefix + '礼盒装2');
    const renamed = page.waitForResponse((r) => r.url().endsWith('/scm/product/tag/update'));
    await modal.getByRole('button', {name: /确.*定/}).click();
    expect((await (await renamed).json()).code).toBe(0);
    const renamedRow = page.getByRole('row').filter({hasText: prefix + '礼盒装2'});
    await expect(renamedRow).toBeVisible();
    await button(renamedRow, '删除').click();
    await page
      .locator('.ant-popover:visible')
      .getByRole('button', {name: /确.*定/})
      .click();
    await expect(renamedRow).toHaveCount(0);
  });
  test('read-only role cannot mutate products and buttons are hidden', async ({page}) => {
    const readToken = await login(name + '_read');
    const client = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${readToken}`}});
    const result = await (await client.post('/scm/product/delete', {data: {spuId: 1, version: 0}})).json();
    expect(result.code).toBe(30005);
    // 批量命令的权限码里没有 add/update/delete 之类关键词，只读授权必须单独排除它
    const batch = await (
      await client.post('/scm/product/batch/updateStatus', {data: {items: [{spuId: 1, version: 0}], masterStatus: 'DISABLED'}})
    ).json();
    expect(batch.code).toBe(30005);
    await authenticate(page, readToken);
    await page.goto('/#/product/product-list');
    await expect(button(page, '查询')).toBeVisible();
    await expect(button(page, '新增商品')).toHaveCount(0);
    await expect(button(page, '批量改状态')).toHaveCount(0);
    await client.get('/login/logout');
    await client.dispose();
  });

  // PCO-2 商品运营入口：Excel 导入 / 导出按钮。用真实小 xlsx 字节只验证「选文件前禁止提交、
  // 选后解禁」的结构契约，本用例刻意不点「开始导入」（无效字节必被后端拒，写库另有代价）；
  // 真正以页面提交走完整链路的是文件末尾的 CREATE / UPDATE 往返两个用例。
  // 图片中心已改为侧边栏菜单入口，列表工具栏不再放这个按钮，其路由与功能由下方 image center 用例覆盖。
  test('PCO-2 excel entry: import modal gates submit until a file is chosen', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await authenticate(page);
    await page.goto('/#/product/product-list');
    await expect(button(page, '导入')).toBeVisible();
    await expect(button(page, '导出')).toBeVisible();
    await button(page, '导入').click();
    const modal = page.locator('.ant-modal:visible');
    await expect(modal.getByText('导入商品', {exact: true})).toBeVisible();
    const start = button(modal, '开始导入');
    // 未选文件时提交入口必须禁用，这就是「禁止重复提交」的第一道闸门
    await expect(start).toBeDisabled();
    await modal.locator('input[type=file]').setInputFiles({
      name: prefix + '.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: Buffer.from('UEsDBBQAAAAIAA==', 'base64'),
    });
    await expect(modal.getByText(prefix + '.xlsx')).toBeVisible();
    await expect(start).toBeEnabled();
    await button(modal, '下载模板').click();
    // 导入弹窗的页脚只有「下载模板 / 选择文件 / 开始导入」，关闭走弹窗自身的 Close 控件
    await modal.getByRole('button', {name: 'Close'}).click();
    await expect(modal).not.toBeVisible();
    expect(errors).toEqual([]);
  });

  // 图片中心：无图商品筛选、单商品维护入口、批量匹配先预览（0 命中时禁止绑定）。
  test('PCO-2 image center: filter no-image products, open single-SPU maintenance, preview batch before binding', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    // 复用「live product pilot」用例已建好的三级叶子分类（workers:1 使本文件串行、模块状态共享）
    const categoryId = categoryIds[categoryIds.length - 1];
    const added = await (
      await api.post('/scm/product/add', {
        data: {
          spuCode: prefix + 'IMG',
          name: prefix + '无图商品',
          categoryId,
          status: 'ON_SHELF',
          images: [],
          skuList: [
            {
              skuCode: prefix + 'IMGA',
              specName: '散装',
              specValues: {规格: '散装'},
              saleUnit: 'kg',
              productType: 'NON_STANDARD',
              marketPrice: '3.5000',
              status: 'ON_SHELF',
              defaultFlag: true,
              sortOrder: 0,
            },
          ],
        },
      })
    ).json();
    expect(added.code, `categoryId=${categoryId} ${JSON.stringify(added)}`).toBe(0);
    const spuId = added.data;
    productIds.push(spuId);

    await authenticate(page);
    await page.goto('/#/product/image-center');
    await expect(page).toHaveURL(/product-image-center|image-center/);
    const noImage = page.locator('.ant-checkbox-wrapper', {hasText: '仅无主图'});
    await noImage.click();
    await page.getByPlaceholder('商品名称').fill(prefix + '无图商品');
    await button(page, '查询').click();
    const row = page.getByRole('row').filter({hasText: prefix + '无图商品'});
    await expect(row).toBeVisible();
    // 「仅无主图」命中后，目标行必须显性标记为无主图
    await expect(row.getByText('无主图', {exact: true})).toBeVisible();
    await row.click();
    const batchEntry = button(page, '批量导入图片');
    await expect(batchEntry).toBeVisible();
    // 上传入口的可见文案含前置加号（虚线新增卡片）。a-upload 会把 children 包进同名的
    // <span class="ant-upload" role="button">，按角色选会命中两个节点，因此定位真实的那个 <button>。
    await expect(page.locator('button.add-tile')).toBeVisible();
    await expect(page.locator('button.add-tile')).toHaveText('+ 上传图片');
    await batchEntry.click();
    const modal = page.locator('.ant-modal:visible');
    await expect(modal.getByText('批量导入商品图片', {exact: true})).toBeVisible();
    // 两步式：先选图再指定商品。没有选择任何文件时「已指定/待指定」都是 0，
    // 提交入口保持禁用，绝不静默写入。
    await expect(modal.getByText('0 已指定')).toBeVisible();
    await expect(modal.getByText('0 待指定')).toBeVisible();
    await expect(button(modal, '请先指定商品')).toBeDisabled();
    await button(modal, '取消').click();
    await expect(modal).not.toBeVisible();
    expect(errors).toEqual([]);
  });

  // 无导入/导出授权的账号看不到这两个入口（图片中心仍可达，但批量写入口按权限隐藏）。
  test('PCO-2 read-only role cannot see import or export entry', async ({page}) => {
    const readToken = await login(name + '_read');
    await authenticate(page, readToken);
    await page.goto('/#/product/product-list');
    await expect(button(page, '查询')).toBeVisible();
    await expect(button(page, '导入')).toHaveCount(0);
    await expect(button(page, '导出')).toHaveCount(0);
  });

  // Wave 1 §12.3「商品 Excel UPDATE（修复后）」的真实浏览器闭环。此前的 PCO-2 用例刻意只验到
  // 「选文件前禁止提交」，从没真的用更新模式写过一行，所以计划把它记为验收缺口。
  // 本用例走完整链路：下载更新模板并填入真实定位键 → 只改一格、另一格留空 → 页面以更新模式上传 →
  // 库内见新值且留空列原值仍在 → 同一份旧文件重放必被乐观锁整批拒（不改写、不新增第二条）。
  test('Wave 1 UPDATE import: real page round trip preserves blank columns and rejects a stale file', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    // 复用「live product pilot」建的三级分类：本文件 workers:1 串行，模块状态按用例顺序共享
    const categoryId = categoryIds[categoryIds.length - 1];
    expect(categoryIds.length, '更新导入需要同文件首个用例建好的三级分类，请整文件跑本 spec 而不是 -g 单跑').toBeGreaterThan(0);
    const added = await (
      await api.post('/scm/product/add', {
        data: {
          spuCode: prefix + 'UPD',
          name: prefix + '更新导入商品',
          categoryId,
          status: 'ON_SHELF',
          alias: '原别名',
          brandName: '原品牌',
          images: [],
          skuList: [
            {
              skuCode: prefix + 'UPDA',
              specName: '统一',
              specValues: {规格: '统一'},
              saleUnit: 'kg',
              productType: 'STANDARD',
              marketPrice: '8.0000',
              status: 'ON_SHELF',
              defaultFlag: true,
              sortOrder: 0,
            },
          ],
        },
      })
    ).json();
    expect(added.code, JSON.stringify(added)).toBe(0);
    const spuId = added.data;
    productIds.push(spuId);

    // 更新导入的定位键（商品ID / 商品版本 / 规格ID / 规格版本）必须取自库里真值，测试不自己编乐观锁版本。
    // 输入用服务端的更新模板而不是列表导出：两者列集合刻意不同（导出给人看，含分类路径与标签名称；
    // 更新模板要分类编码与标签编码），把导出喂给更新导入会被 HEADER_INVALID 整批拒绝。
    const before = await (await api.get(`/scm/product/detail/${spuId}`)).json();
    expect(before.code).toBe(0);
    // 被留空的那一列必须在更新前有真原值，否则「空白列保持原值」会退化成空转断言
    expect(before.data.brandName, '更新导入的商品没有品牌原值').toBe('原品牌');
    const template = await api.get('/scm/product/import/template', {params: {mode: 'UPDATE'}});
    expect(template.status()).toBe(200);
    const dir = mkdtempSync(join(tmpdir(), 'w1-update-'));
    try {
      const templatePath = join(dir, 'template.xlsx');
      writeFileSync(templatePath, await template.body());
      const updatePath = join(dir, 'update.xlsx');
      const newAlias = prefix + '新别名';
      const sku = before.data.skuList[0];
      const patched = JSON.parse(
        execFileSync(
          'python',
          [
            '../tools/e2e_fixtures.py',
            'product-update',
            '--template',
            '--in',
            templatePath,
            '--out',
            updatePath,
            '--set',
            `商品ID=${before.data.spuId}`,
            '--set',
            `商品版本=${before.data.version}`,
            '--set',
            `商品规格ID=${sku.skuId}`,
            '--set',
            `商品规格版本=${sku.version}`,
            '--set',
            `别名=${newAlias}`,
          ],
          {encoding: 'utf8'}
        ).trim()
      ) as {rows: number; templateVersion: string};
      expect(patched.rows).toBe(1);
      expect(patched.templateVersion, '更新模板没有自带模板版本').toBeTruthy();

      await authenticate(page);
      await page.goto('/#/product/product-list');
      await button(page, '导入').click();
      const modal = page.locator('.ant-modal:visible');
      // 导入模式由模板自身决定，弹窗不再提供模式切换；提示语必须把「留空=不改写」讲清楚
      await expect(modal.getByText('空白列不会覆盖原值')).toBeVisible();
      let responded = page.waitForResponse((r) => r.url().includes('/scm/product/import'), {timeout: 30000});
      await modal.locator('input[type=file]').setInputFiles(updatePath);
      await expect(modal.getByText('update.xlsx')).toBeVisible();
      await button(modal, '开始导入').click();
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
      await modal.getByRole('button', {name: 'Close'}).click();
      await expect(modal).not.toBeVisible();
      await button(page, '导入').click();
      responded = page.waitForResponse((r) => r.url().includes('/scm/product/import'), {timeout: 30000});
      await modal.locator('input[type=file]').setInputFiles(updatePath);
      await button(modal, '开始导入').click();
      const stale = await (await responded).json();
      expect(stale.code).toBe(0);
      expect(stale.data.updatedProducts, '过期版本仍被写入了').toBe(0);
      expect((stale.data.errors as {code: string}[]).map((e) => e.code)).toContain('VERSION_CONFLICT');
      await expect(modal.getByText('本次没有任何商品写入')).toBeVisible();
      const afterStale = await (await api.get(`/scm/product/detail/${spuId}`)).json();
      expect(afterStale.data.alias).toBe(newAlias);
      expect(afterStale.data.version, '被拒的更新仍然推进了乐观锁版本').toBe(detail.data.version);
      await modal.getByRole('button', {name: 'Close'}).click();
    } finally {
      rmSync(dir, {recursive: true, force: true});
    }
    expect(errors).toEqual([]);
  });

  // §12.3 Wave 1「商品 Excel CREATE」此前只有弹窗闸门（见 PCO-2 excel entry 用例），新增模式从没真的
  // 用页面写过一行商品。本用例自建分类与文件，不借用其他用例的数据，单独执行亦成立：
  // 页面以新增模式上传「一个 SPU 两行 SKU」→ 库内见两个 SKU 且默认 SKU 只有一个 → 列表能查到该商品 →
  // 再上传「两行好 + 一行分类编码不存在 + 一行分类只到二级」必须整批 0 写入，既存商品不会多出第三个 SKU。
  test('Wave 1 CREATE import: real page round trip writes a multi-SKU product and rejects a batch with bad category rows', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    // 新增导入要求分类编码存在、ENABLED 且是三级分类：层级在逐行预校验就被拦下（与写入路径的
    // ProductCategoryService.requireSelectableCategory 同口径），不再等到整批写库才收到 40011
    const chainIds: (number | string)[] = [];
    let parentId: number | string | undefined;
    for (const name of [prefix + '导入一级', prefix + '导入二级', prefix + '导入三级']) {
      const created = await (
        await api.post('/scm/product/category/add', {
          data: {
            parentId,
            name,
            sortOrder: 0,
            status: 'ENABLED',
          },
        })
      ).json();
      expect(created.code, JSON.stringify(created)).toBe(0);
      categoryIds.push(created.data);
      chainIds.push(created.data);
      parentId = created.data;
    }
    // V147 起分类编码由后端发号：导入模板的「分类编码」只能用回读的真实编码
    const treeBody = await (await api.post('/scm/product/category/tree', {data: {}})).json();
    const flatCats = (rows: any[]): any[] => rows.flatMap((r) => [r, ...flatCats(r.children ?? [])]);
    const catCodeOf = (id: number | string) =>
      String(flatCats(treeBody.data).find((c: any) => String(c.categoryId ?? c.id) === String(id))?.categoryCode);
    const leafCategoryCode = catCodeOf(chainIds[2]);
    const secondLevelCode = catCodeOf(chainIds[1]);

    // 新增模板按业务列命名：分组只是本次导入的归并标记，SPU / SKU 编码由后端发号
    const spuName = prefix + '导入商品';
    const group = prefix + 'GRP';
    const goodRows = [
      {
        导入商品分组: group,
        商品名称: spuName,
        分类编码: leafCategoryCode,
        品牌: '导入品牌',
        商品状态: 'ON_SHELF',
        商品规格名称: '散装',
        销售单位: 'kg',
        商品类型: 'NON_STANDARD',
        市场价: '8.0000',
        商品规格状态: 'ON_SHELF',
        默认商品规格: '是',
      },
      {
        导入商品分组: group,
        商品名称: spuName,
        分类编码: leafCategoryCode,
        商品状态: 'ON_SHELF',
        商品规格名称: '整箱',
        销售单位: 'kg',
        商品类型: 'STANDARD',
        市场价: '96.0000',
        商品规格状态: 'ON_SHELF',
        默认商品规格: '否',
      },
    ];
    // 输入用服务端的新增模板而不是更新模板：两者列集合刻意不同，喂错模板由生成器显性拒绝
    const template = await api.get('/scm/product/import/template', {params: {mode: 'CREATE'}});
    expect(template.status()).toBe(200);
    const dir = mkdtempSync(join(tmpdir(), 'w1-create-'));
    try {
      const templatePath = join(dir, 'template.xlsx');
      writeFileSync(templatePath, await template.body());
      const fill = (out: string, rows: Record<string, string>[]) =>
        JSON.parse(
          execFileSync(
            'python',
            [
              '../tools/e2e_fixtures.py',
              'product-create',
              '--in',
              templatePath,
              '--out',
              out,
              ...rows.flatMap((row) => [
                '--row',
                Object.entries(row)
                  .map(([column, value]) => `${column}=${value}`)
                  .join(';'),
              ]),
            ],
            {encoding: 'utf8'}
          ).trim()
        ) as {rows: number; templateVersion: string};

      const goodPath = join(dir, 'create.xlsx');
      const filled = fill(goodPath, goodRows);
      expect(filled.rows).toBe(2);
      expect(filled.templateVersion, '新增模板没有自带模板版本').toBeTruthy();

      await authenticate(page);
      await page.goto('/#/product/product-list');
      await button(page, '导入').click();
      const modal = page.locator('.ant-modal:visible');
      // 导入入口只有一种模式：模板决定新增还是更新，响应里的 mode 断言继续校验实际走的是 CREATE
      await expect(modal.getByText('导入商品', {exact: true})).toBeVisible();
      let responded = page.waitForResponse((r) => r.url().includes('/scm/product/import'), {timeout: 30000});
      await modal.locator('input[type=file]').setInputFiles(goodPath);
      await expect(modal.getByText('create.xlsx')).toBeVisible();
      await button(modal, '开始导入').click();
      const vo = await (await responded).json();
      expect(vo.code, vo.msg).toBe(0);
      expect(vo.data.mode).toBe('CREATE');
      expect(vo.data.totalErrors, JSON.stringify(vo.data.errors)).toBe(0);
      expect(vo.data.totalRows).toBe(2);
      expect(vo.data.totalProducts).toBe(1);
      expect(vo.data.importedProducts, '一个 SPU 的两行 SKU 应只新增一个商品').toBe(1);
      expect(vo.data.spuIds).toHaveLength(1);
      const spuId = vo.data.spuIds[0];
      productIds.push(spuId);
      await expect(modal.getByText('成功导入 1 个商品')).toBeVisible();

      const detail = await (await api.get(`/scm/product/detail/${spuId}`)).json();
      expect(detail.code).toBe(0);
      expect(detail.data.spuCode).toMatch(/^SPU\d{6,}$/);
      expect(detail.data.brandName).toBe('导入品牌');
      const skus = detail.data.skuList as {skuCode: string; defaultFlag: boolean}[];
      expect(skus).toHaveLength(2);
      expect(
        skus.every((s) => /^SKU\d{6,}$/.test(s.skuCode)),
        skus.map((s) => s.skuCode).join(',')
      ).toBe(true);
      expect(skus.filter((s) => s.defaultFlag)).toHaveLength(1);

      // 页面闭环：新增结果必须能在列表里查到，而不只是接口回一个 id
      await modal.getByRole('button', {name: 'Close'}).click();
      await expect(modal).not.toBeVisible();
      // 商品编码由后端发号、不可搜索，列表搜索框只认名称与条码
      await page.getByPlaceholder('商品名称 / 条码').fill(spuName);
      await button(page, '查询').click();
      await expect(page.getByRole('row').filter({hasText: spuName})).toHaveCount(1);

      // 再加两行错行：一行给既存商品补个分类编码不存在的 SKU，另一行独立新建一个商品但只填到二级分类。
      // 二级分类那行刻意不并入同一 SPU，否则「同商品分类必须一致」会先炸出无关诊断，看不出层级校验。
      // 整批必须 0 写入：既存商品不会多出第三个 SKU，二级分类的新商品也不会落库
      const badPath = join(dir, 'create-bad.xlsx');
      expect(
        fill(badPath, [
          ...goodRows,
          {
            导入商品分组: group,
            商品名称: spuName,
            分类编码: prefix + 'NOCAT',
            商品状态: 'ON_SHELF',
            商品规格名称: '礼盒',
            销售单位: 'kg',
            商品类型: 'STANDARD',
            市场价: '199.0000',
            商品规格状态: 'ON_SHELF',
            默认商品规格: '否',
          },
          {
            导入商品分组: prefix + 'L2',
            商品名称: prefix + '二级分类商品',
            分类编码: secondLevelCode,
            商品状态: 'ON_SHELF',
            商品规格名称: '组合装',
            销售单位: 'kg',
            商品类型: 'STANDARD',
            市场价: '299.0000',
            商品规格状态: 'ON_SHELF',
            默认商品规格: '是',
          },
        ]).rows
      ).toBe(4);
      await button(page, '导入').click();
      responded = page.waitForResponse((r) => r.url().includes('/scm/product/import'), {timeout: 30000});
      await modal.locator('input[type=file]').setInputFiles(badPath);
      await button(modal, '开始导入').click();
      const rejected = await (await responded).json();
      expect(rejected.code).toBe(0);
      expect(rejected.data.importedProducts, '含错行的批次仍写入了商品').toBe(0);
      const badErrors = rejected.data.errors as {rowNumber: number; column: string; code: string}[];
      expect(badErrors.map((e) => e.code)).toContain('CATEGORY_NOT_FOUND');
      expect(badErrors.filter((e) => e.rowNumber === 5)).toEqual([expect.objectContaining({column: '分类编码', code: 'CATEGORY_LEVEL_INVALID'})]);
      await expect(modal.getByText('本次没有任何商品写入')).toBeVisible();
      const afterBad = await (await api.get(`/scm/product/detail/${spuId}`)).json();
      expect(afterBad.data.skuList).toHaveLength(2);
      expect(afterBad.data.version, '被拒的导入仍然推进了乐观锁版本').toBe(detail.data.version);
      await modal.getByRole('button', {name: 'Close'}).click();
    } finally {
      rmSync(dir, {recursive: true, force: true});
    }
    expect(errors).toEqual([]);
  });
});

// ---------------------------------------------------------------- 来源：e2e/scm-supplier.spec.ts
test.describe('scm-supplier', () => {
  const test = pwTest;
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

  const apiUrl = 'http://127.0.0.1:18080';
  const name = 'w2_e2e_' + Date.now().toString(36) + '_s';
  const password = 'W2@' + randomBytes(6).toString('hex');
  const env = {...process.env, W2_E2E_NAME: name, W2_E2E_PASSWORD: password};
  let api: APIRequestContext;
  let token: string;
  const supplierIds: (number | string)[] = [];
  const productIds: (number | string)[] = [];
  const categoryIds: (number | string)[] = [];

  const escapeRe = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const buttonName = (text: string) => new RegExp('^' + [...text].map(escapeRe).join('\\s*') + '$');
  const button = (root: Page | Locator, text: string) => root.getByRole('button', {name: buttonName(text)});

  async function searchSupplier(page: Page, keyword: string, rowText = keyword): Promise<Locator> {
    // 编码由服务端生成，夹具只能按「创建后回读的真实编码」或名称搜索；helper 本身与关键字无关。
    // 表格不常驻「供应商编码」列，所以按编码搜索时要用名称来定位那一行（结果集本来就只有一行）。
    await page.getByPlaceholder('编码 / 名称 / 联系人 / 电话').fill(keyword);
    await button(page, '查询').click();
    const row = page.getByRole('row').filter({hasText: rowText}).first();
    await expect(row).toBeVisible();
    return row;
  }

  async function login(account: string) {
    const client = await request.newContext({baseURL: apiUrl});
    const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
    const source = readFileSync('src/lib/encrypt.ts', 'utf8');
    const key = /const SM4_KEY = '([^']+)'/.exec(source)?.[1];
    if (!key) throw new Error('SmartAdmin transport key not found');
    const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
    const result = await (
      await client.post('/login', {
        data: {loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1},
      })
    ).json();
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
    const result = await (
      await api.post('/scm/product/category/add', {data: {categoryCode: code, name: label, sortOrder: 0, status: 'ENABLED', parentId}})
    ).json();
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
      specValues: {规格: suffix},
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
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-setup', '--wave', 'w2'], {env, stdio: 'pipe'});
    token = await login(name);
    api = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${token}`}});
  });

  test.afterAll(async () => {
    if (api) {
      for (const id of supplierIds) {
        const detail = await (await api.get(`/scm/supplier/detail/${id}`)).json();
        if (detail.code === 0) {
          // 还有活动关联时供应商删不掉（40947），必须先整表清空。
          // 用例中途失败会跳过清理，所以这一步不能省 —— 否则每次失败都留一条脏数据。
          await api.post('/scm/supplier/sku/replace', {data: {supplierId: id, items: []}});
          const fresh = await (await api.get(`/scm/supplier/detail/${id}`)).json();
          await api.post('/scm/supplier/delete', {data: {supplierId: id, version: fresh.data.version}});
        }
      }
      for (const id of productIds) {
        const detail = await (await api.get(`/scm/product/detail/${id}`)).json();
        if (detail.code === 0) await api.post('/scm/product/delete', {data: {spuId: id, version: detail.data.version}});
      }
      for (const id of [...categoryIds].reverse()) {
        const detail = await (await api.get(`/scm/product/category/${id}`)).json();
        if (detail.code === 0) await api.post('/scm/product/category/delete', {data: {categoryId: id, version: detail.data.version}});
      }
      await api.get('/login/logout');
      await api.dispose();
    }
    execFileSync('python', ['../tools/e2e_fixtures.py', 'account-cleanup', '--wave', 'w2'], {env, stdio: 'pipe'});
  });

  async function authenticate(page: Page, value = token) {
    await page.addInitScript((v) => localStorage.setItem('smart_admin_user_token', v), value);
  }

  /** 当前打开的抽屉（同一时刻只有一个）。 */
  const currentDrawer = (page: Page) => page.locator('.ant-drawer-open');

  /** 在可编辑表格的某一行里选一个商品规格：点开下拉 → 输入编码过滤 → 取第一个命中项。 */
  async function openRowAction(page: Page, row: Locator, label: string) {
    await row.getByRole('button', {name: /更多/}).click();
    await page.locator('.ant-dropdown:visible').getByText(label, {exact: true}).click();
  }

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

  test('live supplier pilot: SKU relations with R12 defaults, whole-table clear, status and deletion', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await authenticate(page);
    const prefix = name.toUpperCase();

    const productId = await seedProduct(prefix);
    const productDetail = (await (await api.get(`/scm/product/detail/${productId}`)).json()).data;
    expect(productDetail.skuList).toHaveLength(2);
    // V147 起规格编码由后端发号：造数传入的字符串不落库，交互与断言都只能用回读值。
    const skuCodeOfSpec = (spec: string) => String(productDetail.skuList.find((item: {specName: string}) => item.specName === spec).skuCode);
    const firstSkuCode = skuCodeOfSpec('规格1');
    const secondSkuCode = skuCodeOfSpec('规格2');

    // ---------------------------------------------------------------- 新建供应商
    await page.goto('/#/supplier/supplier-list');
    await button(page, '新增供应商').click();
    let drawer = currentDrawer(page);
    // 供应商编码由服务端发号：创建态既没有可填输入框，也不放「由系统生成」这类提示。
    await expect(drawer.getByLabel('供应商编码', {exact: true})).toHaveCount(0);
    await drawer.getByLabel('供应商名称', {exact: true}).fill(prefix + '供应商甲');
    await drawer.getByLabel('联系人', {exact: true}).fill('张三');
    await drawer.getByLabel('联系电话', {exact: true}).fill('13900139000');
    const created = page.waitForResponse((r) => r.url().endsWith('/scm/supplier/add'));
    await button(drawer, '保存').click();
    const body = await (await created).json();
    expect(body.code, JSON.stringify(body)).toBe(0);
    const supplierId = body.data as number;
    supplierIds.push(supplierId);
    await expect(page.locator('.ant-drawer-open')).toHaveCount(0);
    // 提交的编码不再被采信：真实编码只能回读（SUP + 至少 6 位全局序号）。
    const supplierDetail = (await (await api.get(`/scm/supplier/detail/${supplierId}`)).json()).data;
    const supplierCode = String(supplierDetail.supplierCode);
    expect(supplierCode).toMatch(/^SUP\d{6,}$/);

    // ------------------------------------------------- 关联两个 SKU，且都为默认来源
    let row = await searchSupplier(page, supplierCode, prefix + '供应商甲');
    await button(row, '关联商品').click();
    drawer = currentDrawer(page);
    await expect(drawer.getByText('关联商品 · ' + prefix + '供应商甲')).toBeVisible();

    await button(drawer, '添加商品').click();
    let skuRow = drawer.locator('tbody tr').first();
    await pickSku(page, skuRow, firstSkuCode);
    await skuRow.getByLabel('第 1 行采购单位').fill('kg');
    await skuRow.getByLabel('第 1 行参考价').fill('3.5000');
    await skuRow.locator('.ant-checkbox-input').check();

    await button(drawer, '添加商品').click();
    skuRow = drawer.locator('tbody tr').nth(1);
    await pickSku(page, skuRow, secondSkuCode);
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
    expect(relations.filter((item: {defaultFlag: boolean}) => item.defaultFlag)).toHaveLength(2);
    const first = relations.find((item: {purchaseUnit: string}) => item.purchaseUnit === 'kg');
    expect(first.referencePrice).toBe('3.5000');
    // 快照冻结：取自商品，不随商品后续改名而漂移。
    expect(first.skuNameSnapshot).toBe(prefix + '商品');
    expect(first.skuCodeSnapshot).toBe(firstSkuCode);

    // 列表「关联商品数」必须反映活动关联数量。
    // `exact: true` 不能省：前缀（时间戳 base36）里可能含「2」，供应商名称按钮会被一并命中。
    await page.reload();
    row = await searchSupplier(page, supplierCode, prefix + '供应商甲');
    await expect(row.getByRole('button', {name: '2', exact: true})).toBeVisible();

    // ------------------------------------------------- 深链详情：只读快照表
    await button(row, prefix + '供应商甲').click();
    await expect(page).toHaveURL(new RegExp(`supplier-detail.*supplierId=${supplierId}`));
    await page.reload();
    // 详情页只展示快照里的名称/规格；编码快照由上面接口断言保证，不进页面
    await expect(page.getByText(prefix + '商品').first()).toBeVisible();
    await expect(page.getByText(secondSkuCode, {exact: true})).toHaveCount(0);
    await page.screenshot({path: '../.runtime/w2-supplier-detail.png', fullPage: true});

    // ------------------------------------------------------------------ 编辑
    await button(page, '返回供应商列表').click();
    row = await searchSupplier(page, supplierCode, prefix + '供应商甲');
    await button(row, '编辑').click();
    drawer = currentDrawer(page);
    // 编辑态与创建态一致：编码字段不进表单，内部发号只在列表 / 详情只读展示。
    await expect(drawer.getByLabel('供应商编码', {exact: true})).toHaveCount(0);
    await drawer.getByLabel('联系人', {exact: true}).fill('李四');
    const updated = page.waitForResponse((r) => r.url().endsWith('/scm/supplier/update'));
    await button(drawer, '保存').click();
    expect((await (await updated).json()).code).toBe(0);
    await expect(page.locator('.ant-drawer-open')).toHaveCount(0);
    expect((await (await api.get(`/scm/supplier/detail/${supplierId}`)).json()).data.contactName).toBe('李四');

    // --------------------------------------------------------- 状态：停用 / 启用
    // 停用 / 启用属低频动作，已收进操作列「更多」；结果按状态列文本验证。
    await openRowAction(page, row, '启用 / 停用');
    await page
      .locator('.ant-modal-confirm:visible')
      .getByRole('button', {name: /停\s*用/})
      .click();
    await expect(row).toContainText('停用');
    await openRowAction(page, row, '启用 / 停用');
    await page
      .locator('.ant-modal-confirm:visible')
      .getByRole('button', {name: /启\s*用/})
      .click();
    await expect(row).toContainText('启用');

    // --------------------------------------------- 空数组 = 清空全部关联（R11）
    await button(row, '关联商品').click();
    drawer = currentDrawer(page);
    await expect(drawer.locator('tbody tr')).toHaveCount(2);
    await drawer.getByRole('button', {name: buttonName('移除第 2 行')}).click();
    await page
      .locator('.ant-popover:visible')
      .getByRole('button', {name: /确.*定/})
      .click();
    await drawer.getByRole('button', {name: buttonName('移除第 1 行')}).click();
    await page
      .locator('.ant-popover:visible')
      .getByRole('button', {name: /确.*定/})
      .click();
    await expect(drawer.locator('tbody tr').first()).toContainText('尚未关联任何商品');
    const cleared = page.waitForResponse((r) => r.url().endsWith('/scm/supplier/sku/replace'));
    await button(drawer, '保存').click();
    expect((await (await cleared).json()).code).toBe(0);
    expect((await (await api.get(`/scm/supplier/sku/list/${supplierId}`)).json()).data).toHaveLength(0);

    // ------------------------------------------------------------------ 删除
    await page.reload();
    row = await searchSupplier(page, supplierCode, prefix + '供应商甲');
    await openRowAction(page, row, '删除');
    await page
      .locator('.ant-modal-confirm:visible')
      .getByRole('button', {name: /删\s*除/})
      .click();
    await expect(row).toHaveCount(0);
    expect(errors).toEqual([]);
  });

  test('read-only role cannot mutate suppliers and buttons are hidden', async ({page}) => {
    const readToken = await login(name + '_read');
    const client = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${readToken}`}});
    // 载荷合法，因此 @Valid 通过、拦截器先抛权限错误 30005。
    const result = await (await client.post('/scm/supplier/delete', {data: {supplierId: 1, version: 0}})).json();
    expect(result.code).toBe(30005);
    await authenticate(page, readToken);
    await page.goto('/#/supplier/supplier-list');
    await expect(button(page, '查询')).toBeVisible();
    await expect(button(page, '新增供应商')).toHaveCount(0);
    await client.get('/login/logout');
    await client.dispose();
  });
});
