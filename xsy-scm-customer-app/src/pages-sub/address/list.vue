<template>
  <view class="addr">
    <view v-if="loading && !list.length" class="addr__hint">加载中…</view>

    <view v-else-if="!list.length" class="addr__empty">
      <uni-icons type="location" :size="56" :color="COLOR_TEXT_PLACEHOLDER" />
      <text class="addr__empty-title">还没有收货地址</text>
      <text class="addr__empty-desc">添加常用收货地址，下单时可直接选择</text>
    </view>

    <view v-else class="addr__list">
      <view v-for="item in list" :key="item.addressId" class="addr-item" @click="onItemTap(item)">
        <view class="addr-item__head">
          <text class="addr-item__receiver">{{ item.receiverName }}</text>
          <text class="addr-item__phone">{{ item.receiverPhone }}</text>
          <text v-if="item.isDefault" class="addr-item__tag">默认</text>
          <text v-if="selectMode" class="addr-item__tag addr-item__tag--pick">点击选择</text>
        </view>
        <text class="addr-item__detail">{{ fullAddress(item) }}</text>
        <view class="addr-item__actions">
          <text v-if="!item.isDefault" class="addr-item__action" @click.stop="setDefault(item)">设为默认</text>
          <text class="addr-item__action" @click.stop="edit(item)">编辑</text>
          <text class="addr-item__action addr-item__action--danger" @click.stop="remove(item)">删除</text>
        </view>
      </view>
    </view>

    <view class="addr__bar">
      <view class="addr__add" @click="create">新增收货地址</view>
    </view>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad, onShow } from '@dcloudio/uni-app';
  import { mallAddressApi } from '@/api/mall/address-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { COLOR_TEXT_PLACEHOLDER } from '@/constants/theme-color-const';
  import { CHECKOUT_ADDRESS } from '@/constants/local-storage-key-const';

  const list = ref([]);
  const loading = ref(false);
  /** 由结算页以 ?select=1 打开时，点选地址即回传并返回，不做编辑 */
  const selectMode = ref(false);

  onLoad((options) => {
    selectMode.value = String(options?.select || '') === '1';
  });

  function onItemTap(item) {
    if (!selectMode.value) {
      edit(item);
      return;
    }
    uni.setStorageSync(CHECKOUT_ADDRESS, item.addressId);
    uni.navigateBack();
  }

  function fullAddress(item) {
    return [item.provinceName, item.cityName, item.districtName, item.detailAddress].filter(Boolean).join(' ');
  }

  async function load() {
    loading.value = true;
    try {
      const response = await mallAddressApi.getList();
      list.value = response.data || [];
    } catch (error) {
      smartSentry.captureError(error);
    } finally {
      loading.value = false;
    }
  }

  function create() {
    uni.navigateTo({ url: '/pages-sub/address/edit' });
  }
  function edit(item) {
    uni.navigateTo({ url: `/pages-sub/address/edit?id=${item.addressId}` });
  }

  async function setDefault(item) {
    try {
      const response = await mallAddressApi.setDefault(item.addressId);
      list.value = response.data || [];
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '设置失败', icon: 'none' });
    }
  }

  function remove(item) {
    uni.showModal({
      title: '删除地址',
      content: `确认删除「${item.receiverName} ${item.receiverPhone}」这条地址？`,
      success: async ({ confirm }) => {
        if (!confirm) return;
        try {
          const response = await mallAddressApi.remove(item.addressId);
          list.value = response.data || [];
        } catch (error) {
          smartSentry.captureError(error);
          uni.showToast({ title: error?.message || '删除失败', icon: 'none' });
        }
      },
    });
  }

  onShow(() => {
    load();
  });
</script>

<style lang="scss" scoped>
  .addr {
    min-height: 100vh;
    background-color: $color-bg-page;
    padding-bottom: 140rpx;
  }
  .addr__hint {
    display: block;
    padding: $space-6;
    text-align: center;
    color: $color-text-tertiary;
  }
  .addr__empty {
    @include flex-center;
    flex-direction: column;
    padding-top: 160rpx;
  }
  .addr__empty-title {
    margin-top: $space-3;
    font-size: $font-size-lg;
    color: $color-text-secondary;
  }
  .addr__empty-desc {
    margin-top: $space-1;
    font-size: $font-size-sm;
    color: $color-text-tertiary;
  }

  .addr__list {
    margin-top: $space-3;
    background-color: $color-bg-card;
  }
  .addr-item {
    padding: $space-4;
    @include hairline-bottom;
  }
  .addr-item__head {
    @include flex-start;
  }
  .addr-item__receiver {
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
    color: $color-text-primary;
  }
  .addr-item__phone {
    margin-left: $space-3;
    font-size: $font-size-base;
    color: $color-text-secondary;
  }
  .addr-item__tag {
    margin-left: $space-2;
    padding: 2rpx 12rpx;
    border-radius: $radius-pill;
    background-color: $color-primary-light;
    color: $color-primary;
    font-size: $font-size-xs;
  }
  .addr-item__tag--pick {
    background-color: $color-bg-hover;
    color: $color-text-secondary;
  }
  .addr-item__detail {
    display: block;
    margin-top: $space-2;
    font-size: $font-size-sm;
    color: $color-text-secondary;
  }
  .addr-item__actions {
    @include flex-start;
    justify-content: flex-end;
    margin-top: $space-2;
  }
  .addr-item__action {
    margin-left: $space-4;
    font-size: $font-size-sm;
    color: $color-text-secondary;
  }
  .addr-item__action--danger {
    color: $color-danger;
  }

  .addr__bar {
    @include fixed-bottom-bar;
    @include flex-center;
    height: 108rpx;
    padding: 0 $space-4;
  }
  .addr__add {
    width: 100%;
    height: 80rpx;
    @include flex-center;
    border-radius: $radius-pill;
    background-color: $color-primary;
    color: white;
    font-size: $font-size-base;
  }
</style>
