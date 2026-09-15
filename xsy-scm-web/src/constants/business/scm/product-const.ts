export const SHELF_STATUS_ENUM = [ { value: 'ON_SHELF', label: '上架' }, { value: 'OFF_SHELF', label: '下架' } ];
export const PRODUCT_TYPE_ENUM = [ { value: 'STANDARD', label: '标品' }, { value: 'NON_STANDARD', label: '非标品' } ];
export const ENABLE_STATUS_ENUM = [ { value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' } ];
export const priceRange = (min: string | null, max: string | null) => min === null ? '未定价' : min === max || max === null ? `¥ ${min}` : `¥ ${min} ～ ${max}`;
