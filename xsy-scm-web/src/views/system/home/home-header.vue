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
        <h2 class="home-welcome__title">{{ welcomeSentence }}</h2>
        <p class="home-welcome__meta">{{ dayInfo }}</p>
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
  min-height: 164px;
  border: 1px solid var(--scm-border);
  border-radius: 12px;
  background-color: #effcf5;
  background-repeat: no-repeat;
  background-position: center right;
  background-size: cover;
  overflow: hidden;

  &::before {
    position: absolute;
    inset: 0;
    background: linear-gradient(90deg, rgba(245, 255, 249, 0.98) 0%, rgba(245, 255, 249, 0.9) 38%, rgba(245, 255, 249, 0.42) 72%, rgba(245, 255, 249, 0.04) 100%);
    content: '';
    pointer-events: none;
  }

  :deep(.ant-card-body) {
    position: relative;
    z-index: 1;
    padding: 20px;
    min-height: 164px;
    display: flex;
    align-items: center;
  }

  .home-welcome__row {
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .home-welcome__title {
    margin: 0;
    font-size: clamp(20px, 2vw, 28px);
    font-weight: 600;
    color: var(--scm-text);
  }

  .home-welcome__meta {
    margin: 4px 0 0;
    font-size: 13px;
    color: var(--scm-text-secondary);
  }

  .home-welcome__text {
    max-width: 560px;
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
    gap: 8px;
    min-width: 0;
  }

  @media (max-width: 767px) {
    min-height: 208px;
    background-position: 66% center;

    &::before {
      background: linear-gradient(90deg, rgba(245, 255, 249, 0.98) 0%, rgba(245, 255, 249, 0.86) 62%, rgba(245, 255, 249, 0.28) 100%);
    }

    :deep(.ant-card-body) {
      min-height: 208px;
      align-items: flex-start;
    }

    .home-welcome__tools {
      align-items: flex-start;
    }
  }
}
</style>
