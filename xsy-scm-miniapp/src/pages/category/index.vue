<template>
  <view class="category">
    <xsy-nav-bar title="分类" divider>
      <template #bottom>
        <!-- 搜索对齐 Home / Final：图标 20px @xRel=12、占位文本 @xRel=40、图文间距 8px -->
        <view class="category__search" @click="goSearch">
          <uni-icons type="search" :size="20" :color="COLOR_TEXT_TERTIARY" />
          <text class="category__search-ph">搜商品 / SKU</text>
        </view>
      </template>
    </xsy-nav-bar>

    <view class="category__body">
      <!-- 左侧一级分类：清单来自服务端 getCategories()，不写死分类名 -->
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
        <!--
          二级分类横滑：只在当前一级分类**确实有 children** 时渲染。
          没有 children 时整块不渲染、不占高度，商品列表直接从内容区顶部开始。
        -->
        <scroll-view v-if="subCategories.length" class="category__subs" scroll-x>
          <view class="category__subs-inner">
            <!--
              「全部」不是服务端分类：它映射当前一级 categoryId，
              不构造假的 category 对象提交给后端。
            -->
            <view class="category__sub" :class="{ 'category__sub--active': activeSubId === null }" @click="selectAllSubs">
              <text class="category__sub-text">全部</text>
            </view>
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
          <ProductCard v-for="p in products" :key="p.skuId" :product="p" @click="goDetail" @add="onAdd" @inquiry="onInquiry" />
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
  import { onShow } from '@dcloudio/uni-app';
  import XsyNavBar from '@/components/common/xsy-nav-bar.vue';
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
  import ProductCard from '@/components/business/product-card.vue';
  import { mallCatalogApi } from '@/api/mall';
  import { smartSentry } from '@/lib/smart-sentry';
  import { useMallNavigationStore } from '@/store/modules/mall/navigation';
  import { COLOR_TEXT_TERTIARY } from '@/constants/theme-color-const';

  const PAGE_SIZE = 10;

  const navigationStore = useMallNavigationStore();

  const categories = ref([]);
  const activePrimaryId = ref(null);
  const activeSubId = ref(null);

  const products = ref([]);
  const pageNum = ref(0);
  const total = ref(0);
  const loading = ref(false);
  const contentScrollTop = ref(0);
  /** 分类清单是否已加载完成：用于收口「从 Home 跳入时分类还没到」的竞态 */
  const categoriesLoaded = ref(false);

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
      // 首次进入：优先兑现 Home 带过来的目标分类，没有才落到首个
      const pendingHit = takePendingCategory();
      const initial = pendingHit || categories.value[0];
      if (initial) {
        selectPrimary(initial);
      }
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      categoriesLoaded.value = true;
      // 分类清单到位后再兜一次：覆盖「分类页已存在、被 switchTab 复用」的情况
      applyPendingCategory();
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

  /**
   * 回到「全部」。
   *
   * 「全部」不是一个服务端分类，它等价于「用当前一级 categoryId 查询」，
   * 也就是把 activeSubId 置回 null（effectiveCategoryId 会回落到一级 id）。
   * 这里不构造假的 category 对象，也不新增接口。
   */
  function selectAllSubs() {
    if (activeSubId.value === null) {
      return;
    }
    activeSubId.value = null;
    loadProducts(true);
  }

  /* ===================== Home → Category 定向跳转 ===================== */

  /**
   * 取出待选中的一级分类实体。
   *
   * 只按 id 匹配**服务端返回的真实分类对象**；无论命中与否都清掉意图
   * （一次性消费，避免陈旧意图影响下一次进入）。
   */
  function takePendingCategory() {
    const pendingId = navigationStore.pendingCategoryId;
    if (pendingId === null || pendingId === undefined) {
      return null;
    }
    const hit = categories.value.find((c) => c.categoryId === Number(pendingId)) || null;
    navigationStore.clearPendingCategoryId();
    return hit;
  }

  /**
   * 兑现从 Home 带过来的一次性导航意图。
   *
   * 三种情况：
   *   1. 分类清单尚未加载完成 → 直接返回，等 loadCategories() 的 finally 再兜一次（首开竞态）
   *   2. 命中真实分类 → selectPrimary（内部会把 activeSubId 归零 = 回到「全部」）
   *   3. 没命中 → 安全回退到当前 / 首个分类，不报错
   */
  function applyPendingCategory() {
    if (!categoriesLoaded.value || !categories.value.length) {
      return;
    }
    if (navigationStore.pendingCategoryId === null || navigationStore.pendingCategoryId === undefined) {
      return;
    }
    const hit = takePendingCategory();
    const fallback = categories.value.find((c) => c.categoryId === activePrimaryId.value) || categories.value[0];
    selectPrimary(hit || fallback);
  }

  /*
   * tabBar 页会被复用：再次从 Home 跳进来时不会重新执行 setup，
   * 所以定向跳转要在 onShow 兑现（此时分类清单通常已就绪）。
   */
  onShow(() => {
    applyPendingCategory();
  });

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

  /** 无客户价商品不进入加购，改为引导询价（与 Home 保持同一交互，不新增接口/页面） */
  function onInquiry(product) {
    uni.showToast({ title: `${product.productName} 暂无客户价，请联系业务员询价`, icon: 'none' });
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
      /* 390 主稿 358×36：70rpx ≈ 36.4px（rpx 网格取不到 36，按「不追 1px」取最近值） */
      height: 70rpx;
      padding: 0 $space-3;
      border-radius: $radius-pill;
      background-color: $color-bg-page;
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

    /*
     * 一级分类 rail。
     *
     * 390 主稿：rail 76px（含右侧 1px 分割线）+ 内容区 314px = 390。
     * 146rpx 在 390 下 ≈ 75.9px，配合右侧 flex:1，两栏之和恒等于视口宽，不会横向溢出。
     * 375 / 414 下实测 73 / 80.6px（Figma 目标 72 / 82，差 ≤1.4px）——
     * 单值 rpx 无法同时命中三档，按「不为 1px 写复杂布局」取 390 主稿基准。
     */
    &__rail {
      box-sizing: border-box;
      width: 146rpx;
      flex-shrink: 0;
      background-color: $color-bg-page;
      border-right: 1px solid $color-divider;
    }

    &__rail-item {
      @include flex-center;
      position: relative;
      /* 390 主稿 48px → 92rpx ≈ 47.8px */
      height: 92rpx;
      padding: 0 $space-1;

      &--active {
        background-color: $color-primary-light;
        border-radius: $radius-sm;

        &::before {
          content: '';
          position: absolute;
          left: 0;
          top: 50%;
          transform: translateY(-50%);
          /* 390 主稿 3×20px、圆角 2px */
          width: 6rpx;
          height: 40rpx;
          border-radius: 2px;
          background-color: $color-primary;
        }
      }
    }

    &__rail-text {
      font-size: $font-size-sm;
      color: $color-text-secondary;
      text-align: center;
      /* flex 子项默认 min-width:auto 不会收缩，必须显式放开，长分类名才会截断 */
      min-width: 0;
      max-width: 100%;
      @include ellipsis;
    }

    /* 选中态：字号放大到 14px 并转主色，Active 比未选中明显 */
    &__rail-item--active &__rail-text {
      font-size: $font-size-base;
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
      padding: $space-3 0;
      background-color: $color-bg-card;
    }

    &__subs-inner {
      display: inline-flex;
      align-items: center;
      padding: 0 $space-2;
    }

    &__sub {
      flex-shrink: 0;
      margin-right: $space-2;
      padding: 0 $space-3;
      /* 390 主稿 chip 高 28px → 54rpx ≈ 28.1px */
      height: 54rpx;
      border-radius: $radius-pill;
      background-color: $color-bg-page;
      @include flex-center;

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
