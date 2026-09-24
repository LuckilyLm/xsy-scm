<!--
  收货与入库（Finance R0 计划 §17–§20）。

  这是 XSY 与参考系统最需要「借结构、不抄语义」的一页：**收货确认与库存入账是两件事**。
  `purchase_receipt.status = CONFIRMED` 只是商业确认；库存真正入账由 `putaway_status = COMPLETED`
  决定（`DIRECT` 同事务完成，`WAREHOUSE_CONFIRM` 要仓库再操作一次）。因此：

  - 收货明细的时间列叫「收货确认时间」（`confirmed_at`），入库明细的时间列叫「入库时间」
    （`inventory_movement.occurred_at`），**两个 Tab 的日期筛选落在不同事实上**；
  - 收货明细必须显示「入库状态」，不能把已确认收货显示成已入库；
  - 待入库 Tab 只读：不提供任何「确认入库」写入口，入库动作留在采购收货页，
    报表页写库存会让同一笔入库有两个入口。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="业务日期" class="smart-query-form-item">
        <ReportDateRangePicker v-model:value="dateRange"/>
      </a-form-item>
      <a-form-item label="供应商" class="smart-query-form-item">
        <SupplierSelect v-model:value="filters.supplierId" width="200px"/>
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="filters.warehouseId" width="180px"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.PURCHASE_QUERY" @click="onSearch">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
        <a-button class="smart-margin-left10" @click="advanced = !advanced">
          {{ advanced ? '收起高级筛选' : '展开高级筛选' }}
        </a-button>
      </a-form-item>
    </a-row>
    <a-row v-if="advanced" class="smart-query-form-row">
      <a-form-item label="收货模式" class="smart-query-form-item">
        <SmartEnumSelect v-model:value="filters.receiptMode" enum-name="SCM_RECEIPT_MODE_ENUM" width="170px"/>
      </a-form-item>
      <a-form-item label="入库状态" class="smart-query-form-item">
        <SmartEnumSelect v-model:value="filters.putawayStatus" enum-name="SCM_PUTAWAY_STATUS_ENUM" width="140px"/>
      </a-form-item>
      <a-form-item label="关键字" class="smart-query-form-item">
        <a-input v-model:value="filters.keyword" placeholder="收货单号 / 采购单号 / 商品" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
    </a-row>
  </a-form>

  <a-tabs v-model:activeKey="activeTab" class="smart-margin-top10" @change="onTabChange">
    <!-- ==================== 收货明细 ==================== -->
    <a-tab-pane key="receipt" tab="收货明细">
      <a-alert
          message="日期筛选的是「收货确认时间」；本 Tab 的每一行是收货单行，不是库存入账记录。"
          type="info"
          show-icon
          class="smart-margin-bottom10"
      />
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" @click="exportReceipt">导出</a-button>
            <a-typography-text type="secondary" class="smart-margin-left10">
              「收货参考金额」= 本次收货数量 × 采购单价，不是应付金额。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="receiptColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_RECEIPT"
                :refresh="loadReceipt"
            />
          </div>
        </a-row>
        <a-alert v-if="receipt.error" :message="receipt.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadReceipt">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.RECEIPT"
            size="small"
            :data-source="receipt.rows"
            :columns="receiptColumns"
            row-key="receiptItemId"
            bordered
            :loading="receipt.loading"
            :pagination="false"
            :locale="{emptyText: '暂无收货明细'}"
            :scroll="{x: 2500}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'confirmedAt'">
              {{ datetime(record.confirmedAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'receiptNo'">
              <a v-if="record.receiptId" @click="openReceipt(record.receiptNo)">{{ record.receiptNo }}</a>
              <span v-else>{{ record.receiptNo ?? '—' }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receiptMode'">
              <a-tag>{{ enumDescText(record.receiptMode, SCM_RECEIPT_MODE_ENUM) }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'putawayStatus'">
              <a-tag :color="record.putawayStatus === 'COMPLETED' ? 'green' : 'orange'">
                {{ enumDescText(record.putawayStatus, SCM_PUTAWAY_STATUS_ENUM) }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'receivedQuantity'">
              <span class="num">{{ quantityText(record.receivedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'cumulativeReceivedQuantity'">
              <span class="num">{{ quantityText(record.cumulativeReceivedQuantity) }}</span>
              <a-tooltip title="该采购行在全部收货单上的累计已收量，不能在本页逐行相加">
                <InfoCircleOutlined class="report-hint-icon" aria-hidden="true"/>
              </a-tooltip>
            </template>
            <template v-else-if="column.dataIndex === 'remainingQuantity'">
              <span class="num">{{ quantityText(record.remainingQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'overReceiptQuantity'">
              <span class="num">{{ quantityText(record.overReceiptQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receiptDifference'">
              <span class="num">{{ quantityText(record.receiptDifference) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'purchasePrice'">
              <span class="num">{{ moneyText(record.purchasePrice) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receiptReferenceAmount'">
              <span class="num">{{ moneyText(record.receiptReferenceAmount) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="receipt.pageNum"
              v-model:page-size="receipt.pageSize"
              :total="receipt.total"
              @change="loadReceipt"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <!-- ==================== 入库明细 ==================== -->
    <a-tab-pane key="inbound" tab="入库明细">
      <a-alert
          message="日期筛选的是「入库时间」（PURCHASE_IN 流水的 occurred_at）；未做仓库二次入库的收货单不会出现在这里。"
          type="info"
          show-icon
          class="smart-margin-bottom10"
      />
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" @click="exportInbound">导出</a-button>
            <a-typography-text type="secondary" class="smart-margin-left10">
              单位成本缺失的行显示 — 而不是 0：0 是「成本确实是零」，— 才是「没有这个事实」。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="inboundColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_INBOUND"
                :refresh="loadInbound"
            />
          </div>
        </a-row>
        <a-alert v-if="inbound.error" :message="inbound.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadInbound">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.INBOUND"
            size="small"
            :data-source="inbound.rows"
            :columns="visibleInboundColumns"
            row-key="movementId"
            bordered
            :loading="inbound.loading"
            :pagination="false"
            :locale="{emptyText: '暂无入库明细'}"
            :scroll="{x: 1900}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'occurredAt'">
              {{ datetime(record.occurredAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'receiptNo'">
              <a v-if="record.receiptNo" @click="openReceipt(record.receiptNo)">{{ record.receiptNo }}</a>
              <span v-else>—</span>
            </template>
            <template v-else-if="column.dataIndex === 'quantity'">
              <span class="num">{{ quantityText(record.quantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'unitCost'">
              <span class="num">{{ costAmountText(record.unitCost, record.costMissing) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'costAmount'">
              <span class="num">{{ costAmountText(record.costAmount, record.costMissing) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="inbound.pageNum"
              v-model:page-size="inbound.pageSize"
              :total="inbound.total"
              @change="loadInbound"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <!-- ==================== 待入库 ==================== -->
    <a-tab-pane key="pending" tab="待入库">
      <a-alert
          message="只列「仓库确认入库 + 收货已确认 + 入库状态待入库」的收货单；本页只读，请到采购收货页办理入库。"
          type="info"
          show-icon
          class="smart-margin-bottom10"
      />
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-typography-text type="secondary">
              收货数量按采购单位分组显示（如 12kg / 3箱）：一张单可以有多种单位，相加没有量纲。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="pendingColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PENDING_PUTAWAY"
                :refresh="loadPending"
            />
          </div>
        </a-row>
        <a-alert v-if="pending.error" :message="pending.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadPending">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.PENDING_PUTAWAY"
            size="small"
            :data-source="pending.rows"
            :columns="pendingColumns"
            row-key="receiptId"
            bordered
            :loading="pending.loading"
            :pagination="false"
            :locale="{emptyText: '暂无待入库收货单'}"
            :scroll="{x: 1250}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'confirmedAt'">
              {{ datetime(record.confirmedAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'skuKindCount'">
              <span class="num">{{ countText(record.skuKindCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'quantityText'">
              {{ textOrDash(record.quantityText) }}
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <div class="smart-table-operate">
                <!-- 只有一个跳转：报表不提供「确认入库」写入口 -->
                <a-button type="link" @click="openReceipt(record.receiptNo)">查看原收货单</a-button>
              </div>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="pending.pageNum"
              v-model:page-size="pending.pageSize"
              :total="pending.total"
              @change="loadPending"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>
  </a-tabs>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import {InfoCircleOutlined} from '@ant-design/icons-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import ReportDateRangePicker from './report-components/report-date-range-picker.vue';
import {reportReceiptApi} from '/@/api/business/scm/report-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import {SCM_PUTAWAY_STATUS_ENUM, SCM_RECEIPT_MODE_ENUM} from '/@/constants/business/scm/purchase-const';
import type {InboundRow, PendingPutawayRow, ReceiptQuery, ReceiptRow} from './report-types';
import {
    buildReportQuery,
    countText,
    createTabView,
    defaultDateRange,
    enumDescText,
    enterTab,
    filterCostColumns,
    rangeFromQuery,
    rangeOverLimitError,
    textOrDash,
} from './report-model';
import {moneyText, quantityText} from '../inventory/inventory-model';
import {datetime} from '../common/scm-display';
import {useReportPermission} from './use-report-permission';
import {createTabLoader} from './use-report-query';
import type {DateRange} from './report-model';

const PERM = SCM_REPORT_PERMISSION;
const route = useRoute();
const router = useRouter();
const {canViewCost} = useReportPermission();

type ReceiptTab = 'receipt' | 'inbound' | 'pending';

const activeTab = ref<ReceiptTab>('receipt');
const dateRange = ref<DateRange | undefined>();
const filters = reactive<Omit<ReceiptQuery, 'pageNum' | 'pageSize' | 'startDate' | 'endDate'>>({});
const advanced = ref(false);

const receipt = reactive(createTabView<ReceiptRow>());
const inbound = reactive(createTabView<InboundRow>());
const pending = reactive(createTabView<PendingPutawayRow>());

const receiptColumns = ref<TableColumnsType<ReceiptRow>>([
    {title: '收货确认时间', dataIndex: 'confirmedAt', width: 190},
    {title: '收货单号', dataIndex: 'receiptNo', width: 190},
    {title: '采购单号', dataIndex: 'purchaseOrderNo', width: 190},
    {title: '供应商', dataIndex: 'supplierName', width: 180},
    {title: '仓库', dataIndex: 'warehouseName', width: 150},
    {title: '收货模式', dataIndex: 'receiptMode', align: 'center', width: 130},
    {title: '入库状态', dataIndex: 'putawayStatus', align: 'center', width: 110},
    {title: 'SPU 编码', dataIndex: 'spuCode', width: 140},
    {title: '商品', dataIndex: 'productName', width: 180},
    {title: 'SKU 编码', dataIndex: 'skuCode', width: 170},
    {title: '规格', dataIndex: 'skuName', width: 140},
    {title: '采购单位', dataIndex: 'purchaseUnit', align: 'center', width: 100},
    {title: '本次收货数量', dataIndex: 'receivedQuantity', align: 'right', width: 140},
    {title: '累计收货数量', dataIndex: 'cumulativeReceivedQuantity', align: 'right', width: 150},
    {title: '剩余数量', dataIndex: 'remainingQuantity', align: 'right', width: 120},
    {title: '超收数量', dataIndex: 'overReceiptQuantity', align: 'right', width: 120},
    {title: '收货差异', dataIndex: 'receiptDifference', align: 'right', width: 120},
    {title: '采购单价', dataIndex: 'purchasePrice', align: 'right', width: 130},
    {title: '收货参考金额', dataIndex: 'receiptReferenceAmount', align: 'right', width: 150},
]);

const inboundColumns = ref<TableColumnsType<InboundRow>>([
    {title: '入库时间', dataIndex: 'occurredAt', width: 190},
    {title: '仓库', dataIndex: 'warehouseName', width: 150},
    {title: '收货单号', dataIndex: 'receiptNo', width: 190},
    {title: '采购单号', dataIndex: 'purchaseOrderNo', width: 190},
    {title: '供应商', dataIndex: 'supplierName', width: 180},
    {title: '商品', dataIndex: 'productName', width: 180},
    {title: 'SKU', dataIndex: 'skuCode', width: 170},
    {title: '单位', dataIndex: 'unit', align: 'center', width: 90},
    {title: '入库数量', dataIndex: 'quantity', align: 'right', width: 130},
    {title: '入库单位成本', dataIndex: 'unitCost', align: 'right', width: 150},
    {title: '入库成本金额', dataIndex: 'costAmount', align: 'right', width: 150},
    {title: '操作人', dataIndex: 'operator', width: 120},
]);

const pendingColumns = ref<TableColumnsType<PendingPutawayRow>>([
    {title: '收货单号', dataIndex: 'receiptNo', width: 190},
    {title: '采购单号', dataIndex: 'purchaseOrderNo', width: 190},
    {title: '供应商', dataIndex: 'supplierName', width: 180},
    {title: '仓库', dataIndex: 'warehouseName', width: 150},
    {title: '确认时间', dataIndex: 'confirmedAt', width: 190},
    {title: '商品种类', dataIndex: 'skuKindCount', align: 'right', width: 110},
    {title: '收货数量', dataIndex: 'quantityText', width: 220},
    {title: '操作', dataIndex: 'action', align: 'right', fixed: 'right', width: 140},
]);

/** 入库成本两列受成本权限控制。 */
const COST_INDEXES = ['unitCost', 'costAmount'];
const visibleInboundColumns = computed(() => filterCostColumns(inboundColumns.value, COST_INDEXES, canViewCost.value));

function receiptQuery(tab: {pageNum: number; pageSize: number}): ReceiptQuery {
    return buildReportQuery<ReceiptQuery>(dateRange.value, {...filters}, tab);
}

function exportQuery(): Partial<ReceiptQuery> {
    return buildReportQuery<Partial<ReceiptQuery>>(dateRange.value, {...filters});
}

const loadReceipt = createTabLoader(receipt, () => receiptQuery(receipt), reportReceiptApi.query);
const loadInbound = createTabLoader(inbound, () => receiptQuery(inbound), reportReceiptApi.inboundQuery);
const loadPending = createTabLoader(pending, () => receiptQuery(pending), reportReceiptApi.pendingPutawayQuery);

/**
 * 成本单元格文案：`costMissing` 为真或无成本权限时都是 `—`。
 *
 * 后端用显式布尔而不是留空表达「这一行没有成本事实」，所以这里必须优先看它 ——
 * 把缺成本读成 0 会直接把成本核对带偏。
 */
function costAmountText(value: string | null | undefined, costMissing: boolean | null | undefined): string {
    if (costMissing || !canViewCost.value) {
        return '—';
    }
    return moneyText(value);
}

function queryActiveTab() {
    const overLimit = rangeOverLimitError(dateRange.value);
    if (overLimit) {
        receipt.error = overLimit;
        inbound.error = overLimit;
        pending.error = overLimit;
        return;
    }
    switch (activeTab.value) {
        case 'receipt':
            void loadReceipt();
            break;
        case 'inbound':
            void loadInbound();
            break;
        case 'pending':
            void loadPending();
            break;
        default:
            break;
    }
}

function onTabChange() {
    if (activeTab.value === 'receipt') {
        enterTab(receipt);
    } else if (activeTab.value === 'inbound') {
        enterTab(inbound);
    } else {
        enterTab(pending);
    }
    queryActiveTab();
}

function onSearch() {
    queryActiveTab();
}

function resetQuery() {
    filters.supplierId = undefined;
    filters.warehouseId = undefined;
    filters.purchaseOrderId = undefined;
    filters.keyword = undefined;
    filters.receiptMode = undefined;
    filters.putawayStatus = undefined;
    dateRange.value = defaultDateRange();
    enterTab(receipt);
    enterTab(inbound);
    enterTab(pending);
    onSearch();
}

function exportReceipt() {
    void reportReceiptApi.receiptExport(exportQuery());
}

function exportInbound() {
    void reportReceiptApi.inboundExport(exportQuery());
}

/** 复用采购收货页作为「原收货单」视图：不在报表里再造一张收货单详情页。 */
function openReceipt(receiptNo: string | null | undefined) {
    if (!receiptNo) {
        return;
    }
    void router.push({path: '/purchase/purchase-receipt-list', query: {receiptNo}});
}

onMounted(() => {
    dateRange.value = rangeFromQuery(route.query) ?? defaultDateRange();
    queryActiveTab();
});
</script>

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.report-hint-icon {
  color: var(--ant-color-text-tertiary);
  margin-left: 4px;
}
</style>
