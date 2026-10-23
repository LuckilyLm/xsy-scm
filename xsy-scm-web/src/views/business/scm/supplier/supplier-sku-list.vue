<!--
  商品-供应商关系：先按公司看汇总卡片，点进去看这家公司的商品规格明细。
  调整供货关系不在这里做，入口在「供应商档案」→「关联商品」。
-->
<template>
  <section aria-label="商品-供应商关系">
    <!-- 一、公司卡片 -->
    <a-card v-if="!selected" size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <p class="scm-note scm-note--block">按公司查看可供货的商品；点卡片看明细。</p>
        <a-input-search v-model:value="keyword" placeholder="搜索公司名称或编码" allow-clear style="width: 260px" />
      </a-row>

      <a-alert v-if="cardError" type="error" show-icon :message="cardError" class="smart-margin-bottom10">
        <template #action>
          <a-button size="small" @click="loadCards">重新加载</a-button>
        </template>
      </a-alert>

      <a-spin :spinning="cardLoading">
        <div v-if="visibleCards.length" class="supplier-grid">
          <button v-for="item in visibleCards" :key="item.supplierId" type="button" class="supplier-card" @click="openSupplier(item)">
            <span class="supplier-card__head">
              <span class="supplier-card__name" :title="item.supplierName">{{ item.supplierName }}</span>
              <RightOutlined class="supplier-card__arrow" />
            </span>
            <span class="supplier-card__stats">
              <span class="supplier-card__stat"
                ><b>{{ item.productCount }}</b> 个商品</span
              >
              <span class="supplier-card__stat"
                ><b>{{ item.specCount }}</b> 个规格</span
              >
              <span v-if="item.defaultCount" class="supplier-card__stat"
                ><b>{{ item.defaultCount }}</b> 个优先</span
              >
            </span>
          </button>
        </div>
        <a-empty v-else-if="!cardLoading" description="没有匹配的供应商" />
      </a-spin>
    </a-card>

    <!-- 二、某家公司的商品明细 -->
    <a-card v-else size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <a-button type="link" class="scm-detail-header__back" @click="backToCards">
          <ArrowLeftOutlined />
          返回公司列表
        </a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_SUPPLIER_SKU" :refresh="load" />
      </a-row>

      <a-form class="smart-query-form" layout="inline">
        <a-row class="smart-query-form-row">
          <a-form-item label="商品规格" class="smart-query-form-item">
            <SkuSelect v-model:value="filters.skuId" width="300px" />
          </a-form-item>
          <a-form-item label="状态" class="smart-query-form-item">
            <SmartEnumSelect v-model:value="filters.status" enum-name="SUPPLIER_SKU_STATUS_ENUM" width="130px" />
          </a-form-item>
          <a-form-item class="smart-query-form-item">
            <a-space>
              <a-button type="primary" @click="search">查询</a-button>
              <a-button @click="resetDetail">重置</a-button>
              <a-button :disabled="!filters.skuId" :loading="compareLoading" @click="compare">同规格参考价比对</a-button>
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
        row-key="id"
        :loading="loading"
        :pagination="false"
        size="small"
        bordered
        :scroll="{x: scmColumnsWidth(detailColumns)}"
      >
        <template #bodyCell="{column, record}">
          <span v-if="column.dataIndex === 'spec'">{{ specText(record.specValuesSnapshot) }}</span>
          <span v-else-if="column.dataIndex === 'referencePrice'" class="amount">{{ record.referencePrice ?? '—' }}</span>
          <span v-else-if="column.dataIndex === 'purchaserName'">{{ record.purchaserName || '—' }}</span>
          <a-tag v-else-if="column.dataIndex === 'defaultFlag'" :color="record.defaultFlag ? 'blue' : 'default'">
            {{ record.defaultFlag ? '优先采购' : '—' }}
          </a-tag>
          <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 'ENABLED' ? 'green' : 'default'">
            {{ statusText(record.status) }}
          </a-tag>
        </template>
      </a-table>
      <div class="smart-query-table-page">
        <a-pagination
          v-model:current="filters.pageNum"
          v-model:page-size="filters.pageSize"
          :total="total"
          show-size-changer
          :show-total="(n: number) => `共 ${n} 条`"
          @change="load"
        />
      </div>
    </a-card>

    <a-modal v-model:open="compareOpen" title="多供应商参考价对比" :footer="null" width="1000px">
      <a-table
        :columns="compareColumns"
        :data-source="compareRows"
        row-key="supplierId"
        :loading="compareLoading"
        size="small"
        bordered
        :pagination="false"
        :scroll="{x: scmColumnsWidth(compareColumns)}"
      >
        <template #bodyCell="{column, record}">
          <span
            v-if="column.dataIndex === 'referencePrice' || column.dataIndex === 'lowestReferencePrice' || column.dataIndex === 'priceGapAmount'"
            class="amount"
            >{{ record[column.dataIndex] ?? '—' }}</span
          >
          <span v-else-if="column.dataIndex === 'priceGapPercent'" class="amount">{{
            record.priceGapPercent == null ? '—' : `${record.priceGapPercent}%`
          }}</span>
          <a-tag v-else-if="column.dataIndex === 'defaultFlag'" :color="record.defaultFlag ? 'blue' : 'default'">{{
            record.defaultFlag ? '优先采购' : '—'
          }}</a-tag>
        </template>
      </a-table>
      <p class="scm-note" style="margin-top: 8px">不足两家有效报价或最低参考价为 0 时不计算涨幅；缺少参考价不会视作零报价。</p>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {ArrowLeftOutlined, RightOutlined} from '@ant-design/icons-vue';
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {supplierSkuApi} from '/@/api/business/scm/supplier-sku-api';
import type {
  EnableStatus,
  SupplierSkuQuery,
  SupplierSkuRow,
  SupplierSkuComparisonRow,
  SupplierSkuSupplierSummary,
} from '/@/types/business/scm/supplier';
import {SUPPLIER_SKU_STATUS_ENUM} from '/@/constants/business/scm/supplier-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {supplierError} from './supplier-errors';
import {datetime} from '../common/scm-display';

