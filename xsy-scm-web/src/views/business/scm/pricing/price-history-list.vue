<!--  新写：双来源价格历史与字段级前后对照。 -->
<template>
  <a-form layout="inline" class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="来源" class="smart-query-form-item">
        <a-select v-model:value="query.source" allow-clear style="width:150px" :options="sourceOptions"/>
      </a-form-item>
      <a-form-item label="客户" class="smart-query-form-item">
        <CustomerSelect v-model:value="query.customerId" width="180px"/>
      </a-form-item>
      <a-form-item label="客户类型" class="smart-query-form-item">
        <CustomerTypeSelect v-model:value="query.customerTypeId" width="150px"/>
      </a-form-item>
      <a-form-item label="商品规格" class="smart-query-form-item">
        <SkuSelect v-model:value="query.skuId" width="230px" :disabled-statuses="[]"/>
      </a-form-item>
      <a-form-item label="变更类型" class="smart-query-form-item">
        <a-select v-model:value="query.operationType" allow-clear style="width:110px" :options="operationOptions"/>
      </a-form-item>
      <a-form-item label="有效区间" class="smart-query-form-item">
        <a-range-picker v-model:value="effective" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" :allow-empty="[true,true]"/>
      </a-form-item>
      <a-form-item label="变更区间" class="smart-query-form-item">
        <a-range-picker v-model:value="operated" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" :allow-empty="[true,true]"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button type="primary" @click="search" v-privilege="'scm:pricing:history:query'">查询</a-button>
      </a-form-item>
    </a-row>
  </a-form>
  <a-card size="small" :bordered="false">
    <div class="smart-table-setting-block">
      <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PRICING_HISTORY" :refresh="load"/>
    </div>
    <a-table :data-source="rows" :columns="columns" :row-key="(r:HistoryRow)=>`${r.source}-${r.historyId}`"
             :loading="loading" :pagination="false" size="small" bordered :scroll="{x:1940}">
      <template #bodyCell="{record,column}">
        <template v-if="column.dataIndex==='source'">
          {{ historyLabel(HISTORY_SOURCE_LABEL, record.source) }}
        </template>
        <template v-else-if="column.dataIndex==='skuCode'">
          <span class="scm-mono">{{ record.skuCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex==='operationType'">
          <ScmStatusTag
              :tone="HISTORY_OPERATION_TONE[record.operationType] ?? 'neutral'"
              :label="historyLabel(HISTORY_OPERATION_LABEL, record.operationType)"
          />
        </template>
        <template v-else-if="column.dataIndex==='currentUnitPrice'">
          <span class="scm-money">{{ formatAmount(record.currentUnitPrice) }}</span>
        </template>
        <template v-else-if="column.dataIndex==='currentEffectiveFrom'">
          <!-- 生效 / 结束时间属于价格本身的事实，必须留在列表上 -->
          {{ datetime(record.currentEffectiveFrom) }}
        </template>
        <template v-else-if="column.dataIndex==='currentEffectiveTo'">
          {{ record.currentEffectiveTo ? datetime(record.currentEffectiveTo) : '长期有效' }}
        </template>
        <template v-else-if="column.dataIndex==='operatedAt'">
          {{ datetime(record.operatedAt) }}
        </template>
        <template v-else-if="column.dataIndex==='currentDeleted'">
          <ScmStatusTag :tone="record.currentDeleted ? 'error' : 'neutral'"
                        :label="record.currentDeleted ? '已删除' : '有效记录'"/>
        </template>
        <template v-else-if="column.dataIndex==='action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" @click="selected=record">变更详情</a-button>
          </a-space>
        </template>
        <template v-else>{{record[column.dataIndex]??'—'}}</template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination v-model:current="query.pageNum" v-model:page-size="query.pageSize" :total="total"
                    show-size-changer @change="load"/>
    </div>
  </a-card>
  <a-modal title="价格变更详情" :open="!!selected" :footer="null" :width="760" @cancel="selected=undefined">
    <ScmDiffTable :before="selected?.beforeData" :after="selected?.afterData"/>
  </a-modal>
</template>
<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import CustomerTypeSelect from '/@/components/business/scm/customer-type-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import {formatAmount} from '/@/utils/scm-amount';
import type {PriceQuery, HistoryRow} from '/@/types/business/scm/pricing';
import {pricingError} from './pricing-errors';
import {
  HISTORY_OPERATION_LABEL,
  HISTORY_OPERATION_TONE,
  HISTORY_SOURCE_LABEL,
  historyLabel,
} from './pricing-display';
import ScmDiffTable from '/@/views/business/scm/common/scm-diff-table.vue';
import {datetime} from '../common/scm-display';
import {useScmErrorToast} from '../common/scm-error-toast';

const query = reactive<PriceQuery & { source?: string; operationType?: string }>({pageNum: 1, pageSize: 20}),
    effective = ref<[string, string]>(), operated = ref<[string, string]>(), rows = ref<HistoryRow[]>([]),
    total = ref(0), loading = ref(false), selected = ref<HistoryRow>();
const error = useScmErrorToast();
let requestId = 0;

// 下拉与列表列共用同一份中文（pricing-display），避免两处各写一份
const sourceOptions = Object.entries(HISTORY_SOURCE_LABEL).map(([value, label]) => ({value, label}));
const operationOptions = Object.entries(HISTORY_OPERATION_LABEL).map(([value, label]) => ({value, label}));

/**
 * 列按「哪来的 / 谁的价格 / 什么货 / 改了什么 / 现在是什么价、有效期到什么时候 / 谁在什么时候改的」排列，
 * 一列一个值：客户与客户类型、规格名与规格编码、生效与结束时间、变更时间与操作人各自成列。
 *
 * 时间不能隐藏：本页是变更账本，生效时间与变更时间就是它要回答的问题本身。
 */
const columns = ref<TableColumnsType<HistoryRow>>([
  {title: '来源', dataIndex: 'source', width: 120},
  {title: '客户', dataIndex: 'customerName', width: 170},
  {title: '客户类型', dataIndex: 'customerTypeName', width: 150},
  {title: '商品', dataIndex: 'productName', width: 150},
  {title: '商品规格', dataIndex: 'specName', width: 170},
  {title: '商品规格编码', dataIndex: 'skuCode', width: 140},
  {title: '变更', dataIndex: 'operationType', align: 'center', width: 100},
  {title: '当前价格', dataIndex: 'currentUnitPrice', align: 'right', width: 120},
  {title: '当前生效时间', dataIndex: 'currentEffectiveFrom', width: 170},
  {title: '当前结束时间', dataIndex: 'currentEffectiveTo', width: 130},
  {title: '变更时间', dataIndex: 'operatedAt', width: 170},
  {title: '操作人', dataIndex: 'operator', width: 110},
  {title: '记录', dataIndex: 'currentDeleted', align: 'center', width: 100},
  {title: '详情', dataIndex: 'action', align: 'center', fixed: 'right', width: 100},
]);

async function load() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await pricingApi.history({
      ...query,
      effectiveFrom: effective.value?.[0] || null,
      effectiveTo: effective.value?.[1] || null,
      operatedFrom: operated.value?.[0] || null,
      operatedTo: operated.value?.[1] || null
    });
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
