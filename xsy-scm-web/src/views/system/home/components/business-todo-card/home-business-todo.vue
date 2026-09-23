<!--
  * 业务待办卡片（Wave 4）
  *
  * 首页右侧的只读入口：把各业务域「当前待处理量」聚合成几张卡片，点击带条件跳转到对应列表页。
  * 卡片可见性与数字全部来自后端 `GET /scm/dashboard/todo`（按登录人权限过滤），
  * 前端不判断权限、不缓存计数、不写任何业务表 —— 刷新即最新。
-->
<template>
  <default-home-card icon="CheckSquareOutlined" title="业务待办">
    <div style="height: 240px">
      <a-spin :spinning="loading">
        <div class="center column">
          <a-empty v-if="!loading && todos.length === 0" description="暂无待办事项"/>
          <div v-for="todo in todos" :key="todo.key" class="todo-row" @click="goto(todo)">
            <span class="label">{{ todo.label }}</span>
            <a-badge :count="todo.count" :overflow-count="999" :number-style="badgeStyle(todo.count)"/>
          </div>
        </div>
      </a-spin>
    </div>
  </default-home-card>
</template>
<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {useRouter} from 'vue-router';
import DefaultHomeCard from '/@/views/system/home/components/default-home-card.vue';
import {scmDashboardApi, type ScmTodo} from '/@/api/business/scm/dashboard-api';

const router = useRouter();
const todos = ref<ScmTodo[]>([]);
const loading = ref(false);

// 计数为 0 时用中性灰，避免把「有权限但当前无任务」误读成异常。
function badgeStyle(count: number) {
  return {backgroundColor: count > 0 ? '#ff4d4f' : '#d9d9d9', color: '#fff'};
}

async function load() {
  loading.value = true;
  try {
    const r = await scmDashboardApi.todo();
    todos.value = r.data ?? [];
  } finally {
    loading.value = false;
  }
}

// route 已带查询条件；vue-router 接受含 query 的完整路径字符串。
function goto(todo: ScmTodo) {
  void router.push(todo.route);
}

onMounted(load);

defineExpose({load});
</script>
<style lang="less" scoped>
.center {
  display: flex;
  justify-content: center;
  height: 100%;
  overflow-y: auto;

  &.column {
    flex-direction: column;
    width: 100%;
    padding: 0 10px;
    justify-content: flex-start;
  }
}

.todo-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 4px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;

  &:hover {
    background-color: #e8f8f0;
  }

  .label {
    color: #1f2329;
  }
}
</style>
