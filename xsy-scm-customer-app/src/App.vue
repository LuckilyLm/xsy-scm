<script>
  import { useUserStore } from '@/store/modules/system/user';
  import { useMallThemeStore } from '@/store/modules/mall/theme';

  export default {
    onLaunch: function () {
      // 冷启动先以默认主题渲染，再异步刷新已发布主题（失败自动回退默认）
      useMallThemeStore().refresh();
      // 用本地令牌恢复登录态；无令牌时静默返回，由页面守卫决定是否跳登录
      useUserStore().getLoginInfo();
    },
    onShow: function () {
      // 恢复前台刷新主题；与页面 onShow 触发的请求在 store 内合并为一次
      useMallThemeStore().refresh();
    },
  };
</script>

<style lang="scss">
  @import '@/uni_modules/uni-scss/index.scss';
</style>
