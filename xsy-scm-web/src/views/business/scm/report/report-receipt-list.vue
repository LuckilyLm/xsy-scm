<!-- 收货确认和库存入账是两步。收货明细按确认时间、入库明细按实际流水时间查询；已确认收货不一定已入库，报表页不提供入库操作。 -->
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
      <a-card size="small" :bordered="false">
        <a-alert v-if="receiptSummaryError" :message="receiptSummaryError" type="error" show-icon class="smart-margin-bottom10">
          <template #action><a-button @click="loadReceiptTab">重试</a-button></template>
        </a-alert>
        <div v-else class="scm-report-summary">
          <span v-if="!receiptSummary">汇总查询中…</span>
          <template v-else>
            <span>收货单 <strong>{{ countText(receiptSummary.receiptCount) }}</strong> 张</span>
            <span>收货行 <strong>{{ countText(receiptSummary.itemCount) }}</strong> 条</span>
            <span>收货数量 <strong>{{ unitQuantitiesText(receiptSummary.quantities) }}</strong></span>
            <span>收货参考金额 <strong class="scm-money">{{ moneyText(receiptSummary.referenceAmount) }}</strong></span>
          </template>
        </div>
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" :loading="receiptExport.exporting" @click="exportReceipt">导出</a-button>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="receiptColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_RECEIPT"
                :refresh="loadReceiptTab"
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
            :scroll="{x: scmColumnsWidth(receiptColumns)}"
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
              <span class="scm-quantity">{{ quantityText(record.receivedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'cumulativeReceivedQuantity'">
              <span class="scm-quantity">{{ quantityText(record.cumulativeReceivedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'remainingQuantity'">
              <span class="scm-quantity">{{ quantityText(record.remainingQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'overReceiptQuantity'">
              <span class="scm-quantity">{{ quantityText(record.overReceiptQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receiptDifference'">
              <span class="scm-quantity">{{ quantityText(record.receiptDifference) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'purchasePrice'">
              <span class="scm-money">{{ moneyText(record.purchasePrice) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receiptReferenceAmount'">
              <span class="scm-money">{{ moneyText(record.receiptReferenceAmount) }}</span>
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
              :exporting="receipt.exporting"
              @change="loadReceiptTab"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <!-- ==================== 入库明细 ==================== -->
    <a-tab-pane key="inbound" tab="入库明细">
      <a-card size="small" :bordered="false">
        <a-alert v-if="inboundSummaryError" :message="inboundSummaryError" type="error" show-icon class="smart-margin-bottom10">
          <template #action><a-button @click="loadInboundTab">重试</a-button></template>
        </a-alert>
        <div v-else class="scm-report-summary">
          <span v-if="!inboundSummary">汇总查询中…</span>
          <template v-else>
            <span>入库流水 <strong>{{ countText(inboundSummary.movementCount) }}</strong> 条</span>
            <span>入库数量 <strong>{{ unitQuantitiesText(inboundSummary.quantities) }}</strong></span>
            <template v-if="canViewCost">
              <span>已知入库成本小计 <strong class="scm-money">{{ moneyText(inboundSummary.knownCostAmount) }}</strong></span>
              <span v-if="inboundSummary.costMissingCount">{{ inboundSummary.costMissingCount }} 条流水缺少成本，金额不代表全部入库成本</span>
            </template>
          </template>
        </div>
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" :loading="inboundExport.exporting" @click="exportInbound">导出</a-button>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="inboundColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_INBOUND"
                :refresh="loadInboundTab"
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
            :scroll="{x: scmColumnsWidth(visibleInboundColumns)}"
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
              <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'unitCost'">
              <span class="scm-money">{{ costAmountText(record.unitCost, record.costMissing) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'costAmount'">
              <span class="scm-money">{{ costAmountText(record.costAmount, record.costMissing) }}</span>
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
              :exporting="inbound.exporting"
              @change="loadInboundTab"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <!-- ==================== 待入库 ==================== -->
    <a-tab-pane key="pending" tab="待入库">
      <a-card size="small" :bordered="false">
        <a-alert v-if="pendingSummaryError" :message="pendingSummaryError" type="error" show-icon class="smart-margin-bottom10">
          <template #action><a-button @click="loadPendingTab">重试</a-button></template>
        </a-alert>
        <div v-else class="scm-report-summary">
          <span v-if="!pendingSummary">汇总查询中…</span>
          <template v-else>
            <span>当前待入库收货单 <strong>{{ countText(pendingSummary.receiptCount) }}</strong> 张</span>
            <span>待入库数量 <strong>{{ unitQuantitiesText(pendingSummary.quantities) }}</strong></span>
            <span>当前状态不受业务日期筛选影响</span>
          </template>
        </div>
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="pendingColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PENDING_PUTAWAY"
                :refresh="loadPendingTab"
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
            :scroll="{x: scmColumnsWidth(pendingColumns)}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'confirmedAt'">
              {{ datetime(record.confirmedAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'skuKindCount'">
              <span class="scm-quantity">{{ countText(record.skuKindCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'quantityText'">
              {{ textOrDash(record.quantityText) }}
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <div class="smart-table-operate scm-table-actions">
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
              @change="loadPendingTab"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>
  </a-tabs>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, onMounted, reactive, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import ReportDateRangePicker from './report-components/report-date-range-picker.vue';
import {reportReceiptApi} from '/@/api/business/scm/report-api';
import {useScmExport} from '../common/use-scm-export';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import {SCM_PUTAWAY_STATUS_ENUM, SCM_RECEIPT_MODE_ENUM} from '/@/constants/business/scm/purchase-const';
import type {
    InboundRow,
    InboundSummary,
    PendingPutawayRow,
    PendingPutawaySummary,
    ReceiptQuery,
    ReceiptRow,
    ReceiptSummary,
    ReceiptUnitQuantity,
} from './report-types';
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
import {createGuardedLoader, createTabLoader} from './use-report-query';
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
const receiptSummary = ref<ReceiptSummary | null>(null);
const inboundSummary = ref<InboundSummary | null>(null);
const pendingSummary = ref<PendingPutawaySummary | null>(null);
const receiptSummaryError = ref('');
const inboundSummaryError = ref('');
const pendingSummaryError = ref('');
const inboundExport = useScmExport(inbound);
const receiptExport = useScmExport(receipt);

const receiptColumns = ref<TableColumnsType<ReceiptRow>>([
    {title: '收货确认时间', dataIndex: 'confirmedAt', width: 190},
    {title: '收货确认人', dataIndex: 'confirmedBy', width: 130},
    {title: '收货单号', dataIndex: 'receiptNo', width: 190},
    {title: '采购单号', dataIndex: 'purchaseOrderNo', width: 190},
    {title: '供应商', dataIndex: 'supplierName', width: 180},
    {title: '仓库', dataIndex: 'warehouseName', width: 150},
    {title: '收货模式', dataIndex: 'receiptMode', align: 'center', width: 130},
    {title: '入库状态', dataIndex: 'putawayStatus', align: 'center', width: 110},
    {title: '商品', dataIndex: 'productName', width: 180},
    {title: '商品规格', dataIndex: 'skuName', width: 140},
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
    {title: '商品规格', dataIndex: 'skuCode', width: 170},
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
    {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 140},
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
const loadReceiptSummary = createGuardedLoader(
    (options) => reportReceiptApi.summary(receiptQuery(receipt), options),
    (data) => { receiptSummary.value = data; }, receiptSummaryError);
const loadInboundSummary = createGuardedLoader(
    (options) => reportReceiptApi.inboundSummary(receiptQuery(inbound), options),
    (data) => { inboundSummary.value = data; }, inboundSummaryError);
const loadPendingSummary = createGuardedLoader(
    (options) => reportReceiptApi.pendingPutawaySummary(receiptQuery(pending), options),
    (data) => { pendingSummary.value = data; }, pendingSummaryError);

function loadReceiptTab() {
    receiptSummary.value = null;
    receiptSummaryError.value = '';
    void loadReceipt();
    void loadReceiptSummary();
}

function loadInboundTab() {
    inboundSummary.value = null;
    inboundSummaryError.value = '';
    void loadInbound();
    void loadInboundSummary();
}

function loadPendingTab() {
    pendingSummary.value = null;
    pendingSummaryError.value = '';
    void loadPending();
    void loadPendingSummary();
}

function unitQuantitiesText(quantities: ReceiptUnitQuantity[] | null | undefined): string {
    if (!quantities?.length) {
        return '—';
    }
    return quantities.map(({quantity, unit}) => `${quantityText(quantity)} ${unit || '未标明单位'}`).join(' / ');
}

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
        receiptSummaryError.value = overLimit;
        inboundSummaryError.value = overLimit;
        pendingSummaryError.value = overLimit;
        return;
    }
    switch (activeTab.value) {
        case 'receipt':
            loadReceiptTab();
            break;
        case 'inbound':
            loadInboundTab();
            break;
        case 'pending':
            loadPendingTab();
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
    if (activeTab.value === 'receipt') {
        enterTab(receipt);
    } else if (activeTab.value === 'inbound') {
        enterTab(inbound);
    } else {
        enterTab(pending);
    }
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
    void receiptExport.run(() => reportReceiptApi.receiptExport(exportQuery()));
}

function exportInbound() {
    void inboundExport.run(() => reportReceiptApi.inboundExport(exportQuery()));
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
.scm-report-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 24px;
  padding: 10px 14px;
  margin-bottom: 10px;
  background: var(--scm-fill, #fafafa);
  border-radius: 4px;
}
</style>
