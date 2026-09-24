<template>
  <view class="category">
    <xsy-nav-bar title="分类">
      <template #bottom>
        <view class="category__search" @click="goSearch">
          <text class="category__search-mark">搜</text>
          <text class="category__search-ph">搜商品 / SKU</text>
        </view>
      </template>
    </xsy-nav-bar>

    <view class="category__body">
      <!-- 左侧一级分类 -->
      <scroll-view class="category__rail" scroll-y>
        <view
          v-for="item in categories"
          :key="item.categoryId"
          class="category__rail-item"
          :class="{ 'category__rail-item--active': item.categoryId === activePrimaryId }"
          @click="selectPrimary(item)"
        >
          <text class="category__rail-text">{{ item.categoryName }}</text>
        </view>
      </scroll-view>

      <!-- 右侧二级分类 + 商品列表 -->
      <scroll-view class="category__content" scroll-y :scroll-top="contentScrollTop" @scrolltolower="loadMore">
        <!-- 二级分类横滑 -->
        <scroll-view v-if="subCategories.length" class="category__subs" scroll-x>
          <view class="category__subs-inner">
            <view
              v-for="sub in subCategories"
              :key="sub.categoryId"
              class="category__sub"
              :class="{ 'category__sub--active': sub.categoryId === activeSubId }"
              @click="selectSub(sub)"
            >
              <text class="category__sub-text">{{ sub.categoryName }}</text>
            </view>
          </view>
        </scroll-view>

        <!-- 商品列表 -->
        <view v-if="products.length" class="category__list">
          <ProductCard v-for="p in products" :key="p.skuId" :product="p" @click="goDetail" @add="onAdd" />
          <view class="category__more">
            <text class="category__more-text">{{ loadMoreText }}</text>
          </view>
        </view>

        <view v-else-if="!loading" class="category__empty">
          <PagePlaceholder
            mark="无商品"
            title="该分类暂无可见商品"
            desc="商品可见性与客户价由服务端过滤，此处只展示服务端返回的结果。"
            plan-ref="规划 §11 / §12"
          />
        </view>

        <view v-if="loading" class="category__loading">
          <text class="category__loading-text">加载中…</text>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import XsyNavBar from '@/components/common/xsy-nav-bar.vue';
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
  import ProductCard from '@/components/business/product-card.vue';
  import { mallCatalogApi } from '@/api/mall';
  import { smartSentry } from '@/lib/smart-sentry';

  const PAGE_SIZE = 10;

  const categories = ref([]);
  const activePrimaryId = ref(null);
  const activeSubId = ref(null);

  const products = ref([]);
  const pageNum = ref(0);
  const total = ref(0);
  const loading = ref(false);
  const contentScrollTop = ref(0);

  const subCategories = computed(() => {
    const hit = categories.value.find((c) => c.categoryId === activePrimaryId.value);
    return hit ? hit.children || [] : [];
  });

  const hasMore = computed(() => products.value.length < total.value);

  const loadMoreText = computed(() => {
    if (loading.value) {
      return '加载中…';
    }
    return hasMore.value ? '上拉加载更多' : '没有更多了';
  });

  /** 当前生效的分类 id：优先二级，其次一级 */
  const effectiveCategoryId = computed(() => activeSubId.value || activePrimaryId.value);

  async function loadCategories() {
    try {
      const res = await mallCatalogApi.getCategories();
      categories.value = res.data || [];
      if (categories.value.length) {
        selectPrimary(categories.value[0]);
      }
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
    if (!effectiveCategoryId.value) {
      return;
    }

    loading.value = true;
    try {
      const res = await mallCatalogApi.getProducts({
        pageNum: pageNum.value + 1,
        pageSize: PAGE_SIZE,
        categoryId: effectiveCategoryId.value,
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
   * 触底加载下一页（本页是 scroll-view 内滚动，故用 @scrolltolower）。
   * 两道拦截：loading 期间不重复请求；已取满 total 时不再请求。
   * 这里只避免「无意义请求」，真正的分页边界仍以服务端返回的 total 为准。
   */
  function loadMore() {
    if (loading.value || !hasMore.value) {
      return;
    }
    loadProducts(false);
  }

  function selectPrimary(item) {
    if (activePrimaryId.value === item.categoryId) {
      return;
    }
    activePrimaryId.value = item.categoryId;
    // 切换一级分类时回到「全部子类」，避免沿用上一个分类的二级选中项
    activeSubId.value = null;
    contentScrollTop.value = contentScrollTop.value === 0 ? 1 : 0;
    loadProducts(true);
  }

  function selectSub(sub) {
    if (activeSubId.value === sub.categoryId) {
      return;
    }
    activeSubId.value = sub.categoryId;
    loadProducts(true);
  }

  function goSearch() {
    uni.navigateTo({ url: '/pages-sub/product/search' });
  }

  function goDetail(product) {
    uni.navigateTo({ url: `/pages-sub/product/detail?skuId=${product.skuId}` });
  }

  function onAdd(product) {
    // 加购属 §39 第 10 项（购物车），cart 契约接入后替换这里
    uni.showToast({ title: `加入购物车：${product.productName}`, icon: 'none' });
  }

  loadCategories();
</script>

<style lang="scss" scoped>
  .category {
    height: 100vh;
    display: flex;
    flex-direction: column;
    background-color: $color-bg-card;

    &__search {
      @include flex-start;
      margin: 0 $space-4 $space-3;
      height: 68rpx;
      padding: 0 $space-3;
      border-radius: $radius-pill;
      background-color: $color-bg-page;
    }

    &__search-mark {
      font-size: $font-size-sm;
      color: $color-text-tertiary;
    }

    &__search-ph {
      margin-left: $space-2;
      font-size: $font-size-base;
      color: $color-text-placeholder;
    }

    &__body {
      flex: 1;
      display: flex;
      min-height: 0;
    }

    &__rail {
      width: 180rpx;
      flex-shrink: 0;
      background-color: $color-bg-page;
    }

    &__rail-item {
      @include flex-center;
      height: 100rpx;
      padding: 0 $space-2;

      &--active {
        background-color: $color-bg-card;
        position: relative;

        &::before {
          content: '';
          position: absolute;
          left: 0;
          top: 50%;
          transform: translateY(-50%);
          width: 6rpx;
          height: 40rpx;
          border-radius: 0 $radius-sm $radius-sm 0;
          background-color: $color-primary;
        }
      }
    }

    &__rail-text {
      font-size: $font-size-base;
      color: $color-text-secondary;
      text-align: center;
      @include ellipsis;
    }

    &__rail-item--active &__rail-text {
      color: $color-primary;
      font-weight: $font-weight-medium;
    }

    &__content {
      flex: 1;
      min-width: 0;
      height: 100%;
    }

    &__subs {
      white-space: nowrap;
      padding: $space-3 0 $space-2;
      background-color: $color-bg-card;
    }

    &__subs-inner {
      display: inline-flex;
      align-items: center;
      padding: 0 $space-3;
    }

    &__sub {
      flex-shrink: 0;
      margin-right: $space-2;
      padding: $space-1 $space-3;
      border-radius: $radius-pill;
      background-color: $color-bg-page;

      &--active {
        background-color: $color-primary-light;
      }
    }

    &__sub-text {
      font-size: $font-size-sm;
      color: $color-text-secondary;
    }

    &__sub--active &__sub-text {
      color: $color-primary;
      font-weight: $font-weight-medium;
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
