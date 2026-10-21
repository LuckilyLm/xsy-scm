<template>
  <view class="addr-edit" :style="themeStyle">
    <view class="addr-edit__card">
      <!-- #ifdef MP-WEIXIN -->
      <view class="addr-edit__wechat" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="fillFromWechatAddress">
        <uni-icons type="contact" :size="16" :color="COLOR_PRIMARY" />
        <text class="addr-edit__wechat-text">使用微信收货地址</text>
        <text class="addr-edit__wechat-hint">自动填入姓名、电话、详细地址</text>
      </view>
      <!-- #endif -->
      <view class="field">
        <text class="field__label">收货人</text>
        <input
          class="field__input"
          v-model="form.receiverName"
          :maxlength="64"
          placeholder="请输入收货人姓名"
          confirm-type="next"
          :cursor-spacing="24"
          hold-keyboard
        />
      </view>
      <view class="field">
        <text class="field__label">联系电话</text>
        <!-- 座机含区号/分机与 +，需要文本键盘；用 type="number" 会打不出这些字符，与校验规则冲突 -->
        <input
          class="field__input"
          type="text"
          v-model="form.receiverPhone"
          :maxlength="32"
          placeholder="手机或座机号码"
          confirm-type="next"
          :cursor-spacing="24"
          hold-keyboard
        />
      </view>
      <picker mode="region" :value="region" @change="onRegionChange">
        <view class="field">
          <text class="field__label">所在地区</text>
          <text class="field__value" :class="{ 'field__value--placeholder': !region.length }">
            {{ region.length ? region.join(' ') : '请选择省 / 市 / 区' }}
          </text>
        </view>
      </picker>
      <view class="field">
        <text class="field__label">搜索位置</text>
        <input
          class="field__input"
          v-model="searchKeyword"
          placeholder="小区、楼宇或街道"
          confirm-type="search"
          :cursor-spacing="24"
          hold-keyboard
          @confirm="searchPlaces"
        />
        <text class="field__link" @click="searchPlaces">搜索</text>
      </view>
      <view v-if="searching" class="addr-edit__map-hint">正在搜索高德地点…</view>
      <view v-for="place in places" :key="`${place.longitude}-${place.latitude}-${place.name}`" class="addr-edit__place" @click="selectPlace(place)">
        <text>{{ place.name }}</text
        ><text class="addr-edit__place-address">{{ place.address }}</text>
      </view>
      <map id="address-map" class="addr-edit__map" :latitude="mapLatitude" :longitude="mapLongitude" :scale="15" :markers="markers"></map>
      <view class="addr-edit__map-actions">
        <text @click="chooseMapCenter">选取地图中心</text>
        <text @click="chooseCurrentLocation">使用当前位置</text>
      </view>
      <view class="addr-edit__map-hint">
        {{ form.longitude == null ? '请搜索地点或移动地图后选取中心点' : `已选 GCJ-02 坐标：${form.longitude}, ${form.latitude}` }}
      </view>
      <view class="field field--textarea">
        <text class="field__label">详细地址</text>
        <textarea
          class="field__textarea"
          v-model="form.detailAddress"
          :maxlength="255"
          placeholder="街道、门牌号、楼层等"
          auto-height
          :cursor-spacing="96"
          show-confirm-bar
          hold-keyboard
        ></textarea>
      </view>
      <view class="field field--switch">
        <text class="field__label">设为默认地址</text>
        <switch :checked="form.isDefault" :color="theme.primary" @change="onDefaultChange" />
      </view>
    </view>

    <view class="addr-edit__bar" :style="barStyle">
      <view class="addr-edit__save" :class="{ 'addr-edit__save--disabled': saving }" @click="save">
        {{ saving ? '保存中…' : '保存' }}
      </view>
    </view>
  </view>
</template>

