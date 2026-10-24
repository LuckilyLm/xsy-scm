<!-- 客户可见性：先按客户看汇总，再点进去看这家客户的可见商品规格。 -->
<template>
  <section aria-label="客户商品规格可见性">
    <!-- 客户卡片 -->
    <a-card v-if="!selected" size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <p class="scm-note scm-note--block">按客户查看可见的商品规格；点卡片看明细。</p>
        <a-space>
          <a-input-search v-model:value="keyword" placeholder="搜索客户名称" allow-clear style="width: 240px" />
          <a-select v-model:value="policyFilter" allow-clear placeholder="全部策略" style="width: 150px">
            <a-select-option value="ALLOWLIST">指定可售规格</a-select-option>
            <a-select-option value="ALL_ENABLED">全部可售</a-select-option>
          </a-select>
        </a-space>
      </a-row>

      <a-alert v-if="cardError" type="error" show-icon :message="cardError" class="smart-margin-bottom10">
        <template #action>
          <a-button size="small" @click="loadCards">重新加载</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="cardLoading">
        <div v-if="visibleCards.length" class="customer-grid">
          <button v-for="item in visibleCards" :key="item.customerId" type="button" class="customer-card" @click="openCustomer(item)">
            <span class="customer-card__head">
              <span class="customer-card__name" :title="item.customerName">{{ item.customerName }}</span>
            </span>
            <span v-if="item.customerTypeName" class="customer-card__meta">{{ item.customerTypeName }}</span>
            <span class="customer-card__stats">
              <span class="customer-card__stat">{{ visibilityPolicyLabel(item.visibilityPolicy) }}</span>
              <template v-if="item.visibilityPolicy === 'ALLOWLIST'">
                <span class="customer-card__stat"
                  ><b>{{ item.productCount }}</b> 个商品</span
                >
                <span class="customer-card__stat"
                  ><b>{{ item.specCount }}</b> 个规格</span
                >
              </template>
              <span v-else class="customer-card__stat">不限具体商品规格</span>
            </span>
          </button>
        </div>
        <a-empty v-else-if="!cardLoading" description="没有匹配的客户" />
      </a-spin>
    </a-card>

    <!-- 某家客户的可见商品规格明细 -->
    <a-card v-else size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <a-button type="link" class="scm-detail-header__back" @click="backToCards">
          <ArrowLeftOutlined />
          返回客户列表
        </a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_CUSTOMER_SKU_VISIBILITY" :refresh="load" />
      </a-row>

      <div class="customer-visibility-head">
        <h3 class="customer-visibility-head__name">{{ selected.customerName }}</h3>
      </div>

      <a-empty v-if="selected.visibilityPolicy === 'ALL_ENABLED'" description="该客户设置为「全部可售」，不限制具体商品规格" />

      <template v-else>
        <a-form class="smart-query-form" layout="inline" @submit.prevent="search">
          <a-row class="smart-query-form-row">
            <a-form-item label="商品规格" class="smart-query-form-item">
              <SkuSelect v-model:value="filters.skuId" width="300px" />
            </a-form-item>
            <a-form-item class="smart-query-form-item">
              <a-space>
                <a-button type="primary" @click="search">查询</a-button>
                <a-button @click="resetDetail">重置</a-button>
              </a-space>
            </a-form-item>
          </a-row>
        </a-form>

        <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button size="small" @click="load">重新加载</a-button>
          </template>
        </a-alert>

        <a-table
          :data-source="rows"
          :columns="detailColumns"
          :row-key="visibilityRowKey"
          :loading="loading"
          :pagination="false"
          :locale="{emptyText: error ? ' ' : '这家客户还没有指定可售的商品规格'}"
          :scroll="{x: scmColumnsWidth(detailColumns)}"
          size="small"
          bordered
        >
          <template #bodyCell="{column, record}">
            <span v-if="column.dataIndex === 'productName'">{{ record.productName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'specName'">{{ record.specName || '—' }}</span>
            <template v-else-if="column.dataIndex === 'status'">
              <a-space size="small">
                <ScmStatusTag :tone="shelfStatusTone(record.spuStatus)" :label="`商品${shelfStatusLabel(record.spuStatus)}`" />
                <ScmStatusTag :tone="shelfStatusTone(record.skuStatus)" :label="`规格${shelfStatusLabel(record.skuStatus)}`" />
              </a-space>
            </template>
            <span v-else-if="column.dataIndex === 'createdAt'">{{ datetime(record.createdAt) }}</span>
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
      </template>
    </a-card>
  </section>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, onMounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {ArrowLeftOutlined} from '@ant-design/icons-vue';
import {customerVisibilityApi} from '/@/api/business/scm/customer-visibility-api';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import type {ScmId} from '/@/types/business/scm/customer';
import type {CustomerVisibilitySummary, VisibilityRow} from '/@/types/business/scm/pricing';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {customerError} from './customer-errors';
import {shelfStatusLabel} from '/@/constants/business/scm/product-const';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {datetime} from '../common/scm-display';

// 客户卡片

const cards = ref<CustomerVisibilitySummary[]>([]);
const cardLoading = ref(false);
const cardError = ref('');
const keyword = ref('');
const policyFilter = ref<string | undefined>();
const selected = ref<CustomerVisibilitySummary | null>(null);

/** 搜索与策略过滤只在已取回的客户里做，不请求服务端。 */
const visibleCards = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return cards.value.filter((item) => {
    const matchesKeyword = !text || `${item.customerName} ${item.customerCode}`.toLowerCase().includes(text);
    const matchesPolicy = !policyFilter.value || item.visibilityPolicy === policyFilter.value;
    return matchesKeyword && matchesPolicy;
  });
});

