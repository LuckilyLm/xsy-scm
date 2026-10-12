<!--
  * 首页欢迎区
  *
  * 只保留「现在是谁、今天几号」：问候语、日期与部门，右侧是品牌插画。
  * 快捷入口（新建订单 / 客户 / 采购 / 商品）、运营大屏入口与手动刷新都不再占首屏 ——
  * 前四个在左侧菜单与各列表页都有；大屏入口按 scm:screen:query 显隐，属于少数人的视图；
  * 首页各区块挂载时自己取数，浏览器刷新即可重新拉取。
  * 天气、农历节气、毒鸡汤、上次登录与 IP 属于个人兴趣或安全信息，同样不占工作台首屏。
-->
<template>
  <a-card
    class="home-welcome"
    :bordered="false"
    :style="{backgroundImage: `url('${homeAsset('banner/home-hero-harvest.webp')}')`}">
    <div class="home-welcome__row">
      <div class="home-welcome__text">
        <div class="home-welcome__copy">
          <h2 class="home-welcome__title">{{ welcomeSentence }}</h2>
          <p class="home-welcome__meta">{{ dayInfo }}</p>
        </div>
      </div>
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {useUserStore} from '/@/store/modules/system/user';
import {homeAsset} from './home-assets';

const userStore = useUserStore();

const departmentName = computed(() => userStore.departmentName);

const welcomeSentence = computed(() => {
  const hour = new Date().getHours();
  let greeting = '晚上好';
  if (hour < 6) {
    greeting = '午夜好';
  } else if (hour < 12) {
    greeting = '早上好';
  } else if (hour < 14) {
    greeting = '中午好';
  } else if (hour < 18) {
    greeting = '下午好';
  }
  return `${greeting}，${userStore.$state.actualName ?? ''}`;
});

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

const dayInfo = computed(() => {
  const now = new Date();
  const week = ['日', '一', '二', '三', '四', '五', '六'][now.getDay()];
  const date = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  const parts = [`${date} 星期${week}`];
  if (departmentName.value) {
    parts.push(departmentName.value);
  }
  return parts.join(' · ');
});
</script>

<style lang="less" scoped>
.home-welcome {
  position: relative;
  min-height: 148px;
  border: 1px solid var(--scm-border);
  border-radius: 14px;
  background-color: #e9f8f0;
  background-repeat: no-repeat;
  background-position: right 68%;
  background-size: cover;
  overflow: hidden;

  /* 左实右透：文字永远压在接近不透明的底色上，插画只在右侧露出 */
  &::before {
    position: absolute;
    inset: 0;
    background: linear-gradient(94deg,
      rgba(244, 255, 250, 0.98) 0%,
      rgba(244, 255, 250, 0.95) 34%,
      rgba(244, 255, 250, 0.72) 52%,
      rgba(244, 255, 250, 0.18) 74%,
      rgba(244, 255, 250, 0) 100%);
    content: '';
    pointer-events: none;
  }

  :deep(.ant-card-body) {
    position: relative;
    z-index: 1;
    padding: 24px;
    min-height: 148px;
    display: flex;
    align-items: center;
  }

  .home-welcome__row {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 20px;
    flex-wrap: wrap;
  }

  .home-welcome__text {
    display: flex;
    align-items: center;
    min-width: 0;
    max-width: 620px;
  }

  .home-welcome__copy {
    min-width: 0;
  }

  .home-welcome__title {
    margin: 0;
    font-size: 24px;
    font-weight: 600;
    line-height: 1.25;
    color: var(--scm-text);
  }

  .home-welcome__meta {
    margin: 6px 0 0;
    font-size: 13px;
    color: var(--scm-text-secondary);
  }

  @media (max-width: 767px) {
    min-height: 212px;
    background-position: 68% center;

    &::before {
      background: linear-gradient(180deg,
        rgba(244, 255, 250, 0.98) 0%,
        rgba(244, 255, 250, 0.94) 56%,
        rgba(244, 255, 250, 0.6) 100%);
    }

    :deep(.ant-card-body) {
      min-height: 212px;
      align-items: flex-start;
      padding: 20px;
    }

    .home-welcome__title {
      font-size: 20px;
    }
  }
}
</style>
