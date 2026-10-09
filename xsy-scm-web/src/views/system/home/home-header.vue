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
    :style="{backgroundImage: `url('${homeAsset('banner/home-hero-harvest.webp')}')`}">
    <div class="home-welcome__row">
      <div class="home-welcome__text">
        <div class="home-welcome__copy">
          <h2 class="home-welcome__title">{{ welcomeSentence }}</h2>
          <p class="home-welcome__meta">{{ dayInfo }}</p>
        </div>
      </div>
      <div class="home-welcome__tools">
        <quick-entries/>
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
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {useRouter} from 'vue-router';
import {useUserStore} from '/@/store/modules/system/user';
import QuickEntries from './components/quick-entries.vue';
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

function gotoScreen() {
  void router.push('/screen');
}

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
    justify-content: space-between;
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

  .home-welcome__actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .home-welcome__tools {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 10px;
    min-width: 0;
    max-width: 100%;
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

    .home-welcome__tools {
      align-items: flex-start;
    }

    .home-welcome__title {
      font-size: 20px;
    }
  }
}
</style>
