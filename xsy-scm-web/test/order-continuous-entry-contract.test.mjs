import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8');
const drawer = read('../src/views/business/scm/order/components/order-form-drawer.vue');
const model = read('../src/views/business/scm/order/order-form-model.ts');

test('连续录单复用原 create-and-progress 命令，不新增隐蔽的自动确认、复制或批量写入端点', () => {
  assert.match(drawer, /创建并继续录单/);
  assert.match(drawer, /save\(false, true\)/);
  assert.match(drawer, /orderApi\.createAndProgress\(f\)/);
  assert.match(drawer, /isNew && !exchangeReturnId\.value && !draft && continueCreating && visible\.value/);
  assert.doesNotMatch(drawer, /orderApi\.copy\(|orderApi\.clone\(|orderApi\.batchCreate\(/);
});

test('只有创建成功才清草稿和继续；后续客户地址从服务端重取且失败时清空客户', () => {
  assert.match(drawer, /if \(isNew && !exchangeReturnId\.value\) clearDraft\(\)/);
  assert.match(drawer, /await continueWithNextOrder\(f\)/);
  assert.match(drawer, /nextOrderForCustomer\(previous\)/);
  assert.match(drawer, /customerApi\.detail\(form\.value\.customerId/);
  assert.match(drawer, /form\.value\.customerId = undefined/);
  assert.match(drawer, /if \(token !== requestId \|\| !visible\.value\) return/);
  assert.match(drawer, /if \(saving\.value \|\| loading\.value\) return/);
  assert.match(model, /nextOrderForCustomer/);
  assert.doesNotMatch(model.match(/export function nextOrderForCustomer[\s\S]+?\n\}/)?.[0] || '',
    /items\s*=|lockedUnitPrice|manualPriceOverride|originalOrderId|orderNo|status/);
});
