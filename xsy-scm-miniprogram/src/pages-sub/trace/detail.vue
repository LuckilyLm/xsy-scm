<template>
  <view class="trace" :style="themeStyle">
    <view v-if="loading && !view" class="trace__state">
      <xsy-skeleton :rows="6" />
    </view>

    <view v-else-if="!view" class="trace__state">
      <xsy-empty :title="error || '未找到该溯源码'" :desc="error ? '' : '请确认标签完整'" />
      <text v-if="error" class="trace__retry" @click="load">重试</text>
    </view>

    <template v-else>
      <view class="trace__banner" :class="voided ? 'trace__banner--void' : 'trace__banner--active'">
        {{ voided ? '该码已作废，请勿食用' : '溯源信息查询' }}
      </view>

      <view class="trace__card">
        <text class="trace__name">{{ view.productName || '未知商品' }}</text>
        <text class="trace__spec">{{ view.skuName || '—' }}</text>
        <view class="trace__hero">
          <text class="trace__hero-label">净重</text>
          <text class="trace__hero-value">{{ weightText }}</text>
        </view>
      </view>

      <view class="trace__card">
        <view class="trace__row"
          ><text>溯源码</text><text class="trace__mono">{{ view.unitCode }}</text></view
        >
        <view class="trace__row"
          ><text>包装</text><text>{{ packageLabel }}</text></view
        >
        <view class="trace__row"
          ><text>数量</text><text>{{ quantityText }}</text></view
        >
        <view class="trace__row"
          ><text>批次</text><text>{{ view.lotNo || '—' }}</text></view
        >
        <view v-if="view.sourceLotNo" class="trace__row"
          ><text>生产批号</text><text>{{ view.sourceLotNo }}</text></view
        >
        <view class="trace__row"
          ><text>供应商</text><text>{{ view.supplierName || '—' }}</text></view
        >
        <view class="trace__row"
          ><text>收货时间</text><text>{{ receivedText }}</text></view
        >
        <view class="trace__row"
          ><text>配送对象</text><text>{{ view.customerName || '待分配' }}</text></view
        >
        <view class="trace__row"
          ><text>订单号</text><text>{{ view.orderNo || '—' }}</text></view
        >
      </view>

      <view class="trace__card">
        <text class="trace__section">批次检测报告</text>
        <text v-if="!reports.length">{{ voided ? '包装码已作废，不提供检测报告' : '暂无检测报告' }}</text>
        <view v-for="report in reports" :key="report.reportNo" class="trace__report">
          <text class="trace__section">{{ report.reportName }}</text>
          <text class="trace__report-line trace__mono">{{ report.reportNo }}</text>
          <text class="trace__report-result"
            :class="'trace__report-result--' + inspectionPublicResult(report).tone">
            {{ inspectionPublicResult(report).label }}
          </text>
          <text class="trace__report-line">检测日期：{{ report.inspectionDate }}</text>
          <text class="trace__report-line">检测机构：{{ report.inspectionOrg }}</text>
          <text class="trace__report-line">报告状态：{{ inspectionStatusLabel(report.status) }}{{ report.expired ? ' · 已过期' : '' }}</text>
          <text v-if="report.expiresAt" class="trace__report-line">有效期至：{{ formatBeijingTime(report.expiresAt) }}（北京时间）</text>
          <text v-if="report.expired" class="trace__report-line">报告已过期，不作为当前检测合格依据。</text>
          <text v-if="report.expired" class="trace__report-line">原报告结论：{{ inspectionConclusionLabel(report.conclusion) }}</text>
          <view v-for="file in report.attachments" :key="file.fileKey" class="trace__file"
            hover-class="xsy-press--row--active" @click="openFile(file)">
            <text class="trace__file-name">{{ file.fileName }}</text>
            <uni-icons type="download" :size="16" :color="COLOR_PRIMARY" />
          </view>
        </view>
      </view>

      <view v-if="attachments.length && !voided" class="trace__card">
        <text class="trace__section">长期资质证明</text>
        <view
          v-for="file in attachments"
          :key="file.fileKey"
          class="trace__file xsy-press--row--active"
          hover-class="xsy-press--row--active"
          @click="openFile(file)"
        >
          <text class="trace__file-name">{{ file.fileName }}</text>
          <uni-icons type="download" :size="16" :color="COLOR_PRIMARY" />
        </view>
      </view>

      <text class="trace__note">本页由系统按实际称重与收货记录自动生成。</text>
    </template>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { onLoad, onShow, onUnload } from '@dcloudio/uni-app';
  import { tracePublicApi } from '@/api/trace/trace-public-api';
  import { useMallTheme } from '@/composables/use-mall-theme';
  import { inspectionStatusLabel, inspectionConclusionLabel, inspectionPublicResult } from '@/constants/trace-const';

  const COLOR_PRIMARY = '#12833c';

  const { themeStyle } = useMallTheme();

  const token = ref('');
  const loading = ref(false);
  const error = ref('');
  const view = ref(null);
  let requestId = 0;

  const voided = computed(() => view.value?.status === 'VOID');
  const attachments = computed(() => view.value?.attachments || []);
  const reports = computed(() => voided.value ? [] : view.value?.inspectionReports || []);

  const PACKAGE_LABEL = { BAG: '袋', BOX: '箱', CRATE: '筐' };
  const packageLabel = computed(() => PACKAGE_LABEL[view.value?.packageType] || '—');

  const quantityText = computed(() => {
    const item = view.value;
    if (!item?.quantity) return '—';
    return `${item.quantity}${item.unit || ''}`;
  });

  /** 没称过的码要显式说「待称重」，不能显示成 0。 */
  const weightText = computed(() => {
    const item = view.value;
    if (!item?.netWeight) return '待称重';
    return `${item.netWeight}${item.weightUnit || ''}`;
  });

  const receivedText = computed(() => {
    const value = view.value?.receivedAt;
    return value ? value.replace('T', ' ').slice(0, 16) : '—';
  });

  function formatBeijingTime(value) {
    const time = Date.parse(value);
    if (!Number.isFinite(time)) return '—';
    return new Date(time + 8 * 60 * 60 * 1000).toISOString().slice(0, 16).replace('T', ' ');
  }

  async function load() {
    const current = ++requestId;
    if (!token.value) {
      error.value = '链接缺少溯源码，请重新扫码';
      return;
    }
    loading.value = true;
    error.value = '';
    try {
      const result = await tracePublicApi.detail(token.value);
      if (current === requestId) view.value = result;
    } catch (e) {
      if (current === requestId) {
        view.value = null;
        error.value = e?.msg || e?.message || '加载失败，请重试';
      }
    } finally {
      if (current === requestId) loading.value = false;
    }
  }

  // 每次从受控下载接口读取，避免复用撤回前的远程文件地址。
  function openFile(file) {
    const url = tracePublicApi.fileUrl(token.value, file.fileKey);
    uni.showLoading({ title: '正在打开' });
    uni.downloadFile({
      url,
      success: (res) => {
        if (res.statusCode !== 200) {
          fileUnavailable();
          return;
        }
        if (/\.(jpg|jpeg|png|webp)$/i.test(file.fileName || '')) {
          uni.previewImage({
            urls: [res.tempFilePath],
            fail: fileUnavailable,
            complete: () => uni.hideLoading(),
          });
          return;
        }
        uni.openDocument({
          filePath: res.tempFilePath,
          showMenu: true,
          fail: fileUnavailable,
          complete: () => uni.hideLoading(),
        });
      },
      fail: fileUnavailable,
    });
  }

  function fileUnavailable() {
    uni.hideLoading();
    uni.showToast({ title: '文件暂时无法查看，请刷新报告后重试', icon: 'none' });
    load();
  }

  onLoad((options) => {
    token.value = options?.token || '';
  });
  onShow(() => { if (token.value) load(); });
  onUnload(() => { requestId++; });