// ---------------------------------------------------------------- 公司卡片

const cards = ref<SupplierSkuSupplierSummary[]>([]);
const cardLoading = ref(false);
const cardError = ref('');
const keyword = ref('');
const selected = ref<SupplierSkuSupplierSummary | null>(null);

/** 搜索只作用于已取回的公司，不再往返服务端：公司数量远小于商品行数。 */
const visibleCards = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  if (!text) return cards.value;
  return cards.value.filter((item) => `${item.supplierName} ${item.supplierCode}`.toLowerCase().includes(text));
});

async function loadCards() {
  cardLoading.value = true;
  cardError.value = '';
  try {
    // 加载失败由卡片区的 Alert 承担，不再让全局 toast 重复说一遍
    const response = await supplierSkuApi.summarize(undefined, {suppressGlobalErrorMessage: true});
    cards.value = response.data ?? [];
  } catch (e) {
    cards.value = [];
    cardError.value = supplierError(e);
  } finally {
    cardLoading.value = false;
  }
}

function openSupplier(item: SupplierSkuSupplierSummary) {
  selected.value = item;
  Object.assign(filters, {pageNum: 1, supplierId: item.supplierId, skuId: undefined, status: undefined});
  void load();
}

function backToCards() {
  selected.value = null;
  rows.value = [];
  total.value = 0;
  error.value = '';
}

// ---------------------------------------------------------------- 明细表格

