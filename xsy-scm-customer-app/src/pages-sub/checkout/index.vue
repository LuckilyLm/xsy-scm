<template>
  <view class="co">
    <!-- 收货地址 -->
    <view class="co-card co-address" @click="chooseAddress">
      <template v-if="address">
        <view class="co-address__head">
          <text class="co-address__name">{{ address.receiverName }}</text>
          <text class="co-address__phone">{{ address.receiverPhone }}</text>
        </view>
        <text class="co-address__detail">{{ fullAddress(address) }}</text>
      </template>
      <text v-else class="co-address__empty">请选择收货地址</text>
      <text class="co-address__arrow">›</text>
    </view>

    <!-- 期望配送日期 -->
    <picker mode="date" :value="deliveryDate" :start="today" @change="onDateChange">
      <view class="co-card co-row">
        <text class="co-row__label">期望配送日期</text>
        <text class="co-row__value" :class="{ 'co-row__value--placeholder': !deliveryDate }">
          {{ deliveryDate || '不指定' }}
        </text>
      </view>
    </picker>

    <!-- 商品明细 -->
    <view class="co-card">
      <view class="co-card__title">商品明细</view>
      <view v-for="item in items" :key="item.skuId" class="co-item">
        <view class="co-item__thumb">
          <image v-if="item.primaryImageUrl" class="co-item__image" :src="item.primaryImageUrl" mode="aspectFill" />
          <view v-else class="co-item__thumb-fallback">{{ (item.productName || '商').slice(0, 1) }}</view>
        </view>
        <view class="co-item__main">
          <text class="co-item__name">{{ item.productName }}</text>
          <text class="co-item__spec">{{ item.specName }}<text v-if="item.nonStandard"> · 按实重</text></text>
          <view class="co-item__price-row">
            <text class="co-item__price">¥{{ item.unitPrice }}</text>
            <text class="co-item__unit">/{{ item.saleUnit }}</text>
            <text class="co-item__qty">×{{ displayQuantity(item.quantity) }}</text>
          </view>
        </view>
        <text class="co-item__amount">¥{{ item.lineAmount }}</text>
      </view>
      <view v-if="nonStandardCount" class="co-tip">
        含 {{ nonStandardCount }} 项按实重结算，最终金额以出库实重为准
      </view>
    </view>

    <!-- 备注 -->
    <view class="co-card">
      <view class="co-card__title">订单备注</view>
      <textarea class="co-remark" v-model="remark" :maxlength="500" placeholder="如有特殊要求可在此说明" auto-height />
    </view>

    <view class="co-bar">
      <view class="co-bar__amount">
        <text class="co-bar__label">预计金额</text>
        <text v-if="previewing" class="co-bar__hint">计算中…</text>
        <text v-else class="co-bar__value">¥{{ estimatedAmount }}</text>
      </view>
      <view class="co-bar__submit" :class="{ 'co-bar__submit--disabled': !canSubmit }" @click="submit">
        {{ submitting ? '提交中…' : '提交订单' }}
      </view>
    </view>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { onLoad, onShow } from '@dcloudio/uni-app';
  import { mallCartApi } from '@/api/mall/cart-api';
  import { mallAddressApi } from '@/api/mall/address-api';
  import { mallOrderApi } from '@/api/mall/order-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { CHECKOUT_ADDRESS } from '@/constants/local-storage-key-const';

  const skuIds = ref([]);
  const items = ref([]);
  const address = ref(null);
  const estimatedAmount = ref('0.00');
  const deliveryDate = ref('');
  const remark = ref('');
  const previewing = ref(false);
  const submitting = ref(false);
  /** 服务端返回的价格指纹，提交时必须原样回传 */
  const fingerprint = ref('');
  const today = new Date().toISOString().slice(0, 10);

  /** 幂等键与内容绑定：内容不变时重试复用同一个键，内容变化才换新键 */
  const idempotencyKey = ref('');
  let lastSignature = '';

  const nonStandardCount = computed(() => items.value.filter((item) => item.nonStandard === true).length);
  const canSubmit = computed(() => !!address.value && items.value.length > 0 && !previewing.value && !submitting.value);

  function fullAddress(item) {
    return [item.provinceName, item.cityName, item.districtName, item.detailAddress].filter(Boolean).join(' ');
  }

  /** 数量按四位小数定点做整数运算，避免浮点误差 */
  function displayQuantity(quantity) {
    const scaled = Math.round(Number(quantity) * 10000);
    const int = Math.floor(scaled / 10000);
    const frac = String(scaled % 10000).padStart(4, '0').replace(/0+$/, '');
    return frac ? `${int}.${frac}` : `${int}`;
  }

  onLoad((options) => {
    skuIds.value = String(options?.skus || '')
      .split(',')
      .map((value) => Number(value))
      .filter((value) => Number.isFinite(value) && value > 0);
  });

  onShow(() => {
    load();
  });

  async function load() {
    await Promise.all([loadAddress(), loadItems()]);
    await refreshPreview();
  }

  async function loadAddress() {
    try {
      const response = await mallAddressApi.getList();
      const list = response.data || [];
      const picked = uni.getStorageSync(CHECKOUT_ADDRESS);
      const chosen = picked ? list.find((item) => item.addressId === Number(picked)) : null;
      address.value = chosen || list.find((item) => item.isDefault) || list[0] || null;
      if (picked) uni.removeStorageSync(CHECKOUT_ADDRESS);
    } catch (error) {
      smartSentry.captureError(error);
    }
  }

  async function loadItems() {
    try {
      const response = await mallCartApi.getCart();
      const all = response.data?.items || [];
      const selected = all.filter((item) => skuIds.value.includes(item.skuId));
      items.value = selected.filter((item) => item.availability === 'AVAILABLE');
      const dropped = selected.length - items.value.length;
      if (dropped > 0) {
        uni.showToast({ title: `${dropped} 项已不可售，已从本次结算中移除`, icon: 'none' });
      }
    } catch (error) {
      smartSentry.captureError(error);
    }
  }

  function payloadItems() {
    return items.value.map((item) => ({ skuId: item.skuId, quantity: item.quantity }));
  }

  /** 结算预览：金额与价格指纹都来自服务端 */
  async function refreshPreview() {
    if (!items.value.length) {
      estimatedAmount.value = '0.00';
      return;
    }
    previewing.value = true;
    try {
      const response = await mallOrderApi.preview({
        items: payloadItems(),
        addressId: address.value ? address.value.addressId : null,
      });
      const data = response.data || {};
      items.value = data.items || items.value;
      estimatedAmount.value = data.estimatedAmount || '0.00';
      fingerprint.value = data.priceFingerprint || '';
      syncIdempotencyKey();
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '结算信息获取失败', icon: 'none' });
    } finally {
      previewing.value = false;
    }
  }

  function contentSignature() {
    return JSON.stringify({
      items: payloadItems(),
      addressId: address.value ? address.value.addressId : null,
      remark: remark.value,
      deliveryDate: deliveryDate.value,
    });
  }

  /** 内容变化才换幂等键；网络重试复用旧键，避免生成重复订单 */
  function syncIdempotencyKey() {
    const signature = contentSignature();
    if (signature !== lastSignature) {
      lastSignature = signature;
      idempotencyKey.value = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    }
  }

  function onDateChange(event) {
    deliveryDate.value = event.detail.value || '';
    syncIdempotencyKey();
  }

  function chooseAddress() {
    uni.navigateTo({ url: '/pages-sub/address/list?select=1' });
  }

  async function submit() {
    if (submitting.value) return;
    if (!items.value.length) {
      uni.showToast({ title: '没有可结算的商品', icon: 'none' });
      return;
    }
    if (!address.value) {
      uni.showToast({ title: '请选择收货地址', icon: 'none' });
      return;
    }
    if (!fingerprint.value) {
      uni.showToast({ title: '请等待结算金额计算完成', icon: 'none' });
      return;
    }
    submitting.value = true;
    try {
      const response = await mallOrderApi.submit(
        {
          items: payloadItems(),
          addressId: address.value.addressId,
          priceFingerprint: fingerprint.value,
          remark: remark.value || undefined,
          expectDeliveryDate: deliveryDate.value || undefined,
        },
        idempotencyKey.value
      );
      const order = response.data || {};
      uni.redirectTo({
        url: `/pages-sub/checkout/success?orderNo=${encodeURIComponent(order.orderNo || '')}&amount=${order.orderedTotalAmount || ''}`,
      });
    } catch (error) {
      smartSentry.captureError(error);
      // 变价必须让客户重新确认，不做静默重试
      if (error?.code === 40985) {
        uni.showModal({
          title: '价格已变化',
          content: '商品价格已更新，请确认新的结算金额后重新提交。',
          showCancel: false,
          success: () => refreshPreview(),
        });
        return;
      }
      uni.showToast({ title: error?.message || '提交失败，请重试', icon: 'none' });
    } finally {
      submitting.value = false;
    }
  }
