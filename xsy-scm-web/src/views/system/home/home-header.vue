<!-- 首页欢迎区只保留当前用户、日期和品牌内容；常用业务操作通过侧栏进入，减少首屏重复入口。 -->
<template>
  <a-card class="home-welcome" :bordered="false" :style="{backgroundImage: `url('${homeAsset('banner/home-hero-harvest.webp')}')`}">
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
  height: 160px;
  min-height: 148px;
  border: 1px solid var(--scm-border);
  border-radius: var(--home-card-radius, 14px);
  background-color: var(--scm-primary-bg);
  background-repeat: no-repeat;
  background-position: right 68%;
  background-size: cover;
  box-shadow: var(--home-card-shadow, 0 3px 12px rgba(15, 44, 32, 0.05));
  overflow: hidden;

  &::before {
    position: absolute;
    inset: 0;
    background: linear-gradient(
      96deg,
      color-mix(in srgb, var(--scm-bg-container) 90%, var(--scm-primary-bg)) 0%,
      color-mix(in srgb, var(--scm-bg-container) 78%, transparent) 38%,
      color-mix(in srgb, var(--scm-primary-bg) 46%, transparent) 62%,
      transparent 86%
    );
    content: '';
    pointer-events: none;
  }

  :deep(.ant-card-body) {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center;
    height: 100%;
    min-height: 148px;
    padding: 24px 28px;
  }
}

.home-welcome__row {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.home-welcome__text {
  min-width: 0;
  width: min(64%, 620px);
}

.home-welcome__copy {
  min-width: 0;
}

.home-welcome__title {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--scm-text);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: 0.2px;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.home-welcome__meta {
  margin: 10px 0 0;
  overflow: hidden;
  color: var(--scm-text-secondary);
  font-size: 13px;
  line-height: 1.6;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 767px) {
  .home-welcome {
    height: auto;
    min-height: 212px;
    background-position: 68% center;

    &::before {
      background: linear-gradient(
        180deg,
        color-mix(in srgb, var(--scm-bg-container) 94%, var(--scm-primary-bg)) 0%,
        color-mix(in srgb, var(--scm-bg-container) 88%, transparent) 56%,
        color-mix(in srgb, var(--scm-bg-container) 62%, transparent) 100%
      );
    }

    :deep(.ant-card-body) {
      min-height: 212px;
      align-items: flex-start;
      padding: 20px;
    }
  }

  .home-welcome__text {
    width: 100%;
  }

  .home-welcome__title {
    font-size: 20px;
  }

  .home-welcome__meta {
    white-space: normal;
    overflow-wrap: anywhere;
  }
}
</style>
