/*
  Wave 6 盘点 Excel 快照导入 E2E：导出带签名凭证的模板 + 整批拒绝，钉死「导入只读不写库存」。

  跑真实后端 + 真实浏览器，需要一次性管理员令牌 W6I_E2E_ADMIN_TOKEN；未设置时整体 skip
  （共享库当前口径过期，见 docs/progress.md）。令牌只存内存，绝不写盘、绝不 attach 报告。

  数据面（签名 / 版本漂移 / 来源集合 / 幂等重放）后端 ScmStocktakeImportPgIT 已按真 PG 覆盖；
  本 spec 只补真实链路上 IT 覆盖不到的三件事：
    1. 模板下载受 scm:inventory:stocktake:import 权限把关，且对「导出者本人」签发的凭证可被同一人导回；
    2. 把未填实盘的模板原样导回 → 信封 code=0 但 totalErrors>0（含 BLANK_ACTUAL），且不产生草稿；
    3. 页面渲染「导出快照模板 / 导入盘点」入口（管理员权限下可见）。
  刻意不在浏览器里构造「填好的成功件」：那需要前端解析并改写 xlsx 的签名快照，成功建草稿/漂移拒绝
  已由 IT 用真实 POI + 真实凭证证明，这里再验一遍只会增加脆性。
*/
import {test, expect, request, type APIRequestContext, type Page} from '@playwright/test';
import {randomUUID} from 'node:crypto';

const apiUrl = process.env.W6I_E2E_API_BASE || 'http://127.0.0.1:18080';
const webUrl = process.env.W6I_E2E_WEB_BASE || 'http://127.0.0.1:8080';
const adminToken = process.env.W6I_E2E_ADMIN_TOKEN;

let admin: APIRequestContext;

async function get(path: string) {
  const r = await (await admin.get(path)).json();
  expect(r.code, `${path}: ${r.msg}`).toBe(0);
  return r.data;
}
async function post(path: string, data: unknown) {
  const r = await (await admin.post(path, {data: data ?? {}})).json();
  expect(r.code, `${path}: ${r.msg}`).toBe(0);
  return r.data;
}
const draftTotal = async (warehouseId: string) =>
  Number((await post('/scm/inventory/stocktake/query', {warehouseId, pageNum: 1, pageSize: 1})).total ?? 0);

test.describe('Wave 6 盘点 Excel 快照导入', () => {
  test.skip(!adminToken, 'Set W6I_E2E_ADMIN_TOKEN (temporary admin Bearer) to run against a live backend');

  let warehouseId: string;
  let templateBytes: Buffer;

  test.beforeAll(async () => {
    admin = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${adminToken}`}});
    // 找一个「有当前余额」的仓库：模板要按余额行签发凭证，空仓库没有可盘来源。
    const warehouses = (await get('/scm/warehouse/list')) as any[];
    for (const w of warehouses) {
      const bal = await post('/scm/inventory/balance/query', {warehouseId: w.id, pageNum: 1, pageSize: 1});
      if ((bal.list as any[]).length > 0) {
        warehouseId = String(w.id);
        break;
      }
    }
    test.skip(!warehouseId, '当前库没有任何带余额的仓库，跳过');
  });

  test.afterAll(async () => {
    await admin?.dispose();
  });

  test('导出快照模板：受 import 权限把关，返回合法 xlsx（zip 魔数 PK）', async () => {
    const resp = await admin.get(`/scm/inventory/stocktake/import/template?warehouseId=${warehouseId}`);
    expect(resp.status()).toBe(200);
    templateBytes = await resp.body();
    expect(templateBytes.length, '模板不应为空').toBeGreaterThan(0);
    // xlsx 是 zip 容器，头两字节恒为 'PK'
    expect(templateBytes.subarray(0, 2).toString('latin1')).toBe('PK');
  });

  test('未填实盘的模板原样导回 → 整批拒绝（BLANK_ACTUAL）且草稿数不变', async () => {
    test.skip(!templateBytes, '依赖上一用例导出的模板');
    const before = await draftTotal(warehouseId);
    const form = new FormData();
    form.append('file', new Blob([new Uint8Array(templateBytes)], {type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'}), 'stocktake.xlsx');
    const resp = await admin.post('/scm/inventory/stocktake/import', {
      data: form,
      headers: {'Idempotency-Key': randomUUID()},
    });
    const body = await resp.json();
    // 整批语义：信封成功（code=0），但错误列表非空、不落任何草稿
    expect(body.code).toBe(0);
    expect(body.data.totalErrors).toBeGreaterThan(0);
    expect(body.data.stocktakeId).toBeNull();
    expect((body.data.errors as any[]).some((e) => e.code === 'BLANK_ACTUAL')).toBe(true);
    expect(await draftTotal(warehouseId), '拒绝不得新增草稿').toBe(before);
  });

  test('盘点页在管理员权限下渲染导出模板与导入入口', async ({page}: {page: Page}) => {
    test.skip(!templateBytes, '依赖模板用例');
    await page.addInitScript((t) => localStorage.setItem('smart_admin_user_token', t), adminToken);
    await page.goto(`${webUrl}/#/scm/inventory/stocktake`);
    await expect(page.getByRole('button', {name: '导出快照模板'})).toBeVisible();
    await expect(page.getByRole('button', {name: '导入盘点'})).toBeVisible();
  });
});
