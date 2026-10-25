<template>
  <view v-if="visible && items.length" class="home-popup" @touchmove.stop.prevent>
    <view class="home-popup__mask" @click="close" />
    <view class="home-popup__panel">
      <swiper
        class="home-popup__swiper"
        :indicator-dots="false"
        :autoplay="items.length > 1"
        :interval="4500"
        :circular="items.length > 1"
        @change="onChange"
      >
        <swiper-item v-for="(item, index) in items" :key="`${item.imageUrl}-${index}`">
          <image class="home-popup__image" :src="item.imageUrl" mode="aspectFill" :alt="item.title || `弹窗图片${index + 1}`" @click="select(item)" />
        </swiper-item>
      </swiper>
      <view v-if="items.length > 1" class="home-popup__dots">
        <view v-for="(item, index) in items" :key="index" class="home-popup__dot" :class="{ 'home-popup__dot--active': index === current }" />
      </view>
      <button class="home-popup__close" aria-label="关闭弹窗" @click="close">
        <uni-icons type="closeempty" :size="18" color="#ffffff" />
      </button>
    </view>
  </view>
</template>

<script setup>
  import { ref, watch } from 'vue';

  const props = defineProps({
    visible: { type: Boolean, default: false },
    items: { type: Array, default: () => [] },
  });
  const emit = defineEmits(['close', 'select']);
  const current = ref(0);

  watch(
    () => props.visible,
    (visible) => {
      if (visible) current.value = 0;
    }
  );

  function onChange(event) {
    current.value = event.detail.current || 0;
  }

  function close() {
    emit('close');
  }

  function select(item) {
    emit('select', item);
  }
</script>

<style lang="scss" scoped>
  .home-popup {
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 9000;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0 $space-6;
  }

  .home-popup__mask {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    background: rgba(0, 0, 0, 0.62);
  }

  .home-popup__panel {
    position: relative;
    width: 620rpx;
    max-height: 84vh;
    border-radius: $radius-lg;
    overflow: hidden;
    background: #000;
  }

  .home-popup__swiper {
    width: 620rpx;
    height: 820rpx;
  }

  .home-popup__image {
    display: block;
    width: 620rpx;
    height: 820rpx;
  }

  .home-popup__dots {
    position: absolute;
    right: 0;
    bottom: $space-3;
    left: 0;
    display: flex;
    justify-content: center;
    gap: $space-1;
  }

  .home-popup__dot {
    width: 12rpx;
    height: 12rpx;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.5);
  }

  .home-popup__dot--active {
    width: 28rpx;
    border-radius: $radius-pill;
    background: #ffffff;
  }

  .home-popup__close {
    position: absolute;
    top: $space-2;
    right: $space-2;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 56rpx;
    height: 56rpx;
    padding: 0;
    border: none;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.45);
    line-height: 1;
  }

  .home-popup__close::after {
    border: none;
  }
</style>
