<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/components/business/sku-select/index.vue
 复制日期：2026-09-15。剪枝：SPU 分页拍平。适配：远程搜索、截断提示、竞态保护、多选与不可售禁选。验收：W1/W2/W3 E2E。 -->
<template>
  <a-select :value="value" :mode="mode" :style="{width}" :size="size" :placeholder="placeholder" show-search allow-clear
            :filter-option="false" :loading="loading" :options="options" @search="search" @change="changed"
            @dropdown-visible-change="opened">
    <template #notFoundContent><span>{{ error || (loading ? '加载中…' : '暂无匹配 SKU') }}</span>
      <a-button v-if="error" type="link" size="small" @click="load('')">重试</a-button>
    </template>
    <template #dropdownRender="{menuNode}">
      <component :is="menuNode"/>
      <div v-if="truncated" class="hint">结果过多，请继续输入以缩小范围</div>
    </template>
  </a-select>
</template>
<script setup lang="ts">
import {computed, onMounted, onBeforeUnmount, ref} from 'vue';
import {productSkuApi} from '/@/api/business/scm/product-sku-api';
import type {ScmId} from '/@/types/business/scm/customer';
import type {SkuOption} from '/@/types/business/scm/pricing';

const props = withDefaults(defineProps<{
  value?: ScmId | ScmId[] | null;
  mode?: 'multiple' | 'tags';
  placeholder?: string;
  width?: string;
  size?: string;
  status?: string | null;
  spuId?: ScmId;
  limit?: number;
  disabledStatuses?: string[]
}>(), {
  placeholder: '请选择 SKU',
  width: '100%',
  size: 'default',
  status: null,
  limit: 50,
  disabledStatuses: () => ['OFF_SHELF']
});
const emit = defineEmits<{
  'update:value': [value: ScmId | ScmId[] | undefined];
  change: [value: ScmId | ScmId[] | undefined]
}>();
const rows = ref<SkuOption[]>([]), loading = ref(false), truncated = ref(false), error = ref('');
let requestId = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
const options = computed(() => rows.value.map(s => ({
  value: s.skuId,
  label: `${s.productName} / ${s.specName}（${s.skuCode}）`,
  disabled: props.disabledStatuses.length > 0 && (props.disabledStatuses.includes(s.status) || s.spuStatus !== 'ON_SHELF' || s.categoryStatus !== 'ENABLED')
})));

async function load(keyword: string) {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await productSkuApi.optionList({keyword, status: props.status, spuId: props.spuId, limit: props.limit});
    if (id === requestId) {
      const selected = new Set(Array.isArray(props.value) ? props.value : [props.value]);
      const retained = rows.value.filter(s => selected.has(s.skuId));
      rows.value = [...r.data.options, ...retained.filter(s => !r.data.options.some(n => n.skuId === s.skuId))];
      truncated.value = r.data.truncated;
    }
  } catch {
    if (id === requestId) error.value = '加载失败';
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function search(text: string) {
  clearTimeout(timer);
  // Do not leave the previous unfiltered options clickable while the debounced
  // server search is pending; that made fast keyboard selection choose SKU #1
  // for every row in the legacy supplier flow.
  rows.value = [];
  timer = setTimeout(() => load(text), 300);
}

function changed(value: ScmId | ScmId[] | undefined) {
  emit('update:value', value);
  emit('change', value);
}

function opened(open: boolean) {
  if (open && !rows.value.length) load('');
}

onMounted(() => load(''));
onBeforeUnmount(() => {
  clearTimeout(timer);
  requestId++;
});
</script>
<style scoped>.hint {
  padding: 8px 12px;
  color: var(--ant-color-text-secondary);
  font-size: 12px;
}</style>
