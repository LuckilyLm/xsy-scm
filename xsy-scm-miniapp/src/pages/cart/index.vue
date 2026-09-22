<template>
  <view class="cart">
    <xsy-nav-bar title="购物车" />

    <!-- 购物车为空时的状态（接入 cart-api 后由真实数据驱动） -->
    <view class="cart__empty">
      <PagePlaceholder
        mark="购物车"
        title="购物车暂无商品"
        desc="可售 / 失效 / 暂不可售 / 库存不足 / 无价格 五类商品分组、数量修改与批量删除将在接入 cart-api 后实现。"
        plan-ref="规划 §16"
      />
    </view>

    <!-- 底部结算条：仅在购物车有商品时展示 -->
    <view v-if="false" class="cart__bar">
      <view class="cart__bar-all">
        <text class="cart__bar-all-text">全选</text>
      </view>
      <view class="cart__bar-amount">
        <text class="cart__bar-amount-label">预计金额</text>
        <text class="cart__bar-amount-value">¥0.00</text>
        <text class="cart__bar-amount-tip">最终以实重结算</text>
      </view>
      <view class="cart__bar-submit" @click="goCheckout">去结算</view>
    </view>
  </view>
</template>

<script setup>
  import XsyNavBar from '@/components/common/xsy-nav-bar.vue';
  import PagePlaceholder from '@/components/common/page-placeholder.vue';

  function goCheckout() {
    uni.navigateTo({ url: '/pages-sub/checkout/index' });
  }
</script>

<style lang="scss" scoped>
  .cart {
    min-height: 100vh;
    background-color: $color-bg-page;

    &__empty {
      @include flex-center;
      flex-direction: column;
      padding-top: $space-8;
    }

    &__bar {
      @include fixed-bottom-bar;
      @include flex-between;
      height: 100rpx;
      padding: 0 $space-4;
    }

    &__bar-all-text {
      font-size: $font-size-base;
      color: $color-text-primary;
    }

    &__bar-amount {
      flex: 1;
      display: flex;
      align-items: baseline;
      justify-content: flex-end;
      margin-right: $space-3;
    }

    &__bar-amount-label {
      font-size: $font-size-sm;
      color: $color-text-secondary;
    }

    &__bar-amount-value {
      margin-left: $space-1;
      font-size: $font-size-lg;
      font-weight: $font-weight-bold;
      color: $color-danger;
    }

    &__bar-amount-tip {
      margin-left: $space-2;
      font-size: $font-size-xs;
      color: $color-text-tertiary;
    }

    &__bar-submit {
      flex-shrink: 0;
      padding: 0 $space-6;
      height: 72rpx;
      border-radius: $radius-pill;
      background-color: $color-primary;
      color: $color-text-inverse;
      font-size: $font-size-base;
      @include flex-center;
    }
  }
</style>
