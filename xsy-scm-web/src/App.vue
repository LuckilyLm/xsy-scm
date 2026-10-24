<!--
  * 主应用页面
  *
-->

<template>
  <a-config-provider :locale="antdLocale" :theme="antdTheme" :transformCellText="transformCellText">
    <!---全局loading--->
    <a-spin :spinning="spinning" tip="稍等片刻，我在拼命加载中..." size="large">
      <!--- 路由 -->
      <RouterView />
    </a-spin>
  </a-config-provider>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import {computed, h, useSlots, watchEffect} from 'vue';
import {useRoute} from 'vue-router';
import {messages} from '/@/i18n';
import {useAppConfigStore} from '/@/store/modules/system/app-config';
import {useSpinStore} from '/@/store/modules/system/spin';
import {Popover, theme} from 'ant-design-vue';
import {mixColor, themeColors} from '/@/theme/color';
import {useScmThemeVars} from '/@/theme/scm/use-scm-theme-vars';
import SmartCopyIcon from '/@/components/framework/smart-copy-icon/index.vue';

const antdLocale = computed(() => messages[useAppConfigStore().language].antdLocale);
const dayjsLocale = computed(() => messages[useAppConfigStore().language].dayjsLocale);
dayjs.locale(dayjsLocale);

// 全局loading
let spinStore = useSpinStore();
const spinning = computed(() => spinStore.loading);
// 主题颜色
const colorIndex = computed(() => {
  return useAppConfigStore().colorIndex;
});
// 主题
const themeAlgorithm = computed(() => {
  let themeArray = [];
  themeArray.push(useAppConfigStore().darkModeFlag ? theme.darkAlgorithm : theme.defaultAlgorithm);
  if (useAppConfigStore().compactFlag) {
    themeArray.push(theme.compactAlgorithm);
  }
  return themeArray;
});
// 圆角
const borderRadius = computed(() => {
  return useAppConfigStore().borderRadius;
});

// 主题 token：浅色派生色由主色与底色直接合成，避免 antd 从深色推导出灰调
const antdTheme = computed(() => {
  const color = themeColors[colorIndex.value];
  const surface = useAppConfigStore().darkModeFlag ? '#141414' : '#ffffff';
  const tint = (amount: number) => mixColor(color.primaryColor, surface, amount);
  return {
    algorithm: themeAlgorithm.value,
    token: {
      colorPrimary: color.primaryColor,
      colorLink: color.primaryColor,
      colorLinkActive: color.activeColor,
      colorLinkHover: color.hoverColor,
      colorIcon: color.primaryColor,
      borderRadius: borderRadius.value,
      colorPrimaryBg: tint(0.08),
      colorPrimaryBgHover: tint(0.16),
      colorPrimaryBorder: tint(0.3),
      colorPrimaryBorderHover: tint(0.45),
    },
    components: {
      Button: {
        colorLink: color.primaryColor,
        colorLinkActive: color.activeColor,
        colorLinkHover: color.hoverColor,
      },
      Icon: {
        colorIcon: color.primaryColor,
      },
    },
  };
});

function transformCellText({text, column, record, index}) {
  if (column && column.textEllipsisFlag === true) {
    return h(
      Popover,
      {placement: 'bottom'},
      {
        default: () =>
          h(
            'div',
            {
              style: {whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis'},
              id: `${column.dataIndex}${index}`,
            },
            text
          ),
        content: () =>
          h('div', {style: {display: 'flex'}}, [
            h('div', text),
            h(SmartCopyIcon, {value: document.getElementById(`${column.dataIndex}${index}`).innerText}),
          ]),
      }
    );
  } else {
    return text;
  }
}

const {useToken} = theme;
const {token} = useToken();
// SCM 全局样式变量：主题色与暗色切换时同步刷新，业务组件不必各自注入一份
useScmThemeVars();

// 只在 SCM 页面挂外观类，OA 与系统设置页保持 SmartAdmin 原貌
const route = useRoute();
watchEffect(() => {
  document.body.classList.toggle('scm-module', route.meta?.scmModule === true);
});
</script>
<style lang="less">
@color-bg-container: v-bind('token.colorBgContainer');

:deep(.ant-table-column-sorters) {
  align-items: flex-start !important;
}

.smart-query-form {
  background-color: @color-bg-container;
  padding: 5px 10px;
  margin-bottom: 10px;
}

.smart-detail-header {
  background-color: @color-bg-container;
  padding: 10px;
}
</style>
