<!--
  * 传统菜单
  *
-->
<template>
  <!--左侧菜单分为两部分：1、顶部logo区域，包含 logo和名称;2、下方菜单区域-->

  <!-- 1、顶部logo区域：直接展示鲜蔬源横版 logo（已含品牌文字，不再重复渲染名称） -->
  <div class="logo" @click="onGoHome" :style="sideMenuWidth" v-if="!collapsed">
    <img class="logo-img" :src="logoImg"/>
  </div>
  <div class="min-logo" @click="onGoHome" v-if="collapsed">
    <img class="logo-img" :src="logoMinImg"/>
  </div>

  <!-- 2、下方菜单区域： 这里使用一个递归菜单解决 -->
  <div class="menu">
    <RecursionMenu :collapsed="collapsed" ref="menuRef"/>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, ref, watch} from 'vue';
import {useRouter} from 'vue-router';
import RecursionMenu from './recursion-menu.vue';
import logoImg from '/@/assets/images/logo/xsy-logo.png';
import logoMinImg from '/@/assets/images/logo/xsy-logo-min.png';
import {HOME_PAGE_NAME} from '/@/constants/system/home-const';
import {useAppConfigStore} from '/@/store/modules/system/app-config';

const sidebarStore = useAppConfigStore();
const sideMenuWidth = computed(() => 'width:' + sidebarStore.sideMenuWidth + 'px');

const props = defineProps({
  collapsed: {
    type: Boolean,
    required: false,
    default: false,
  },
});

const menuRef = ref();

watch(
    () => props.collapsed,
    (newValue) => {
      // 如果是展开菜单的话，重新获取更新菜单的展开项: openkeys和selectKeys
      if (!newValue) {
        nextTick(() => menuRef.value.updateOpenKeysAndSelectKeys());
      }
    }
);

const router = useRouter();

function onGoHome() {
  router.push({name: HOME_PAGE_NAME});
}

const color = computed(() => {
  let isLight = useAppConfigStore().$state.sideMenuTheme === 'light';
  return {
    background: isLight ? '#FFFFFF' : '#001529',
  };
});
</script>

<style lang="less" scoped>
.shadow {
  box-shadow: 2px 0 6px rgba(0, 21, 41, 0.35);
}

.side-menu {
  min-height: 100vh;
  overflow-y: auto;
  z-index: 10;

  .min-logo {
    height: @header-user-height;
    line-height: @header-user-height;
    padding: 0px 15px 0px 15px;
    background-color: v-bind('color.background');
    position: fixed;
    width: 80px;
    z-index: 21;
    display: flex;
    justify-content: center;
    align-items: center;

    .logo-img {
      // 收起态用方形图标版，高度与展开态保持一致观感
      width: 26px;
      height: 26px;
      object-fit: contain;
    }
  }

  .logo {
    height: @header-user-height;
    line-height: @header-user-height;
    background-color: v-bind('color.background');
    padding: 0px 15px 0px 15px;
    position: fixed;
    z-index: 21;
    display: flex;
    cursor: pointer;
    justify-content: center;
    align-items: center;

    .logo-img {
      // 鲜蔬源横版 logo（含品牌文字），按高度撑满、宽度自适应，
      // 避免固定 30x30 把 2.88:1 的横版图压扁
      height: 30px;
      width: auto;
      max-width: 100%;
      object-fit: contain;
    }
  }
}

.menu {
  margin-top: @header-user-height;
}
</style>
