<template>
  <view class="detail">
    <scroll-view class="detail__body" scroll-y>
      <view v-if="loading" class="detail__hint">
        <text class="detail__hint-text">加载中…</text>
      </view>

      <!-- 商品不存在 / 对该客户不可见：走页面内空态，不做「跳回列表」的强制行为 -->
      <view v-else-if="!product" class="detail__missing">
        <PagePlaceholder
          mark="不可见"
          title="商品不存在或已下架"
          desc="商品可见性由服务端按客户权限过滤。若确认该商品应可见，请联系业务员核对。"
          plan-ref="规划 §11"
        />
        <view class="detail__missing-action" @click="goCategory">
          <text class="detail__missing-action-text">去看其他商品</text>
        </view>
      </view>

      <template v-else>
        <!-- 主图：业主提供 fileKey 前用首字占位；真实图片加载失败同样回退首字，不出裂图 -->
        <view class="detail__gallery">
          <image v-if="product.imageUrl && !imageFailed" class="detail__image" :src="product.imageUrl" mode="aspectFill" @error="onImageError" />
          <text v-else class="detail__gallery-text">{{ thumb }}</text>
        </view>

        <!-- 价格 + 库存 -->
        <view class="detail__block">
          <view class="detail__price-row">
            <!-- 无报价时展示「询价」，绝不显示 ¥0.00（规划 §11.3） -->
            <template v-if="priced">
              <text class="detail__price-symbol">¥</text>
              <text class="detail__price">{{ product.price }}</text>
              <text class="detail__unit">/{{ product.unit }}</text>
            </template>
            <text v-else class="detail__price-unpriced">询价</text>
            <!--
              价格来源标签只在有价时渲染：UNPRICED 的主价格位已写「询价」，
              再显示「暂无报价」是重复信息（Final §价格来源 / UNPRICED）。
              档位一律来自 priceSourceLevel，不在这里重写 if/else 判定。
            -->
            <text v-if="priced && sourceLabel" class="detail__source" :class="`detail__source--${sourceLevel}`">
              {{ sourceLabel }}
            </text>
          </view>

          <view class="detail__stock-row">
            <text class="detail__stock" :class="`detail__stock--${level}`">{{ stockLabel }}</text>
            <text v-if="showQty" class="detail__available">可售 {{ product.availableQty }}{{ product.unit }}</text>
          </view>

          <text v-if="!priced" class="detail__price-hint">该商品暂无客户报价，请联系业务员询价后再下单。</text>
        </view>

        <!-- 名称 + 规格 -->
        <view class="detail__block">
          <view class="detail__title-row">
            <!-- 用 view 而非 text：小程序里 -webkit-line-clamp 在 block 级盒上才稳定 -->
            <view class="detail__name">{{ product.productName }}</view>
            <!-- 非标品按实重结算，是 XSY 与普通电商的关键差异，必须显式标注 -->
            <text v-if="nonStandard" class="detail__badge">按实重</text>
          </view>
          <view class="detail__meta-row">
            <text class="detail__meta">{{ product.spec }}</text>
            <text v-if="product.categoryName" class="detail__meta">· {{ product.categoryName }}</text>
          </view>
        </view>

        <!-- 非标品说明：下单量 ≠ 实际重量（规划 §11.2） -->
        <view v-if="nonStandard" class="detail__block detail__block--warn">
          <text class="detail__notice-title">按实际称重结算</text>
          <text class="detail__notice-text">{{ nonStandardText }}</text>
          <text class="detail__notice-text detail__notice-text--muted">下单数量 ≠ 最终实际重量</text>
        </view>

        <!-- 购买数量 -->
        <view class="detail__block">
          <view class="detail__qty-row">
            <text class="detail__qty-label">购买数量</text>
            <view class="detail__stepper">
              <view class="detail__step" :class="{ 'detail__step--disabled': !canDecrease }" @click="decrease">
                <text class="detail__step-text">-</text>
              </view>
              <text class="detail__qty-value">{{ quantity }}</text>
              <view class="detail__step" :class="{ 'detail__step--disabled': !canIncrease }" @click="increase">
                <text class="detail__step-text">+</text>
              </view>
            </view>
          </view>
          <text v-if="quantityHint" class="detail__qty-hint">{{ quantityHint }}</text>
        </view>

        <!-- 配送说明 -->
        <view v-if="product.deliveryTip" class="detail__block">
          <text class="detail__notice-title">配送说明</text>
          <text class="detail__notice-text">{{ product.deliveryTip }}</text>
        </view>

        <!-- 商品描述 -->
        <view v-if="product.description" class="detail__block">
          <text class="detail__notice-title">商品描述</text>
          <text class="detail__notice-text">{{ product.description }}</text>
        </view>

        <view class="detail__tail" />
      </template>
    </scroll-view>

    <!-- 底部固定操作条：只有一个主操作，形态由 submitKind 决定（add / inquiry / disabled） -->
    <view v-if="product" class="detail__footer">
      <view class="detail__footer-button" :class="`detail__footer-button--${submitKind}`" @click="onFooterAction">
        <text class="detail__footer-text">{{ submitText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
  import { mallCatalogApi } from '@/api/mall';
  import { isApiError } from '@/lib/smart-request';
  import { smartSentry } from '@/lib/smart-sentry';
  import { PLATFORM_ERROR_CODE } from '@/constants/error-code-const';
  import {
    hasPrice,
    isNonStandard,
    isOrderable,
    nonStandardTip,
    priceSourceLabel,
    priceSourceLevel,
    showAvailableQty,
    stockLevel,
    stockText,
    thumbText,
  } from '@/utils/product-display';

  const skuId = ref(0);
  const product = ref(null);
  const loading = ref(true);
  const quantity = ref(1);

  /*
   * 展示口径一律来自 @/utils/product-display，与 ProductCard 同源，
   * 避免「列表说有货、详情说缺货」这类不一致。
   */
  const priced = computed(() => hasPrice(product.value));
  const nonStandard = computed(() => isNonStandard(product.value));
  const nonStandardText = computed(() => nonStandardTip(product.value));
  const orderable = computed(() => isOrderable(product.value));
  const stockLabel = computed(() => stockText(product.value));
  const level = computed(() => stockLevel(product.value));
  const showQty = computed(() => showAvailableQty(product.value));
  const thumb = computed(() => thumbText(product.value));
  const sourceLabel = computed(() => priceSourceLabel(product.value));
  /** 价格来源语义档位（agreement / type / standard / unpriced），与 ProductCard 同一来源 */
  const sourceLevel = computed(() => priceSourceLevel(product.value));

  /** 主图加载失败兜底：与 ProductCard 同一做法，失败后退回首字占位，不出裂图 */
  const imageFailed = ref(false);

  function onImageError() {
    imageFailed.value = true;
  }

  /** 起订量与步进来自服务端契约，客户端只按它约束步进，不自行设定业务规则 */
  const minQty = computed(() => Math.max(1, Number(product.value?.minOrderQty) || 1));
  const stepQty = computed(() => Math.max(1, Number(product.value?.stepQty) || 1));

  /**
   * 数量上限取服务端下发的可售量。
   * 这只是**交互上限**（避免用户选出必然被拒的数量），不是权威判断：
   * 真正的库存校验必须在服务端事务内完成（规划 §38.7）。
   */
  const maxQty = computed(() => {
    const available = Number(product.value?.availableQty);
    if (!Number.isFinite(available) || available <= 0) {
      return minQty.value;
    }
    return Math.max(minQty.value, Math.floor(available));
  });

  const canDecrease = computed(() => orderable.value && quantity.value > minQty.value);
  const canIncrease = computed(() => orderable.value && quantity.value + stepQty.value <= maxQty.value);

  const quantityHint = computed(() => {
    const unit = product.value?.unit || '';
    const tips = [];
    if (minQty.value > 1) {
      tips.push(`起订 ${minQty.value}${unit}`);
    }
    if (stepQty.value > 1) {
      tips.push(`按 ${stepQty.value}${unit}递增`);
    }
    if (nonStandard.value) {
      tips.push('数量为预估量，最终按实重结算');
    }
    return tips.join(' · ');
  });

  /**
   * 底部主操作的形态（纯展示/交互分流，不是新的业务规则）：
   *   inquiry   无客户价 → 可点击的「询价」，与缺货的 disabled 语义完全不同
   *   disabled  已缺货   → 灰态不可点
   *   add       其余     → 加入购物车
   * 价格是否存在仍只由 hasPrice()（priced）决定，可下单性仍只由 isOrderable() 决定。
   */
  const submitKind = computed(() => {
    if (!priced.value) {
      return 'inquiry';
    }
    if (!orderable.value) {
      return 'disabled';
    }
    return 'add';
  });

  const submitText = computed(() => {
    if (submitKind.value === 'inquiry') {
      return '询价';
    }
    if (submitKind.value === 'disabled') {
      return '暂时缺货';
    }
    return '加入购物车';
  });

  function decrease() {
    if (canDecrease.value) {
      quantity.value -= stepQty.value;
    }
  }

  function increase() {
    if (canIncrease.value) {
      quantity.value = Math.min(maxQty.value, quantity.value + stepQty.value);
    }
  }

  function goCategory() {
    uni.switchTab({ url: '/pages/category/index' });
  }

  function onAdd() {
    if (!orderable.value) {
      return;
    }
    /*
     * 加购属 §39 第 10 项（购物车），购物车页面与 mock 路由就绪前保持占位。
     * 接入时改为：mallCartApi.addItem({ skuId, quantity: String(quantity.value) })
     * —— quantity 必须是字符串定点，见规划 §16。
     */
    uni.showToast({ title: `加入购物车：${product.value.productName} × ${quantity.value}`, icon: 'none' });
  }

  /** 无客户价商品的引导（与 Home / Category / Search 逐字一致） */
  function onInquiry() {
    uni.showToast({ title: `${product.value.productName} 暂无客户价，请联系业务员询价`, icon: 'none' });
  }

  function onFooterAction() {
    if (submitKind.value === 'add') {
      onAdd();
      return;
    }
    if (submitKind.value === 'inquiry') {
      onInquiry();
    }
    // disabled：缺货不允许发起任何动作
  }

  async function loadProduct() {
    loading.value = true;
    // 换商品时给主图一次重新加载的机会，避免上一个商品的失败状态污染这一个
    imageFailed.value = false;
    try {
      const res = await mallCatalogApi.getProduct(skuId.value);
      product.value = res.data || null;
      quantity.value = Math.max(1, Number(product.value?.minOrderQty) || 1);
    } catch (e) {
      // 「数据不存在」是预期的业务结果，已由页面空态表达，不再当作异常上报
      if (!isApiError(e, PLATFORM_ERROR_CODE.DATA_NOT_EXIST)) {
        smartSentry.captureError(e);
      }
      product.value = null;
    } finally {
      loading.value = false;
    }
  }

  onLoad((options) => {
    skuId.value = Number(options?.skuId) || 0;
    if (!skuId.value) {
      loading.value = false;
      product.value = null;
      return;
    }
    loadProduct();
  });
</script>

<style lang="scss" scoped>
  .detail {
    height: 100vh;
    display: flex;
    flex-direction: column;
    background-color: $color-bg-page;

    &__body {
      flex: 1;
      min-height: 0;
    }

    &__hint,
    &__missing-action {
      @include flex-center;
      padding: $space-6 0;
    }

    &__hint-text,
    &__missing-action-text {
      font-size: $font-size-sm;
      color: $color-text-tertiary;
    }

    &__missing {
      padding-top: $space-6;
    }

    &__missing-action-text {
      color: $color-primary;
      font-size: $font-size-base;
    }

    /* ---------------------------- 主图 ---------------------------- */
    &__gallery {
      height: 560rpx;
      background-color: $color-primary-light;
      @include flex-center;
    }

    &__image {
      width: 100%;
      height: 100%;
    }

    &__gallery-text {
      font-size: 120rpx;
      font-weight: $font-weight-bold;
      color: $color-primary;
    }

    /* ---------------------------- 通用块 ---------------------------- */
    &__block {
      margin-top: $space-2;
      padding: $space-4;
      background-color: $color-bg-card;

      /* 非标品提示块：用整块底色强调「按实重结算」，而不是只加一个小标签 */
      &--warn {
        background-color: $color-warning-light;
      }
    }

    /* ---------------------------- 价格 / 库存 ---------------------------- */
    &__price-row {
      @include flex-start;
    }

    &__price-symbol {
      font-size: $font-size-lg;
      color: $color-danger;
    }

    &__price {
      font-size: $font-size-xxl;
      font-weight: $font-weight-bold;
      color: $color-danger;
    }

    &__unit {
      font-size: $font-size-base;
      color: $color-text-tertiary;
    }

    &__price-unpriced {
      font-size: $font-size-xl;
      font-weight: $font-weight-medium;
      color: $color-text-secondary;
    }

    &__source {
      flex-shrink: 0;
      margin-left: $space-2;
      padding: 0 $space-2;
      /* Figma：来源标签高 24px（46rpx ≈ 23.9px）、圆角 4px、11px Medium */
      box-sizing: border-box;
      height: 46rpx;
      line-height: 46rpx;
      border-radius: $radius-sm;
      font-size: $font-size-xs;
      font-weight: $font-weight-medium;
      white-space: nowrap;

      /* 档位来自 priceSourceLevel()，与 ProductCard 的语义配色一致 */
      &--agreement {
        background-color: $color-primary-light;
        color: $color-primary;
      }

      &--type {
        background-color: $color-info-light;
        color: $color-info;
      }

      &--standard {
        background-color: $color-bg-page;
        color: $color-text-tertiary;
      }

      &--unpriced {
        background-color: $color-bg-page;
        color: $color-text-tertiary;
      }
    }

    &__stock-row {
      @include flex-start;
      margin-top: $space-2;
    }

    &__stock {
      font-size: $font-size-sm;
      font-weight: $font-weight-medium;

      &--ok {
        color: $color-success;
      }

      &--warn {
        color: $color-warning;
      }

      &--off {
        color: $color-text-tertiary;
      }
    }

    &__available {
      margin-left: $space-2;
      font-size: $font-size-sm;
      color: $color-text-secondary;
    }

    &__price-hint {
      display: block;
      margin-top: $space-2;
      font-size: $font-size-sm;
      color: $color-warning;
    }

    /* ---------------------------- 名称 / 规格 ---------------------------- */
    &__title-row {
      @include flex-start;
    }

    &__name {
      flex: 1;
      min-width: 0;
      /* Figma 主稿：商品名 20px Medium */
      font-size: $font-size-xl;
      font-weight: $font-weight-medium;
      color: $color-text-primary;
      @include ellipsis(2);
    }

    &__badge {
      flex-shrink: 0;
      margin-left: $space-2;
      padding: 0 $space-2;
      /* Figma：按实重 badge 高 24px，与价格来源标签同一量级 */
      box-sizing: border-box;
      height: 46rpx;
      line-height: 46rpx;
      border-radius: $radius-sm;
      background-color: $color-warning-light;
      color: $color-warning;
      font-size: $font-size-xs;
      white-space: nowrap;
    }

    &__meta-row {
      @include flex-start;
      margin-top: $space-2;
    }

    &__meta {
      font-size: $font-size-sm;
      color: $color-text-tertiary;

      & + & {
        margin-left: $space-1;
      }
    }

    /* ---------------------------- 说明块 ---------------------------- */
    &__notice-title {
      display: block;
      font-size: $font-size-base;
      font-weight: $font-weight-medium;
      color: $color-text-primary;
    }

    &__notice-text {
      display: block;
      margin-top: $space-1;
      font-size: $font-size-sm;
      line-height: 1.6;
      color: $color-text-secondary;

      /* 「下单数量 ≠ 最终实际重量」在 Final 里是 11px 弱化一行 */
      &--muted {
        font-size: $font-size-xs;
        color: $color-text-tertiary;
      }
    }

    &__block--warn &__notice-title {
      color: $color-warning;
    }

    /* ---------------------------- 数量 ---------------------------- */
    &__qty-row {
      @include flex-between;
    }

    &__qty-label {
      font-size: $font-size-base;
      color: $color-text-primary;
    }

    &__stepper {
      @include flex-start;
      /* Figma：步进器容器高 38px、底色 #F5F6F8、圆角 4px */
      box-sizing: border-box;
      height: 73rpx;
      border-radius: $radius-sm;
      background-color: $color-bg-page;
      overflow: hidden;
    }

    &__step {
      /* Figma：加减按钮 34×38，底色 #F2F3F5；禁用态只把文字转灰 */
      box-sizing: border-box;
      width: 66rpx;
      height: 100%;
      background-color: $color-bg-hover;
      @include flex-center;

      &--disabled {
        .detail__step-text {
          color: $color-text-tertiary;
        }
      }
    }

    &__step-text {
      font-size: $font-size-base;
      color: $color-text-primary;
      line-height: 1;
    }

    &__qty-value {
      /* Figma：数量格 58×38 */
      box-sizing: border-box;
      min-width: 112rpx;
      height: 100%;
      line-height: 73rpx;
      text-align: center;
      background-color: $color-bg-page;
      font-size: $font-size-base;
      font-weight: $font-weight-medium;
      color: $color-text-primary;
    }

    &__qty-hint {
      display: block;
      margin-top: $space-2;
      font-size: $font-size-xs;
      color: $color-text-tertiary;
    }

    /* ---------------------------- 底部条 ---------------------------- */
    &__tail {
      height: $space-6;
    }

    &__footer {
      /* Figma：底栏 = 1px 分割线 + 5 上 + 44 按钮 + 4 下 ≈ 54px；这里取 token 4/4 近似 */
      padding: $space-1 $space-4;
      background-color: $color-bg-card;
      @include hairline-top($color-divider);
      @include safe-area-bottom;
    }

    &__footer-button {
      box-sizing: border-box;
      /* Figma：主按钮 358×44 */
      height: 85rpx;
      border-radius: $radius-md;
      background-color: $color-primary;
      @include flex-center;

      /* 询价：白底 + primary 描边 + primary 字。可点击，不是 disabled */
      &--inquiry {
        background-color: $color-bg-card;
        border: 1px solid $color-primary;

        .detail__footer-text {
          color: $color-primary;
        }
      }

      /* 缺货：灰态，点击无动作 */
      &--disabled {
        background-color: $color-bg-hover;

        .detail__footer-text {
          color: $color-text-tertiary;
        }
      }
    }

    &__footer-text {
      font-size: $font-size-base;
      font-weight: $font-weight-medium;
      color: $color-text-inverse;
    }
  }
</style>
