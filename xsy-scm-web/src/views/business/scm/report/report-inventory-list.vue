<!--
  库存分析（Finance R0 计划 §21–§25）：库存流水 / 损耗分析 / 当前库存价值 / 收发存（数量版）。

  三条边界：
  1. **方向不重新发明**：流水的「入 / 出」由 `SCM_INVENTORY_MOVEMENT_INBOUND_TYPES`
     （与后端 `ScmInventoryMovementTypeEnum.getInbound()` 同源）派生，本页不再维护第二份
     IN / OUT 清单；`quantity` 恒为正。
  2. **成本受 `scm:report:cost:query` 控制**：没有该权限时单位成本 / 成本金额整列不出现，
     「当前库存价值」整个 Tab 也不出现；后端同时已把这些字段置 null，前端不会看到别人的成本。
  3. **收发存只有数量**：计划 §25 明确 R0 不做历史期初 / 期末均价与金额 ——
     流水存的是「本次 movement 的 unit_cost」，不是「每次 movement 后的 avg_cost」，
     用它回算历史均价就是伪造成本。跨单位的数量也永不相加。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <!--
        「当前库存价值」是当前时点快照，与日期区间无关，因此在该 Tab 下**收掉日期筛选**：
        留着它会被读成「这段区间的库存金额」。后端日期字段必填，所以请求仍带当前区间，
        只是那个值在该 Tab 不参与计算（计划 §24）。
      -->
      <a-form-item v-if="activeTab !== 'value'" label="发生日期" class="smart-query-form-item">
        <ReportDateRangePicker v-model:value="dateRange"/>
      </a-form-item>
      <a-form-item v-else label="库存价值" class="smart-query-form-item">
        <a-tag color="blue">当前时点，与查询区间无关</a-tag>
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="filters.warehouseId" width="180px"/>
      </a-form-item>
      <a-form-item label="关键字" class="smart-query-form-item">
        <a-input v-model:value="filters.keyword" placeholder="商品名称 / SKU 编码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.INVENTORY_QUERY" @click="onSearch">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
        <a-button class="smart-margin-left10" @click="advanced = !advanced">
          {{ advanced ? '收起高级筛选' : '展开高级筛选' }}
        </a-button>
      </a-form-item>
    </a-row>
    <a-row v-if="advanced" class="smart-query-form-row">
      <a-form-item label="流水类型" class="smart-query-form-item">
        <SmartEnumSelect v-model:value="filters.movementType" enum-name="SCM_INVENTORY_MOVEMENT_TYPE_ENUM" width="150px"/>
      </a-form-item>
      <a-form-item label="来源单据类型" class="smart-query-form-item">
        <SmartEnumSelect
            v-model:value="filters.sourceDocumentType"
            enum-name="SCM_INVENTORY_SOURCE_TYPE_ENUM"
            width="180px"
        />
      </a-form-item>
      <a-form-item label="SKU" class="smart-query-form-item">
        <SkuSelect v-model:value="filters.skuId" width="240px"/>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="chartError" :message="chartError" type="error" show-icon>
    <template #action>
      <a-button @click="queryActiveTab">重试</a-button>
    </template>
  </a-alert>

  <a-tabs v-model:activeKey="activeTab" class="smart-margin-top10" @change="onTabChange">
    <!-- ==================== 库存流水 ==================== -->
    <a-tab-pane key="movement" tab="库存流水">
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" @click="exportMovement">导出</a-button>
            <a-typography-text type="secondary" class="smart-margin-left10">
              流水是只追加的账本：不可编辑、不可删除，冲销以新增反向流水实现。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="movementColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_INVENTORY_MOVEMENT"
                :refresh="loadMovement"
            />
          </div>
        </a-row>
        <a-alert v-if="movement.error" :message="movement.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadMovement">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.INVENTORY_MOVEMENT"
            size="small"
            :data-source="movement.rows"
            :columns="visibleMovementColumns"
            row-key="movementId"
            bordered
            :loading="movement.loading"
            :pagination="false"
            :locale="{emptyText: '暂无库存流水'}"
            :scroll="{x: 2700}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'occurredAt'">
              {{ datetime(record.occurredAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'movementType'">
              {{ enumDescText(record.movementType, SCM_INVENTORY_MOVEMENT_TYPE_ENUM) }}
            </template>
            <template v-else-if="column.dataIndex === 'direction'">
              <a-tag :color="directionOf(record.movementType) === 'IN' ? 'green' : 'orange'">
                {{ directionText(directionOf(record.movementType)) }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'sourceDocumentType'">
              {{ enumDescText(record.sourceDocumentType, SCM_INVENTORY_SOURCE_TYPE_ENUM) }}
            </template>
            <template v-else-if="column.dataIndex === 'quantity'">
              <span class="num">{{ quantityText(record.quantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'unitCost'">
              <span class="num">{{ costText(record.unitCost, canViewCost) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'costAmount'">
              <span class="num">{{ costText(record.costAmount, canViewCost) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'beforeQuantity'">
              <span class="num">{{ quantityText(record.beforeQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'afterQuantity'">
              <span class="num">{{ quantityText(record.afterQuantity) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="movement.pageNum"
              v-model:page-size="movement.pageSize"
              :total="movement.total"
              @change="loadMovement"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <!-- ==================== 损耗分析 ==================== -->
    <a-tab-pane key="loss" tab="损耗分析">
      <a-row :gutter="[12, 12]">
        <a-col v-for="card in lossCards" :key="card.label" :xs="24" :sm="12" :md="8" :lg="6" :xl="4">
          <ReportKpiCard :label="card.label" :value="card.value" :hint="card.hint" :warning="card.warning"/>
        </a-col>
      </a-row>
      <a-row :gutter="[12, 12]" class="smart-margin-top10">
        <a-col :xs="24" :lg="12">
          <ReportPieChart
              title="损耗类型金额占比"
              :slices="lossPieSlices"
              extra="只统计盘亏与手工报损；盘盈与报溢是增益，不进损耗成本"
          />
        </a-col>
        <a-col :xs="24" :lg="12">
          <ReportLineChart
              title="损耗金额按日趋势"
              :x-axis="lossTrendAxis"
              :series="lossTrendSeries"
              empty-text="暂无按日损耗趋势（需后端提供按日聚合，前端不自行累加分页明细）"
          />
        </a-col>
      </a-row>
      <a-card size="small" :bordered="false" class="smart-margin-top10">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" @click="exportLoss">导出</a-button>
            <a-typography-text type="secondary" class="smart-margin-left10">
              R0 只承认盘亏与手工报损两类可证明的损耗事实，不伪造「采购损耗 / 退货损耗」。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="lossColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_INVENTORY_LOSS"
                :refresh="loadLoss"
            />
          </div>
        </a-row>
        <a-alert v-if="loss.error" :message="loss.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadLoss">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.INVENTORY_LOSS"
            size="small"
            :data-source="loss.rows"
            :columns="visibleLossColumns"
            row-key="movementId"
            bordered
            :loading="loss.loading"
            :pagination="false"
            :locale="{emptyText: '暂无损耗明细'}"
            :scroll="{x: 1800}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'movementType'">
              {{ enumDescText(record.movementType, SCM_REPORT_LOSS_TYPE_ENUM) }}
            </template>
            <template v-else-if="column.dataIndex === 'quantity'">
              <span class="num">{{ quantityText(record.quantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'unitCost'">
              <span class="num">{{ costText(record.unitCost, canViewCost) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'costAmount'">
              <span class="num">{{ costText(record.costAmount, canViewCost) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'occurredAt'">
              {{ datetime(record.occurredAt) }}
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="loss.pageNum"
              v-model:page-size="loss.pageSize"
              :total="loss.total"
              @change="loadLoss"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <!-- ==================== 当前库存价值（成本权限可见） ==================== -->
    <a-tab-pane v-if="canViewCost" key="value" tab="当前库存价值">
      <a-alert
          message="当前时点快照：数值是此刻的余额 × 移动加权均价，不是所选区间的期末值。"
          type="info"
          show-icon
          class="smart-margin-bottom10"
      />
      <a-row v-if="hasValueSummary" :gutter="[12, 12]">
        <a-col v-for="card in valueCards" :key="card.label" :xs="24" :sm="12" :md="8">
          <ReportKpiCard :label="card.label" :value="card.value" :hint="card.hint" :sub="card.sub" current-point/>
        </a-col>
      </a-row>
      <a-card size="small" :bordered="false" :class="hasValueSummary ? 'smart-margin-top10' : ''">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" @click="exportValue">导出</a-button>
            <a-typography-text type="secondary" class="smart-margin-left10">
              账面金额 = 当前数量 × 移动加权均价，由后端算，前端不做二次乘除。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="valueColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_INVENTORY_VALUE"
                :refresh="loadValue"
            />
          </div>
        </a-row>
        <a-alert v-if="value.error" :message="value.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadValue">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.INVENTORY_VALUE"
            size="small"
            :data-source="value.rows"
            :columns="valueColumns"
            row-key="balanceId"
            bordered
            :loading="value.loading"
            :pagination="false"
            :locale="{emptyText: '暂无库存价值'}"
            :scroll="{x: 1400}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'quantity'">
              <span class="num">{{ quantityText(record.quantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'reservedQuantity'">
              <span class="num">{{ quantityText(record.reservedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'availableQuantity'">
              <span class="num">{{ quantityText(record.availableQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'avgCost'">
              <span class="num">{{ moneyText(record.avgCost) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'amount'">
              <span class="num">{{ moneyText(record.amount) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="value.pageNum"
              v-model:page-size="value.pageSize"
              :total="value.total"
              @change="loadValue"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <!-- ==================== 收发存（数量版） ==================== -->
    <a-tab-pane key="flow" tab="收发存（数量版）">
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-typography-text type="secondary">
              一行 = 仓库 + SKU + 记账单位；数量列按单位分组，不做跨单位合计（10kg + 5箱 ≠ 15）。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="flowColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_INVENTORY_FLOW_SUMMARY"
                :refresh="loadFlow"
            />
          </div>
        </a-row>
        <a-alert v-if="flow.error" :message="flow.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadFlow">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.INVENTORY_FLOW_SUMMARY"
            size="small"
            :data-source="flow.rows"
            :columns="flowColumns"
            :row-key="(row: InventoryFlowSummaryRow) => `${row.warehouseId}-${row.skuId}-${row.unit}`"
            bordered
            :loading="flow.loading"
            :pagination="false"
            :locale="{emptyText: '暂无收发存数据'}"
            :scroll="{x: 2500}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="QUANTITY_INDEXES.includes(String(column.dataIndex))">
              <span class="num">{{ quantityText(record[column.dataIndex]) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="flow.pageNum"
              v-model:page-size="flow.pageSize"
              :total="flow.total"
              @change="loadFlow"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>
  </a-tabs>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import {useRoute} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ReportDateRangePicker from './report-components/report-date-range-picker.vue';
import ReportKpiCard from './report-components/report-kpi-card.vue';
import ReportPieChart from './report-components/report-pie-chart.vue';
import ReportLineChart from './report-components/report-line-chart.vue';
import {reportInventoryApi} from '/@/api/business/scm/report-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_REPORT_LOSS_TYPE_ENUM, SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import {
    SCM_INVENTORY_MOVEMENT_INBOUND_TYPES,
    SCM_INVENTORY_MOVEMENT_TYPE_ENUM,
    SCM_INVENTORY_SOURCE_TYPE_ENUM,
} from '/@/constants/business/scm/inventory-const';
import type {
    InventoryFlowSummaryRow,
    InventoryLossRow,
    InventoryLossSummary,
    InventoryMovementRow,
    InventoryReportQuery,
    InventoryValueRow,
    ReportChartLine,
    ReportChartSlice,
} from './report-types';
import {
    buildReportQuery,
    chartValue,
    costText,
    countText,
    createTabView,
    defaultDateRange,
    directionText,
    enumDescText,
    enterTab,
    filterCostColumns,
    incompleteCostHint,
    movementDirection,
    rangeFromQuery,
    rangeOverLimitError,
} from './report-model';
import {moneyText, quantityText} from '../inventory/inventory-model';
import {datetime} from '../common/scm-display';
import {useReportPermission} from './use-report-permission';
import {createGuardedLoader, createTabLoader} from './use-report-query';
import type {DateRange} from './report-model';

const PERM = SCM_REPORT_PERMISSION;
const route = useRoute();
const {canViewCost} = useReportPermission();

type InventoryTab = 'movement' | 'loss' | 'value' | 'flow';

const activeTab = ref<InventoryTab>('movement');
const dateRange = ref<DateRange | undefined>();
const filters = reactive<Omit<InventoryReportQuery, 'pageNum' | 'pageSize' | 'startDate' | 'endDate'>>({});
const advanced = ref(false);
const chartError = ref('');

const lossSummary = ref<InventoryLossSummary>();
const valueSummary = ref<{bookValue?: string | null; stockedSkuCount?: number | null; zeroStockSkuCount?: number | null; snapshotAt?: string | null}>({});

const movement = reactive(createTabView<InventoryMovementRow>());
const loss = reactive(createTabView<InventoryLossRow>());
const value = reactive(createTabView<InventoryValueRow>());
const flow = reactive(createTabView<InventoryFlowSummaryRow>());

/** 无成本权限时不展示价值 Tab；此时如果正停在价值 Tab，回落到流水（见 `onTabChange`）。 */
const COST_INDEXES = ['unitCost', 'costAmount'];

const movementColumns = ref<TableColumnsType<InventoryMovementRow>>([
    {title: '发生时间', dataIndex: 'occurredAt', width: 190},
    {title: '仓库', dataIndex: 'warehouseName', width: 150},
    {title: '商品', dataIndex: 'productName', width: 180},
    {title: 'SKU', dataIndex: 'skuCode', width: 170},
    {title: '流水类型', dataIndex: 'movementType', width: 120},
    {title: '方向', dataIndex: 'direction', align: 'center', width: 80},
    {title: '来源单据类型', dataIndex: 'sourceDocumentType', width: 150},
    {title: '来源单号', dataIndex: 'sourceDocumentNo', width: 190},
    {title: '来源单据 ID', dataIndex: 'sourceDocumentId', width: 150},
    {title: '来源行 ID', dataIndex: 'sourceDocumentItemId', width: 150},
    {title: '数量', dataIndex: 'quantity', align: 'right', width: 120},
    {title: '单位', dataIndex: 'unitSnapshot', align: 'center', width: 90},
    {title: '单位成本', dataIndex: 'unitCost', align: 'right', width: 130},
    {title: '成本金额', dataIndex: 'costAmount', align: 'right', width: 140},
    {title: '变动前数量', dataIndex: 'beforeQuantity', align: 'right', width: 140},
    {title: '变动后数量', dataIndex: 'afterQuantity', align: 'right', width: 140},
    {title: '操作人', dataIndex: 'operator', width: 120},
]);

const lossColumns = ref<TableColumnsType<InventoryLossRow>>([
    {title: '商品', dataIndex: 'productName', width: 180},
    {title: 'SKU', dataIndex: 'skuCode', width: 170},
    {title: '仓库', dataIndex: 'warehouseName', width: 150},
    {title: '损耗类型', dataIndex: 'movementType', width: 110},
    {title: '数量', dataIndex: 'quantity', align: 'right', width: 120},
    {title: '单位', dataIndex: 'unitSnapshot', align: 'center', width: 90},
    {title: '单位成本', dataIndex: 'unitCost', align: 'right', width: 130},
    {title: '损耗成本金额', dataIndex: 'costAmount', align: 'right', width: 150},
    {title: '来源单号', dataIndex: 'sourceDocumentNo', width: 190},
    {title: '发生时间', dataIndex: 'occurredAt', width: 190},
    {title: '操作人', dataIndex: 'operator', width: 120},
]);

const valueColumns = ref<TableColumnsType<InventoryValueRow>>([
    {title: '仓库', dataIndex: 'warehouseName', width: 150},
    {title: '商品', dataIndex: 'productName', width: 180},
    {title: 'SKU', dataIndex: 'skuCode', width: 170},
    {title: '单位', dataIndex: 'unit', align: 'center', width: 90},
    {title: '当前数量', dataIndex: 'quantity', align: 'right', width: 130},
    {title: '预留数量', dataIndex: 'reservedQuantity', align: 'right', width: 130},
    {title: '可用数量', dataIndex: 'availableQuantity', align: 'right', width: 130},
    {title: '当前移动平均成本', dataIndex: 'avgCost', align: 'right', width: 170},
    {title: '当前账面金额', dataIndex: 'amount', align: 'right', width: 160},
]);

/** 收发存的全部数量列（同一份名单既驱动渲染，也说明「这些列不可相加」）。 */
const QUANTITY_INDEXES = [
    'purchaseInQuantity',
    'salesOutQuantity',
    'stocktakeGainQuantity',
    'stocktakeLossQuantity',
    'gainReportQuantity',
    'lossReportQuantity',
    'transferInQuantity',
    'transferOutQuantity',
    'convertInQuantity',
    'convertOutQuantity',
    'netChangeQuantity',
];

const flowColumns = ref<TableColumnsType<InventoryFlowSummaryRow>>([
    {title: '仓库', dataIndex: 'warehouseName', width: 150},
    {title: '商品', dataIndex: 'productName', width: 180},
    {title: 'SKU', dataIndex: 'skuCode', width: 170},
    {title: '单位', dataIndex: 'unit', align: 'center', width: 90},
    {title: '期内采购入库数量', dataIndex: 'purchaseInQuantity', align: 'right', width: 160},
    {title: '期内销售出库数量', dataIndex: 'salesOutQuantity', align: 'right', width: 160},
    {title: '期内盘盈数量', dataIndex: 'stocktakeGainQuantity', align: 'right', width: 140},
    {title: '期内盘亏数量', dataIndex: 'stocktakeLossQuantity', align: 'right', width: 140},
    {title: '期内报溢数量', dataIndex: 'gainReportQuantity', align: 'right', width: 140},
    {title: '期内报损数量', dataIndex: 'lossReportQuantity', align: 'right', width: 140},
    {title: '期内调拨入', dataIndex: 'transferInQuantity', align: 'right', width: 130},
    {title: '期内调拨出', dataIndex: 'transferOutQuantity', align: 'right', width: 130},
    {title: '期内转换入', dataIndex: 'convertInQuantity', align: 'right', width: 130},
    {title: '期内转换出', dataIndex: 'convertOutQuantity', align: 'right', width: 130},
    {title: '期内净变动量', dataIndex: 'netChangeQuantity', align: 'right', width: 150},
]);

const visibleMovementColumns = computed(() => filterCostColumns(movementColumns.value, COST_INDEXES, canViewCost.value));
const visibleLossColumns = computed(() => filterCostColumns(lossColumns.value, COST_INDEXES, canViewCost.value));

function directionOf(movementType: string | null | undefined): 'IN' | 'OUT' | null {
    return movementDirection(movementType, SCM_INVENTORY_MOVEMENT_INBOUND_TYPES);
}

const lossCards = computed(() => {
    const data = lossSummary.value ?? {};
    const cards: Array<{label: string; value: string; hint?: string; warning?: string; cost?: boolean}> = [
        {
            label: '盘亏数量（按单位）',
            value: data.stocktakeLossQuantityText || '—',
            hint: 'STOCKTAKE_LOSS 流水的数量，按记账单位分组，不做跨单位相加',
        },
        {
            label: '盘亏成本金额',
            value: costText(data.stocktakeLossCostAmount, canViewCost.value),
            hint: 'SUM(quantity × unit_cost)',
            cost: true,
        },
        {
            label: '报损数量（按单位）',
            value: data.lossReportQuantityText || '—',
            hint: 'LOSS_REPORT 流水的数量，按记账单位分组',
        },
        {
            label: '报损成本金额',
            value: costText(data.lossReportCostAmount, canViewCost.value),
            hint: 'SUM(quantity × unit_cost)',
            cost: true,
        },
        {
            label: '损耗总成本金额',
            value: costText(data.totalLossCostAmount, canViewCost.value),
            hint: '盘亏 + 报损的合计，由后端聚合；不含盘盈与报溢',
            warning: incompleteCostHint(data.missingCostCount, '损耗成本金额'),
            cost: true,
        },
    ];
    return cards.filter((card) => !card.cost || canViewCost.value);
});

/** 占比图直接吃两个 KPI 金额：角度由图表库按值分配，前端不做任何金额运算。 */
const lossPieSlices = computed<ReportChartSlice[]>(() => {
    const data = lossSummary.value ?? {};
    if (!canViewCost.value) {
        return [];
    }
    return [
        {
            name: '盘亏',
            value: chartValue(data.stocktakeLossCostAmount),
            text: moneyText(data.stocktakeLossCostAmount),
        },
        {
            name: '报损',
            value: chartValue(data.lossReportCostAmount),
            text: moneyText(data.lossReportCostAmount),
        },
    ];
});

const lossTrendPoints = computed(() => lossSummary.value?.dailyTrend ?? []);
const lossTrendAxis = computed(() => lossTrendPoints.value.map((point) => point.bizDate ?? ''));
const lossTrendSeries = computed<ReportChartLine[]>(() => {
    const points = lossTrendPoints.value;
    if (!canViewCost.value) {
        return [];
    }
    return [
        {
            name: '损耗金额',
            data: points.map((point) => chartValue(point.totalLossCostAmount)),
            texts: points.map((point) => moneyText(point.totalLossCostAmount)),
        },
    ];
});

/** 价值 KPI：后端没给聚合就不显示，绝不把当页金额相加冒充总额。 */
const hasValueSummary = computed(() => Object.values(valueSummary.value).some((item) => item !== undefined && item !== null));
const valueCards = computed(() => {
    const data = valueSummary.value;
    return [
        {
            label: '当前库存账面金额',
            value: costText(data.bookValue, canViewCost.value),
            hint: 'SUM(inventory_balance.quantity × avg_cost)，当前时点',
            sub: data.snapshotAt ? `截至 ${datetime(data.snapshotAt)}` : undefined,
        },
        {label: '有库存 SKU 数', value: countText(data.stockedSkuCount), hint: '余额行数量大于 0 的 (仓库, SKU) 行数'},
        {label: '零库存 SKU 数', value: countText(data.zeroStockSkuCount), hint: '余额行存在但数量为 0'},
    ];
});

function inventoryQuery(tab: {pageNum: number; pageSize: number}): InventoryReportQuery {
    return buildReportQuery<InventoryReportQuery>(dateRange.value, {...filters}, tab);
}

function exportQuery(): Partial<InventoryReportQuery> {
    return buildReportQuery<Partial<InventoryReportQuery>>(dateRange.value, {...filters});
}

const loadMovement = createTabLoader(movement, () => inventoryQuery(movement), reportInventoryApi.movementQuery);
const loadLoss = createTabLoader(loss, () => inventoryQuery(loss), reportInventoryApi.lossQuery);
const loadFlow = createTabLoader(flow, () => inventoryQuery(flow), reportInventoryApi.flowSummaryQuery);

const loadLossSummary = createGuardedLoader(
    () => reportInventoryApi.lossSummary(buildReportQuery<InventoryReportQuery>(dateRange.value, {...filters})),
    (data) => (lossSummary.value = data),
    chartError
);

/** 价值页：日期必填但被后端忽略（当前时点快照），仍带当前区间过去以满足校验。 */
const loadValue = createTabLoader(value, () => inventoryQuery(value), (query) =>
    reportInventoryApi.valueQuery(query).then((response) => {
        valueSummary.value = {
            bookValue: response.data.bookValue,
            stockedSkuCount: response.data.stockedSkuCount,
            zeroStockSkuCount: response.data.zeroStockSkuCount,
            snapshotAt: response.data.snapshotAt,
        };
        return response;
    })
);

function queryActiveTab() {
    const overLimit = rangeOverLimitError(dateRange.value);
    if (overLimit) {
        chartError.value = overLimit;
        return;
    }
    chartError.value = '';
    switch (activeTab.value) {
        case 'movement':
            void loadMovement();
            break;
        case 'loss':
            void loadLoss();
            void loadLossSummary();
            break;
        case 'value':
            void loadValue();
            break;
        case 'flow':
            void loadFlow();
            break;
        default:
            break;
    }
}

function onTabChange() {
    // 无成本权限时价值 Tab 整个不渲染；若权限在停留期间被收回，回落到流水而不是查一个空页
    if (activeTab.value === 'value' && !canViewCost.value) {
        activeTab.value = 'movement';
        enterTab(movement);
        queryActiveTab();
        return;
    }
    switch (activeTab.value) {
        case 'movement':
            enterTab(movement);
            break;
        case 'loss':
            enterTab(loss);
            break;
        case 'value':
            enterTab(value);
            break;
        case 'flow':
            enterTab(flow);
            break;
        default:
            break;
    }
    queryActiveTab();
}

function onSearch() {
    queryActiveTab();
}

function resetQuery() {
    filters.warehouseId = undefined;
    filters.skuId = undefined;
    filters.keyword = undefined;
    filters.movementType = undefined;
    filters.sourceDocumentType = undefined;
    dateRange.value = defaultDateRange();
    enterTab(movement);
    enterTab(loss);
    enterTab(value);
    enterTab(flow);
    onSearch();
}

function exportMovement() {
    void reportInventoryApi.movementExport(exportQuery());
}

function exportLoss() {
    void reportInventoryApi.lossExport(exportQuery());
}

function exportValue() {
    void reportInventoryApi.valueExport(exportQuery());
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
</style>
