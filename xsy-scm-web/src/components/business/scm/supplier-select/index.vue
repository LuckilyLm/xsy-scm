<!--
  * 供应商下拉选择框
  *
  * 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/components/business/supplier-select/index.vue
  * （Copy First + Adapt）
  *
  * 适配：
  * - API 改为 `/@/api/business/scm/supplier-api`，`queryAll` → `optionList`（后端只返回 `ENABLED`）；
  * - DTO 字段 `supplierId` / `supplierName` / `supplierNo` → `supplierId` / `name` / `supplierCode`；
  * - `supplierList` 补 DTO 类型；`onChange(value)` 补显式类型；
  * - 补 `optionFilterProp="label"`；`value` 增加 `String` 支持。
-->
<template>
  <a-select
      v-model:value="selectValue"
      :style="`width: ${width}`"
      :placeholder="props.placeholder"
      :show-search="true"
      :allow-clear="true"
      :size="size"
      option-filter-prop="label"
      @change="onChange"
  >
    <a-select-option
        v-for="item in supplierList"
        :key="item.supplierId"
        :value="item.supplierId"
        :label="`${item.name}（${item.supplierCode}）`"
    >
      {{ item.name }}
      <template v-if="item.supplierCode"> （{{ item.supplierCode }}）</template>
    </a-select-option>
  </a-select>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue';
import {supplierApi} from '/@/api/business/scm/supplier-api';
import {smartSentry} from '/@/lib/smart-sentry';
import type {ScmId, SupplierOption} from '/@/types/business/scm/supplier';

const props = withDefaults(
    defineProps<{
      value?: ScmId | ScmId[] | null;
      placeholder?: string;
      width?: string;
      size?: string;
    }>(),
    {placeholder: '请选择供应商', width: '100%', size: 'default'}
);

const emit = defineEmits<{ 'update:value': [value: ScmId | undefined]; change: [value: ScmId | undefined] }>();

const supplierList = ref<SupplierOption[]>([]);

async function query() {
  try {
    const resp = await supplierApi.optionList();
    supplierList.value = resp.data ?? [];
  } catch (e) {
    smartSentry.captureError(e);
  }
}

onMounted(query);

const selectValue = ref<ScmId | ScmId[] | null | undefined>(props.value);
watch(
    () => props.value,
    (newValue) => {
      selectValue.value = newValue;
    }
);

function onChange(value: ScmId | undefined) {
  emit('update:value', value);
  emit('change', value);
}
</script>
