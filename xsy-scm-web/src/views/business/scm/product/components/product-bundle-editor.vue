<template>
  <section class="bundle-editor">
    <a-space class="bundle-editor__head">
      <strong>{{ sku.specName }}</strong>
      <a-button v-privilege="'scm:product:update'" size="small" @click="open">{{ rows.length ? '修改组成' : '设置组合' }}</a-button>
    </a-space>
    <a-alert v-if="error" type="error" :message="error" show-icon />
    <span v-else-if="loading">加载组成中…</span>
    <span v-else-if="!rows.length" class="scm-note">普通商品规格</span>
    <a-table
      v-else
      :data-source="rows"
      :columns="columns"
      :row-key="(row: BundleRow) => String(row.componentSkuId)"
      size="small"
      bordered
      :pagination="false"
    />
    <a-modal :open="visible" title="组合商品组成" :confirm-loading="saving" @ok="save" @cancel="visible = false">
      <div v-for="(row, index) in draft" :key="index" class="bundle-editor__line">
        <SkuSelect v-model:value="row.skuId" :disabled-statuses="[]" />
        <a-input-number
          v-model:value="row.quantityPerBundleSaleUnit"
          string-mode
          :min="'0.0001'"
          :precision="4"
          :step="'0.0001'"
          :controls="false"
          placeholder="每套用量"
          aria-label="每套用量"
          style="width: 100%"
        />
        <a-button danger @click="draft.splice(index, 1)">移除</a-button>
      </div>
      <a-button style="margin-top: 12px" @click="draft.push({skuId: undefined, quantityPerBundleSaleUnit: '1.0000'})">添加组成规格</a-button>
    </a-modal>
  </section>
</template>
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue';
import {message, type TableColumnsType} from 'ant-design-vue';
import {productApi} from '/@/api/business/scm/product-api';
import type {ProductId, ProductSku} from '/@/types/business/scm/product';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {productError} from '../product-errors';

type BundleRow = {
  componentSkuId: ProductId;
  productName: string;
  specName: string;
  skuCode: string;
  saleUnit: string;
  quantityPerBundleSaleUnit: string;
};
type DraftRow = {skuId?: ProductId | ProductId[]; quantityPerBundleSaleUnit: string};
const props = defineProps<{sku: ProductSku}>();
const emit = defineEmits<{saved: []}>();
const rows = ref<BundleRow[]>([]),
  draft = ref<DraftRow[]>([]);
const loading = ref(false),
  saving = ref(false),
  visible = ref(false),
  error = ref('');
const columns: TableColumnsType<BundleRow> = [
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '商品规格', dataIndex: 'specName', width: 140},
  {title: '组成销售单位', dataIndex: 'saleUnit', width: 120},
  {title: '每套用量（组成销售单位）', dataIndex: 'quantityPerBundleSaleUnit', width: 220},
];
let requestId = 0;

async function load(): Promise<void> {
  if (props.sku.skuId == null) return;
  const request = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const response = await productApi.bundleComponents(props.sku.skuId);
    if (request === requestId) rows.value = response.data || [];
  } catch (cause) {
    if (request === requestId) error.value = productError(cause);
  } finally {
    if (request === requestId) loading.value = false;
  }
}

function open(): void {
  draft.value = rows.value.map((row) => ({skuId: row.componentSkuId, quantityPerBundleSaleUnit: row.quantityPerBundleSaleUnit}));
  if (!draft.value.length) draft.value.push({skuId: undefined, quantityPerBundleSaleUnit: '1.0000'});
  visible.value = true;
}

async function save(): Promise<void> {
  if (
    props.sku.skuId == null ||
    props.sku.version == null ||
    !draft.value.length ||
    draft.value.some(
      (row) =>
        row.skuId == null ||
        Array.isArray(row.skuId) ||
        !/^\d{1,14}(\.\d{1,4})?$/.test(row.quantityPerBundleSaleUnit) ||
        Number(row.quantityPerBundleSaleUnit) <= 0
    )
  ) {
    void message.warning('请填写有效组成商品规格和每套用量');
    return;
  }
  saving.value = true;
  try {
    await productApi.saveBundle({
      bundleSkuId: props.sku.skuId,
      version: props.sku.version,
      components: draft.value.map((row) => ({skuId: row.skuId as ProductId, quantityPerBundleSaleUnit: row.quantityPerBundleSaleUnit})),
    });
    visible.value = false;
    emit('saved');
    void message.success('组合商品组成已保存');
  } catch (cause) {
    void message.error(productError(cause));
  } finally {
    saving.value = false;
  }
}

watch(() => props.sku.skuId, load);
onMounted(load);
</script>
<style scoped>
.bundle-editor {
  margin-bottom: 16px;
}
.bundle-editor__head {
  margin-bottom: 8px;
}
.bundle-editor__line {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 140px auto;
  gap: 8px;
  margin-top: 12px;
}
@media (max-width: 600px) {
  .bundle-editor__line {
    grid-template-columns: 1fr;
  }
}
</style>
