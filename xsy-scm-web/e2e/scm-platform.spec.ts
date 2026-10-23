/*
 * 本文件由按域拆分的 E2E 用例合并而成：每个 test.describe 对应一份原文件，
 * 块内状态与钩子互不共享，执行顺序按来源文件排列。
 */
import {test, expect} from './scm-test-base';
import {test as pwTest} from '@playwright/test';
import {request, type APIRequestContext, type Page} from '@playwright/test';
import {apiUrl, authenticate, login, provisionTempAccounts, type TempAccounts} from './scm-e2e-account';
import {randomBytes} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import smCrypto from 'sm-crypto';

// ---------------------------------------------------------------- 来源：e2e/adm02-04-ui-contract.spec.ts
test.describe('adm02-04-ui-contract', () => {

  type Row = Record<string, unknown>;

  const root = 'http://127.0.0.1:18083/e2e/fixtures/adm-ui.html';
  const order = {orderId: 1, orderNo: 'SO-授信验收', version: 3, status: 'PENDING', customerId: 2,
    customerNameSnapshot: '验收客户', customerCodeSnapshot: 'ADM-C', settleModeSnapshot: 'INDEPENDENT',
    orderedTotalAmount: '10.0000', settlementTotalAmount: null as string | null, items: [] as Row[],
    address: {receiverName: '收货人', receiverPhone: '13800000000', address: '验收地址'}};

  async function mock(page: any, handler: (path: string, body: any, request: any) => unknown) {
    await page.route('**/*', async (route: any) => {
      const req = route.request();
      const url = new URL(req.url());
      // 列配置的真实路径是 /support/tableColumn/getColumns/{id}，不是 /tableColumn/…：
      // 只按前缀放行会让这一发请求打到真后端 → 无令牌 401 → axios 拦截器跳登录页，
      // 用例于是看到「元素 detached」而不是缺 mock。
      if (!url.pathname.startsWith('/scm/') && !url.pathname.includes('/tableColumn/')) return route.continue();
      let body;
      try { body = req.postDataJSON(); } catch { body = undefined; }
      const data = handler(url.pathname, body, req);
      await route.fulfill({contentType: 'application/json', body: JSON.stringify({code: 0, ok: true, data: data ?? [], msg: 'success'})});
    });
  }

  test('授信阻断提示与例外原因随确认命令提交', async ({page}) => {
    let confirmation: any;
    await mock(page, (path, body, req) => {
      if (path === '/scm/order/detail/1') return order;
      if (path === '/scm/order/credit-check/order/1') return {allowed: false, overLimit: true, overdue: false,
        creditLimit: '15.0000', projectedExposure: '20.0000', openReceivableAmount: '10.0000',
        confirmedOrderAmount: '0.0000', requestedOrderAmount: '10.0000'};
      if (path === '/scm/order/confirm') { confirmation = {body, key: req.headers()['idempotency-key']}; return {...order, status: 'CONFIRMED'}; }
    });
    await page.goto(root + '?mode=credit');
    await page.getByRole('button', {name: '确认订单', exact: true}).click();
    const modal = page.locator('.ant-modal:visible');
    await expect(modal).toContainText('额度不足或存在逾期');
    await expect(modal.locator('.ant-modal-footer .ant-btn-primary')).toBeDisabled();
    await modal.getByRole('checkbox').check();
    await modal.locator('textarea').fill('主管批准本次临时额度');
    await expect(modal.locator('.ant-modal-footer .ant-btn-primary')).toBeEnabled();
    await page.screenshot({path: '../.runtime/adm-credit-desktop.png'});
    await page.setViewportSize({width: 390, height: 844});
    await page.screenshot({path: '../.runtime/adm-credit-mobile.png'});
    await modal.locator('.ant-modal-footer .ant-btn-primary').click();
    await expect(modal).toBeHidden();
    expect(confirmation.body).toMatchObject({orderId: 1, version: 3, creditOverride: true, creditOverrideReason: '主管批准本次临时额度'});
    expect(confirmation.key).toMatch(/^[0-9a-f-]{36}$/);
  });

  test('部分退货仅提交剩余数量且无需不可见的处理原因', async ({page}) => {
    let receipt: any;
    const returned = {returnId: 11, returnNo: 'RT-实物验收', orderId: 1, version: 2, status: 'APPROVED',
      approvedAmount: '50.0000', reason: '实物验收', items: [{returnItemId: 12, orderItemId: 13,
        productName: '验收青菜', unit: 'kg', requestedQuantity: '5.0000', approvedQuantity: '5.0000', receivedQuantity: '3.0000'}]};
    await mock(page, (path, body, req) => {
      if (path === '/scm/order/return/query') return {list: [returned], total: 1, pageNum: 1, pageSize: 20};
      if (path === '/scm/order/return/detail/11') return returned;
      if (path === '/scm/warehouse/list') return [{id: 9, name: '验收仓库', warehouseCode: 'WH-ADM'}];
      if (path === '/scm/order/return/receive') { receipt = {body, key: req.headers()['idempotency-key']}; return {receiptId: 14}; }
    });
    await page.goto(root + '?mode=return');
    await page.getByRole('button', {name: '实物接收', exact: true}).click();
    const modal = page.locator('.ant-modal:visible');
    await expect(modal.getByLabel('本次接收数量')).toHaveValue('2.0000');
    await modal.locator('.ant-form-item').filter({hasText: '接收仓库'}).locator('.ant-select-selector').click();
    await page.locator('.ant-select-dropdown:visible').getByText('验收仓库', {exact: false}).first().click();
    await modal.locator('.ant-modal-title').click();
    await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0);
    await page.screenshot({path: '../.runtime/adm-return-desktop.png'});
    await page.setViewportSize({width: 390, height: 844});
    await page.screenshot({path: '../.runtime/adm-return-mobile.png'});
    await modal.locator('.ant-modal-footer .ant-btn-primary').click();
    await expect(modal).toBeHidden();
    expect(receipt.body).toEqual({returnId: 11, version: 2, warehouseId: 9,
      items: [{returnItemId: 12, quantity: '2.0000', disposition: 'RETURN_TO_STOCK'}]});
    expect(receipt.key).toMatch(/^[0-9a-f-]{36}$/);
  });

});