async function loadCards() {
  cardLoading.value = true;
  cardError.value = '';
  try {
    // 加载失败由卡片区 Alert 提示，不再弹全局 toast
    const response = await customerVisibilityApi.summarize({suppressGlobalErrorMessage: true});
    cards.value = response.data ?? [];
  } catch (cause) {
    cards.value = [];
    cardError.value = customerError(cause);
  } finally {
    cardLoading.value = false;
  }
}

function openCustomer(item: CustomerVisibilitySummary) {
  selected.value = item;
  rows.value = [];
  total.value = 0;
  Object.assign(filters, {pageNum: 1, pageSize: 20, skuId: undefined});
  if (item.visibilityPolicy === 'ALLOWLIST') {
    void load();
  }
}

function backToCards() {
  selected.value = null;
  rows.value = [];
  total.value = 0;
  error.value = '';
}

// 明细表格

interface VisibilityQuery {
  pageNum: number;
  pageSize: number;
  skuId?: ScmId;
}

const filters = reactive<VisibilityQuery>({pageNum: 1, pageSize: 20});
const rows = ref<VisibilityRow[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
let requestId = 0;

const shelfStatusTone = (value?: string | null): ScmStatusTone => (value === 'ON_SHELF' ? 'success' : value === 'OFF_SHELF' ? 'neutral' : 'warning');

// 客户相关列只在卡片上展示，明细里由 detailColumns 过滤；编码属于内部标识，不上列表。
type VisibilityListColumn = TableColumnsType<VisibilityRow>[number] & {showFlag?: boolean};
const columns = ref<VisibilityListColumn[]>([
  {title: '客户名称', dataIndex: 'customerName', width: 180},
  {title: '客户类型', dataIndex: 'customerTypeName', width: 120},
  {title: '商品名称', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'specName', width: 150},
  {title: '可见策略', dataIndex: 'visibilityPolicy', width: 125, align: 'center'},
  {title: '商品状态', dataIndex: 'status', width: 190, align: 'center'},
  {title: '加入时间', dataIndex: 'createdAt', width: 170},
]);

const detailColumns = computed<TableColumnsType<VisibilityRow>>(
  () =>
    (columns.value as Array<{dataIndex?: unknown}>).filter(
      (column) => !['customerName', 'customerCode', 'customerTypeName', 'visibilityPolicy'].includes(String(column.dataIndex))
    ) as TableColumnsType<VisibilityRow>
);

function visibilityPolicyLabel(policy: string): string {
  return policy === 'ALLOWLIST' ? '指定可售规格' : policy === 'ALL_ENABLED' ? '全部可售' : policy;
}

function visibilityRowKey(row: VisibilityRow): string {
  return `${row.customerId}:${row.skuId ?? 'all-enabled'}`;
}

async function load() {
  if (!selected.value) return;
  const currentRequest = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头 Alert 提示，不再弹全局 toast
    const response = await customerVisibilityApi.query({...filters, customerId: selected.value.customerId}, {suppressGlobalErrorMessage: true});
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

/** 只清商品规格，保留当前客户。 */
function resetDetail() {
  Object.assign(filters, {pageNum: 1, skuId: undefined});
  void load();
}

onMounted(loadCards);
</script>

<style scoped>
.customer-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

/* 用 button 保证键盘可操作 */
.customer-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--scm-border, #e5e6eb);
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.customer-card:hover,
.customer-card:focus-visible {
  border-color: var(--scm-primary, #1677ff);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  outline: none;
}

.customer-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.customer-card__name {
  flex: 1;
  overflow: hidden;
  color: var(--scm-text, #1f2329);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.customer-card__meta {
  color: var(--scm-text-secondary, #667085);
  font-size: 12px;
}

.customer-card__stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  color: var(--scm-text-secondary, #667085);
  font-size: 12px;
}

.customer-card__stat b {
  color: var(--scm-text, #1f2329);
  font-variant-numeric: tabular-nums;
}

.customer-visibility-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.customer-visibility-head__name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

/* 返回链接与列设置同一行，去掉公共下边距 */
:deep(.scm-detail-header__back.ant-btn) {
  margin-bottom: 0;
}
</style>
