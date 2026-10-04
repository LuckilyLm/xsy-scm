<template>
  <ReportBarChart
      class="smart-margin-bottom10"
      title="供应商采购入库成本 TOP10"
      :items="items"
      series-name="采购入库成本金额"
  />
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" @click="emit('export')">导出</a-button>
        <a-typography-text type="secondary" class="smart-margin-left10">
          点供应商名称，右侧抽屉看该供应商的商品维度明细。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PURCHASE_SUPPLIER" :refresh="refresh"/>
      </div>
    </a-row>
    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button @click="refresh">重试</a-button>
      </template>
    </a-alert>
    <a-table
        :id="SCM_REPORT_TABLE_ID.PURCHASE_SUPPLIER"
        size="small"
        :data-source="rows"
        :columns="visibleColumns"
        row-key="supplierId"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无供应商采购数据'}"
        :scroll="{x: 1600}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'supplierName'">
          <a v-if="record.supplierId" @click="emit('openSupplier', record)">{{ record.supplierName }}</a>
          <span v-else>{{ record.supplierName ?? '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'orderCount'">
          <span class="num">{{ countText(record.orderCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'skuKindCount'">
          <span class="num">{{ countText(record.skuKindCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'orderAmount'">
          <span class="num">{{ moneyText(record.orderAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'receiptReferenceAmount'">
          <span class="num">{{ moneyText(record.receiptReferenceAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'inboundCostAmount'">
          <span class="num">{{ costText(record.inboundCostAmount, canViewCost) }}</span>
          <a-tooltip v-if="incompleteCostHint(record.inboundCostMissingCount, '采购入库成本金额')"
                     :title="incompleteCostHint(record.inboundCostMissingCount, '采购入库成本金额')">
            <ExclamationCircleOutlined class="report-warn-icon" aria-hidden="true"/>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'lastSubmittedAt'">
          {{ datetime(record.lastSubmittedAt) }}
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
import {computed, ref} from 'vue';
import {ExclamationCircleOutlined} from '@ant-design/icons-vue';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import ReportBarChart from './report-bar-chart.vue';
import {costText, countText, filterCostColumns, incompleteCostHint} from '../report-model';
import {moneyText} from '../../inventory/inventory-model';
import {datetime} from '../../common/scm-display';
import type {PurchaseSupplierRow, ReportChartBar} from '../report-types';

const props = defineProps<{
  items: ReportChartBar[];
  rows: PurchaseSupplierRow[];
  loading: boolean;
  error: string;
  canViewCost: boolean;
  pageNum: number;
  pageSize: number;
  total: number;
}>();

const emit = defineEmits<{
  export: [];
  reload: [];
  pageChange: [page: number, pageSize: number];
  openSupplier: [row: PurchaseSupplierRow];
}>();

const COST_INDEXES = ['inboundCostAmount'];
const columns = ref<TableColumnsType<PurchaseSupplierRow>>([
  {title: '供应商编码', dataIndex: 'supplierCode', width: 150},
  {title: '供应商名称', dataIndex: 'supplierName', width: 200},
  {title: '采购单数', dataIndex: 'orderCount', align: 'right', width: 110},
  {title: 'SKU 种类数', dataIndex: 'skuKindCount', align: 'right', width: 120},
  {title: '采购订单金额', dataIndex: 'orderAmount', align: 'right', width: 160},
  {title: '已收参考金额', dataIndex: 'receiptReferenceAmount', align: 'right', width: 150},
  {title: '采购入库成本金额', dataIndex: 'inboundCostAmount', align: 'right', width: 180},
  {title: '最近采购时间', dataIndex: 'lastSubmittedAt', width: 190},
  {title: '金额排名', dataIndex: 'amountRank', align: 'right', width: 110},
]);

const visibleColumns = computed(() => filterCostColumns(columns.value, COST_INDEXES, props.canViewCost));

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
