/*
 * W5.5 SmartAdmin 原生功能 Playwright smoke
 * =============================================================================
 * 覆盖范围（工作单 §9 明确的 11 项 + 若干必查项）：
 *   代码生成 / 监控服务 / 定时任务 / 缓存 / 系统配置 / 字典 / 文件 /
 *   登录日志 / 操作日志 / 文档中心 / 网络安全
 *   另加：心跳监控、单号管理、更新日志、意见反馈、Reload、数据脱敏、菜单管理
 *
 * 断言口径（对齐工作单「不能只看菜单是否出现」）：
 *   1. 页面能打开（URL 命中 + 不落到 404 页）；
 *   2. 页面无 JS 运行时错误（pageerror）；
 *   3. 关键查询接口返回 code = 0（在页面真实触发，不是直接打 API）；
 *   4. 表格容器渲染出来（说明数据链路通了，而不是白屏）。
 *
 * 账号：复用 W5 的临时管理员脚本（tools/e2e_accounts.py）。
 * 只读：本 spec 不新增/修改/删除任何原生配置或业务数据。
 */
import { test, expect, request, type APIRequestContext, type Page } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import smCrypto from 'sm-crypto';

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
  try {
    execFileSync('python', ['../tools/w5_e2e_accounts.py', action], { env, stdio: 'pipe' });
  } catch (error) {
    throw new Error(
      `无法执行 tools/w5_e2e_accounts.py ${action}（账号 ${name}）。\n` +
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
// 工作单 §9 点名要求的 11 项
// =============================================================================

test('smartadmin native: 代码生成页面加载并成功查询表清单', async ({ page }) => {
  await openNative(page, {
    path: '/support/code-generator',
    apiPath: '/support/codeGenerator/table/queryTableList',
    apiBody: {},
    marker: () => page.locator('.ant-table-tbody'),
  });
});

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
