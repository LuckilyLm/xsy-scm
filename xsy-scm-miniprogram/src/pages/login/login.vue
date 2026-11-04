<template>
  <view class="login" :style="themeStyle">
    <image class="login__scene" :src="LOGIN_IMAGES.hero" mode="widthFix" aria-hidden="true" />
    <view class="login__panel">
      <image class="login__logo" :src="LOGIN_IMAGES.logo" mode="aspectFit" alt="鲜蔬源SCM" />
      <view class="login__heading">
        <view class="login__heading-line" />
        <text class="login__title">{{ storeName }}</text>
        <view class="login__heading-line login__heading-line--end" />
      </view>
      <text class="login__subtitle">客户专属商品与价格</text>
      <view class="field">
        <image class="field__icon" :src="LOGIN_IMAGES.phone" mode="aspectFit" aria-hidden="true" />
        <input
          id="login-phone"
          class="field__input"
          type="number"
          maxlength="11"
          placeholder="请输入管理员开通的手机号"
          placeholder-class="field__placeholder"
          v-model="form.phone"
          confirm-type="next"
          :cursor-spacing="100"
          aria-label="手机号"
          @confirm="focusPassword"
        />
      </view>
      <view class="field">
        <image class="field__icon" :src="LOGIN_IMAGES.lock" mode="aspectFit" aria-hidden="true" />
        <input
          id="login-password"
          class="field__input"
          :password="!passwordVisible"
          maxlength="64"
          placeholder="请输入密码"
          placeholder-class="field__placeholder"
          v-model="form.password"
          confirm-type="done"
          :cursor-spacing="100"
          aria-label="密码"
          :focus="passwordFocused"
          @confirm="submit"
          @blur="passwordFocused = false"
        />
        <button
          class="field__toggle"
          :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
          :aria-pressed="passwordVisible"
          hover-class="xsy-press--active"
          @click="passwordVisible = !passwordVisible"
        >
          <uni-icons v-if="passwordVisible" type="eye" :size="21" :color="theme.ink" />
          <image v-else class="field__eye" :src="LOGIN_IMAGES.eyeClosed" mode="aspectFit" aria-hidden="true" />
        </button>
      </view>
      <button
        class="login__submit"
        :class="{'login__submit--disabled': submitting}"
        :disabled="submitting"
        :loading="submitting"
        hover-class="xsy-press-button--active"
        :hover-stay-time="80"
        @click="submit"
      >
        <text>{{ submitting ? '登录中…' : '登录' }}</text>
      </button>
      <view class="login__help">
        <image class="login__help-icon" :src="LOGIN_IMAGES.info" mode="aspectFit" aria-hidden="true" />
        <text>账号由鲜蔬源后台管理员开通。如无法登录，请联系业务员。</text>
      </view>
    </view>
    <image class="login__waves" :src="LOGIN_IMAGES.waves" mode="widthFix" aria-hidden="true" />
  </view>
</template>

<script setup>
import {reactive, ref} from 'vue';
import {mallAuthApi} from '@/api/mall';
import {useUserStore} from '@/store/modules/system/user';
import {useMallTheme} from '@/composables/use-mall-theme';
import {useMallStorefront} from '@/composables/use-mall-storefront';
import {smartSentry} from '@/lib/smart-sentry';

const {themeStyle, theme} = useMallTheme();
const {storeName} = useMallStorefront();

// 登录拆分图来自交付参考包，表单和登录交互仍使用原生控件。
const LOGIN_IMAGES = {
  hero: '/static/mall-login/hero.png',
  logo: '/static/mall-login/logo.png',
  waves: '/static/mall-login/waves.png',
  phone: '/static/mall-login/phone.png',
  lock: '/static/mall-login/lock.png',
  info: '/static/mall-login/info.png',
  eyeClosed: '/static/mall-login/eye-closed.png',
};

const submitting = ref(false);
const form = reactive({phone: '', password: ''});
/** 手机号键盘点「下一项」时把焦点交给密码框，省一次点击 */
const passwordFocused = ref(false);
const passwordVisible = ref(false);
function focusPassword() {
  passwordFocused.value = true;
}

