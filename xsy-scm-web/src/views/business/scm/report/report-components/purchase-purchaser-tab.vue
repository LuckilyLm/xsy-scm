<template>
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" @click="emit('export')">导出</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PURCHASE_PURCHASER" :refresh="refresh"/>
      </div>
    </a-row>
    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button @click="refresh">重试</a-button>
      </template>
    </a-alert>
    <a-table
        :id="SCM_REPORT_TABLE_ID.PURCHASE_PURCHASER"
        size="small"
        :data-source="rows"
        :columns="visibleColumns"
        row-key="purchaserId"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无采购员数据'}"
        :scroll="{x: 1400}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'purchaserName'">
          <a @click="emit('openPurchaser', record)">{{ record.purchaserName ?? '未分配采购员' }}</a>
        </template>
        <template v-else-if="column.dataIndex === 'orderCount'">
          <span class="scm-quantity">{{ countText(record.orderCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'skuKindCount'">
          <span class="scm-quantity">{{ countText(record.skuKindCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'orderAmount'">
          <span class="scm-money">{{ moneyText(record.orderAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'receiptReferenceAmount'">
          <span class="scm-money">{{ moneyText(record.receiptReferenceAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'inboundCostAmount'">
          <span class="scm-money">{{ costText(record.inboundCostAmount, canViewCost) }}</span>
          <a-tooltip v-if="incompleteCostHint(record.inboundCostMissingCount, '采购入库成本金额')"
                     :title="incompleteCostHint(record.inboundCostMissingCount, '采购入库成本金额')">
            <ExclamationCircleOutlined class="report-warn-icon" aria-hidden="true"/>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'lastSubmittedAt'">
          {{ datetime(record.lastSubmittedAt) }}
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
import {costText, countText, filterCostColumns, incompleteCostHint} from '../report-model';
import {moneyText} from '../../inventory/inventory-model';
import {datetime} from '../../common/scm-display';
import type {PurchasePurchaserRow} from '../report-types';

const props = defineProps<{
  rows: PurchasePurchaserRow[];
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
  openPurchaser: [row: PurchasePurchaserRow];
}>();

const COST_INDEXES = ['inboundCostAmount'];
const columns = ref<TableColumnsType<PurchasePurchaserRow>>([
  {title: '采购员', dataIndex: 'purchaserName', width: 160},
  {title: '采购单数', dataIndex: 'orderCount', align: 'right', width: 120},
  {title: '商品规格数', dataIndex: 'skuKindCount', align: 'right', width: 120},
  {title: '采购订单金额', dataIndex: 'orderAmount', align: 'right', width: 170},
  {title: '已收参考金额', dataIndex: 'receiptReferenceAmount', align: 'right', width: 160},
  {title: '采购入库成本金额', dataIndex: 'inboundCostAmount', align: 'right', width: 180},
  {title: '最近采购时间', dataIndex: 'lastSubmittedAt', width: 190},
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
.report-warn-icon {
  color: var(--scm-warning);
  margin-left: 4px;
}
</style>