<script setup>
  import { computed, reactive, ref } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';

  import { onLoad } from '@dcloudio/uni-app';
  import { mallAddressApi } from '@/api/mall/address-api';
  import { mallMapApi } from '@/api/mall/map-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { useKeyboardHeight } from '@/composables/use-keyboard-height';
  import { getCurrentLocation, requestLocationPermission } from '@/composables/use-location-permission';
  import { guideToSetting } from '@/composables/use-permission';
  import { COLOR_PRIMARY } from '@/constants/theme-color-const';

  const { theme, themeStyle } = useMallTheme();
  /** 键盘弹出时把保存栏抬到键盘上沿，避免被遮住 */
  const { barStyle } = useKeyboardHeight();

  const addressId = ref(null);
  const region = ref([]);
  const regionCodes = ref([]);
  const searchKeyword = ref('');
  const searching = ref(false);
  const places = ref([]);
  const mapLatitude = ref(35);
  const mapLongitude = ref(105);
  const saving = ref(false);
  const form = reactive({
    receiverName: '',
    receiverPhone: '',
    detailAddress: '',
    longitude: null,
    latitude: null,
    isDefault: false,
  });
  const markers = computed(() =>
    form.longitude == null
      ? []
      : [
          {
            id: 1,
            longitude: Number(form.longitude),
            latitude: Number(form.latitude),
          },
        ]
  );

  onLoad(async (options) => {
    const id = options?.id ? Number(options.id) : null;
    if (!id) return;
    addressId.value = id;
    try {
      const response = await mallAddressApi.getDetail(id);
      const data = response.data || {};
      form.receiverName = data.receiverName || '';
      form.receiverPhone = data.receiverPhone || '';
      form.detailAddress = data.detailAddress || '';
      form.isDefault = data.isDefault === true;
      region.value = [data.provinceName, data.cityName, data.districtName].filter(Boolean);
      regionCodes.value = [data.provinceCode, data.cityCode, data.districtCode].filter(Boolean);
      form.longitude = data.longitude ?? null;
      form.latitude = data.latitude ?? null;
      if (form.longitude != null && form.latitude != null) {
        mapLongitude.value = Number(form.longitude);
        mapLatitude.value = Number(form.latitude);
      }
    } catch (error) {
      smartSentry.captureError(error);
    }
  });

  function onRegionChange(event) {
    region.value = event.detail.value || [];
    regionCodes.value = event.detail.code || [];
    form.longitude = null;
    form.latitude = null;
    places.value = [];
  }

  async function searchPlaces() {
    if (!searchKeyword.value.trim() || searching.value) return;
    searching.value = true;
    try {
      const response = await mallMapApi.search(searchKeyword.value.trim(), region.value[1]);
      places.value = response.data || [];
      if (!places.value.length) uni.showToast({ title: '未找到地点，请换个关键词', icon: 'none' });
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '地点搜索失败', icon: 'none' });
    } finally {
      searching.value = false;
    }
  }

  async function selectCoordinates(longitude, latitude) {
    try {
      const response = await mallMapApi.reverse(longitude, latitude);
      const place = response.data;
      if (!place?.districtCode) throw new Error('该位置没有可用的行政区信息');
      form.longitude = place.longitude;
      form.latitude = place.latitude;
      mapLongitude.value = Number(place.longitude);
      mapLatitude.value = Number(place.latitude);
      region.value = [place.provinceName, place.cityName, place.districtName];
      regionCodes.value = [place.provinceCode, place.cityCode, place.districtCode];
      places.value = [];
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '位置解析失败', icon: 'none' });
    }
  }

  function selectPlace(place) {
    void selectCoordinates(place.longitude, place.latitude);
  }

  function chooseMapCenter() {
    uni.createMapContext('address-map').getCenterLocation({
      success: ({ longitude, latitude }) => {
        void selectCoordinates(longitude, latitude);
      },
      fail: () => uni.showToast({ title: '暂时无法读取地图中心', icon: 'none' }),
    });
  }

  /**
   * 读取微信收货地址。
   *
   * 只回填姓名、电话与详细地址：微信地址不带 GCJ-02 坐标，行政区与坐标
   * 必须由地图选点产生，否则保存时会被服务端重新解析拒绝。
   */
  function fillFromWechatAddress() {
    uni.chooseAddress({
      success: (res) => {
        form.receiverName = res.userName || form.receiverName;
        form.receiverPhone = res.telNumber || form.receiverPhone;
        form.detailAddress = res.detailInfo || form.detailAddress;
        uni.showToast({ title: '已填入，请继续在地图上选择位置', icon: 'none' });
      },
      fail: (error) => {
        if (/auth|deny|denied/i.test(String(error?.errMsg || ''))) {
          guideToSetting('scope.address', {
            title: '需要收货地址授权',
            content: '开启后才能读取微信收货地址，是否前往设置开启？',
          }).then((granted) => {
            if (granted) fillFromWechatAddress();
          });
          return;
        }
        uni.showToast({ title: '未能读取微信收货地址', icon: 'none' });
      },
    });
  }

  async function chooseCurrentLocation() {
    const allowed = await requestLocationPermission();
    if (!allowed) {
      uni.showToast({ title: '未开启定位权限，可在右上角设置中打开', icon: 'none' });
      return;
    }
    try {
      const { longitude, latitude } = await getCurrentLocation();
      void selectCoordinates(longitude, latitude);
    } catch (error) {
      // 权限已给但系统定位关闭、超时或无网络时，getLocation 仍会失败
      const denied = /auth|deny|denied/i.test(String(error?.errMsg || ''));
      uni.showToast({ title: denied ? '定位权限未开启' : '定位失败，请检查系统定位开关', icon: 'none' });
    }
  }
  function onDefaultChange(event) {
    form.isDefault = event.detail.value === true;
  }

  function validate() {
    if (!form.receiverName.trim()) return '请输入收货人姓名';
    // 允许手机号与座机（含区号、分机分隔符），只做长度与字符校验
    if (!/^[\d\-\s+]{6,32}$/.test(form.receiverPhone.trim())) return '请输入正确的联系电话';
    if (!region.value.length) return '请选择所在地区';
    if (!regionCodes.value[2]) return '请选择有效的区县位置';
    if (form.longitude == null || form.latitude == null) return '请在地图上选择收货位置';
    if (!form.detailAddress.trim()) return '请输入详细地址';
    return '';
  }

  async function save() {
    if (saving.value) return;
    const error = validate();
    if (error) {
      uni.showToast({ title: error, icon: 'none' });
      return;
    }
    saving.value = true;
    const payload = {
      receiverName: form.receiverName.trim(),
      receiverPhone: form.receiverPhone.trim(),
      provinceName: region.value[0] || null,
      cityName: region.value[1] || null,
      districtName: region.value[2] || null,
      provinceCode: regionCodes.value[0] || null,
      cityCode: regionCodes.value[1] || null,
      districtCode: regionCodes.value[2] || null,
      detailAddress: form.detailAddress.trim(),
      longitude: form.longitude,
      latitude: form.latitude,
      isDefault: form.isDefault,
    };
    try {
      if (addressId.value) {
        await mallAddressApi.update(addressId.value, payload);
      } else {
        await mallAddressApi.create(payload);
      }
      uni.showToast({ title: '已保存', icon: 'none' });
      setTimeout(() => uni.navigateBack(), 400);
    } catch (err) {
      smartSentry.captureError(err);
      uni.showToast({ title: err?.message || '保存失败', icon: 'none' });
    } finally {
      saving.value = false;
    }
  }