</script>

<style lang="scss" scoped>
  .co { min-height: 100vh; background-color: $color-bg-page; padding-bottom: 140rpx; }
  .co-card { margin-top: $space-3; background-color: $color-bg-card; padding: $space-4; }
  .co-card__title { font-size: $font-size-base; font-weight: $font-weight-bold; color: $color-text-primary; }

  .co-address { position: relative; padding-right: $space-8; }
  .co-address__head { @include flex-start; }
  .co-address__name { font-size: $font-size-lg; font-weight: $font-weight-bold; color: $color-text-primary; }
  .co-address__phone { margin-left: $space-3; font-size: $font-size-base; color: $color-text-secondary; }
  .co-address__detail { display: block; margin-top: $space-2; font-size: $font-size-sm; color: $color-text-secondary; }
  .co-address__empty { font-size: $font-size-base; color: $color-text-placeholder; }
  .co-address__arrow { position: absolute; right: $space-4; top: 50%; transform: translateY(-50%); color: $color-text-tertiary; }

  .co-row { @include flex-between; }
  .co-row__label { font-size: $font-size-base; color: $color-text-primary; }
  .co-row__value { font-size: $font-size-base; color: $color-text-primary; }
  .co-row__value--placeholder { color: $color-text-placeholder; }

  .co-item { @include flex-start; margin-top: $space-3; }
  .co-item__thumb { width: 112rpx; height: 112rpx; border-radius: $radius-md; overflow: hidden; flex-shrink: 0; background-color: $color-bg-hover; }
  .co-item__image { width: 100%; height: 100%; }
  .co-item__thumb-fallback { width: 100%; height: 100%; @include flex-center; color: $color-text-tertiary; }
  .co-item__main { flex: 1; min-width: 0; margin-left: $space-3; }
  .co-item__name { font-size: $font-size-base; color: $color-text-primary; @include ellipsis; }
  .co-item__spec { display: block; margin-top: $space-1; font-size: $font-size-xs; color: $color-text-tertiary; }
  .co-item__price-row { @include flex-start; align-items: baseline; margin-top: $space-2; }
  .co-item__price { font-size: $font-size-base; font-weight: $font-weight-bold; color: $color-danger; }
  .co-item__unit { font-size: $font-size-xs; color: $color-text-tertiary; }
  .co-item__qty { margin-left: $space-3; font-size: $font-size-sm; color: $color-text-secondary; }
  .co-item__amount { margin-left: $space-2; font-size: $font-size-base; color: $color-text-primary; }

  .co-tip { margin-top: $space-3; padding: $space-2 $space-3; border-radius: $radius-sm;
    background-color: #fff8e6; font-size: $font-size-xs; color: $color-warning; }
  .co-remark { width: 100%; min-height: 96rpx; margin-top: $space-2; font-size: $font-size-base; color: $color-text-primary; }

  .co-bar { @include fixed-bottom-bar; @include flex-between; height: 108rpx; padding: 0 $space-4; }
  .co-bar__amount { @include flex-start; align-items: baseline; flex: 1; }
  .co-bar__label { font-size: $font-size-sm; color: $color-text-secondary; }
  .co-bar__value { margin-left: $space-1; font-size: $font-size-xl; font-weight: $font-weight-bold; color: $color-danger; }
  .co-bar__hint { margin-left: $space-1; font-size: $font-size-sm; color: $color-text-tertiary; }
  .co-bar__submit { flex-shrink: 0; padding: 0 $space-6; height: 72rpx; @include flex-center; border-radius: $radius-pill;
    background-color: $color-primary; color: white; font-size: $font-size-base; }
  .co-bar__submit--disabled { background-color: $color-primary-disabled; }
</style>
