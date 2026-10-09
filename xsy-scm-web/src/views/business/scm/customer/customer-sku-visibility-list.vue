<template>
  <section aria-label="客户商品规格可见性">
    <a-form class="smart-query-form" layout="inline" @submit.prevent="search">
      <a-row class="smart-query-form-row">
        <a-form-item label="客户" class="smart-query-form-item">
          <CustomerSelect v-model:value="filters.customerId" width="220px" />
        </a-form-item>
        <a-form-item label="商品规格" class="smart-query-form-item">
          <SkuSelect v-model:value="filters.skuId" width="280px" />
        </a-form-item>
        <a-form-item label="可见策略" class="smart-query-form-item">
          <a-select v-model:value="filters.visibilityPolicy" allow-clear placeholder="全部策略" style="width: 150px">
            <a-select-option value="ALLOWLIST">商品规格白名单</a-select-option>
            <a-select-option value="ALL_ENABLED">全部可售</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-space>
            <a-button type="primary" @click="search">查询</a-button>
            <a-button @click="reset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-row>
    </a-form>

    <a-card size="small" :bordered="false">
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
        <template #action>
          <a-button size="small" @click="load">重新加载</a-button>
        </template>
      </a-alert>

      <a-row class="smart-table-btn-block scm-table-toolbar">
        <div class="smart-table-setting-block">
          <TableOperator
              v-model="columns"
              :table-id="TABLE_ID_CONST.BUSINESS.SCM_CUSTOMER_SKU_VISIBILITY"
              :refresh="load"
          />
        </div>
      </a-row>

      <a-table
        :data-source="rows"
        :columns="columns"
        :row-key="visibilityRowKey"
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '当前授权范围内没有匹配的客户可见性配置'}"
        :scroll="{x: 1045}"
        size="small"
      >
        <template #bodyCell="{column, record}">
          <span v-if="column.dataIndex === 'customerName'">{{ record.customerName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'customerCode'" class="scm-mono">{{ record.customerCode || '—' }}</span>
          <span v-else-if="column.dataIndex === 'customerTypeName'">{{ record.customerTypeName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'productName'">{{ record.productName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'specName'">{{ record.specName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'skuCode'" class="scm-mono">{{ record.skuCode || '—' }}</span>
          <template v-else-if="column.dataIndex === 'visibilityPolicy'">
            <a-tag :color="record.visibilityPolicy === 'ALLOWLIST' ? 'blue' : 'default'">
              {{ visibilityPolicyLabel(record.visibilityPolicy) }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <span v-if="record.skuId == null">适用于全部可售商品规格</span>
            <a-space v-else size="small">
              <ScmStatusTag :tone="shelfStatusTone(record.spuStatus)"
                            :label="`商品${shelfStatusLabel(record.spuStatus)}`"/>
              <ScmStatusTag :tone="shelfStatusTone(record.skuStatus)"
                            :label="`规格${shelfStatusLabel(record.skuStatus)}`"/>
            </a-space>
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <div class="scm-table-actions">
              <a-button
                v-privilege="'scm:customer:query'"
                type="link"
                size="small"
                @click="openCustomer(record.customerId)"
              >
                客户详情
              </a-button>
            </div>
          </template>
        </template>
      </a-table>

      <div class="smart-query-table-page">
        <a-pagination
          v-model:current="filters.pageNum"
          v-model:page-size="filters.pageSize"
          :total="total"
          show-size-changer
          :show-total="(count: number) => `共 ${count} 条`"
          @change="load"
        />
      </div>
    </a-card>
  </section>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import {customerVisibilityApi} from '/@/api/business/scm/customer-visibility-api';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import type {ScmId} from '/@/types/business/scm/customer';
import type {VisibilityRow} from '/@/types/business/scm/pricing';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {customerError} from './customer-errors';
import {shelfStatusLabel} from '/@/constants/business/scm/product-const';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';

interface VisibilityQuery {
  pageNum: number;
  pageSize: number;
  customerId?: ScmId;
  skuId?: ScmId;
  visibilityPolicy?: string;
}

const router = useRouter();
const filters = reactive<VisibilityQuery>({pageNum: 1, pageSize: 20});
const rows = ref<VisibilityRow[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
let requestId = 0;

const shelfStatusTone = (value?: string | null): ScmStatusTone =>
    value === 'ON_SHELF' ? 'success' : value === 'OFF_SHELF' ? 'neutral' : 'warning';

// 编码列单独保留，但默认收起；列设置仍可打开这两项。
type VisibilityListColumn = TableColumnsType<VisibilityRow>[number] & {showFlag?: boolean};
const columns = ref<VisibilityListColumn[]>([
  {title: '客户名称', dataIndex: 'customerName', width: 180},
  {title: '客户编码', dataIndex: 'customerCode', width: 120, showFlag: false},
  {title: '客户类型', dataIndex: 'customerTypeName', width: 120},
  {title: '商品名称', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'specName', width: 150},
  {title: '规格编码', dataIndex: 'skuCode', width: 135, showFlag: false},
  {title: '可见策略', dataIndex: 'visibilityPolicy', width: 125},
  {title: '商品状态', dataIndex: 'status', width: 190},
  {title: '操作', dataIndex: 'action', width: 100, fixed: 'right', align: 'center'},
]);

function visibilityPolicyLabel(policy: string): string {
  return policy === 'ALLOWLIST' ? '商品规格白名单' : policy === 'ALL_ENABLED' ? '全部可售' : policy;
}

function visibilityRowKey(row: VisibilityRow): string {
  return `${row.customerId}:${row.skuId ?? 'all-enabled'}`;
}

async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const response = await customerVisibilityApi.query({...filters});
    if (currentRequest === requestId) {
      rows.value = response.data.list;
      total.value = Number(response.data.total);
    }
  } catch (cause) {
    if (currentRequest === requestId) {
      error.value = customerError(cause);
    }
  } finally {
    if (currentRequest === requestId) {
      loading.value = false;
    }
  }
}

function search() {
  filters.pageNum = 1;
  void load();
}

function reset() {
  filters.customerId = undefined;
  filters.skuId = undefined;
  filters.visibilityPolicy = undefined;
  filters.pageNum = 1;
  filters.pageSize = 20;
  void load();
}

function openCustomer(customerId: ScmId) {
  void router.push({path: '/customer/customer-detail', query: {customerId: String(customerId)}});
}

onMounted(load);
</script>
