<!-- 价格变更历史：协议价、分组价和时价等来源，可查看前后差异。 -->
<template>
  <a-form layout="inline" class="xsy-query-form">
    <a-row class="xsy-query-form-row">
      <a-form-item label="来源" class="xsy-query-form-item">
        <a-select v-model:value="query.source" allow-clear style="width: 150px" :options="sourceOptions" />
      </a-form-item>
      <a-form-item label="客户" class="xsy-query-form-item">
        <CustomerSelect v-model:value="query.customerId" width="180px" />
      </a-form-item>
      <a-form-item label="商品规格" class="xsy-query-form-item">
        <SkuSelect v-model:value="query.skuId" width="230px" :disabled-statuses="[]" />
      </a-form-item>
      <a-form-item label="变更类型" class="xsy-query-form-item">
        <a-select v-model:value="query.operationType" allow-clear style="width: 110px" :options="operationOptions" />
      </a-form-item>
      <a-form-item label="有效区间" class="xsy-query-form-item">
        <a-range-picker v-model:value="effective" show-time value-format="YYYY-MM-DD HH:mm:ss" :allow-empty="[true, true]" />
      </a-form-item>
      <a-form-item label="变更区间" class="xsy-query-form-item">
        <a-range-picker v-model:value="operated" show-time value-format="YYYY-MM-DD HH:mm:ss" :allow-empty="[true, true]" />
      </a-form-item>
      <a-form-item class="xsy-query-form-item">
        <a-button type="primary" @click="search" v-privilege="'scm:pricing:history:query'">查询</a-button>
      </a-form-item>
    </a-row>
  </a-form>
  <RequestError v-if="error" :message="error" />
  <a-card size="small" :bordered="false">
    <div class="xsy-table-btn-block">
      <div class="xsy-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PRICING_HISTORY" :refresh="load" />
      </div>
    </div>

    <a-table
      :data-source="rows"
      :columns="columns"
      :row-key="(r: HistoryRow) => `${r.source}-${r.historyId}`"
      :loading="loading"
      :pagination="false"
      size="small"
      bordered
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'source'">
          {{ historyLabel(HISTORY_SOURCE_LABEL, record.source) }}
        </template>
        <template v-else-if="column.dataIndex === 'customerName'">{{ record.customerName || record.groupName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'specName'">{{ record.specName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'operationType'">
          <ScmStatusTag
            :tone="HISTORY_OPERATION_TONE[record.operationType] ?? 'neutral'"
            :label="historyLabel(HISTORY_OPERATION_LABEL, record.operationType)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'currentUnitPrice'">
          <span class="scm-money">{{ formatAmount(record.currentUnitPrice) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'currentEffectiveFrom'">{{ datetime(record.currentEffectiveFrom) }}</template>
        <template v-else-if="column.dataIndex === 'currentEffectiveTo'">{{
          record.currentEffectiveTo ? datetime(record.currentEffectiveTo) : '长期有效'
        }}</template>
        <template v-else-if="column.dataIndex === 'operatedAt'">{{ datetime(record.operatedAt) }}</template>
        <template v-else-if="column.dataIndex === 'operator'">{{ record.operator || '—' }}</template>
        <template v-else-if="column.dataIndex === 'currentDeleted'">
          <ScmStatusTag :tone="record.currentDeleted ? 'error' : 'neutral'" :label="record.currentDeleted ? '已删除' : '有效记录'" />
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="xsy-table-operate scm-table-actions">
            <a-button type="link" size="small" @click="selected = record">变更详情</a-button>
          </a-space>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <div class="xsy-query-table-page">
      <a-pagination
        show-quick-jumper
        show-less-items
        :show-total="(total: number) => `共${total}条`"
        v-model:current="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        show-size-changer
        :page-size-options="SCM_PAGE_SIZE_OPTIONS"
        @change="load"
      />
    </div>
  </a-card>
  <a-modal title="价格变更详情" :open="!!selected" :footer="null" :width="760" @cancel="selected = undefined">
    <ScmDiffTable :before="selected?.beforeData" :after="selected?.afterData" :scope="selected?.operationType" />
  </a-modal>
</template>
<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {onMounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import {formatAmount} from '/@/utils/scm-amount';
import type {PriceQuery, HistoryRow} from '/@/types/business/scm/pricing';
import {pricingError} from './pricing-errors';
import {HISTORY_OPERATION_LABEL, HISTORY_OPERATION_TONE, HISTORY_SOURCE_LABEL, historyLabel} from './pricing-display';
import ScmDiffTable from '/@/views/business/scm/common/scm-diff-table.vue';
import {datetime} from '../common/scm-display';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';

const query = reactive<PriceQuery & {source?: string; operationType?: string}>({pageNum: 1, pageSize: 20}),
  effective = ref<[string, string]>(),
  operated = ref<[string, string]>(),
  rows = ref<HistoryRow[]>([]),
  total = ref(0),
  loading = ref(false),
  error = ref(''),
  selected = ref<HistoryRow>();
let requestId = 0;

// 下拉与列表列共用同一份中文（pricing-display），避免两处各写一份
const sourceOptions = Object.entries(HISTORY_SOURCE_LABEL).map(([value, label]) => ({value, label}));
const operationOptions = Object.entries(HISTORY_OPERATION_LABEL).map(([value, label]) => ({value, label}));

const columns = ref<TableColumnsType<HistoryRow>>([
  {title: '来源', dataIndex: 'source', width: 120},
  {title: '客户', dataIndex: 'customerName', width: 170},
  {title: '商品', dataIndex: 'productName', width: 150},
  {title: '商品规格', dataIndex: 'specName', width: 170},
  {title: '变更', dataIndex: 'operationType', align: 'center', width: 100},
  {title: '当前价格', dataIndex: 'currentUnitPrice', width: 120},
  {title: '当前生效时间', dataIndex: 'currentEffectiveFrom', width: 170},
  {title: '当前结束时间', dataIndex: 'currentEffectiveTo', width: 130},
  {title: '变更时间', dataIndex: 'operatedAt', width: 170},
  {title: '操作人', dataIndex: 'operator', width: 110},
  {title: '记录', dataIndex: 'currentDeleted', width: 100},
  {title: '详情', dataIndex: 'action', align: 'center', fixed: 'right', width: 100},
]);

async function load() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 请求失败交给共享反馈处理，避免重复提示。
    const r = await pricingApi.history(
      {
        ...query,
        effectiveFrom: effective.value?.[0] || null,
        effectiveTo: effective.value?.[1] || null,
        operatedFrom: operated.value?.[0] || null,
        operatedTo: operated.value?.[1] || null,
      },
      {suppressGlobalErrorMessage: true}
    );
    if (id === requestId) {
      rows.value = r.data.list;
      total.value = r.data.total;
    }
  } catch (e) {
    if (id === requestId) error.value = pricingError(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function search() {
  query.pageNum = 1;
  load();
}

onMounted(load);
</script>
