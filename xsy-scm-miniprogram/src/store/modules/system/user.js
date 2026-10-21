import { defineStore } from 'pinia';
import { USER_TOKEN } from '@/constants/local-storage-key-const';
import { mallAuthApi } from '@/api/mall/auth-api';
import { setSessionExpiredHandler } from '@/lib/smart-request';
import { smartSentry } from '@/lib/smart-sentry';

const initialState = () => ({
  token: '',
  accountId: null,
  customerId: null,
  customerCode: '',
  customerName: '',
  contactName: '',
  phone: '',
  customerStatus: '',
  passwordChangeRequired: false,
});

export const useUserStore = defineStore({
  id: 'mallUser',
  state: initialState,
  getters: {
    isLogin: (state) => !!state.token,
    tradable: (state) => state.customerStatus === 'COOPERATING',
  },
  actions: {
    async logout() {
      try {
        await mallAuthApi.logout();
      } catch (error) {
        smartSentry.captureError(error);
      } finally {
        this.clearUserLoginInfo();
      }
    },
    clearUserLoginInfo() {
      Object.assign(this, initialState());
      uni.removeStorageSync(USER_TOKEN);
    },
    async getLoginInfo() {
      const token = uni.getStorageSync(USER_TOKEN);
      if (!token) return;
      this.token = token;
      try {
        const response = await mallAuthApi.getProfile();
        this.setUserLoginInfo({ token, profile: response.data });
        if (this.passwordChangeRequired) {
          uni.reLaunch({ url: '/pages/account/password' });
        }
      } catch (error) {
        smartSentry.captureError(error);
      }
    },
    setUserLoginInfo(data) {
      if (!data) return;
      const profile = data.profile || data;
      const token = data.token || uni.getStorageSync(USER_TOKEN) || '';
      Object.assign(this, {
        token,
        accountId: profile.accountId ?? null,
        customerId: profile.customerId ?? null,
        customerCode: profile.customerCode || '',
        customerName: profile.customerName || '',
        contactName: profile.contactName || '',
        phone: profile.loginPhone || '',
        customerStatus: profile.customerStatus || '',
        passwordChangeRequired: !!profile.firstPasswordChange,
      });
      if (data.token) uni.setStorageSync(USER_TOKEN, data.token);
    },
  },
});

setSessionExpiredHandler(() => useUserStore().clearUserLoginInfo());
