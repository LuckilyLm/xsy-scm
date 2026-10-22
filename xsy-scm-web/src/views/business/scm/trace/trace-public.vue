<template>
  <div class="trace-page">
    <div class="trace-page__card">
      <a-spin :spinning="loading">
        <a-alert v-if="error" type="error" show-icon :message="error" class="trace-page__alert">
          <template #action>
            <a-button size="small" @click="load">重试</a-button>
          </template>
        </a-alert>

        <template v-if="view">
          <div class="trace-page__status" :class="voided ? 'trace-page__status--void' : 'trace-page__status--active'">
            {{ voided ? '该码已作废，请勿食用' : '溯源信息已核验' }}
          </div>

          <h1 class="trace-page__title">{{ view.productName || '未知商品' }}</h1>
          <div class="trace-page__spec">{{ view.skuName || '—' }}</div>

          <a-descriptions :column="1" size="small" bordered class="trace-page__info">
            <a-descriptions-item label="溯源码">
              <span class="trace-page__code">{{ view.unitCode }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="包装">{{ packageLabel }}</a-descriptions-item>
            <a-descriptions-item label="数量">{{ quantityText }}</a-descriptions-item>
            <a-descriptions-item label="净重">{{ weightText }}</a-descriptions-item>
            <a-descriptions-item label="批次">{{ view.lotNo || '—' }}</a-descriptions-item>
            <a-descriptions-item v-if="view.sourceLotNo" label="生产批号">{{ view.sourceLotNo }}</a-descriptions-item>
            <a-descriptions-item label="供应商">{{ view.supplierName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="收货时间">{{ receivedText }}</a-descriptions-item>
            <a-descriptions-item label="配送对象">{{ view.customerName || '待分配' }}</a-descriptions-item>
            <a-descriptions-item label="订单号">{{ view.orderNo || '—' }}</a-descriptions-item>
          </a-descriptions>

          <div v-if="attachments.length" class="trace-page__files">
            <div class="trace-page__files-title">资质与检测报告</div>
            <a
                v-for="file in attachments"
                :key="file.fileKey"
                class="trace-page__file"
                :href="fileUrl(file.fileKey)"
                target="_blank"
                rel="noopener">
              {{ file.fileName }}
            </a>
          </div>

          <p class="trace-page__foot">本页由系统按实际称重与收货记录自动生成。</p>
        </template>

        <a-empty v-else-if="!loading && !error" description="未找到该溯源码，请确认标签完整"/>
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
  import {computed, onMounted, ref} from 'vue';
  import {useRoute} from 'vue-router';
  import {tracePublicApi} from '/@/api/business/scm/trace-public-api';
  import type {TracePublicView} from '/@/api/business/scm/trace-public-api';

  const route = useRoute();
  const token = String(route.params.token ?? '');

  const loading = ref(false);
  const error = ref('');
  const view = ref<TracePublicView | null>(null);

  const voided = computed(() => view.value?.status === 'VOID');
  const attachments = computed(() => view.value?.attachments ?? []);

  const packageLabel = computed(() => {
    switch (view.value?.packageType) {
      case 'BAG':
        return '袋';
      case 'BOX':
        return '箱';
      case 'CRATE':
        return '筐';
      default:
        return '—';
    }
  });

  const quantityText = computed(() => {
    const item = view.value;
    if (!item?.quantity) return '—';
    return `${item.quantity}${item.unit || ''}`;
  });

  const weightText = computed(() => {
    const item = view.value;
    if (!item?.netWeight) return '待称重';
    return `${item.netWeight}${item.weightUnit || ''}`;
  });

  /** 时间只展示到分钟：扫码的人看的是「哪天到的货」，秒级精度没有意义还会挤满一行。 */
  function formatTime(value?: string): string {
    if (!value) return '—';
    return value.replace('T', ' ').slice(0, 16);
  }

  const receivedText = computed(() => formatTime(view.value?.receivedAt));

  function fileUrl(fileKey: string): string {
    return tracePublicApi.fileUrl(token, fileKey);
  }

  async function load() {
    if (!token) {
      error.value = '链接缺少溯源码，请重新扫码';
      return;
    }
    loading.value = true;
    error.value = '';
    try {
      const result = await tracePublicApi.detail(token);
      view.value = result.data ?? null;
    } catch (e) {
      view.value = null;
      error.value = e instanceof Error ? e.message : '加载失败，请重试';
    } finally {
      loading.value = false;
    }
  }

  onMounted(load);
</script>

<style scoped>
  .trace-page {
    min-height: 100vh;
    padding: 16px;
    background: #f5f6f8;
  }

  .trace-page__card {
    max-width: 560px;
    margin: 0 auto;
    padding: 16px;
    background: #fff;
    border-radius: 8px;
  }

  .trace-page__alert {
    margin-bottom: 12px;
  }

  .trace-page__status {
    margin-bottom: 12px;
    padding: 8px 12px;
    border-radius: 6px;
    font-size: 13px;
  }

  .trace-page__status--active {
    color: #12833c;
    background: #eaf7ef;
  }

  .trace-page__status--void {
    color: #d92d20;
    background: #fdecea;
  }

  .trace-page__title {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: #1f2329;
  }

  .trace-page__spec {
    margin: 4px 0 12px;
    color: #667085;
    font-size: 13px;
  }

  .trace-page__info {
    margin-bottom: 16px;
  }

  .trace-page__code {
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  }

  .trace-page__files-title {
    margin-bottom: 8px;
    font-weight: 600;
  }

  .trace-page__file {
    display: block;
    padding: 8px 0;
    border-top: 1px solid #f0f0f0;
    color: #1668dc;
    word-break: break-all;
  }

  .trace-page__foot {
    margin: 16px 0 0;
    color: #98a2b3;
    font-size: 12px;
  }
</style>
