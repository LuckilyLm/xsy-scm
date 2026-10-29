<template>
  <a-alert
    v-if="canViewCost && (summary?.zeroCostRowCount ?? 0) > 0"
    type="warning"
    show-icon
    style="margin-bottom: 12px"
    :message="`有 ${summary?.zeroCostRowCount} 行余额均价为 0（先卖后补、尚无入库成本），估算金额会偏低，不能当作完整损耗金额`"
  />
  <a-row :gutter="[12, 12]">
    <a-col v-for="card in cards" :key="card.label" :xs="24" :sm="12" :md="8" :lg="6">
      <ReportKpiCard :label="card.label" :value="card.value" :sub="card.sub" :warning="card.warning" :tone="card.tone" current-point />
    </a-col>
  </a-row>
  <a-card size="small" :bordered="false" class="smart-margin-top10">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-if="canViewCost" @click="historyModal?.open()">月度留存</a-button>
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" :loading="exporting" @click="emit('export')">导出</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
          v-model="columns"
          :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_INVENTORY_NEGATIVE_STOCK"
          :refresh="refresh"
          :exclude-column-keys="canViewCost ? [] : COST_INDEXES"
        />
      </div>
    </a-row>
    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button @click="refresh">重试</a-button>
      </template>
    </a-alert>
    <a-table
      :id="SCM_REPORT_TABLE_ID.INVENTORY_NEGATIVE_STOCK"
      size="small"
      :data-source="rows"
      :columns="visibleColumns"
      row-key="balanceId"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{emptyText: '当前没有负库存余额'}"
      :scroll="{x: scmColumnsWidth(visibleColumns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'quantity'">
          <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'avgCost'">
          <span class="scm-money">{{ costText(record.avgCost, canViewCost) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'estimatedAmount'">
          <span class="scm-money">{{ costText(record.estimatedAmount, canViewCost, 'amount') }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'lastOutboundAt'">
          {{ record.lastOutboundAt ? datetime(record.lastOutboundAt) : '—' }}
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
        :disabled="exporting"
        show-size-changer
        :page-size-options="SCM_PAGE_SIZE_OPTIONS_WIDE"
        show-quick-jumper
        :current="pageNum"
        :page-size="pageSize"
        :total="total"
        @change="changePage"
        :show-total="(n: number) => `共${n}条`"
      />
    </div>
  </a-card>
  <InventoryNegativeStockHistory ref="historyModal" />
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import InventoryNegativeStockHistory from './inventory-negative-stock-history.vue';
import {computed, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import ReportKpiCard from './report-kpi-card.vue';
import {datetime} from '../../common/scm-display';
import {quantityText} from '../../inventory/inventory-model';
import {costText, countText, filterCostColumns} from '../report-model';
import type {InventoryNegativeStockRow, InventoryNegativeStockSummary} from '../report-types';
import {SCM_PAGE_SIZE_OPTIONS_WIDE} from '/@/constants/business/scm/pagination-const';

const historyModal = ref<InstanceType<typeof InventoryNegativeStockHistory>>();
const props = defineProps<{
  summary?: InventoryNegativeStockSummary;
  rows: InventoryNegativeStockRow[];
  loading: boolean;
  error: string;
  canViewCost: boolean;
  pageNum: number;
  pageSize: number;
  total: number;
  exporting: boolean;
}>();

const emit = defineEmits<{
  export: [];
  reload: [];
  pageChange: [page: number, pageSize: number];
}>();

/** 成本列受权限控制；没有成本权限时整列移除，不留空列。 */
const COST_INDEXES = ['avgCost', 'estimatedAmount'];

const columns = ref<TableColumnsType<InventoryNegativeStockRow>>([
  {title: '仓库', dataIndex: 'warehouseName', width: 150},
  {title: '商品', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'skuCode', width: 170},
  {title: '规格', dataIndex: 'specName', width: 110},
  {title: '负数量', dataIndex: 'quantity', width: 130},
  {title: '单位', dataIndex: 'unit', width: 76},
  {title: '余额均价', dataIndex: 'avgCost', width: 130},
  {title: '估算金额', dataIndex: 'estimatedAmount', width: 140},
  {title: '最近出库时间', dataIndex: 'lastOutboundAt', width: 180},
  {title: '最近出库单号', dataIndex: 'lastOutboundNo', width: 190},
]);

const visibleColumns = computed(() => filterCostColumns(columns.value, COST_INDEXES, props.canViewCost));

interface NegativeStockCard {
  label: string;
  value: string;
  sub?: string;
  warning?: string;
  tone?: 'danger';
}

const cards = computed<NegativeStockCard[]>(() => {
  const data = props.summary ?? {};
  const items: NegativeStockCard[] = [
    {
      label: '负库存商品规格数',
      value: countText(data.rowCount),
      sub: data.snapshotAt ? `截至 ${datetime(data.snapshotAt)}` : undefined,
    },
    {label: '涉及仓库数', value: countText(data.warehouseCount)},
  ];
  if (props.canViewCost) {
    items.push(
      {
        label: '估算金额（负数量 × 均价）',
        value: costText(data.totalEstimatedAmount, true, 'amount'),
        sub: '金额随数量符号为负，代表账面缺口',
        tone: 'danger',
      },
      {
        label: '均价为 0 的规格数',
        value: countText(data.zeroCostRowCount),
        warning: (data.zeroCostRowCount ?? 0) > 0 ? '这些行没有入库成本，金额被低估' : undefined,
      }
    );
  }
  return items;
});

function refresh() {
  emit('reload');
}

function changePage(page: number, pageSize: number) {
  emit('pageChange', page, pageSize);
}
</script>