// ---------------------------------------------------------------- 来源：e2e/f0-file-storage.spec.ts
test.describe('f0-file-storage', () => {
  const test = pwTest;

  /**
   * 对象存储（S3/MinIO）模式下的文件权限取证 —— 只在 `XSY_FILE_STORAGE_MODE=cloud` 时执行。
   *
   * <p>为什么本地跑不了：本地存储把 `/upload/**` 静态直出、没有守卫，「私有」在本地不等于保密，
   * 预签名 / 403 / 短 TTL 这些语义只有在真实 S3 客户端上才存在。
   *
   * <p>身份沿用仓库约定（见 `scm-e2e-account.ts` 头部说明）：脚本自建临时账号、自己走真实登录，
   * 令牌只活在内存里、收尾删账号 —— 不从环境注入一次性令牌，否则「文件存在」会被误当成「已验收」。
   * 文件读判定与功能权限点无关（`/support/file/getFileUrl` 上没有 `@SaCheckPermission`），
   * 所以这里的「另一个员工」用的是零角色账号：证明的正是归属与关系，而不是权限码。
   */
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5XcAAAAASUVORK5CYII=', 'base64');
  const created: { fileId: number; fileKey: string }[] = [];
  let accounts: TempAccounts;
  let adminToken = '';
  let admin: APIRequestContext;
  let employee: APIRequestContext;
  let anonymous: APIRequestContext;

  test.describe('F0 cloud/MinIO file acceptance', () => {
    test.skip(process.env.XSY_FILE_STORAGE_MODE !== 'cloud', 'F0 presigned acceptance requires cloud storage; local is regression only');

    test.beforeAll(async () => {
      // 账号在 beforeAll 里建、在 afterAll 里删：本组在本地模式下整组 skip，
      // 若把 provisionTempAccounts 放到模块顶层，收集阶段就会建出一批没人回收的临时员工
      accounts = provisionTempAccounts('f0');
      adminToken = await login(accounts, accounts.admin);
      const employeeToken = await login(accounts, accounts.noRole);
      admin = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${adminToken}` } });
      employee = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${employeeToken}` } });
      anonymous = await request.newContext({ baseURL: apiUrl });
    });

    test.afterAll(async ({}, testInfo) => {
      // Native file API has no delete endpoint. Record exact fixture keys/IDs for scoped cleanup.
      // Never attach tokens, signed URLs or whole HTTP responses.
      await testInfo.attach('f0-created-file-fixtures', { body: JSON.stringify(created, null, 2), contentType: 'application/json' });
      await Promise.all([admin?.dispose(), employee?.dispose(), anonymous?.dispose()]);
      accounts.cleanup();
    });

    async function upload(client: APIRequestContext, folder = 1) {
      const response = await client.post(`/support/file/upload?folder=${folder}`, {
        multipart: { file: { name: `f0-${Date.now()}.png`, mimeType: 'image/png', buffer: png } },
      });
      const result = await response.json();
      expect(result.code).toBe(0);
      created.push({ fileId: result.data.fileId, fileKey: result.data.fileKey });
      expect(new URL(result.data.fileUrl).searchParams.has('X-Amz-Signature')).toBe(true);
      return result.data;
    }

    async function openFiles(page: Page) {
      await authenticate(page, adminToken);
      await page.goto('/#/home');
      await page.waitForLoadState('networkidle');
      const queried = page.waitForResponse(r => r.url().includes('/support/file/queryPage') && r.request().method() === 'POST');
      await page.goto('/#/support/file/file-list');
      expect((await (await queried).json()).code).toBe(0);
    }

    test('1 file page upload persists and appears in the list', async ({ page }) => {
      const errors: string[] = [];
      page.on('pageerror', e => errors.push(e.message));
      await openFiles(page);
      // Ant Design icons contribute their aria-label (e.g. "cloud-upload") to the
      // button accessible name, so exact text matching never resolves here.
      await page.getByRole('button', { name: /上传文件/ }).click();
      const name = `f0-ui-${Date.now()}.png`;
      const uploaded = page.waitForResponse(r => r.url().includes('/support/file/upload') && r.request().method() === 'POST');
      await page.locator('input[type=file]').setInputFiles({ name, mimeType: 'image/png', buffer: png });
      const result = await (await uploaded).json();
      expect(result.code).toBe(0);
      expect(new URL(result.data.fileUrl).searchParams.has('X-Amz-Signature')).toBe(true);
      created.push({ fileId: result.data.fileId, fileKey: result.data.fileKey });
      await page.locator('.ant-modal').getByRole('button', { name: /确.*定/ }).click();
      await page.getByPlaceholder('文件名', { exact: true }).fill(name);
      await page.getByRole('button', { name: /查\s*询/ }).click();
      await expect(page.locator('.ant-table-tbody').getByText(name, { exact: true })).toBeVisible();
      expect(errors).toEqual([]);
    });

    /**
     * 两个动作的顺序是刻意的：**先下载、后预览**。
     * antd 的图片预览遮罩会拦截整页指针事件，而本应用没有稳定的关闭入口
     * （点遮罩只是多数情况下生效，Escape 无效）；把预览放在最后，就不需要为了收尾再赌一次点击。
     */
    test('2 private image downloads through the guarded endpoint and previews via a presigned URL', async ({ page }) => {
      const file = await upload(admin);
      await openFiles(page);
      await page.getByPlaceholder('文件Key', { exact: true }).fill(file.fileKey);
      await page.getByRole('button', { name: /查\s*询/ }).click();
      const row = page.locator('.ant-table-tbody tr').filter({ hasText: file.fileName });
      const downloaded = page.waitForResponse(r => r.url().includes('/support/file/downLoad'));
      await row.getByRole('button', { name: '下载', exact: true }).click();
      expect(await (await downloaded).body()).toEqual(png);

      await row.getByRole('button', { name: '查看', exact: true }).click();
      const image = page.locator('.ant-image-preview-img');
      await expect(image).toBeVisible();
      await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);
    });

    async function signedFor(client: APIRequestContext, fileKey: string) {
      const body = await (await client.get('/support/file/getFileUrl', { params: { fileKey } })).json();
      return body;
    }

    async function noticeType(): Promise<number> {
      const body = await (await admin.get('/oa/noticeType/getAll')).json();
      expect(body.code).toBe(0);
      if (!body.data?.length) {
        const created = await (await admin.get('/oa/noticeType/add/F0%20E2E')).json();
        expect(created.code).toBe(0);
        const again = await (await admin.get('/oa/noticeType/getAll')).json();
        return again.data[0].noticeTypeId;
      }
      return body.data[0].noticeTypeId;
    }

    /**
     * FA-2 之后「能看这条公告 ⇒ 能看它的附件」，前缀级放行已经删除。
     * 这里成对取证，缺一条都不算收口：
     *   未绑定业务对象的私有附件，对不是上传者的员工必须拒（旧口径会因为 private/notice/ 前缀直接放行）；
     *   绑定到全部可见公告之后，同一个员工必须能签出字节。
     */
    test('3 notice attachment follows the notice visibility, not the directory prefix', async () => {
      const file = await upload(admin, 2);
      const params = { fileKey: file.fileKey };

      const detached = await signedFor(employee, file.fileKey);
      expect(detached.code, '未绑定公告的私有附件不得因为目录前缀被放行').toBe(30005);

      const add = await (await admin.post('/oa/notice/add', {
        data: {
          title: `f0-attachment-${Date.now()}`,
          noticeTypeId: await noticeType(),
          allVisibleFlag: true,
          scheduledPublishFlag: false,
          publishTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
          contentText: 'f0 e2e',
          contentHtml: '<p>f0 e2e</p>',
          attachment: file.fileKey,
          author: 'f0',
          source: 'f0',
        },
      })).json();
      expect(add.code, '公告新建必须成功，否则后面的授权判据无从取证').toBe(0);

      const signed = await signedFor(employee, file.fileKey);
      expect(signed.code).toBe(0);
      const url = new URL(signed.data);
      expect(url.searchParams.has('X-Amz-Signature')).toBe(true);
      // 取字节必须用签好的**原串**：URLSearchParams 会把 X-Amz-Credential 里的 %2F 再编码一次，
      // 签名随即失效 —— 那样报的是 403，看起来像授权没生效。
      const fetched = await anonymous.get(signed.data);
      const fetchedBody = await fetched.body();
      if (!fetchedBody.equals(png)) {
          console.log(`[diag] status=${fetched.status()} bytes=${fetchedBody.length} head=` +
              fetchedBody.subarray(0, 240).toString('utf8'));
      }
      // 取字节必须用**没有 Authorization 头**的上下文：预签名地址本身就是授权，
      // 再带上应用的 Bearer 会被 S3 判成「同时用了两种鉴权」而直接 400，与权限无关。
      expect(fetchedBody).toEqual(png);
      // 签名地址本身可匿名读，但去掉签名就不行：私有对象只靠短 TTL 签名授权
      expect((await anonymous.get(url.origin + url.pathname)).status()).toBe(403);
      expect((await signedFor(anonymous, file.fileKey)).code).not.toBe(0);
      expect((await signedFor(admin, file.fileKey)).code).toBe(0);
      expect(await (await employee.get('/support/file/downLoad', { params })).body()).toEqual(png);
    });

    test('4 COMMON/feedback rejects other employees; owner and administrator remain allowed', async () => {
      for (const folder of [1, 4]) {
        const other = await upload(admin, folder);
        for (const endpoint of ['getFileUrl', 'downLoad']) {
          const denied = await employee.get(`/support/file/${endpoint}`, { params: { fileKey: other.fileKey } });
          expect(denied.status()).toBe(403);
          expect((await denied.json()).code).toBe(30005);
        }
        const owned = await upload(employee, folder);
        expect((await (await employee.get('/support/file/getFileUrl', { params: { fileKey: owned.fileKey } })).json()).code).toBe(0);
        expect(await (await admin.get('/support/file/downLoad', { params: { fileKey: owned.fileKey } })).body()).toEqual(png);
      }
    });

    test('5 malformed prefixes and mixed-key batches fail closed', async () => {
      for (const fileKey of ['publicity/a', 'PUBLIC/a', 'private/notice/../common/a', 'private/notice/a,private/common/other']) {
        const denied = await employee.get('/support/file/getFileUrl', { params: { fileKey } });
        expect(denied.status()).toBe(403);
        expect((await denied.json()).code).toBe(30005);
      }
    });

    test('6 upload over 20 MiB is rejected', async () => {
      const response = await admin.post('/support/file/upload?folder=1', {
        multipart: { file: { name: 'f0-too-large.png', mimeType: 'image/png', buffer: Buffer.alloc(20 * 1024 * 1024 + 1) } },
      });
      const result = await response.json();
      expect(result.code).not.toBe(0);
      expect(result.msg).toMatch(/20|大小|上传|超/);
    });

    test('7 Tika rejects HTML disguised as PNG', async () => {
      const response = await admin.post('/support/file/upload?folder=1', {
        multipart: { file: { name: 'f0-fake.png', mimeType: 'image/png', buffer: Buffer.from('<!DOCTYPE html><html><script>alert(1)</script></html>') } },
      });
      const result = await response.json();
      expect(result.code).not.toBe(0);
      expect(result.msg).toContain('禁止上传');
    });

    /**
     * 商品图前缀（folder=5 → public/image/）是公开资产：URL 必须是**不过期的静态地址**且匿名可读。
     * FA-3 把存量商品图搬到这个前缀之后，商品详情才不再依赖短 TTL 签名。
     */
    test('8 public image folder yields a cacheable anonymous-readable URL', async () => {
      const response = await admin.post('/support/file/upload?folder=5', {
        multipart: { file: { name: `f0-public-${Date.now()}.png`, mimeType: 'image/png', buffer: png } },
      });
      const result = await response.json();
      expect(result.code).toBe(0);
      const fileKey: string = result.data.fileKey;
      created.push({ fileId: result.data.fileId, fileKey });
      expect(fileKey.startsWith('public/image/')).toBe(true);
      expect(new URL(result.data.fileUrl).searchParams.has('X-Amz-Signature')).toBe(false);
      const anonymousGet = await anonymous.get(result.data.fileUrl);
      expect(anonymousGet.status()).toBe(200);
      expect(await anonymousGet.body()).toEqual(png);
    });
  });

});