</script>

<style lang="scss" scoped>
  .addr-edit {
    @include viewport-min-height;
    background-color: $color-bg-page;
    padding-bottom: 140rpx;
  }
  .addr-edit__card {
    margin-top: $space-3;
    background-color: $color-bg-card;
  }
  /* 微信收货地址入口：放在表单最上方，填完仍需地图选点确认坐标 */
  .addr-edit__wechat {
    @include flex-start;
    gap: $space-2;
    padding: $space-3 $space-4;
    @include hairline-bottom;
  }
  .addr-edit__wechat-text {
    color: var(--mall-primary-ink, #12833c);
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
  }
  .addr-edit__wechat-hint {
    margin-left: auto;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .field {
    @include flex-between;
    padding: $space-3 $space-4;
    @include hairline-bottom;
  }
  .field__label {
    flex-shrink: 0;
    width: 180rpx;
    font-size: $font-size-base;
    color: $color-text-primary;
  }
  .field__input {
    flex: 1;
    text-align: right;
    font-size: $font-size-base;
    color: $color-text-primary;
  }
  .field__value {
    flex: 1;
    text-align: right;
    font-size: $font-size-base;
    color: $color-text-primary;
  }
  .field__value--placeholder {
    color: $color-text-placeholder;
  }
  .field--textarea {
    align-items: flex-start;
  }
  .field__textarea {
    flex: 1;
    min-height: 80rpx;
    text-align: right;
    font-size: $font-size-base;
    color: $color-text-primary;
  }
  .field--switch {
    align-items: center;
  }

  .addr-edit__bar {
    @include fixed-bottom-bar;
    @include flex-center;
    height: 108rpx;
    padding: 0 $space-4;
  }
  .addr-edit__save {
    width: 100%;
    height: 80rpx;
    @include flex-center;
    border-radius: $radius-pill;
    background-color: var(--mall-primary, #16a34a);
    color: white;
    font-size: $font-size-base;
  }
  .addr-edit__save--disabled {
    background-color: var(--mall-primary-disabled, #a7d8b9);
  }
  .field__link {
    margin-left: $space-3;
    color: var(--mall-primary-ink, #12833c);
  }
  .addr-edit__map {
    width: 100%;
    height: 420rpx;
  }
  .addr-edit__map-actions {
    display: flex;
    justify-content: space-around;
    padding: $space-3;
    color: var(--mall-primary-ink, #12833c);
  }
  .addr-edit__map-hint {
    padding: $space-2 $space-4;
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .addr-edit__place {
    padding: $space-2 $space-4;
    @include hairline-bottom;
  }
  .addr-edit__place-address {
    display: block;
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
</style>
