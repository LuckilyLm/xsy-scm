<!-- 按商品规格查看可供货的供应商。修改供货关系需前往供应商档案的“关联商品”。 -->
<template>
  <section aria-label="商品-供应商关系">
    <a-form class="smart-query-form" layout="inline">
      <a-row class="smart-query-form-row">
        <a-form-item label="供应商" class="smart-query-form-item">
          <SupplierSelect v-model:value="filters.supplierId" width="240px" />
        </a-form-item>
        <a-form-item label="商品规格" class="smart-query-form-item">
          <SkuSelect v-model:value="filters.skuId" width="300px" />
        </a-form-item>
        <a-form-item label="状态" class="smart-query-form-item">
          <SmartEnumSelect v-model:value="filters.status" enum-name="SUPPLIER_SKU_STATUS_ENUM" width="130px" />
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-space>
            <a-button type="primary" @click="search">查询</a-button>
            <a-button @click="reset">重置</a-button>
            <a-button :disabled="!filters.skuId" :loading="compareLoading" @click="compare">同规格参考价比对</a-button>
          </a-space>
        </a-form-item>
      </a-row>
    </a-form>

    <a-card size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <p class="scm-note scm-note--block">查看各商品规格对应的供货商；需要调整供货关系，请到「供应商档案」→「关联商品」。</p>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_SUPPLIER_SKU" :refresh="load"/>
      </a-row>
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
        <template #action>
          <a-button size="small" @click="load">重新加载</a-button>
        </template>
      </a-alert>
      <a-table
          :data-source="rows"
          :columns="columns"
          row-key="id"
          :loading="loading"
          :pagination="false"
          size="small"
          bordered
          :scroll="{ x: 1400 }"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'supplierNameSnapshot'">{{ record.supplierNameSnapshot }}</span>
          <span v-else-if="column.dataIndex === 'skuNameSnapshot'">{{ record.skuNameSnapshot }}</span>
          <span v-else-if="column.dataIndex === 'spec'">{{ specText(record.specValuesSnapshot) }}</span>
          <span v-else-if="column.dataIndex === 'referencePrice'" class="amount">{{
              record.referencePrice ?? '—'
            }}</span>
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
      <a-alert type="info" show-icon message="仅比较当前可采购且同一商品规格、同一采购单位的有效供应商参考价；不是采购合同价、实际成交价或入库成本。" class="smart-margin-bottom10"/>
      <a-table :columns="compareColumns" :data-source="compareRows" row-key="supplierId" :loading="compareLoading"
               size="small" bordered :pagination="false" :scroll="{x: 1100}">
        <template #bodyCell="{column, record}">
          <span v-if="column.dataIndex === 'referencePrice' || column.dataIndex === 'lowestReferencePrice' || column.dataIndex === 'priceGapAmount'" class="amount">{{ record[column.dataIndex] ?? '—' }}</span>
          <span v-else-if="column.dataIndex === 'priceGapPercent'" class="amount">{{ record.priceGapPercent == null ? '—' : `${record.priceGapPercent}%` }}</span>
          <a-tag v-else-if="column.dataIndex === 'defaultFlag'" :color="record.defaultFlag ? 'blue' : 'default'">{{ record.defaultFlag ? '优先采购' : '—' }}</a-tag>
        </template>
      </a-table>
      <p class="scm-note" style="margin-top: 8px">不足两家有效报价或最低参考价为 0 时不计算涨幅；缺少参考价不会视作零报价。</p>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {supplierSkuApi} from '/@/api/business/scm/supplier-sku-api';
import type {EnableStatus, SupplierSkuQuery, SupplierSkuRow, SupplierSkuComparisonRow} from '/@/types/business/scm/supplier';
import {SUPPLIER_SKU_STATUS_ENUM} from '/@/constants/business/scm/supplier-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {supplierError} from './supplier-errors';
import {datetime} from '../common/scm-display';

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
  {title: '采购单位', dataIndex: 'purchaseUnit', width: 100, align: 'center'},
  {title: '参考价', dataIndex: 'referencePrice', width: 125, align: 'right'},
  {title: '最低有效参考价', dataIndex: 'lowestReferencePrice', width: 140, align: 'right'},
  {title: '高于最低价金额', dataIndex: 'priceGapAmount', width: 140, align: 'right'},
  {title: '高于最低价比例', dataIndex: 'priceGapPercent', width: 140, align: 'right'},
  {title: '有效报价数', dataIndex: 'pricedSupplierCount', width: 115, align: 'right'},
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
  {title: '采购单位', dataIndex: 'purchaseUnit', width: 100, align: 'center'},
  {title: '参考价', dataIndex: 'referencePrice', width: 120, align: 'right'},
  {title: '采购员', dataIndex: 'purchaserName', width: 110},
  {title: '优先采购', dataIndex: 'defaultFlag', width: 110, align: 'center'},
  {title: '状态', dataIndex: 'status', width: 90, align: 'center'},
  {title: '更新时间', dataIndex: 'updatedAt', width: 190, customRender: ({text}) => datetime(text)},
]);

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

function reset() {
  Object.assign(filters, {
    pageNum: 1,
    supplierId: undefined,
    skuId: undefined,
    status: undefined,
    sortItemList: undefined
  });
  void load();
}

onMounted(load);
</script>

<style scoped>
.amount {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
