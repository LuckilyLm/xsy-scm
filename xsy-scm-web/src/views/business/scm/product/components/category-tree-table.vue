<template>
  <a-table :columns="columns" :data-source="rows" row-key="categoryId" :pagination="false" :loading="loading" size="small" bordered :scroll="{ x: 850 }">
    <template #bodyCell="{ column, record }">
      <template v-if="column.dataIndex === 'level'">{{ record.level }} 级</template>
      <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 'ENABLED' ? 'green' : 'default'">{{ record.status === 'ENABLED' ? '启用' : '停用' }}</a-tag>
      <a-space v-else-if="column.dataIndex === 'action'" class="smart-table-operate">
        <a-button v-if="record.level < 3" v-privilege="'scm:product:category:add'" type="link" size="small" :disabled="record.status !== 'ENABLED'" @click="emit('add', record)">新增子分类</a-button>
        <a-button v-privilege="'scm:product:category:update'" type="link" size="small" @click="emit('edit', record)">编辑</a-button>
        <a-popconfirm title="确认删除此分类？" @confirm="emit('remove', record)"><a-button v-privilege="'scm:product:category:delete'" type="link" danger size="small">删除</a-button></a-popconfirm>
      </a-space>
    </template>
  </a-table>
</template>
<script setup lang="ts">
import type { ProductCategory } from '/@/types/business/scm/product';
defineProps<{ rows: ProductCategory[]; loading: boolean }>();
const emit = defineEmits<{ add: [row: ProductCategory]; edit: [row: ProductCategory]; remove: [row: ProductCategory] }>();
const columns = [
  { title: '分类名称', dataIndex: 'name', width: 260 }, { title: '分类编码', dataIndex: 'categoryCode', width: 190 },
  { title: '层级', dataIndex: 'level', width: 90 }, { title: '排序', dataIndex: 'sortOrder', width: 90, align: 'right' as const },
  { title: '状态', dataIndex: 'status', width: 90, align: 'center' as const }, { title: '操作', dataIndex: 'action', width: 250, align: 'right' as const },
];
</script>
