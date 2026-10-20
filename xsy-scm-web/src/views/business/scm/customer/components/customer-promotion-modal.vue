<template>
  <a-modal :open="open" title="客户推广二维码" :footer="null" width="560px" @cancel="close">
    <div class="promo">
      <div class="promo__head">
        <span class="promo__name">{{ customerName || '—' }}</span>
        <a-button type="primary" size="small" :loading="creating" @click="create">生成推广码</a-button>
      </div>

      <a-alert v-if="error" type="error" show-icon :message="error"/>

      <div v-if="active" class="promo__code">
        <img v-if="qrDataUrl" class="promo__qr" :src="qrDataUrl" alt="客户推广二维码"/>
        <div class="promo__meta">
          <a-typography-text class="promo__url" copyable>{{ shareUrl }}</a-typography-text>
          <span class="promo__hint">归属业务员：{{ active.sellerName || '未分配' }}</span>
        </div>
      </div>
      <a-empty v-else description="该客户还没有推广码"/>

      <a-table
          v-if="rows.length"
          :data-source="rows"
          :columns="columns"
          :pagination="false"
          row-key="id"
          size="small">
        <template #bodyCell="{column, record}">
          <template v-if="column.dataIndex === 'status'">
            <a-tag :color="record.revokedAt ? 'default' : 'green'">{{ record.revokedAt ? '已撤销' : '有效' }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-button type="link" size="small" @click="showScans(record)">扫码记录</a-button>
            <a-button v-if="!record.revokedAt" type="link" size="small" danger @click="revoke(record)">撤销</a-button>
          </template>
        </template>
      </a-table>

      <div v-if="scanRows.length" class="promo__scans">
        <div class="promo__scans-title">最近扫码</div>
        <div v-for="scan in scanRows" :key="scan.id" class="promo__scan">
          <span>{{ formatDateTime(scan.scannedAt) }}</span>
          <span>{{ scan.sellerName || '未分配业务员' }}</span>
          <span>{{ scan.clientIp || '—' }}</span>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
  import {computed, ref} from 'vue';
  import QRCode from 'qrcode';
  import {message} from 'ant-design-vue';
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
  const error = ref('');
  const customerId = ref<Id>();
  const customerName = ref('');
  const rows = ref<CustomerPromotion[]>([]);
  const scanRows = ref<CustomerPromotionScan[]>([]);
  const qrDataUrl = ref('');

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
    scanRows.value = [];
    qrDataUrl.value = '';
    await load();
  }

  function close() {
    open.value = false;
  }

  async function load() {
    if (customerId.value == null) {
      return;
    }
    try {
      const response = await customerPromotionApi.list(customerId.value);
      rows.value = response.data ?? [];
      if (active.value) {
        qrDataUrl.value = await QRCode.toDataURL(shareUrl.value, {width: 200, margin: 1});
      } else {
        qrDataUrl.value = '';
      }
    } catch {
      // 列表失败只走全局提示
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
    await customerPromotionApi.revoke(record.id);
    message.success('已撤销');
    await load();
  }

  async function showScans(record: CustomerPromotion) {
    const response = await customerPromotionApi.scans(record.id, 20);
    scanRows.value = response.data ?? [];
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
