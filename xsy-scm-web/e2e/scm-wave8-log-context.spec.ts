/*
  Wave 8 操作日志业务上下文 + 列表查询条件本地记忆 E2E。

  覆盖 §12.3 Wave 8 的四件事，并且**全部用真实非管理员登录**（只读角色 / 零权限角色），
  避免「只有 SUPER_ADMIN 走过链路」这种验收口径：
    1. 商品详情的「操作日志」入口把业务类型与 ID 带进日志页，查询载荷按下钻而不是扫全表；
    2. 日志页刷新与「重置」都不丢业务上下文（重置只清普通筛选），返回行确实落在该对象上；
    3. 零权限账号按业务对象查询日志被服务端拒（30005）——前端隐藏按钮不是权限；
    4. 客户列表查询条件按登录人分别记忆：查询落键、刷新恢复、重置清除，且换人不共用同一键。

  来源：结构与登录链路照抄 `e2e/scm-customer.spec.ts`（复用同一账号脚本与 CJK 按钮处理）。
  STRPOS 精确匹配口径、脏行退化、写库前脱敏已由后端 PgIT / 前端契约测试覆盖，本 spec 只补真实页面接线。
*/
import {test, expect, request, type Page, type Locator, type APIRequestContext} from '@playwright/test';
import {randomBytes} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import smCrypto from 'sm-crypto';

const apiUrl = process.env.W8_E2E_API_BASE || 'http://127.0.0.1:18080';
const account = 'w2_e2e_' + Date.now().toString(36);
const password = 'W8@' + randomBytes(6).toString('hex');
const env = {...process.env, W2_E2E_NAME: account, W2_E2E_PASSWORD: password};
const ACCOUNT_SCRIPT = '../tools/w2_e2e_accounts.py';

// antd 在两个 CJK 字符间插入视觉空格（"查询" → "查 询"），按字符间可选空白精确匹配。
const escapeRe = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const buttonName = (text: string) => new RegExp('^' + [...text].map(escapeRe).join('\\s*') + '$');
const button = (root: Page | Locator, text: string) => root.getByRole('button', {name: buttonName(text)});

/** 真实登录：Sa-Token + SM4 传输加密 + 验证码，不能伪造 token。 */
async function login(loginName: string): Promise<string> {
  const client = await request.newContext({baseURL: apiUrl});
  const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
  const key = /const SM4_KEY = '([^']+)'/.exec(readFileSync('src/lib/encrypt.ts', 'utf8'))?.[1];
  if (!key) throw new Error('SmartAdmin transport key not found');
  const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
  const result = await (await client.post('/login', {
    data: {loginName, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1},
  })).json();
  expect(result.code, `登录 ${loginName} 应成功`).toBe(0);
  await client.dispose();
  return result.data.token as string;
}

function authenticate(page: Page, token: string) {
  return page.addInitScript((t) => localStorage.setItem('smart_admin_user_token', t), token);
}

/** 日志分页请求的载荷：上下文是否真的下钻，只能看发出去的字节。 */
function logQueryRequest(page: Page) {
  return page.waitForRequest((r) => r.url().includes('/support/operateLog/page/query') && r.method() === 'POST');
}

const queryPayload = async (page: Page) => JSON.parse((await logQueryRequest(page)).postData() ?? '{}');

let adminApi: APIRequestContext;
let adminToken: string;
let readToken: string;
/** 本用例临时建的客户（updateStatus 会往 param 里写入 customerId，才是可下钻的日志）。 */
let pilotCustomer: {customerId: number; version: number} | undefined;

async function createPilotCustomer() {
  const typeList = await (await adminApi.post('/scm/customer/type/query', {data: {pageSize: 1, pageNum: 1}})).json();
  const employee = await (await adminApi.post('/scm/employee/query', {data: {pageNum: 1, pageSize: 1}})).json();
  const form = {
    customerCode: account.toUpperCase(),
    name: account.toUpperCase() + '日志客户',
    customerTypeId: typeList.code === 0 ? typeList.data.list[0].typeId : 1,
    settleMode: 'INDEPENDENT',
    sellerId: employee.code === 0 ? employee.data.list[0].employeeId : 1,
  };
  const added = await (await adminApi.post('/scm/customer/add', {data: form})).json();
  expect(added.code, `建临时客户失败：${added.msg}`).toBe(0);
  const customerId = added.data as number;
  const status = await (await adminApi.post('/scm/customer/updateStatus', {
    data: {customerId, version: 0, status: 'COOPERATING'},
  })).json();
  expect(status.code, `改临时客户状态失败：${status.msg}`).toBe(0);
  const detail = await (await adminApi.get(`/scm/customer/detail/${customerId}`)).json();
  pilotCustomer = {customerId, version: detail.data.version};
}

