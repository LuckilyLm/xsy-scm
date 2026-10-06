<!--
  商品分拣汇总（只读）：跨订单按「商品 + 规格 + 单位」聚合的分拣进度视图。

  <b>本页不允许任何动作入口</b>：聚合行不是单据（一行可能横跨多张订单与多个岗位），
  在它上面下写命令等于对一个没有主键的抽象做写入。要办分拣去「分拣任务」页。

  量的口径：分组键<b>含销售单位</b>，因此页面绝不跨行求和、不做合计行；
  `plannedQuantity` / `sortedQuantity` 是 4 位定点字符串或 `null`，
  `null` 渲染 `—` 而<b>不兜底成 `0`</b>；`unprocessedCount` 是明细<b>行数</b>，不是数量。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent="onSearch">
    <a-form-item label="关键词" class="smart-query-form-item">
      <a-input
          v-model:value="queryForm.keyword"
          placeholder="商品名称 / 编码 / 规格"
          allow-clear
          :maxlength="100"
          style="width: 260px"
          @pressEnter="onSearch"
      />
    </a-form-item>
    <a-form-item label="仓库" class="smart-query-form-item">
      <WarehouseSelect v-model:value="queryForm.warehouseId" placeholder="全部仓库" width="200px"/>
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
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <div class="smart-table-setting-block">
      <TableOperator
          v-model="columns"
          :table-id="TABLE_ID_CONST.BUSINESS.SCM_SORTING_SUMMARY"
          :refresh="queryData"
      />
    </div>
    <a-table
        :id="SCM_SORTING_TABLE_ID.SUMMARY"
        size="small"
        :data-source="rows"
        :columns="columns"
        :row-key="(r: SortingSkuSummary) => `${r.skuId}-${r.saleUnitSnapshot}`"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText }"
        :scroll="{ x: 1120 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'product'">
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main">{{ record.productNameSnapshot || '—' }}</span>
            <span v-if="record.spuCodeSnapshot" class="scm-cell-stack__sub">{{ record.spuCodeSnapshot }}</span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'sku'">
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main">{{ record.specNameSnapshot || '—' }}</span>
            <span v-if="record.skuCodeSnapshot" class="scm-cell-stack__sub">{{ record.skuCodeSnapshot }}</span>
          </div>
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

    <div class="smart-query-table-page">
      <a-pagination
          show-size-changer
          show-quick-jumper
          :page-size-options="['10', '20', '50', '100']"
          v-model:current="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          @change="queryData"
          :show-total="(n: number) => `共 ${n} 个商品规格`"
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
} from '/@/constants/business/scm/sorting-const';
import {hasPermission} from '../common/scm-permission';
import type {SortingSkuSummary, SortingSummaryQuery} from './sorting-types';
import {quantityText, sortingError} from './sorting-types';

const queryForm = reactive<SortingSummaryQuery>({pageNum: 1, pageSize: 20});
const rows = ref<SortingSkuSummary[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
let generation = 0;

const statusOptions = Object.values(SCM_SORTING_TASK_STATUS_ENUM).map((item) => ({value: item.value, label: item.desc}));

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
  {title: '商品', dataIndex: 'product', width: 190},
  {title: '商品规格', dataIndex: 'sku', width: 190},
  {title: '单位', dataIndex: 'saleUnitSnapshot', align: 'center', width: 90},
  {title: '涉及订单数', dataIndex: 'orderCount', align: 'right', width: 110},
  {title: '任务数', dataIndex: 'taskCount', align: 'right', width: 90},
  {title: '明细行数', dataIndex: 'lineCount', align: 'right', width: 100},
  {title: '未处理行数', dataIndex: 'unprocessedCount', align: 'center', width: 110},
  {title: '计划量合计', dataIndex: 'plannedQuantity', align: 'right', width: 120},
  {title: '已分量合计', dataIndex: 'sortedQuantity', align: 'right', width: 120},
]);

async function queryData() {
    const current = ++generation;
    loading.value = true;
    error.value = '';
    try {
        const result = await sortingApi.summary({...queryForm});
        if (current === generation) {
            rows.value = result.data.list;
            total.value = result.data.total;
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
    onSearch();
}

onMounted(queryData);
</script>

<style scoped>
.read-only-hint {
  margin-bottom: 12px;
}
</style>
