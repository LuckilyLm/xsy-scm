<template>
  <view class="co" :class="{ 'co--editing': remarkFocused || keyboardHeight > 0 }" :style="themeStyle">
    <!-- 收货地址 -->
    <view class="co-card co-address" @click="chooseAddress">
      <template v-if="address">
        <view class="co-address__head">
          <text class="co-address__name">{{ address.receiverName }}</text>
          <text class="co-address__phone">{{ address.receiverPhone }}</text>
        </view>
        <text class="co-address__detail">{{ fullAddress(address) }}</text>
      </template>
      <text v-else class="co-address__empty">请选择收货地址</text>
      <uni-icons class="co-address__arrow" type="right" :size="16" :color="COLOR_TEXT_TERTIARY" />
    </view>

    <picker v-if="warehouseOptions.length" :range="warehouseOptions" range-key="warehouseName" :value="warehouseIndex" @change="onWarehouseChange">
      <view class="co-card co-row">
        <text class="co-row__label">配送仓</text>
        <view class="co-row__value xsy-chevron">
          <text>{{ selectedWarehouse?.warehouseName || '请选择配送仓' }}</text>
          <uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY" />
        </view>
      </view>
    </picker>
    <view v-if="warehouseError" class="co-alert">
      <text class="co-alert__text">{{ warehouseError }}</text>
    </view>

    <!-- 不可售提示：预览发现不可售项时不允许提交，先让客户回购物车调整 -->
    <view v-if="unavailableItems.length" class="co-alert">
      <text class="co-alert__text">{{ unavailableTip }}</text>
      <text class="co-alert__action" @click="backToCart">返回购物车</text>
    </view>

    <!-- 期望配送日期 -->
    <picker mode="date" :value="deliveryDate" :start="today" @change="onDateChange">
      <view class="co-card co-row">
        <text class="co-row__label">期望配送日期</text>
        <text class="co-row__value" :class="{ 'co-row__value--placeholder': !deliveryDate }">
          {{ deliveryDate || '不指定' }}
        </text>
      </view>
    </picker>

    <!-- 商品明细 -->
    <view class="co-card">
      <view class="co-card__title">商品明细</view>
      <view v-for="item in items" :key="item.skuId" class="co-item" :class="{ 'co-item--off': !isAvailable(item) }">
        <view class="co-item__thumb">
          <image v-if="item.primaryImageUrl" class="co-item__image" :src="item.primaryImageUrl" mode="aspectFill" lazy-load />
          <view v-else class="co-item__thumb-fallback">{{ (item.productName || '商').slice(0, 1) }}</view>
        </view>
        <view class="co-item__main">
          <text class="co-item__name">{{ item.productName }}</text>
          <text class="co-item__spec">{{ item.specName }}<text v-if="item.nonStandard"> · 按实重</text></text>
          <view class="co-item__price-row">
            <text v-if="isAvailable(item)" class="co-item__price">¥{{ displayMoney(item.unitPrice) }}</text>
            <text v-else class="co-item__price co-item__price--off">不可售</text>
            <text class="co-item__unit">/{{ item.saleUnit }}</text>
            <text class="co-item__qty">×{{ displayQuantity(item.quantity) }}</text>
          </view>
        </view>
        <text v-if="isAvailable(item)" class="co-item__amount">¥{{ displayMoney(item.lineAmount) }}</text>
      </view>
      <view v-if="nonStandardCount" class="co-tip"> 含 {{ nonStandardCount }} 项按实重结算，最终金额以出库实重为准 </view>
    </view>

    <view class="co-card co-coupon">
      <view class="co-row" @click="chooseCoupon">
        <text class="co-row__label">优惠券</text>
        <view class="co-row__value xsy-chevron">
          <text>{{ selectedCouponName || '选择优惠券' }}</text>
          <uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY" />
        </view>
      </view>
      <text v-if="selectedCouponId" class="co-coupon__clear" @click="clearCoupon">不使用优惠券</text>
      <text v-if="promotionError" class="co-coupon__error">{{ promotionError }}</text>
      <text v-else-if="couponNotice" class="co-coupon__hint">{{ couponNotice }}</text>
      <text v-else-if="promotion?.couponName" class="co-coupon__hint">已试算：{{ promotion.couponName }}</text>
    </view>

    <view v-if="promotion && previewReady" class="co-card co-promotion">
      <view class="co-row"
        ><text class="co-row__label">商品预估金额</text><text>¥{{ displayMoney(estimatedAmount) }}</text></view
      >
      <view v-if="Number(promotion.discountAmount) > 0" class="co-row"
        ><text class="co-row__label">活动及优惠券优惠</text
        ><text class="co-promotion__discount">−¥{{ displayMoney(promotion.discountAmount) }}</text></view
      >
      <text v-for="name in promotion.appliedActivityNames || []" :key="name" class="co-promotion__detail">{{ name }}</text>
      <text v-for="gift in promotion.giftDescriptions || []" :key="gift" class="co-promotion__detail">赠品：{{ gift }}</text>
      <text class="co-promotion__note">优惠金额仅供参考，确认订单时会重新核对并确定；称重商品以最终结算金额为准。</text>
    </view>

    <!-- 备注 -->
    <view class="co-card">
      <view class="co-card__title">订单备注</view>
      <textarea
        class="co-remark"
        v-model="remark"
        :maxlength="500"
        placeholder="如有特殊要求可在此说明"
        auto-height
        :cursor-spacing="24"
        show-confirm-bar
        hold-keyboard
        :adjust-position="true"
        @focus="remarkFocused = true"
        @blur="remarkFocused = false"
      ></textarea>
    </view>

    <!-- 原生hidden会被flex样式覆盖，输入期间直接移除底栏，避免遮挡备注。 -->
    <view v-if="!remarkFocused && keyboardHeight === 0" class="co-bar">
      <view class="co-bar__amount">
        <text class="co-bar__label">优惠后预估</text>
        <text v-if="previewing || preparing" class="co-bar__hint">计算中…</text>
        <text v-else-if="!previewReady" class="co-bar__error" @click="retryPreview">{{ loadError || promotionError || '点击重新获取金额' }}</text>
        <text v-else class="co-bar__value">¥{{ displayMoney(promotion?.payableAmount || estimatedAmount) }}</text>
      </view>
      <view
        class="co-bar__submit"
        :class="{ 'co-bar__submit--disabled': !canSubmit }"
        hover-class="xsy-press-button--active"
        :hover-stay-time="80"
        @click="submit"
      >
        {{ submitting ? '提交中…' : '提交订单' }}
      </view>
    </view>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';

  import { onLoad, onShow, onHide } from '@dcloudio/uni-app';
  import { mallCartApi } from '@/api/mall/cart-api';
  import { mallAddressApi } from '@/api/mall/address-api';
  import { mallOrderApi } from '@/api/mall/order-api';
  import { xsySentry } from '@/lib/xsy-sentry';
  import { shanghaiDate } from '@/utils/mall-date';
  import { displayMoney } from '@/utils/money-display';
  import { CHECKOUT_ADDRESS } from '@/constants/local-storage-key-const';
  import { COLOR_TEXT_TERTIARY } from '@/constants/theme-color-const';
  import { useKeyboardHeight } from '@/pages-sub/shared/composables/use-keyboard-height';
  import { previewCheckout } from '@/pages-sub/shared/utils/checkout-preview';

  const { themeStyle } = useMallTheme();
  // 备注使用系统原生避让；输入期间隐藏提交栏，避免键盘顶起与手工bottom叠加。
  const { keyboardHeight } = useKeyboardHeight();
  const remarkFocused = ref(false);
  const loadError = ref('');
  const preparing = ref(false);
  const couponMode = ref('AUTO');
  const couponNotice = ref('');
  let loadSeq = 0;

  const skuIds = ref([]);
  const items = ref([]);
  const address = ref(null);
  const warehouseOptions = ref([]);
  const warehouseId = ref(null);
  const warehouseError = ref('');
  const estimatedAmount = ref('0.00');
  const selectedCouponId = ref(null);
  const selectedCouponName = ref('');
  const promotion = ref(null);
  const promotionError = ref('');
  const deliveryDate = ref('');
  const remark = ref('');
  const previewing = ref(false);
  const submitting = ref(false);
  /** 服务端返回的价格指纹，提交时必须原样回传 */
  const fingerprint = ref('');
  /** 本次预览是否成功；失败时不允许提交，避免拿旧金额下单 */
  const previewReady = ref(false);
  /** 预览发现的不可售项（含原因）；非空时禁止提交 */
  const unavailableItems = ref([]);
  /** 预览请求序号：只允许最后一次预览的响应更新页面，避免旧响应覆盖新金额与指纹 */
  let previewSeq = 0;
  const today = shanghaiDate();

  /** 防重复请求编号与内容绑定：内容不变时重试复用同一个键，内容变化才换新键 */
  const idempotencyKey = ref('');
  let lastSignature = '';

  const selectedWarehouse = computed(() => warehouseOptions.value.find((item) => item.warehouseId === warehouseId.value));
  const warehouseIndex = computed(() =>
    Math.max(
      0,
      warehouseOptions.value.findIndex((item) => item.warehouseId === warehouseId.value)
    )
  );
  const nonStandardCount = computed(() => items.value.filter((item) => item.nonStandard === true).length);
  const unavailableTip = computed(() => {
    if (!unavailableItems.value.length) return '';
    const names = unavailableItems.value.map((item) => item.productName || '部分商品');
    const head = names.slice(0, 2).join('、');
    const rest = names.length > 2 ? ` 等 ${names.length} 项` : '';
    return `${head}${rest}：${unavailableText(unavailableItems.value[0])}。请返回购物车调整后再结算`;
  });
  const canSubmit = computed(
    () =>
      !!address.value &&
      !!warehouseId.value &&
      !warehouseError.value &&
      items.value.length > 0 &&
      unavailableItems.value.length === 0 &&
      !promotionError.value &&
      previewReady.value &&
      !previewing.value &&
      !submitting.value &&
      !preparing.value &&
      !loadError.value
  );

  function isAvailable(item) {
    return item.availability === 'AVAILABLE';
  }
  /** 与购物车页保持同一套不可售文案 */
  function unavailableText(item) {
    if (item.availability === 'NO_PRICE') return '暂无客户价，请联系业务员询价';
    if (item.availability === 'QUANTITY_INVALID') return '数量不符合当前起订量和递增量，请返回购物车调整';
    if (item.unavailableReason === 'CHECKOUT_ITEM_UNAVAILABLE') return '当前商品不可下单';
    return '商品已下架或对当前客户不可见';
  }
  function backToCart() {
    uni.switchTab({ url: '/pages/cart/index' });
  }

  function fullAddress(item) {
    return [item.provinceName, item.cityName, item.districtName, item.detailAddress].filter(Boolean).join(' ');
  }

  /** 数量按四位小数定点做整数运算，避免浮点误差 */
  function displayQuantity(quantity) {
    const scaled = Math.round(Number(quantity) * 10000);
    const int = Math.floor(scaled / 10000);
    const frac = String(scaled % 10000)
      .padStart(4, '0')
      .replace(/0+$/, '');
    return frac ? `${int}.${frac}` : `${int}`;
  }

  onLoad((options) => {
    skuIds.value = String(options?.skus || '')
      .split(',')
      .map((value) => Number(value))
      .filter((value) => Number.isFinite(value) && value > 0);
  });

  onShow(() => {
    remarkFocused.value = false;
    keyboardHeight.value = 0;
    load();
  });
  onHide(() => {
    remarkFocused.value = false;
    keyboardHeight.value = 0;
    ++loadSeq;
    ++previewSeq;
  });

  async function load() {
    const seq = ++loadSeq;
    ++previewSeq;
    previewReady.value = false;
    previewing.value = false;
    fingerprint.value = '';
    preparing.value = true;
    loadError.value = '';
    try {
      await Promise.all([loadAddress(seq), loadItems(seq)]);
      if (seq !== loadSeq) return;
      await loadWarehouses(seq);
      if (seq !== loadSeq) return;
      await refreshPreview();
    } catch (error) {
      if (seq !== loadSeq) return;
      loadError.value = error?.message || '订单资料加载失败，点击重试';
      xsySentry.captureError(error);
    } finally {
      if (seq === loadSeq) preparing.value = false;
    }
  }

  function retryPreview() {
    return loadError.value ? load() : refreshPreview();
  }

  async function loadAddress(seq) {
    const response = await mallAddressApi.getList();
    const list = response.data || [];
    if (seq !== loadSeq) return;
    const picked = uni.getStorageSync(CHECKOUT_ADDRESS);
    const selectedId = picked || address.value?.addressId;
    const chosen = selectedId ? list.find((item) => String(item.addressId) === String(selectedId)) : null;
    address.value = chosen || list.find((item) => item.isDefault) || list[0] || null;
    if (picked) uni.removeStorageSync(CHECKOUT_ADDRESS);
  }

  async function loadWarehouses(seq) {
    warehouseOptions.value = [];
    warehouseError.value = '';
    try {
      const response = await mallOrderApi.getWarehouses(address.value?.addressId);
      if (seq !== loadSeq) return;
      warehouseOptions.value = response.data || [];
      if (!warehouseOptions.value.some((item) => item.warehouseId === warehouseId.value)) {
        warehouseId.value = warehouseOptions.value[0]?.warehouseId || null;
      }
      if (!warehouseId.value) warehouseError.value = '当前收货地址暂无可用配送仓，请联系管理员';
    } catch (error) {
      if (seq !== loadSeq) return;
      warehouseId.value = null;
      warehouseError.value = error?.message || '配送仓信息加载失败，请稍后重试';
      xsySentry.captureError(error);
    }
  }

  function onWarehouseChange(event) {
    const selected = warehouseOptions.value[Number(event.detail.value)];
    if (!selected || selected.warehouseId === warehouseId.value) return;
    warehouseId.value = selected.warehouseId;
    syncIdempotencyKey();
    return refreshPreview();
  }

  async function loadItems(seq) {
    const response = await mallCartApi.getCart();
    const all = response.data?.items || [];
    if (seq !== loadSeq) return;
    const selected = all.filter((item) => skuIds.value.includes(item.skuId));
    // 购物车按默认仓读取的可售状态不适用于本次收货地址对应的配送仓。
    // 不能提前丢弃行；统一由选仓后的结算预览给出可售性及原因。
    items.value = selected;
  }

  function payloadItems() {
    return items.value.map((item) => ({ skuId: item.skuId, quantity: item.quantity }));
  }

  /** 结算预览：金额与价格指纹都来自服务端 */
  async function refreshPreview() {
    const seq = ++previewSeq;
    // 每次重新预览先作废旧结果：失败时不能继续沿用上一次的金额与指纹
    previewReady.value = false;
    fingerprint.value = '';
    unavailableItems.value = [];
    promotion.value = null;
    promotionError.value = '';
    if (!items.value.length) {
      estimatedAmount.value = '0.00';
      promotionError.value = '没有可结算的商品，请返回购物车调整';
      previewing.value = false;
      return;
    }
    previewing.value = true;
    try {
      const result = await previewCheckout(
        {
          items: payloadItems(),
          addressId: address.value ? address.value.addressId : null,
          warehouseId: warehouseId.value || undefined,
          couponInstanceId: selectedCouponId.value || undefined,
        },
        couponMode.value,
        () => seq === previewSeq
      );
      // 只认最后一次预览：旧响应后到一律丢弃，否则会把新金额与指纹覆盖回旧的
      if (seq !== previewSeq) return;
      const data = result.data || {};
      selectedCouponId.value = result.couponId;
      selectedCouponName.value = result.couponName;
      couponNotice.value = result.couponNotice;
      if (result.couponNotice) couponMode.value = 'NONE';
      warehouseOptions.value = data.warehouses || warehouseOptions.value;
      warehouseId.value = data.warehouseId || null;
      warehouseError.value = data.warehouseRequired ? '当前收货地址暂无可用配送仓，请联系商家' : '';
      const nextItems = data.items || items.value;
      items.value = nextItems;
      // 服务端判定不可售的项：金额已被排除，必须同时禁止提交，不能只看金额
      unavailableItems.value = nextItems.filter((item) => !isAvailable(item));
      estimatedAmount.value = data.estimatedAmount || '0.00';
      promotion.value = data.promotion || null;
      promotionError.value = data.promotionError || '';
      fingerprint.value = data.priceFingerprint || '';
      if (!fingerprint.value && !promotionError.value) promotionError.value = '订单金额资料不完整，请点击重试';
      previewReady.value = !!fingerprint.value && !promotionError.value;
      syncIdempotencyKey();
    } catch (error) {
      if (seq !== previewSeq) return;
      xsySentry.captureError(error);
      estimatedAmount.value = '';
      promotionError.value = error?.message || '订单金额加载失败，点击重试';
    } finally {
      // 旧请求不得把仍在进行中的最新预览标记成已结束
      if (seq === previewSeq) previewing.value = false;
    }
  }

  function contentSignature() {
    return JSON.stringify({
      items: payloadItems(),
      addressId: address.value ? address.value.addressId : null,
      warehouseId: warehouseId.value,
      remark: remark.value,
      deliveryDate: deliveryDate.value,
      couponInstanceId: selectedCouponId.value,
    });
  }

  /** 内容变化才换防重复请求编号；网络重试复用旧键，避免生成重复订单 */
  function syncIdempotencyKey() {
    const signature = contentSignature();
    if (signature !== lastSignature) {
      lastSignature = signature;
      idempotencyKey.value = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    }
  }

  function onDateChange(event) {
    deliveryDate.value = event.detail.value || '';
    syncIdempotencyKey();
    return refreshPreview();
  }

  function chooseAddress() {
    uni.navigateTo({ url: '/pages-sub/address/list?select=1' });
  }

  function chooseCoupon() {
    uni.navigateTo({
      url: '/pages-sub/promotion/my?select=1',
      events: {
        couponSelected: (coupon) => {
          couponMode.value = 'MANUAL';
          selectedCouponId.value = coupon.id;
          selectedCouponName.value = coupon.name;
          syncIdempotencyKey();
          refreshPreview();
        },
      },
    });
  }

  function clearCoupon() {
    couponMode.value = 'NONE';
    couponNotice.value = '';
    selectedCouponId.value = null;
    selectedCouponName.value = '';
    syncIdempotencyKey();
    return refreshPreview();
  }

  async function submit() {
    if (submitting.value || preparing.value || loadError.value) return;
    if (!items.value.length) {
      uni.showToast({ title: '没有可结算的商品', icon: 'none' });
      return;
    }
    if (!address.value) {
      uni.showToast({ title: '请选择收货地址', icon: 'none' });
      return;
    }
    if (!warehouseId.value) {
      uni.showToast({ title: warehouseError.value || '请选择配送仓', icon: 'none' });
      return;
    }
    if (!previewReady.value || !fingerprint.value) {
      uni.showToast({ title: promotionError.value || '订单金额获取失败，请点击金额处重试', icon: 'none' });
      return;
    }
    if (unavailableItems.value.length) {
      uni.showToast({ title: '有商品已不可售，请返回购物车调整', icon: 'none' });
      return;
    }
    submitting.value = true;
    try {
      syncIdempotencyKey();
      const response = await mallOrderApi.submit(
        {
          items: payloadItems(),
          addressId: address.value.addressId,
          warehouseId: warehouseId.value,
          priceFingerprint: fingerprint.value,
          remark: remark.value || undefined,
          expectDeliveryDate: deliveryDate.value || undefined,
          couponInstanceId: selectedCouponId.value || undefined,
        },
        idempotencyKey.value
      );
      const order = response.data || {};
      // 成功页与结算栏保持同一套规则：订货金额是订单记录，优惠与应付取自下单前最后一次成功试算，
      // 成功页会标注「优惠以订单确认为准」，避免把试算值当成已保存结果
      const discount = promotion.value?.discountAmount || '';
      const payable = promotion.value?.payableAmount || '';
      uni.redirectTo({
        url: `/pages-sub/checkout/success?orderId=${order.orderId || ''}&orderNo=${encodeURIComponent(order.orderNo || '')}&amount=${
          order.orderedTotalAmount || ''
        }&discount=${discount}&payable=${payable}`,
      });
    } catch (error) {
      xsySentry.captureError(error);
      // 变价必须让客户重新确认，不做静默重试
      if (error?.code === 40985) {
        uni.showModal({
          title: '价格已变化',
          content: '商品价格已更新，请确认新的结算金额后重新提交。',
          showCancel: false,
          success: () => refreshPreview(),
        });
        return;
      }
      uni.showToast({ title: error?.message || '提交失败，请重试', icon: 'none' });
    } finally {
      submitting.value = false;
    }
  }
