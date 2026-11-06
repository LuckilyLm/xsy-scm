import { defineStore } from 'pinia';
import { USER_TOKEN } from '@/constants/local-storage-key-const';
import { SESSION_EXPIRED_CODES } from '@/constants/error-code-const';
import { mallAuthApi } from '@/api/mall/auth-api';
import { setSessionExpiredHandler } from '@/lib/xsy-request';
import { xsySentry } from '@/lib/xsy-sentry';

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

let restoreRequest = null;

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
        xsySentry.captureError(error);
      } finally {
        this.clearUserLoginInfo();
      }
    },
    clearUserLoginInfo() {
      restoreRequest = null;
      Object.assign(this, initialState());
      uni.removeStorageSync(USER_TOKEN);
    },
    async getLoginInfo() {
      const token = uni.getStorageSync(USER_TOKEN);
      if (!token || this.token === token) return;
      if (restoreRequest?.token === token) return restoreRequest.task;

      const request = { token, task: null };
      restoreRequest = request;
      const isCurrent = () => restoreRequest === request && uni.getStorageSync(USER_TOKEN) === token;
      request.task = (async () => {
        try {
          const { data: profile } = await mallAuthApi.getProfile();
          if (!isCurrent()) return;
          if (!profile?.accountId || !profile?.customerId) throw new Error('未读取到完整的登录资料');
          this.setUserLoginInfo({ token, profile });
          if (this.passwordChangeRequired) {
            uni.reLaunch({ url: '/pages/account/password' });
          }
        } catch (error) {
          if (!isCurrent()) return;
          if (SESSION_EXPIRED_CODES.includes(error?.code)) {
            this.clearUserLoginInfo();
            return;
          }
          // 临时请求失败保留本地令牌，待网络恢复或回到前台后再读资料。
          xsySentry.captureError(error);
        } finally {
          if (restoreRequest === request) restoreRequest = null;
        }
      })();
      return request.task;
    },
    setUserLoginInfo(data) {
      if (!data) return;
      restoreRequest = null;
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
