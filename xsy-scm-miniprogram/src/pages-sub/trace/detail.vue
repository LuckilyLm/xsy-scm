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
        {{ voided ? '该码已作废，请勿食用' : '溯源信息已核验' }}
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

      <view v-if="attachments.length" class="trace__card">
        <text class="trace__section">资质与检测报告</text>
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
  import { onLoad } from '@dcloudio/uni-app';
  import { tracePublicApi } from '@/api/trace/trace-public-api';
  import { useMallTheme } from '@/composables/use-mall-theme';

  const COLOR_PRIMARY = '#12833c';

  const { themeStyle } = useMallTheme();

  const token = ref('');
  const loading = ref(false);
  const error = ref('');
  const view = ref(null);

  const voided = computed(() => view.value?.status === 'VOID');
  const attachments = computed(() => view.value?.attachments || []);

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

  async function load() {
    if (!token.value) {
      error.value = '链接缺少溯源码，请重新扫码';
      return;
    }
    loading.value = true;
    error.value = '';
    try {
      view.value = await tracePublicApi.detail(token.value);
    } catch (e) {
      view.value = null;
      error.value = e?.msg || e?.message || '加载失败，请重试';
    } finally {
      loading.value = false;
    }
  }

  /**
   * 打开附件。
   *
   * PDF 在微信内可以直接预览；Word 在 iOS 上常常打不开，失败时把链接复制出去，
   * 而不是只弹一句「打开失败」让人无从下手。
   */
  function openFile(file) {
    const url = tracePublicApi.fileUrl(token.value, file.fileKey);
    uni.showLoading({ title: '正在打开' });
    uni.downloadFile({
      url,
      success: (res) => {
        if (res.statusCode !== 200) {
          fallbackCopy(url);
          return;
        }
        uni.openDocument({
          filePath: res.tempFilePath,
          showMenu: true,
          fail: () => fallbackCopy(url),
          complete: () => uni.hideLoading(),
        });
      },
      fail: () => fallbackCopy(url),
    });
  }

  function fallbackCopy(url) {
    uni.hideLoading();
    uni.setClipboardData({
      data: url,
      success: () => uni.showToast({ title: '链接已复制，请在浏览器打开', icon: 'none' }),
    });
  }

  onLoad((options) => {
    token.value = options?.token || '';
    load();
  });
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
    color: $color-success;
    background: #eaf7ef;
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
