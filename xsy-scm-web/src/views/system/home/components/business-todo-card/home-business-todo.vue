<template>
  <default-home-card icon-name="section-business-todo" title="业务待办">
    <region-error v-if="error" :message="error" :min-height="332" @retry="load"/>
    <a-spin v-else :spinning="loading">
      <div class="home-todos">
        <a-empty v-if="!loading && todos.length === 0" description="暂无待办事项"/>
        <router-link v-for="todo in todos" :key="todo.key" class="todo-row" :to="todo.route">
          <span class="todo-row__visual" :class="`tone-${todoTone(todo.key)}`">
            <scm-icon :name="todoIcon(todo.key)" :size="36"/>
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
    iconName: 'todo-inventory-error', tone: 'danger', hint: '库存不足、超配或临界预警',
  },
  'receipt-putaway': {
    iconName: 'todo-pending-receipt', tone: 'warn', hint: '采购到货待确认入库',
  },
  'loss-gain-audit': {
    iconName: 'todo-pending-approval', tone: 'primary', hint: '待审批的报损报溢单据',
  },
  'delivery-route-draft': {
    iconName: 'todo-draft-delivery', tone: 'ok', hint: '待完善的配送线路',
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
  min-height: 332px;
  max-height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.todo-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 10px;
  border-radius: 10px;
  border-bottom: 1px solid var(--scm-border);
  color: var(--scm-text);
  transition: background 0.16s ease;

  &:last-child {
    border-bottom: 0;
  }

  &:hover {
    background: var(--scm-fill);
  }

  &:hover .todo-row__count {
    color: var(--scm-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--scm-primary);
    outline-offset: -2px;
  }
}

/* 圆形图标底：与右侧数字形成左右对称的视觉锚点 */
.todo-row__visual {
  display: inline-flex;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;

  &.tone-ok { background: #e7f8ef; }
  &.tone-warn { background: #fff5df; }
  &.tone-danger { background: #ffeded; }
  &.tone-primary { background: #eaf4ff; }

  img { width: 26px; height: 26px; object-fit: contain; }
}

.todo-row__copy {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.todo-row__label {
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--scm-text);
  overflow-wrap: anywhere;
}

.todo-row__hint {
  overflow: hidden;
  color: var(--scm-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-row__count {
  flex: 0 0 auto;
  align-self: center;
  min-width: 30px;
  text-align: right;
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  color: var(--scm-text-disabled);
  transition: color 0.16s ease;

  &.has-tasks {
    color: var(--scm-error);
  }
}
</style>