// ---------------------------------------------------------------- 来源：e2e/mall-theme.spec.ts
test.describe('mall-theme', () => {

  // H5 浏览器契约使用受控主题响应；真实发布/回滚事实由 MallHomeLayoutPgIT 验证。
  const mallUrl = process.env.SCM_MALL_H5_URL;
  test('商城主题跨页、刷新、直达及发布版本切换', async ({page}) => {
      test.skip(!mallUrl, '需要独立启动商城 H5 并设置 SCM_MALL_H5_URL');
      let themeCode = 'WARM';
      let releaseId = 1;
      let offline = false;
      await page.route('**/scm/mall/home/theme*', async route => {
          if (offline) return route.abort();
          await route.fulfill({json: {code: 0, ok: true, data: {themeCode, releaseId}}});
      });
      const currentColor = () => page.locator('[style*="--mall-primary:"]').first().evaluate(
          node => getComputedStyle(node).getPropertyValue('--mall-primary').trim());
      await page.goto(`${mallUrl}/#/pages/login/login`);
      await expect.poll(currentColor).toBe('#f97316');
      await page.goto(`${mallUrl}/#/pages/account/password`);
      await expect.poll(currentColor).toBe('#f97316');
      await page.goto(`${mallUrl}/#/pages-sub/mall/web`);
      await expect.poll(currentColor).toBe('#f97316');
      await page.reload();
      await expect.poll(currentColor).toBe('#f97316');
      themeCode = 'OCEAN';
      releaseId = 2;
      await page.goto(`${mallUrl}/#/pages/login/login`);
      await page.reload();
      await expect.poll(currentColor).toBe('#0284c7');
      themeCode = 'WARM';
      releaseId = 3;
      await page.reload();
      await expect.poll(currentColor).toBe('#f97316');
      themeCode = 'UNKNOWN';
      await page.reload();
      await expect.poll(currentColor).toBe('#16a34a');
      offline = true;
      await page.reload();
      await expect.poll(currentColor).toBe('#16a34a');
  });

});