</script>

<style lang="scss" scoped>
  .co {
    @include viewport-min-height;
    background-color: $color-bg-page;
    /* 固定操作栏之外保留阅读间距，安全区单独相加 */
    @include content-clear-bottom(68px, $space-4);
  }
  .co--editing {
    padding-bottom: $space-4;
  }
  .co-card {
    margin-top: $space-3;
    background-color: $color-bg-card;
    padding: $space-4;
  }
  .co-card__title {
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
    color: $color-text-primary;
  }

  .co-item {
    display: flex;
    align-items: center;
    gap: $space-3;
    padding: $space-3 0;
    @include hairline-bottom;
  }
  .co-item__thumb {
    flex-shrink: 0;
    width: 112rpx;
    height: 112rpx;
    overflow: hidden;
    border-radius: $radius-md;
    background-color: $color-bg-hover;
  }
  .co-item__image {
    display: block;
    width: 100%;
    height: 100%;
  }
  .co-item__thumb-fallback {
    width: 100%;
    height: 100%;
    @include flex-center;
    color: $color-text-tertiary;
    font-size: $font-size-xl;
  }
  .co-item__main {
    flex: 1;
    min-width: 0;
  }
  .co-item__name {
    display: block;
    font-size: $font-size-base;
    color: $color-text-primary;
    @include ellipsis(2);
  }
  .co-item__spec {
    display: block;
    margin-top: $space-1;
    font-size: $font-size-xs;
    line-height: 1.5;
    color: $color-text-tertiary;
  }
  .co-item__price-row {
    display: flex;
    align-items: baseline;
    gap: $space-1;
    margin-top: $space-1;
  }
  .co-item__price {
    color: $color-danger;
    font-size: $font-size-base;
  }
  .co-item__unit,
  .co-item__qty {
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }
  .co-item__amount {
    flex-shrink: 0;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
    color: $color-text-primary;
  }
  .co-tip {
    margin-top: $space-3;
    font-size: $font-size-sm;
    line-height: 1.6;
    color: $color-text-secondary;
  }
  .co-alert {
    margin-top: $space-3;
    padding: $space-3 $space-4;
    background-color: $color-warning-light;
    color: $color-warning;
    font-size: $font-size-sm;
    line-height: 1.6;
  }
  .co-alert__text {
    display: block;
  }
  .co-alert__action {
    display: inline-block;
    margin-top: $space-1;
    color: var(--mall-primary-ink, #12833c);
    font-weight: $font-weight-medium;
  }

  .co-address {
    position: relative;
    padding-right: $space-8;
  }
  .co-address__head {
    @include flex-start;
  }
  .co-address__name {
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
    color: $color-text-primary;
  }
  .co-address__phone {
    margin-left: $space-3;
    font-size: $font-size-base;
    color: $color-text-secondary;
  }
  .co-address__detail {
    display: block;
    margin-top: $space-2;
    font-size: $font-size-sm;
    color: $color-text-secondary;
  }
  .co-address__empty {
    font-size: $font-size-base;
    color: $color-text-placeholder;
  }
  .co-address__arrow {
    position: absolute;
    right: $space-4;
    top: 50%;
    transform: translateY(-50%);
  }

  .co-row {
    @include flex-between;
  }
  .co-row__label {
    font-size: $font-size-base;
    color: $color-text-primary;
  }
  .co-row__value {
    font-size: $font-size-base;
    color: $color-text-primary;
  }
  .co-row__value--placeholder {
    color: $color-text-placeholder;
  }
  .co-coupon__clear {
    display: block;
    margin-top: $space-3;
    color: $color-text-tertiary;
    font-size: $font-size-sm;
    text-align: right;
  }
  .co-coupon__error {
    display: block;
    margin-top: $space-3;
    color: $color-danger;
    font-size: $font-size-sm;
  }
  .co-coupon__hint {
    display: block;
    margin-top: $space-3;
    color: var(--mall-primary-ink, #12833c);
    font-size: $font-size-sm;
  }
  .co-promotion .co-row + .co-row {
    margin-top: $space-3;
  }
  .co-promotion__discount {
    color: $color-danger;
  }
  .co-promotion__detail {
    display: block;
    margin-top: $space-2;
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .co-promotion__note {
    display: block;
    margin-top: $space-3;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }

  .co-remark {
    width: 100%;
    min-height: 96rpx;
    margin-top: $space-2;
    font-size: $font-size-base;
    color: $color-text-primary;
  }

  .co-bar {
    @include fixed-bottom-bar;
    @include flex-between;
    @include action-bar-spacing;
    min-height: 44px;
    gap: $space-3;
  }
  .co-bar__amount {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    min-width: 0;
    flex: 1;
  }
  .co-bar__label {
    font-size: $font-size-sm;
    color: $color-text-secondary;
  }
  .co-bar__value {
    margin-top: $space-1;
    font-size: $font-size-xl;
    font-weight: $font-weight-bold;
    color: $color-danger;
  }
  .co-bar__hint {
    margin-left: $space-1;
    font-size: $font-size-sm;
    color: $color-text-tertiary;
  }
  .co-bar__error {
    margin-left: $space-1;
    font-size: $font-size-sm;
    color: $color-danger;
  }
  .co-bar__submit {
    flex-shrink: 0;
    padding: 0 $space-6;
    height: 44px;
    @include flex-center;
    border-radius: $radius-pill;
    background: var(--mall-action-gradient, var(--mall-primary, #16a34a));
    color: white;
    font-size: $font-size-base;
  }
  .co-bar__submit--disabled {
    background: var(--mall-primary-disabled, #96d6ae);
  }
</style>
