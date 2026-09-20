import { test } from 'node:test';
import assert from 'node:assert/strict';
import { emptyProduct, emptySku, productFormOf, removeSku, validateProduct } from '../src/views/business/scm/product/product-form-model.ts';
test('new product starts with one default SKU and zero price stays explicit', () => {
  const form = emptyProduct(); assert.equal(form.skuList.length, 1); assert.equal(form.skuList[0].defaultFlag, true); assert.equal(form.skuList[0].marketPrice, '0.0000');
});
test('PCO-1 master fields start at the backend defaults', () => {
  const form = emptyProduct();
  // tagIds 与 taxExempt 是新增接口的必填项，缺省值必须能直接提交。
  assert.deepEqual(form.tagIds, []); assert.equal(form.taxExempt, false); assert.equal(form.masterStatus, 'ENABLED');
  assert.equal(form.shelfLifeDays, null); assert.equal(form.lossRate, null); assert.equal(form.taxRate, null);
});
test('detail VO maps to the edit form: tags become tagIds and summary fields stay out', () => {
  const detail = { ...emptyProduct(), spuId: 11, version: 3, masterStatus: 'DISABLED', skuList: [emptySku(0, true)], tags: [{ tagId: 5, tagCode: 'T', name: '有机', status: 'DISABLED' }], skuCount: 2, categoryPath: '生鲜 / 蔬菜', minMarketPrice: '1.0000' };
  const form = productFormOf(detail);
  // 编辑接口按 tagIds 整批替换：漏映射等于把这个商品的标签清空。
  assert.deepEqual(form.tagIds, [5]); assert.equal(form.masterStatus, 'DISABLED'); assert.equal(form.version, 3);
  assert.equal(form.skuCount, undefined); assert.equal(form.categoryPath, undefined); assert.equal(form.tags, undefined);
  // SKU 深拷贝：弹窗里改数量不能写回列表页已缓存的行对象。
  assert.deepEqual(form.skuList, detail.skuList); assert.notEqual(form.skuList[0], detail.skuList[0]);
});
test('archived product cannot stay on shelf in the form model either', () => {
  const form = emptyProduct(); Object.assign(form.skuList[0], { skuCode: 'A', specName: '大', saleUnit: 'kg' });
  form.masterStatus = 'ARCHIVED'; assert.match(validateProduct(form), /归档/);
  form.status = 'OFF_SHELF'; assert.equal(validateProduct(form), undefined);
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
