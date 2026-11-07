<template>
  <view class="remote-image" :class="{ 'remote-image--fill': fill }" :aria-hidden="ariaHidden">
    <image
      v-if="src && !failed"
      class="remote-image__content"
      :src="src"
      :mode="mode"
      :alt="alt"
      :aria-hidden="ariaHidden"
      :lazy-load="lazyLoad"
      @error="failed = true"
      @load="$emit('load', $event)"
    />
    <view v-else class="remote-image__fallback" :aria-hidden="ariaHidden">
      <uni-icons v-if="fallbackIcon" :type="fallbackIcon" :size="24" color="#8F959E" />
    </view>
  </view>
</template>

<script setup>
  import { ref, watch } from 'vue';
  import { onShow } from '@dcloudio/uni-app';
  // 去掉微信自定义组件宿主，让图片及失败占位沿用调用页面的尺寸和定位。
  defineOptions({ options: { virtualHost: true } });
  const props = defineProps({
    src: { type: String, default: '' },
    mode: { type: String, default: 'aspectFit' },
    lazyLoad: { type: Boolean, default: false },
    fallbackIcon: { type: String, default: 'image' },
    alt: { type: String, default: '' },
    ariaHidden: { type: [Boolean, String], default: false },
    /** 嵌在已有固定尺寸容器中时填满容器，避免依赖跨组件的局部样式。 */
    fill: { type: Boolean, default: false },
  });
  defineEmits(['load']);
  const failed = ref(false);
  watch(
    () => props.src,
    () => {
      failed.value = false;
    }
  );
  // 再次进入页面时允许重新加载，断网一次不能让插画永久停在兜底状态。
  onShow(() => {
    failed.value = false;
  });
</script>

<style lang="scss" scoped>
  .remote-image {
    display: block;
    &--fill {
      width: 100%;
      height: 100%;
    }
  }
  .remote-image__content,
  .remote-image__fallback {
    width: 100%;
    height: 100%;
  }
  .remote-image__fallback {
    display: flex;
    align-items: center;
    justify-content: center;
  }
</style>
