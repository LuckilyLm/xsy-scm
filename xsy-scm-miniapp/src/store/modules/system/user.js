/*
 * 登录客户（商城侧）
 *
 * 身份模型见规划文档 §31：服务端由登录态派生客户身份，客户端只负责保存与展示，
 * **不得**把 customerId 当作权限依据回传给服务端。
 */
import { defineStore } from 'pinia';
import { USER_TOKEN } from '@/constants/local-storage-key-const';
import { mallAuthApi } from '@/api/mall/auth-api';
import { setSessionExpiredHandler } from '@/lib/smart-request';
import { smartSentry } from '@/lib/smart-sentry';

const defaultUserInfo = {
  token: '',
  // 商城账号 id
  accountId: null,
  // 客户 id（服务端派生，仅用于展示判断，不作为鉴权凭据）
  customerId: null,
  // 客户编号
  customerCode: '',
  // 客户 / 门店名称
  customerName: '',
  // 联系人
  contactName: '',
  // 手机号
  phone: '',
  // 是否已绑定微信
  wechatBound: false,
  // 客户状态：COOPERATING / SUSPENDED / BLACKLIST / POTENTIAL
  customerStatus: '',
};

export const useUserStore = defineStore({
  id: 'userStore',
  state: () => ({
    ...defaultUserInfo,
  }),
  getters: {
    getToken() {
      return uni.getStorageSync(USER_TOKEN);
    },
    /** 是否已登录（仅判断本地是否有令牌，真实性以服务端返回为准） */
    isLogin(state) {
      return !!state.token;
    },
    /** 是否可交易：仅 COOPERATING 允许进入订货链路 */
    tradable(state) {
      return state.customerStatus === 'COOPERATING';
    },
  },

  actions: {
    async logout() {
      try {
        await mallAuthApi.logout();
      } catch (e) {
        // 退出接口失败不应阻塞本地登出
        smartSentry.captureError(e);
      } finally {
        this.clearUserLoginInfo();
      }
    },

    clearUserLoginInfo() {
      Object.assign(this, defaultUserInfo);
      uni.removeStorage(USER_TOKEN);
    },

    /** 应用启动时用本地令牌换取当前客户资料；无令牌时静默返回 */
    async getLoginInfo() {
      const token = uni.getStorageSync(USER_TOKEN);
      if (!token) {
        return;
      }
      try {
        const res = await mallAuthApi.getProfile();
        this.setUserLoginInfo(res.data);
      } catch (e) {
        // 令牌失效由请求层统一处理（清会话 + 跳登录），这里只记录
        smartSentry.captureError(e);
      }
    },

    setUserLoginInfo(data) {
      if (!data) {
        return;
      }
      this.token = data.token || uni.getStorageSync(USER_TOKEN) || '';
      this.accountId = data.accountId ?? null;
      this.customerId = data.customerId ?? null;
      this.customerCode = data.customerCode || '';
      this.customerName = data.customerName || '';
      this.contactName = data.contactName || '';
      this.phone = data.phone || '';
      this.wechatBound = !!data.wechatBound;
      this.customerStatus = data.customerStatus || '';

      if (data.token) {
        uni.setStorageSync(USER_TOKEN, data.token);
      }
    },
  },
});

/*
 * 把「清会话」注入请求层，打破 store → api → request 的循环依赖。
 *
 * 依赖方向：store 依赖 api 层（业务需要），api 层依赖请求层（发请求），
 * 因此请求层不能再反向依赖 store，否则 Vite 会报 Circular chunk。
 * 回调是惰性的：只有真正发生令牌失效时才会执行，不会在模块求值期调用 useUserStore()。
 */
setSessionExpiredHandler(() => {
  useUserStore().clearUserLoginInfo();
});
