<!--
  * 顶部菜单-递归菜单
  * 
-->
<template>
  <a-menu
      v-model:openKeys="openKeys"
      v-model:selectedKeys="selectedKeys"
      class="smart-menu"
      mode="horizontal"
      :theme="theme"
  >
    <template v-for="item in menuTree" :key="item.menuId">
      <template v-if="item.visibleFlag && !item.disabledFlag">
        <template v-if="$lodash.isEmpty(item.children)">
          <a-menu-item :key="item.menuId" @click="turnToPage(item)">
            <template #icon>
              <component :is="$antIcons[item.icon]"/>
            </template>
            {{ item.menuName }}
          </a-menu-item>
        </template>
        <template v-else>
          <SubMenu :menu-info="item" :key="item.menuId" @turnToPage="turnToPage"/>
        </template>
      </template>
    </template>
  </a-menu>
</template>
<script setup lang="ts">
import _ from 'lodash';
import {computed, ref, watch} from 'vue';
import {useRoute} from 'vue-router';
import SubMenu from './sub-menu.vue';
import {router} from '/@/router/index';
import {useAppConfigStore} from '/@/store/modules/system/app-config';
import {useUserStore} from '/@/store/modules/system/user';

const theme = computed(() => useAppConfigStore().$state.sideMenuTheme);

const menuTree = computed(() => useUserStore().getMenuTree || []);

//展开的菜单
let currentRoute = useRoute();
const selectedKeys = ref([]);
const openKeys = ref([]);

// 页面跳转
function turnToPage(menu) {
  useUserStore().deleteKeepAliveIncludes(menu.menuId.toString());
  router.push({path: menu.path});
}

/**
 * SmartAdmin中 router的name 就是 后端存储menu的id
 * 所以此处可以直接监听路由，根据路由更新菜单的选中和展开
 */
function updateSelectKeys() {
  // 更新选中
  selectedKeys.value = [_.toNumber(currentRoute.name)];
}

watch(
    currentRoute,
    () => {
      updateSelectKeys();
    },
    {
      immediate: true,
    }
);

defineExpose({
  updateSelectKeys,
});
</script>

<style lang="less" scoped>
.smart-menu {
  position: relative;
  // 顶部菜单是 header-main（flex）里的一段。不给宽度约束时它会按内容撑到 1723px，
  // 视口一变窄就整段溢出、连右侧图标一起被挤出屏幕，且没有滚动条。
  // flex + min-width:0 让它可收缩，antd 横向菜单的 overflow 逻辑才会把
  // 放不下的菜单项折叠成「…」下拉。
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
}
</style>
