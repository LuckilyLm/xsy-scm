/*
 * 仓库写请求体单测
 *
 * 运行方式与断言风格与 `test/customer-form-model.test.mjs` 一致
 * （`node --experimental-strip-types --test`，直接 import `.ts` 源码）。
 *
 * 只覆盖「写请求体里不该有什么」：仓库编码由服务端生成，后端 `WarehouseAddForm` /
 * `WarehouseUpdateForm` 都没有这个字段，客户端带上它只是噪音。
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {toWarehousePayload} from '../src/views/business/scm/purchase/warehouse-form-model.ts';

test('写请求体不含仓库编码：编码由服务端生成', () => {
  const payload = toWarehousePayload({warehouseCode: 'WH000001', name: '主仓'});
  // 编码只在 UI 表单模型（WarehouseFormModel）上做只读回显。
  assert.equal('warehouseCode' in payload, false);
  assert.equal(payload.name, '主仓');
});

test('写请求体保留编辑所需的 id 与 version', () => {
  const payload = toWarehousePayload({id: 3, version: 7, warehouseCode: 'WH000003', name: '分仓'});
  assert.equal(payload.id, 3);
  assert.equal(payload.version, 7);
  assert.equal('warehouseCode' in payload, false);
});
