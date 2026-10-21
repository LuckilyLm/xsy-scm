<template>
  <web-view v-if="safeUrl" :src="safeUrl"/>
  <view v-else class="web-error" :style="themeStyle">链接不可用，请返回商城首页。</view>
</template>
<script setup>
import { ref } from 'vue';
import { useMallTheme } from '@/composables/use-mall-theme';
const { themeStyle } = useMallTheme();
import { onLoad } from '@dcloudio/uni-app';
import { mallHomeApi } from '@/api/mall/home-api';

const safeUrl = ref('');
onLoad(async query => {
  try {
    const value = decodeURIComponent(query.url || '');
    if (!/^https:\/\//i.test(value)) return;
    const page = (await mallHomeApi.published()).data;
    if (page.blocks.some(block => block.linkType === 'EXTERNAL_URL' && block.linkTarget === value)) {
      safeUrl.value = value;
    }
  } catch {
    safeUrl.value = '';
  }
});
</script>
<style scoped>
.web-error {padding: 32rpx; color: #6b7280; text-align: center}
</style>
