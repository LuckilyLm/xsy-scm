<template>
  <view class="cart">
    <!-- 价格与库存提示：与效果图 04 顶部提示条一致 -->
    <view class="cart__notice">
      <uni-icons type="info-filled" :size="14" :color="COLOR_WARNING" />
      <text class="cart__notice-text">价格与库存在结算时重新校验，非标品按实际称重结算</text>
    </view>

    <view v-if="loading && !items.length" class="cart__hint">
      <text>加载中…</text>
    </view>

    <view v-else-if="!items.length" class="cart__empty">
      <uni-icons type="cart" :size="64" :color="COLOR_TEXT_PLACEHOLDER" />
      <text class="cart__empty-title">购物车暂无商品</text>
      <text class="cart__empty-desc">去首页或分类页挑选商品吧</text>
      <view class="cart__empty-action" @click="goHome">去逛逛</view>
    </view>

    <template v-else>
      <view class="cart__list">
        <view v-for="item in items" :key="item.skuId" class="cart-item" :class="{ 'cart-item--off': !isSettleable(item) }">
          <view class="cart-item__check" @click="toggle(item)">
            <uni-icons :type="isChecked(item) ? 'checkbox-filled' : 'circle'" :size="22"
                       :color="isChecked(item) ? COLOR_PRIMARY : COLOR_TEXT_PLACEHOLDER" />
          </view>

          <view class="cart-item__thumb">
            <image v-if="item.primaryImageUrl && !failed[item.skuId]" class="cart-item__image" :src="item.primaryImageUrl"
                   mode="aspectFill" @error="onImageError(item.skuId)" />
            <view v-else class="cart-item__thumb-fallback">{{ thumb(item) }}</view>
          </view>

          <view class="cart-item__main">
            <text class="cart-item__name">{{ item.productName || '商品已失效' }}</text>
            <text class="cart-item__spec">{{ item.specName || '' }}</text>

            <view v-if="isSettleable(item)" class="cart-item__price-row">
              <text class="cart-item__price-symbol">¥</text>
              <text class="cart-item__price">{{ item.unitPrice }}</text>
              <text class="cart-item__unit">/{{ item.saleUnit }}</text>
            </view>
            <text v-else class="cart-item__unavailable">{{ unavailableText(item) }}</text>

            <view class="cart-item__foot">
              <view class="stepper">
                <view class="stepper__btn" @click="step(item, -1)">−</view>
                <text class="stepper__value">{{ displayQuantity(item) }}</text>
                <view class="stepper__btn" @click="step(item, 1)">+</view>
              </view>
              <text class="cart-item__remove" @click="remove(item)">移除</text>
            </view>
          </view>
        </view>
      </view>

      <view class="cart__bar">
        <view class="cart__all" @click="toggleAll">
          <uni-icons :type="allChecked ? 'checkbox-filled' : 'circle'" :size="22"
                     :color="allChecked ? COLOR_PRIMARY : COLOR_TEXT_PLACEHOLDER" />
          <text class="cart__all-text">全选</text>
        </view>
        <view class="cart__amount">
          <text class="cart__amount-label">合计</text>
          <text v-if="estimateState === 'loading'" class="cart__amount-hint">计算中…</text>
          <text v-else-if="estimateState === 'error'" class="cart__amount-error" @click="retryEstimate">金额获取失败，点击重试</text>
          <text v-else class="cart__amount-value">¥{{ selectedAmount }}</text>
        </view>
        <view class="cart__submit" :class="{ 'cart__submit--disabled': !checkedCount || submitting || estimateState !== 'idle' }" @click="goCheckout">
          去结算{{ checkedCount ? `(${checkedCount})` : '' }}
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
  import { computed, reactive, ref } from 'vue';
  import { onShow } from '@dcloudio/uni-app';
  import { mallCartApi } from '@/api/mall/cart-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import {
    COLOR_PRIMARY,
    COLOR_TEXT_PLACEHOLDER,
    COLOR_WARNING,
  } from '@/constants/theme-color-const';

  const items = ref([]);
  /** 所选商品的预估金额，**一律来自服务端** /scm/mall/cart/estimate，客户端不做金额累计 */
  const selectedAmount = ref('0.00');
  /** 预估金额状态：idle / loading / error。请求中与失败时都不能沿用旧金额误导客户 */
  const estimateState = ref('idle');
  const loading = ref(false);
  const submitting = ref(false);
  const checked = reactive({});
  const failed = reactive({});
  /** 预估请求序号，避免旧响应覆盖新选择 */
  let estimateSeq = 0;

  const settleable = computed(() => items.value.filter((item) => isSettleable(item)));
  const checkedItems = computed(() => settleable.value.filter((item) => checked[item.skuId]));
  const checkedCount = computed(() => checkedItems.value.length);
  const allChecked = computed(() => settleable.value.length > 0 && checkedCount.value === settleable.value.length);

  function isSettleable(item) {
    return item.availability === 'AVAILABLE';
  }
  function isChecked(item) {
    return isSettleable(item) && checked[item.skuId] === true;
  }
  function thumb(item) {
    return ((item.productName || '商')).slice(0, 1);
  }
  function unavailableText(item) {
    if (item.availability === 'NO_PRICE') return '暂无客户价，请联系业务员询价';
    return '商品已下架或对当前客户不可见';
  }
  function onImageError(skuId) {
    failed[skuId] = true;
  }

  async function load() {
    loading.value = true;
    try {
      const response = await mallCartApi.getCart();
      apply(response.data);
    } catch (error) {
      smartSentry.captureError(error);
    } finally {
      loading.value = false;
    }
  }

  /** 服务端返回整车快照；勾选状态按可结算行保留，失效行自动取消勾选 */
  function apply(cart) {
    items.value = cart?.items || [];
    items.value.forEach((item) => {
      if (!isSettleable(item)) checked[item.skuId] = false;
    });
    refreshEstimate();
  }

  /** 勾选变化后向服务端要权威预估金额；序号防止旧响应覆盖新选择 */
  async function refreshEstimate() {
    const skuIds = checkedItems.value.map((item) => item.skuId);
    const seq = ++estimateSeq;
    if (!skuIds.length) {
      selectedAmount.value = '0.00';
      estimateState.value = 'idle';
      return;
    }
    // 先清掉旧金额：请求中与失败时都不能继续显示上一组选择的合计
    selectedAmount.value = '';
    estimateState.value = 'loading';
    try {
      const response = await mallCartApi.estimate(skuIds);
      if (seq !== estimateSeq) return;
      selectedAmount.value = response.data?.estimatedAmount || '0.00';
      estimateState.value = 'idle';
    } catch (error) {
      if (seq !== estimateSeq) return;
      smartSentry.captureError(error);
      estimateState.value = 'error';
    }
  }

  function retryEstimate() {
    if (estimateState.value === 'error') {
      refreshEstimate();
    }
  }

  function toggle(item) {
    if (!isSettleable(item)) return;
    checked[item.skuId] = !checked[item.skuId];
    refreshEstimate();
  }
  function toggleAll() {
    const next = !allChecked.value;
    settleable.value.forEach((item) => {
      checked[item.skuId] = next;
    });
    refreshEstimate();
  }

  /*
   * 数量一律按四位小数定点做**整数**运算，再格式化成字符串，避免浮点误差。
   * 服务端也按四位小数校验，两端口径一致。
   * 递增单位当前固定为 1；按商品配置起订量与递增单位需要先有商品主档配置，见路线图。
   */
  function scaled(value) {
    return Math.round(Number(value) * 10000);
  }
  function formatQuantity(scaledValue) {
    const int = Math.floor(scaledValue / 10000);
    const frac = String(scaledValue % 10000).padStart(4, '0').replace(/0+$/, '');
    return frac ? `${int}.${frac}` : `${int}`;
  }
  function displayQuantity(item) {
    return formatQuantity(scaled(item.quantity));
  }

  async function step(item, delta) {
    const next = scaled(item.quantity) + delta * 10000;
    if (next <= 0) return;
    await mutate(() => mallCartApi.updateItem(item.skuId, formatQuantity(next)));
  }

  async function remove(item) {
    await mutate(() => mallCartApi.removeItem(item.skuId));
  }

  async function mutate(action) {
    if (submitting.value) return;
    submitting.value = true;
    try {
      const response = await action();
      apply(response.data);
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '操作失败', icon: 'none' });
    } finally {
      submitting.value = false;
    }
  }

  function goHome() {
    uni.switchTab({ url: '/pages/home/index' });
  }

  function goCheckout() {
    if (!checkedCount.value) {
      uni.showToast({ title: '请先选择要结算的商品', icon: 'none' });
      return;
    }
    if (estimateState.value === 'loading') {
      uni.showToast({ title: '正在计算金额，请稍候', icon: 'none' });
      return;
    }
    if (estimateState.value === 'error') {
      uni.showToast({ title: '金额获取失败，请先点击重试', icon: 'none' });
      return;
    }
    uni.showToast({ title: '结算将在下一阶段开放', icon: 'none' });
  }

  onShow(() => {
    load();
  });
