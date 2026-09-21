export const SHELF_STATUS_ENUM = [{value: 'ON_SHELF', label: '上架'}, {value: 'OFF_SHELF', label: '下架'}];
export const PRODUCT_TYPE_ENUM = [{value: 'STANDARD', label: '标品'}, {value: 'NON_STANDARD', label: '非标品'}];
export const ENABLE_STATUS_ENUM = [{value: 'ENABLED', label: '启用'}, {value: 'DISABLED', label: '停用'}];
/** 主档生命周期：与「是否在售」正交，DISABLED 只停止新引用，不改动在售状态。 */
export const MASTER_STATUS_ENUM = [{value: 'ENABLED', label: '可用'}, {
    value: 'DISABLED',
    label: '停止引用'
}, {value: 'ARCHIVED', label: '已归档'}];
export const MASTER_STATUS_COLOR: Record<string, string> = {ENABLED: 'green', DISABLED: 'orange', ARCHIVED: 'default'};
export const STORAGE_METHOD_ENUM = [{value: 'AMBIENT', label: '常温'}, {
    value: 'CHILLED',
    label: '冷藏'
}, {value: 'FROZEN', label: '冷冻'}];
export const UOM_CATEGORY_ENUM = [{value: 'WEIGHT', label: '重量'}, {value: 'COUNT', label: '计数'}, {
    value: 'VOLUME',
    label: '体积'
}, {value: 'LENGTH', label: '长度'}, {value: 'OTHER', label: '其他'}];
export const TAG_MODE_ENUM = [{value: 'ADD', label: '追加'}, {value: 'REMOVE', label: '移除'}, {
    value: 'REPLACE',
    label: '覆盖'
}];
/** 有/无 三态筛选：allow-clear 才是「不限」，所以不能做成 checkbox。 */
export const YES_NO_ENUM: { value: boolean; label: string }[] = [{value: true, label: '有'}, {
    value: false,
    label: '无'
}];
export const enumLabel = (options: {
    value: string;
    label: string
}[], value?: string | null) => options.find((item) => item.value === value)?.label || '—';
export const priceRange = (min: string | null, max: string | null) => min === null ? '未定价' : min === max || max === null ? `¥ ${min}` : `¥ ${min} ～ ${max}`;
