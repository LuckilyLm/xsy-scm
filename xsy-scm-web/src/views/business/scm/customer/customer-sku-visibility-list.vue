<template>
  <section aria-label="客户 SKU 可见性">
    <a-form class="smart-query-form" layout="inline" @submit.prevent="search">
      <a-row class="smart-query-form-row">
        <a-form-item label="客户" class="smart-query-form-item">
          <CustomerSelect v-model:value="filters.customerId" width="220px" />
        </a-form-item>
        <a-form-item label="SKU" class="smart-query-form-item">
          <SkuSelect v-model:value="filters.skuId" width="280px" />
        </a-form-item>
        <a-form-item label="可见策略" class="smart-query-form-item">
          <a-select v-model:value="filters.visibilityPolicy" allow-clear placeholder="全部策略" style="width: 150px">
            <a-select-option value="ALLOWLIST">SKU 白名单</a-select-option>
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
      <a-alert
        class="smart-margin-bottom10"
        type="info"
        show-icon
        message="列表按客户归属范围展示可见策略与 SKU 白名单；“全部可售”客户显示一行，不会展开每个可售 SKU。按 SKU 查询只筛选明确配置的白名单关联。"
      />
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
        <template #action>
          <a-button size="small" @click="load">重新加载</a-button>
        </template>
      </a-alert>

      <a-table
        :data-source="rows"
        :columns="columns"
        :row-key="visibilityRowKey"
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '当前授权范围内没有匹配的客户可见性配置'}"
        :scroll="{x: 1320}"
        size="small"
      >
        <template #bodyCell="{column, record}">
          <template v-if="column.key === 'visibilityPolicy'">
            <a-tag :color="record.visibilityPolicy === 'ALLOWLIST' ? 'blue' : 'default'">
              {{ visibilityPolicyLabel(record.visibilityPolicy) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <span v-if="record.skuId == null">适用于全部可售 SKU</span>
            <a-space v-else size="small">
              <a-tag>{{ record.spuStatus || 'SPU 状态未知' }}</a-tag>
              <a-tag>{{ record.skuStatus || 'SKU 状态未知' }}</a-tag>
            </a-space>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button
              v-privilege="'scm:customer:query'"
              type="link"
              size="small"
              @click="openCustomer(record.customerId)"
            >
              客户详情
            </a-button>
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
import {customerError} from './customer-errors';
import {datetime} from '../common/scm-display';

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

const columns = ref<TableColumnsType<VisibilityRow>>([
  {title: '客户编码', dataIndex: 'customerCode', width: 140},
  {title: '客户名称', dataIndex: 'customerName', width: 180},
  {title: '客户类型', dataIndex: 'customerTypeName', width: 130},
  {title: 'SKU 编码', dataIndex: 'skuCode', width: 150},
  {title: '商品', dataIndex: 'productName', width: 200},
  {title: '规格', dataIndex: 'specName', width: 160},
  {title: '可见策略', key: 'visibilityPolicy', width: 125},
  {title: '商品状态', key: 'status', width: 190},
  {title: '加入时间', dataIndex: 'createdAt', width: 180, customRender: ({text}) => text ? datetime(text) : '—'},
  {title: '操作', key: 'action', width: 100, fixed: 'right', align: 'right'},
]);

function visibilityPolicyLabel(policy: string): string {
  return policy === 'ALLOWLIST' ? 'SKU 白名单' : policy === 'ALL_ENABLED' ? '全部可售' : policy;
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
