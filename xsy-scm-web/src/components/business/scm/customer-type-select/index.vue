<!--
  * 客户类型下拉选择框
  *
  * 来源：**新写**。
  * C 没有这个组件 —— C 把客户类型做成前端硬编码枚举（`CUSTOMER_TYPE_ENUM` = 1/2/3）。
  * V2 的客户类型是可维护字典表（`customer_type`），因此必须走接口动态取数。
  * 结构照抄 C 的 `supplier-select/index.vue`。
  *
  * 后端 `/scm/customer/type/option/list` 只返回 `ENABLED` 的类型。
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
      v-for="item in typeList"
      :key="item.typeId"
      :value="item.typeId"
      :label="`${item.name}（${item.typeCode}）`"
    >
      {{ item.name }}
      <template v-if="item.typeCode"> （{{ item.typeCode }}） </template>
    </a-select-option>
  </a-select>
</template>

<script setup lang="ts">
  import { onMounted, ref, watch } from 'vue';
  import { customerTypeApi } from '/@/api/business/scm/customer-type-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import type { CustomerType, ScmId } from '/@/types/business/scm/customer';

  const props = withDefaults(
    defineProps<{
      value?: ScmId | ScmId[] | null;
      placeholder?: string;
      width?: string;
      size?: string;
    }>(),
    { placeholder: '请选择客户类型', width: '100%', size: 'default' }
  );

  const emit = defineEmits<{ 'update:value': [value: ScmId | undefined]; change: [value: ScmId | undefined] }>();

  const typeList = ref<CustomerType[]>([]);

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
