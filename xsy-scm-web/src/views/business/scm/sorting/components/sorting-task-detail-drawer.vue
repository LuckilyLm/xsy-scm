<template>
  <!-- workspace：分拣工作台 —— 明细表 scroll.x 1420，且录入时要把任务头、进度与明细行
       放在同一屏里对照，横向空间即作业面。 -->
  <a-drawer :open="open" title="分拣任务详情" :width="scmDrawerWidth('workspace')" :destroy-on-close="true" @close="emit('update:open', false)">
    <a-alert v-if="detailError" :message="detailError" type="error" show-icon>
      <template #action>
        <a-button @click="emit('reload')">刷新任务</a-button>
      </template>
    </a-alert>
    <a-spin :spinning="detailLoading">
      <template v-if="detail">
        <div class="task-heading">
          <div>
            <h2>{{ detail.task.taskNo }}</h2>
            <span>{{ detail.task.warehouseNameSnapshot }} · 创建于 {{ datetime(detail.task.createdAt) }}</span>
          </div>
          <a-space wrap>
            <a-tag :color="SCM_SORTING_TASK_STATUS_COLOR[detail.task.status]">{{ statusDesc(detail.task.status) }}</a-tag>
            <a-button
                v-if="SCM_SORTING_PRINTABLE_STATUS.includes(detail.task.status)"
                v-privilege="'scm:sorting:task:query'"
                @click="emit('openTicket', detail.task.id)"
            >打印小票</a-button>
            <a-button
                v-if="SCM_SORTING_PRINTABLE_STATUS.includes(detail.task.status)"
                v-privilege="'scm:sorting:task:print'"
                @click="emit('openPrint', detail.task)"
            >打印</a-button>
            <a-button
                v-if="isWorking(detail.task.status)"
                v-privilege="'scm:sorting:task:assign'"
                :disabled="busy"
                @click="emit('action', 'assign', detail.task)"
            >{{ detail.task.assigneeEmployeeId == null ? '指派' : '改派' }}</a-button>
            <a-button
                v-if="isWorking(detail.task.status)"
                type="primary"
                v-privilege="'scm:sorting:task:complete'"
                :disabled="busy"
                @click="emit('complete', detail.task)"
            >完成任务</a-button>
            <a-button
                v-if="isWorking(detail.task.status)"
                danger
                v-privilege="'scm:sorting:task:cancel'"
                :disabled="busy"
                @click="emit('action', 'cancel', detail.task)"
            >取消任务</a-button>
            <a-button
                v-if="detail.task.status === 'COMPLETED'"
                v-privilege="'scm:sorting:task:reopen'"
                :disabled="busy"
                @click="emit('action', 'reopen', detail.task)"
            >重开任务</a-button>
          </a-space>
        </div>

        <a-descriptions bordered size="small" :column="3" class="task-desc">
          <a-descriptions-item label="受指派人">{{ detail.task.assigneeName || '未指派' }}</a-descriptions-item>
          <a-descriptions-item label="明细行数">{{ detail.task.itemCount }}</a-descriptions-item>
          <a-descriptions-item label="已处理行数">{{ detail.task.processedCount }} / {{ detail.task.itemCount }}</a-descriptions-item>
          <a-descriptions-item label="开始分拣">{{ datetime(detail.task.startedAt) }}</a-descriptions-item>
          <a-descriptions-item label="完成时间">{{ datetime(detail.task.completedAt) }}</a-descriptions-item>
          <a-descriptions-item label="取消时间">{{ datetime(detail.task.cancelledAt) }}</a-descriptions-item>
          <a-descriptions-item label="打印次数">{{ detail.task.printCount ? detail.task.printCount : '—' }}</a-descriptions-item>
          <a-descriptions-item label="最近打印">{{ datetime(detail.task.lastPrintedAt) }}</a-descriptions-item>
          <a-descriptions-item label="版本">{{ detail.task.version }}</a-descriptions-item>
          <a-descriptions-item label="备注" :span="3">{{ detail.task.remark || '—' }}</a-descriptions-item>
        </a-descriptions>

        <a-alert
            type="info"
            show-icon
            class="entry-hint"
            message="分拣不回写订单、不改库存"
            description="这里录入的数量只写在本任务明细上，作为后续出库与结算的实发依据；订单行的实发量与结算金额、库存余额与流水都不会因此变化。计划量是建单时冻结的快照，之后订单侧修改不追溯。"
        />
        <a-alert v-if="!canEditItems" type="warning" show-icon :message="readOnlyReason"/>
        <a-alert v-if="entryError" type="error" show-icon :message="entryError"/>

        <a-table
            :id="SCM_SORTING_TABLE_ID.TASK_ITEM"
            size="small"
            :data-source="detail.items"
            :columns="itemColumns"
            row-key="id"
            bordered
            :pagination="false"
            :scroll="{x: 1420}"
        >
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'product'">
              <div>
                {{ record.productNameSnapshot }}
                <a-tag v-if="isGiftRow(record)" color="purple">赠品</a-tag>
              </div>
              <a-typography-text type="secondary">
                {{ record.specNameSnapshot || '—' }} · {{ productTypeDesc(record.productTypeSnapshot) }}
                <template v-if="isGiftRow(record)"> · 满赠赠品，无需录入实分量</template>
                <template v-else-if="record.occupationStatus === 'RELEASED'"> · 占用已释放</template>
              </a-typography-text>
            </template>
            <template v-else-if="column.dataIndex === 'plannedQuantitySnapshot'">
              <span class="scm-quantity">{{ quantityText(record.plannedQuantitySnapshot) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'sortedQuantity'">
              <a-input-number
                  v-if="canEditRow(record)"
                  :value="drafts[String(record.id)]?.sortedQuantity ?? ''"
                  string-mode
                  :min="'0'"
                  :precision="4"
                  :disabled="busy"
                  style="width: 130px"
                  :aria-label="`分拣量 ${record.orderNoSnapshot} ${record.productNameSnapshot}`"
                  @update:value="updateDraft(record.id, 'sortedQuantity', $event)"
              />
              <span v-else class="scm-quantity">{{ quantityText(record.sortedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'result'">
              <a-select
                  v-if="canEditRow(record)"
                  :value="drafts[String(record.id)]?.result ?? ''"
                  :options="resultOptions"
                  placeholder="未录入"
                  allow-clear
                  :disabled="busy"
                  style="width: 130px"
                  :aria-label="`分拣结果 ${record.productNameSnapshot}`"
                  @change="updateDraft(record.id, 'result', $event)"
              />
              <a-tag v-else :color="resultColor(record.result)">{{ resultDesc(record.result) }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'reason'">
              <template v-if="canEditRow(record)">
                <a-input
                    :value="drafts[String(record.id)]?.reason ?? ''"
                    :maxlength="500"
                    :status="entryErrors[String(record.id)] ? 'error' : undefined"
                    :disabled="busy"
                    placeholder="非正常结果必填"
                    :aria-label="`分拣原因 ${record.productNameSnapshot}`"
                    @update:value="updateDraft(record.id, 'reason', $event)"
                />
                <a-typography-text v-if="entryErrors[String(record.id)]" type="danger" class="cell-error">
                  {{ entryErrors[String(record.id)] }}
                </a-typography-text>
              </template>
              <span v-else>{{ record.reason || '—' }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'sortedAt'">{{ datetime(record.sortedAt) }}</template>
            <template v-else-if="column.dataIndex === 'occupationStatus'">
              <span v-if="isGiftRow(record)">—</span>
              <a-tag v-else :color="SCM_SORTING_OCCUPATION_COLOR[record.occupationStatus]">
                {{ occupationDesc(record.occupationStatus) }}
              </a-tag>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>

        <div class="entry-footer">
          <a-typography-text type="secondary">
            只提交改动过的行，每行带它自己读到的版本；若某行在录入期间被别人改过，本次提交会被服务端拒绝并要求刷新。
          </a-typography-text>
          <a-button
              type="primary"
              v-privilege="'scm:sorting:item:update'"
              :disabled="!canEditItems || busy || !dirtyCount"
              :loading="busy"
              @click="emit('submitEntry')"
          >提交分拣录入（{{ dirtyCount }} 行）</a-button>
        </div>
      </template>
      <a-empty v-else-if="!detailLoading" description="任务尚未加载"/>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {datetime} from '../../common/scm-display';
import {
  SCM_SORTING_OCCUPATION_COLOR,
  SCM_SORTING_OCCUPATION_ENUM,
  SCM_SORTING_PRINTABLE_STATUS,
  SCM_SORTING_RESULT_COLOR,
  SCM_SORTING_RESULT_ENUM,
  SCM_SORTING_TABLE_ID,
  SCM_SORTING_TASK_STATUS_COLOR,
} from '/@/constants/business/scm/sorting-const';
import {quantityText} from '../sorting-types';
import type {Id, SortingTask, SortingTaskDetail, SortingTaskItem} from '../sorting-types';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

type ActionMode = 'assign' | 'cancel' | 'reopen';
type DraftField = 'sortedQuantity' | 'result' | 'reason';
type EntryDraft = {sortedQuantity: string; result: string; reason: string};

defineProps<{
  open: boolean;
  detail?: SortingTaskDetail;
  detailLoading: boolean;
  detailError: string;
  entryError: string;
  busy: boolean;
  canEditItems: boolean;
  readOnlyReason: string;
  drafts: Record<string, EntryDraft>;
  entryErrors: Record<string, string>;
  dirtyCount: number;
  statusDesc: (status?: string | null) => string;
  isWorking: (status: string) => boolean;
  productTypeDesc: (type?: string | null) => string;
  resultDesc: (result?: string | null) => string;
  isGiftRow: (record: SortingTaskItem) => boolean;
  canEditRow: (record: SortingTaskItem) => boolean;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
  reload: [];
  openTicket: [taskId: Id];
  openPrint: [task: SortingTask];
  action: [mode: ActionMode, task: SortingTask];
  complete: [task: SortingTask];
  draftChange: [itemId: Id, field: DraftField, value: string];
  submitEntry: [];
}>();

const resultOptions = Object.values(SCM_SORTING_RESULT_ENUM).map((item) => ({value: item.value, label: item.desc}));

const itemColumns = ref<TableColumnsType<SortingTaskItem>>([
  {title: '订单号', dataIndex: 'orderNoSnapshot', width: 170},
  {title: '客户', dataIndex: 'customerNameSnapshot', width: 150},
  {title: '商品 / 商品规格', dataIndex: 'product', width: 220},
  {title: '单位', dataIndex: 'saleUnitSnapshot', align: 'center', width: 80},
  {title: '计划量', dataIndex: 'plannedQuantitySnapshot', align: 'right', width: 110},
  {title: '分拣量', dataIndex: 'sortedQuantity', align: 'right', width: 150},
  {title: '结果', dataIndex: 'result', align: 'center', width: 140},
  {title: '原因', dataIndex: 'reason', width: 220},
  {title: '录入人', dataIndex: 'sortedBy', width: 120},
  {title: '录入时间', dataIndex: 'sortedAt', width: 170},
  {title: '占用', dataIndex: 'occupationStatus', align: 'center', width: 100},
]);

function resultColor(result?: string | null) {
  return (result && SCM_SORTING_RESULT_COLOR[result]) || 'default';
}

function occupationDesc(occupation?: string | null) {
  return occupation ? SCM_SORTING_OCCUPATION_ENUM[occupation]?.desc ?? occupation : '—';
}

function updateDraft(itemId: Id, field: DraftField, value: unknown) {
  emit('draftChange', itemId, field, value == null ? '' : String(value));
}
</script>

<style scoped>
.task-heading {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: space-between;
  margin-bottom: 16px;
}

.task-heading h2 {
  font-size: 20px;
  margin: 0 0 4px;
}

.task-desc {
  margin-bottom: 12px;
}

.entry-hint {
  margin-bottom: 12px;
}

.cell-error {
  display: block;
  font-size: 12px;
}

.entry-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 16px;
}

</style>