test.beforeAll(async () => {
  execFileSync('python', [ACCOUNT_SCRIPT, 'setup'], {env, stdio: 'pipe'});
  adminToken = await login(account);
  adminApi = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${adminToken}`}});
  readToken = await login(account + '_read');
  await createPilotCustomer();
});

test.afterAll(async () => {
  if (adminApi && pilotCustomer) {
    await adminApi.post('/scm/customer/delete', {data: {customerId: pilotCustomer.customerId, version: pilotCustomer.version}});
  }
  await adminApi?.dispose();
  execFileSync('python', [ACCOUNT_SCRIPT, 'cleanup'], {env, stdio: 'pipe'});
});

test('商品详情「操作日志」入口带 PRODUCT 上下文下钻（只读角色）', async ({page}) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  const query = await (await adminApi.post('/scm/product/query', {data: {pageNum: 1, pageSize: 1}})).json();
  const spuId = query.code === 0 ? query.data?.list?.[0]?.spuId : undefined;
  // 缺夹具必须是**失败**而不是 skip：绿色跑过 + 场景没执行 = 把「文件存在」当成「已验收」
  expect(spuId, '当前库没有任何商品，Wave 8 下钻场景无法执行').toBeTruthy();

  await authenticate(page, readToken);
  await page.goto(`/#/product/product-detail?spuId=${spuId}`);
  await expect(button(page, '操作日志')).toBeVisible();
  const pending = queryPayload(page);
  await button(page, '操作日志').click();
  await page.waitForURL(/operate-log-list/);
  expect(page.url()).toContain('businessType=PRODUCT');
  expect(page.url()).toContain(`businessId=${spuId}`);

  const payload = await pending;
  expect(payload.businessType).toBe('PRODUCT');
  expect(payload.businessId, '业务 ID 必须以数值下钻，不能退化成文本筛选').toBe(Number(spuId));
  // 下钻视图必须自带口径说明：它不是该对象的全部历史。
  await expect(page.getByText('正在按当前业务对象查看操作记录')).toBeVisible();
  expect(errors).toEqual([]);
});

test('日志页刷新与重置都不丢业务上下文，返回行落在该客户', async ({page}) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  const url = `/#/support/operate-log/operate-log-list?businessType=CUSTOMER&businessId=${pilotCustomer!.customerId}`;

  await authenticate(page, readToken);
  await page.goto(url);
  const first = await queryPayload(page);
  expect(first.businessType).toBe('CUSTOMER');
  expect(first.businessId).toBe(pilotCustomer!.customerId);

  // 刷新：路由查询仍在，必须重新套上上下文而不是退回全表。
  const reload = queryPayload(page);
  await page.reload();
  expect((await reload).businessId).toBe(pilotCustomer!.customerId);

  // 重置：清掉普通筛选，业务上下文按当前路由重算后仍保留。
  // 本页按钮带图标（图标自带 aria-label），可及名不是纯文案，故用包含式匹配。
  await page.getByPlaceholder('模块/操作内容').fill('噪声关键字');
  const afterReset = queryPayload(page);
  await page.getByRole('button', {name: /重\s*置/}).click();
  const third = await afterReset;
  expect(third.businessType).toBe('CUSTOMER');
  expect(third.businessId).toBe(pilotCustomer!.customerId);
  expect(third.keywords, '重置必须清掉普通筛选').toBeFalsy();

  // 精确匹配的真实链路结果：临时客户 updateStatus 必然留下一条含其 ID 的日志，且返回行都含该 ID。
  const rows = await (await adminApi.post('/support/operateLog/page/query', {
    data: {pageNum: 1, pageSize: 20, businessType: 'CUSTOMER', businessId: pilotCustomer!.customerId},
  })).json();
  expect(rows.code).toBe(0);
  expect(rows.data.list.length, '刚改过状态的客户应能查到自己的操作日志').toBeGreaterThan(0);
  for (const row of rows.data.list) {
    expect(JSON.stringify(row), '返回行必须含该业务 ID').toContain(String(pilotCustomer!.customerId));
  }
  expect(errors).toEqual([]);
});

