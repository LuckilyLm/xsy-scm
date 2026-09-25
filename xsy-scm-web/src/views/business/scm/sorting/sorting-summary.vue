<!--
  商品分拣汇总（P1 分拣管理，**只读**）。

  ## 这是什么

  跨订单按「商品 + 规格 + 单位」聚合的分拣进度视图：哪只 SKU 铺在多少张订单上、
  生成了多少个任务、还有多少行没录。它是一个**队列健康度**视角，不是一张库存或收入报表。

  ## 为什么这一页不允许存在任何动作入口（硬产品规则）

  聚合行不是单据：一行 `上海青 / 10 扎` 可能横跨 7 张订单、4 个任务、2 个仓库岗位。
  在这上面放一个「录入 / 完成 / 批量处理」按钮，等于让用户对一个**没有主键的抽象**下写命令 ——
  要么后端得把这次写入拆回若干明细行（谁先谁后、失败怎么算全是未定义行为），
  要么按钮只能偷偷跳回任务页，而那是把导航伪装成命令。
  所以本页只有查询、重置与表格；要办分拣，去「分拣任务」页按任务行操作。

  ## 量的口径

  - 分组键**含销售单位**，因此同一行内的量天然同单位；页面绝不跨行求和，
    也不做「合计」行 —— `kg` 与 `扎` 相加出来的数字没有业务含义。
  - `plannedQuantity` / `sortedQuantity` 是后端 4 位定点字符串或 `null`；
    `null` 渲染成 `—`（没有这个事实），**不兜底成 `0`**（那是「录过且为 0」）。
  - `unprocessedCount` 是**明细行数**，不是数量：待办量在这里是可数的行，不是可加的公斤。
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

  <a-alert
      type="info"
      show-icon
      class="read-only-hint"
      message="只读汇总视角"
      description="每行是一个「商品 + 规格 + 单位」的聚合，不对应任何单据，因此本页不提供录入、完成或批量处理入口；请到「分拣任务」按任务明细行办理。"
  />

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
        :scroll="{ x: 1380 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'plannedQuantity'">
          <span class="num">{{ quantityText(record.plannedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'sortedQuantity'">
          <span class="num">{{ quantityText(record.sortedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unprocessedCount'">
          <a-tag :color="record.unprocessedCount ? 'orange' : 'green'">{{ record.unprocessedCount }}</a-tag>
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

/** 列即口径：数量列只有本行同单位内的合计，绝不出现跨行的「合计」列。 */
const columns = ref<TableColumnsType<SortingSkuSummary>>([
  {title: '商品', dataIndex: 'productNameSnapshot', width: 190},
  {title: '规格', dataIndex: 'specNameSnapshot', width: 150},
  {title: '商品编码', dataIndex: 'spuCodeSnapshot', width: 140},
  {title: '规格编码', dataIndex: 'skuCodeSnapshot', width: 140},
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
.num {
  font-variant-numeric: tabular-nums;
}

.read-only-hint {
  margin-bottom: 12px;
}
</style>
