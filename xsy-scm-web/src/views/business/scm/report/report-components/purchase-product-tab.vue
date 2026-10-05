<template>
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" @click="emit('export')">导出</a-button>
        <a-typography-text type="secondary" class="smart-margin-left10">
          采购入库数量按库存记账单位分组，与采购单位可能不同，因此是文本且不做合计。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            :model-value="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PURCHASE_PRODUCT"
            :refresh="refresh"
            @update:model-value="emit('update:columns', $event)"
        />
      </div>
    </a-row>
    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button @click="refresh">重试</a-button>
      </template>
    </a-alert>
    <a-table
        :id="SCM_REPORT_TABLE_ID.PURCHASE_PRODUCT"
        size="small"
        :data-source="rows"
        :columns="visibleColumns"
        row-key="skuId"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无商品采购数据'}"
        :scroll="{x: 1580}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'orderCount'">
          <span class="scm-quantity">{{ countText(record.orderCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'plannedQuantity'">
          <span class="scm-quantity">{{ quantityText(record.plannedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'receivedQuantity'">
          <span class="scm-quantity">{{ quantityText(record.receivedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'orderAmount'">
          <span class="scm-money">{{ moneyText(record.orderAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'avgPurchasePrice'">
          <span class="scm-money">{{ moneyText(record.avgPurchasePrice) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'inboundQuantityText'">
          {{ textOrDash(record.inboundQuantityText) }}
        </template>
        <template v-else-if="column.dataIndex === 'inboundCostAmount'">
          <span class="scm-money">{{ costText(record.inboundCostAmount, canViewCost) }}</span>
          <a-tooltip v-if="incompleteCostHint(record.inboundCostMissingCount, '采购入库成本金额')"
                     :title="incompleteCostHint(record.inboundCostMissingCount, '采购入库成本金额')">
            <ExclamationCircleOutlined class="report-warn-icon" aria-hidden="true"/>
          </a-tooltip>
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
import {computed} from 'vue';
import {ExclamationCircleOutlined} from '@ant-design/icons-vue';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import {costText, countText, filterCostColumns, incompleteCostHint, textOrDash} from '../report-model';
import {moneyText, quantityText} from '../../inventory/inventory-model';
import type {PurchaseProductRow} from '../report-types';

const props = defineProps<{
  columns: TableColumnsType<PurchaseProductRow>;
  rows: PurchaseProductRow[];
  loading: boolean;
  error: string;
  canViewCost: boolean;
  pageNum: number;
  pageSize: number;
  total: number;
}>();

const emit = defineEmits<{
  'update:columns': [columns: TableColumnsType<PurchaseProductRow>];
  export: [];
  reload: [];
  pageChange: [page: number, pageSize: number];
}>();

const visibleColumns = computed(() => filterCostColumns(props.columns, ['inboundCostAmount'], props.canViewCost));

function refresh() {
  emit('reload');
}

function changePage(page: number, pageSize: number) {
  emit('pageChange', page, pageSize);
}
</script>

<style scoped>
.report-warn-icon {
  color: var(--ant-color-warning);
  margin-left: 4px;
}
</style>