test('零权限账号按业务对象查询日志被服务端拒绝，不泄露任何行', async () => {
  const noneToken = await login(account + '_none');
  const client = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${noneToken}`}});
  const body = await (await client.post('/support/operateLog/page/query', {
    data: {pageNum: 1, pageSize: 10, businessType: 'PRODUCT', businessId: 1},
  })).json();
  expect(body.code, '无 support:operateLog:query 必须被服务端拒绝').toBe(30005);
  expect(body.data?.list ?? [], '被拒响应不得带出任何日志行').toEqual([]);
  await client.dispose();
});

test('客户列表查询条件按登录人分别记忆：查询落键、刷新恢复、重置清除', async ({page}) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  const keyword = '记忆' + Date.now().toString(36);
  const adminKeyword = '他人' + Date.now().toString(36);
  const placeholder = '编码 / 名称 / 联系人 / 电话';

  // 不拼 employeeId（登录响应不带该字段），直接枚举本页的真实偏好键：
  // 键里那段数字就是隔离用的登录用户，换人必然落到另一枚键上。
  const listed = (root: Page) => root.evaluate(() =>
    Object.keys(localStorage)
      .filter((k) => /^xsy-scm:query-filter:\d+:scm:customer:list$/.test(k))
      .map((k) => ({key: k, value: String(localStorage.getItem(k))})));

  await authenticate(page, readToken);
  await page.goto('/#/customer/customer-list');
  const input = page.getByPlaceholder(placeholder);
  await expect(input).toBeVisible();
  // 首次进入不应带着别人（或上一次）的条件。
  await expect(input).toHaveValue('');
  await input.fill(keyword);
  const queryRequest = page.waitForRequest((r) => r.url().includes('/scm/customer/query'), {timeout: 5000});
  await button(page, '查询').click();
  // 「查询」必须真的发起一次带关键字的下钻请求：曾经 a-form 无 model 时 @finish 永不触发，
  // 按钮点了什么也不做，偏好键自然也不会有。
  expect(JSON.parse((await queryRequest).postData() ?? '{}').keyword).toBe(keyword);

  const readEntries = await listed(page);
  expect(readEntries.length, '查询后应恰落一枚本页偏好键').toBe(1);
  expect(readEntries[0].value, '偏好键里必须存了本次筛选').toContain(keyword);

  // 刷新恢复：条件回到输入框。
  await page.reload();
  await expect(page.getByPlaceholder(placeholder)).toHaveValue(keyword);

  // 换登录人：管理员进入同一页面读不到只读账号的偏好；他查询后落的是另一枚键。
  const adminPage = await page.context().newPage();
  await authenticate(adminPage, adminToken);
  await adminPage.goto('/#/customer/customer-list');
  await expect(adminPage.getByPlaceholder(placeholder)).toHaveValue('');
  await adminPage.getByPlaceholder(placeholder).fill(adminKeyword);
  await button(adminPage, '查询').click();
  const adminEntries = await listed(adminPage);
  expect(adminEntries.length, '两个登录人各一枚键').toBe(2);
  const adminOwn = adminEntries.find((e) => e.value.includes(adminKeyword));
  expect(adminOwn, '管理员必须落到自己的键').toBeTruthy();
  expect(adminOwn!.key).not.toBe(readEntries[0].key);
  expect(adminOwn!.value, '他人的筛选不得写进同一键').not.toContain(keyword);
  await adminPage.close();

  // 重置即清除本人偏好，且不能顺手删掉别人的键。
  await button(page, '重置').click();
  await expect(page.getByPlaceholder(placeholder)).toHaveValue('');
  const afterReset = await listed(page);
  expect(afterReset.some((e) => e.key === readEntries[0].key), '重置必须删除本人偏好键').toBe(false);
  expect(afterReset.map((e) => e.key), '重置不得越界删除他人键').toContain(adminOwn!.key);
  expect(errors).toEqual([]);
});
