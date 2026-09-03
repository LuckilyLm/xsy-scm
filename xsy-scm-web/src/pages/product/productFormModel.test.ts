import {
  addSku,
  createEmptyProductForm,
  normalizeProductPayload,
  removeSku,
  selectDefaultSku,
} from './productFormModel';

describe('productFormModel', () => {
  it('creates exactly one default SKU', () => {
    const form = createEmptyProductForm();

    expect(form.skus).toHaveLength(1);
    expect(form.skus[0]).toMatchObject({
      specName: '默认规格',
      defaultSku: true,
      productType: 'NON_STANDARD',
    });
  });

  it('does not remove the final SKU', () => {
    const form = createEmptyProductForm();

    expect(() => removeSku(form, form.skus[0].key)).toThrow('至少保留一个 SKU');
  });

  it('keeps exactly one default after selecting another SKU', () => {
    const first = createEmptyProductForm();
    const form = addSku(first);

    const selected = selectDefaultSku(form, form.skus[1].key);

    expect(selected.skus.map((sku) => sku.defaultSku)).toEqual([false, true]);
  });

  it('promotes another SKU when removing the default SKU', () => {
    const first = createEmptyProductForm();
    const form = addSku(first);

    const result = removeSku(form, form.skus[0].key);

    expect(result.skus).toHaveLength(1);
    expect(result.skus[0].defaultSku).toBe(true);
  });

  it('preserves stable IDs and converts specification pairs on submit', () => {
    const form = createEmptyProductForm();
    form.version = 3;
    form.spuCode = '  veg-001 ';
    form.name = '  西红柿  ';
    form.categoryId = 30;
    form.skus[0] = {
      ...form.skus[0],
      id: 81,
      version: 2,
      skuCode: '  veg-001-jin ',
      barcode: '  ',
      saleUnit: '斤',
      marketPrice: '6.5000',
      specPairs: [
        { key: '包装', value: '散装' },
        { key: '等级', value: '一级' },
      ],
    };

    expect(normalizeProductPayload(form)).toEqual(
      expect.objectContaining({
        version: 3,
        spuCode: 'VEG-001',
        name: '西红柿',
        categoryId: 30,
        skus: [
          expect.objectContaining({
            id: 81,
            version: 2,
            skuCode: 'VEG-001-JIN',
            barcode: null,
            marketPrice: '6.5000',
            specValues: { 包装: '散装', 等级: '一级' },
          }),
        ],
      }),
    );
  });
});
