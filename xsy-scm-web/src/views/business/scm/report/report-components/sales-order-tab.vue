<template>
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" @click="emit('export')">导出</a-button>
        <a-typography-text type="secondary" class="smart-margin-left10">
          一行 = 一个订单：回答「这个客户有几单、每单多少」。分类与关键词只判定订单是否命中，
          命中后金额仍按整单汇总，不会只算匹配到的行。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_SALES_ORDER" :refresh="refresh"/>
      </div>
    </a-row>
    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button @click="refresh">重试</a-button>
      </template>
    </a-alert>
    <a-table
        :id="SCM_REPORT_TABLE_ID.SALES_ORDER"
        size="small"
        :data-source="rows"
        :columns="columns"
        row-key="orderId"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无客户订单明细'}"
        :scroll="{x: 1600}"
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
        <template v-else-if="column.dataIndex === 'lineCount'">
          <span class="num">{{ countText(record.lineCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'skuKindCount'">
          <span class="num">{{ countText(record.skuKindCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'settlementAmount'">
          <span class="num">{{ moneyText(record.settlementAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'completedRefundAmount'">
          <span class="num">{{ moneyText(record.completedRefundAmount) }}</span>
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
import {SCM_ORDER_SOURCE_ENUM} from '/@/constants/business/scm/order-const';
import {SETTLE_MODE_ENUM} from '/@/constants/business/scm/customer-const';
import {countText, enumDescText} from '../report-model';
import {moneyText} from '../../inventory/inventory-model';
import {datetime} from '../../common/scm-display';
import type {ReportId, SalesOrderRow} from '../report-types';

defineProps<{
  rows: SalesOrderRow[];
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
  openOrder: [orderId: ReportId];
}>();

const columns = ref<TableColumnsType<SalesOrderRow>>([
  {title: '确认时间', dataIndex: 'confirmedAt', width: 180},
  {title: '订单号', dataIndex: 'orderNo', width: 190},
  {title: '客户编码', dataIndex: 'customerCode', width: 150},
  {title: '客户名称', dataIndex: 'customerName', width: 200},
  {title: '销售员', dataIndex: 'sellerName', width: 120},
  {title: '订单来源', dataIndex: 'orderSource', width: 110},
  {title: '结算方式', dataIndex: 'settleMode', width: 120},
  {title: '订单行数', dataIndex: 'lineCount', align: 'right', width: 110},
  {title: '商品规格数', dataIndex: 'skuKindCount', align: 'right', width: 120},
  {title: '结算金额', dataIndex: 'settlementAmount', align: 'right', width: 150},
  {title: '已完成退款金额', dataIndex: 'completedRefundAmount', align: 'right', width: 160},
]);

function refresh() {
  emit('reload');
}

function changePage(page: number, pageSize: number) {
  emit('pageChange', page, pageSize);
}

function openOrder(orderId?: ReportId) {
  if (orderId !== undefined) emit('openOrder', orderId);
}
</script>

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
