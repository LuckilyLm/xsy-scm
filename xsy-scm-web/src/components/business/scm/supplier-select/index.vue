<template>
  <a-select
      v-model:value="selectValue"
      :style="`width: ${width}`"
      :placeholder="props.placeholder"
      :show-search="true"
      :allow-clear="true"
      :size="size"
      :filter-option="filterOption"
      @change="onChange"
  >
    <a-select-option
        v-for="item in supplierList"
        :key="item.supplierId"
        :value="item.supplierId"
        :label="item.name"
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

function filterOption(input: string, option?: {value?: ScmId | null}): boolean {
  const supplier = supplierList.value.find((item) => String(item.supplierId) === String(option?.value));
  return `${supplier?.name ?? ''} ${supplier?.supplierCode ?? ''}`.toLowerCase().includes(input.trim().toLowerCase());
}

async function query() {
  try {
    const resp = await supplierApi.optionList({suppressGlobalErrorMessage: true});
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
