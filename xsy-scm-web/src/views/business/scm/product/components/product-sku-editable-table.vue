<template>
  <div class="sku-editor">
    <div v-for="(sku, index) in modelValue" :key="sku.skuId ?? `new-${index}`" class="scm-form-section scm-form-section--nested sku-card">
      <div class="sku-card__head">
        <a-radio :checked="sku.defaultFlag" :aria-label="`将第 ${index + 1} 个商品规格设为默认商品规格`"
                 @change="makeDefault(index)">默认</a-radio>
        <span class="sku-card__title">商品规格 {{ index + 1 }}</span>
        <span v-if="sku.specName" class="sku-card__subtitle">· {{ sku.specName }}</span>
        <a-popconfirm title="确认删除此商品规格？" :disabled="modelValue.length === 1"
                      @confirm="emit('update:modelValue', removeSku(modelValue, index))">
          <a-button size="small" class="scm-btn-icon-danger sku-card__remove"
                    :aria-label="`删除第 ${index + 1} 个商品规格`" :disabled="modelValue.length === 1">
            <DeleteOutlined/>
          </a-button>
        </a-popconfirm>
      </div>
      <a-row :gutter="16">
        <a-col :xs="24" :sm="12">
          <a-form-item label="商品规格编码" required class="sku-field">
            <a-input v-model:value="sku.skuCode" :maxlength="64" :aria-label="`第 ${index + 1} 个商品规格编码`"/>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12">
          <a-form-item label="商品规格" required class="sku-field">
            <a-input v-model:value="sku.specName" :maxlength="150" :aria-label="`第 ${index + 1} 个商品规格名称`"/>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12">
          <a-form-item label="单位" required class="sku-field">
            <a-select v-model:value="sku.saleUnit" :options="unitOptions" show-search option-filter-prop="label"
                      placeholder="选择单位" :aria-label="`第 ${index + 1} 个商品规格的单位`"/>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12">
          <a-form-item label="市场价" required class="sku-field">
            <a-input-number :value="sku.marketPrice" string-mode :min="0" step="0.0001" :controls="false"
                            addon-before="¥" style="width: 100%" :aria-label="`第 ${index + 1} 个商品规格的市场价`"
                            @update:value="sku.marketPrice = $event === null ? '' : String($event)"/>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12">
          <a-form-item label="类型" class="sku-field">
            <a-select v-model:value="sku.productType" :options="PRODUCT_TYPE_ENUM"
                      :aria-label="`第 ${index + 1} 个商品规格的类型`"/>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12">
          <a-form-item label="条码" class="sku-field">
            <a-input v-model:value="sku.barcode" :maxlength="64" :aria-label="`第 ${index + 1} 个商品规格的条码`"/>
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="12">
          <a-form-item label="状态" class="sku-field">
            <a-select v-model:value="sku.status" :options="SHELF_STATUS_ENUM"
                      :aria-label="`第 ${index + 1} 个商品规格的状态`"/>
          </a-form-item>
        </a-col>
        <a-col :span="24">
          <a-form-item label="规格值" class="sku-field">
            <div v-for="([key, value], pairIndex) in Object.entries(sku.specValues)" :key="pairIndex"
                 class="spec-pair">
              <a-input :value="key" placeholder="规格项名称" :aria-label="`第 ${index + 1} 个商品规格的第 ${pairIndex + 1} 个规格项名称`"
                       @change="renameKey(sku, key, $event.target.value ?? '')"/>
              <a-input :value="value" placeholder="规格值" :aria-label="`第 ${index + 1} 个商品规格的第 ${pairIndex + 1} 个规格值`"
                       @update:value="sku.specValues[key] = $event"/>
              <a-button class="scm-btn-icon-danger spec-pair__remove"
                        :aria-label="`删除第 ${index + 1} 个商品规格的第 ${pairIndex + 1} 个规格项`"
                        @click="delete sku.specValues[key]">
                <DeleteOutlined/>
              </a-button>
            </div>
            <a-button size="small" class="scm-btn-tertiary" @click="addAttribute(sku)">
              <PlusOutlined/>
              添加规格项
            </a-button>
          </a-form-item>
        </a-col>
      </a-row>
    </div>
    <a-button class="scm-btn-secondary scm-btn-add-row" block
              @click="emit('update:modelValue', [...modelValue, emptySku(modelValue.length)])">
      <PlusOutlined/>
      新增商品规格
    </a-button>
  </div>
</template>
<script setup lang="ts">
import {computed} from 'vue';
import {message} from 'ant-design-vue';
import {DeleteOutlined, PlusOutlined} from '@ant-design/icons-vue';
import type {ProductSku, ProductUom} from '/@/types/business/scm/product';
import {PRODUCT_TYPE_ENUM, SHELF_STATUS_ENUM} from '/@/constants/business/scm/product-const';
import {emptySku, removeSku} from '../product-form-model';

const props = defineProps<{ modelValue: ProductSku[]; units: ProductUom[] }>();
const emit = defineEmits<{ 'update:modelValue': [rows: ProductSku[]] }>();
// 字典只给启用行；历史商品可能挂着已停用或字典外的单位，这些当前值要留在下拉里，否则编辑时看不出原值。
const unitOptions = computed(() => {
  const known = new Set(props.units.map((unit) => unit.name));
  const outside = [...new Set(props.modelValue.map((sku) => sku.saleUnit).filter((name) => name && !known.has(name)))];
  return [...props.units.map((unit) => ({value: unit.name, label: unit.name})), ...outside.map((name) => ({
    value: name,
    label: `${name}（字典外/已停用）`
  }))];
});
function makeDefault(index: number) {
  props.modelValue.forEach((row, i) => {
    row.defaultFlag = i === index;
  });
}

function addAttribute(sku: ProductSku) {
  let index = 1;
  while (`属性${index}` in sku.specValues) index++;
  sku.specValues[`属性${index}`] = '';
}

function renameKey(sku: ProductSku, previous: string, key: string) {
  if (key !== previous && key in sku.specValues) {
    message.error('规格项名称不能重复');
    return;
  }
  sku.specValues = Object.fromEntries(Object.entries(sku.specValues).map(([k, v]) => [k === previous ? key : k, v]));
}
</script>
<style scoped>
.sku-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 12px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--ant-color-border-secondary, #f0f0f0);
}

.sku-card__title {
  font-weight: 600;
  color: var(--ant-color-text, rgba(0, 0, 0, 0.88));
}

.sku-card__subtitle {
  font-size: 13px;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--ant-color-text-secondary, rgba(0, 0, 0, 0.45));
}

.sku-card__remove {
  flex: none;
  margin-left: auto;
}

.spec-pair {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.spec-pair__remove {
  flex: none;
  width: 32px;
  height: 32px;
  padding: 0;
}
</style>
