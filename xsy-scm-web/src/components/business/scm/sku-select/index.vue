<template>
  <a-select
    :value="value"
    :mode="mode"
    :style="{width}"
    :size="size"
    :placeholder="placeholder"
    show-search
    allow-clear
    :filter-option="false"
    :loading="loading"
    :disabled="disabled"
    :options="options"
    @search="search"
    @change="changed"
    @dropdown-visible-change="opened"
  >
    <template #notFoundContent
      ><span>{{ error || (loading ? '加载中…' : '暂无匹配商品规格') }}</span>
      <a-button v-if="error" type="link" size="small" @click="load('')">重试</a-button>
    </template>
    <template #dropdownRender="{menuNode}">
      <component :is="menuNode" />
      <div v-if="truncated" class="hint">结果过多，请继续输入以缩小范围</div>
    </template>
  </a-select>
</template>
<script setup lang="ts">
import {computed, onMounted, onBeforeUnmount, ref} from 'vue';
import {productSkuApi} from '/@/api/business/scm/product-sku-api';
import type {ScmId} from '/@/types/business/scm/customer';
import type {SkuOption} from '/@/types/business/scm/pricing';

/** 回显用：已有值可能不在首屏选项里，调用方补一份必要资料即可显示名称。 */
type SelectedSku = Pick<SkuOption, 'skuId' | 'productName' | 'specName'> & Partial<Pick<SkuOption, 'status' | 'spuStatus' | 'categoryStatus'>>;

const props = withDefaults(
  defineProps<{
    value?: ScmId | ScmId[] | null;
    mode?: 'multiple' | 'tags';
    placeholder?: string;
    width?: string;
    size?: string;
    status?: string | null;
    spuId?: ScmId;
    limit?: number;
    disabled?: boolean;
    disabledStatuses?: string[];
    /** 回显已选规格的名称，避免首屏未加载到时只显示 ID。 */
    selectedOptions?: SelectedSku[];
    /** 已在本表其他行使用的规格，禁止重复选择。 */
    disabledValues?: ScmId[];
  }>(),
  {
    placeholder: '请选择商品规格',
    width: '100%',
    size: 'default',
    status: null,
    limit: 50,
    disabledStatuses: () => ['OFF_SHELF'],
    selectedOptions: () => [],
    disabledValues: () => [],
  }
);
const emit = defineEmits<{
  'update:value': [value: ScmId | ScmId[] | undefined];
  change: [value: ScmId | ScmId[] | undefined];
  /** 选中项的完整商品规格资料；订单行用它回填商品名称、规格和单位，避免只存 skuId。 */
  select: [option: SkuOption | undefined];
}>();
const rows = ref<SkuOption[]>([]),
  loading = ref(false),
  truncated = ref(false),
  error = ref('');
let requestId = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
const options = computed(() => {
  const merged = new Map<string, SelectedSku>();
  for (const sku of [...props.selectedOptions, ...rows.value]) merged.set(String(sku.skuId), sku);
  return [...merged.values()].map((s) => ({
    value: s.skuId,
    label: `${s.productName} / ${s.specName}`,
    disabled:
      props.disabledValues.some((value) => String(value) === String(s.skuId)) ||
      (props.disabledStatuses.length > 0 &&
        ((s.status != null && props.disabledStatuses.includes(s.status)) ||
          (s.spuStatus != null && s.spuStatus !== 'ON_SHELF') ||
          (s.categoryStatus != null && s.categoryStatus !== 'ENABLED'))),
  }));
});

/** 同页多个下拉同时挂载时共享首屏请求，避免一行一个全量查询。 */
const initialRequests = new Map<string, Promise<{options: SkuOption[]; truncated: boolean}>>();

function loadInitial() {
  const key = `${props.status ?? ''}|${props.spuId ?? ''}|${props.limit}`;
  let request = initialRequests.get(key);
  if (!request) {
    request = productSkuApi.optionList({keyword: '', status: props.status, spuId: props.spuId, limit: props.limit}).then((response) => response.data);
    initialRequests.set(key, request);
    void request.then(
      () => initialRequests.delete(key),
      () => initialRequests.delete(key)
    );
  }
  return request;
}

async function load(keyword: string) {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const data = keyword
      ? (await productSkuApi.optionList({keyword, status: props.status, spuId: props.spuId, limit: props.limit})).data
      : await loadInitial();
    if (id === requestId) {
      const selected = new Set(Array.isArray(props.value) ? props.value : [props.value]);
      const retained = rows.value.filter((s) => selected.has(s.skuId));
      rows.value = [...data.options, ...retained.filter((s) => !data.options.some((n) => n.skuId === s.skuId))];
      truncated.value = data.truncated;
    }
  } catch {
    if (id === requestId) error.value = '加载失败';
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function search(text: string) {
  clearTimeout(timer);
  // 搜索期间清空旧选项，并提升请求序号，避免旧响应覆盖新结果
  requestId++;
  rows.value = [];
  timer = setTimeout(() => load(text), 300);
}

function changed(value: ScmId | ScmId[] | undefined) {
  emit('update:value', value);
  emit('change', value);
  const id = Array.isArray(value) ? value[0] : value;
  emit(
    'select',
    rows.value.find((option) => String(option.skuId) === String(id))
  );
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
<style scoped>
.hint {
  padding: 8px 12px;
  color: var(--scm-text-secondary);
  font-size: 12px;
}
</style>
