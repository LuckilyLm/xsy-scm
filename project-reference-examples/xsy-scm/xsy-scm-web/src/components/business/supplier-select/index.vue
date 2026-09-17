<!--
  * 供应商下拉选择框
  *
  * @author xsy-scm
-->
<template>
  <a-select
    v-model:value="selectValue"
    :style="`width: ${width}`"
    :placeholder="props.placeholder"
    :showSearch="true"
    :allowClear="true"
    :size="size"
    @change="onChange"
  >
    <a-select-option v-for="item in supplierList" :key="item.supplierId" :value="item.supplierId">
      {{ item.supplierName }}
      <template v-if="item.supplierNo"> （{{ item.supplierNo }}） </template>
    </a-select-option>
  </a-select>
</template>

<script setup lang="ts">
  import { onMounted, ref, watch } from 'vue';
  import { supplierApi } from '/@/api/business/purchase/supplier-api';
  import { smartSentry } from '/@/lib/smart-sentry';

  // =========== 属性定义 和 事件方法暴露 =============

  const props = defineProps({
    value: [Number, Array],
    placeholder: {
      type: String,
      default: '请选择供应商',
    },
    width: {
      type: String,
      default: '100%',
    },
    size: {
      type: String,
      default: 'default',
    },
  });

  const emit = defineEmits(['update:value', 'change']);

  // =========== 查询数据 =============

  const supplierList = ref([]);
  async function query() {
    try {
      let resp = await supplierApi.queryAll();
      supplierList.value = resp.data;
    } catch (e) {
      smartSentry.captureError(e);
    }
  }
  onMounted(query);

  // =========== 选择 监听、事件 =============

  const selectValue = ref(props.value);
  watch(
    () => props.value,
    (newValue) => {
      selectValue.value = newValue;
    }
  );

  function onChange(value) {
    emit('update:value', value);
    emit('change', value);
  }
</script>
