/**
 * Wave 6 盘点效率（快照模板导出 / Excel 幂等导入 / 复制到新建）前端契约单测。
 *
 * 钉死这一波最容易被日后改动悄悄破坏、且破坏后不报错的前端边界（后端导入 / 签名 / 幂等由
 * ScmStocktakeImportPgIT 覆盖）：
 * 1. 快照模板走只读 getDownload，导入走带 Idempotency-Key 的 POST，两者都不是确认（不写库存）；
 * 2. 模板与导入按钮都由独立权限 scm:inventory:stocktake:import 把关，不复用 add/update/confirm；
 * 3. 复制历史是纯前端：只读 detail + 余额 query，不引入后端「复制盘点」命令，也不自动落草稿；
 * 4. 导入成功只建草稿（回填实盘量留空、需重新清点），确认仍走既有 confirm 端点。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

/** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的名字是合规的。 */
function code(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

const API = '../src/api/business/scm/inventory-stocktake-api.ts';
const LIST = '../src/views/business/scm/inventory/inventory-stocktake-list.vue';

test('快照模板走只读 getDownload，导入走带 Idempotency-Key 的 POST，均不触达 confirm（不写库存）', () => {
  const api = code(API);
  assert.match(api, /downloadImportTemplate:[\s\S]*?getDownload\('\/scm\/inventory\/stocktake\/import\/template'/);
  assert.match(api, /url:\s*'\/scm\/inventory\/stocktake\/import'[\s\S]*?method:\s*'post'[\s\S]*?'Idempotency-Key':\s*key/);
  // 同一文件复用同一幂等键（WeakMap），换文件即新命令
  assert.match(api, /new WeakMap<File,\s*string>\(\)/);
  // 导入 / 模板端点绝不指向 /confirm —— 它们只建草稿
  assert.doesNotMatch(api, /stocktake\/import[^']*confirm/);
});

test('导出模板与导入按钮均由独立权限 scm:inventory:stocktake:import 把关', () => {
  const list = code(LIST);
  assert.match(list, /@click="onDownloadTemplate"[\s\S]*?v-privilege="'scm:inventory:stocktake:import'"/);
  assert.match(list, /:custom-request="onUploadImport"[\s\S]*?v-privilege="'scm:inventory:stocktake:import'"/);
});

test('复制历史是纯前端：只读 detail + 余额 query，不引入后端复制命令，也不自动建草稿', () => {
  const list = code(LIST);
  // 代码里的注释已被 code() 剥掉，因此用下一个函数声明作为边界，而不是注释块。
  const start = list.indexOf('async function openCopy');
  const openCopy = list.slice(start, list.indexOf('async function onDownloadTemplate', start));
  assert.ok(openCopy.length > 0 && openCopy.includes('inventoryStocktakeApi.detail('));
  assert.match(openCopy, /inventoryStocktakeApi\.detail\(/);
  assert.match(openCopy, /inventoryBalanceApi\.query\(/);
  // 复制路径不落草稿、不确认：不调用 create / confirm
  assert.doesNotMatch(openCopy, /inventoryStocktakeApi\.(create|confirm|update|delete)\(/);
  assert.match(openCopy, /已复制到新建表单（未保存）/);
  // API 层不存在后端「复制 / 克隆盘点」命令
  const api = code(API);
  assert.doesNotMatch(api, /\b(copy|duplicate|clone)\b/i);
});

test('导入成功回填实盘量留空并复用既有 confirm 端点调整库存', () => {
  const list = code(LIST);
  assert.match(list, /await inventoryStocktakeApi\.confirm\(record\.id\)/);
  // 复制过来的行强制重新清点：实盘量清空，不带入历史 / 当前值
  assert.match(list, /actualQuantity:\s*''/);
});
