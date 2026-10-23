<template>
  <view class="aa" :style="themeStyle">
    <view v-if="loading && !order" class="aa__state">订单加载中…</view>
    <view v-else-if="!order" class="aa__state">订单不存在或已不可查看</view>

    <template v-else>
      <view class="aa-card">
        <view class="aa-card__title">选择要退的商品</view>
        <view v-for="line in lines" :key="line.item.itemId" class="aa-item">
          <view class="aa-item__thumb">
            <image v-if="line.item.primaryImageUrl" class="aa-item__image" :src="line.item.primaryImageUrl" mode="aspectFill" lazy-load />
            <view v-else class="aa-item__thumb-fallback">{{ (line.item.productName || '商').slice(0, 1) }}</view>
          </view>
          <view class="aa-item__main">
            <text class="aa-item__name">{{ line.item.productName }}</text>
            <text class="aa__sub">{{ line.item.specName || '默认规格' }} · {{ line.item.saleUnit }}</text>
            <text class="aa__sub">
              ¥{{ displayAmount(line.item.unitPrice) }} · 可申请
              {{ line.available === null ? '待称重后可申请' : `${displayQuantity(line.available)}${line.item.saleUnit}` }}
            </text>
            <view v-if="line.available !== null" class="aa-item__quantity">
              <text class="aa-item__quantity-label">申请数量</text>
              <input
                class="aa-item__quantity-input"
                type="digit"
                :value="line.quantity"
                :placeholder="`最多 ${displayQuantity(line.available)}`"
                confirm-type="done"
                :cursor-spacing="24"
                @input="(event) => updateQuantity(line, event.detail.value)"
              />
            </view>
            <text v-if="lineError(line)" class="aa-item__error">{{ lineError(line) }}</text>
          </view>
          <view class="aa-item__estimate">
            <text v-if="lineEstimate(line)" class="aa-item__estimate-value">¥{{ lineEstimate(line) }}</text>
            <text v-if="lineEstimate(line)" class="aa-item__estimate-label">预估</text>
          </view>
        </view>
      </view>

      <view class="aa-card">
        <view class="aa-card__title">售后原因</view>
        <view class="aa-reasons">
          <view
            v-for="option in AFTERSALE_REASONS"
            :key="option"
            class="aa-reasons__chip"
            :class="{ 'aa-reasons__chip--active': reasonChoice === option }"
            @click="reasonChoice = option"
          >
            {{ option }}
          </view>
        </view>
        <textarea
          v-model="reasonDetail"
          class="aa-reasons__detail"
          maxlength="200"
          placeholder="补充说明（选填）：如破损程度、短缺数量等"
          :cursor-spacing="96"
          show-confirm-bar
          auto-height
        ></textarea>
      </view>

      <view class="aa-card">
        <view class="aa__row">
          <text class="aa__row-label">预估退款金额</text>
          <text class="aa__row-value aa__row-value--strong">¥{{ estimatedTotal }}</text>
        </view>
        <text class="aa__tip">预估金额按申请数量 × 成交单价计算，核定退款金额以平台审核为准</text>
      </view>

      <view class="aa-bar" :style="barStyle">
        <view class="aa-bar__btn" :class="{ 'aa-bar__btn--disabled': submitting }" @click="submit">提交申请</view>
      </view>
    </template>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';

  import { onLoad } from '@dcloudio/uni-app';
  import { mallOrderApi } from '@/api/mall/order-api';
  import { mallAfterSaleApi } from '@/api/mall/after-sale-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { displayAmount, displayQuantity } from '@/utils/order-display';
  import { AFTERSALE_REASONS, estimateLineAmount, isValidApplyQuantity, sumAmounts } from '@/utils/after-sale-display';
  import { useKeyboardHeight } from '@/composables/use-keyboard-height';

  const { themeStyle } = useMallTheme();
  /** 键盘弹出时把提交栏抬到键盘上沿 */
  const { barStyle } = useKeyboardHeight();

  const orderId = ref(null);
  const order = ref(null);
  const loading = ref(false);
  const submitting = ref(false);
  /** 每行的申请数量（字符串）；空串表示该行不退 */
  const quantities = ref({});
  const reasonChoice = ref('');
  const reasonDetail = ref('');
  const requestKeys = new Map();

  const lines = computed(() =>
    (order.value?.items || []).map((item) => ({
      item,
      // 可申请上限是实重：未录实重的行不能申请，等后台称重后再来
      available: item.actualQuantity === null || item.actualQuantity === undefined ? null : item.actualQuantity,
      quantity: quantities.value[item.itemId] || '',
    }))
  );

  const reason = computed(() => {
    const detail = reasonDetail.value.trim();
    return detail ? `${reasonChoice.value}：${detail}` : reasonChoice.value;
  });

  const selectedLines = computed(() => lines.value.filter((line) => line.quantity !== ''));

  const estimatedTotal = computed(() => sumAmounts(selectedLines.value.map((line) => lineEstimate(line))));

  onLoad((options) => {
    orderId.value = Number(options?.orderId) || null;
    load();
  });

  async function load() {
    if (!orderId.value || loading.value) return;
    loading.value = true;
    try {
      const response = await mallOrderApi.getDetail(orderId.value);
      order.value = response.data || null;
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '订单加载失败', icon: 'none' });
    } finally {
      loading.value = false;
    }
  }

  function updateQuantity(line, value) {
    quantities.value = { ...quantities.value, [line.item.itemId]: String(value).trim() };
  }

  function lineEstimate(line) {
    if (!isValidApplyQuantity(line.quantity, line.available)) return null;
    return estimateLineAmount(line.quantity, line.item.unitPrice);
  }

  function lineError(line) {
    if (line.quantity === '' || isValidApplyQuantity(line.quantity, line.available)) return '';
    return `数量需大于 0、最多 4 位小数，且不超过可申请数量`;
  }

  async function submit() {
    if (submitting.value) return;
    const items = selectedLines.value.map((line) => ({
      orderItemId: line.item.itemId,
      quantity: line.quantity,
    }));
    if (!items.length) {
      uni.showToast({ title: '请填写要退的商品数量', icon: 'none' });
      return;
    }
    if (selectedLines.value.some((line) => !isValidApplyQuantity(line.quantity, line.available))) {
      uni.showToast({ title: '申请数量超出可退数量，请检查', icon: 'none' });
      return;
    }
    if (!reasonChoice.value) {
      uni.showToast({ title: '请选择售后原因', icon: 'none' });
      return;
    }
    submitting.value = true;
    try {
      // 幂等键与「订单 + 明细 + 原因」绑定：网络重试复用同一个键，不会重复建单
      const payload = { orderId: orderId.value, reason: reason.value, items };
      const signature = JSON.stringify(payload);
      let key = requestKeys.get(signature);
      if (!key) {
        key = `aftersale-${orderId.value}-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
        requestKeys.set(signature, key);
      }
      const response = await mallAfterSaleApi.apply(payload, key);
      const returnId = response.data?.returnId;
      uni.showToast({ title: '售后申请已提交', icon: 'none' });
      if (returnId) {
        setTimeout(() => uni.redirectTo({ url: `/pages-sub/after-sale/detail?returnId=${returnId}` }), 600);
      } else {
        setTimeout(() => uni.navigateBack(), 600);
      }
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '提交失败，请稍后重试', icon: 'none' });
    } finally {
      submitting.value = false;
    }
  }
</script>

<style lang="scss" scoped>
  .aa {
    @include viewport-min-height;
    background: $color-bg-page;
    box-sizing: border-box;
    padding-bottom: 160rpx;
  }
  .aa__state {
    padding-top: 240rpx;
    text-align: center;
    color: $color-text-tertiary;
  }
  .aa-card {
    margin: $space-3 $space-4 0;
    padding: $space-4;
    @include card(0);
  }
  .aa-card__title {
    margin-bottom: $space-2;
    color: $color-text-primary;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .aa__sub {
    display: block;
    margin-top: $space-1;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .aa__row {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    margin-top: $space-2;
  }
  .aa__row-label {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .aa__row-value {
    flex: 1;
    margin-left: $space-3;
    text-align: right;
    color: $color-text-primary;
    font-size: $font-size-sm;
  }
  .aa__row-value--strong {
    color: $color-danger;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .aa__tip {
    display: block;
    margin-top: $space-2;
    color: $color-warning;
    font-size: $font-size-xs;
  }
  .aa-item {
    display: flex;
    align-items: flex-start;
    padding: $space-3 0;
    @include hairline-bottom;
  }
  .aa-item__thumb {
    width: 112rpx;
    height: 112rpx;
    flex-shrink: 0;
    border-radius: $radius-md;
    overflow: hidden;
    background: $color-bg-hover;
  }
  .aa-item__image {
    width: 100%;
    height: 100%;
  }
  .aa-item__thumb-fallback {
    width: 100%;
    height: 100%;
    @include flex-center;
    color: $color-text-tertiary;
  }
  .aa-item__main {
    flex: 1;
    min-width: 0;
    margin-left: $space-3;
  }
  .aa-item__name {
    color: $color-text-primary;
    font-size: $font-size-base;
    font-weight: $font-weight-medium;
  }
  .aa-item__quantity {
    display: flex;
    align-items: center;
    margin-top: $space-2;
  }
  .aa-item__quantity-label {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .aa-item__quantity-input {
    flex: 1;
    margin-left: $space-3;
    padding: $space-1 $space-3;
    border: 1px solid $color-border;
    border-radius: $radius-sm;
    font-size: $font-size-sm;
  }
  .aa-item__error {
    display: block;
    margin-top: $space-1;
    color: $color-danger;
    font-size: $font-size-xs;
  }
  .aa-item__estimate {
    margin-left: $space-3;
    text-align: right;
  }
  .aa-item__estimate-value {
    display: block;
    color: $color-text-primary;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .aa-item__estimate-label {
    display: block;
    margin-top: $space-1;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .aa-reasons {
    display: flex;
    flex-wrap: wrap;
  }
  .aa-reasons__chip {
    margin: 0 $space-2 $space-2 0;
    padding: $space-1 $space-3;
    border: 1px solid $color-border;
    border-radius: $radius-pill;
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .aa-reasons__chip--active {
    border-color: var(--mall-primary, #16a34a);
    color: var(--mall-primary-ink, #12833c);
  }
  .aa-reasons__detail {
    width: 100%;
    min-height: 140rpx;
    margin-top: $space-2;
    padding: $space-2 $space-3;
    box-sizing: border-box;
    border: 1px solid $color-border;
    border-radius: $radius-sm;
    font-size: $font-size-sm;
  }
  .aa-bar {
    @include fixed-bottom-bar;
    display: flex;
    align-items: center;
    justify-content: center;
    height: 108rpx;
    padding: 0 $space-4;
  }
  .aa-bar__btn {
    width: 100%;
    height: 80rpx;
    @include flex-center;
    border-radius: $radius-pill;
    background: var(--mall-primary, #16a34a);
    color: $color-text-inverse;
    font-size: $font-size-base;
  }
  .aa-bar__btn--disabled {
    opacity: 0.6;
  }
</style>