</script>

<style lang="scss" scoped>
  .cart { min-height: 100vh; background-color: $color-bg-page; padding-bottom: 140rpx; }
  .cart__notice { @include flex-start; padding: $space-2 $space-4; background-color: #fff8e6; }
  .cart__notice-text { margin-left: $space-1; font-size: $font-size-xs; color: $color-warning; }
  .cart__hint { display: block; padding: $space-6; text-align: center; color: $color-text-tertiary; }
  .cart__empty { @include flex-center; flex-direction: column; padding-top: 160rpx; }
  .cart__empty-title { margin-top: $space-3; font-size: $font-size-lg; color: $color-text-secondary; }
  .cart__empty-desc { margin-top: $space-1; font-size: $font-size-sm; color: $color-text-tertiary; }
  .cart__empty-action { margin-top: $space-5; padding: 0 $space-6; height: 72rpx; @include flex-center;
    border-radius: $radius-pill; background-color: $color-primary; color: white; font-size: $font-size-base; }

  .cart__list { margin-top: $space-3; background-color: $color-bg-card; }
  .cart-item { @include flex-start; padding: $space-3 $space-4; @include hairline-bottom; }
  .cart-item--off { opacity: .6; }
  .cart-item__check { flex-shrink: 0; padding-right: $space-2; }
  .cart-item__thumb { width: 128rpx; height: 128rpx; border-radius: $radius-md; overflow: hidden; flex-shrink: 0; background-color: $color-bg-hover; }
  .cart-item__image { width: 100%; height: 100%; }
  .cart-item__thumb-fallback { width: 100%; height: 100%; @include flex-center; color: $color-text-tertiary; font-size: $font-size-xl; }
  .cart-item__main { flex: 1; min-width: 0; margin-left: $space-3; }
  .cart-item__name { font-size: $font-size-base; color: $color-text-primary; font-weight: $font-weight-medium; @include ellipsis; }
  .cart-item__spec { display: block; margin-top: $space-1; font-size: $font-size-xs; color: $color-text-tertiary; }
  .cart-item__price-row { @include flex-start; align-items: baseline; margin-top: $space-2; }
  .cart-item__price-symbol { font-size: $font-size-xs; color: $color-danger; }
  .cart-item__price { font-size: $font-size-lg; font-weight: $font-weight-bold; color: $color-danger; }
  .cart-item__unit { font-size: $font-size-xs; color: $color-text-tertiary; }
  .cart-item__unavailable { display: block; margin-top: $space-2; font-size: $font-size-xs; color: $color-text-tertiary; }
  .cart-item__foot { @include flex-between; margin-top: $space-2; }
  .cart-item__remove { font-size: $font-size-xs; color: $color-text-tertiary; }
  .stepper { @include flex-start; }
  .stepper__btn { width: 48rpx; height: 48rpx; @include flex-center; border: 1px solid $color-border; border-radius: $radius-sm; color: $color-text-secondary; }
  .stepper__value { min-width: 64rpx; text-align: center; font-size: $font-size-base; color: $color-text-primary; }

  .cart__bar { @include fixed-bottom-bar; @include flex-between; height: 108rpx; padding: 0 $space-4; }
  .cart__all { @include flex-start; }
  .cart__all-text { margin-left: $space-1; font-size: $font-size-base; color: $color-text-primary; }
  .cart__amount { flex: 1; @include flex-start; justify-content: flex-end; margin-right: $space-3; align-items: baseline; }
  .cart__amount-label { font-size: $font-size-sm; color: $color-text-secondary; }
  .cart__amount-value { margin-left: $space-1; font-size: $font-size-xl; font-weight: $font-weight-bold; color: $color-danger; }
  .cart__amount-hint { margin-left: $space-1; font-size: $font-size-sm; color: $color-text-tertiary; }
  .cart__amount-error { margin-left: $space-1; font-size: $font-size-sm; color: $color-danger; }
  .cart__submit { flex-shrink: 0; padding: 0 $space-6; height: 72rpx; @include flex-center; border-radius: $radius-pill;
    background-color: $color-primary; color: white; font-size: $font-size-base; }
  .cart__submit--disabled { background-color: $color-primary-disabled; }
</style>
