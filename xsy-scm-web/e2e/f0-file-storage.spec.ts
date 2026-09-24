import { test, expect, request, type APIRequestContext, type Page } from '@playwright/test';
import {
    apiUrl,
    authenticate,
    login,
    provisionTempAccounts,
    type TempAccounts,
} from './scm-e2e-account';

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
