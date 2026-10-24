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
    <!-- 编码只用于后端传值与搜索，不展示给用户。 -->
    <a-select-option v-for="item in visibleList" :key="item.customerId" :value="item.customerId" :label="item.name">
      {{ item.name }}
    </a-select-option>
  </a-select>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import {smartSentry} from '/@/lib/smart-sentry';
import type {CustomerOption, ScmId} from '/@/types/business/scm/customer';

const props = withDefaults(
  defineProps<{
    value?: ScmId | ScmId[] | null;
    placeholder?: string;
    width?: string;
    size?: string;
    /** 只列出指定类型编码的客户；上级客户只接受集团。 */
    typeCode?: string | null;
    /** 要排除的客户 ID；编辑时传自己，避免把自己选为上级。 */
    excludeId?: ScmId | null;
  }>(),
  {placeholder: '请选择客户', width: '100%', size: 'default', typeCode: null, excludeId: null}
);

const emit = defineEmits<{'update:value': [value: ScmId | undefined]; change: [value: ScmId | undefined]}>();

const customerList = ref<CustomerOption[]>([]);

const visibleList = computed(() =>
  customerList.value.filter((item) => {
    if (props.typeCode && item.customerTypeCode !== props.typeCode) {
      return false;
    }
    // 用 String() 比较：后端 Long 与前端可能拿到的字符串形式 ID 都要能对上。
    return props.excludeId == null || String(item.customerId) !== String(props.excludeId);
  })
);

function filterOption(input: string, option?: {value?: ScmId | null}): boolean {
  const customer = visibleList.value.find((item) => String(item.customerId) === String(option?.value));
  return `${customer?.name ?? ''} ${customer?.customerCode ?? ''}`.toLowerCase().includes(input.trim().toLowerCase());
}

async function query() {
  try {
    const resp = await customerApi.optionList();
    customerList.value = resp.data ?? [];
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
