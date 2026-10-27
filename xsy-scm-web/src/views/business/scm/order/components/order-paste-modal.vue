<template>
  <a-modal :open="open" title="整单粘贴" :width="960" :mask-closable="false" destroy-on-close @cancel="close">
    <a-alert
      type="info"
      show-icon
      message="从 Excel / 送货单复制多行，一次追加到当前客户订单"
      description="有表头时按「商品名称 / 规格 / 单位 / 数量(实数量) / 销售单价 / 备注」识别；没有表头时按送货单顺序「商品名称 / 单位 / 规格 / 实数量 / 销售单价 / 销售金额 / 备注」读取，首列序号可有可无。单价留空按系统定价。"
    />
    <a-textarea
      v-model:value="text"
      class="paste-input"
      :rows="7"
      placeholder="在这里粘贴，例如：&#10;上海青&#9;斤&#9;散装&#9;20&#9;&#9;&#10;土豆&#9;斤&#9;大&#9;10&#9;3.5&#9;加急"
    />
    <div class="paste-actions">
      <a-button type="primary" :loading="loading" :disabled="!rows.length" @click="preview">解析预览</a-button>
      <a-button @click="reset">清空</a-button>
      <span class="paste-count">{{ rows.length }} 行</span>
    </div>
    <a-alert v-if="parseError" type="error" show-icon :message="parseError" class="paste-alert" />
    <a-table
      v-if="previewed"
      size="small"
      bordered
      :data-source="previewRows"
      :columns="columns"
      :pagination="false"
      :scroll="{x: 860}"
      row-key="rowNumber"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'rowNumber'">{{ record.rowNumber }}</template>
        <template v-else-if="column.dataIndex === 'product'">
          <div>{{ record.matchedProductName || record.productName || '—' }}</div>
          <div class="paste-sub">{{ record.matchedSpecName || record.specName || '' }}</div>
        </template>
        <template v-else-if="column.dataIndex === 'quantity'"> {{ record.quantity }} {{ record.saleUnit || record.unit || '' }} </template>
        <template v-else-if="column.dataIndex === 'price'">
          {{ record.manualPrice ? record.rawUnitPrice : amount(record.resolvedUnitPrice, true) }}
        </template>
        <template v-else-if="column.dataIndex === 'result'">
          <a-tag v-if="record.matched" color="green">可追加</a-tag>
          <a-tag v-else color="red">需修正</a-tag>
          <div v-for="error in record.errors" :key="error.code + error.column" class="paste-error">{{ error.column }}：{{ error.message }}</div>
        </template>
      </template>
    </a-table>
    <template #footer>
      <a-space>
        <span v-if="previewed" class="paste-summary">
          可追加 {{ appendable.length }} 行<template v-if="previewRows.length !== appendable.length">
            ，{{ previewRows.length - appendable.length }} 行需修正</template
          >
        </span>
        <a-button @click="close">取消</a-button>
        <a-button type="primary" :disabled="!appendable.length" @click="append">追加到订单</a-button>
      </a-space>
    </template>
  </a-modal>
</template>
<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {message, type TableColumnsType} from 'ant-design-vue';
import {orderApi} from '/@/api/business/scm/order-api';
import type {Id, Item} from '../order-types';
import {amount, fixed} from '../order-form-model';
import {orderError} from '../order-errors';

interface PasteRow {
  productName: string;
  specName: string;
  unit: string;
  quantity: string;
  unitPrice: string;
  remark: string;
}

interface PreviewRow extends PasteRow {
  rowNumber: number;
  skuId?: Id;
  skuCode?: string;
  matchedProductName?: string;
  matchedSpecName?: string;
  saleUnit?: string;
  productType?: string;
  resolvedUnitPrice?: string | null;
  priceSource?: string | null;
  manualPrice: boolean;
  matched: boolean;
  errors: Array<{rowNumber: number; column: string; code: string; message: string}>;
}

const props = defineProps<{open: boolean; customerId?: Id; existingSkuIds?: Id[]}>();
const emit = defineEmits<{
  'update:open': [value: boolean];
  append: [items: Item[]];
}>();

const text = ref('');
const rows = ref<PasteRow[]>([]);
const previewRows = ref<PreviewRow[]>([]);
const loading = ref(false);
const previewed = ref(false);
const parseError = ref('');

const columns: TableColumnsType<PreviewRow> = [
  {title: '行', dataIndex: 'rowNumber', width: 50},
  {title: '商品', dataIndex: 'product', width: 240},
  {title: '数量', dataIndex: 'quantity', width: 110},
  {title: '单价', dataIndex: 'price', width: 110},
  {title: '结果', dataIndex: 'result', align: 'center'},
];

