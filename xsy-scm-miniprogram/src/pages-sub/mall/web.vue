<template>
  <web-view v-if="safeUrl" :src="safeUrl" />
  <view v-else class="web-error" :style="themeStyle">链接不可用，请返回商城首页。</view>
</template>
<script setup>
  import { ref } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';
  const { themeStyle } = useMallTheme();
  import { onLoad } from '@dcloudio/uni-app';
  import { mallHomeApi } from '@/api/mall/home-api';

  const safeUrl = ref('');
  onLoad(async (query) => {
    try {
      const value = decodeURIComponent(query.url || '');
      if (!/^https:\/\//i.test(value)) return;
      // 外链只允许来自当前已发布首页或当前生效弹窗，两个入口都算“后台配置过的链接”。
      // 任一入口读取失败时仍用另一个入口判断，不能因为弹窗接口异常把首页外链一并拒绝。
      const [pageResult, popupResult] = await Promise.allSettled([mallHomeApi.published(), mallHomeApi.popup()]);
      const blocks = pageResult.status === 'fulfilled' ? pageResult.value.data?.blocks || [] : [];
      const popup = popupResult.status === 'fulfilled' ? popupResult.value.data : null;
      const popupItems = popup?.active ? popup.items || [] : [];
      if ([...blocks, ...popupItems].some((item) => item.linkType === 'EXTERNAL_URL' && item.linkTarget === value)) {
        safeUrl.value = value;
      }
    } catch {
      safeUrl.value = '';
    }
  });
</script>
<style scoped>
  .web-error {
    padding: 32rpx;
    color: #6b7280;
    text-align: center;
  }
</style>
