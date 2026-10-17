<!--
  * 传统菜单-递归菜单
  * 
-->
<template>
  <a-menu :open-keys="openKeys" v-model:selectedKeys="selectedKeys" class="smart-menu" mode="inline" :inline-indent="16" :theme="theme"
          @openChange="onOpenChange">
    <!-- 使用组件库的实心三角形：展开向下，收起向右。 -->
    <template #expandIcon="{ isOpen }">
      <span class="smart-menu-caret-frame">
        <CaretDownFilled class="smart-menu-caret" :class="{ 'smart-menu-caret-collapsed': !isOpen }"/>
      </span>
    </template>
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
import {CaretDownFilled} from '@ant-design/icons-vue';
import {router} from '/@/router/index';
import {useAppConfigStore} from '/@/store/modules/system/app-config';
import {useUserStore} from '/@/store/modules/system/user';

const theme = computed(() => useAppConfigStore().$state.sideMenuTheme);
const menuSingleExpandFlag = computed(() => useAppConfigStore().$state.menuSingleExpandFlag);

const props = defineProps({
  collapsed: {
    type: Boolean,
    default: false,
  },
});

const menuTree = computed(() => useUserStore().getMenuTree || []);
const rootSubmenuKeys = computed(() => menuTree.value.map((item) => item.menuId));

//展开的菜单
let currentRoute = useRoute();
const selectedKeys = ref<Array<string | number>>([]);
const openKeys = ref<Array<string | number>>([]);

// 页面跳转
function turnToPage(menu) {
  useUserStore().deleteKeepAliveIncludes(menu.menuId.toString());
  router.push({path: menu.path});
}

/**
 * SmartAdmin中 router的name 就是 后端存储menu的id
 * 所以此处可以直接监听路由，根据路由更新菜单的选中和展开
 */
function updateOpenKeysAndSelectKeys() {
  // 更新选中
  selectedKeys.value = [_.toNumber(currentRoute.name)];

  /**
   * 更新展开（1、获取新展开的menu key集合；2、保留原有的openkeys，然后把新展开的与之合并）
   */
      //获取需要展开的menu key集合
  let menuParentIdListMap = useUserStore().getMenuParentIdListMap;
  let parentList = menuParentIdListMap.get(currentRoute.name) || [];

  // 如果是折叠菜单的话，则不需要设置openkey
  if (props.collapsed) {
    return;
  }

  let needOpenKeys = _.map(parentList, 'name').map(Number);
  if (menuSingleExpandFlag.value) {
    openKeys.value = [...needOpenKeys];
  } else {
    // 使用lodash的union函数，进行 去重合并两个数组
    openKeys.value = _.union(openKeys.value, needOpenKeys);
  }
}

watch(
    currentRoute,
    () => {
      updateOpenKeysAndSelectKeys();
    },
    {
      immediate: true,
    }
);

function onOpenChange(openKeysParams) {
  if (!menuSingleExpandFlag.value) {
    // open-keys 是受控属性：允许多组展开时也必须同步点击后的 keys，否则点击看起来没有反应。
    openKeys.value = [...openKeysParams];
    return;
  }
  const latestOpenKey = openKeysParams.find((key) => openKeys.value.indexOf(key) === -1);
  if (rootSubmenuKeys.value.indexOf(latestOpenKey) === -1) {
    openKeys.value = openKeysParams;
  } else {
    openKeys.value = latestOpenKey ? [latestOpenKey] : [];
  }
}

defineExpose({
  updateOpenKeysAndSelectKeys,
});
</script>

