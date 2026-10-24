<!-- 订单缺口预览只读，按已确认订单实际数量和指定仓库可用库存比较；不能在预览时自动生成采购需求。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="统计时间段" class="smart-query-form-item">
        <a-range-picker v-model:value="range" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 380px" />
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="warehouseId" width="200px" placeholder="请选择仓库" />
      </a-form-item>
      <a-form-item label="商品关键字" class="smart-query-form-item">
        <a-input v-model:value="keyword" placeholder="商品名称 / 商品规格" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:purchase:demand:query'">查询缺口</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-table
      id="scm-purchase-demand-summary-preview-table"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="skuId"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'calculationStatus'">
          <a-tag :color="SCM_DEMAND_SUMMARY_STATUS_COLOR[record.calculationStatus] || 'default'">
            {{ SCM_DEMAND_SUMMARY_STATUS_ENUM[record.calculationStatus]?.desc || record.calculationStatus }}
          </a-tag>
        </template>
        <template v-else-if="numericColumns.includes(column.dataIndex)">
          <span class="scm-quantity">{{ quantity(record[column.dataIndex]) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'inventoryUnit'">
          {{ record.inventoryUnit || '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'categoryName'">
          {{ record.categoryName || '—' }}
        </template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
        show-size-changer
        show-quick-jumper
        v-model:current="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        @change="queryData"
        :show-total="(n: number) => `共${n}条`"
      />
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, h, ref} from 'vue';
import {message, type TableColumnsType} from 'ant-design-vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import {purchaseDemandApi} from '/@/api/business/scm/purchase-demand-api';
import {SCM_DEMAND_SUMMARY_STATUS_COLOR, SCM_DEMAND_SUMMARY_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import type {DemandSummaryRow, Id} from '../purchase-types';
import {quantity} from '../purchase-form-model';
import {purchaseError} from '../purchase-errors';

/** 数量列统一按四位小数显示，空值显示“—”。 */
const numericColumns = [
  'orderDemandQuantity',
  'onHandQuantity',
  'reservedQuantity',
  'selectedOrderReservedQuantity',
  'otherReservedQuantity',
  'availableQuantity',
  'stockAvailableForSelectedOrders',
  'stockComparisonGap',
  'inTransitQuantity',
  'purchaseCoverageQuantity',
  'netPurchaseGap',
];

const range = ref<[string, string] | undefined>(undefined);
const warehouseId = ref<Id | undefined>(undefined);
const keyword = ref('');
const tableData = ref<DemandSummaryRow[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const error = ref('');
let requestId = 0;

const columns = computed<TableColumnsType<DemandSummaryRow>>(() => [
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '商品规格', dataIndex: 'skuName', width: 130},
  {title: '分类', dataIndex: 'categoryName', align: 'center', width: 130},
  {title: '来源订单数', dataIndex: 'sourceOrderCount', width: 92},
  {title: '来源行数', dataIndex: 'sourceLineCount', width: 92},
  {title: '需求单位', dataIndex: 'demandUnit', width: 95},
  {title: '订单需求量', dataIndex: 'orderDemandQuantity', width: 120},
  {title: '库存单位', dataIndex: 'inventoryUnit', width: 76},
  {title: '现有量', dataIndex: 'onHandQuantity', width: 110},
  {title: '全仓预留', dataIndex: 'reservedQuantity', width: 110},
  {title: '其中本批预留', dataIndex: 'selectedOrderReservedQuantity', width: 130},
  {title: '其他业务预留', dataIndex: 'otherReservedQuantity', width: 130},
  {title: '全仓净可用', dataIndex: 'availableQuantity', width: 110},
  {title: '本批可用', dataIndex: 'stockAvailableForSelectedOrders', width: 110},
  {title: '库存对比差额', dataIndex: 'stockComparisonGap', width: 130},
  {title: '有效在途未收', dataIndex: 'inTransitQuantity', width: 130},
  {title: '已有采购覆盖', dataIndex: 'purchaseCoverageQuantity', width: 130},
  {
    // 表头问号说明：这里只是预览，不代表最终采购建议
    title: () => h('span', [h('span', '净采购缺口'), h(ScmFieldHelp, {label: '净采购缺口', text: '预览不是最终净采购建议，也不生成采购单。'})]),
    dataIndex: 'netPurchaseGap',
    width: 125,
  },
  {title: '计算状态', dataIndex: 'calculationStatus', align: 'center', width: 96},
]);

async function queryData() {
  if (!range.value || range.value.length !== 2) {
    message.warning('请选择统计时间段');
    return;
  }
  if (!warehouseId.value) {
    message.warning('请选择仓库');
    return;
  }
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头的 Alert 提示并可重试
    const r = await purchaseDemandApi.summaryPreview(
      {
        startAt: range.value[0],
        endAt: range.value[1],
        warehouseId: warehouseId.value,
        keyword: keyword.value || null,
        pageNum: pageNum.value,
        pageSize: pageSize.value,
      },
      {suppressGlobalErrorMessage: true}
    );
    if (id === requestId) {
      tableData.value = r.data.list;
      total.value = r.data.total;
    }
  } catch (e) {
    if (id === requestId) {
      error.value = purchaseError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

function onSearch() {
  pageNum.value = 1;
  queryData();
}

function resetQuery() {
  range.value = undefined;
  warehouseId.value = undefined;
  keyword.value = '';
  tableData.value = [];
  total.value = 0;
  error.value = '';
}
</script>
