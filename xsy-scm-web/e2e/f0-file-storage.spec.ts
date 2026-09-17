import { test, expect, request, type APIRequestContext, type Page } from '@playwright/test';

// External tester provisions temporary identities in an isolated dev database.
// No formal roles, seed permissions, or production account mutations are introduced by F0.
const apiUrl = process.env.F0_E2E_API_BASE || 'http://127.0.0.1:18080';
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5XcAAAAASUVORK5CYII=', 'base64');
const created: { fileId: number; fileKey: string }[] = [];
let admin: APIRequestContext;
let employee: APIRequestContext;
let anonymous: APIRequestContext;

test.describe('F0 cloud/MinIO file acceptance', () => {
  test.skip(process.env.XSY_FILE_STORAGE_MODE !== 'cloud', 'F0 presigned acceptance requires cloud ENV; local is regression only');

  test.beforeAll(async () => {
    if (!process.env.F0_E2E_ADMIN_TOKEN || !process.env.F0_E2E_EMPLOYEE_TOKEN) {
      throw new Error('Set temporary admin and unprivileged employee tokens in F0_E2E_ADMIN_TOKEN / F0_E2E_EMPLOYEE_TOKEN');
    }
    admin = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${process.env.F0_E2E_ADMIN_TOKEN}` } });
    employee = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${process.env.F0_E2E_EMPLOYEE_TOKEN}` } });
    anonymous = await request.newContext({ baseURL: apiUrl });
  });

  test.afterAll(async ({}, testInfo) => {
    // Native file API has no delete endpoint. Record exact fixture keys/IDs for scoped cleanup.
    // Never attach tokens, signed URLs or whole HTTP responses.
    await testInfo.attach('f0-created-file-fixtures', { body: JSON.stringify(created, null, 2), contentType: 'application/json' });
    await Promise.all([admin?.dispose(), employee?.dispose(), anonymous?.dispose()]);
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
    await page.addInitScript(token => localStorage.setItem('smart_admin_user_token', token), process.env.F0_E2E_ADMIN_TOKEN!);
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

  test('2 private image preview loads through a presigned URL and page download returns bytes', async ({ page }) => {
    const file = await upload(admin);
    await openFiles(page);
    await page.getByPlaceholder('文件Key', { exact: true }).fill(file.fileKey);
    await page.getByRole('button', { name: /查\s*询/ }).click();
    const row = page.locator('.ant-table-tbody tr').filter({ hasText: file.fileName });
    await row.getByRole('button', { name: '查看', exact: true }).click();
    const image = page.locator('.ant-image-preview-img');
    await expect(image).toBeVisible();
    await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);
    // This app renders the antd preview without a close button and Escape does not
    // dismiss it; clicking the mask is the real close affordance. Without waiting
    // for it, the preview wrap keeps intercepting pointer events on the whole page.
    await page.locator('.ant-image-preview-wrap').click({ position: { x: 20, y: 20 } });
    await expect(page.locator('.ant-image-preview-img')).toBeHidden();
    const downloaded = page.waitForResponse(r => r.url().includes('/support/file/downLoad'));
    await row.getByRole('button', { name: '下载', exact: true }).click();
    expect(await (await downloaded).body()).toEqual(png);
  });

  test('3 Notice/HelpDoc attachment APIs allow other employees but reject anonymous readers', async () => {
    for (const folder of [2, 3]) {
      const file = await upload(admin, folder);
      const params = { fileKey: file.fileKey };
      const signed = await (await employee.get('/support/file/getFileUrl', { params })).json();
      expect(signed.code).toBe(0);
      const url = new URL(signed.data);
      expect(url.searchParams.has('X-Amz-Signature')).toBe(true);
      expect((await anonymous.get(url.origin + url.pathname)).status()).toBe(403);
      expect(await (await anonymous.get(signed.data)).body()).toEqual(png);
      expect(await (await employee.get('/support/file/downLoad', { params })).body()).toEqual(png);
      const denied = await anonymous.get('/support/file/getFileUrl', { params });
      expect((await denied.json()).code).not.toBe(0);
    }
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
});