// ---------------------------------------------------------------- 来源：e2e/smartadmin-native.spec.ts
test.describe('smartadmin-native', () => {
  const test = pwTest;
  /*
   * W5.5 SmartAdmin 原生功能 Playwright smoke
   * =============================================================================
   * 覆盖范围：
   *   监控服务 / 定时任务 / 缓存 / 系统配置 / 字典 / 文件 /
   *   登录日志 / 操作日志 / 文档中心 / 网络安全
   *   另加：心跳监控、单号管理、更新日志、意见反馈、Reload、数据脱敏、菜单管理
   *
   * 断言口径（对齐工作单「不能只看菜单是否出现」）：
   *   1. 页面能打开（URL 命中 + 不落到 404 页）；
   *   2. 页面无 JS 运行时错误（pageerror）；
   *   3. 关键查询接口返回 code = 0（在页面真实触发，不是直接打 API）；
   *   4. 表格容器渲染出来（说明数据链路通了，而不是白屏）。
   *
   * 账号：复用 W5 的临时管理员脚本（tools/e2e_fixtures.py）。
   * 只读：本 spec 不新增/修改/删除任何原生配置或业务数据。
   */

  /**
   * 后端地址。
   * 直接在宿主机跑时就是 127.0.0.1；在容器里跑（Docker Desktop for Windows）
   * 容器内的 127.0.0.1 指向容器自身，必须换成 host.docker.internal。
   */
  const apiUrl = process.env.W5_E2E_API_BASE || 'http://127.0.0.1:18080';
  const name = process.env.W5_E2E_NAME || 'w5_e2e_native_' + Date.now().toString(36);
  const password = process.env.W5_E2E_PASSWORD || 'Native@' + randomBytes(8).toString('hex');
  const env = { ...process.env, W5_E2E_NAME: name, W5_E2E_PASSWORD: password };

  /**
   * 账号由谁来建：
   * Playwright 可能跑在没有 python 的容器里（如 mcr.microsoft.com/playwright），
   * 此时由宿主机预先建号并通过 W5_E2E_NAME / W5_E2E_PASSWORD 注入，
   * 设置 W5_E2E_MANAGED_EXTERNALLY=1 即跳过脚本调用。
   */
  const accountManagedExternally = process.env.W5_E2E_MANAGED_EXTERNALLY === '1';
  /** 不设 python 时的兜底：只在能真正跑起来时才建号。 */
  function tryRunAccountsScript(action: 'setup' | 'cleanup') {
    const command = action === 'setup' ? 'account-setup' : 'account-cleanup';
    try {
      execFileSync('python', ['../tools/e2e_fixtures.py', command, '--wave', 'w5'], { env, stdio: 'pipe' });
    } catch (error) {
      throw new Error(
        `无法执行 tools/e2e_fixtures.py ${command}（账号 ${name}）。\n` +
          '若当前环境没有 python，请在宿主机先建号，然后带上：\n' +
          '  W5_E2E_NAME=<账号> W5_E2E_PASSWORD=<密码> W5_E2E_MANAGED_EXTERNALLY=1 npx playwright test\n' +
          `原始错误：${(error as Error).message}`,
      );
    }
  }

  let api: APIRequestContext;
  let token: string;

  // 前端把 18081 的请求直接打到 18080（axios baseURL = VITE_APP_API_URL）
  const API_ORIGIN = apiUrl;

  /** SmartAdmin 登录：captcha 明文回吐 + SM4 传输加密。 */
  async function login(account: string) {
    const client = await request.newContext({ baseURL: apiUrl });
    const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
    const source = readFileSync('src/lib/encrypt.ts', 'utf8');
    const key = /const SM4_KEY = '([^']+)'/.exec(source)?.[1];
    if (!key) throw new Error('SmartAdmin transport key not found');
    const encrypted = Buffer.from(
      smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex')),
    ).toString('base64');
    const result = await (
      await client.post('/login', {
        data: {
          loginName: account,
          password: encrypted,
          captchaUuid: captcha.captchaUuid,
          captchaCode: captcha.captchaText,
          loginDevice: 1,
        },
      })
    ).json();
    expect(result.code, 'SmartAdmin login succeeds').toBe(0);
    await client.dispose();
    return result.data.token as string;
  }

  test.beforeAll(async () => {
    if (!accountManagedExternally) {
      tryRunAccountsScript('setup');
    }
    token = await login(name);
    api = await request.newContext({
      baseURL: apiUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });
  });

  test.afterAll(async () => {
    if (api) {
      await api.get('/login/logout');
      await api.dispose();
    }
    if (!accountManagedExternally) {
      tryRunAccountsScript('cleanup');
    }
  });

  async function authenticate(page: Page) {
    await page.addInitScript(
      (v) => localStorage.setItem('smart_admin_user_token', v),
      token,
    );
  }

  /**
   * 打开一个原生页面并做统一断言。
   * @param path      前端路由（# 后面的部分）
   * @param apiPath   页面加载时会触发的关键查询接口
   * @param marker    页面特有的定位器，用于确认「不是白屏 / 不是 404」
   */
  async function openNative(
    page: Page,
    opts: {
      path: string;
      apiPath: string;
      apiMethod?: 'GET' | 'POST';
      apiBody?: unknown;
      marker?: () => ReturnType<Page['locator']>;
    },
  ) {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));

    await authenticate(page);

    // 必须先落到首页，让前端把菜单树拉回来并完成动态路由注册；
    // 直接深链到目标路由会因为 routerMap 尚未填充而命中 404。
    await page.goto('/#/home');
    await page.waitForLoadState('networkidle');

    // 动态路由就绪后再去目标页，此处才开始等待关键查询接口。
    const apiHit = page.waitForResponse(
      (r) =>
        r.url().startsWith(API_ORIGIN) &&
        r.url().includes(opts.apiPath) &&
        r.request().method() === (opts.apiMethod ?? 'POST'),
      { timeout: 20000 },
    );

    await page.goto('/#' + opts.path);

    const response = await apiHit;
    const body = await response.json();
    expect(body.code, `${opts.apiPath} 应返回 code=0，实际 ${body.code} / ${body.msg}`).toBe(0);

    // 不是 404 / 无权限页
    await expect(page).not.toHaveURL(/system\/404|system\/403|system\/forbidden/i);
    await expect(page.getByText('您访问的内容不存在')).toHaveCount(0);

    if (opts.marker) {
      // `.first()` 是必须的：这些选择器用逗号列出多个候选（表格体 / 空态 / 表单），
      // 命中多个元素时 Playwright 的 strict mode 会直接判失败。
      await expect(opts.marker().first()).toBeVisible({ timeout: 15000 });
    }

    expect(errors, `${opts.path} 不应有 JS 运行时错误`).toEqual([]);
  }

  // =============================================================================
  // SmartAdmin 正式页面
  // =============================================================================

  test('smartadmin native: 监控服务-心跳监控页面加载并成功查询', async ({ page }) => {
    await openNative(page, {
      path: '/support/heart-beat/heart-beat-list',
      apiPath: '/support/heartBeat/query',
      apiBody: {},
      marker: () => page.locator('.ant-table-tbody'),
    });
  });

  test('smartadmin native: 定时任务页面加载并成功查询', async ({ page }) => {
    await openNative(page, {
      path: '/job/list',
      apiPath: '/support/job/query',
      apiBody: {},
      marker: () => page.locator('.ant-table-tbody'),
    });
  });

  test('smartadmin native: 缓存管理页面加载并成功列举缓存名', async ({ page }) => {
    await openNative(page, {
      path: '/support/cache/cache-list',
      apiPath: '/support/cache/names',
      apiMethod: 'GET',
      marker: () => page.locator('.ant-table-tbody, .ant-table-placeholder'),
    });
  });

  test('smartadmin native: 系统配置页面加载并成功查询', async ({ page }) => {
    await openNative(page, {
      path: '/config/config-list',
      apiPath: '/support/config/query',
      apiBody: {},
      marker: () => page.locator('.ant-table-tbody'),
    });
  });

  test('smartadmin native: 数据字典页面加载并成功查询', async ({ page }) => {
    await openNative(page, {
      path: '/setting/dict',
      apiPath: '/support/dict/queryPage',
      apiBody: {},
      marker: () => page.locator('.ant-table-tbody'),
    });
  });

  test('smartadmin native: 文件管理页面加载并成功查询', async ({ page }) => {
    await openNative(page, {
      path: '/support/file/file-list',
      apiPath: '/support/file/queryPage',
      apiBody: {},
      marker: () => page.locator('.ant-table-tbody, .ant-table-placeholder'),
    });
  });

  test('smartadmin native: 登录登出记录页面加载并成功查询', async ({ page }) => {
    await openNative(page, {
      path: '/support/login-log/login-log-list',
      apiPath: '/support/loginLog/page/query',
      apiBody: {},
      marker: () => page.locator('.ant-table-tbody'),
    });
  });

  test('smartadmin native: 用户操作记录页面加载并成功查询', async ({ page }) => {
    await openNative(page, {
      path: '/support/operate-log/operate-log-list',
      apiPath: '/support/operateLog/page/query',
      apiBody: {},
      marker: () => page.locator('.ant-table-tbody'),
    });
  });

  test('smartadmin native: 文档中心页面加载并成功查询', async ({ page }) => {
    await openNative(page, {
      path: '/help-doc/help-doc-manage-list',
      apiPath: '/support/helpDoc/query',
      apiBody: {},
      marker: () => page.locator('.ant-table-tbody, .ant-table-placeholder, .help-doc-card'),
    });
  });

  test('smartadmin native: 网络安全-三级等保设置页面加载并成功读取配置', async ({ page }) => {
    await openNative(page, {
      path: '/support/level3protect/level3-protect-config-index',
      apiPath: '/support/protect/level3protect/getConfig',
      apiMethod: 'GET',
      marker: () => page.locator('form, .ant-form'),
    });
  });

  // =============================================================================
  // 其余原生菜单（同一闭环口径，避免「菜单在但页打不开」漏网）
  // =============================================================================

  test('smartadmin native: 单号管理页面加载并成功查询', async ({ page }) => {
    await openNative(page, {
      path: '/support/serial-number/serial-number-list',
      apiPath: '/support/serialNumber/all',
      apiMethod: 'GET',
      marker: () => page.locator('.ant-table-tbody'),
    });
  });

  test('smartadmin native: 更新日志页面加载并成功查询', async ({ page }) => {
    await openNative(page, {
      path: '/support/change-log/change-log-list',
      apiPath: '/support/changeLog/query',
      apiBody: {},
      marker: () => page.locator('.ant-table-tbody, .ant-table-placeholder'),
    });
  });

  test('smartadmin native: 意见反馈页面加载并成功查询', async ({ page }) => {
    await openNative(page, {
      path: '/feedback/feedback-list',
      apiPath: '/support/feedback/query',
      apiBody: {},
      marker: () => page.locator('.ant-table-tbody, .ant-table-placeholder'),
    });
  });

  test('smartadmin native: Reload 页面加载并成功读取当前 tag', async ({ page }) => {
    await openNative(page, {
      path: '/hook',
      apiPath: '/support/reload/query',
      apiMethod: 'GET',
      marker: () => page.locator('.ant-table-tbody, .ant-table-placeholder, form, .ant-form'),
    });
  });

  test('smartadmin native: 敏感数据脱敏页面加载', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await authenticate(page);
    // 同 openNative：先落首页等动态路由注册完成，再深链到目标页
    await page.goto('/#/home');
    await page.waitForLoadState('networkidle');
    await page.goto('/#/support/level3protect/data-masking-list');
    await expect(page).not.toHaveURL(/system\/404|system\/403|system\/forbidden/i);
    await expect(page.getByText('您访问的内容不存在')).toHaveCount(0);
    await expect(page.locator('.ant-table-tbody, .ant-table-placeholder, form, .ant-form').first())
      .toBeVisible({ timeout: 15000 });
    expect(errors).toEqual([]);
  });

  test('smartadmin native: 菜单管理页面加载并成功查询', async ({ page }) => {
    await openNative(page, {
      path: '/menu/list',
      apiPath: '/menu/query',
      apiMethod: 'GET',
      marker: () => page.locator('.ant-table-tbody, .ant-table-placeholder'),
    });
  });

});
