#!/usr/bin/env node
/**
 * SmartAdmin 原生功能 · 真实 PostgreSQL 连通性探针。
 *
 * 用途：把「当前 V2 保留的 SmartAdmin 原生功能」逐个打到真实后端 + 真实 PostgreSQL，
 * 用 HTTP 状态 + ResponseDTO.code 作为证据，而不是靠读代码推断。
 *
 * 做法：
 *   1. 用 psql 造一个 administrator_flag=TRUE 的临时员工（管理员自动获得全部菜单权限），
 *      密码按 EmployeeService.generateSaltPassword 的加盐规则 + Argon2 v5_8 参数生成；
 *   2. POST /login（SM4 加密口令 + 非生产环境直接返回的图形验证码）拿 token；
 *   3. 逐项调用功能端点，记录 HTTP 状态与响应码；
 *   4. GET /login/logout，再删掉临时员工。
 *
 * 依赖：后端 dev(18080) 与 PostgreSQL(15432) 同时在线。
 */
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, '..');
const WEB = resolve(REPO, 'xsy-scm-web');
const require = createRequire(resolve(WEB, 'package.json'));
const smCrypto = require('sm-crypto');

const API = process.env.SA_API ?? 'http://127.0.0.1:18080';

// 账号夹具走 Python：argon2-cffi 装在 managed venv 里（前端 node_modules 没有 argon2），
// 且这与 tools/w{1,2,3}_e2e_accounts.py 的既有做法一致。
const PYTHON = process.env.SA_PROBE_PYTHON
  ?? 'C:/Users/17757/.workbuddy-ai/binaries/python/envs/default/Scripts/python.exe';
const FIXTURE = resolve(HERE, 'smartadmin_probe_account.py');

const account = 'sa_audit_' + Date.now().toString(36);
const password = 'Sa@' + randomBytes(6).toString('hex');

function fixture(action) {
  return execFileSync(PYTHON, [FIXTURE, action], {
    text: true,
    stdio: ['ignore', 'inherit', 'inherit'],
    env: { ...process.env, SA_PROBE_NAME: account, SA_PROBE_PASSWORD: password },
  });
}

const setup = () => fixture('setup');
const cleanup = () => fixture('cleanup');