const appendable = computed(() =>
  previewRows.value.filter((row) => row.matched && row.skuId != null && !(props.existingSkuIds ?? []).some((id) => String(id) === String(row.skuId)))
);

watch(
  () => text.value,
  () => {
    previewed.value = false;
    parseError.value = '';
    rows.value = parseText(text.value);
  }
);

watch(
  () => props.open,
  (open) => {
    if (!open) reset();
  }
);

function parseText(value: string): PasteRow[] {
  const lines = value.split(/\r?\n/).filter((line) => line.trim() !== '');
  if (!lines.length) return [];
  const cells = lines.map((line) => line.split('\t').map((cell) => cell.trim()));
  const header = cells[0];
  const indexOf = (aliases: string[]) => header.findIndex((cell) => aliases.includes(cell));
  const productIndex = indexOf(['商品名称', '商品', '品名']);
  const hasHeader = productIndex >= 0;
  let indexes: Record<keyof PasteRow, number>;
  if (hasHeader) {
    indexes = {
      productName: productIndex,
      specName: indexOf(['规格']),
      unit: indexOf(['单位']),
      quantity: indexOf(['数量', '实数量', '下单数量']),
      unitPrice: indexOf(['单价', '销售单价']),
      remark: indexOf(['备注']),
    };
  } else {
    // 送货单顺序：商品名称、单位、规格、实数量、销售单价、销售金额、备注；首列序号可选。
    const offset = /^\d+$/.test(cells[0][0] ?? '') && cells[0].length >= 6 ? 1 : 0;
    indexes = {productName: offset, unit: offset + 1, specName: offset + 2, quantity: offset + 3, unitPrice: offset + 4, remark: offset + 6};
  }
  if (indexes.quantity < 0) {
    return [];
  }
  return (hasHeader ? cells.slice(1) : cells)
    .map((row) => ({
      productName: row[indexes.productName] ?? '',
      specName: indexes.specName >= 0 ? row[indexes.specName] ?? '' : '',
      unit: indexes.unit >= 0 ? row[indexes.unit] ?? '' : '',
      quantity: row[indexes.quantity] ?? '',
      unitPrice: indexes.unitPrice >= 0 ? row[indexes.unitPrice] ?? '' : '',
      remark: indexes.remark >= 0 ? row[indexes.remark] ?? '' : '',
    }))
    .filter((row) => row.productName || row.quantity);
}

async function preview() {
  if (!props.customerId) {
    parseError.value = '请先在订单里选择客户';
    return;
  }
  if (!rows.value.length) {
    parseError.value = '没有可解析的行';
    return;
  }
  loading.value = true;
  parseError.value = '';
  try {
    const r = await orderApi.pastePreview({customerId: props.customerId, rows: rows.value});
    previewRows.value = r.data.rows as unknown as PreviewRow[];
    previewed.value = true;
    const duplicate = previewRows.value.find((row) => row.matched && (props.existingSkuIds ?? []).some((id) => String(id) === String(row.skuId)));
    if (duplicate) {
      message.warning(`第 ${duplicate.rowNumber} 行商品已在当前订单中，请先移除原行或修正粘贴内容`);
    }
  } catch (e) {
    parseError.value = orderError(e);
  } finally {
    loading.value = false;
  }
}

function append() {
  const items: Item[] = appendable.value.map((row) => ({
    skuId: row.skuId,
    orderedQuantity: fixed(row.quantity),
    manualPriceOverride: row.manualPrice,
    unitPrice: row.manualPrice ? fixed(row.unitPrice) : null,
    overrideReason: row.manualPrice ? '整单粘贴' : null,
    productNameSnapshot: row.matchedProductName,
    specNameSnapshot: row.matchedSpecName,
    saleUnitSnapshot: row.saleUnit,
    skuCodeSnapshot: row.skuCode,
    productTypeSnapshot: row.productType,
    draftUnitPrice: row.resolvedUnitPrice ?? null,
    draftPriceSource: row.priceSource ?? null,
  }));
  emit('append', items);
  close();
}

function reset() {
  text.value = '';
  rows.value = [];
  previewRows.value = [];
  previewed.value = false;
  parseError.value = '';
}

function close() {
  emit('update:open', false);
}
</script>
<style scoped>
.paste-input {
  margin-top: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.paste-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
}

.paste-count {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.paste-alert {
  margin-bottom: 12px;
}

.paste-sub {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.paste-error {
  color: #cf1322;
  font-size: 12px;
}

.paste-summary {
  margin-right: 8px;
  color: rgba(0, 0, 0, 0.55);
}
</style>
