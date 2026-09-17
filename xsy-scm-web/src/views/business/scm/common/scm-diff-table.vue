<!--
  审计快照差异表（跨模块共享）。

  用途：把操作日志的 `beforeData` / `afterData` 全量快照渲染成**字段级差异**，
  替代原先裸 `JSON.stringify` 塞进 `<pre>` 的做法。

  设计要点：
  - **只读**。这是审计证据，不提供任何编辑入口。
  - **变更行高亮**。未变更的字段灰显，变更的字段加左边框 + 底色，让「改了什么」一眼可见。
  - **数组按行展开**。`items` 这类数组不铺平成一个巨大 JSON，而是逐行给出明细字段，
    新增行标「新增」、移除行标「移除」。
  - **空快照有话说**。「无前态」（如 CREATE）明确写出「（无变更前记录）」，
    而不是给一片空白让用户以为数据丢了。
  - 完全受控：父组件传 `before` / `after` 即可，不自持状态。
-->
<template>
  <div class="scm-diff">
    <template v-if="!result.fields.length && !result.arrays.length">
      <a-empty :image="simpleImage" description="该记录没有可对比的变更内容" />
    </template>

    <template v-else>
      <a-table
        class="scm-diff-table"
        :data-source="result.fields"
        :columns="fieldColumns"
        row-key="key"
        :pagination="false"
        size="small"
        bordered
        :row-class-name="rowClass"
      >
        <template #bodyCell="{ record, column }">
          <template v-if="column.dataIndex === 'key'">
            <span class="scm-diff-key">{{ record.key }}</span>
            <a-tag v-if="record.changed" color="orange" class="scm-diff-badge">已变更</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'before'">
            <span class="scm-diff-value" :class="{ 'is-empty': record.before === '—' }">
              {{ record.before }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'after'">
            <span class="scm-diff-value" :class="{ 'is-empty': record.after === '—' }">
              {{ record.after }}
            </span>
          </template>
        </template>
      </a-table>

      <div v-for="group in result.arrays" :key="group.key" class="scm-diff-group">
        <div class="scm-diff-group-title">
          <span class="scm-diff-key">{{ group.key }}</span>
          <a-tag>{{ group.rows.length }} 行</a-tag>
        </div>
        <a-table
          v-for="(row, index) in group.rows"
          :key="`${group.key}-${index}`"
          class="scm-diff-table scm-diff-nested"
          :data-source="row.fields"
          :columns="fieldColumns"
          row-key="key"
          :pagination="false"
          size="small"
          bordered
          :show-header="false"
          :row-class-name="rowClass"
        >
          <template #title>
            <span class="scm-diff-row-title">{{ row.identity }}</span>
            <a-tag v-if="!row.beforeExists" color="green">新增</a-tag>
            <a-tag v-else-if="!row.afterExists" color="red">移除</a-tag>
          </template>
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'key'">
              <span class="scm-diff-key">{{ record.key }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'before'">
              <span class="scm-diff-value" :class="{ 'is-empty': record.before === '—' }">
                {{ record.before }}
              </span>
            </template>
            <template v-else-if="column.dataIndex === 'after'">
              <span class="scm-diff-value" :class="{ 'is-empty': record.after === '—' }">
                {{ record.after }}
              </span>
            </template>
          </template>
        </a-table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Empty } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import { diffSnapshot, type DiffField } from '/@/views/business/scm/common/scm-diff';

// `a-empty` 的 `image` 需要一个 URL；用 antd 内置的简洁插图，避免依赖外部图片资源。
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;

const props = defineProps<{
  /** 变更前快照；`null` / `undefined` 表示无前态。 */
  before?: unknown;
  /** 变更后快照。 */
  after?: unknown;
}>();

const result = computed(() => diffSnapshot(props.before, props.after));

const fieldColumns: TableColumnsType<DiffField> = [
  // 字段名列窄、固定；两列值平分剩余宽度，避免出现大片空白。
  { title: '字段', dataIndex: 'key', width: 200 },
  { title: '变更前', dataIndex: 'before', width: '50%' },
  { title: '变更后', dataIndex: 'after' },
];

/** 变更行整行加底色，让扫描时先看到改动的行。 */
function rowClass(record: DiffField): string {
  return record.changed ? 'scm-diff-row-changed' : 'scm-diff-row-same';
}
</script>

<style scoped>
.scm-diff-table {
  margin-bottom: 12px;
}
.scm-diff-key {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
.scm-diff-badge {
  margin-left: 8px;
}
.scm-diff-value {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
/* 空值（—）弱化，避免把「没有值」读成「值是破折号」。 */
.scm-diff-value.is-empty {
  color: rgba(0, 0, 0, 0.25);
}

/*
  变更行高亮。
  必须用 `:deep()` 打到 `td` 上：`row-class-name` 给的是 `<tr>` 的类，
  而 antd 的单元格背景来自 `.ant-table-tbody > tr > td`，
  只在 `<tr>` 上设背景会被单元格的 `background: #fff` 盖掉（实测完全看不见）。
*/
.scm-diff-table :deep(.scm-diff-row-changed > td) {
  background-color: #fff7e6 !important;
}
/* 变更行左侧加一道竖条，扫读时能直接定位到改动位置。 */
.scm-diff-table :deep(.scm-diff-row-changed > td:first-child) {
  box-shadow: inset 3px 0 0 0 #fa8c16;
}
/* 未变更的值整体弱化，让变更行更突出。 */
.scm-diff-table :deep(.scm-diff-row-same > td) {
  color: rgba(0, 0, 0, 0.55);
}

.scm-diff-group {
  margin: 16px 0;
}
/*
  数组分组：给一个左边框 + 缩进，让嵌套的逐行明细在视觉上从属于这个数组字段，
  否则会和上层的标量字段表连成一片，分不清层级。
*/
.scm-diff-group {
  padding-left: 12px;
  border-left: 3px solid #d9d9d9;
}
.scm-diff-group-title,
.scm-diff-row-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}
.scm-diff-row-title {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
/* 分组标题与行标题与各自表格拉开一点距离。 */
.scm-diff-group-title {
  margin-bottom: 8px;
}
</style>
