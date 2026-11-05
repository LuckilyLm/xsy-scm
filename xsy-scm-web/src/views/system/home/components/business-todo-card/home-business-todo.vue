<template>
  <default-home-card icon-name="section-business-todo" title="业务待办">
    <region-error v-if="error" :message="error" :min-height="332" @retry="load" />
    <a-spin v-else :spinning="loading">
      <div class="home-todos">
        <a-empty v-if="!loading && todos.length === 0" description="暂无待办事项" />
        <router-link v-for="todo in todos" :key="todo.key" class="todo-row" :to="todo.route">
          <span class="todo-row__visual" :class="`tone-${todoTone(todo.key)}`">
            <scm-icon :name="todoIcon(todo.key)" :size="22" />
          </span>
          <span class="todo-row__copy">
            <span class="todo-row__label">{{ todo.label }}</span>
            <span class="todo-row__hint">{{ todoHint(todo.key) }}</span>
          </span>
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
import ScmIcon from '../scm-icon.vue';

interface TodoMeta {
  iconName: string;
  tone: 'ok' | 'warn' | 'danger' | 'primary';
  hint: string;
}

const TODO_META: Record<string, TodoMeta> = {
  'inventory-warning': {
    iconName: 'todo-inventory-error',
    tone: 'danger',
    hint: '库存不足、超配或临界预警',
  },
  'receipt-putaway': {
    iconName: 'todo-pending-receipt',
    tone: 'warn',
    hint: '采购到货待确认入库',
  },
  'loss-gain-audit': {
    iconName: 'todo-pending-approval',
    tone: 'primary',
    hint: '待审批的报损报溢单据',
  },
  'delivery-route-pending': {
    iconName: 'todo-draft-delivery',
    tone: 'ok',
    hint: '待完善的配送线路',
  },
};

function todoMeta(key: string) {
  return TODO_META[key] ?? {iconName: 'todo-pending-approval', tone: 'ok' as const, hint: '待处理业务事项'};
}

function todoIcon(key: string) {
  return todoMeta(key).iconName;
}

function todoTone(key: string) {
  return todoMeta(key).tone;
}

function todoHint(key: string) {
  return todoMeta(key).hint;
}

const {data, loading, error, load} = useRegionData<ScmTodo[]>(scmDashboardApi.todo, '待办加载失败');
const todos = computed(() => data.value ?? []);

onMounted(load);
defineExpose({load});
</script>

<style lang="less" scoped>
.home-todos {
  display: flex;
  flex-direction: column;
  min-height: 332px;
  max-height: 400px;
  overflow-y: auto;
  gap: 8px;
  scrollbar-width: thin;
}

.todo-row {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  color: var(--scm-text);
  text-decoration: none;
  transition:
    background 0.18s ease,
    border-color 0.18s ease;

  &:hover {
    border-color: color-mix(in srgb, var(--scm-primary) 9%, transparent);
    background: color-mix(in srgb, var(--scm-primary-bg) 45%, var(--scm-bg-container));
  }

  &:focus-visible {
    outline: 2px solid var(--scm-primary);
    outline-offset: -2px;
    background: var(--scm-primary-bg);
  }
}

.todo-row__visual {
  display: inline-flex;
  flex: 0 0 44px;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;

  &.tone-ok {
    color: var(--scm-success);
    background: color-mix(in srgb, var(--scm-success) 9%, var(--scm-bg-container));
  }

  &.tone-warn {
    color: var(--scm-warning);
    background: color-mix(in srgb, var(--scm-warning) 10%, var(--scm-bg-container));
  }

  &.tone-danger {
    color: var(--scm-error);
    background: color-mix(in srgb, var(--scm-error-bg) 75%, var(--scm-bg-container));
  }

  &.tone-primary {
    color: var(--scm-primary);
    background: color-mix(in srgb, var(--scm-primary-bg) 75%, var(--scm-bg-container));
  }
}

.todo-row__copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 4px;
}

.todo-row__label {
  min-width: 0;
  color: var(--scm-text);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.todo-row__hint {
  overflow: hidden;
  color: var(--scm-text-secondary);
  font-size: 12px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-row__count {
  flex: 0 0 auto;
  min-width: 30px;
  text-align: right;
  color: var(--scm-text-disabled);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;

  &.has-tasks {
    color: var(--scm-primary);
  }
}

@media (max-width: 575px) {
  .todo-row {
    gap: 10px;
    padding: 12px 8px;
  }

  .todo-row__hint {
    white-space: normal;
    overflow-wrap: anywhere;
  }
}

@media (prefers-reduced-motion: reduce) {
  .todo-row {
    transition: none;
  }
}
</style>
