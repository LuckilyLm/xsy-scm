<template>
  <a-tree-select
    v-model:value="selectValue"
    :style="`width:${width}`"
    :dropdown-style="{ maxHeight: '400px', overflowX: 'auto' }"
    :tree-data="categoryTreeData"
    :placeholder="placeholder"
    :allowClear="true"
    :showSearch="true"
    tree-node-filter-prop="title"
    tree-default-expand-all
    @change="onChange"
  />
</template>

<script setup lang="ts">
  import { ref, watch, onMounted } from 'vue';
  import { productCategoryApi } from '/@/api/business/product/product-category-api';
  import { smartSentry } from '/@/lib/smart-sentry';

  const props = defineProps({
    value: Number,
    placeholder: {
      type: String,
      default: '请选择产品分类',
    },
    width: {
      type: String,
      default: '100%',
    },
  });

  const emit = defineEmits(['update:value', 'change']);

  // 查询产品分类树
  const categoryTreeData = ref([]);
  async function queryCategoryTree() {
    try {
      let resp = await productCategoryApi.queryTree({});
      categoryTreeData.value = resp.data;
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  // 选中相关监听、事件
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

  onMounted(queryCategoryTree);
</script>
