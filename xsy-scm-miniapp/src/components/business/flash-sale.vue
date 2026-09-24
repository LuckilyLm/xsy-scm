<template>
  <!-- 无活动时整层隐藏（规划 §10 约定）；showEmpty 仅用于设计态预览与兜底 -->
  <view v-if="visible" class="flash-sale">
    <view class="flash-sale__head">
      <view class="flash-sale__title-wrap">
        <text class="flash-sale__badge">限时</text>
        <text class="flash-sale__title">{{ title }}</text>
        <!-- 有倒计时时不再挤副标题：358px 宽下两者同时出现必然截断 -->
        <text v-if="subtitle && !countdown" class="flash-sale__subtitle">{{ subtitle }}</text>
      </view>

      <view v-if="countdown" class="flash-sale__countdown">
        <text class="flash-sale__countdown-label">距结束</text>
        <text class="flash-sale__countdown-cell">{{ countdown.h }}</text>
        <text class="flash-sale__countdown-sep">:</text>
        <text class="flash-sale__countdown-cell">{{ countdown.m }}</text>
        <text class="flash-sale__countdown-sep">:</text>
        <text class="flash-sale__countdown-cell">{{ countdown.s }}</text>
      </view>
    </view>

    <scroll-view v-if="items.length" class="flash-sale__scroll" scroll-x>
      <view class="flash-sale__list">
        <view v-for="item in items" :key="item.skuId" class="flash-sale__item" @click="$emit('click', item)">
          <view class="flash-sale__thumb">
            <image v-if="hasImage(item)" class="flash-sale__image" :src="item.imageUrl" mode="aspectFill" lazy-load @error="onImageError(item)" />
            <view v-else class="flash-sale__thumb-fallback">
              <text class="flash-sale__thumb-text">{{ firstChar(item.productName) }}</text>
            </view>
          </view>

          <text class="flash-sale__name">{{ item.productName }}</text>

          <view class="flash-sale__price-row">
            <text class="flash-sale__price-symbol">¥</text>
            <text class="flash-sale__price">{{ item.activityPrice }}</text>
            <text class="flash-sale__unit">/{{ item.unit }}</text>
          </view>

          <!-- 活动价由服务端下发，客户端不参与任何折扣计算（规划 §38.6） -->
          <text v-if="showOrigin(item)" class="flash-sale__origin">¥{{ item.price }}</text>

          <view class="flash-sale__action" @click.stop="$emit('add', item)">
            <text class="flash-sale__action-text">加购</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <view v-else class="flash-sale__empty">
      <text class="flash-sale__empty-text">{{ emptyText }}</text>
    </view>
  </view>
</template>

<script setup>
  import { computed, onUnmounted, ref, watch } from 'vue';

  /**
   * 限时抢购（Home V2 轻量展示组件）。
   *
   * 活动价来自楼层配置（规划 §9.2 FLASH_SALE「活动价」），
   * 商品基础价语义不变，客户端只展示、不算价。
   *
   * 无活动（无选品 / 已结束）时整层隐藏；showEmpty 用于保留设计态的 Empty 预览。
   */
  const props = defineProps({
    title: {
      type: String,
      default: '限时抢购',
    },
    subtitle: {
      type: String,
      default: '',
    },
    /** 活动结束时间，ISO 字符串 */
    endTime: {
      type: String,
      default: '',
    },
    items: {
      type: Array,
      default: () => [],
    },
    /** 是否在无活动时渲染 Empty 态（默认隐藏整层） */
    showEmpty: {
      type: Boolean,
      default: false,
    },
    emptyText: {
      type: String,
      default: '当前暂无进行中的活动',
    },
  });

  defineEmits(['click', 'add']);

  const now = ref(Date.now());
  let timer = null;

  function stop() {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  }

  function start() {
    stop();
    timer = setInterval(() => {
      now.value = Date.now();
    }, 1000);
  }

  /** 活动是否仍在进行中 */
  const active = computed(() => {
    if (!props.endTime) {
      return true;
    }
    const end = Date.parse(props.endTime);
    return Number.isNaN(end) ? true : end > now.value;
  });

  const hasItems = computed(() => props.items.length > 0);
  const visible = computed(() => (hasItems.value && active.value) || props.showEmpty);

  const countdown = computed(() => {
    if (!props.endTime) {
      return null;
    }
    const end = Date.parse(props.endTime);
    if (Number.isNaN(end)) {
      return null;
    }
    const diff = Math.max(0, end - now.value);
    const total = Math.floor(diff / 1000);
    const pad = (n) => String(n).padStart(2, '0');
    return {
      h: pad(Math.floor(total / 3600)),
      m: pad(Math.floor((total % 3600) / 60)),
      s: pad(total % 60),
    };
  });

  /** 仅当服务端同时给了基础价且与活动价不同，才展示划线原价 */
  function showOrigin(item) {
    return !!item.price && !!item.activityPrice && item.price !== item.activityPrice;
  }

  function firstChar(name) {
    return String(name || '商').slice(0, 1);
  }

  /*
   * 图片加载失败兜底：与 ProductCard 同一考虑——
   * 商品图是本地 static 素材，仓库只保留每品类少量样本，
   * 干净检出时部分 imageUrl 无对应文件，必须退回首字占位而不是显示裂图。
   */
  const failedImages = ref([]);

  function hasImage(item) {
    return !!item.imageUrl && !failedImages.value.includes(item.skuId);
  }

  function onImageError(item) {
    if (!failedImages.value.includes(item.skuId)) {
      failedImages.value = [...failedImages.value, item.skuId];
    }
  }

  // 有活动且未结束时才跑倒计时，避免无谓的每秒重渲染
  watch(
    () => visible.value,
    (v) => {
      if (v && active.value && props.endTime) {
        start();
      } else {
        stop();
      }
    },
    { immediate: true }
  );

  onUnmounted(stop);
