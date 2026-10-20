<template>
  <a-modal :open="open" title="采购单二维码分享" :footer="null" width="520px" @cancel="close">
    <div class="share">
      <div class="share__head">
        <span class="share__order">{{ orderNo || '—' }}</span>
        <a-space>
          <a-select v-model:value="validDays" size="small" style="width: 96px" :options="VALID_DAY_OPTIONS"/>
          <a-button type="primary" size="small" :loading="creating" @click="create">生成分享链接</a-button>
        </a-space>
      </div>

      <a-alert v-if="error" type="error" show-icon :message="error"/>

      <div v-if="currentUrl" class="share__code">
        <img v-if="qrDataUrl" class="share__qr" :src="qrDataUrl" alt="采购单分享二维码"/>
        <div class="share__meta">
          <a-typography-text class="share__url" copyable>{{ currentUrl }}</a-typography-text>
          <span class="share__expire">有效期至 {{ formatDateTime(currentExpiresAt) }}</span>
        </div>
      </div>

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
            <a-button v-if="!record.revokedAt" type="link" size="small" danger
                      v-privilege="'scm:purchase:share:revoke'" @click="revoke(record)">撤销</a-button>
          </template>
        </template>
      </a-table>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
  import {reactive, ref} from 'vue';
  import QRCode from 'qrcode';
  import {purchaseShareApi, type PurchaseShare} from '/@/api/business/scm/purchase-share-api';
  import {message} from 'ant-design-vue';
  import type {Id} from '/@/views/business/scm/inventory/inventory-types';

  const VALID_DAY_OPTIONS = [
    {value: 1, label: '1 天'},
    {value: 7, label: '7 天'},
    {value: 30, label: '30 天'},
  ];

  const columns = [
    {title: '前缀', dataIndex: 'tokenPrefix', width: 90},
    {title: '状态', dataIndex: 'status', width: 80, align: 'center' as const},
    {title: '生成人', dataIndex: 'createdBy', width: 100},
    {title: '有效期至', dataIndex: 'expiresAt', width: 160},
    {title: '操作', dataIndex: 'action', width: 70, align: 'center' as const},
  ];

  const open = ref(false);
  const creating = ref(false);
  const error = ref('');
  const validDays = ref(7);
  const orderId = ref<Id>();
  const orderNo = ref('');
  const rows = ref<PurchaseShare[]>([]);
  const currentUrl = ref('');
  const currentExpiresAt = ref('');
  const qrDataUrl = ref('');
  const state = reactive({loaded: false});

  function reset() {
    error.value = '';
    rows.value = [];
    currentUrl.value = '';
    currentExpiresAt.value = '';
    qrDataUrl.value = '';
    state.loaded = false;
  }

  async function openFor(id: Id, no?: string | null) {
    orderId.value = id;
    orderNo.value = no ?? '';
    open.value = true;
    reset();
    await load();
  }

  function close() {
    open.value = false;
  }

  async function load() {
    if (orderId.value == null) {
      return;
    }
    try {
      const response = await purchaseShareApi.list(orderId.value);
      rows.value = response.data ?? [];
    } catch {
      // 列表失败只走全局提示，弹窗内不重复报错
    }
  }

  async function create() {
    if (orderId.value == null) {
      return;
    }
    error.value = '';
    creating.value = true;
    try {
      const response = await purchaseShareApi.create(orderId.value, validDays.value);
      const share = response.data;
      const url = shareUrl(String(share?.token ?? ''));
      currentUrl.value = url;
      currentExpiresAt.value = String(share?.expiresAt ?? '');
      qrDataUrl.value = await QRCode.toDataURL(url, {width: 220, margin: 1});
      await load();
    } catch (e) {
      error.value = (e as Error)?.message || '生成分享链接失败';
    } finally {
      creating.value = false;
    }
  }

  async function revoke(record: PurchaseShare) {
    await purchaseShareApi.revoke(record.id);
    message.success('已撤销');
    if (currentUrl.value) {
      currentUrl.value = '';
      qrDataUrl.value = '';
    }
    await load();
  }

  /** 分享页是哈希路由，必须带上前端自身的 origin 与部署路径。 */
  function shareUrl(token: string) {
    const base = `${window.location.origin}${window.location.pathname.replace(/\/$/, '')}`;
    return `${base}/#/purchase-share/${token}`;
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
  .share__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .share__order {
    font-weight: 600;
  }

  .share__code {
    display: flex;
    gap: 16px;
    align-items: center;
    padding: 12px;
    margin-bottom: 12px;
    border: 1px solid var(--scm-border, #f0f0f0);
    border-radius: 4px;
  }

  .share__qr {
    width: 110px;
    height: 110px;
  }

  .share__meta {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;
  }

  .share__url {
    word-break: break-all;
  }

  .share__expire {
    color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  }
</style>
