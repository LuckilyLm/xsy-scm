<template>
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" @click="emit('export')">导出</a-button>
        <a-typography-text type="secondary" class="smart-margin-left10">
          一行 = 一个订单行，名称与价格全部取订单行快照，不回查当前商品主档。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_SALES_ITEM" :refresh="refresh"/>
      </div>
    </a-row>
    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button @click="refresh">重试</a-button>
      </template>
    </a-alert>
    <a-table
        :id="SCM_REPORT_TABLE_ID.SALES_ITEM"
        size="small"
        :data-source="rows"
        :columns="columns"
        row-key="orderItemId"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无订单明细'}"
        :scroll="{x: 2150}"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'orderNo'">
          <a v-if="record.orderId" @click="openOrder(record.orderId)">{{ record.orderNo }}</a>
          <span v-else>{{ record.orderNo ?? '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'confirmedAt'">
          {{ datetime(record.confirmedAt) }}
        </template>
        <template v-else-if="column.dataIndex === 'orderSource'">
          {{ enumDescText(record.orderSource, SCM_ORDER_SOURCE_ENUM) }}
        </template>
        <template v-else-if="column.dataIndex === 'settleMode'">
          {{ enumDescText(record.settleMode, SETTLE_MODE_ENUM) }}
        </template>
        <template v-else-if="column.dataIndex === 'productType'">
          {{ optionLabel(PRODUCT_TYPE_ENUM, record.productType) }}
        </template>
        <template v-else-if="column.dataIndex === 'orderedQuantity'">
          <span class="scm-quantity">{{ quantityText(record.orderedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'actualQuantity'">
          <span class="scm-quantity">{{ quantityText(record.actualQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'lockedUnitPrice'">
          <span class="scm-money">{{ moneyText(record.lockedUnitPrice) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'lockedPriceSource'">
          {{ enumDescText(record.lockedPriceSource, SCM_ORDER_PRICE_SOURCE_ENUM) }}
        </template>
        <template v-else-if="column.dataIndex === 'settlementLineAmount'">
          <span class="scm-money">{{ moneyText(record.settlementLineAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'manualPriceOverride'">
          {{ yesNoText(record.manualPriceOverride) }}
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
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import {SCM_ORDER_PRICE_SOURCE_ENUM, SCM_ORDER_SOURCE_ENUM} from '/@/constants/business/scm/order-const';
import {SETTLE_MODE_ENUM} from '/@/constants/business/scm/customer-const';
import {PRODUCT_TYPE_ENUM} from '/@/constants/business/scm/product-const';
import {enumDescText, optionLabel, yesNoText} from '../report-model';
import {moneyText, quantityText} from '../../inventory/inventory-model';
import {datetime} from '../../common/scm-display';
import type {SalesItemRow} from '../report-types';

defineProps<{
  rows: SalesItemRow[];
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
  openOrder: [orderId: string | number];
}>();

const columns = ref<TableColumnsType<SalesItemRow>>([
  {title: '确认时间', dataIndex: 'confirmedAt', width: 190},
  {title: '订单号', dataIndex: 'orderNo', width: 190},
  {title: '客户名称', dataIndex: 'customerName', width: 180},
  {title: '销售员', dataIndex: 'sellerName', width: 110},
  {title: '订单来源', dataIndex: 'orderSource', width: 110},
  {title: '结算方式', dataIndex: 'settleMode', width: 110},
  {title: '商品名称', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'specName', width: 130},
  {title: '商品类型', dataIndex: 'productType', width: 100},
  {title: '销售单位', dataIndex: 'saleUnit', align: 'center', width: 90},
  {title: '订购数量', dataIndex: 'orderedQuantity', align: 'right', width: 120},
  {title: '实际数量', dataIndex: 'actualQuantity', align: 'right', width: 120},
  {title: '锁定成交单价', dataIndex: 'lockedUnitPrice', align: 'right', width: 140},
  {title: '价格来源', dataIndex: 'lockedPriceSource', width: 130},
  {title: '结算金额', dataIndex: 'settlementLineAmount', align: 'right', width: 140},
  {title: '是否手工改价', dataIndex: 'manualPriceOverride', align: 'center', width: 120},
  {title: '手工改价原因', dataIndex: 'manualPriceReason', width: 200},
]);

function refresh() {
  emit('reload');
}

function changePage(page: number, pageSize: number) {
  emit('pageChange', page, pageSize);
}

function openOrder(orderId?: string | number) {
  if (orderId !== undefined) emit('openOrder', orderId);
}
</script>
