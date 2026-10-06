<template>
  <!-- workspace：称重工作台 —— 秤读数事件表 scroll.x 1410，读数要逐列横向比对设备 / 稳定位 / 时间。 -->
  <a-drawer :open="open" title="秤读数" :width="scmDrawerWidth('workspace')" @close="close">
    <a-alert
        message="读数不等于分拣结果：只有「接受」才会把该读数写进分拣结果，且只处理标准品。"
        type="info"
        show-icon
    />
    <a-alert v-if="scaleError" type="error" show-icon :message="scaleError" class="scale-banner"/>
    <a-table
        size="small"
        :data-source="scaleEvents"
        :columns="scaleColumns"
        row-key="id"
        bordered
        :loading="scaleLoading"
        :pagination="false"
        :scroll="{x:1410}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="sortingScaleStatuses[record.status as SortingScaleStatus]?.color || 'default'">
            {{ sortingScaleStatuses[record.status as SortingScaleStatus]?.label || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'stableFlag'">
          <a-tag :color="record.stableFlag ? 'green' : 'orange'">{{ record.stableFlag ? '已稳定' : '未稳定' }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'rawReading'">
          <!-- 秤读数是本抽屉唯一要「读」的数字：放大 + 等宽 -->
          <span class="reading">{{ quantityText(record.rawReading) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'acceptedQuantity'">
          <!-- 与读数区分：绿色表示这个数字已经写进分拣结果 -->
          <span class="accepted">{{ quantityText(record.acceptedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'capturedAt' || column.dataIndex === 'receivedAt'">
          {{ datetime(record[column.dataIndex]) }}
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space v-if="record.status === 'PENDING'" :size="4">
            <a-button
                type="link"
                size="small"
                v-privilege="'scm:sorting:scale:accept'"
                @click="acceptScale(record)"
            >接受
            </a-button>
            <a-button type="link" size="small" danger v-privilege="'scm:sorting:scale:accept'"
                      @click="openReject(record)">驳回</a-button>
          </a-space>
          <span v-else class="hint">{{ record.acceptedBy || record.rejectedBy || '—' }}</span>
        </template>
      </template>
    </a-table>
  </a-drawer>

  <a-modal v-model:open="rejectOpen" title="驳回秤读数" :confirm-loading="scaleBusy" @ok="submitReject">
    <a-form layout="vertical">
      <a-form-item label="驳回原因" required>
        <a-textarea v-model:value="rejectReason" :maxlength="200" :rows="3"
                    placeholder="例如读数未稳定 / 与实物明显不符 / 秤未校准"/>
      </a-form-item>
    </a-form>
    <a-alert v-if="scaleError" type="error" show-icon :message="scaleError"/>
  </a-modal>
</template>

<script setup lang="ts">
import {ref, watch} from 'vue';
import {message, type TableColumnsType} from 'ant-design-vue';
import {sortingApi} from '/@/api/business/scm/sorting-api';
import {datetime} from '../../common/scm-display';
import {quantityText, sortingError, sortingScaleStatuses} from '../sorting-types';
import type {Id, SortingScaleEvent, SortingScaleStatus} from '../sorting-types';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const props = defineProps<{open: boolean; taskId?: Id}>();
const emit = defineEmits<{ 'update:open': [value: boolean] }>();

const scaleLoading = ref(false);
const scaleBusy = ref(false);
const scaleError = ref('');
const scaleEvents = ref<SortingScaleEvent[]>([]);
const rejectOpen = ref(false);
const rejectReason = ref('');
const rejectTarget = ref<SortingScaleEvent>();

const scaleColumns: TableColumnsType = [
    {title: '状态', dataIndex: 'status', width: 90},
    {title: '设备', dataIndex: 'deviceCode', width: 130},
    {title: '原始读数', dataIndex: 'rawReading', align: 'right', width: 110},
    {title: '单位', dataIndex: 'unit', width: 80},
    {title: '稳定', dataIndex: 'stableFlag', align: 'center', width: 90},
    {title: '商品', dataIndex: 'productNameSnapshot', width: 160},
    {title: '商品规格', dataIndex: 'skuCodeSnapshot', width: 140},
    {title: '采集时间', dataIndex: 'capturedAt', width: 175},
    {title: '接收时间', dataIndex: 'receivedAt', width: 175},
    {title: '接受数量', dataIndex: 'acceptedQuantity', align: 'right', width: 110},
    {title: '处理', dataIndex: 'action', align: 'center', fixed: 'right', width: 140},
];

watch(
    () => [props.open, props.taskId] as const,
    ([open, taskId]) => {
      if (!open || taskId === undefined) return;
      scaleError.value = '';
      void loadScale(taskId);
    }
);

async function loadScale(taskId: Id) {
  scaleLoading.value = true;
  try {
    scaleEvents.value = (await sortingApi.scaleEvents(taskId)).data ?? [];
  } catch (error) {
    scaleEvents.value = [];
    scaleError.value = sortingError(error);
  } finally {
    scaleLoading.value = false;
  }
}

async function acceptScale(record: SortingScaleEvent) {
  if (props.taskId === undefined) return;
  scaleBusy.value = true;
  scaleError.value = '';
  try {
    await sortingApi.acceptScaleEvent(record.id, record.version);
    message.success('已接受读数并写入分拣结果');
    await loadScale(props.taskId);
  } catch (error) {
    scaleError.value = sortingError(error);
  } finally {
    scaleBusy.value = false;
  }
}

function openReject(record: SortingScaleEvent) {
  rejectTarget.value = record;
  rejectReason.value = '';
  scaleError.value = '';
  rejectOpen.value = true;
}

async function submitReject() {
  const target = rejectTarget.value;
  if (!target || props.taskId === undefined) return;
  if (!rejectReason.value.trim()) {
    scaleError.value = '请填写驳回原因';
    return;
  }
  scaleBusy.value = true;
  scaleError.value = '';
  try {
    await sortingApi.rejectScaleEvent(target.id, target.version, rejectReason.value.trim());
    message.success('已驳回该读数');
    rejectOpen.value = false;
    await loadScale(props.taskId);
  } catch (error) {
    scaleError.value = sortingError(error);
  } finally {
    scaleBusy.value = false;
  }
}

function close() {
  if (scaleBusy.value) return;
  emit('update:open', false);
}
</script>

<style scoped>
/* 原始读数：放大到 18px，与「接受数量」形成明确的视觉层级 */
.reading {
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* 接受数量：绿色 = 已写进分拣结果，与「还只是一个读数」区分开 */
.accepted {
  font-variant-numeric: tabular-nums;
  color: var(--scm-success, #52c41a);
}

.scale-banner {
  margin: 12px 0;
}

.hint {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}
</style>
