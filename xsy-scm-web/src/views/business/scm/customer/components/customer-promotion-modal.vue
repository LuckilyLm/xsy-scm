<template>
  <a-modal :open="open" title="客户推广二维码" :footer="null" width="560px" @cancel="close">
    <a-spin :spinning="loading">
      <div class="promo">
        <div class="promo__head">
          <span class="promo__name">{{ customerName || '—' }}</span>
          <a-button type="primary" size="small" :loading="creating" @click="create">生成推广码</a-button>
        </div>

        <a-alert v-if="error" type="error" show-icon :message="error">
          <template #action>
            <a-button size="small" :loading="loading" @click="load">重试</a-button>
          </template>
        </a-alert>

        <div v-if="active" class="promo__code">
          <img v-if="qrDataUrl" class="promo__qr" :src="qrDataUrl" alt="客户推广二维码" />
          <div class="promo__meta">
            <a-typography-text class="promo__url" copyable>{{ shareUrl }}</a-typography-text>
            <span class="promo__hint">归属业务员：{{ active.sellerName || '未分配' }}</span>
          </div>
        </div>
        <a-empty v-else-if="!loading && !error" description="该客户还没有推广码" />

        <a-table v-if="rows.length" :data-source="rows" :columns="columns" :pagination="false" row-key="id" size="small">
          <template #bodyCell="{column, record}">
            <template v-if="column.dataIndex === 'status'">
              <a-tag :color="record.revokedAt ? 'default' : 'green'">{{ record.revokedAt ? '已撤销' : '有效' }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <a-button type="link" size="small" @click="showScans(record)">扫码记录</a-button>
              <a-button
                v-if="!record.revokedAt"
                type="link"
                size="small"
                danger
                :loading="revokingId === record.id"
                :disabled="revokingId != null"
                @click="revoke(record)"
                >撤销</a-button
              >
            </template>
          </template>
        </a-table>

        <a-alert v-if="scanError" class="promo__scan-error" type="error" show-icon :message="scanError" />
        <div v-if="scanRows.length" class="promo__scans">
          <div class="promo__scans-title">最近扫码</div>
          <div v-for="scan in scanRows" :key="scan.id" class="promo__scan">
            <span>{{ formatDateTime(scan.scannedAt) }}</span>
            <span>{{ scan.sellerName || '未分配业务员' }}</span>
            <span>{{ scan.clientIp || '—' }}</span>
          </div>
        </div>
      </div>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import QRCode from 'qrcode';
import {message, Modal} from 'ant-design-vue';
import {customerPromotionApi, type CustomerPromotion, type CustomerPromotionScan} from '/@/api/business/scm/customer-promotion-api';
import type {Id} from '/@/views/business/scm/inventory/inventory-types';

const columns = [
  {title: '推广码', dataIndex: 'code', width: 130},
  {title: '状态', dataIndex: 'status', width: 80, align: 'center' as const},
  {title: '扫码次数', dataIndex: 'scanCount', width: 90},
  {title: '生成人', dataIndex: 'createdBy', width: 100},
  {title: '操作', dataIndex: 'action', width: 150, align: 'center' as const},
];

const open = ref(false);
const creating = ref(false);
const loading = ref(false);
const revokingId = ref<Id>();
const error = ref('');
let requestId = 0;
const customerId = ref<Id>();
const customerName = ref('');
const rows = ref<CustomerPromotion[]>([]);
const scanRows = ref<CustomerPromotionScan[]>([]);
const scanError = ref('');
const qrDataUrl = ref('');
let scanRequestId = 0;

const active = computed(() => rows.value.find((row) => !row.revokedAt));
const shareUrl = computed(() => {
  const base = `${window.location.origin}${window.location.pathname.replace(/\/$/, '')}`;
  return `${base}/#/promo/${active.value?.code ?? ''}`;
});

async function openFor(id: Id, name?: string | null) {
  customerId.value = id;
  customerName.value = name ?? '';
  open.value = true;
  error.value = '';
  rows.value = [];
  scanRows.value = [];
  scanError.value = '';
  qrDataUrl.value = '';
  await load();
}

function close() {
  open.value = false;
  scanRequestId += 1;
  scanRows.value = [];
  scanError.value = '';
}

async function load() {
  if (customerId.value == null) {
    return;
  }
  const current = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const response = await customerPromotionApi.list(customerId.value);
    if (current !== requestId) return;
    rows.value = response.data ?? [];
    qrDataUrl.value = active.value ? await QRCode.toDataURL(shareUrl.value, {width: 200, margin: 1}) : '';
  } catch (e) {
    if (current === requestId) {
      rows.value = [];
      qrDataUrl.value = '';
      error.value = (e as Error)?.message || '推广码加载失败';
    }
  } finally {
    if (current === requestId) loading.value = false;
  }
}

async function create() {
  if (customerId.value == null) {
    return;
  }
  error.value = '';
  creating.value = true;
  try {
    await customerPromotionApi.create(customerId.value);
    await load();
  } catch (e) {
    error.value = (e as Error)?.message || '生成推广码失败';
  } finally {
    creating.value = false;
  }
}

async function revoke(record: CustomerPromotion) {
  try {
    await Modal.confirm({
      title: '撤销推广码',
      content: '撤销后已印刷或转发出去的推广二维码将立即失效。',
      okText: '确认撤销',
      okType: 'danger',
      cancelText: '取消',
    });
  } catch {
    return;
  }
  revokingId.value = record.id;
  try {
    await customerPromotionApi.revoke(record.id);
    message.success('已撤销');
    await load();
  } catch {
    // 失败由全局提示
  } finally {
    revokingId.value = undefined;
  }
}

async function showScans(record: CustomerPromotion) {
  const current = ++scanRequestId;
  scanError.value = '';
  try {
    const response = await customerPromotionApi.scans(record.id, 20);
    if (current !== scanRequestId) return;
    scanRows.value = response.data ?? [];
  } catch (e) {
    if (current === scanRequestId) {
      scanRows.value = [];
      scanError.value = (e as Error)?.message || '扫码记录加载失败';
    }
  }
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '—';
  }
  return value.replace('T', ' ').slice(0, 16);
}

defineExpose({openFor});
</script>

<style scoped>
.promo__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.promo__name {
  font-weight: 600;
}

.promo__code {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid var(--scm-border, #f0f0f0);
  border-radius: 4px;
}

.promo__qr {
  width: 100px;
  height: 100px;
}

.promo__meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.promo__url {
  word-break: break-all;
}

.promo__hint {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.promo__scan-error {
  margin-top: 12px;
}

.promo__scans {
  margin-top: 12px;
}

.promo__scans-title {
  margin-bottom: 6px;
  font-weight: 600;
}

.promo__scan {
  display: flex;
  gap: 12px;
  padding: 4px 0;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}
</style>
