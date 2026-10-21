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
      <uni-icons :type="index === active ? item.activeIcon : item.icon" :size="22" :color="index === active ? theme.primary : COLOR_TEXT_TERTIARY" />
      <text class="tabbar__text">{{ item.text }}</text>
    </view>
  </view>
</template>

<script setup>
  import { onMounted } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';
  import { COLOR_TEXT_TERTIARY } from '@/constants/theme-color-const';

  /*
   * 自绘底部导航。
   *
   * 微信原生 tabBar 只接受图片路径（iconPath），字体图标只有 App/H5 支持，
   * 所以这里用 uni-icons 自绘，并把原生栏隐藏掉：
   * 图标、选中色都跟着主题 CSS 变量走，不再需要 20 张 PNG 与按主题换图的逻辑。
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

  const { theme, themeStyle } = useMallTheme();

  const items = [
    { icon: 'home', activeIcon: 'home-filled', text: '首页', path: '/pages/home/index' },
    { icon: 'shop', activeIcon: 'shop-filled', text: '分类', path: '/pages/category/index' },
    { icon: 'cart', activeIcon: 'cart-filled', text: '购物车', path: '/pages/cart/index' },
    { icon: 'list', activeIcon: 'list', text: '订单', path: '/pages/order/index' },
    { icon: 'person', activeIcon: 'person-filled', text: '我的', path: '/pages/mine/index' },
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

  .tabbar__item--active .tabbar__text {
    color: var(--mall-primary-ink, #12833c);
    font-weight: $font-weight-medium;
  }
</style>
