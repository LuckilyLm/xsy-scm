<template>
  <div class="sku-editor">
    <div class="sku-scroll"><table>
      <thead><tr><th>默认</th><th>SKU 编码 *</th><th>规格名称 *</th><th>规格属性</th><th>单位 *</th><th>市场价 *</th><th>类型</th><th>条码</th><th>状态</th><th>操作</th></tr></thead>
      <tbody><tr v-for="(sku, index) in modelValue" :key="sku.skuId ?? `new-${index}`">
        <td><a-radio :checked="sku.defaultFlag" :aria-label="`将第 ${index + 1} 个 SKU 设为默认`" @change="makeDefault(index)" /></td>
        <td><a-input v-model:value="sku.skuCode" :maxlength="64" :aria-label="`SKU ${index + 1} 编码`" /></td>
        <td><a-input v-model:value="sku.specName" :maxlength="150" :aria-label="`SKU ${index + 1} 规格名称`" /></td>
        <td>
          <div v-for="([key, value], pairIndex) in Object.entries(sku.specValues)" :key="pairIndex" class="spec-pair">
            <a-input :value="key" placeholder="属性" :aria-label="`SKU ${index + 1} 属性 ${pairIndex + 1}`" @change="renameKey(sku, key, $event.target.value ?? '')" />
            <a-input :value="value" placeholder="值" :aria-label="`SKU ${index + 1} 属性值 ${pairIndex + 1}`" @update:value="sku.specValues[key] = $event" />
            <a-button size="small" danger :aria-label="`删除 SKU ${index + 1} 属性 ${pairIndex + 1}`" @click="delete sku.specValues[key]">×</a-button>
          </div>
          <a-button type="link" size="small" @click="addAttribute(sku)">添加属性</a-button>
        </td>
        <td><a-select v-model:value="sku.saleUnit" :options="unitOptions" show-search option-filter-prop="label" placeholder="选择单位" :aria-label="`SKU ${index + 1} 单位`" /></td>
        <td><a-input-number :value="sku.marketPrice" string-mode :min="0" step="0.0001" :controls="false" :aria-label="`SKU ${index + 1} 市场价`" @update:value="sku.marketPrice = $event === null ? '' : String($event)" /></td>
        <td><a-select v-model:value="sku.productType" :options="PRODUCT_TYPE_ENUM" :aria-label="`SKU ${index + 1} 类型`" /></td>
        <td><a-input v-model:value="sku.barcode" :maxlength="64" :aria-label="`SKU ${index + 1} 条码`" /></td>
        <td><a-select v-model:value="sku.status" :options="SHELF_STATUS_ENUM" :aria-label="`SKU ${index + 1} 状态`" /></td>
        <td><a-popconfirm title="确认删除此 SKU？" :disabled="modelValue.length === 1" @confirm="emit('update:modelValue', removeSku(modelValue, index))"><a-button type="link" danger size="small" :disabled="modelValue.length === 1">删除 SKU</a-button></a-popconfirm></td>
      </tr></tbody>
    </table></div>
    <a-button class="add-sku" @click="emit('update:modelValue', [...modelValue, emptySku(modelValue.length)])">添加 SKU</a-button>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { message } from 'ant-design-vue';
import type { ProductSku, ProductUom } from '/@/types/business/scm/product';
import { PRODUCT_TYPE_ENUM, SHELF_STATUS_ENUM } from '/@/constants/business/scm/product-const';
import { emptySku, removeSku } from '../product-form-model';
const props = defineProps<{ modelValue: ProductSku[]; units: ProductUom[] }>();
const emit = defineEmits<{ 'update:modelValue': [rows: ProductSku[]] }>();
// 字典只给启用行；历史商品可能挂着已停用或字典外的单位，这些当前值要留在下拉里，否则编辑时看不出原值。
const unitOptions = computed(() => {
  const known = new Set(props.units.map((unit) => unit.name));
  const outside = [...new Set(props.modelValue.map((sku) => sku.saleUnit).filter((name) => name && !known.has(name)))];
  return [...props.units.map((unit) => ({ value: unit.name, label: unit.name })), ...outside.map((name) => ({ value: name, label: `${name}（字典外/已停用）` }))];
});
function makeDefault(index: number) { props.modelValue.forEach((row, i) => { row.defaultFlag = i === index; }); }
function addAttribute(sku: ProductSku) { let index = 1; while (`属性${index}` in sku.specValues) index++; sku.specValues[`属性${index}`] = ''; }
function renameKey(sku: ProductSku, previous: string, key: string) {
  if (key !== previous && key in sku.specValues) { message.error('属性名称不能重复'); return; }
  sku.specValues = Object.fromEntries(Object.entries(sku.specValues).map(([k, v]) => [k === previous ? key : k, v]));
}
</script>
<style scoped>
.sku-scroll { overflow-x: auto; } table { width: 100%; min-width: 1220px; border-collapse: collapse; }
th, td { padding: 8px; border: 1px solid var(--ant-color-border-secondary, #f0f0f0); vertical-align: top; text-align: left; }
th { background: var(--ant-color-fill-alter, #fafafa); font-weight: 500; white-space: nowrap; }
td:nth-child(2), td:nth-child(3), td:nth-child(8) { min-width: 135px; } td:nth-child(4) { min-width: 235px; }
td:nth-child(5) { min-width: 85px; } td:nth-child(7), td:nth-child(9) { min-width: 105px; }
.ant-select { width: 100%; } .spec-pair { display: flex; gap: 4px; margin-bottom: 6px; } .add-sku { margin-top: 12px; }
</style>
