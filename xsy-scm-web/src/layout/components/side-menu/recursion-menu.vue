<!--
  * 传统菜单-递归菜单
  *
-->
<template>
  <a-menu
    :open-keys="openKeys"
    v-model:selectedKeys="selectedKeys"
    class="smart-menu smart-menu--side"
    :class="{'smart-menu--collapsed': collapsed}"
    mode="inline"
    :inline-indent="14"
    :theme="theme"
    @openChange="onOpenChange"
  >
    <template #expandIcon />
    <template v-for="item in menuTree" :key="item.menuId">
      <template v-if="item.visibleFlag && !item.disabledFlag">
        <template v-if="$lodash.isEmpty(item.children)">
          <a-menu-item :key="item.menuId" @click="turnToPage(item)">
            <template #icon>
              <component :is="resolveMenuIcon(item.icon)" v-if="resolveMenuIcon(item.icon)" />
              <span v-else class="smart-menu-icon-blank" />
            </template>
            <span class="smart-menu-label" :title="item.menuName">{{ item.menuName }}</span>
          </a-menu-item>
        </template>
        <template v-else>
          <SubMenu :menu-info="item" :key="item.menuId" @turnToPage="turnToPage" />
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
import {resolveMenuIcon} from '/@/lib/menu-icon';
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

// 菜单树来自 SmartAdmin 的用户 store（`menuTree` 初值空数组，元素类型未声明）；
// 不显式放宽的话 computed 会被推成 never[]，模板里读 item.menuName 直接报错。
const menuTree = computed<any[]>(() => useUserStore().getMenuTree || []);
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
  --side-menu-motion: 0.22s;
  --side-menu-ease: cubic-bezier(0.22, 1, 0.36, 1);

  box-sizing: border-box;
  width: 100%;
  padding: 8px 10px 16px;
  border-inline-end: 0;
  background: transparent;

  :deep(.ant-menu-item),
  :deep(.ant-menu-submenu-title) {
    display: flex;
    align-items: center;
    width: 100%;
    height: 46px;
    margin: 2px 0;
    padding-inline-end: 12px;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 500;
    line-height: 20px;
    white-space: nowrap;
    transition:
      background-color 0.16s ease,
      color 0.16s ease;
  }

  :deep(.ant-menu-sub .ant-menu-item),
  :deep(.ant-menu-sub .ant-menu-submenu-title) {
    height: 40px;
    margin-block: 2px;
    font-size: 13px;
    font-weight: 400;
  }

  :deep(.ant-menu-sub.ant-menu-inline) {
    background: transparent;
  }

  :deep(.ant-menu-item > .ant-menu-item-icon),
  :deep(.ant-menu-submenu-title > .ant-menu-item-icon) {
    display: inline-flex;
    flex: 0 0 16px;
    align-items: center;
    justify-content: center;
    width: 16px;
    min-width: 16px;
    height: 16px;
    font-size: 16px;
    line-height: 1;
    transition: color 0.16s ease;
  }

  :deep(.smart-menu-icon-blank) {
    display: inline-block;
    width: 16px;
    height: 16px;
  }

  // 保留组件库的高度测量与清理钩子；其 transition 带 important，只覆盖时长和曲线。
  :deep(.ant-motion-collapse) {
    transition-duration: var(--side-menu-motion) !important;
    transition-timing-function: var(--side-menu-ease) !important;
  }
}

.smart-menu.ant-menu-root:not(.smart-menu--collapsed) {
  :deep(.ant-menu-item > .ant-menu-title-content),
  :deep(.ant-menu-submenu-title > .ant-menu-title-content) {
    flex: 1 1 0;
    min-width: 0;
    margin-inline-start: 10px;
    white-space: nowrap;
  }

  :deep(.smart-menu-label) {
    display: block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.smart-menu.ant-menu-root.smart-menu--collapsed {
  width: 100%;

  :deep(> .ant-menu-item),
  :deep(> .ant-menu-submenu > .ant-menu-submenu-title) {
    padding-inline: 14px;
  }

  :deep(.ant-menu-title-content) {
    flex: 1 1 0;
    min-width: 0;
    margin-inline-start: 0;
    opacity: 0;
    overflow: hidden;
    white-space: nowrap;
  }

  :deep(.ant-menu-item-icon) {
    margin: 0;
  }
}

.smart-menu:not(.ant-menu-dark) {
  :deep(.ant-menu-item),
  :deep(.ant-menu-submenu-title) {
    color: var(--scm-text);
  }

  :deep(.ant-menu-item > .anticon),
  :deep(.ant-menu-submenu-title > .anticon) {
    color: var(--scm-text-secondary);
  }

  :deep(.ant-menu-item:hover),
  :deep(.ant-menu-submenu-title:hover) {
    background: color-mix(in srgb, var(--scm-primary-bg) 65%, transparent);
    color: var(--scm-primary);
  }

  :deep(.ant-menu-item:hover > .anticon),
  :deep(.ant-menu-submenu-title:hover > .anticon),
  :deep(.ant-menu-item-selected > .anticon),
  :deep(.ant-menu-submenu-selected > .ant-menu-submenu-title > .anticon) {
    color: var(--scm-primary);
  }

  :deep(.ant-menu-item-selected) {
    background: var(--scm-primary-bg);
    color: var(--scm-primary);
    font-weight: 600;
  }

  :deep(.ant-menu-submenu-selected > .ant-menu-submenu-title) {
    color: var(--scm-primary);
  }
}

.smart-menu.ant-menu-root {
  :deep(.ant-menu-item-selected::before) {
    position: absolute;
    inset: 10px auto 10px 0;
    width: 3px;
    border-radius: 0 3px 3px 0;
    background: var(--scm-primary);
    content: '';
  }

  :deep(.ant-menu-item-selected) {
    font-weight: 600;
  }
}

.smart-menu.ant-menu-dark {
  --side-menu-dark-active: color-mix(in srgb, var(--scm-primary) 20%, white);

  :deep(.ant-menu-item:hover),
  :deep(.ant-menu-submenu-title:hover) {
    color: var(--side-menu-dark-active);
    background: color-mix(in srgb, var(--scm-primary) 12%, transparent);
  }

  :deep(.ant-menu-submenu-selected > .ant-menu-submenu-title) {
    color: var(--side-menu-dark-active);
  }

  :deep(.ant-menu-item-selected) {
    color: var(--side-menu-dark-active);
    background: color-mix(in srgb, var(--scm-primary) 20%, transparent);
  }
}

@media (prefers-reduced-motion: reduce) {
  .smart-menu {
    :deep(.ant-menu-item),
    :deep(.ant-menu-submenu-title),
    :deep(.ant-menu-item-icon),
    :deep(.ant-menu-title-content) {
      transition: none;
    }

    :deep(.ant-motion-collapse) {
      transition-duration: 0.001s !important;
    }
  }
}
</style>
