import { test } from 'node:test';
import assert from 'node:assert/strict';
import { emptyProduct, emptySku, removeSku, validateProduct } from '../src/views/business/scm/product/product-form-model.ts';
test('new product starts with one default SKU and zero price stays explicit', () => {
  const form = emptyProduct(); assert.equal(form.skuList.length, 1); assert.equal(form.skuList[0].defaultFlag, true); assert.equal(form.skuList[0].marketPrice, '0.0000');
});
test('last SKU cannot be removed; removing default selects first survivor and keeps identities', () => {
  assert.throws(() => removeSku(emptyProduct().skuList, 0));
  const rows = [{ ...emptySku(0, true), skuId: 7 }, { ...emptySku(1), skuId: 8 }, { ...emptySku(2), skuId: 9 }];
  const saved = removeSku(rows, 0); assert.deepEqual(saved.map(s => [s.skuId, s.defaultFlag]), [[8, true], [9, false]]);
});
test('validates four decimal prices and normalized duplicate specifications', () => {
  const form = emptyProduct(); Object.assign(form.skuList[0], { skuCode: 'A', specName: '大', saleUnit: 'kg', specValues: { Size: ' Large ' } });
  assert.equal(validateProduct(form), undefined);
  form.skuList[0].marketPrice = '1.23456'; assert.match(validateProduct(form), /四位/);
  form.skuList[0].marketPrice = '0.0000'; form.skuList.push({ ...form.skuList[0], skuCode: 'B', defaultFlag: false, specValues: { size: 'large' } });
  assert.match(validateProduct(form), /规格组合重复/);
});
