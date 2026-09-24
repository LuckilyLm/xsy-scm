/*
 * E2E 临时账号与真实登录的共用实现。
 *
 * 为什么要有这个文件：`scm-stocktake-import.spec.ts` 与 `scm-customer-360.spec.ts` 原先靠
 * 外部注入的一次性管理员令牌（`W6I_E2E_ADMIN_TOKEN` / `W7_E2E_ADMIN_TOKEN`）门控，
 * 缺令牌即整组 `test.skip` —— 于是「用例文件存在」被当成了「场景已验收」，
 * 而默认 web 地址还写死成 8080（本机栈在 18081），即使注入令牌也打不开页面。
 * 这里改成与 `scm-product.spec.ts` 同款的**自给自足**链路：脚本自己建临时账号、
 * 自己走真实登录（验证码 + SM4 传输加密 + Sa-Token），令牌只活在内存里、收尾即删账号。
 *
 * 浏览器地址一律用 `page.goto('/#/...')` 相对路径，由 playwright.config 的 baseURL 提供，
 * 不在用例里再写一个可能漂移的端口。
 */
import {expect, request, type APIRequestContext, type Page} from 'playwright/test';
import {randomBytes} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import smCrypto from 'sm-crypto';

export const apiUrl = process.env.SCM_E2E_API_BASE || 'http://127.0.0.1:18080';

/** `tools/wN_e2e_accounts.py` 的波次前缀；脚本按 `<prefix>e2e_<随机>` 建管理员 + 只读 + 零角色（可选扣权）账号。 */
export type WavePrefix = 'f0' | 'p1' | 'w1' | 'w2' | 'w3' | 'w4' | 'w5' | 'w6' | 'w7' | 'w8';

export type TempAccounts = {
  /** 临时管理员 login_name（administrator_flag，菜单全开） */
  admin: string;
  password: string;
  /** 只读角色账号：页面可达、写入被服务端拒 */
  readOnly: string;
  /** 零角色账号：菜单不下发，深链落到 404 */
  noRole: string;
  /** 「只读面 − `deny`」账号：用于 AND 双权限读端点的「只缺一项也必须被拒」反例；未传 deny 时为 undefined */
  denied?: string;
  /** 第二个管理员账号：审批类用例需要「另一个人」，未传 secondAdmin 时为 undefined */
  secondAdmin?: string;
  /** 正式角色码 → login_name；只有传了 roles 才有内容。名字由服务端派生，不在这里重算 */
  roleAccounts: Record<string, string>;
  /** login_name → employee_id；数据范围夹具需要员工 id，不能只靠登录名 */
  employeeIds: Record<string, number>;
  cleanup: () => void;
};

/**
 * 建立本组用例专用的临时账号。返回的 `cleanup` 必须放进 `afterAll`：
 * 账号只写进 t_employee / t_role / t_role_menu，删掉即回到干净库。
 *
 * `roles` 传 Flyway 种下的正式角色码（如 `SCM_STOREKEEPER`）：脚本为每个角色建一个
 * `administrator_flag=false` 的账号。数据范围的正反例只能用这种账号证——只读夹具
 * 持有全部 `:query` 权限，其中就含 `scm:*:scope:all:query`，对它等于不收窄。
 *
 * `secondAdmin` 再建一个管理员账号：报损报溢 / 规格转换禁止「录单人自己审批」，
 * 同一账号既录单又审批会被 41065 挡下，审批链用例必须有第二个人。
 */
export function provisionTempAccounts(
    wave: WavePrefix,
    deny?: string[],
    roles?: string[],
    secondAdmin?: boolean,
): TempAccounts {
  const envKey = wave.toUpperCase() + '_E2E';
  const name = `${wave}_e2e_` + Date.now().toString(36);
  const password = `${wave.toUpperCase()}@` + randomBytes(6).toString('hex');
  const script = `../tools/${wave}_e2e_accounts.py`;
  const env = {
    ...process.env,
    [`${envKey}_NAME`]: name,
    [`${envKey}_PASSWORD`]: password,
    // Windows 控制台默认 GBK，不指定 UTF-8 时脚本的中文失败原因会变成乱码。
    PYTHONIOENCODING: 'utf-8',
    ...(deny?.length ? {E2E_DENY: deny.join(',')} : {}),
    ...(roles?.length ? {E2E_BUSINESS_ROLES: roles.join(',')} : {}),
    ...(secondAdmin ? {E2E_SECOND_ADMIN: '1'} : {}),
  };
  // 账号名与 employee_id 一律取脚本回显，不在两边各写一遍派生/截断规则
  const stdout = execFileSync('python', [script, 'setup'], {env, stdio: 'pipe'}).toString();
  const roleAccounts: Record<string, string> = {};
  for (const pair of /roles=(\S+)/.exec(stdout)?.[1]?.split(',') ?? []) {
    const [roleCode, loginName] = pair.split('=');
    if (roleCode && loginName) roleAccounts[roleCode] = loginName;
  }
  const employeeIds: Record<string, number> = {};
  for (const line of stdout.matchAll(/login_name=(\S+)\s+employee_id=(\d+)/g)) {
    employeeIds[line[1]] = Number(line[2]);
  }
  if (roles?.length) {
    const missing = roles.filter((role) => !roleAccounts[role]);
    if (missing.length) {
      throw new Error(`业务角色账号未建立：${missing.join(',')}（脚本输出：${stdout.trim()}）`);
    }
  }
  return {
    admin: name,
    password,
    readOnly: name + '_read',
    noRole: name + '_none',
    denied: deny?.length ? name + '_deny' : undefined,
    secondAdmin: secondAdmin ? name + '_two' : undefined,
    roleAccounts,
    employeeIds,
    cleanup: () => execFileSync('python', [script, 'cleanup'], {env, stdio: 'pipe'}),
  };
}

/** 真实登录取 Bearer：验证码 + SM4 加密口令 + Sa-Token，不伪造令牌。 */
export async function login(accounts: TempAccounts, loginName: string): Promise<string> {
  const client = await request.newContext({baseURL: apiUrl});
  try {
    const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
    const key = /const SM4_KEY = '([^']+)'/.exec(readFileSync('src/lib/encrypt.ts', 'utf8'))?.[1];
    if (!key) throw new Error('SmartAdmin transport key not found');
    const encrypted = Buffer.from(smCrypto.sm4.encrypt(accounts.password, Buffer.from(key).toString('hex'))).toString('base64');
    const result = await (await client.post('/login', {
      data: {loginName, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1},
    })).json();
    expect(result.code, `登录 ${loginName} 应成功：${result.msg}`).toBe(0);
    return result.data.token as string;
  } finally {
    await client.dispose();
  }
}

/** 带 Bearer 的 API 上下文：造数与断言都走真实鉴权链路。 */
export async function apiClient(token: string): Promise<APIRequestContext> {
  return request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${token}`}});
}

/** 令牌注入 localStorage（SmartAdmin 读取 `smart_admin_user_token`），必须在 goto 之前调用。 */
export function authenticate(page: Page, token: string) {
  return page.addInitScript((t) => localStorage.setItem('smart_admin_user_token', t), token);
}

const escapeRe = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
/** antd 在两个 CJK 字符间插视觉空格（"查询" → "查 询"），按字符间可选空白精确匹配可及名。 */
export const accessibleName = (text: string) => new RegExp('^' + [...text].map(escapeRe).join('\\s*') + '$');
