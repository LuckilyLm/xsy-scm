<!-- 关联商品可编辑多行；允许多个优先采购来源，价格用字符串。 -->
<template>
  <div class="sku-editor">
    <div class="sku-scroll">
      <table>
        <thead>
          <tr>
            <th>商品规格 *</th>
            <th>供应商货号</th>
            <th>采购单位 *</th>
            <th>参考价</th>
            <th>优先采购</th>
            <th>采购员</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(draft, index) in modelValue" :key="draft.id ?? `new-${index}`">
            <td>
              <SkuSelect
                :value="draft.skuId"
                :disabled="readonly"
                :aria-label="`第 ${index + 1} 行商品规格`"
                @update:value="(value) => (draft.skuId = Array.isArray(value) ? value[0] : value)"
              />
            </td>
            <td>
              <a-input v-model:value="draft.supplierItemCode" :maxlength="100" :disabled="readonly" :aria-label="`第 ${index + 1} 行供应商货号`" />
            </td>
            <td>
              <a-input v-model:value="draft.purchaseUnit" :maxlength="32" :disabled="readonly" :aria-label="`第 ${index + 1} 行采购单位`" />
            </td>
            <td>
              <a-input v-model:value="draft.referencePrice" placeholder="例如 3.5000" :disabled="readonly" :aria-label="`第 ${index + 1} 行参考价`" />
            </td>
            <td class="center">
              <a-checkbox v-model:checked="draft.defaultFlag" :disabled="readonly" :aria-label="`第 ${index + 1} 行设为优先采购`" />
            </td>
            <td>
              <EmployeeSelect
                :value="draft.purchaserId ?? undefined"
                width="100%"
                placeholder="请选择"
                :disabled="readonly"
                :aria-label="`第 ${index + 1} 行采购员`"
                @update:value="(value: number | undefined) => (draft.purchaserId = value ?? null)"
              />
            </td>
            <td>
              <SmartEnumSelect
                :value="draft.status ?? 'ENABLED'"
                enum-name="SUPPLIER_SKU_STATUS_ENUM"
                width="100%"
                :disabled="readonly"
                :aria-label="`第 ${index + 1} 行状态`"
                @update:value="(value: string) => (draft.status = value as EnableStatus)"
              />
            </td>
            <td class="center">
              <a-popconfirm v-if="!readonly" title="确认移除该关联？" @confirm="emit('update:modelValue', removeSkuDraft(modelValue, index))">
                <a-button type="link" danger size="small" :aria-label="`移除第 ${index + 1} 行`">移除</a-button>
              </a-popconfirm>
            </td>
          </tr>
          <tr v-if="modelValue.length === 0">
            <td colspan="8" class="empty">尚未关联任何商品；保存后将清空该供应商的全部关联</td>
          </tr>
        </tbody>
      </table>
    </div>
    <a-button v-if="!readonly" class="add-sku" @click="emit('update:modelValue', [...modelValue, emptySkuDraft()])">添加商品</a-button>
  </div>
</template>

<script setup lang="ts">
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import type {EnableStatus} from '/@/types/business/scm/supplier';
import {emptySkuDraft, removeSkuDraft} from '../supplier-form-model';
import type {SkuDraft} from '../supplier-form-model';

const props = defineProps<{modelValue: SkuDraft[]; readonly?: boolean}>();
const emit = defineEmits<{'update:modelValue': [rows: SkuDraft[]]}>();

// 显式引用 props，避免 noUnusedLocals 报警。
void props;
</script>

<style scoped>
.sku-scroll {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 8px;
  border: 1px solid var(--scm-border, #f0f0f0);
  vertical-align: middle;
  text-align: left;
}

th {
  background: var(--scm-fill, #fafafa);
  font-weight: 500;
  white-space: nowrap;
}

td:nth-child(1) {
  min-width: 260px;
}

td:nth-child(2) {
  min-width: 110px;
}

td:nth-child(3) {
  min-width: 130px;
}

td:nth-child(5) {
  min-width: 160px;
}

td:nth-child(6) {
  min-width: 110px;
}

.center {
  text-align: center;
}

.empty {
  text-align: center;
  color: var(--scm-text-secondary, #8c8c8c);
}

.ant-select {
  width: 100%;
}

.add-sku {
  margin-top: 12px;
}
</style>
