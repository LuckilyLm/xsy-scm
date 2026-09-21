<template>
  <a-tree-select :value="value" :tree-data="options" show-search allow-clear tree-default-expand-all
                 tree-node-filter-prop="title" placeholder="请选择分类" :loading="loading"
                 @update:value="emit('update:value', $event)"/>
</template>
<script setup lang="ts">
import {computed} from 'vue';
import type {ProductCategory, ProductId} from '/@/types/business/scm/product';

const props = withDefaults(defineProps<{
  value?: ProductId | null;
  categories: ProductCategory[];
  mode?: 'filter' | 'product' | 'parent';
  excludeId?: ProductId;
  loading?: boolean
}>(), {mode: 'filter'});
const emit = defineEmits<{ 'update:value': [value: ProductId | undefined] }>();

interface Node {
  title: string;
  value: ProductId;
  selectable: boolean;
  disabled: boolean;
  children?: Node[]
}

const options = computed(() => {
  const convert = (rows: ProductCategory[]): Node[] => rows.filter(row => row.categoryId !== props.excludeId).map(row => ({
    title: row.name + (row.status === 'DISABLED' ? '（停用）' : ''), value: row.categoryId,
    disabled: props.mode !== 'filter' && row.status === 'DISABLED',
    selectable: props.mode === 'filter' || (props.mode === 'product' ? row.level === 3 : row.level < 3),
    children: row.children?.length ? convert(row.children) : undefined,
  }));
  return convert(props.categories);
});
</script>