async function submit() {
  if (submitting.value) return;
  if (!/^1\d{10}$/.test(form.phone)) {
    uni.showToast({title: '请输入正确的手机号', icon: 'none'});
    return;
  }
  if (!form.password) {
    uni.showToast({title: '请输入密码', icon: 'none'});
    return;
  }
  submitting.value = true;
  uni.showLoading({title: '登录中'});
  try {
    const response = await mallAuthApi.login({phone: form.phone, password: form.password});
    const userStore = useUserStore();
    userStore.setUserLoginInfo(response.data);
    if (userStore.passwordChangeRequired) {
      uni.redirectTo({url: '/pages/account/password'});
    } else {
      uni.switchTab({url: '/pages/home/index'});
    }
  } catch (error) {
    smartSentry.captureError(error);
  } finally {
    submitting.value = false;
    uni.hideLoading();
  }
}
</script>

<style lang="scss" scoped>
.login {
  @include viewport-min-height;
  position: relative;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  overflow: hidden;
  padding-top: 376rpx;
  padding-bottom: max(164rpx, env(safe-area-inset-bottom));
  background: linear-gradient(180deg, $color-login-sky 0%, $color-login-ground 48%, $color-bg-card 85%);
}
.login__scene {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  pointer-events: none;
}
.login__panel {
  position: relative;
  z-index: 1;
  box-sizing: border-box;
  width: calc(100% - 72rpx);
  max-width: 480px;
  padding: 36rpx 28rpx 44rpx;
  border-radius: $radius-login-panel;
  background: $color-bg-card;
  box-shadow: 0 16px 40px rgba(22, 80, 45, 0.08);
}
.login__logo {
  display: block;
  width: 392rpx;
  height: 118rpx;
  margin: 0 auto;
}
.login__heading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $space-5;
  margin-top: 20rpx;
}
.login__heading-line {
  flex: 1;
  max-width: 84rpx;
  height: 2px;
  background: linear-gradient(90deg, $color-bg-card, var(--mall-primary-disabled, #a7d8b9));
}
.login__heading-line--end {
  transform: rotate(180deg);
}
.login__title {
  max-width: 72%;
  font-size: 44rpx;
  line-height: 1.4;
  font-weight: $font-weight-bold;
  color: var(--mall-primary-ink, #12833c);
  text-align: center;
  overflow-wrap: anywhere;
}
.login__subtitle {
  display: block;
  margin: 6rpx 0 46rpx;
  font-size: $font-size-base;
  line-height: 1.6;
  color: $color-text-secondary;
  text-align: center;
}
.field {
  box-sizing: border-box;
  min-height: 44px;
  margin-top: 18rpx;
  padding: 0 $space-3;
  gap: $space-2;
  @include flex-start;
  border-radius: $radius-lg;
  background: $color-login-field;
}
.field__icon {
  flex-shrink: 0;
  width: 44rpx;
  height: 44rpx;
}
.field__input {
  flex: 1;
  min-width: 0;
  height: 88rpx;
  min-height: 44px;
  font-size: $font-size-base;
  color: $color-text-primary;
  caret-color: var(--mall-primary-ink, #12833c);
}
.field__placeholder {
  color: $color-text-placeholder;
}
.field__toggle {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  min-width: 44px;
  min-height: 44px;
  margin: 0 -8rpx 0 0;
  padding: 0;
  border-radius: $radius-md;
  background: transparent;
  line-height: 1;
}
.field__toggle::after,
.login__submit::after {
  border: 0;
}
.field__eye {
  width: 38rpx;
  height: 38rpx;
}
.login__submit {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $space-2;
  width: 100%;
  margin-top: 44rpx;
  height: 88rpx;
  min-height: 44px;
  padding: 0;
  border-radius: $radius-lg;
  background: linear-gradient(118deg, var(--mall-primary, #16a34a) 0%, var(--mall-primary-dark, #12863d) 100%);
  color: $color-text-inverse;
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
  line-height: 1;
}
.login__submit--disabled[disabled] {
  background: var(--mall-primary-disabled, #a7d8b9);
  color: $color-text-inverse;
}
.login__help {
  display: flex;
  align-items: flex-start;
  gap: $space-1;
  margin-top: 36rpx;
  font-size: $font-size-xs;
  line-height: 1.6;
  color: $color-text-secondary;
}
.login__help-icon {
  flex-shrink: 0;
  width: 28rpx;
  height: 28rpx;
  margin-top: 2rpx;
}
.login__waves {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  pointer-events: none;
}
@media (max-width: 359px) {
  .login {
    padding-top: 352rpx;
  }
  .login__panel {
    width: calc(100% - 48rpx);
    padding-right: 24rpx;
    padding-left: 24rpx;
  }
}
</style>