</script>

<style lang="scss" scoped>
  .flash-sale {
    margin: 0 $space-4;
    padding: $space-3 0 $space-3;
    border-radius: $radius-lg;
    background: linear-gradient(180deg, $color-danger-light 0%, $color-bg-card 55%);
  }

  .flash-sale__head {
    @include flex-between;
    padding: 0 $space-3 $space-3;
  }

  .flash-sale__title-wrap {
    @include flex-start;
    min-width: 0;
  }

  .flash-sale__badge {
    flex-shrink: 0;
    padding: 0 $space-1;
    height: 32rpx;
    line-height: 32rpx;
    border-radius: $radius-sm;
    background-color: $color-danger;
    color: $color-text-inverse;
    font-size: $font-size-xs;
  }

  .flash-sale__title {
    margin-left: $space-2;
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
    color: $color-text-primary;
    white-space: nowrap;
  }

  .flash-sale__subtitle {
    margin-left: $space-2;
    font-size: $font-size-xs;
    color: $color-text-tertiary;
    @include ellipsis;
  }

  .flash-sale__countdown {
    @include flex-start;
    flex-shrink: 0;
    margin-left: $space-2;
  }

  .flash-sale__countdown-label {
    margin-right: $space-1;
    font-size: $font-size-xs;
    color: $color-text-tertiary;
  }

  .flash-sale__countdown-cell {
    min-width: 32rpx;
    padding: 0 $space-1;
    height: 32rpx;
    line-height: 32rpx;
    border-radius: $radius-sm;
    background-color: $color-text-primary;
    color: $color-text-inverse;
    font-size: $font-size-xs;
    text-align: center;
  }

  .flash-sale__countdown-sep {
    padding: 0 2rpx;
    font-size: $font-size-xs;
    color: $color-text-primary;
  }

  .flash-sale__scroll {
    white-space: nowrap;
  }

  .flash-sale__list {
    display: inline-flex;
    padding: 0 $space-3;
  }

  .flash-sale__item {
    position: relative;
    width: 200rpx;
    margin-right: $space-3;
    padding: $space-2;
    border-radius: $radius-md;
    background-color: $color-bg-card;
    display: flex;
    flex-direction: column;
  }

  .flash-sale__thumb {
    width: 100%;
    height: 168rpx;
    border-radius: $radius-sm;
    overflow: hidden;
    background-color: $color-primary-light;
    @include flex-center;
  }

  .flash-sale__image {
    width: 100%;
    height: 100%;
  }

  .flash-sale__thumb-fallback {
    width: 100%;
    height: 100%;
    background-color: $color-primary-light;
    @include flex-center;
  }

  .flash-sale__thumb-text {
    font-size: $font-size-lg;
    font-weight: $font-weight-medium;
    color: $color-primary;
  }

  .flash-sale__name {
    margin-top: $space-2;
    font-size: $font-size-sm;
    color: $color-text-primary;
    @include ellipsis;
  }

  .flash-sale__price-row {
    @include flex-start;
    margin-top: $space-1;
    flex-wrap: nowrap;
  }

  .flash-sale__price-symbol {
    flex-shrink: 0;
    font-size: $font-size-xs;
    color: $color-danger;
    white-space: nowrap;
  }

  .flash-sale__price {
    flex-shrink: 0;
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
    color: $color-danger;
    white-space: nowrap;
  }

  .flash-sale__unit {
    flex-shrink: 0;
    font-size: $font-size-xs;
    color: $color-text-tertiary;
    white-space: nowrap;
  }

  .flash-sale__origin {
    margin-top: 2rpx;
    font-size: $font-size-xs;
    color: $color-text-placeholder;
    text-decoration: line-through;
    @include ellipsis;
  }

  .flash-sale__action {
    margin-top: $space-2;
    height: 48rpx;
    border-radius: $radius-pill;
    background-color: $color-primary;
    @include flex-center;
  }

  .flash-sale__action-text {
    font-size: $font-size-xs;
    color: $color-text-inverse;
    white-space: nowrap;
  }

  .flash-sale__empty {
    @include flex-center;
    padding: $space-6 0;
  }

  .flash-sale__empty-text {
    font-size: $font-size-sm;
    color: $color-text-tertiary;
  }
</style>
