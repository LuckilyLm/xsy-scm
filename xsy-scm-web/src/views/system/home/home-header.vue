<!--
  * 首页欢迎区
  *
  * 只保留「现在是谁、今天几号、能去哪里」：问候语、日期与部门、业务快捷入口、刷新数据、运营大屏。
  * 天气、农历节气、毒鸡汤、上次登录与 IP 属于个人兴趣或安全信息，不占工作台首屏。
  * 运营大屏入口按 scm:screen:query 显隐 —— 它的路由不拦人，只有接口会拒绝。
-->
<template>
  <a-card
    class="home-welcome"
    :bordered="false"
    :style="{backgroundImage: `url('${homeAsset('banner/home-hero-banner.webp')}')`}">
    <div class="home-welcome__row">
      <div class="home-welcome__text">
        <span class="home-welcome__brand" aria-hidden="true">
          <img :src="homeAsset('brand/xsy-logo-mark.png')" alt=""/>
        </span>
        <div class="home-welcome__copy">
          <h2 class="home-welcome__title">{{ welcomeSentence }}</h2>
          <p class="home-welcome__meta">{{ subtitle }}</p>
        </div>
      </div>
      <div class="home-welcome__actions">
        <a-button v-if="canScreen" @click="gotoScreen">
          <template #icon>
            <bar-chart-outlined/>
          </template>
          运营大屏
        </a-button>
        <a-button type="primary" :loading="refreshing" @click="emit('refresh')">
          <template #icon>
            <reload-outlined/>
          </template>
          刷新数据
        </a-button>
      </div>
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {useRouter} from 'vue-router';
import {useUserStore} from '/@/store/modules/system/user';
import {homeAsset} from './home-assets';

defineProps<{canScreen: boolean; refreshing: boolean}>();
const emit = defineEmits(['refresh']);

const router = useRouter();
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

/** 静态文案，不表达任何业务数字，只是让欢迎区右半边的留白有内容。 */
const slogan = '从田间到餐桌，让新鲜更简单';

/**
 * 日期、星期与部门一行说完，再接品牌口号。
 *
 * <p>原来拆成「日期·部门」+「口号」两行，欢迎区因此白白高出一行；设计稿是一行。
 */
const subtitle = computed(() => {
  const now = new Date();
  const week = ['日', '一', '二', '三', '四', '五', '六'][now.getDay()];
  const parts = [`${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} 星期${week}`];
  if (departmentName.value) {
    parts.push(departmentName.value);
  }
  return `${parts.join(' · ')}　|　${slogan}`;
});

function gotoScreen() {
  void router.push('/screen');
}
</script>

<style lang="less" scoped>
.home-welcome {
  position: relative;
  min-height: 88px;
  border: 1px solid var(--scm-border);
  border-radius: 14px;
  background-color: #e9f8f0;
  background-repeat: no-repeat;
  background-position: center right;
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
    padding: 16px 20px;
    min-height: 88px;
    display: flex;
    align-items: center;
  }

  .home-welcome__row {
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
    flex-wrap: wrap;
  }

  .home-welcome__text {
    display: flex;
    align-items: center;
    gap: 14px;
    min-width: 0;
    max-width: 620px;
  }

  /* 品牌圆标：轻描边 + 白底，压在彩色插画前也有边界感 */
  .home-welcome__brand {
    display: inline-flex;
    flex: 0 0 auto;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    border-radius: 14px;
    border: 1px solid rgba(8, 169, 102, 0.16);
    background: rgba(255, 255, 255, 0.86);
    box-shadow: 0 6px 16px rgba(8, 120, 74, 0.1);

    img {
      width: 26px;
      height: 26px;
      object-fit: contain;
    }
  }

  .home-welcome__copy {
    min-width: 0;
  }

  .home-welcome__title {
    margin: 0;
    font-size: clamp(18px, 1.6vw, 24px);
    font-weight: 600;
    line-height: 1.25;
    color: var(--scm-text);
  }

  .home-welcome__meta {
    margin: 4px 0 0;
    font-size: 13px;
    color: var(--scm-text-secondary);
  }

  .home-welcome__actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  @media (max-width: 767px) {
    min-height: 132px;
    background-position: 68% center;

    &::before {
      background: linear-gradient(180deg,
        rgba(244, 255, 250, 0.98) 0%,
        rgba(244, 255, 250, 0.94) 56%,
        rgba(244, 255, 250, 0.6) 100%);
    }

    :deep(.ant-card-body) {
      min-height: 132px;
      align-items: flex-start;
      padding: 16px;
    }
  }
}
</style>
