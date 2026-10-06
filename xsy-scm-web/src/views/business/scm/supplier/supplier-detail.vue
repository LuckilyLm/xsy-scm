<!--
  供应商详情（独立隐藏路由，可深链）。
  按业务 Section 分区：概览 / 联系方式 / 地址与地图 / 采购与账期 / 关联商品 / 系统信息。

  列表 VO 不含 `address` / `remark`，详情页是唯一能看到完整信息的地方；
  「已关联商品」展示的是**冻结快照**（`skuNameSnapshot` 等）—— 商品后来改名不会改写
  历史关联的展示口径。
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
      <div class="detail-doc-title">{{ supplier.name }}</div>

      <!-- 1. 概览 -->
      <section class="detail-section">
        <h3>概览</h3>
        <a-descriptions bordered size="small" :column="{ xs: 1, sm: 2, lg: 3 }">
          <a-descriptions-item label="供应商编码">{{ supplier.supplierCode }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="supplier.status === 'ENABLED' ? 'green' : 'default'">{{ statusText(supplier.status) }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="关联商品数">{{ supplier.skuCount ?? 0 }}</a-descriptions-item>
        </a-descriptions>

        <!-- 2. 联系方式 -->
        <h3 class="detail-section--nested">联系方式</h3>
        <a-descriptions bordered size="small" :column="{ xs: 1, sm: 2, lg: 3 }">
          <a-descriptions-item label="联系人">{{ supplier.contactName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="联系电话">{{ supplier.contactPhone || '—' }}</a-descriptions-item>
        </a-descriptions>

        <!-- 3. 地址与地图 -->
        <h3 class="detail-section--nested">地址与地图</h3>
        <a-descriptions bordered size="small" :column="{ xs: 1, sm: 2, lg: 3 }">
          <a-descriptions-item label="地址" :span="3">{{ supplier.address || '—' }}</a-descriptions-item>
          <a-descriptions-item label="地图定位" :span="3">
            <span v-if="isLocated(supplier)">
              {{ supplier.longitude }}，{{ supplier.latitude }}（{{ supplier.geomCrs }}）
            </span>
            <span v-else class="hint">未采集点位；点位用于地图分布与供应商位置查询</span>
          </a-descriptions-item>
        </a-descriptions>

        <!-- 4. 采购与账期 -->
        <h3 class="detail-section--nested">采购与账期</h3>
        <a-descriptions bordered size="small" :column="{ xs: 1, sm: 2, lg: 3 }">
          <a-descriptions-item label="付款账期">{{ supplier.paymentPeriodDays ?? 0 }} 天</a-descriptions-item>
        </a-descriptions>

        <!-- 6. 系统信息：编码与时间放最后，不占核心区域 -->
        <h3 class="detail-section--nested">系统信息</h3>
        <a-descriptions bordered size="small" :column="{ xs: 1, sm: 2, lg: 3 }">
          <a-descriptions-item label="创建时间">{{ datetime(supplier.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="更新时间">{{ datetime(supplier.updatedAt) }}</a-descriptions-item>
          <a-descriptions-item label="备注" :span="3">{{ supplier.remark || '—' }}</a-descriptions-item>
        </a-descriptions>

        <!-- 5. 关联商品 -->
        <h3 class="detail-section--nested">已关联商品（快照）</h3>
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
            <span v-else-if="column.dataIndex === 'referencePrice'" class="scm-money">{{
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
      </section>
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
import {isLocated} from '/@/components/business/scm/map/types';

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
  {title: '商品规格', dataIndex: 'spec', width: 160},
  {title: '商品规格编码（快照）', dataIndex: 'skuCodeSnapshot', width: 170},
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
/* 文档标题：比描述列表更突出（「核心业务信息优先」） */
.detail-doc-title {
  color: var(--scm-text);
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
}

.detail-section h3 {
  margin: 0 0 12px;
  font-weight: 600;
}

/* 同一段里的后续小标题（概览段串起 联系/地址/采购/系统信息/关联商品） */
.detail-section--nested {
  margin-top: 20px;
}

.hint {
  color: var(--scm-text-secondary);
}
</style>
