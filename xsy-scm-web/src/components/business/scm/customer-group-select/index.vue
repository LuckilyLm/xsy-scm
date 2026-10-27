<!-- 客户分组下拉：只列出启用分组；分组价按分组生效。 -->
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
    <a-select-option v-for="item in groupList" :key="item.groupId" :value="item.groupId" :label="item.groupName">
      {{ item.groupName }}
    </a-select-option>
  </a-select>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue';
import {customerGroupApi} from '/@/api/business/scm/customer-api';
import {smartSentry} from '/@/lib/smart-sentry';
import type {CustomerGroupOption, ScmId} from '/@/types/business/scm/customer';

const props = withDefaults(
  defineProps<{
    value?: ScmId | ScmId[] | null;
    placeholder?: string;
    width?: string;
    size?: string;
  }>(),
  {placeholder: '请选择客户分组', width: '100%', size: 'default'}
);

const emit = defineEmits<{'update:value': [value: ScmId | undefined]; change: [value: ScmId | undefined]}>();

const groupList = ref<CustomerGroupOption[]>([]);

function filterOption(input: string, option?: {value?: ScmId | null}): boolean {
  const group = groupList.value.find((item) => String(item.groupId) === String(option?.value));
  return `${group?.groupName ?? ''} ${group?.groupCode ?? ''}`.toLowerCase().includes(input.trim().toLowerCase());
}

async function query() {
  try {
    const resp = await customerGroupApi.options();
    groupList.value = resp.data ?? [];
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
