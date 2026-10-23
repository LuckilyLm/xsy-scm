<template>
  <a-modal :open="open" title="分次分拣记录" :footer="null" :width="760" @cancel="emit('update:open', false)">
    <a-alert v-if="error" type="error" :message="error" show-icon style="margin-bottom: 12px" />
    <p class="scm-note">
      {{ row?.orderNoSnapshot }} · {{ row?.productNameSnapshot }} · {{ row?.specNameSnapshot || '默认规格' }} （单位：{{ row?.saleUnitSnapshot }}）
    </p>
    <a-spin :spinning="loading">
      <a-descriptions :column="2" bordered size="small" style="margin-bottom: 12px">
        <a-descriptions-item label="计划量">{{ quantityText(row?.plannedQuantitySnapshot) }}</a-descriptions-item>
        <a-descriptions-item label="当前登记累计量">
          {{ totalQuantity == null ? '暂无分次登记' : quantityText(totalQuantity) }}
        </a-descriptions-item>
      </a-descriptions>
      <a-form v-if="canAppend" layout="inline" style="margin-bottom: 12px">
        <a-form-item label="本次新增量">
          <a-input-number
            v-model:value="quantity"
            string-mode
            :min="'0.0001'"
            :precision="4"
            placeholder="请输入本次实际量"
            style="width: 170px"
            :disabled="busy"
          />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="note" :maxlength="500" placeholder="可记录框号 / 称重说明" style="width: 220px" :disabled="busy" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="busy" @click="appendPart">登记本次分量</a-button>
        </a-form-item>
      </a-form>
      <p class="scm-note">分次登记不会确认最终分拣量。请在任务明细核对并提交。</p>
      <a-button v-if="canAppend && totalQuantity != null" size="small" style="margin-bottom: 12px" @click="useTotal"
        >将当前累计量填入最终分拣草稿</a-button
      >
      <a-table
        size="small"
        bordered
        :data-source="records"
        :columns="columns"
        row-key="id"
        :loading="loading"
        :pagination="{pageSize: 10}"
        :scroll="{x: scmColumnsWidth(columns)}"
      >
        <template #bodyCell="{record, column}">
          <template v-if="column.dataIndex === 'eventType'">
            {{ record.eventType === 'APPEND' ? '分次追加' : '最终数量确认/调整' }}
          </template>
          <template v-else-if="column.dataIndex === 'createdAt'">{{ datetime(record.createdAt) }}</template>
          <template v-else-if="column.dataIndex === 'quantityDelta' || column.dataIndex === 'totalQuantity'">
            <span class="scm-quantity">{{ quantityText(record[column.dataIndex]) }}</span>
          </template>
          <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
        </template>
      </a-table>
      <a-button v-if="hasMore" :loading="loading" size="small" @click="loadRecords(false)">加载更早记录</a-button>
    </a-spin>
    <div style="text-align: right; margin-top: 12px">
      <a-button @click="emit('update:open', false)">关闭</a-button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, ref, watch} from 'vue';
import {message, type TableColumnsType} from 'ant-design-vue';
import {sortingApi} from '/@/api/business/scm/sorting-api';
import {datetime} from '../../common/scm-display';
import {quantityText, sortingError} from '../sorting-types';
import type {Id, SortingPartRecord, SortingTaskItem} from '../sorting-types';

const props = defineProps<{
  open: boolean;
  taskId?: Id;
  row?: SortingTaskItem;
  canEdit: boolean;
}>();
const emit = defineEmits<{
  'update:open': [value: boolean];
  saved: [];
  useTotal: [itemId: Id, quantity: string];
}>();
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const records = ref<SortingPartRecord[]>([]);
const totalQuantity = ref<string | null>(null);
const hasMore = ref(false);
const beforeId = ref<Id | null>(null);
const quantity = ref<string>();
const note = ref('');
let generation = 0;
const canAppend = computed(() => props.canEdit && props.row?.occupationStatus === 'ACTIVE' && props.row?.result == null);

const columns: TableColumnsType<SortingPartRecord> = [
  {title: '时间', dataIndex: 'createdAt', width: 160},
  {title: '事件', dataIndex: 'eventType', align: 'center', width: 140},
  {title: '本次变化量', dataIndex: 'quantityDelta', width: 115},
  {title: '登记后累计量', dataIndex: 'totalQuantity', width: 125},
  {title: '操作人', dataIndex: 'createdBy', width: 120},
  {title: '备注 / 调整原因', dataIndex: 'note', width: 200},
];

async function loadRecords(reset = true) {
  if (!reset && (loading.value || !hasMore.value)) return;
  const current = ++generation;
  const taskId = props.taskId;
  const itemId = props.row?.id;
  if (!props.open || taskId == null || itemId == null) return;
  loading.value = true;
  error.value = '';
  if (reset) {
    records.value = [];
    totalQuantity.value = null;
    hasMore.value = false;
    beforeId.value = null;
  }
  try {
    const result = await sortingApi.partRecords(taskId, itemId, reset ? undefined : beforeId.value ?? undefined);
    if (current === generation) {
      records.value = reset ? result.data.records : records.value.concat(result.data.records);
      totalQuantity.value = result.data.totalQuantity;
      hasMore.value = result.data.hasMore;
      beforeId.value = result.data.nextBeforeId;
    }
  } catch (e) {
    if (current === generation) error.value = sortingError(e);
  } finally {
    if (current === generation) loading.value = false;
  }
}

watch(
  [() => props.open, () => props.row?.id],
  () => {
    quantity.value = undefined;
    note.value = '';
    void loadRecords();
  },
  {immediate: true}
);

function useTotal() {
  if (!props.row || !canAppend.value || totalQuantity.value == null) return;
  emit('useTotal', props.row.id, totalQuantity.value);
  emit('update:open', false);
}

async function appendPart() {
  if (!canAppend.value || props.taskId == null || props.row == null) return;
  if (!quantity.value || !/^\d{1,14}(?:\.\d{1,4})?$/.test(quantity.value) || !/[1-9]/.test(quantity.value)) {
    error.value = '请输入大于 0 且最多 4 位小数的本次分拣量';
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    await sortingApi.appendPart(props.taskId, {
      taskItemId: props.row.id,
      version: props.row.version,
      quantity: quantity.value,
      note: note.value.trim() || undefined,
    });
    message.success('分次分拣已登记，请核对累计量后最终确认');
    emit('update:open', false);
    emit('saved');
  } catch (e) {
    error.value = sortingError(e);
  } finally {
    busy.value = false;
  }
}
</script>
