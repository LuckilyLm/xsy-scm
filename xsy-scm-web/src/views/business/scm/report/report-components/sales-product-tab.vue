<template>
  <ReportBarChart
      class="smart-margin-bottom10"
      title="商品确认订单金额 TOP5"
      :items="items"
      series-name="确认订单金额"
      extra="按已确认订单金额降序，由数据库直接取前 5 名"
  />
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" @click="emit('export')">导出</a-button>
        <a-typography-text type="secondary" class="smart-margin-left10">
          一行 = 商品规格 × 销售单位；确认数量按各自单位统计，不做跨单位合计。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_SALES_PRODUCT" :refresh="refresh"/>
      </div>
    </a-row>
    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button @click="refresh">重试</a-button>
      </template>
    </a-alert>
    <a-table
        :id="SCM_REPORT_TABLE_ID.SALES_PRODUCT"
        size="small"
        :data-source="rows"
        :columns="columns"
        row-key="skuId"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无商品销售数据'}"
        :scroll="{x: 1720}"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'orderCount'">
          <span class="num">{{ countText(record.orderCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'customerCount'">
          <span class="num">{{ countText(record.customerCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'confirmedQuantity'">
          <span class="num">{{ quantityText(record.confirmedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'avgTransactionPrice'">
          <span class="num">{{ moneyText(record.avgTransactionPrice) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'settlementAmount'">
          <span class="num">{{ moneyText(record.settlementAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'amountRank'">
          <span class="num">{{ countText(record.amountRank) }}</span>
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
import ReportBarChart from './report-bar-chart.vue';
import {countText} from '../report-model';
import {moneyText, quantityText} from '../../inventory/inventory-model';
import type {ReportChartBar, SalesProductRow} from '../report-types';

defineProps<{
  items: ReportChartBar[];
  rows: SalesProductRow[];
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
}>();

const columns = ref<TableColumnsType<SalesProductRow>>([
  {title: '商品名称', dataIndex: 'productName', width: 200},
  {title: '一级分类', dataIndex: 'rootCategoryName', width: 140},
  {title: '末级分类', dataIndex: 'leafCategoryName', width: 140},
  {title: '商品编码', dataIndex: 'spuCode', width: 150},
  {title: '商品规格编码', dataIndex: 'skuCode', width: 170},
  {title: '商品规格', dataIndex: 'specName', width: 140},
  {title: '销售单位', dataIndex: 'saleUnit', align: 'center', width: 100},
  {title: '订单笔数', dataIndex: 'orderCount', align: 'right', width: 110},
  {title: '客户数', dataIndex: 'customerCount', align: 'right', width: 100},
  {title: '确认数量', dataIndex: 'confirmedQuantity', align: 'right', width: 130},
  {title: '成交均价', dataIndex: 'avgTransactionPrice', align: 'right', width: 130},
  {title: '确认订单金额', dataIndex: 'settlementAmount', align: 'right', width: 160},
  {title: '金额排名', dataIndex: 'amountRank', align: 'right', width: 110},
]);

function refresh() {
  emit('reload');
}

function changePage(page: number, pageSize: number) {
  emit('pageChange', page, pageSize);
}
</script>

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
