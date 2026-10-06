<!--
  仓库选择器。
  端点：`GET /scm/warehouse/list`（只返回启用仓库；权限 `scm:warehouse:query`）。
  `options` 由调用方传入时不再自己拉取 —— 调用方本来就要拉一次，传进来避免同一请求发两次。
-->
<template>
  <a-select
      v-model:value="selectValue"
      :style="`width: ${width}`"
      :placeholder="props.placeholder"
      :show-search="true"
      :allow-clear="true"
      :size="props.size"
      :disabled="props.disabled"
      option-filter-prop="label"
      @change="onChange"
  >
    <a-select-option
        v-for="item in list"
        :key="item.id"
        :value="item.id"
        :label="`${item.name}（${item.warehouseCode}）`"
    >
      {{ item.name }}
      <template v-if="item.warehouseCode"> （{{ item.warehouseCode }}）</template>
    </a-select-option>
  </a-select>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {smartSentry} from '/@/lib/smart-sentry';
import type {Warehouse} from '/@/views/business/scm/purchase/purchase-types';

const props = withDefaults(
    defineProps<{
      value?: string | number | null;
      /** 由调用方提供的仓库列表；不传时组件自己拉取 `GET /scm/warehouse/list`。 */
      options?: Warehouse[] | null;
      placeholder?: string;
      width?: string;
      size?: string;
      disabled?: boolean;
    }>(),
    {placeholder: '请选择仓库', width: '100%', size: 'default', disabled: false, options: null}
);

const emit = defineEmits<{
  'update:value': [value: string | number | undefined];
  change: [value: string | number | undefined];
}>();

const fetched = ref<Warehouse[]>([]);
const list = computed<Warehouse[]>(() => props.options ?? fetched.value);

async function query() {
  try {
    const resp = await warehouseApi.list({suppressGlobalErrorMessage: true});
    fetched.value = resp.data ?? [];
  } catch (e) {
    // 没有 `scm:warehouse:query` 时静默降级：仓库筛选只是便利，不该让整页失败
    smartSentry.captureError(e);
  }
}

onMounted(() => {
  if (props.options === null) {
    query();
  }
});

const selectValue = ref<string | number | null | undefined>(props.value);
watch(
    () => props.value,
    (newValue) => {
      selectValue.value = newValue;
    }
);

function onChange(value: string | number | undefined) {
  emit('update:value', value);
  emit('change', value);
}
</script>
