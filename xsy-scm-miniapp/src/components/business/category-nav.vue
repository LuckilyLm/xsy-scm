<template>
  <view class="category-nav">
    <view v-for="item in items" :key="item.categoryId" class="category-nav__item" @click="$emit('select', item)">
      <view class="category-nav__icon">
        <!-- Image：服务端下发图标时优先用图 -->
        <image v-if="item.iconUrl" class="category-nav__image" :src="item.iconUrl" mode="aspectFill" />
        <!-- Fallback：无图时退回首字，保证任何分类都能渲染 -->
        <view v-else class="category-nav__fallback">
          <text class="category-nav__fallback-text">{{ firstChar(item.categoryName) }}</text>
        </view>
      </view>
      <text class="category-nav__label">{{ item.categoryName }}</text>
    </view>
  </view>
</template>

<script setup>
  /**
   * 分类导航（Home V2 轻量展示组件）。
   *
   * 对齐 Figma「CategoryItem V2」的 Image / Icon / Fallback 三态：
   * 有 iconUrl 用图，没有则退回首字占位。
   *
   * 分类一律来自服务端 CATEGORY_NAV 楼层，客户端不写死分类清单，
   * 避免出现「页面上的分类」与「后端真实分类」两套事实。
   */
  defineProps({
    items: {
      type: Array,
      default: () => [],
    },
  });

  defineEmits(['select']);

  function firstChar(name) {
    return String(name || '分').slice(0, 1);
  }
</script>

<style lang="scss" scoped>
  .category-nav {
    display: flex;
    flex-wrap: wrap;
    padding: $space-2 $space-2 $space-1;
    background-color: $color-bg-card;
    border-radius: $radius-lg;
    margin: 0 $space-4;

    &__item {
      width: 16.666%;
      padding: $space-2 0;
      display: flex;
      flex-direction: column;
      align-items: center;
    }

    &__icon {
      width: 96rpx;
      height: 96rpx;
      border-radius: 50%;
      overflow: hidden;
      background-color: $color-bg-page;
      @include flex-center;
    }

    &__image {
      width: 100%;
      height: 100%;
    }

    &__fallback {
      width: 100%;
      height: 100%;
      background-color: $color-primary-light;
      @include flex-center;
    }

    &__fallback-text {
      font-size: $font-size-base;
      font-weight: $font-weight-medium;
      color: $color-primary;
    }

    &__label {
      margin-top: $space-1;
      max-width: 100%;
      font-size: $font-size-xs;
      color: $color-text-secondary;
      text-align: center;
      @include ellipsis;
    }
  }
</style>
