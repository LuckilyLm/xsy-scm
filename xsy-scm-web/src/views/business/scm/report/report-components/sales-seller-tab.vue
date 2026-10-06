<template>
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" @click="emit('export')">导出</a-button>
        <ReportNote title="口径说明" :points="['「销售员订单业绩」不是收入、利润或提成', '未分配销售员的订单单独归集']"/>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_SALES_SELLER" :refresh="refresh"/>
      </div>
    </a-row>
    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button @click="refresh">重试</a-button>
      </template>
    </a-alert>
    <a-table
        :id="SCM_REPORT_TABLE_ID.SALES_SELLER"
        size="small"
        :data-source="rows"
        :columns="columns"
        row-key="sellerId"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无销售员业绩数据'}"
        :scroll="{x: 1000}"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'orderCount'">
          <span class="scm-quantity">{{ countText(record.orderCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'customerCount'">
          <span class="scm-quantity">{{ countText(record.customerCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'settlementAmount'">
          <span class="scm-money">{{ moneyText(record.settlementAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'completedRefundAmount'">
          <span class="scm-money">{{ moneyText(record.completedRefundAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'lastConfirmedAt'">
          {{ datetime(record.lastConfirmedAt) }}
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
import ReportNote from '/@/components/business/scm/report-note/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import {countText} from '../report-model';
import {moneyText} from '../../inventory/inventory-model';
import {datetime} from '../../common/scm-display';
import type {SalesSellerRow} from '../report-types';

defineProps<{
  rows: SalesSellerRow[];
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

const columns = ref<TableColumnsType<SalesSellerRow>>([
  {title: '销售员', dataIndex: 'sellerName', width: 160},
  {title: '订单笔数', dataIndex: 'orderCount', align: 'right', width: 120},
  {title: '客户数', dataIndex: 'customerCount', align: 'right', width: 110},
  {title: '确认订单金额', dataIndex: 'settlementAmount', align: 'right', width: 170},
  {title: '已完成退款金额', dataIndex: 'completedRefundAmount', align: 'right', width: 170},
  {title: '最近确认时间', dataIndex: 'lastConfirmedAt', width: 190},
]);

function refresh() {
  emit('reload');
}

function changePage(page: number, pageSize: number) {
  emit('pageChange', page, pageSize);
}
</script>
