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
            {{ voided ? '该码已作废，请勿食用' : '溯源信息查询' }}
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

          <section class="trace-page__reports" aria-label="检测报告">
            <h2 class="trace-page__files-title">批次检测报告</h2>
            <p v-if="!reports.length">{{ voided ? '包装码已作废，不提供检测报告' : '暂无检测报告' }}</p>
            <article v-for="report in reports" :key="report.reportNo" class="trace-page__report">
              <strong>{{ report.reportName }}</strong>
              <p class="trace-page__code">{{ report.reportNo }}</p>
              <ScmStatusTag :tone="inspectionPublicResult(report).tone" :label="inspectionPublicResult(report).label" />
              <p>检测日期：{{ report.inspectionDate }}</p>
              <p>检测机构：{{ report.inspectionOrg }}</p>
              <p>报告状态：{{ inspectionStatusLabel(report.status) }}{{ report.expired ? ' · 已过期' : '' }}</p>
              <p v-if="report.expiresAt">有效期至：{{ formatTime(report.expiresAt) }}（北京时间）</p>
              <p v-if="report.expired">报告已过期，不作为当前检测合格依据。</p>
              <p v-if="report.expired">原报告结论：{{ inspectionConclusionLabel(report.conclusion) }}</p>
              <a
                v-for="file in report.attachments"
                :key="file.fileKey"
                class="trace-page__file"
                :href="fileUrl(file.fileKey)"
                target="_blank"
                rel="noopener"
                >{{ file.fileName }}</a
              >
            </article>
          </section>

          <div v-if="attachments.length && !voided" class="trace-page__files">
            <div class="trace-page__files-title">长期资质证明</div>
            <a v-for="file in attachments" :key="file.fileKey" class="trace-page__file" :href="fileUrl(file.fileKey)" target="_blank" rel="noopener">
              {{ file.fileName }}
            </a>
          </div>

          <p class="trace-page__foot">本页由系统按实际称重与收货记录自动生成。</p>
        </template>

        <a-empty v-else-if="!loading && !error" description="未找到该溯源码，请确认标签完整" />
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue';
import {useRoute} from 'vue-router';
import {tracePublicApi} from '/@/api/business/scm/trace-public-api';
import type {TracePublicView} from '/@/api/business/scm/trace-public-api';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {inspectionPublicResult, inspectionStatusLabel, inspectionConclusionLabel} from '/@/constants/business/scm/trace-const';
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
dayjs.extend(utc);

const route = useRoute();
const token = computed(() => String(route.params.token ?? ''));
let requestId = 0;

const loading = ref(false);
const error = ref('');
const view = ref<TracePublicView | null>(null);

const voided = computed(() => view.value?.status === 'VOID');
const attachments = computed(() => view.value?.attachments ?? []);
const reports = computed(() => (voided.value ? [] : view.value?.inspectionReports ?? []));

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
  return dayjs(value).utcOffset(8).format('YYYY-MM-DD HH:mm');
}

const receivedText = computed(() => formatTime(view.value?.receivedAt));

function fileUrl(fileKey: string): string {
  return tracePublicApi.fileUrl(token.value, fileKey);
}

/** axios 以响应对象拒绝业务错误，不是 Error；优先保留后端可读提示。 */
function errorText(e: unknown): string {
  const response = e as {
    data?: {msg?: string};
    response?: {data?: {msg?: string}};
    message?: string;
  };
  return response?.data?.msg || response?.response?.data?.msg || response?.message || '加载失败，请重试';
}

async function load() {
  const current = ++requestId;
  if (!token.value) {
    view.value = null;
    loading.value = false;
    error.value = '链接缺少溯源码，请重新扫码';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const result = await tracePublicApi.detail(token.value);
    if (current === requestId) view.value = result.data ?? null;
  } catch (e) {
    if (current === requestId) {
      view.value = null;
      error.value = errorText(e);
    }
  } finally {
    if (current === requestId) loading.value = false;
  }
}

function refreshVisible() {
  if (document.visibilityState === 'visible') void load();
}
watch(
  token,
  () => {
    view.value = null;
    void load();
  },
  {immediate: true}
);
onMounted(() => document.addEventListener('visibilitychange', refreshVisible));
onBeforeUnmount(() => {
  requestId++;
  document.removeEventListener('visibilitychange', refreshVisible);
});
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
  color: #344054;
  background: #f2f4f7;
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
  font-size: 14px;
}

.trace-page__report {
  padding: 12px 0;
  border-bottom: 1px solid #e5e7eb;
  overflow-wrap: anywhere;
}
.trace-page__report p {
  margin: 8px 0;
}
.trace-page__reports {
  margin-bottom: 20px;
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
