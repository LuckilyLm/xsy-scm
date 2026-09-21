<!--
  * 商品-供应商关系 可编辑表格
  *
  * 来源：**W1 派生** —— 结构照抄 `views/business/scm/product/components/product-sku-editable-table.vue`
  * （同样的「原生 table + 内联控件」手写表格，不使用 `a-table` 的可编辑单元格，
  * 因为这一层要精确控制列宽、行内控件与 aria 标签）。
  *
  * 为什么不能直接复用 C：C 的供货关系是 **SPU 级**（`product-supplier-api.ts`，
  * 「供货价 / 是否默认」挂在 SPU 上），V2 是 **SKU 级**（`supplier_sku`），
  * 两者行标识、字段与唯一性约束都不同，只借交互形态，不借数据形状。
  *
  * 与 W1 SKU 表格的关键差异（业务规则，必须守住）：
  * - **「默认来源」用 checkbox 而不是 radio** —— legacy 不变量 R12 明确允许同一供应商下
  *   存在多条 `is_default = TRUE`，做成单选是凭空发明约束；
  * - 单位、参考价是**字符串**，参考价绝不做 `Number()` 运算（后端 `ScmDecimalStrings.PATTERN`）；
  * - 规格用 `SkuSelect`，只列「SPU 与 SKU 同时上架」的规格，与后端 `40942` 判定一致。
-->
<template>
  <div class="sku-editor">
    <div class="sku-scroll">
      <table>
        <thead>
        <tr>
          <th>商品规格 *</th>
          <th>采购单位 *</th>
          <th>参考价</th>
          <th>默认来源</th>
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
                :aria-label="`第 ${index + 1} 行商品规格`"
                @update:value="(value) => (draft.skuId = Array.isArray(value) ? value[0] : value)"
            />
          </td>
          <td>
            <a-input v-model:value="draft.purchaseUnit" :maxlength="32" :aria-label="`第 ${index + 1} 行采购单位`"/>
          </td>
          <td>
            <a-input
                v-model:value="draft.referencePrice"
                placeholder="例如 3.5000"
                :aria-label="`第 ${index + 1} 行参考价`"
            />
          </td>
          <td class="center">
            <a-checkbox
                v-model:checked="draft.defaultFlag"
                :aria-label="`第 ${index + 1} 行设为默认来源`"
            />
          </td>
          <td>
            <EmployeeSelect
                :value="draft.purchaserId ?? undefined"
                width="100%"
                placeholder="请选择"
                :aria-label="`第 ${index + 1} 行采购员`"
                @update:value="(value: number | undefined) => (draft.purchaserId = value ?? null)"
            />
          </td>
          <td>
            <SmartEnumSelect
                :value="draft.status ?? 'ENABLED'"
                enum-name="SUPPLIER_SKU_STATUS_ENUM"
                width="100%"
                :aria-label="`第 ${index + 1} 行状态`"
                @update:value="(value: string) => (draft.status = value as EnableStatus)"
            />
          </td>
          <td class="center">
            <a-popconfirm title="确认移除该关联？"
                          @confirm="emit('update:modelValue', removeSkuDraft(modelValue, index))">
              <a-button type="link" danger size="small" :aria-label="`移除第 ${index + 1} 行`">移除</a-button>
            </a-popconfirm>
          </td>
        </tr>
        <tr v-if="modelValue.length === 0">
          <td colspan="7" class="empty">尚未关联任何商品；保存后将清空该供应商的全部关联</td>
        </tr>
        </tbody>
      </table>
    </div>
    <a-button class="add-sku" @click="emit('update:modelValue', [...modelValue, emptySkuDraft()])">添加商品</a-button>
    <div class="hint">同一供应商可以同时有多条「默认来源」，这不是配置错误。</div>
  </div>
</template>

<script setup lang="ts">
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import type {EnableStatus} from '/@/types/business/scm/supplier';
import {emptySkuDraft, removeSkuDraft} from '../supplier-form-model';
import type {SkuDraft} from '../supplier-form-model';

const props = defineProps<{ modelValue: SkuDraft[] }>();
const emit = defineEmits<{ 'update:modelValue': [rows: SkuDraft[]] }>();

// 模板里 `modelValue` 直接引用 props（与 W1 product-sku-editable-table 同写法）；
// 这里显式引用一次，避免 `noUnusedLocals` 把 props 变量判为未使用。
void props;
</script>

<style scoped>
.sku-scroll {
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 1080px;
  border-collapse: collapse;
}

th,
td {
  padding: 8px;
  border: 1px solid var(--ant-color-border-secondary, #f0f0f0);
  vertical-align: top;
  text-align: left;
}

th {
  background: var(--ant-color-fill-alter, #fafafa);
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
  color: var(--ant-color-text-secondary, #8c8c8c);
}

.ant-select {
  width: 100%;
}

.add-sku {
  margin-top: 12px;
}

.hint {
  margin-top: 8px;
  color: var(--ant-color-text-secondary, #8c8c8c);
  font-size: 12px;
}
</style>

