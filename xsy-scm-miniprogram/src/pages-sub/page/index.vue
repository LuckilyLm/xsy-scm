<template>
  <view class="rich-page" :style="themeStyle">
    <view v-if="loading" class="rich-page__loading">
      <XsySkeleton variant="list" :rows="6" />
    </view>
    <view v-else-if="error" class="rich-page__state">
      <XsyEmpty
        icon="info"
        :tone="error === '内容加载失败，请重试' ? 'error' : 'muted'"
        :title="error"
        desc="内容由门店维护，请稍后再试"
        action-text="重新加载"
        @action="load"
      />
    </view>
    <view v-else class="rich-page__body">
      <text class="rich-page__title">{{ title }}</text>
      <rich-text class="rich-page__content" :nodes="richContent" />
    </view>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { onLoad, onShow } from '@dcloudio/uni-app';
  import { MALL_ERROR_CODE } from '@/constants/error-code-const';
  import XsySkeleton from '@/components/common/xsy-skeleton.vue';
  import XsyEmpty from '@/components/common/xsy-empty.vue';
  import { mallDecorationApi } from '@/api/mall';
  import { useMallTheme } from '@/composables/use-mall-theme';
  import { xsySentry } from '@/lib/xsy-sentry';

  const { themeStyle } = useMallTheme();

  /** 只允许这四个编码，避免 query 参数被拼成任意请求路径 */
  const PAGE_TITLES = {
    ANNOUNCEMENT: '商城公告',
    TERMS: '服务条款',
    ABOUT: '关于我们',
    AFTER_SALE: '售后规则',
  };
  const pageCode = ref('');
  const title = ref('');
  const content = ref('');
  /**
   * 服务端清洗会移除 style 属性，富文本里的图片可能按原图宽度撑破屏幕；
   * 在渲染前补一个受控的宽度约束（内容本身已经过白名单清洗）。
   */
  const richContent = computed(() => content.value.replace(/<img\b/gi, '<img style="max-width:100%;height:auto"'));
  const loading = ref(true);
  const error = ref('');
  let requestSeq = 0;

  async function load() {
    if (!pageCode.value) return;
    const request = ++requestSeq;
    loading.value = true;
    error.value = '';
    try {
      const data = (await mallDecorationApi.page(pageCode.value)).data;
      if (request !== requestSeq) return;
      title.value = data.title || PAGE_TITLES[pageCode.value];
      content.value = data.content || '';
      if (!content.value) {
        error.value = '内容暂未发布';
      } else {
        uni.setNavigationBarTitle({ title: title.value });
      }
    } catch (cause) {
      if (request !== requestSeq) return;
      const unpublished = cause?.code === MALL_ERROR_CODE.INVALID_PAGE;
      if (!unpublished) xsySentry.captureError(cause);
      error.value = unpublished ? '内容暂未发布' : '内容加载失败，请重试';
      title.value = PAGE_TITLES[pageCode.value] || '商城页面';
    } finally {
      if (request === requestSeq) loading.value = false;
    }
  }

  onLoad((options) => {
    const code = (options?.pageCode || '').toUpperCase();
    if (!Object.prototype.hasOwnProperty.call(PAGE_TITLES, code)) {
      pageCode.value = '';
      loading.value = false;
      error.value = '页面不存在';
      return;
    }
    pageCode.value = code;
    title.value = PAGE_TITLES[code];
    uni.setNavigationBarTitle({ title: title.value });
  });
  // 返回页面或恢复前台都读取当前发布内容，后台改稿不会被页面实例长期缓存。
  onShow(load);
</script>

<style lang="scss" scoped>
  .rich-page {
    @include viewport-min-height;
    background: $color-bg-page;
  }

  .rich-page__loading {
    padding: $space-4 var(--xsy-page-x);
  }

  .rich-page__state {
    padding-top: 160rpx;
  }

  .rich-page__body {
    margin-top: $space-3;
    padding: $space-5 var(--xsy-page-x) $space-8;
    background: white;
  }

  .rich-page__title {
    display: block;
    margin-bottom: $space-4;
    color: $color-text-primary;
    font-size: $font-size-xl;
    font-weight: $font-weight-bold;
    text-align: center;
  }

  .rich-page__content {
    color: $color-text-primary;
    font-size: $font-size-base;
    line-height: 1.8;
    overflow-wrap: anywhere;
  }
</style>