</script>

<style lang="scss" scoped>
  .trace {
    @include viewport-min-height;
    padding: $space-4 $space-4 $space-8;
    box-sizing: border-box;
    background: $color-bg-page;
  }

  .trace__state {
    padding: 240rpx $space-4 0;
    text-align: center;
    color: $color-text-secondary;
  }

  .trace__retry {
    display: block;
    margin-top: $space-4;
    color: var(--mall-primary-ink, #12833c);
  }

  .trace__banner {
    margin-bottom: $space-3;
    padding: $space-3 $space-4;
    border-radius: $radius-md;
    font-size: $font-size-sm;
  }

  .trace__banner--active {
    color: $color-text-secondary;
    background: $color-bg-card;
  }

  .trace__banner--void {
    color: $color-danger;
    background: #fdecea;
  }

  .trace__card {
    margin-bottom: $space-3;
    padding: $space-4;
    border-radius: $radius-lg;
    background: $color-bg-card;
  }

  .trace__name {
    display: block;
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
  }

  .trace__spec {
    display: block;
    margin-top: $space-1;
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }

  .trace__hero {
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-top: $space-4;
  }

  .trace__hero-label {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }

  .trace__hero-value {
    margin-top: $space-1;
    font-size: 48rpx;
    font-weight: $font-weight-bold;
    font-variant-numeric: tabular-nums;
  }

  .trace__row {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    padding: $space-2 0;
    font-size: $font-size-sm;
  }

  .trace__row text:first-child {
    color: $color-text-secondary;
    flex-shrink: 0;
    margin-right: $space-3;
  }

  .trace__mono {
    font-family: ui-monospace, Menlo, Consolas, monospace;
  }

  .trace__section {
    display: block;
    margin-bottom: $space-2;
    font-weight: $font-weight-bold;
  }

  .trace__report { padding: $space-3 0; border-top: 1px solid $color-border; overflow-wrap: anywhere; }
  .trace__report-line { display: block; margin-top: $space-2; font-size: $font-size-sm; }
  .trace__report-result { display: block; margin-top: $space-2; font-weight: $font-weight-bold; }
  .trace__report-result--success { color: $color-success; }
  .trace__report-result--error { color: $color-danger; }
  .trace__report-result--warning { color: $color-warning; }

  .trace__file {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $space-3 0;
    border-top: 1px solid $color-border;
  }

  .trace__file-name {
    flex: 1;
    margin-right: $space-3;
    font-size: $font-size-sm;
    word-break: break-all;
  }

  .trace__note {
    display: block;
    margin-top: $space-4;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
</style>
