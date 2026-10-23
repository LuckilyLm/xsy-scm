<template>
  <a-modal
    :open="open"
    title="标品一键分拣"
    :width="880"
    :style="{maxWidth: 'calc(100vw - 32px)'}"
    :mask-closable="false"
    :closable="!saving"
    :keyboard="!saving"
    :confirm-loading="saving"
    :ok-button-props="{disabled: loading || !selectedIds.length}"
    :cancel-button-props="{disabled: saving}"
    ok-text="确认所选标品"
    @cancel="close"
    @ok="submit"
  >
    <a-alert type="info" show-icon message="将所选标准品按下方计划数量确认为正常分拣。" />
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-button :disabled="saving || loading" @click="load">刷新预览</a-button>
    <a-table
      size="small"
      row-key="id"
      :loading="loading"
      :data-source="preview?.items || []"
      :columns="columns"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns) + 44, y: 400}"
      :row-selection="rowSelection"
    >
      <template #bodyCell="{column, record}">
        <template v-if="column.dataIndex === 'quantity'">
          <span class="scm-quantity">{{ record.quantity ?? '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'reason'">{{ record.reason || '可确认' }}</template>
      </template>
    </a-table>
    <p>已选择 {{ selectedIds.length }} 条明细。数量以各行单位为准。</p>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {scmColumnsWidth} from '../../common/scm-column';
import {message, type TableColumnsType} from 'ant-design-vue';
import {sortingApi} from '/@/api/business/scm/sorting-api';
import {sortingError} from '../sorting-types';
import type {Id, SortingStandardItem, SortingStandardPreview} from '../sorting-types';

const props = defineProps<{open: boolean; taskId?: Id}>();
const emit = defineEmits<{'update:open': [value: boolean]; saved: []}>();
const preview = ref<SortingStandardPreview>();
const selectedIds = ref<Id[]>([]);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
let generation = 0;
const columns: TableColumnsType<SortingStandardItem> = [
  {title: '订单号', dataIndex: 'orderNo', width: 170},
  {title: '客户', dataIndex: 'customerName', width: 130},
  {title: '商品', dataIndex: 'productName', width: 150},
  {title: '商品规格', dataIndex: 'specName', width: 130},
  {title: '确认数量', dataIndex: 'quantity', width: 110},
  {title: '单位', dataIndex: 'unit', width: 70},
  {title: '可处理状态', dataIndex: 'reason', width: 200},
];
const rowSelection = computed(() => ({
  columnWidth: 44,
  selectedRowKeys: selectedIds.value,
  onChange: (keys: (string | number)[]) => {
    selectedIds.value = keys;
  },
  getCheckboxProps: (row: SortingStandardItem) => ({disabled: !row.eligible || saving.value || loading.value}),
}));

watch(
  () => [props.open, props.taskId],
  () => {
    generation++;
    selectedIds.value = [];
    preview.value = undefined;
    error.value = '';
    if (props.open) void load();
  }
);

async function load() {
  if (props.taskId == null) return;
  const current = ++generation;
  loading.value = true;
  selectedIds.value = [];
  preview.value = undefined;
  error.value = '';
  try {
    const response = await sortingApi.standardPreview(props.taskId);
    if (current === generation) preview.value = response.data;
  } catch (cause) {
    if (current === generation) error.value = sortingError(cause);
  } finally {
    if (current === generation) loading.value = false;
  }
}

function close() {
  if (!saving.value) emit('update:open', false);
}

async function submit() {
  if (saving.value || loading.value || !preview.value || !selectedIds.value.length) return;
  const data = preview.value;
  const selected = new Set(selectedIds.value.map(String));
  const items = data.items.filter((item) => selected.has(String(item.id))).map((item) => ({id: item.id, version: item.version}));
  saving.value = true;
  error.value = '';
  try {
    const response = await sortingApi.confirmStandard(data.taskId, {version: data.version, items});
    message.success(`已确认 ${response.data.confirmedItemIds.length} 条标准品明细，请核对后单独完成任务`);
    emit('saved');
    emit('update:open', false);
  } catch (cause) {
    // 网络结果不明时保留原选择，重试由 API 层沿用原幂等键；刷新预览后须重新选择。
    error.value = `${sortingError(cause)}。可重试原操作；如数据已变化，请刷新预览后重新选择。`;
  } finally {
    saving.value = false;
  }
}
</script>
