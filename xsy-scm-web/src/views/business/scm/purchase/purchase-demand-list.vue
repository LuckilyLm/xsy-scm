<!-- C 无需求页（新增文件）。仿 W4 `order-list.vue` 的列表骨架。
适配：`/scm/purchase/demand/**`（A6）、`version`（A8）、
      **Q6a 半开区间生成**（`startAt` / `endAt`，不是 `startTime`/`endTime`）、
      A-D3 **去掉库存抵扣**、A21 半开时间段、`scm:purchase:demand:*`（A22）、
      `scm-purchase-demand-table`（A23）、`purchase-errors`（A24）、
      loading/empty/error/retry（A27）、`v-privilege`（A30）。
分配弹窗**内联在本页**：W5 的文件清单是冻结的 22 个，不新增组件文件。
验收：W5 单测、TS 棘轮与 Playwright。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="来源销售单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.salesOrderNo" placeholder="销售单号" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="商品" class="smart-query-form-item">
        <SkuSelect v-model:value="queryForm.skuId" width="240px" />
      </a-form-item>
      <a-form-item label="需求状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_DEMAND_STATUS_ENUM" v-model:value="queryForm.status" width="150px" />
      </a-form-item>
      <a-form-item label="需求日期" class="smart-query-form-item">
        <a-range-picker v-model:value="dateRange" value-format="YYYY-MM-DD" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:purchase:demand:query'">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action><a-button @click="queryData">重试</a-button></template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button type="primary" v-privilege="'scm:purchase:demand:generate'" @click="generateOpen = true">
          汇总生成需求
        </a-button>
        <span class="hint">需求来自「已确认」的销售订单行，重复汇总不会重复生成</span>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PURCHASE_DEMAND" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      :id="SCM_PURCHASE_TABLE_ID.DEMAND"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{ x: 1500 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag>{{ SCM_DEMAND_STATUS_ENUM[record.status]?.desc }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'requiredQuantity'">
          <span class="num">{{ quantity(record.requiredQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'allocatedQuantity'">
          <span class="num">{{ quantity(record.allocatedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unallocatedQuantity'">
          <span class="num">{{ quantity(record.unallocatedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'supplierName'">{{ record.supplierName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'warehouseName'">{{ record.warehouseName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button
              v-if="Number(record.unallocatedQuantity ?? '0') > 0"
              type="link"
              v-privilege="'scm:purchase:demand:allocate'"
              @click="openAllocate(record)"
            >
              分配到采购行
            </a-button>
            <span v-else class="hint">已分配完</span>
          </div>
        </template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
        show-size-changer
        show-quick-jumper
        v-model:current="queryForm.pageNum"
        v-model:page-size="queryForm.pageSize"
        :total="total"
        @change="queryData"
        :show-total="(n: number) => `共${n}条`"
      />
    </div>
  </a-card>

  <DemandGenerateModal :open="generateOpen" @close="generateOpen = false" @generated="queryData" />

  <!-- 分配到采购行：`allocate` 的入参是 `purchaseOrderItemId`，因此要先选单再选行 -->
  <a-modal
    :open="alloc.open"
    :title="`分配需求到采购行${alloc.demand?.salesOrderNoSnapshot ? '（' + alloc.demand.salesOrderNoSnapshot + '）' : ''}`"
    width="760px"
    :confirm-loading="alloc.saving"
    @ok="submitAllocate"
    @cancel="alloc.open = false"
  >
    <a-alert v-if="alloc.error" :message="alloc.error" type="error" show-icon />
    <a-form layout="vertical">
      <a-form-item label="采购单（仅已提交 / 部分收货）" name="orderId" required>
        <a-select
          v-model:value="alloc.orderId"
          show-search
          allow-clear
          :filter-option="false"
          :loading="alloc.orderLoading"
          :options="alloc.orderOptions"
          placeholder="输入采购单号搜索"
          @search="loadOrders"
          @change="orderChanged"
        />
      </a-form-item>
      <a-form-item label="采购行（同一 SKU）" name="purchaseOrderItemId" required>
        <a-select
          v-model:value="alloc.purchaseOrderItemId"
          :options="alloc.itemOptions"
          :loading="alloc.itemLoading"
          placeholder="请选择采购行"
        />
      </a-form-item>
      <a-form-item label="分配数量" name="quantity" required>
        <a-input-number string-mode :precision="4" :min="'0.0001'" v-model:value="alloc.quantity" style="width: 100%" />
        <span class="hint">该需求剩余可分配 {{ quantity(alloc.demand?.unallocatedQuantity) }}</span>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import { purchaseDemandApi } from '/@/api/business/scm/purchase-demand-api';
import { purchaseOrderApi } from '/@/api/business/scm/purchase-order-api';
import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
import { SCM_DEMAND_STATUS_ENUM, SCM_PURCHASE_TABLE_ID } from '/@/constants/business/scm/purchase-const';
import type { Demand, DemandQuery, Id } from './purchase-types';
import { fixed, quantity } from './purchase-form-model';
import { purchaseError } from './purchase-errors';
import DemandGenerateModal from './components/purchase-demand-generate-modal.vue';

const RECEIVABLE = ['SUBMITTED', 'PARTIALLY_RECEIVED'];

const queryForm = reactive<DemandQuery>({ pageNum: 1, pageSize: 20 });
const dateRange = ref<[string, string] | undefined>(undefined);
const tableData = ref<Demand[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const generateOpen = ref(false);
let requestId = 0;

const alloc = reactive({
  open: false,
  saving: false,
  error: '',
  demand: undefined as Demand | undefined,
  orderLoading: false,
  itemLoading: false,
  orderOptions: [] as { value: Id; label: string }[],
  itemOptions: [] as { value: Id; label: string }[],
  orderId: undefined as Id | undefined,
  purchaseOrderItemId: undefined as Id | undefined,
  quantity: '0.0000',
  supplierId: undefined as Id | undefined,
  warehouseId: undefined as Id | undefined,
});

const columns = computed<TableColumnsType<Demand>>(() => [
  { title: '来源销售单号', dataIndex: 'salesOrderNoSnapshot', width: 200 },
  { title: '商品', dataIndex: 'productName', width: 150 },
  { title: '规格', dataIndex: 'skuName', width: 130 },
  { title: '需求单位', dataIndex: 'demandUnit', width: 95 },
  { title: '需求量', dataIndex: 'requiredQuantity', align: 'right', width: 115 },
  { title: '已分配', dataIndex: 'allocatedQuantity', align: 'right', width: 115 },
  { title: '未分配', dataIndex: 'unallocatedQuantity', align: 'right', width: 115 },
  { title: '状态', dataIndex: 'status', align: 'center', width: 110 },
  { title: '供应商', dataIndex: 'supplierName', width: 150 },
  { title: '仓库', dataIndex: 'warehouseName', width: 130 },
  { title: '需求日期', dataIndex: 'demandDate', width: 120 },
  { title: '操作', dataIndex: 'action', align: 'right', fixed: 'right', width: 160 },
]);

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    queryForm.demandDateFrom = dateRange.value?.[0];
    queryForm.demandDateTo = dateRange.value?.[1];
    const r = await purchaseDemandApi.query(queryForm);
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
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.salesOrderNo = undefined;
  queryForm.skuId = undefined;
  queryForm.status = undefined;
  queryForm.demandDateFrom = undefined;
  queryForm.demandDateTo = undefined;
  dateRange.value = undefined;
  onSearch();
}

// ------------------------------------------------------------------
// 分配到采购行
// ------------------------------------------------------------------

async function openAllocate(demand: Demand) {
  alloc.open = true;
  alloc.error = '';
  alloc.demand = demand;
  alloc.orderId = undefined;
  alloc.purchaseOrderItemId = undefined;
  alloc.itemOptions = [];
  alloc.quantity = fixed(demand.unallocatedQuantity ?? '0');
  await loadOrders('');
}

async function loadOrders(keyword: string) {
  alloc.orderLoading = true;
  try {
    const r = await purchaseOrderApi.query({ pageNum: 1, pageSize: 20, orderNo: keyword || undefined });
    alloc.orderOptions = r.data.list
      .filter((o) => RECEIVABLE.includes(o.status ?? ''))
      .map((o) => ({ value: o.id!, label: `${o.orderNo}（${o.supplierName ?? ''}）` }));
  } catch (e) {
    alloc.error = purchaseError(e);
  } finally {
    alloc.orderLoading = false;
  }
}

/** 选单后拉详情，只留**同一 SKU** 的行（跨 SKU 会 40995）。 */
async function orderChanged(orderId: Id | undefined) {
  alloc.purchaseOrderItemId = undefined;
  alloc.itemOptions = [];
  if (!orderId) {
    return;
  }
  alloc.itemLoading = true;
  try {
    const detail = (await purchaseOrderApi.detail(orderId)).data;
    alloc.supplierId = detail.supplierId;
    alloc.warehouseId = detail.warehouseId;
    alloc.itemOptions = (detail.items ?? [])
      .filter((item) => String(item.skuId) === String(alloc.demand?.skuId))
      .map((item) => ({
        value: item.id!,
        label: `第 ${(item.sortOrder ?? 0) + 1} 行 · 采购量 ${item.plannedQuantity} ${item.purchaseUnit ?? ''}`,
      }));
    if (!alloc.itemOptions.length) {
      alloc.error = '该采购单没有此 SKU 的采购行，请换一张单或先在采购单里补行';
    }
  } catch (e) {
    alloc.error = purchaseError(e);
  } finally {
    alloc.itemLoading = false;
  }
}

async function submitAllocate() {
  const demand = alloc.demand;
  if (!demand) {
    return;
  }
  if (!alloc.purchaseOrderItemId) {
    alloc.error = '请选择采购行';
    return;
  }
  if (!/^\d{1,14}\.\d{4}$/.test(alloc.quantity) || Number(alloc.quantity) <= 0) {
    alloc.error = '分配数量必须为大于零的四位定点数';
    return;
  }
  alloc.error = '';
  alloc.saving = true;
  try {
    await purchaseDemandApi.allocate({
      demandId: demand.id,
      purchaseOrderItemId: alloc.purchaseOrderItemId,
      quantity: fixed(alloc.quantity),
      supplierId: alloc.supplierId!,
      warehouseId: alloc.warehouseId!,
      version: demand.version ?? 0,
    });
    message.success('需求已分配');
    alloc.open = false;
    await queryData();
  } catch (e) {
    alloc.error = purchaseError(e);
  } finally {
    alloc.saving = false;
  }
}

onMounted(queryData);
</script>

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.hint {
  color: var(--ant-color-text-secondary);
  font-size: 12px;
  margin-left: 8px;
}
</style>
