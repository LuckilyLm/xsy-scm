<!-- 采购收货单详情抽屉。只读：表头来自收货单，明细展示计划、实收、差异与批次；
     确认收货与仓库入库仍是列表上的状态动作，不在详情里重复入口。 -->
<template>
  <a-drawer :open="visible" :title="receipt?.receiptNo || '收货单详情'" :width="scmDrawerWidth('l')" @close="visible = false">
    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action>
        <a-button @click="load">重试</a-button>
      </template>
    </a-alert>

    <a-spin :spinning="loading">
      <template v-if="receipt">
        <a-descriptions bordered size="small" :column="2">
          <a-descriptions-item label="收货单号">{{ receipt.receiptNo || '—' }}</a-descriptions-item>
          <a-descriptions-item label="采购单号">{{ receipt.purchaseOrderNo || '—' }}</a-descriptions-item>
          <a-descriptions-item label="供应商">{{ receipt.supplierName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="收货仓库">{{ receipt.warehouseName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag>{{ SCM_RECEIPT_STATUS_ENUM[receipt.status ?? '']?.desc || receipt.status || '—' }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="入库方式">
            <a-tag>{{ SCM_RECEIPT_MODE_ENUM[receipt.receiptMode ?? '']?.desc || receipt.receiptMode || '—' }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="入库状态">
            <a-tag :color="receipt.putawayStatus === 'COMPLETED' ? 'green' : 'orange'">
              {{ SCM_PUTAWAY_STATUS_ENUM[receipt.putawayStatus ?? '']?.desc || receipt.putawayStatus || '—' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="确认时间">{{ datetime(receipt.confirmedAt) }}</a-descriptions-item>
          <a-descriptions-item label="操作者">{{ receipt.operator || '—' }}</a-descriptions-item>
          <a-descriptions-item v-if="receipt.putawayAt" label="入库时间">{{ datetime(receipt.putawayAt) }}</a-descriptions-item>
          <a-descriptions-item v-if="receipt.putawayBy" label="入库人">{{ receipt.putawayBy }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ datetime(receipt.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="备注" :span="2">{{ receipt.remark || '—' }}</a-descriptions-item>
        </a-descriptions>

        <a-divider orientation="left">收货明细</a-divider>
        <a-table
          :data-source="receipt.items ?? []"
          :columns="columns"
          row-key="id"
          :pagination="false"
          :scroll="{x: scmColumnsWidth(columns)}"
          size="small"
          bordered
        >
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'purchaseUnit'">{{ record.purchaseUnit || '—' }}</template>
            <template v-else-if="column.dataIndex === 'plannedQuantity'">
              <span class="scm-quantity">{{ quantity(record.plannedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receivedQuantity'">
              <span class="scm-quantity">{{ quantity(record.receivedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'cumulativeReceivedQuantity'">
              <span class="scm-quantity">{{ quantity(record.cumulativeReceivedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'remainingQuantity'">
              <span class="scm-quantity">{{ quantity(record.remainingQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'overReceiptQuantity'">
              <span class="scm-quantity">{{ quantity(record.overReceiptQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receiptDifference'">
              <span class="scm-quantity">{{ quantity(record.receiptDifference) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'actualWeight'">
              <span v-if="record.actualWeight" class="scm-quantity"> {{ quantity(record.actualWeight) }} {{ record.weightUnit || '' }} </span>
              <span v-else>—</span>
            </template>
            <template v-else-if="column.dataIndex === 'lots'">
              <span v-if="record.lots?.length" class="lots">
                {{ record.lots.map(lotText).join('；') }}
              </span>
              <span v-else>—</span>
            </template>
            <template v-else-if="column.dataIndex === 'correctionReason'">{{ record.correctionReason || '—' }}</template>
          </template>
        </a-table>
      </template>
    </a-spin>

    <template #footer>
      <a-button @click="visible = false">关闭</a-button>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {purchaseReceiptApi} from '/@/api/business/scm/purchase-receipt-api';
import {SCM_PUTAWAY_STATUS_ENUM, SCM_RECEIPT_MODE_ENUM, SCM_RECEIPT_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import {quantity} from '../purchase-form-model';
import {datetime} from '../../common/scm-display';
import {purchaseError} from '../purchase-errors';
import type {Id, Receipt, ReceiptItem, ReceiptItemLot} from '../purchase-types';

const visible = ref(false);
const loading = ref(false);
const error = ref('');
const receipt = ref<Receipt>();
let requestId = 0;

const columns: TableColumnsType<ReceiptItem> = [
  {title: '商品名称', dataIndex: 'skuName', width: 190},
  {title: '采购单位', dataIndex: 'purchaseUnit', width: 84},
  {title: '计划数量', dataIndex: 'plannedQuantity', width: 110},
  {title: '本次实收', dataIndex: 'receivedQuantity', width: 110},
  {title: '累计已收', dataIndex: 'cumulativeReceivedQuantity', width: 110},
  {title: '剩余可收', dataIndex: 'remainingQuantity', width: 110},
  {title: '超收', dataIndex: 'overReceiptQuantity', width: 96},
  {title: '差异', dataIndex: 'receiptDifference', width: 96},
  {title: '实重', dataIndex: 'actualWeight', width: 120},
  {title: '批次', dataIndex: 'lots', width: 200},
  {title: '修正原因', dataIndex: 'correctionReason', width: 160},
];

function lotText(lot: ReceiptItemLot): string {
  const no = lot.sourceLotNo || lot.lotNo || '未登记批号';
  return `${no} / ${quantity(lot.quantity)}`;
}

async function open(id: Id) {
  const current = ++requestId;
  visible.value = true;
  loading.value = true;
  error.value = '';
  receipt.value = undefined;
  try {
    const r = await purchaseReceiptApi.detail(id, {suppressGlobalErrorMessage: true});
    if (current === requestId) {
      receipt.value = r.data;
    }
  } catch (e) {
    if (current === requestId) {
      error.value = purchaseError(e);
    }
  } finally {
    if (current === requestId) {
      loading.value = false;
    }
  }
}

async function load() {
  if (receipt.value?.id) {
    await open(receipt.value.id);
  }
}

defineExpose({open});
</script>

<style scoped lang="less">
.lots {
  white-space: normal;
  word-break: break-all;
}
</style>
