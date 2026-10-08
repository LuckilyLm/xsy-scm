<template>
  <default-home-card extra="更多" asset="icons/notice-announcement.png" title="通知公告" @extraClick="onMore">
    <region-error v-if="error" :message="error" @retry="load"/>
    <a-spin v-else :spinning="loading">
      <div class="home-notices">
        <a-empty v-if="!loading && notices.length === 0" description="暂无通知公告"/>
        <ul v-else class="home-notices__list">
          <li v-for="item in notices" :key="item.noticeId" class="home-notices__item">
            <router-link
              class="home-notices__link"
              :class="{'is-read': item.viewFlag}"
              :to="{path: '/oa/notice/notice-employee-detail', query: {noticeId: item.noticeId}}"
              :title="item.title"
            >
              <a-badge :status="item.viewFlag ? 'default' : 'error'"/>
              {{ item.title }}
            </router-link>
            <span class="home-notices__time">{{ item.publishDate }}</span>
          </li>
        </ul>
      </div>
    </a-spin>
  </default-home-card>
</template>

<script setup lang="ts">
import {computed, onMounted} from 'vue';
import {useRouter} from 'vue-router';
import {noticeApi} from '/@/api/business/oa/notice-api';
import type {ScmResponse} from '/@/types/business/scm/customer';
import DefaultHomeCard from './components/default-home-card.vue';
import RegionError from './components/region-error.vue';
import {useRegionData} from './components/use-region-data';

interface HomeNotice {
  noticeId: number | string;
  title: string;
  viewFlag: boolean;
  publishDate: string;
}

const router = useRouter();
// 不限定类型，仍由员工通知接口裁剪当前人的可见记录。
const {data, loading, error, load} = useRegionData<{list: HomeNotice[]}>(
  () => noticeApi.queryEmployeeNotice(
    {pageNum: 1, pageSize: 6, searchCount: false},
    {suppressGlobalErrorMessage: true}
  ) as unknown as Promise<ScmResponse<{list: HomeNotice[]}>>,
  '通知公告加载失败'
);
const notices = computed(() => data.value?.list ?? []);

function onMore() {
  void router.push('/oa/notice/notice-employee-list');
}

onMounted(load);
defineExpose({load});
</script>

<style lang="less" scoped>
.home-notices {
  min-height: 150px;
}

.home-notices__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.home-notices__item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0;
}

.home-notices__link {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--scm-text);

  &.is-read {
    color: var(--scm-text-secondary);
  }

  &:hover {
    color: var(--scm-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--scm-primary);
    outline-offset: 2px;
  }
}

.home-notices__time {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--scm-text-secondary);
}

@media (max-width: 575px) {
  .home-notices__item {
    flex-wrap: wrap;
    gap: 2px;
  }

  .home-notices__link {
    width: 100%;
  }
}
</style>
