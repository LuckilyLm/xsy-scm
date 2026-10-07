<template>
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" @click="emit('export')">导出</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PURCHASE_ITEM" :refresh="refresh"/>
      </div>
    </a-row>
    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button @click="refresh">重试</a-button>
      </template>
    </a-alert>
    <a-table
        :id="SCM_REPORT_TABLE_ID.PURCHASE_ITEM"
        size="small"
        :data-source="rows"
        :columns="columns"
        row-key="purchaseOrderItemId"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无采购明细'}"
        :scroll="{x: 1990}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'submittedAt'">
          {{ datetime(record.submittedAt) }}
        </template>
        <template v-else-if="column.dataIndex === 'orderNo'">
          <a v-if="record.purchaseOrderId" @click="openPurchaseOrder(record.purchaseOrderId)">{{ record.orderNo }}</a>
          <span v-else>{{ record.orderNo ?? '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag>{{ enumDescText(record.status, SCM_PURCHASE_STATUS_ENUM) }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'plannedArrivalDate'">
          {{ dateOnly(record.plannedArrivalDate) }}
        </template>
        <template v-else-if="column.dataIndex === 'plannedQuantity'">
          <span class="scm-quantity">{{ quantityText(record.plannedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'receivedQuantity'">
          <span class="scm-quantity">{{ quantityText(record.receivedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'purchasePrice'">
          <span class="scm-money">{{ moneyText(record.purchasePrice) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'lineAmount'">
          <span class="scm-money">{{ moneyText(record.lineAmount) }}</span>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
          show-size-changer
          show-quick-jumper
          :current="pageNum"
          :page-size="pageSize"
          :total="total"
          @change="changePage"
          :show-total="(n: number) => `共${n}条`"
      />
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_PURCHASE_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import {dateOnly, datetime} from '../../common/scm-display';
import {moneyText, quantityText} from '../../inventory/inventory-model';
import {enumDescText} from '../report-model';
import type {PurchaseItemRow} from '../report-types';

defineProps<{
  rows: PurchaseItemRow[];
  loading: boolean;
  error: string;
  pageNum: number;
  pageSize: number;
  total: number;
}>();

const emit = defineEmits<{
  export: [];
  reload: [];
  pageChange: [page: number, pageSize: number];
  openPurchaseOrder: [purchaseOrderId: string | number];
}>();

const columns = ref<TableColumnsType<PurchaseItemRow>>([
  {title: '提交时间', dataIndex: 'submittedAt', width: 190},
  {title: '采购单号', dataIndex: 'orderNo', width: 190},
  {title: '状态', dataIndex: 'status', align: 'center', width: 110},
  {title: '供应商', dataIndex: 'supplierName', width: 180},
  {title: '采购员', dataIndex: 'purchaserName', width: 120},
  {title: '仓库', dataIndex: 'warehouseName', width: 150},
  {title: '计划到货日期', dataIndex: 'plannedArrivalDate', width: 130},
  {title: '商品', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'skuName', width: 140},
  {title: '采购单位', dataIndex: 'purchaseUnit', align: 'center', width: 100},
  {title: '计划数量', dataIndex: 'plannedQuantity', align: 'right', width: 120},
  {title: '累计收货数量', dataIndex: 'receivedQuantity', align: 'right', width: 150},
  {title: '采购单价', dataIndex: 'purchasePrice', align: 'right', width: 130},
  {title: '采购行金额', dataIndex: 'lineAmount', align: 'right', width: 140},
]);

function refresh() {
  emit('reload');
}

function changePage(page: number, pageSize: number) {
  emit('pageChange', page, pageSize);
}

function openPurchaseOrder(purchaseOrderId?: string | number) {
  if (purchaseOrderId !== undefined) emit('openPurchaseOrder', purchaseOrderId);
}
</script>
