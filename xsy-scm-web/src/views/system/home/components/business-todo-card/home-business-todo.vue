<template>
  <default-home-card icon="CheckSquareOutlined" title="业务待办">
    <region-error v-if="error" :message="error" @retry="load"/>
    <a-spin v-else :spinning="loading">
      <div class="home-todos">
        <a-empty v-if="!loading && todos.length === 0" description="暂无待办事项"/>
        <router-link v-for="todo in todos" :key="todo.key" class="todo-row" :to="todo.route">
          <span class="todo-row__label">{{ todo.label }}</span>
          <span class="todo-row__count" :class="{'has-tasks': todo.count > 0}">
            {{ formatInt(todo.count) }}
          </span>
        </router-link>
      </div>
    </a-spin>
  </default-home-card>
</template>

<script setup lang="ts">
import {computed, onMounted} from 'vue';
import DefaultHomeCard from '../default-home-card.vue';
import RegionError from '../region-error.vue';
import {useRegionData} from '../use-region-data';
import {scmDashboardApi, type ScmTodo} from '/@/api/business/scm/dashboard-api';
import {formatInt} from '/@/views/business/scm/screen/format';

const {data, loading, error, load} = useRegionData<ScmTodo[]>(scmDashboardApi.todo, '待办加载失败');
const todos = computed(() => data.value ?? []);

onMounted(load);
defineExpose({load});
</script>

<style lang="less" scoped>
.home-todos {
  min-height: 332px;
  max-height: 400px;
  overflow-y: auto;
}

.todo-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 8px;
  border-bottom: 1px solid var(--scm-border);
  color: var(--scm-text);

  &:hover {
    background: var(--scm-fill);
    color: var(--scm-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--scm-primary);
    outline-offset: -2px;
  }
}

.todo-row__label {
  min-width: 0;
  overflow-wrap: anywhere;
}

.todo-row__count {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--scm-fill);
  color: var(--scm-text-secondary);
  font-variant-numeric: tabular-nums;

  &.has-tasks {
    color: var(--scm-error);
    background: var(--scm-error-bg);
  }
}
</style>