const filters = reactive<SupplierSkuQuery>({pageNum: 1, pageSize: 20});
const rows = ref<SupplierSkuRow[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const compareOpen = ref(false);
const compareLoading = ref(false);
const compareRows = ref<SupplierSkuComparisonRow[]>([]);
const compareColumns: TableColumnsType<SupplierSkuComparisonRow> = [
  {title: '供应商', dataIndex: 'supplierName', width: 170},
  {title: '采购单位', dataIndex: 'purchaseUnit', width: 76},
  {title: '参考价', dataIndex: 'referencePrice', width: 125},
  {title: '最低有效参考价', dataIndex: 'lowestReferencePrice', width: 140},
  {title: '高于最低价金额', dataIndex: 'priceGapAmount', width: 140},
  {title: '高于最低价比例', dataIndex: 'priceGapPercent', width: 108},
  {title: '有效报价数', dataIndex: 'pricedSupplierCount', width: 92},
  {title: '优先采购', dataIndex: 'defaultFlag', width: 110, align: 'center'},
];

const statusText = (value: EnableStatus): string => SUPPLIER_SKU_STATUS_ENUM[value]?.desc || value;
const specText = (spec: Record<string, string> | undefined): string => {
  const values = Object.values(spec ?? {});
  return values.length ? values.join('/') : '—';
};

const columns = ref<TableColumnsType<SupplierSkuRow>>([
  {title: '供应商', dataIndex: 'supplierNameSnapshot', width: 200},
  {title: '供应商编码', dataIndex: 'supplierCodeSnapshot', width: 150},
  {title: '商品名称', dataIndex: 'skuNameSnapshot', width: 200},
  {title: '商品规格', dataIndex: 'spec', width: 150},
  {title: '商品规格编码', dataIndex: 'skuCodeSnapshot', width: 160},
  {title: '供应商货号', dataIndex: 'supplierItemCode', width: 160},
  {title: '采购单位', dataIndex: 'purchaseUnit', width: 76},
  {title: '参考价', dataIndex: 'referencePrice', width: 120},
  {title: '采购员', dataIndex: 'purchaserName', width: 110},
  {title: '优先采购', dataIndex: 'defaultFlag', width: 110, align: 'center'},
  {title: '状态', dataIndex: 'status', width: 90, align: 'center'},
  {title: '更新时间', dataIndex: 'updatedAt', width: 190, customRender: ({text}) => datetime(text)},
]);

/**
 * 明细页只显示这一家公司的商品，供应商两列每行都一样，占着 350px 没有信息量。
 * 列设置里仍然保留它们（供应商视角下可能想调顺序），这里只是渲染时过滤掉。
 */
const detailColumns = computed<TableColumnsType<SupplierSkuRow>>(
  () =>
    (columns.value as Array<{dataIndex?: unknown}>).filter(
      (column) => column.dataIndex !== 'supplierNameSnapshot' && column.dataIndex !== 'supplierCodeSnapshot'
    ) as TableColumnsType<SupplierSkuRow>
);

let requestId = 0;

async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头 Alert 承担，不再让全局 toast 重复说一遍
    const response = await supplierSkuApi.query({...filters}, {suppressGlobalErrorMessage: true});
    if (request === requestId) {
      rows.value = response.data.list;
      total.value = Number(response.data.total);
    }
  } catch (e) {
    if (request === requestId) error.value = supplierError(e);
  } finally {
    if (request === requestId) loading.value = false;
  }
}

async function compare() {
  const skuId = filters.skuId;
  if (!skuId) return;
  compareLoading.value = true;
  compareRows.value = [];
  try {
    const response = await supplierSkuApi.compareReferencePrices(skuId, {suppressGlobalErrorMessage: true});
    if (String(filters.skuId) !== String(skuId)) return;
    compareRows.value = response.data;
    compareOpen.value = true;
  } catch (e) {
    error.value = supplierError(e);
  } finally {
    compareLoading.value = false;
  }
}

function search() {
  filters.pageNum = 1;
  void load();
}

/** 重置只清「商品规格与状态」，公司是当前上下文，清掉就没有明细可看了。 */
function resetDetail() {
  Object.assign(filters, {pageNum: 1, skuId: undefined, status: undefined});
  void load();
}

onMounted(loadCards);
</script>

<style scoped>
.amount {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.supplier-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

/* 整卡可点：用 button 而不是 div，键盘 Tab 与回车都能进明细 */
.supplier-card {
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

.supplier-card:hover,
.supplier-card:focus-visible {
  border-color: var(--scm-primary, #1677ff);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  outline: none;
}

.supplier-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.supplier-card__name {
  flex: 1;
  overflow: hidden;
  color: var(--scm-text, #1f2329);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 箭头只做「可以点进去」的暗示，不单独承载交互 */
.supplier-card__arrow {
  color: var(--scm-text-secondary, #667085);
  font-size: 12px;
  transition: color 0.2s;
}

.supplier-card:hover .supplier-card__arrow,
.supplier-card:focus-visible .supplier-card__arrow {
  color: var(--scm-primary, #1677ff);
}

.supplier-card__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--scm-text-secondary, #667085);
  font-size: 12px;
}

.supplier-card__stat b {
  color: var(--scm-text, #1f2329);
  font-variant-numeric: tabular-nums;
}

/* 返回与列设置同一行：公共样式自带 8px 下边距，会把链接顶高，这里抹平 */
:deep(.scm-detail-header__back.ant-btn) {
  margin-bottom: 0;
}
</style>
