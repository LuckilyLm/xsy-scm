<!-- 客户 / 供应商分类树选择：按 scope 各自加载，filter 模式可选任意层级，select 模式排除停用分类。 -->
<template>
  <a-tree-select
    :value="value"
    :tree-data="options"
    show-search
    allow-clear
    tree-default-expand-all
    tree-node-filter-prop="title"
    :placeholder="placeholderText"
    :loading="loading"
    :disabled="disabled"
    @update:value="emit('update:value', $event)"
  />
</template>
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue';
import {businessCategoryApi} from '/@/api/business/scm/business-category-api';
import type {BusinessCategory, BusinessCategoryScope, ScmId} from '/@/types/business/scm/business-category';

const props = withDefaults(
  defineProps<{
    value?: ScmId | null;
    scope: BusinessCategoryScope;
    mode?: 'filter' | 'select';
    excludeId?: ScmId;
    disabled?: boolean;
    placeholder?: string;
  }>(),
  {mode: 'filter'}
);
const emit = defineEmits<{'update:value': [value: ScmId | undefined]}>();

const rows = ref<BusinessCategory[]>([]);
const loading = ref(false);
let requestId = 0;

async function load() {
  const request = ++requestId;
  loading.value = true;
  try {
    const response = await businessCategoryApi.tree(props.scope, {suppressGlobalErrorMessage: true});
    if (request === requestId) rows.value = response.data ?? [];
  } catch {
    if (request === requestId) rows.value = [];
  } finally {
    if (request === requestId) loading.value = false;
  }
}

onMounted(load);
watch(() => props.scope, load);

interface Node {
  title: string;
  value: ScmId;
  selectable: boolean;
  disabled: boolean;
  children?: Node[];
}

const options = computed(() => {
  const convert = (list: BusinessCategory[]): Node[] =>
    list
      .filter((row) => row.categoryId !== props.excludeId)
      .map((row) => ({
        title: row.name + (row.status === 'DISABLED' ? '（停用）' : ''),
        value: row.categoryId,
        selectable: props.mode === 'filter' || row.status === 'ENABLED',
        disabled: props.mode === 'select' && row.status === 'DISABLED',
        children: row.children?.length ? convert(row.children) : undefined,
      }));
  return convert(rows.value);
});

const placeholderText = computed(() => props.placeholder ?? (props.mode === 'filter' ? '全部分类' : '请选择分类'));
</script>
