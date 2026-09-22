<template>
  <view class="login">
    <view class="login__hero">
      <image class="login__logo" src="@/static/images/login/login-logo.png" mode="aspectFit" />
      <text class="login__title">鲜蔬源商城</text>
      <text class="login__subtitle">今日订货，从这里开始</text>
    </view>

    <view class="login__panel">
      <!-- 手机号 + 验证码 -->
      <template v-if="mode === 'SMS'">
        <view class="field">
          <text class="field__prefix">+86</text>
          <input
            class="field__input"
            type="number"
            maxlength="11"
            placeholder="请输入手机号"
            placeholder-class="field__placeholder"
            v-model="form.phone"
          />
        </view>
        <view class="field">
          <input
            class="field__input"
            type="number"
            maxlength="6"
            placeholder="请输入短信验证码"
            placeholder-class="field__placeholder"
            v-model="form.smsCode"
          />
          <text class="field__action" :class="{ 'field__action--disabled': counting }" @click="sendSmsCode">
            {{ counting ? `${countdown}s 后重发` : '获取验证码' }}
          </text>
        </view>
      </template>

      <!-- 账号密码（AppID 未就绪时的兜底路径） -->
      <template v-else>
        <view class="field">
          <input class="field__input" placeholder="请输入账号" placeholder-class="field__placeholder" v-model="form.loginName" />
        </view>
        <view class="field">
          <input class="field__input" :password="true" placeholder="请输入密码" placeholder-class="field__placeholder" v-model="form.password" />
        </view>
      </template>

      <view class="login__submit" :class="{ 'login__submit--disabled': submitting }" @click="submit">
        {{ submitting ? '登录中…' : '登 录' }}
      </view>

      <view class="login__switch" @click="switchMode">
        {{ mode === 'SMS' ? '使用账号密码登录' : '使用手机号验证码登录' }}
      </view>

      <!-- #ifdef MP-WEIXIN -->
      <view class="login__wechat" @click="wechatLogin">
        <image class="login__wechat-icon" src="@/static/images/login/wx-icon.png" mode="aspectFit" />
        <text>微信一键登录</text>
      </view>
      <!-- #endif -->

      <LoginCheckBox ref="checkBoxRef" class="login__agreement" />
    </view>
  </view>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import LoginCheckBox from './components/login-check-box.vue';
  import { mallAuthApi } from '@/api/mall';
  import { useUserStore } from '@/store/modules/system/user';
  import { smartSentry } from '@/lib/smart-sentry';

  /** SMS = 手机号验证码；PASSWORD = 账号密码（兜底） */
  const mode = ref('SMS');
  const submitting = ref(false);
  const counting = ref(false);
  const countdown = ref(60);
  const checkBoxRef = ref();
  let countdownTimer = null;

  const form = reactive({
    phone: '',
    smsCode: '',
    loginName: '',
    password: '',
  });

  function switchMode() {
    mode.value = mode.value === 'SMS' ? 'PASSWORD' : 'SMS';
  }

  function ensureAgreed() {
    if (!checkBoxRef.value?.agreeFlag) {
      uni.showToast({ title: '请阅读并同意《用户协议》与《隐私政策》', icon: 'none' });
      return false;
    }
    return true;
  }

  function startCountdown() {
    counting.value = true;
    countdown.value = 60;
    countdownTimer = setInterval(() => {
      countdown.value -= 1;
      if (countdown.value <= 0) {
        clearInterval(countdownTimer);
        countdownTimer = null;
        counting.value = false;
      }
    }, 1000);
  }

  async function sendSmsCode() {
    if (counting.value) {
      return;
    }
    if (!/^1\d{10}$/.test(form.phone)) {
      uni.showToast({ title: '请输入正确的手机号', icon: 'none' });
      return;
    }
    try {
      await mallAuthApi.sendSmsCode(form.phone);
      uni.showToast({ title: '验证码已发送', icon: 'none' });
      startCountdown();
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  /** 登录成功后的统一收口：写入会话并进入首页 */
  function afterLogin(result) {
    useUserStore().setUserLoginInfo(result);
    uni.showToast({ title: '登录成功', icon: 'none' });
    uni.switchTab({ url: '/pages/home/index' });
  }

  async function submit() {
    if (submitting.value || !ensureAgreed()) {
      return;
    }
    if (mode.value === 'SMS') {
      if (!/^1\d{10}$/.test(form.phone)) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' });
        return;
      }
      if (!form.smsCode) {
        uni.showToast({ title: '请输入短信验证码', icon: 'none' });
        return;
      }
    } else if (!form.loginName || !form.password) {
      uni.showToast({ title: '请输入账号和密码', icon: 'none' });
      return;
    }

    submitting.value = true;
    uni.showLoading({ title: '登录中' });
    try {
      const res =
        mode.value === 'SMS'
          ? await mallAuthApi.smsLogin({ phone: form.phone, code: form.smsCode })
          : await mallAuthApi.login({ loginName: form.loginName, password: form.password });
      afterLogin(res.data);
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      submitting.value = false;
      uni.hideLoading();
    }
  }

  async function wechatLogin() {
    if (submitting.value || !ensureAgreed()) {
      return;
    }
    submitting.value = true;
    uni.showLoading({ title: '登录中' });
    try {
      const loginResult = await uni.login({ provider: 'weixin' });
      const res = await mallAuthApi.wechatLogin({ code: loginResult.code });
      afterLogin(res.data);
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      submitting.value = false;
      uni.hideLoading();
    }
  }
</script>

<style lang="scss" scoped>
  .login {
    min-height: 100vh;
    background-color: $color-bg-card;
    display: flex;
    flex-direction: column;

    &__hero {
      @include flex-center;
      flex-direction: column;
      padding: $space-8 0 $space-6;
    }

    &__logo {
      width: 160rpx;
      height: 160rpx;
    }

    &__title {
      margin-top: $space-3;
      font-size: $font-size-xl;
      font-weight: $font-weight-bold;
      color: $color-text-primary;
    }

    &__subtitle {
      margin-top: $space-1;
      font-size: $font-size-sm;
      color: $color-text-tertiary;
    }

    &__panel {
      padding: 0 $space-6;
    }

    &__submit {
      margin-top: $space-6;
      height: 88rpx;
      border-radius: $radius-md;
      background-color: $color-primary;
      color: $color-text-inverse;
      font-size: $font-size-lg;
      font-weight: $font-weight-medium;
      @include flex-center;

      &--disabled {
        background-color: $color-primary-disabled;
      }
    }

    &__switch {
      margin-top: $space-4;
      text-align: center;
      font-size: $font-size-sm;
      color: $color-text-secondary;
    }

    &__wechat {
      margin-top: $space-6;
      height: 88rpx;
      border-radius: $radius-md;
      border: 1px solid $color-border;
      color: $color-text-primary;
      font-size: $font-size-base;
      @include flex-center;

      &-icon {
        width: 40rpx;
        height: 40rpx;
        margin-right: $space-2;
      }
    }

    &__agreement {
      margin-top: $space-8;
    }
  }

  .field {
    @include flex-start;
    height: 96rpx;
    @include hairline-bottom;

    &__prefix {
      font-size: $font-size-base;
      color: $color-text-primary;
      margin-right: $space-2;
    }

    &__input {
      flex: 1;
      height: 100%;
      font-size: $font-size-base;
      color: $color-text-primary;
    }

    &__placeholder {
      color: $color-text-placeholder;
    }

    &__action {
      font-size: $font-size-sm;
      color: $color-primary;

      &--disabled {
        color: $color-text-tertiary;
      }
    }
  }
</style>
