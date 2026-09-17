<!--
  * 产品下拉选择框
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
    <a-select-option v-for="item in productList" :key="item.productId" :value="item.productId">
      {{ item.productName }}
      <template v-if="item.productNo"> （{{ item.productNo }}） </template>
    </a-select-option>
  </a-select>
</template>

<script setup lang="ts">
  import { onMounted, ref, watch } from 'vue';
  import { productApi } from '/@/api/business/product/product-api';
  import { smartSentry } from '/@/lib/smart-sentry';

  const props = defineProps({
    value: [Number, Array],
    placeholder: {
      type: String,
      default: '请选择产品',
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

  const productList = ref([]);
  async function query() {
    try {
      let resp = await productApi.queryAll();
      productList.value = resp.data;
    } catch (e) {
      smartSentry.captureError(e);
    }
  }
  onMounted(query);

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