async function login() {
  const captcha = (await (await fetch(`${API}/login/getCaptcha`)).json()).data;
  const source = readFileSync(resolve(WEB, 'src/lib/encrypt.ts'), 'utf8');
  const key = /const SM4_KEY = '([^']+)'/.exec(source)?.[1];
  if (!key) throw new Error('SM4 key not found in src/lib/encrypt.ts');
  const encrypted = Buffer.from(
    smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
  const res = await fetch(`${API}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      loginName: account, password: encrypted,
      captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1,
    }),
  });
  const body = await res.json();
  if (body.code !== 0) throw new Error('login failed: ' + JSON.stringify(body));
  return body.data.token;
}

/**
 * 功能 → 端点清单。body 为 POST 的查询表单；GET 用 query（直接拼进 path）。
 * 全部是「查询型」调用：不改数据，只证明该功能能在 PostgreSQL 上跑通。
 *
 * 路径取自运行中后端的 /v3/api-docs（228 条），不是猜的。
 * 注意：SmartAdmin 把未匹配的路径交给静态资源处理器，返回 **HTTP 200 + code 10001**
 * （NoResourceFoundException），所以判定只看 `code`，不能只看 HTTP 状态码。
 */
const PROBES = [
  ['login / getLoginInfo', 'GET', '/login/getLoginInfo'],
  ['login / captcha', 'GET', '/login/getCaptcha'],
  ['employee / query', 'POST', '/employee/query', { pageNum: 1, pageSize: 10, keywords: 'a', disabledFlag: false }],
  ['employee / queryAll', 'GET', '/employee/queryAll'],
  ['employee / getByDepartment', 'GET', '/employee/getAllEmployeeByDepartmentId/1'],
  ['employee / passwordComplexity', 'GET', '/employee/getPasswordComplexityEnabled'],
  ['department / treeList', 'GET', '/department/treeList'],
  ['department / listAll', 'GET', '/department/listAll'],
  ['position / queryPage', 'POST', '/position/queryPage', { pageNum: 1, pageSize: 10 }],
  ['position / queryList', 'GET', '/position/queryList'],
  ['role / getAll', 'GET', '/role/getAll'],
  ['menu / query', 'GET', '/menu/query'],
  ['menu / tree', 'GET', '/menu/tree?onlyMenu=true'],
  ['menu / authUrl', 'GET', '/menu/auth/url'],
  ['role / dataScope list', 'GET', '/dataScope/list'],
  ['dict / getAllDict', 'GET', '/support/dict/getAllDict'],
  ['dict / getAllDictData', 'GET', '/support/dict/getAllDictData'],
  ['dict / queryPage', 'POST', '/support/dict/queryPage', { pageNum: 1, pageSize: 10 }],
  ['config / query', 'POST', '/support/config/query', { pageNum: 1, pageSize: 10, configKey: 'a' }],
  ['config / queryByKey', 'GET', '/support/config/queryByKey?configKey=super_password'],
  ['file / queryPage', 'POST', '/support/file/queryPage', { pageNum: 1, pageSize: 10 }],
  ['operateLog / query', 'POST', '/support/operateLog/page/query', { pageNum: 1, pageSize: 10, userName: 'a', requestKeywords: 'a', startDate: '2020-01-01', endDate: '2030-01-01' }],
  ['operateLog / queryLogin', 'POST', '/support/operateLog/page/query/login', { pageNum: 1, pageSize: 10 }],
  ['loginLog / query', 'POST', '/support/loginLog/page/query', { pageNum: 1, pageSize: 10, userName: 'a', startDate: '2020-01-01', endDate: '2030-01-01' }],
  ['notice / query', 'POST', '/oa/notice/query', { pageNum: 1, pageSize: 10, keywords: 'a', createTimeBegin: '2020-01-01', createTimeEnd: '2030-01-01' }],
  ['notice / employee query', 'POST', '/oa/notice/employee/query', { pageNum: 1, pageSize: 10, keywords: 'a' }],
  ['notice / employee notView', 'POST', '/oa/notice/employee/query', { pageNum: 1, pageSize: 10, keywords: 'a', notViewFlag: true }],
  ['notice / viewRecord', 'POST', '/oa/notice/employee/queryViewRecord', { pageNum: 1, pageSize: 10, noticeId: 1, keywords: 'a' }],
  ['noticeType / getAll', 'GET', '/oa/noticeType/getAll'],
  ['message / queryMyMessage', 'POST', '/support/message/queryMyMessage', { pageNum: 1, pageSize: 10, searchWord: 'a', startDate: '2020-01-01', endDate: '2030-01-01' }],
  ['message / unreadCount', 'GET', '/support/message/getUnreadCount'],
  ['job / query', 'POST', '/support/job/query', { pageNum: 1, pageSize: 10, searchWord: 'a' }],
  ['job / log query', 'POST', '/support/job/log/query', { pageNum: 1, pageSize: 10, searchWord: 'a', startTime: '2020-01-01', endTime: '2030-01-01' }],
  ['reload / query', 'GET', '/support/reload/query'],
  ['reload / result', 'GET', '/support/reload/result/xsy'],
  ['helpDoc / query', 'POST', '/support/helpDoc/query', { pageNum: 1, pageSize: 10, keywords: 'a', createTimeBegin: '2020-01-01', createTimeEnd: '2030-01-01' }],
  ['helpDoc / user queryAll', 'GET', '/support/helpDoc/user/queryAllHelpDocList'],
  ['helpDocCatalog / getAll', 'GET', '/support/helpDoc/helpDocCatalog/getAll'],
  ['codeGenerator / tableList', 'POST', '/support/codeGenerator/table/queryTableList', { pageNum: 1, pageSize: 10 }],
  ['codeGenerator / tableColumns', 'GET', '/support/codeGenerator/table/getTableColumns/t_notice'],
  ['codeGenerator / getConfig', 'GET', '/support/codeGenerator/table/getConfig/t_notice'],
  ['serialNumber / all', 'GET', '/support/serialNumber/all'],
  ['heartBeat / query', 'POST', '/support/heartBeat/query', { pageNum: 1, pageSize: 10, startDate: '2020-01-01', endDate: '2030-01-01' }],
  ['changeLog / queryPage', 'POST', '/support/changeLog/queryPage', { pageNum: 1, pageSize: 10 }],
  ['feedback / query', 'POST', '/support/feedback/query', { pageNum: 1, pageSize: 10, searchWord: 'a', startDate: '2020-01-01', endDate: '2030-01-01' }],
  ['dataTracer / query', 'POST', '/support/dataTracer/query', { pageNum: 1, pageSize: 10, dataId: 1 }],
  // tableId 是 Integer（不是表名），t_table_column 为空 → 返回空配置即算连通。
  ['tableColumn / getColumns', 'GET', '/support/tableColumn/getColumns/1'],
  ['OA enterprise / page', 'POST', '/oa/enterprise/page/query', { pageNum: 1, pageSize: 10, keywords: 'a', startTime: '2020-01-01', endTime: '2030-01-01' }],
  ['OA enterprise / list', 'GET', '/oa/enterprise/query/list'],
  ['OA bank / page', 'POST', '/oa/bank/page/query', { pageNum: 1, pageSize: 10, keywords: 'a', startTime: '2020-01-01', endTime: '2030-01-01' }],
  ['OA invoice / page', 'POST', '/oa/invoice/page/query', { pageNum: 1, pageSize: 10, keywords: 'a', startTime: '2020-01-01', endTime: '2030-01-01' }],
  ['OA enterprise employee / page', 'POST', '/oa/enterprise/employee/queryPage', { pageNum: 1, pageSize: 10, keyword: 'a', enterpriseId: 1 }],
];

async function main() {
  const results = [];
  setup();
  let token;
  try {
    token = await login();
    for (const [feature, method, path, body] of PROBES) {
      const url = method === 'GET' ? `${API}${path}` : `${API}${path}`;
      const res = await fetch(url, {
        method,
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: method === 'GET' ? undefined : JSON.stringify(body ?? {}),
      });
      let code = null;
      let msg = '';
      let rows = null;
      try {
        const j = await res.json();
        code = j.code;
        msg = j.msg ?? '';
        if (j.data && typeof j.data === 'object') {
          rows = j.data.total ?? (Array.isArray(j.data) ? j.data.length : null);
        }
      } catch { /* 非 JSON 响应 */ }
      results.push({ feature, method, path, http: res.status, code, msg, rows });
    }
    const logoutRes = await fetch(`${API}/login/logout`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const out = await logoutRes.json();
    results.push({ feature: 'login / logout', method: 'GET', path: '/login/logout', http: logoutRes.status, code: out.code, msg: out.msg ?? '', rows: null });
  } finally {
    try { cleanup(); } catch (e) { console.error('cleanup failed: ' + e.message); }
  }

  // SmartAdmin 未匹配的路径 → NoResourceFoundException，仍是 HTTP 200。
  // 这属于「探针路径写错」，必须与「后端真报错」区分开，否则会误报。
  const unmapped = r => typeof r.msg === 'string' && r.msg.includes('NoResourceFoundException');
  const failed = results.filter(r => r.code !== 0 && !unmapped(r));
  const wrongPath = results.filter(unmapped);
  console.log(`[smartadmin-probe] total=${results.length} ok=${results.length - failed.length - wrongPath.length} failed=${failed.length} unmappedPath=${wrongPath.length}`);
  for (const r of results) {
    const mark = unmapped(r) ? 'PATH' : ((r.http === 200 && r.code === 0) ? 'OK  ' : 'FAIL');
    console.log(`  ${mark} ${r.feature.padEnd(34)} http=${r.http} code=${r.code} rows=${r.rows ?? '-'} ${r.msg || ''}`);
  }
  const evidence = resolve(REPO, '.runtime/smartadmin-feature-probe.json');
  mkdirSync(dirname(evidence), { recursive: true });
  writeFileSync(evidence, JSON.stringify({ account: account.replace(/[0-9a-z]+$/, '***'), api: API, results }, null, 2));
  console.log(`\n[smartadmin-probe] evidence -> ${evidence}`);
  process.exit(failed.length ? 1 : 0);
}

main().catch(e => { console.error(e); process.exit(2); });
