<!--
  * 商品规格(SKU)下拉选择框
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
    <a-select-option v-for="item in skuList" :key="item.skuId" :value="item.skuId">
      {{ item.specName }}
      <template v-if="item.skuNo"> （{{ item.skuNo }}） </template>
    </a-select-option>
  </a-select>
</template>

<script setup lang="ts">
  import { onMounted, ref, watch } from 'vue';
  import { productSkuApi } from '/@/api/business/product/product-sku-api';
  import { smartSentry } from '/@/lib/smart-sentry';

  const props = defineProps({
    value: [Number, Array],
    placeholder: {
      type: String,
      default: '请选择规格',
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

  const skuList = ref([]);
  async function query() {
    try {
      let resp = await productSkuApi.queryAll();
      skuList.value = resp.data;
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