<style lang="less" scoped>
.smart-menu {
  position: relative;
  padding: 8px 10px 16px;
  border-inline-end: 0;
  background: transparent;

  :deep(.ant-menu-item),
  :deep(.ant-menu-submenu-title) {
    width: 100%;
    height: 42px;
    line-height: 42px;
    margin: 2px 0;
    border-radius: 8px;
    font-size: 14px;
    transition: background-color 0.18s, color 0.18s;
  }

  // expandIcon 自定义插槽直接插入标题中，必须定位插槽根元素。
  :deep(.smart-menu-caret-frame) {
    position: absolute;
    top: 50%;
    inset-inline-end: 14px;
    width: 16px;
    height: 16px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
    margin: 0;
    transform: translateY(-50%);
    color: var(--scm-text-secondary, rgba(0, 0, 0, 0.55));
  }

  :deep(.smart-menu-caret) {
    font-size: 11px !important;
    line-height: 1;
    transform: rotate(0deg);
    transition: transform 0.2s ease;
  }

  :deep(.smart-menu-caret-collapsed) {
    transform: rotate(-90deg);
  }

  :deep(.ant-menu-item .anticon),
  :deep(.ant-menu-submenu-title .anticon) {
    font-size: 16px;
  }

  :deep(.ant-menu-submenu-title) {
    position: relative;
    padding-inline-end: 36px;
    font-weight: 500;
  }

  :deep(.ant-menu-item) {
    padding-inline-end: 12px;
  }

  :deep(.ant-menu-sub .ant-menu-item),
  :deep(.ant-menu-sub .ant-menu-submenu-title) {
    height: 38px;
    line-height: 38px;
    font-size: 13px;
    font-weight: 400;
  }
}

.smart-menu:not(.ant-menu-dark) {
  :deep(.ant-menu-item-selected) {
    background: var(--scm-primary-bg, rgba(0, 185, 107, 0.09));
    color: var(--scm-primary, #1677ff);
    font-weight: 600;
  }

  :deep(.ant-menu-submenu-open > .ant-menu-submenu-title) {
    color: var(--scm-text, rgba(0, 0, 0, 0.88));
  }

  :deep(.ant-menu-item-selected::before) {
    position: absolute;
    top: 10px;
    bottom: 10px;
    left: 0;
    width: 3px;
    background: var(--scm-primary, #1677ff);
    border-radius: 0 3px 3px 0;
    content: '';
  }
}

// 深色菜单使用浅灰箭头，悬停和展开后仍保持中性色。
.smart-menu.ant-menu-dark {
  :deep(.smart-menu-caret-frame) {
    color: rgba(255, 255, 255, 0.7);
  }
}

/*
 * 菜单图标配色
 *
 * antd 的菜单项图标是 `color: inherit`（`resetIcon()`），跟着菜单项自身的状态色走。
 * 亮色菜单下 antd 给的是：普通 = colorText、hover = colorText（只换背景、不换字色）、
 * 选中 = colorPrimary。也就是说 hover 时图标并不会变色 —— 这里把三档显式写出来，
 * 让「普通深色 / hover 主题色 / 当前主题色」成为明确的设计，而不是继承出来的巧合。
 *
 * 两条约束：
 * 1. 必须排除 `.ant-menu-dark`：暗色菜单的文字是浅色，用 `--scm-text`（深色）会把图标压到看不见。
 *    暗色下继续交给 antd 自己的 dark 主题 token。
 * 2. 用 `--scm-*` 而不是写死 `#333` / `#00b96b`：主题色可切换（当前是绿），写死会在换色后失效。
 *    这两个变量由 `useScmThemeVars()` 落在 `<html>` 上。
 */
.smart-menu:not(.ant-menu-dark) {
  :deep(.ant-menu-item .anticon),
  :deep(.ant-menu-submenu-title .anticon) {
    color: var(--scm-text, rgba(0, 0, 0, 0.88));
  }

  :deep(.ant-menu-item:hover .anticon),
  :deep(.ant-menu-submenu-title:hover .anticon) {
    color: var(--scm-primary, #1677ff);
  }

  :deep(.ant-menu-item-selected .anticon),
  :deep(.ant-menu-submenu-selected > .ant-menu-submenu-title .anticon) {
    color: var(--scm-primary, #1677ff);
  }
}
</style>
