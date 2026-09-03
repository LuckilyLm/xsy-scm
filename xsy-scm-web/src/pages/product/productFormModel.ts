import type {
  ProductDetail,
  ProductPayload,
  ProductType,
  ShelfStatus,
} from '../../types/product';

export interface SpecPairForm {
  key: string;
  value: string;
}

export interface ProductSkuForm {
  key: string;
  id: number | null;
  version: number | null;
  skuCode: string;
  barcode: string;
  specName: string;
  specPairs: SpecPairForm[];
  saleUnit: string;
  productType: ProductType;
  marketPrice: string;
  status: ShelfStatus;
  defaultSku: boolean;
  sortOrder: number;
}

export interface ProductForm {
  version: number | null;
  spuCode: string;
  name: string;
  alias: string;
  categoryId: number | null;
  description: string;
  status: ShelfStatus;
  skus: ProductSkuForm[];
}

function createKey() {
  return globalThis.crypto?.randomUUID?.() ?? `sku-${Date.now()}-${Math.random()}`;
}

function createEmptySku(sortOrder: number, defaultSku: boolean): ProductSkuForm {
  return {
    key: createKey(),
    id: null,
    version: null,
    skuCode: '',
    barcode: '',
    specName: defaultSku ? '默认规格' : '',
    specPairs: [],
    saleUnit: '',
    productType: 'NON_STANDARD',
    marketPrice: '0.0000',
    status: 'ON_SHELF',
    defaultSku,
    sortOrder,
  };
}

export function createEmptyProductForm(): ProductForm {
  return {
    version: null,
    spuCode: '',
    name: '',
    alias: '',
    categoryId: null,
    description: '',
    status: 'ON_SHELF',
    skus: [createEmptySku(0, true)],
  };
}

export function addSku(form: ProductForm): ProductForm {
  return {
    ...form,
    skus: [...form.skus, createEmptySku(form.skus.length, false)],
  };
}

export function removeSku(form: ProductForm, key: string): ProductForm {
  if (form.skus.length === 1) {
    throw new Error('至少保留一个 SKU');
  }
  const removed = form.skus.find((sku) => sku.key === key);
  const remaining = form.skus.filter((sku) => sku.key !== key);
  if (!removed) {
    return form;
  }
  return {
    ...form,
    skus: remaining.map((sku, index) => ({
      ...sku,
      defaultSku: removed.defaultSku ? index === 0 : sku.defaultSku,
      sortOrder: index,
    })),
  };
}

export function selectDefaultSku(form: ProductForm, key: string): ProductForm {
  return {
    ...form,
    skus: form.skus.map((sku) => ({ ...sku, defaultSku: sku.key === key })),
  };
}

export function normalizeProductPayload(form: ProductForm): ProductPayload {
  if (form.categoryId === null) {
    throw new Error('请选择三级商品分类');
  }
  return {
    version: form.version,
    spuCode: form.spuCode.trim().toUpperCase(),
    name: form.name.trim(),
    alias: form.alias.trim() || null,
    categoryId: form.categoryId,
    description: form.description.trim() || null,
    status: form.status,
    skus: form.skus.map((sku, index) => ({
      id: sku.id,
      version: sku.version,
      skuCode: sku.skuCode.trim().toUpperCase(),
      barcode: sku.barcode.trim() || null,
      specName: sku.specName.trim(),
      specValues: Object.fromEntries(
        sku.specPairs
          .map((pair) => [pair.key.trim(), pair.value.trim()] as const)
          .filter(([key, value]) => key && value),
      ),
      saleUnit: sku.saleUnit.trim(),
      productType: sku.productType,
      marketPrice: sku.marketPrice.trim(),
      status: sku.status,
      defaultSku: sku.defaultSku,
      sortOrder: index,
    })),
  };
}

export function productDetailToForm(detail: ProductDetail): ProductForm {
  return {
    version: detail.version,
    spuCode: detail.spuCode,
    name: detail.name,
    alias: detail.alias ?? '',
    categoryId: detail.categoryId,
    description: detail.description ?? '',
    status: detail.status,
    skus: detail.skus.map((sku) => ({
      key: `sku-${sku.id}`,
      id: sku.id,
      version: sku.version,
      skuCode: sku.skuCode,
      barcode: sku.barcode ?? '',
      specName: sku.specName,
      specPairs: Object.entries(sku.specValues).map(([key, value]) => ({ key, value })),
      saleUnit: sku.saleUnit,
      productType: sku.productType,
      marketPrice: sku.marketPrice,
      status: sku.status,
      defaultSku: sku.defaultSku,
      sortOrder: sku.sortOrder,
    })),
  };
}
