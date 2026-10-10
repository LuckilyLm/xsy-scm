<script>
  import { useUserStore } from '@/store/modules/system/user';

  export default {
    onLaunch: function () {
      // 用本地令牌恢复登录态；无令牌时静默返回，由页面守卫决定是否跳登录
      useUserStore().getLoginInfo();
    },
  };
</script>

<style lang="scss">
  @import '@/uni_modules/uni-scss/index.scss';
</style>
