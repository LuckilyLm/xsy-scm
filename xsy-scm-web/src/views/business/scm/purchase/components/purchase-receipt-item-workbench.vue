<!-- Wave 2B §6.6 按商品收货工作台（新增文件，只读辅助视图）。
把「可收货」采购单（后端固定为 SUBMITTED / PARTIALLY_RECEIVED，不开放状态入参）的行，
按 `skuId + 采购单位` 跨单归并成一张欠收 / 超收全景，方便收货员按商品盘点而非按单据翻找。
数量（计划 / 已收 / 欠收 / 超收）全部由后端逐行裁剪后以四位定点字符串聚合下发，本页只渲染、绝不重算；
`pendingQuantity` 与 `overReceiptQuantity` 分别来自 SUM(max(计划-已收,0)) / SUM(max(已收-计划,0))，二者相加不等于计划或已收。
无新增迁移：作为「收货单」页的一个 Tab 内联渲染，复用 `scm:purchase:receipt:query` 权限。
本视图不改任何采购 / 收货状态——收货仍走「按单据」页的确认 / 入库命令。
验收：Wave 2B 后端 IT、TS 棘轮、契约单测与 Playwright。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="供应商" class="smart-query-form-item">
        <SupplierSelect v-model:value="supplierId" width="200px"/>
      </a-form-item>
      <a-form-item label="收货仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="warehouseId" width="200px" placeholder="请选择仓库"/>
      </a-form-item>
      <a-form-item label="采购单号" class="smart-query-form-item">
        <a-input v-model:value="orderNo" placeholder="采购单号" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="商品关键字" class="smart-query-form-item">
        <a-input v-model:value="keyword" placeholder="SPU 编码 / 名称 / SKU 编码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:purchase:receipt:query'">查询</a-button>
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
        message="只读工作台"
        description="把待收采购单（已提交 / 部分收货）按商品 + 采购单位归并，展示跨单的计划 / 已收 / 欠收 / 超收汇总；本页只读，收货请切到「按单据」。欠收与超收按每一行裁剪后求和，二者相加不等于计划或已收总量。"
    />
    <a-table
        id="scm-purchase-receipt-item-workbench-table"
        size="small"
        :data-source="tableData"
        :columns="columns"
        :row-key="rowKey"
        bordered
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1500 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="numericColumns.includes(column.dataIndex)">
          <span class="num">{{ quantity(record[column.dataIndex]) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'productType'">
          <a-tag>{{ record.productType === 'NON_STANDARD' ? '非标品' : '标品' }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'purchaseUnit'">
          {{ record.purchaseUnit || '—' }}
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
import {computed, onMounted, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {purchaseReceiptApi} from '/@/api/business/scm/purchase-receipt-api';
import type {Id, ReceiptItemWorkbenchRow} from '../purchase-types';
import {quantity} from '../purchase-form-model';
import {purchaseError} from '../purchase-errors';

/** 后端下发的四位定点字符串列，统一走 `quantity` 渲染（null → —）。 */
const numericColumns = ['plannedQuantity', 'receivedQuantity', 'pendingQuantity', 'overReceiptQuantity'];

const supplierId = ref<Id | undefined>(undefined);
const warehouseId = ref<Id | undefined>(undefined);
const orderNo = ref('');
const keyword = ref('');
const tableData = ref<ReceiptItemWorkbenchRow[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const error = ref('');
let requestId = 0;

/** 聚合行按 `skuId + 采购单位` 归并，故 row-key 是二者复合，单靠 skuId 不唯一。 */
function rowKey(row: ReceiptItemWorkbenchRow) {
  return `${row.skuId}::${row.purchaseUnit ?? ''}`;
}

const columns = computed<TableColumnsType<ReceiptItemWorkbenchRow>>(() => [
  {title: 'SKU 编码', dataIndex: 'skuCode', width: 150},
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '规格', dataIndex: 'skuName', width: 130},
  {title: '采购单位', dataIndex: 'purchaseUnit', width: 95},
  {title: '商品类型', dataIndex: 'productType', align: 'center', width: 100},
  {title: '命中采购单数', dataIndex: 'orderCount', align: 'right', width: 120},
  {title: '命中行数', dataIndex: 'lineCount', align: 'right', width: 100},
  {title: '计划量', dataIndex: 'plannedQuantity', align: 'right', width: 120},
  {title: '已收量', dataIndex: 'receivedQuantity', align: 'right', width: 120},
  {title: '欠收量', dataIndex: 'pendingQuantity', align: 'right', width: 120},
  {title: '超收量', dataIndex: 'overReceiptQuantity', align: 'right', width: 120},
]);

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await purchaseReceiptApi.itemWorkbench({
      supplierId: supplierId.value,
      warehouseId: warehouseId.value,
      orderNo: orderNo.value || undefined,
      keyword: keyword.value || undefined,
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
  supplierId.value = undefined;
  warehouseId.value = undefined;
  orderNo.value = '';
  keyword.value = '';
  onSearch();
}

onMounted(queryData);
</script>

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.banner {
  margin-bottom: 12px;
}
</style>
