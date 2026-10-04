<template>
  <ReportBarChart
      class="smart-margin-bottom10"
      title="分类确认订单金额 TOP5"
      :items="items"
      series-name="确认订单金额"
      extra="一级 + 末级分类；不含「实际金额」这类当前无定义的字段"
  />
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" @click="emit('export')">导出</a-button>
        <a-typography-text type="secondary" class="smart-margin-left10">
          三级分类无 path 列，按 parent_id 上卷；金额是该分类节点及其子孙的合计。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_SALES_CATEGORY" :refresh="refresh"/>
      </div>
    </a-row>
    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button @click="refresh">重试</a-button>
      </template>
    </a-alert>
    <a-table
        :id="SCM_REPORT_TABLE_ID.SALES_CATEGORY"
        size="small"
        :data-source="rows"
        :columns="columns"
        row-key="categoryId"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无分类销售数据'}"
        :scroll="{x: 900}"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'settlementAmount'">
          <span class="num">{{ moneyText(record.settlementAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'orderCount'">
          <span class="num">{{ countText(record.orderCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'customerCount'">
          <span class="num">{{ countText(record.customerCount) }}</span>
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
import {moneyText} from '../../inventory/inventory-model';
import type {ReportChartBar, SalesCategoryRow} from '../report-types';

defineProps<{
  items: ReportChartBar[];
  rows: SalesCategoryRow[];
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

const columns = ref<TableColumnsType<SalesCategoryRow>>([
  {title: '一级分类', dataIndex: 'rootCategoryName', width: 180},
  {title: '末级分类', dataIndex: 'leafCategoryName', width: 180},
  {title: '确认订单金额', dataIndex: 'settlementAmount', align: 'right', width: 170},
  {title: '订单笔数', dataIndex: 'orderCount', align: 'right', width: 120},
  {title: '客户数', dataIndex: 'customerCount', align: 'right', width: 110},
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
