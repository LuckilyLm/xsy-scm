<template>
  <a-modal :open="visible" title="新建独立退货" width="min(980px,96vw)" :confirm-loading="saving" :mask-closable="!saving" @ok="save" @cancel="close">
    <p class="scm-note block-gap">独立退货不关联原订单。单价优先取该客户该商品最近一次成交价，无成交价时按客户价目表取价，仍无价格时须人工填价并填写原因。</p>
    <a-alert v-if="error" :message="error" type="error" show-icon class="block-gap" />
    <a-form layout="vertical">
      <a-row :gutter="12">
        <a-col :span="8">
          <a-form-item label="客户" required>
            <CustomerSelect v-model:value="customerId" :disabled="saving" @change="onCustomerChange" />
          </a-form-item>
        </a-col>
        <a-col :span="16">
          <a-form-item label="退货原因" required>
            <a-input v-model:value="reason" :disabled="saving" maxlength="500" placeholder="例如：现场退回，找不到原单" />
          </a-form-item>
        </a-col>
      </a-row>
    </a-form>
    <a-table
      size="small"
      bordered
      :data-source="rows"
      :columns="columns"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
      :row-key="(row: Row) => row.key"
    >
      <template #bodyCell="{record, column, index}">
        <template v-if="column.dataIndex === 'skuId'">
          <SkuSelect
            :value="record.skuId"
            :disabled="saving"
            width="220px"
            :disabled-statuses="[]"
            @update:value="(value) => onSkuChange(record, value)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'specName'">{{ record.specName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'saleUnit'">{{ record.saleUnit || '—' }}</template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <a-input-number
            v-model:value="record.quantity"
            string-mode
            :precision="4"
            :min="'0.0001'"
            :disabled="saving"
            :aria-label="'退货数量 ' + (index + 1)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'unitPrice'">
          <a-input-number
            v-model:value="record.unitPrice"
            string-mode
            :precision="4"
            :min="'0'"
            :disabled="saving || !record.skuId"
            :aria-label="'退货单价 ' + (index + 1)"
            @change="record.overridden = true"
          />
          <div class="scm-cell-hint">
            {{ record.priced ? '建议价：' + record.suggestedPrice + '（' + priceSourceText(record.priceSource) + '）' : '无可用价格，请人工填价' }}
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'priceReason'">
          <a-input
            v-model:value="record.priceReason"
            :disabled="saving || !needsManualPrice(record)"
            maxlength="500"
            :aria-label="'价格原因 ' + (index + 1)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'amount'">{{ lineAmount(record) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-button type="link" size="small" danger :disabled="saving || rows.length <= 1" @click="removeRow(record)">删除</a-button>
        </template>
      </template>
    </a-table>
    <a-button type="dashed" block :disabled="saving" class="block-gap" @click="addRow">添加商品</a-button>
  </a-modal>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import {message, type TableColumnsType} from 'ant-design-vue';
import Decimal from 'decimal.js';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {orderReturnApi as api} from '/@/api/business/scm/order-return-api';
import type {ScmId} from '/@/types/business/scm/customer';
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {fixed} from '../order-form-model';
import {orderError} from '../order-errors';
import type {Id} from '../order-types';

interface Row {
  key: string;
  skuId?: ScmId;
  productName?: string;
  specName?: string;
  saleUnit?: string;
  quantity?: string | null;
  suggestedPrice?: string | null;
  priceSource?: string | null;
  priced: boolean;
  unitPrice?: string | null;
  priceReason: string;
  overridden: boolean;
}

type SkuValue = ScmId | ScmId[] | undefined;

const emit = defineEmits<{saved: []}>();
const visible = ref(false),
  saving = ref(false),
  error = ref(''),
  customerId = ref<Id>(),
  reason = ref(''),
  rows = ref<Row[]>([]);
let rowSequence = 0;

const columns: TableColumnsType<Row> = [
  {title: '商品名称', dataIndex: 'skuId', width: 240},
  {title: '规格', dataIndex: 'specName', width: 110},
  {title: '单位', dataIndex: 'saleUnit', width: 72},
  {title: '退货数量', dataIndex: 'quantity', width: 120},
  {title: '退货单价', dataIndex: 'unitPrice', width: 160},
  {title: '价格原因', dataIndex: 'priceReason', width: 180},
  {title: '金额', dataIndex: 'amount', width: 110},
  {title: '', dataIndex: 'action', width: 64, fixed: 'right', align: 'center'},
];

const PRICE_SOURCE_TEXT: Record<string, string> = {
  RECENT_TRANSACTION: '最近成交价',
  AGREEMENT: '协议价',
  CUSTOMER_GROUP: '客户分组价',
  CUSTOMER_TYPE: '客户类型价',
  TIME_PRICE: '时价',
  MARKET: '市场价',
  MANUAL: '人工填价',
};
const priceSourceText = (source?: string | null): string => (source ? PRICE_SOURCE_TEXT[source] ?? source : '—');

function newRow(): Row {
  return {key: 'ir-row-' + ++rowSequence, quantity: '1.0000', priced: false, priceReason: '', overridden: false};
}

function open() {
  customerId.value = undefined;
  reason.value = '';
  rows.value = [newRow()];
  error.value = '';
  visible.value = true;
}

function close() {
  if (saving.value) return;
  visible.value = false;
}

function addRow() {
  rows.value = [...rows.value, newRow()];
}

function removeRow(row: Row) {
  rows.value = rows.value.filter((i) => i.key !== row.key);
}

function firstId(value: SkuValue): ScmId | undefined {
  return Array.isArray(value) ? value[0] : value;
}

async function onCustomerChange(value?: ScmId) {
  customerId.value = value;
  await refreshPrices();
}

async function onSkuChange(row: Row, value: SkuValue) {
  row.skuId = firstId(value);
  row.overridden = false;
  row.unitPrice = null;
  row.priceReason = '';
  row.suggestedPrice = null;
  row.priced = false;
  row.priceSource = null;
  if (row.skuId) await refreshPrices();
}

/** 一次把已选商品重新取价，客户切换和新增行共用同一条路径。 */
async function refreshPrices() {
  const skuIds = rows.value.map((row) => row.skuId).filter((id): id is ScmId => id !== undefined);
  if (!customerId.value || !skuIds.length) return;
  try {
    const response = await api.previewIndependentPrices({customerId: customerId.value, skuIds});
    for (const preview of response.data) {
      for (const row of rows.value.filter((item) => item.skuId === preview.skuId)) {
        row.productName = preview.productName ?? undefined;
        row.specName = preview.specName ?? undefined;
        row.saleUnit = preview.saleUnit ?? undefined;
        row.suggestedPrice = preview.unitPrice ?? null;
        row.priceSource = preview.priceSource ?? null;
        row.priced = preview.priced;
        if (!row.overridden) row.unitPrice = preview.priced ? preview.unitPrice : null;
      }
    }
  } catch (e) {
    error.value = orderError(e);
  }
}

function needsManualPrice(row: Row): boolean {
  return row.overridden || !row.priced;
}

function lineAmount(row: Row): string {
  const quantity = new Decimal(row.quantity ?? 0);
  const price = new Decimal(row.unitPrice ?? 0);
  return quantity.mul(price).toFixed(4);
}

async function save() {
  error.value = '';
  if (!customerId.value) {
    error.value = '请选择客户';
    return;
  }
  if (!reason.value.trim()) {
    error.value = '请填写退货原因';
    return;
  }
  const selected = rows.value.filter((row) => row.skuId);
  if (!selected.length) {
    error.value = '请至少选择一个商品';
    return;
  }
  if (selected.some((row) => new Decimal(row.quantity ?? 0).lte(0))) {
    error.value = '退货数量必须大于 0';
    return;
  }
  if (selected.some((row) => needsManualPrice(row) && !row.unitPrice)) {
    error.value = '没有可用价格的商品必须人工填写单价';
    return;
  }
  if (selected.some((row) => needsManualPrice(row) && !row.priceReason.trim())) {
    error.value = '人工填价或改价必须填写价格原因';
    return;
  }
  const skuIds = selected.map((row) => row.skuId);
  if (new Set(skuIds).size !== skuIds.length) {
    error.value = '同一商品只能出现一行';
    return;
  }
  saving.value = true;
  try {
    await api.createIndependent({
      customerId: customerId.value,
      reason: reason.value.trim(),
      items: selected.map((row) => ({
        skuId: row.skuId,
        requestedQuantity: fixed(row.quantity ?? '0'),
        ...(needsManualPrice(row) ? {unitPrice: fixed(row.unitPrice ?? '0'), priceReason: row.priceReason.trim()} : {}),
      })),
    });
    message.success('独立退货单已创建');
    visible.value = false;
    emit('saved');
  } catch (e) {
    error.value = orderError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
<style scoped>
.block-gap {
  margin-bottom: 12px;
}
</style>
