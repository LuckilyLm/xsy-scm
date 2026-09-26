<template>
  <view class="search">
    <!-- 搜索栏 -->
    <view class="search__bar">
      <view class="search__input-wrap">
        <!-- 与 Home / Category Final 同款 SearchOutlined 图标槽：20px、xRel=12 -->
        <uni-icons type="search" :size="20" :color="COLOR_TEXT_TERTIARY" />
        <input
          class="search__input"
          type="text"
          :value="keyword"
          placeholder="搜商品名称 / SKU"
          placeholder-class="search__placeholder"
          confirm-type="search"
          :focus="autoFocus"
          @input="onInput"
          @confirm="doSearch()"
        />
        <text v-if="keyword" class="search__clear" @click="clearKeyword">×</text>
      </view>
      <text class="search__action" @click="doSearch()">搜索</text>
    </view>

    <!-- 未搜索：历史 + 热词 -->
    <view v-if="!searched" class="search__panel">
      <view v-if="history.length" class="search__section">
        <view class="search__section-head">
          <text class="search__section-title">搜索历史</text>
          <text class="search__section-action" @click="clearHistory">清空</text>
        </view>
        <view class="search__tags">
          <view v-for="word in history" :key="word" class="search__tag" @click="doSearch(word)">
            <text class="search__tag-text">{{ word }}</text>
          </view>
        </view>
      </view>

      <view v-if="hotKeywords.length" class="search__section">
        <view class="search__section-head">
          <text class="search__section-title">热门搜索</text>
        </view>
        <view class="search__tags">
          <view v-for="word in hotKeywords" :key="word" class="search__tag search__tag--hot" @click="doSearch(word)">
            <text class="search__tag-text search__tag-text--hot">{{ word }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 搜索结果 -->
    <view v-else class="search__result">
      <view v-if="products.length" class="search__list">
        <ProductCard v-for="p in products" :key="p.skuId" :product="p" @click="goDetail" @add="onAdd" @inquiry="onInquiry" />
        <view class="search__more">
          <text class="search__more-text">{{ loadMoreText }}</text>
        </view>
      </view>

      <view v-else-if="!loading" class="search__empty">
        <PagePlaceholder mark="无结果" title="没有找到相关商品" desc="换个关键词试试；商品是否可见由服务端按客户权限过滤。" plan-ref="规划 §13" />
      </view>

      <!--
        页面级 loading 只在「首次搜索、尚无任何结果」时出现（搜索中…）；
        分页时已有列表，只由列表底部的 loadMoreText 呈现「加载中…」，两处不同时出现。
      -->
      <view v-if="loading && !products.length" class="search__loading">
        <text class="search__loading-text">搜索中…</text>
      </view>
    </view>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { onReachBottom } from '@dcloudio/uni-app';
  import ProductCard from '@/components/business/product-card.vue';
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
  import { mallCatalogApi } from '@/api/mall';
  import { SEARCH_HISTORY } from '@/constants/local-storage-key-const';
  import { smartSentry } from '@/lib/smart-sentry';
  import { COLOR_TEXT_TERTIARY } from '@/constants/theme-color-const';

  const PAGE_SIZE = 10;
  const MAX_HISTORY = 10;

  const keyword = ref('');
  const submittedKeyword = ref('');
  const searched = ref(false);
  const autoFocus = ref(false);

  const products = ref([]);
  const pageNum = ref(0);
  const total = ref(0);
  const loading = ref(false);

  const history = ref(readHistory());
  const hotKeywords = ref([]);

  const hasMore = computed(() => products.value.length < total.value);
  const loadMoreText = computed(() => {
    if (loading.value) {
      return '加载中…';
    }
    return hasMore.value ? '上拉加载更多' : '没有更多了';
  });

  function readHistory() {
    try {
      const raw = uni.getStorageSync(SEARCH_HISTORY);
      return Array.isArray(raw) ? raw : [];
    } catch {
      return [];
    }
  }

  function writeHistory(words) {
    history.value = words.slice(0, MAX_HISTORY);
    uni.setStorageSync(SEARCH_HISTORY, history.value);
  }

  /** 命中过的词提到最前，去重后截断 */
  function pushHistory(word) {
    const rest = history.value.filter((w) => w !== word);
    writeHistory([word, ...rest]);
  }

  function clearHistory() {
    writeHistory([]);
  }

  function clearKeyword() {
    keyword.value = '';
  }

  function onInput(e) {
    keyword.value = e.detail.value;
  }

  async function loadHotKeywords() {
    try {
      const res = await mallCatalogApi.getHotKeywords();
      hotKeywords.value = res.data || [];
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  async function loadProducts(reset = false) {
    if (loading.value) {
      return;
    }
    if (reset) {
      pageNum.value = 0;
      products.value = [];
      total.value = 0;
    }

    loading.value = true;
    try {
      const res = await mallCatalogApi.getProducts({
        pageNum: pageNum.value + 1,
        pageSize: PAGE_SIZE,
        keyword: submittedKeyword.value,
      });
      const page = res.data || {};
      products.value = pageNum.value === 0 ? page.list || [] : products.value.concat(page.list || []);
      total.value = page.total || 0;
      pageNum.value += 1;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  /**
   * 触底加载下一页。
   * 本页是页面级滚动（结果区没有内层 scroll-view），因此用 uni-app 的页面生命周期
   * onReachBottom —— 与分类页的 scroll-view + scrolltolower 同属项目既有方案，不引入新组件。
   * 三道拦截：未发起过搜索 / loading 中 / 已取满 total，都不再请求。
   */
  function loadMore() {
    if (!searched.value || loading.value || !hasMore.value) {
      return;
    }
    loadProducts(false);
  }

  function doSearch(word) {
    const kw = String(word === undefined ? keyword.value : word).trim();
    if (!kw) {
      uni.showToast({ title: '请输入搜索关键词', icon: 'none' });
      return;
    }
    keyword.value = kw;
    submittedKeyword.value = kw;
    searched.value = true;
    pushHistory(kw);
    loadProducts(true);
  }

  function goDetail(product) {
    uni.navigateTo({ url: `/pages-sub/product/detail?skuId=${product.skuId}` });
  }

  function onAdd(product) {
    // 加购属 §39 第 10 项（购物车），cart 契约接入后替换这里
    uni.showToast({ title: `加入购物车：${product.productName}`, icon: 'none' });
  }

  /** 无客户价商品不进入加购，改为引导询价（与 Home / Category 保持同一交互，不新增接口/页面） */
  function onInquiry(product) {
    uni.showToast({ title: `${product.productName} 暂无客户价，请联系业务员询价`, icon: 'none' });
  }

  onReachBottom(loadMore);

  loadHotKeywords();
</script>

<style lang="scss" scoped>
  .search {
    min-height: 100vh;
    background-color: $color-bg-page;

    &__bar {
      @include flex-start;
      padding: $space-3 $space-4;
      background-color: $color-bg-card;
      /* Figma 工具栏分割线为 1px #F0F1F3（$color-divider），不是默认的 $color-border */
      @include hairline-bottom($color-divider);
    }

    &__input-wrap {
      flex: 1;
      min-width: 0;
      @include flex-start;
      /* 390 主稿输入区 36px → 70rpx ≈ 36.4px（与 Home / Category 搜索框同一取值） */
      height: 70rpx;
      padding: 0 $space-3;
      border-radius: $radius-pill;
      background-color: $color-bg-page;
    }

    &__input {
      flex: 1;
      min-width: 0;
      margin-left: $space-2;
      height: 100%;
      font-size: $font-size-base;
      color: $color-text-primary;
    }

    &__placeholder {
      color: $color-text-placeholder;
    }

    &__clear {
      padding: 0 $space-1;
      font-size: $font-size-lg;
      color: $color-text-placeholder;
      line-height: 1;
    }

    &__action {
      flex-shrink: 0;
      margin-left: $space-3;
      font-size: $font-size-base;
      color: $color-primary;
    }

    &__panel {
      padding: $space-4;
    }

    &__section {
      /* Figma：上一区最后一行 tag 底 → 下一区标题顶 = 46px；
         tag 自带 8px 下边距，故此处 38px */
      margin-bottom: 38px;
    }

    &__section-head {
      @include flex-between;
      /* Figma：标题底 → 首行 tag 顶 = 8px */
      margin-bottom: $space-2;
    }

    &__section-title {
      font-size: $font-size-base;
      font-weight: $font-weight-medium;
      color: $color-text-primary;
    }

    &__section-action {
      font-size: $font-size-sm;
      color: $color-text-tertiary;
    }

    &__tags {
      display: flex;
      flex-wrap: wrap;
    }

    &__tag {
      margin: 0 $space-2 $space-2 0;
      /* Figma：tag 高 32px、左右内边距 16px（2 字 56px / 3 字 68px）。
         uni-app 默认 content-box，历史 tag 的 1px 描边会额外撑高 2px，故显式声明 border-box */
      box-sizing: border-box;
      height: 62rpx;
      padding: 0 $space-4;
      border-radius: $radius-pill;
      background-color: $color-bg-card;
      /* 历史 tag：白底 + 1px #F0F1F3 描边 */
      border: 1px solid $color-divider;
      @include flex-center;

      &--hot {
        /* 热门 tag：浅绿底、无描边 */
        background-color: $color-primary-light;
        border: none;
      }
    }

    &__tag-text {
      font-size: $font-size-sm;
      color: $color-text-secondary;

      &--hot {
        color: $color-primary;
      }
    }

    &__list {
      background-color: $color-bg-card;
    }

    &__more,
    &__loading {
      @include flex-center;
      padding: $space-4 0;
    }

    &__more-text,
    &__loading-text {
      font-size: $font-size-sm;
      color: $color-text-tertiary;
    }

    &__empty {
      padding-top: $space-6;
    }
  }
</style>
