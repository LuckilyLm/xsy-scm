<template>
  <view class="tabbar" :style="themeStyle">
    <view
      v-for="(item, index) in items"
      :key="item.path"
      class="tabbar__item"
      :class="{ 'tabbar__item--active': index === active }"
      :aria-label="item.text"
      hover-class="xsy-press--active"
      :hover-stay-time="80"
      @click="go(index, item.path)"
    >
      <image class="tabbar__image" :src="index === active ? item.activeImage : item.image" mode="aspectFit" aria-hidden="true" />
      <text class="tabbar__text">{{ item.text }}</text>
    </view>
  </view>
</template>

<script setup>
  import { onMounted } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';
  import { MALL_NAVIGATION_IMAGES } from '@/constants/mall-visual-const';

  /*
   * 自绘底部导航。
   *
   * 微信原生 tabBar 只接受图片路径（iconPath），字体图标只有 App/H5 支持，
   * 使用交付PNG自绘并隐藏原生栏；普通与选中图片成对切换，文字选中色继续读取主题。
   * 保留固定高度和安全区计算，图片换肤不会改变页面预留的底部空间。
   *
   * 页面只需放 <XsyTabBar :active="n" />，隐藏原生栏在组件挂载时统一处理。
   */
  const props = defineProps({
    /** 当前页在 items 中的下标，0 起 */
    active: {
      type: Number,
      required: true,
    },
  });

  const { themeStyle } = useMallTheme();

  const items = [
    { image: MALL_NAVIGATION_IMAGES.home.normal, activeImage: MALL_NAVIGATION_IMAGES.home.active, text: '首页', path: '/pages/home/index' },
    {
      image: MALL_NAVIGATION_IMAGES.category.normal,
      activeImage: MALL_NAVIGATION_IMAGES.category.active,
      text: '分类',
      path: '/pages/category/index',
    },
    { image: MALL_NAVIGATION_IMAGES.cart.normal, activeImage: MALL_NAVIGATION_IMAGES.cart.active, text: '购物车', path: '/pages/cart/index' },
    { image: MALL_NAVIGATION_IMAGES.orders.normal, activeImage: MALL_NAVIGATION_IMAGES.orders.active, text: '订单', path: '/pages/order/index' },
    { image: MALL_NAVIGATION_IMAGES.mine.normal, activeImage: MALL_NAVIGATION_IMAGES.mine.active, text: '我的', path: '/pages/mine/index' },
  ];

  function go(index, path) {
    if (index === props.active) {
      return;
    }
    uni.switchTab({ url: path });
  }

  onMounted(() => {
    try {
      uni.hideTabBar({ animation: false, fail: () => {} });
    } catch {
      // 非 tab 页或平台不支持时忽略
    }
  });
</script>

<style lang="scss" scoped>
  .tabbar {
    /*
     * hairline-top 会写入 position: relative（供 ::before 定位），
     * 必须放在 position: fixed 之前，否则导航会掉回文档流里。
     */
    @include hairline-top;
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: $z-index-fixed-bar;
    display: flex;
    align-items: stretch;
    /* 总高度已包含安全区，避免内边距再次撑高导航并遮住购物车操作条。 */
    box-sizing: border-box;
    height: var(--xsy-tabbar-h, 100rpx);
    background-color: $color-bg-card;
    @include safe-area-bottom;
  }

  .tabbar__item {
    flex: 1;
    @include pressable;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4rpx;
  }

  .tabbar__text {
    font-size: $font-size-xs;
    line-height: 1.2;
    color: $color-text-tertiary;
  }

  .tabbar__image {
    width: 56rpx;
    height: 56rpx;
    flex-shrink: 0;
  }

  .tabbar__item--active .tabbar__text {
    color: var(--mall-primary-ink, #12833c);
    font-weight: $font-weight-medium;
  }
</style>
