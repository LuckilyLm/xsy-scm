<template>
  <default-home-card extra="更多" icon="FlagOutlined" title="更新日志" @extraClick="onMore">
    <region-error v-if="error" :message="error" @retry="load"/>
    <a-spin v-else :spinning="loading">
      <div class="home-changelog">
        <a-empty v-if="!loading && entries.length === 0" description="暂无更新日志"/>
        <ul v-else class="home-changelog__list">
          <li v-for="item in entries" :key="item.changeLogId" class="home-changelog__item">
            <button type="button" class="home-changelog__link" @click="goDetail(item)">
              {{ $smartEnumPlugin.getDescByValue('CHANGE_LOG_TYPE_ENUM', item.type) }}：{{ item.updateVersion }} 版本
            </button>
            <span class="home-changelog__time">{{ item.publicDate }}</span>
          </li>
        </ul>
      </div>
    </a-spin>
  </default-home-card>
  <ChangeLogForm ref="modalRef"/>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {useRouter} from 'vue-router';
import {changeLogApi} from '/@/api/support/change-log-api';
import type {ScmResponse} from '/@/types/business/scm/customer';
import DefaultHomeCard from './default-home-card.vue';
import RegionError from './region-error.vue';
import {useRegionData} from './use-region-data';
import ChangeLogForm from '/@/views/support/change-log/change-log-modal.vue';

interface ChangeLog {
  changeLogId: string | number;
  type: number;
  updateVersion: string;
  publicDate: string;
  content?: string;
  link?: string;
}

const router = useRouter();
const {data, loading, error, load} = useRegionData<{list: ChangeLog[]}>(
  () => changeLogApi.queryPage(
    {pageNum: 1, pageSize: 8, searchCount: false},
    {suppressGlobalErrorMessage: true}
  ) as unknown as Promise<ScmResponse<{list: ChangeLog[]}>>,
  '更新日志加载失败'
);
const entries = computed(() => data.value?.list ?? []);
const modalRef = ref<InstanceType<typeof ChangeLogForm>>();

function onMore() {
  void router.push('/support/change-log/change-log-list');
}

function goDetail(item: ChangeLog) {
  modalRef.value?.show(item);
}

onMounted(load);
defineExpose({load});
</script>

<style lang="less" scoped>
.home-changelog {
  min-height: 150px;
}

.home-changelog__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.home-changelog__item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0;
}

.home-changelog__link {
  min-width: 0;
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  color: var(--scm-text);
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;

  &:hover { color: var(--scm-primary); }
  &:focus-visible {
    outline: 2px solid var(--scm-primary);
    outline-offset: 2px;
  }
}

.home-changelog__time {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--scm-text-secondary);
}

@media (max-width: 575px) {
  .home-changelog__item {
    flex-wrap: wrap;
    gap: 2px;
  }
}
</style>
