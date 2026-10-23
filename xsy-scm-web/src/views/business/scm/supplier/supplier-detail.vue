<!-- 供应商详情按 SCM 资料模板组织：页头、联系与地址、采购与账期、已关联商品快照和系统信息。已关联商品保留下单时的名称信息。 -->
<template>
  <a-card size="small" :bordered="false" :loading="loading">
    <header class="scm-detail-header">
      <div class="scm-detail-header__main">
        <a-button type="link" class="scm-detail-header__back" @click="router.push('/supplier/supplier-list')">
          <ArrowLeftOutlined />
          返回供应商列表
        </a-button>
        <div class="scm-detail-header__title-row">
          <h1 class="scm-detail-header__title">{{ supplier?.name || '供应商详情' }}</h1>
          <ScmStatusTag v-if="supplier" :tone="supplier.status === 'ENABLED' ? 'success' : 'neutral'" :label="statusText(supplier.status)" />
        </div>
        <p v-if="supplier" class="scm-detail-header__meta">
          <span class="scm-mono">{{ supplier.supplierCode }}</span>
        </p>
      </div>
    </header>

    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action>
        <a-button size="small" @click="load">重新加载</a-button>
      </template>
    </a-alert>
    <template v-else-if="supplier">
      <div class="scm-detail-read">
        <div class="scm-detail-grid">
          <section class="scm-detail-card">
            <h3 class="scm-detail-card__title">联系与地址</h3>
            <dl class="scm-field-list">
              <div class="scm-field">
                <dt class="scm-field__label">联系人</dt>
                <dd class="scm-field__value">{{ supplier.contactName || '—' }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">联系电话</dt>
                <dd class="scm-field__value">{{ supplier.contactPhone || '—' }}</dd>
              </div>
              <div class="scm-field scm-field--wide">
                <dt class="scm-field__label">地址</dt>
                <dd class="scm-field__value">{{ supplier.address || '—' }}</dd>
              </div>
              <div class="scm-field scm-field--wide">
                <dt class="scm-field__label">地图定位</dt>
                <dd class="scm-field__value">
                  <a-tooltip v-if="isLocated(supplier)" :title="`经度：${supplier.longitude}，纬度：${supplier.latitude}`">
                    <span>已设置地图位置</span>
                  </a-tooltip>
                  <span v-else>尚未设置地图位置</span>
                </dd>
              </div>
            </dl>
          </section>

          <section class="scm-detail-card">
            <h3 class="scm-detail-card__title">采购与账期</h3>
            <dl class="scm-field-list">
              <div class="scm-field">
                <dt class="scm-field__label">付款账期</dt>
                <dd class="scm-field__value">{{ supplier.paymentPeriodDays ?? 0 }} 天</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">关联商品数</dt>
                <dd class="scm-field__value">{{ supplier.skuCount ?? 0 }}</dd>
              </div>
            </dl>
          </section>
        </div>

        <section class="scm-detail-card scm-detail-block">
          <h3 class="scm-detail-card__title">已关联商品</h3>
          <a-table
            :data-source="relations"
            :columns="relationColumns"
            row-key="id"
            size="small"
            bordered
            :pagination="false"
            :scroll="{x: scmColumnsWidth(relationColumns)}"
          >
            <template #bodyCell="{column, record}">
              <span v-if="column.dataIndex === 'skuNameSnapshot'">{{ record.skuNameSnapshot }}</span>
              <span v-else-if="column.dataIndex === 'spec'">{{ specText(record.specValuesSnapshot) }}</span>
              <span v-else-if="column.dataIndex === 'referencePrice'" class="scm-money">{{ record.referencePrice ?? '—' }}</span>
              <span v-else-if="column.dataIndex === 'purchaserName'">{{ record.purchaserName || '—' }}</span>
              <a-tag v-else-if="column.dataIndex === 'defaultFlag'" :color="record.defaultFlag ? 'blue' : 'default'">
                {{ record.defaultFlag ? '优先采购' : '—' }}
              </a-tag>
              <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 'ENABLED' ? 'green' : 'default'">
                {{ skuStatusText(record.status) }}
              </a-tag>
            </template>
          </a-table>
          <a-empty v-if="!loading && relations.length === 0" description="尚未关联任何商品" />
        </section>

        <!-- 系统信息：弱化卡片，编码与时间不占核心区域 -->
        <section class="scm-detail-card scm-detail-card--muted scm-detail-block">
          <h3 class="scm-detail-card__title">系统信息</h3>
          <dl class="scm-field-list scm-field-list--3">
            <div class="scm-field">
              <dt class="scm-field__label">供应商编码</dt>
              <dd class="scm-field__value scm-mono">{{ supplier.supplierCode }}</dd>
            </div>
            <div class="scm-field">
              <dt class="scm-field__label">创建时间</dt>
              <dd class="scm-field__value">{{ datetime(supplier.createdAt) }}</dd>
            </div>
            <div class="scm-field">
              <dt class="scm-field__label">更新时间</dt>
              <dd class="scm-field__value">{{ datetime(supplier.updatedAt) }}</dd>
            </div>
            <div class="scm-field scm-field--wide">
              <dt class="scm-field__label">备注</dt>
              <dd class="scm-field__value">{{ supplier.remark || '—' }}</dd>
            </div>
          </dl>
        </section>
      </div>
    </template>
  </a-card>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {ArrowLeftOutlined} from '@ant-design/icons-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {supplierApi} from '/@/api/business/scm/supplier-api';
import {supplierSkuApi} from '/@/api/business/scm/supplier-sku-api';
import type {EnableStatus, SupplierDetail, SupplierSkuRow} from '/@/types/business/scm/supplier';
import {SUPPLIER_SKU_STATUS_ENUM, SUPPLIER_STATUS_ENUM} from '/@/constants/business/scm/supplier-const';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
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
  {title: '商品名称', dataIndex: 'skuNameSnapshot', width: 220},
  {title: '商品规格', dataIndex: 'spec', width: 160},
  {title: '规格编码', dataIndex: 'skuCodeSnapshot', width: 170},
  {title: '采购单位', dataIndex: 'purchaseUnit', width: 76},
  {title: '参考价', dataIndex: 'referencePrice', width: 120},
  {title: '采购员', dataIndex: 'purchaserName', width: 110},
  {title: '优先采购', dataIndex: 'defaultFlag', width: 110, align: 'center'},
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
    // 加载失败由页内 Alert 承担，不再让全局 toast 重复说一遍
    const response = await supplierApi.detail(id, {suppressGlobalErrorMessage: true});
    if (request !== requestId) {
      return;
    }
    supplier.value = response.data;
    // 关联关系是次要信息：单独取，失败不影响主体信息展示，也不弹全局提示。
    try {
      const relationResponse = await supplierSkuApi.listBySupplierId(id, {suppressGlobalErrorMessage: true});
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
