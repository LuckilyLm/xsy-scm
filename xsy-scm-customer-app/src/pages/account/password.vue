<template>
  <view class="password-page">
    <view class="password-card">
      <text class="password-title">{{ required ? '首次登录，请修改临时密码' : '修改登录密码' }}</text>
      <text class="password-hint">新密码设置成功后，需要使用新密码重新登录。</text>
      <input class="password-input" :password="true" v-model="form.oldPassword" placeholder="当前密码" />
      <input class="password-input" :password="true" v-model="form.newPassword" placeholder="新密码" />
      <input class="password-input" :password="true" v-model="form.confirmPassword" placeholder="再次输入新密码" />
      <button class="password-submit" :loading="saving" @click="save">确认修改</button>
    </view>
  </view>
</template>

<script setup>
  import { computed, reactive, ref } from 'vue';
  import { mallAuthApi } from '@/api/mall';
  import { useUserStore } from '@/store/modules/system/user';
  import { smartSentry } from '@/lib/smart-sentry';

  const userStore = useUserStore();
  const required = computed(() => userStore.passwordChangeRequired);
  const saving = ref(false);
  const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });

  async function save() {
    if (saving.value) return;
    if (!form.oldPassword || !form.newPassword) {
      uni.showToast({ title: '请填写当前密码和新密码', icon: 'none' });
      return;
    }
    if (form.newPassword.length < 8) {
      uni.showToast({ title: '新密码至少 8 位', icon: 'none' });
      return;
    }
    if (form.newPassword !== form.confirmPassword) {
      uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' });
      return;
    }
    saving.value = true;
    try {
      await mallAuthApi.changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword });
      userStore.clearUserLoginInfo();
      uni.showToast({ title: '密码已修改，请重新登录', icon: 'none' });
      setTimeout(() => uni.reLaunch({ url: '/pages/login/login' }), 700);
    } catch (error) {
      smartSentry.captureError(error);
    } finally {
      saving.value = false;
    }
  }
</script>

<style lang="scss" scoped>
  .password-page {
    min-height: 100vh;
    padding: $space-4;
    background: $color-bg-page;
  }
  .password-card {
    @include card($space-4);
  }
  .password-title {
    display: block;
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
    color: $color-text-primary;
  }
  .password-hint {
    display: block;
    margin-top: $space-2;
    font-size: $font-size-sm;
    color: $color-text-tertiary;
  }
  .password-input {
    height: 96rpx;
    margin-top: $space-3;
    padding: 0 $space-3;
    border: 1px solid $color-border;
    border-radius: $radius-md;
    background: white;
  }
  .password-submit {
    margin-top: $space-6;
    background: $color-primary;
    color: white;
    font-size: $font-size-base;
  }
</style>
