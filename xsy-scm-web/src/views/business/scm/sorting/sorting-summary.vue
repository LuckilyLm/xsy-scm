<!-- 分拣汇总按商品规格和销售单位展示，只供查询。不同销售单位不能直接合计；要修改分拣数量，请进入分拣任务。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent="onSearch">
    <a-form-item label="关键词" class="smart-query-form-item">
      <a-input
          v-model:value="queryForm.keyword"
          :placeholder="viewMode === 'sku' ? '商品名称 / 编码 / 规格' : '客户 / 订单号 / 商品 / 编码'"
          allow-clear
          :maxlength="100"
          style="width: 260px"
          @pressEnter="onSearch"
      />
    </a-form-item>
    <a-form-item label="仓库" class="smart-query-form-item">
      <WarehouseSelect v-model:value="queryForm.warehouseId" placeholder="全部仓库" width="200px"/>
    </a-form-item>
    <a-form-item label="送货日期" class="smart-query-form-item">
      <a-date-picker v-model:value="queryForm.deliveryDate" value-format="YYYY-MM-DD" placeholder="按冻结送货日筛选" style="width: 190px"/>
    </a-form-item>
    <a-form-item label="任务状态" class="smart-query-form-item">
      <a-select v-model:value="queryForm.taskStatus" :options="statusOptions" placeholder="全部" allow-clear style="width: 140px"/>
    </a-form-item>
    <a-form-item class="smart-query-form-item">
      <a-button-group>
        <a-button type="primary" v-privilege="'scm:sorting:summary:query'" @click="onSearch">查询</a-button>
        <a-button @click="resetQuery">重置</a-button>
      </a-button-group>
    </a-form-item>
    <div class="scm-query-table-tools">
      <TableOperator v-if="viewMode === 'sku'" v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_SORTING_SUMMARY" :refresh="queryData"/>
    </div>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-radio-group v-model:value="viewMode" button-style="solid" style="margin-bottom: 12px" @change="switchView">
      <a-radio-button value="sku">按商品汇总</a-radio-button>
      <a-radio-button v-if="hasPermission(SCM_SORTING_PERMISSION.TASK_QUERY)" value="customer">按客户明细</a-radio-button>
      <a-radio-button value="performance">最终确认统计</a-radio-button>
    </a-radio-group>
    <p class="scm-note">按商品仅同规格同单位汇总；客户明细逐条展示。最终确认统计仅计已完成任务的最后确认人，不等同个人劳动绩效或工资依据。所有视角只读。</p>

    <a-table v-if="viewMode === 'sku'"
        :id="SCM_SORTING_TABLE_ID.SUMMARY"
        size="small"
        :data-source="rows"
        :columns="columns"
        :row-key="(r: SortingSkuSummary) => `${r.skuId}-${r.saleUnitSnapshot}`"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText }"
        :scroll="{ x: 1350 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'product'">{{ record.productNameSnapshot || '—' }}</template>
        <template v-else-if="column.dataIndex === 'spuCodeSnapshot'">
          <span class="scm-mono">{{ record.spuCodeSnapshot || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'sku'">{{ record.specNameSnapshot || '—' }}</template>
        <template v-else-if="column.dataIndex === 'skuCodeSnapshot'">
          <span class="scm-mono">{{ record.skuCodeSnapshot || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'plannedQuantity'">
          <span class="scm-quantity">{{ quantityText(record.plannedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'sortedQuantity'">
          <span class="scm-quantity">{{ quantityText(record.sortedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unprocessedCount'">
          <!-- 未处理行数是本页唯一需要「一眼挑出来」的异常信号：0 不强调，非 0 才着色 -->
          <ScmStatusTag
              :tone="record.unprocessedCount ? 'warning' : 'neutral'"
              :label="String(record.unprocessedCount ?? '—')"
          />
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>

    <a-table
        v-else-if="viewMode === 'customer'"
        size="small"
        :data-source="customerRows"
        :columns="customerColumns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText }"
        :scroll="{ x: 1760 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'taskStatus'">
          <ScmStatusTag :tone="record.taskStatus === 'COMPLETED' ? 'success' : 'processing'" :label="statusLabel(record.taskStatus)"/>
        </template>
        <template v-else-if="column.dataIndex === 'result'">
          <ScmStatusTag
              :tone="record.result === 'NORMAL' ? 'success' : record.result ? 'warning' : 'neutral'"
              :label="resultLabel(record.result)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'deliveryTimeSnapshot'">
          {{ record.deliveryTimeSnapshot ? datetime(record.deliveryTimeSnapshot) : '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'plannedQuantitySnapshot' || column.dataIndex === 'sortedQuantity'">
          <span class="scm-quantity">{{ quantityText(record[column.dataIndex]) }}</span>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>

    <a-table
        v-else
        size="small"
        :data-source="performanceRows"
        :columns="performanceColumns"
        :row-key="(r: SortingPerformance) => `${r.warehouseId}-${r.confirmedBy}-${r.saleUnitSnapshot}`"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText }"
        :scroll="{ x: 1000 }"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'confirmedQuantity'">
          <span class="scm-quantity">{{ quantityText(record.confirmedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'lastConfirmedAt'">
          {{ record.lastConfirmedAt ? datetime(record.lastConfirmedAt) : '—' }}
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
          show-size-changer
          show-quick-jumper
          :page-size-options="['10', '20', '50', '100']"
          v-model:current="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          @change="queryData"
          :show-total="(n: number) => viewMode === 'sku' ? `共 ${n} 个商品规格` : viewMode === 'customer' ? `共 ${n} 条客户分拣明细` : `共 ${n} 条最终确认统计`"
      />
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {sortingApi} from '/@/api/business/scm/sorting-api';
import {
    SCM_SORTING_PERMISSION,
    SCM_SORTING_TABLE_ID,
    SCM_SORTING_TASK_STATUS_ENUM,
    SCM_SORTING_RESULT_ENUM,
} from '/@/constants/business/scm/sorting-const';
import {hasPermission} from '../common/scm-permission';
import type {SortingCustomerLine, SortingPerformance, SortingSkuSummary, SortingSummaryQuery} from './sorting-types';
import {datetime} from '../common/scm-display';
import {quantityText, sortingError} from './sorting-types';

const queryForm = reactive<SortingSummaryQuery>({pageNum: 1, pageSize: 20});
const rows = ref<SortingSkuSummary[]>([]);
const customerRows = ref<SortingCustomerLine[]>([]);
const performanceRows = ref<SortingPerformance[]>([]);
const viewMode = ref<'sku' | 'customer' | 'performance'>('sku');
const total = ref(0);
const loading = ref(false);
const error = ref('');
let generation = 0;

const statusOptions = Object.values(SCM_SORTING_TASK_STATUS_ENUM).map((item) => ({value: item.value, label: item.desc}));
const statusLabel = (value: string) => SCM_SORTING_TASK_STATUS_ENUM[value]?.desc ?? value;
const resultLabel = (value?: string | null) => value ? SCM_SORTING_RESULT_ENUM[value]?.desc ?? value : '待处理';

// 汇总行按「授权仓 ∩ 可见指派人」收窄，与任务页同一套口径；空表要能区分是没授权还是真没数据。
const emptyText = computed(() =>
    hasPermission(SCM_SORTING_PERMISSION.TASK_ASSIGN)
        ? '暂无分拣汇总数据'
        : '当前仅统计授权仓库内派给您本人的任务；若无数据，可能是尚未生成分拣任务，请联系分拣主管确认。'
);

/**
 * 列即口径：数量列只有本行同单位内的合计，绝不出现跨行的「合计」列。
 *
 * 两个编码（商品编码 / 商品规格编码）不再各占一列，改为名称下方的 secondary text ——
 * 本页每行是「商品 + 规格 + 单位」的聚合，两列编码会把 11 列挤成 11 列纯技术字段。
 */
const columns = ref<TableColumnsType<SortingSkuSummary>>([
  {title: '商品', dataIndex: 'product', width: 170},
  {title: '商品编码', dataIndex: 'spuCodeSnapshot', width: 140},
  {title: '商品规格', dataIndex: 'sku', width: 150},
  {title: '商品规格编码', dataIndex: 'skuCodeSnapshot', width: 150},
  {title: '单位', dataIndex: 'saleUnitSnapshot', align: 'center', width: 90},
  {title: '涉及订单数', dataIndex: 'orderCount', align: 'right', width: 110},
  {title: '任务数', dataIndex: 'taskCount', align: 'right', width: 90},
  {title: '明细行数', dataIndex: 'lineCount', align: 'right', width: 100},
  {title: '未处理行数', dataIndex: 'unprocessedCount', align: 'right', width: 110},
  {title: '计划量合计', dataIndex: 'plannedQuantity', align: 'right', width: 120},
  {title: '已分量合计', dataIndex: 'sortedQuantity', align: 'right', width: 120},
]);

const customerColumns: TableColumnsType<SortingCustomerLine> = [
  {title: '送货时间', dataIndex: 'deliveryTimeSnapshot', width: 165},
  {title: '仓库', dataIndex: 'warehouseNameSnapshot', width: 140},
  {title: '客户', dataIndex: 'customerNameSnapshot', width: 150},
  {title: '订单号', dataIndex: 'orderNoSnapshot', width: 180},
  {title: '分拣任务', dataIndex: 'taskNo', width: 180},
  {title: '任务状态', dataIndex: 'taskStatus', align: 'center', width: 100},
  {title: '商品', dataIndex: 'productNameSnapshot', width: 160},
  {title: '商品规格', dataIndex: 'specNameSnapshot', width: 140},
  {title: '单位', dataIndex: 'saleUnitSnapshot', align: 'center', width: 75},
  {title: '计划量', dataIndex: 'plannedQuantitySnapshot', align: 'right', width: 110},
  {title: '已分量', dataIndex: 'sortedQuantity', align: 'right', width: 110},
  {title: '分拣结果', dataIndex: 'result', align: 'center', width: 100},
];

const performanceColumns: TableColumnsType<SortingPerformance> = [
  {title: '仓库', dataIndex: 'warehouseNameSnapshot', width: 165},
  {title: '最后确认人', dataIndex: 'confirmedBy', width: 170},
  {title: '销售单位', dataIndex: 'saleUnitSnapshot', align: 'center', width: 100},
  {title: '已完成任务数', dataIndex: 'taskCount', align: 'right', width: 140},
  {title: '最终确认行数', dataIndex: 'lineCount', align: 'right', width: 140},
  {title: '最终确认量', dataIndex: 'confirmedQuantity', align: 'right', width: 150},
  {title: '最近确认时间', dataIndex: 'lastConfirmedAt', width: 180},
];

function switchView() {
    queryForm.pageNum = 1;
    queryData();
}

async function queryData() {
    const current = ++generation;
    loading.value = true;
    error.value = '';
    try {
        // 加载失败由页头 Alert 承担，不再让全局 toast 重复说一遍
        if (viewMode.value === 'sku') {
            const result = await sortingApi.summary({...queryForm}, {suppressGlobalErrorMessage: true});
            if (current === generation) {
                rows.value = result.data.list;
                total.value = result.data.total;
            }
        } else if (viewMode.value === 'customer') {
            const result = await sortingApi.customerLines({...queryForm}, {suppressGlobalErrorMessage: true});
            if (current === generation) {
                customerRows.value = result.data.list;
                total.value = result.data.total;
            }
        } else {
            const result = await sortingApi.confirmedPerformance({...queryForm}, {suppressGlobalErrorMessage: true});
            if (current === generation) {
                performanceRows.value = result.data.list;
                total.value = result.data.total;
            }
        }
    } catch (e) {
        if (current === generation) error.value = sortingError(e);
    } finally {
        if (current === generation) loading.value = false;
    }
}

function onSearch() {
    queryForm.pageNum = 1;
    queryData();
}

function resetQuery() {
    queryForm.keyword = undefined;
    queryForm.warehouseId = undefined;
    queryForm.taskStatus = undefined;
    queryForm.deliveryDate = undefined;
    onSearch();
}

onMounted(queryData);
</script>
