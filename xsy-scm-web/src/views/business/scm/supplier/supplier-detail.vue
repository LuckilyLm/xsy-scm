<!--
  * 供应商详情（独立隐藏路由，可深链）
  *
  * 来源：**W1 派生** —— 结构照抄 `views/business/scm/product/product-detail.vue`
  * （返回按钮 + 刷新 + `a-descriptions` + 分节 `a-divider` + 子表格）。
  *
  * C 的供应商详情是列表抽屉里的只读区，没有独立详情页；V2 需要可深链的独立页
  * （对应 `t_menu` 463「供应商详情」，`visible_flag = false`，与 W1 商品详情同策略）。
  *
  * 适配 / 新增：
  * - 路由参数 `supplierId`；
  * - 列表 VO 不含 `address` / `remark`，详情页是唯一能看到完整信息的地方；
  * - 新增「已关联商品」只读表格，数据来自 `GET /scm/supplier/sku/list/{supplierId}`，
  *   展示的是**冻结快照**（`skuNameSnapshot` 等）—— 商品后来改名不会改写历史关联的展示口径。
-->
<template>
  <a-card size="small" :bordered="false" :loading="loading">
    <a-space class="smart-margin-bottom10">
      <a-button @click="router.push('/supplier/supplier-list')">返回供应商列表</a-button>
      <a-button @click="load">刷新详情</a-button>
    </a-space>
    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action>
        <a-button size="small" @click="load">重新加载</a-button>
      </template>
    </a-alert>
    <template v-else-if="supplier">
      <a-descriptions :title="supplier.name" bordered :column="{ xs: 1, sm: 2, lg: 3 }">
        <a-descriptions-item label="供应商编码">{{ supplier.supplierCode }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="supplier.status === 'ENABLED' ? 'green' : 'default'">{{ statusText(supplier.status) }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="关联商品数">{{ supplier.skuCount ?? 0 }}</a-descriptions-item>
        <a-descriptions-item label="联系人">{{ supplier.contactName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="联系电话">{{ supplier.contactPhone || '—' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ datetime(supplier.createdAt) }}</a-descriptions-item>
        <a-descriptions-item label="地址" :span="2">{{ supplier.address || '—' }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">{{ datetime(supplier.updatedAt) }}</a-descriptions-item>
        <a-descriptions-item label="备注" :span="3">{{ supplier.remark || '—' }}</a-descriptions-item>
      </a-descriptions>

      <a-divider orientation="left">已关联商品（快照）</a-divider>
      <a-table
          :data-source="relations"
          :columns="relationColumns"
          row-key="id"
          size="small"
          bordered
          :pagination="false"
          :scroll="{ x: 1000 }"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'skuNameSnapshot'">{{ record.skuNameSnapshot }}</span>
          <span v-else-if="column.dataIndex === 'spec'">{{ specText(record.specValuesSnapshot) }}</span>
          <span v-else-if="column.dataIndex === 'referencePrice'" class="amount">{{
              record.referencePrice ?? '—'
            }}</span>
          <span v-else-if="column.dataIndex === 'purchaserName'">{{ record.purchaserName || '—' }}</span>
          <a-tag v-else-if="column.dataIndex === 'defaultFlag'" :color="record.defaultFlag ? 'blue' : 'default'">
            {{ record.defaultFlag ? '默认来源' : '—' }}
          </a-tag>
          <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 'ENABLED' ? 'green' : 'default'">
            {{ skuStatusText(record.status) }}
          </a-tag>
        </template>
      </a-table>
      <a-empty v-if="!loading && relations.length === 0" description="尚未关联任何商品"/>
    </template>
  </a-card>
</template>

<script setup lang="ts">
import {ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import {supplierApi} from '/@/api/business/scm/supplier-api';
import {supplierSkuApi} from '/@/api/business/scm/supplier-sku-api';
import type {EnableStatus, SupplierDetail, SupplierSkuRow} from '/@/types/business/scm/supplier';
import {SUPPLIER_SKU_STATUS_ENUM, SUPPLIER_STATUS_ENUM} from '/@/constants/business/scm/supplier-const';
import {supplierError} from './supplier-errors';
import {datetime} from '../common/scm-display';

const route = useRoute();
const router = useRouter();
const supplier = ref<SupplierDetail>();
const relations = ref<SupplierSkuRow[]>([]);
const loading = ref(false);
const error = ref('');

const statusText = (value: EnableStatus): string => SUPPLIER_STATUS_ENUM[value]?.desc || value;
const skuStatusText = (value: EnableStatus): string => SUPPLIER_SKU_STATUS_ENUM[value]?.desc || value;
const specText = (spec: Record<string, string> | undefined): string => {
  const values = Object.values(spec ?? {});
  return values.length ? values.join('/') : '—';
};

const relationColumns: TableColumnsType<SupplierSkuRow> = [
  {title: '商品名称（快照）', dataIndex: 'skuNameSnapshot', width: 220},
  {title: '规格', dataIndex: 'spec', width: 160},
  {title: '规格编码（快照）', dataIndex: 'skuCodeSnapshot', width: 170},
  {title: '采购单位', dataIndex: 'purchaseUnit', width: 100},
  {title: '参考价', dataIndex: 'referencePrice', width: 120, align: 'right'},
  {title: '采购员', dataIndex: 'purchaserName', width: 110},
  {title: '默认来源', dataIndex: 'defaultFlag', width: 110, align: 'center'},
  {title: '状态', dataIndex: 'status', width: 90, align: 'center'},
];

let requestId = 0;

async function load() {
  const request = ++requestId;
  const id = route.query.supplierId;
  if (typeof id !== 'string' || !/^\d+$/.test(id)) {
    supplier.value = undefined;
    relations.value = [];
    error.value = '供应商链接缺少有效编号';
    return;
  }
  loading.value = true;
  error.value = '';
  supplier.value = undefined;
  relations.value = [];
  try {
    const response = await supplierApi.detail(id);
    if (request !== requestId) {
      return;
    }
    supplier.value = response.data;
    // 关联关系是次要信息：单独取，失败不影响主体信息展示。
    try {
      const relationResponse = await supplierSkuApi.listBySupplierId(id);
      if (request === requestId) relations.value = relationResponse.data ?? [];
    } catch {
      relations.value = [];
    }
  } catch (e) {
    if (request === requestId) error.value = supplierError(e);
  } finally {
    if (request === requestId) loading.value = false;
  }
}

watch(() => route.query.supplierId, load, {immediate: true});
</script>

<style scoped>
.amount {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
