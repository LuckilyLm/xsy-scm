<template>
  <view class="login" :style="themeStyle">
    <view class="login__hero">
      <text class="login__mark">鲜</text>
      <text class="login__title">鲜蔬源商城</text>
      <text class="login__subtitle">客户专属商品与价格</text>
    </view>
    <view class="login__panel">
      <view class="field">
        <text class="field__label">手机号</text>
        <input class="field__input" type="number" maxlength="11" placeholder="请输入管理员开通的手机号" v-model="form.phone" />
      </view>
      <view class="field">
        <text class="field__label">密码</text>
        <input class="field__input" :password="true" placeholder="请输入密码" v-model="form.password" />
      </view>
      <view class="login__submit" :class="{ 'login__submit--disabled': submitting }" @click="submit">
        {{ submitting ? '登录中…' : '登录' }}
      </view>
      <text class="login__help">账号由鲜蔬源后台管理员开通。如无法登录，请联系业务员。</text>
    </view>
  </view>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import { mallAuthApi } from '@/api/mall';
  import { useUserStore } from '@/store/modules/system/user';
  import { useMallTheme } from '@/composables/use-mall-theme';
  import { smartSentry } from '@/lib/smart-sentry';

  const { themeStyle } = useMallTheme();

  const submitting = ref(false);
  const form = reactive({ phone: '', password: '' });

  async function submit() {
    if (submitting.value) return;
    if (!/^1\d{10}$/.test(form.phone)) {
      uni.showToast({ title: '请输入正确的手机号', icon: 'none' });
      return;
    }
    if (!form.password) {
      uni.showToast({ title: '请输入密码', icon: 'none' });
      return;
    }
    submitting.value = true;
    uni.showLoading({ title: '登录中' });
    try {
      const response = await mallAuthApi.login({ phone: form.phone, password: form.password });
      const userStore = useUserStore();
      userStore.setUserLoginInfo(response.data);
      if (userStore.passwordChangeRequired) {
        uni.redirectTo({ url: '/pages/account/password' });
      } else {
        uni.switchTab({ url: '/pages/home/index' });
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
    min-height: 100vh;
    background: $color-bg-card;
  }
  .login__hero {
    @include flex-center;
    flex-direction: column;
    padding: 160rpx 0 80rpx;
  }
  .login__mark {
    width: 144rpx;
    height: 144rpx;
    border-radius: 36rpx;
    background: var(--mall-primary, #16a34a);
    color: white;
    font-size: 72rpx;
    font-weight: $font-weight-bold;
    text-align: center;
    line-height: 144rpx;
  }
  .login__title {
    margin-top: $space-4;
    font-size: $font-size-xl;
    font-weight: $font-weight-bold;
    color: $color-text-primary;
  }
  .login__subtitle {
    margin-top: $space-1;
    font-size: $font-size-sm;
    color: $color-text-tertiary;
  }
  .login__panel {
    padding: 0 $space-6;
  }
  .field {
    height: 104rpx;
    @include flex-start;
    @include hairline-bottom;
  }
  .field__label {
    width: 120rpx;
    font-size: $font-size-base;
    color: $color-text-primary;
  }
  .field__input {
    flex: 1;
    height: 100%;
    font-size: $font-size-base;
  }
  .login__submit {
    margin-top: $space-6;
    height: 88rpx;
    border-radius: $radius-md;
    background: var(--mall-primary, #16a34a);
    color: white;
    @include flex-center;
    font-size: $font-size-lg;
  }
  .login__submit--disabled {
    background: var(--mall-primary-disabled, #a7d8b9);
  }
  .login__help {
    display: block;
    margin-top: $space-4;
    font-size: $font-size-xs;
    line-height: 1.6;
    color: $color-text-tertiary;
    text-align: center;
  }
</style>
