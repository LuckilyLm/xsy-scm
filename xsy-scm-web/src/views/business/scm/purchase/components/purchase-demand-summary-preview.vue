<!-- Wave 2A §6A 订单汇总 / 库存缺口预览（新增文件，只读辅助决策）。
只读：不建需求、不改 `PurchaseDemandService.generate()` 语义，取数口径与其一致（实发量 actual_quantity）。
数量（现有 / 预留分段 / 本批可用 / 对比差额）全部由后端 SQL 用 BigDecimal 算好、以四位定点字符串下发，
本页只渲染、绝不重算（§6A.4）；`UNIT_MISMATCH` 行差额为 null（Q13 单位门禁，不猜折算率）。
接口按 `scm:purchase:demand:query` AND `scm:inventory:balance:query` 鉴权（返回体含库存量），
这里的按钮 v-privilege 只是体验，不是权限边界。
无新增迁移：作为「采购需求」页的一个 Tab 内联渲染，复用其路由 / 菜单。
验收：Wave 2A 后端 IT、TS 棘轮与 Playwright。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="统计时间段" class="smart-query-form-item">
        <a-range-picker
            v-model:value="range"
            show-time
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            style="width: 380px"
        />
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="warehouseId" width="200px" placeholder="请选择仓库"/>
      </a-form-item>
      <a-form-item label="商品关键字" class="smart-query-form-item">
        <a-input v-model:value="keyword" placeholder="SPU 编码 / 名称 / SKU 编码" allow-clear @pressEnter="onSearch"/>
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
    <a-alert
        class="banner"
        type="info"
        show-icon
        message="只读预览"
        description="这是「已确认订单」与当前库存/预留的对比结果，不是最终净采购建议。本批订单自身已预留的量不计入占用（见「其中本批预留」），比对用的是「本批可用」。在途采购是否抵扣、已履约量如何扣减尚未裁决，故均未计入。本操作不生成需求、不改写任何数据。"
    />
    <a-table
        id="scm-purchase-demand-summary-preview-table"
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="skuId"
        bordered
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 2000 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'calculationStatus'">
          <a-tag :color="SCM_DEMAND_SUMMARY_STATUS_COLOR[record.calculationStatus] || 'default'">
            {{ SCM_DEMAND_SUMMARY_STATUS_ENUM[record.calculationStatus]?.desc || record.calculationStatus }}
          </a-tag>
        </template>
        <template v-else-if="numericColumns.includes(column.dataIndex)">
          <span class="num">{{ quantity(record[column.dataIndex]) }}</span>
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
import {computed, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {purchaseDemandApi} from '/@/api/business/scm/purchase-demand-api';
import {SCM_DEMAND_SUMMARY_STATUS_COLOR, SCM_DEMAND_SUMMARY_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import type {DemandSummaryRow, Id} from '../purchase-types';
import {quantity} from '../purchase-form-model';
import {purchaseError} from '../purchase-errors';

/** 这些列是后端下发的四位定点字符串，统一走 `quantity` 渲染（null → —）。 */
const numericColumns = [
  'orderDemandQuantity',
  'onHandQuantity',
  'reservedQuantity',
  'selectedOrderReservedQuantity',
  'otherReservedQuantity',
  'availableQuantity',
  'stockAvailableForSelectedOrders',
  'stockComparisonGap',
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
  {title: 'SKU 编码', dataIndex: 'skuCode', width: 150},
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '规格', dataIndex: 'skuName', width: 130},
  {title: '分类', dataIndex: 'categoryName', width: 130},
  {title: '来源订单数', dataIndex: 'sourceOrderCount', align: 'right', width: 110},
  {title: '来源行数', dataIndex: 'sourceLineCount', align: 'right', width: 100},
  {title: '需求单位', dataIndex: 'demandUnit', width: 95},
  {title: '订单需求量', dataIndex: 'orderDemandQuantity', align: 'right', width: 120},
  {title: '库存单位', dataIndex: 'inventoryUnit', width: 95},
  {title: '现有量', dataIndex: 'onHandQuantity', align: 'right', width: 110},
  {title: '全仓预留', dataIndex: 'reservedQuantity', align: 'right', width: 110},
  {title: '其中本批预留', dataIndex: 'selectedOrderReservedQuantity', align: 'right', width: 130},
  {title: '其他业务预留', dataIndex: 'otherReservedQuantity', align: 'right', width: 130},
  {title: '全仓净可用', dataIndex: 'availableQuantity', align: 'right', width: 110},
  {title: '本批可用', dataIndex: 'stockAvailableForSelectedOrders', align: 'right', width: 110},
  {title: '库存对比差额', dataIndex: 'stockComparisonGap', align: 'right', width: 130},
  {title: '计算状态', dataIndex: 'calculationStatus', align: 'center', width: 120},
]);

async function queryData() {
  if (!range.value || range.value.length !== 2) {
    error.value = '请选择统计时间段';
    return;
  }
  if (!warehouseId.value) {
    error.value = '请选择仓库';
    return;
  }
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await purchaseDemandApi.summaryPreview({
      startAt: range.value[0],
      endAt: range.value[1],
      warehouseId: warehouseId.value,
      keyword: keyword.value || null,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    });
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

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.banner {
  margin-bottom: 12px;
}
</style>
