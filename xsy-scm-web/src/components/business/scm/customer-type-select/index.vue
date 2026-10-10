<!-- 后端 `/scm/customer/type/option/list` 只返回 `ENABLED` 的类型。 -->
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
        v-for="item in typeList"
        :key="item.typeId"
        :value="item.typeId"
        :label="item.name"
    >
      {{ item.name }}
      <template v-if="item.typeCode"> （{{ item.typeCode }}）</template>
    </a-select-option>
  </a-select>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue';
import {customerTypeApi} from '/@/api/business/scm/customer-type-api';
import {smartSentry} from '/@/lib/smart-sentry';
import type {CustomerType, ScmId} from '/@/types/business/scm/customer';

const props = withDefaults(
    defineProps<{
      value?: ScmId | ScmId[] | null;
      placeholder?: string;
      width?: string;
      size?: string;
    }>(),
    {placeholder: '请选择客户类型', width: '100%', size: 'default'}
);

const emit = defineEmits<{ 'update:value': [value: ScmId | undefined]; change: [value: ScmId | undefined] }>();

const typeList = ref<CustomerType[]>([]);

function filterOption(input: string, option?: {value?: ScmId | null}): boolean {
  const customerType = typeList.value.find((item) => String(item.typeId) === String(option?.value));
  return `${customerType?.name ?? ''} ${customerType?.typeCode ?? ''}`.toLowerCase().includes(input.trim().toLowerCase());
}

async function query() {
  try {
    const resp = await customerTypeApi.optionList();
    typeList.value = resp.data ?? [];
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
