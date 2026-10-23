<!-- 收货确认弹窗。非标品必须填实重，所有收货行都要填。
     超收容差由服务端配置，前端只显示剩余可收量，不判断超收。 -->
<template>
  <a-modal
    :open="visible"
    :title="receipt?.receiptNo ? `确认收货 ${receipt.receiptNo}` : '确认收货'"
    width="min(1180px, 96vw)"
    :confirm-loading="saving"
    ok-text="确认收货"
    @ok="confirm"
    @cancel="visible = false"
  >
    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action>
        <a-button @click="load">重试</a-button>
      </template>
    </a-alert>

    <a-spin :spinning="loading">
      <a-descriptions v-if="receipt" bordered size="small" :column="3">
        <a-descriptions-item label="采购单号">{{ receipt.purchaseOrderNo || '—' }}</a-descriptions-item>
        <a-descriptions-item label="供应商">{{ receipt.supplierName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="收货仓库">{{ receipt.warehouseName || '—' }}</a-descriptions-item>
      </a-descriptions>

      <a-table
        class="lines"
        :data-source="receipt?.items ?? []"
        :columns="columns"
        row-key="id"
        :pagination="false"
        size="small"
        bordered
        :scroll="{x: scmColumnsWidth(columns)}"
      >
        <template #bodyCell="{record, column}">
          <template v-if="column.dataIndex === 'plannedQuantity'">
            <span class="scm-quantity">{{ quantity(record.plannedQuantity) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'cumulativeReceivedQuantity'">
            <span class="scm-quantity">{{ quantity(record.cumulativeReceivedQuantity) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'remainingQuantity'">
            <span class="scm-quantity">{{ quantity(record.remainingQuantity) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'overReceiptQuantity'">
            <span class="scm-quantity">{{ quantity(record.overReceiptQuantity) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'receiptDifference'">
            <span class="scm-quantity">{{ quantity(record.receiptDifference) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'receivedQuantity'">
            <a-input-number string-mode :precision="4" :min="'0'" v-model:value="lineOf(record.id).receivedQuantity" aria-label="本次声明数量" />
          </template>
          <template v-else-if="column.dataIndex === 'actualWeight'">
            <!-- 非标品的有效数量取实重，必须录入 -->
            <a-input-number
              v-if="isNonStandard(record)"
              string-mode
              :precision="4"
              :min="'0'"
              v-model:value="lineOf(record.id).actualWeight"
              aria-label="实重"
            />
            <span v-else class="hint">标品按声明数量结算</span>
          </template>
          <template v-else-if="column.dataIndex === 'lots'">
            <template v-if="receipt?.warehouseLotTrackingEnabled">
              <a-button type="link" size="small" @click="openLots(record)">
                {{ (lineOf(record.id).lots ?? []).length ? `已登记 ${(lineOf(record.id).lots ?? []).length} 个` : '登记批次' }}
              </a-button>
            </template>
            <span v-else>—</span>
          </template>
          <template v-else-if="column.dataIndex === 'correctionReason'">
            <a-input
              v-if="isNonStandard(record)"
              v-model:value="lineOf(record.id).correctionReason"
              placeholder="实重修正原因（可选）"
              maxlength="500"
              aria-label="实重修正原因"
            />
          </template>
        </template>
      </a-table>

      <FormNote
        title="超收容差"
        :lines="['超出剩余可收量的比例由「采购超收容差」配置决定；本次可收上限超出容差时整笔确认会被拒绝，不会部分入库。']"
      />
    </a-spin>
  </a-modal>

  <a-modal v-model:open="lotOpen" title="登记批次" width="560px" ok-text="确定" @ok="saveLots">
    <p class="scm-note">数量之和必须等于本次收货量；来源生产批号可以不填。</p>
    <div class="lot-rows">
      <div v-for="(lot, index) in lotDraft" :key="index" class="lot-row">
        <a-input v-model:value="lot.sourceLotNo" placeholder="来源生产批号" allow-clear style="width: 220px" />
        <a-input-number v-model:value="lot.quantity" string-mode :precision="4" :min="'0'" aria-label="批次数量" style="width: 150px" />
        <a-button type="link" danger size="small" @click="removeLot(index)">删除</a-button>
      </div>
    </div>
    <a-button type="dashed" size="small" block @click="addLot">添加批次</a-button>
  </a-modal>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {purchaseReceiptApi} from '/@/api/business/scm/purchase-receipt-api';
import type {Id, Receipt, ReceiptConfirmItemPayload, ReceiptConfirmLot, ReceiptItem} from '../purchase-types';
import {confirmPayload, fixed, isNonStandard, newConfirmLines, quantity, validateConfirm} from '../purchase-form-model';
import {purchaseError} from '../purchase-errors';
import FormNote from '/@/components/framework/form-note/index.vue';

const emit = defineEmits<{saved: []}>();

const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const receipt = ref<Receipt>();
const lines = ref<ReceiptConfirmItemPayload[]>([]);
const lotOpen = ref(false);
const lotLineId = ref<Id>();
const lotDraft = ref<ReceiptConfirmLot[]>([]);
let requestId = 0;

const columns: TableColumnsType<ReceiptItem> = [
  {title: '商品', dataIndex: 'skuName', width: 150},
  {title: '采购单位', dataIndex: 'purchaseUnit', width: 76},
  {title: '计划数量', dataIndex: 'plannedQuantity', width: 105},
  {title: '累计已收', dataIndex: 'cumulativeReceivedQuantity', width: 105},
  {title: '剩余可收', dataIndex: 'remainingQuantity', width: 105},
  {title: '超收', dataIndex: 'overReceiptQuantity', width: 95},
  {title: '差异', dataIndex: 'receiptDifference', width: 105},
  {title: '本次声明数量', dataIndex: 'receivedQuantity', width: 155},
  {title: '批次', dataIndex: 'lots', width: 130},
  {title: '实重', dataIndex: 'actualWeight', width: 145},
  {title: '修正原因', dataIndex: 'correctionReason', width: 170},
];

/** 按收货行 id 取确认行；没有就新建并缓存，保证输入框绑定的对象不变。 */
function lineOf(receiptItemId: Id): ReceiptConfirmItemPayload {
  let line = lines.value.find((row) => String(row.receiptItemId) === String(receiptItemId));
  if (!line) {
    line = {
      receiptItemId,
      version: 0,
      receivedQuantity: '0.0000',
      actualWeight: null,
      weightSource: null,
      correctionReason: null,
    };
    lines.value = [...lines.value, line];
  }
  return line;
}

async function open(id: Id) {
  const current = ++requestId;
  visible.value = true;
  error.value = '';
  receipt.value = undefined;
  lines.value = [];
  loading.value = true;
  try {
    const r = await purchaseReceiptApi.detail(id, {suppressGlobalErrorMessage: true});
    if (current !== requestId) {
      return;
    }
    receipt.value = r.data;
    lines.value = newConfirmLines(r.data);
  } catch (e) {
    if (current === requestId) {
      error.value = purchaseError(e);
    }
  } finally {
    if (current === requestId) {
      loading.value = false;
    }
  }
}

async function load() {
  if (receipt.value?.id) {
    await open(receipt.value.id);
  }
}

/** 打开某一行的批次登记：首次用本次收货量预填一条，合计必须等于本次收货量。 */
function openLots(record: ReceiptItem) {
  const line = lineOf(record.id);
  lotLineId.value = record.id;
  const existing = line.lots ?? [];
  if (existing.length) {
    lotDraft.value = existing.map((lot) => ({...lot}));
  } else {
    const effective = isNonStandard(record) ? line.actualWeight ?? line.receivedQuantity : line.receivedQuantity;
    lotDraft.value = [{sourceLotNo: '', quantity: effective || '0.0000'}];
  }
  lotOpen.value = true;
}

function addLot() {
  lotDraft.value = [...lotDraft.value, {sourceLotNo: '', quantity: '0.0000'}];
}

function removeLot(index: number) {
  lotDraft.value = lotDraft.value.filter((_, i) => i !== index);
}

function saveLots() {
  const line = lineOf(lotLineId.value as Id);
  line.lots = lotDraft.value.map((lot) => ({
    sourceLotNo: lot.sourceLotNo?.trim() ? lot.sourceLotNo.trim() : null,
    quantity: fixed(lot.quantity),
  }));
  lotOpen.value = false;
}

async function confirm() {
  const target = receipt.value;
  if (!target) {
    return;
  }
  const invalid = validateConfirm(target, lines.value);
  if (invalid) {
    error.value = invalid;
    return;
  }
  error.value = '';
  saving.value = true;
  try {
    await purchaseReceiptApi.confirm(confirmPayload(target, lines.value));
    message.success('收货已确认');
    visible.value = false;
    emit('saved');
  } catch {
    // 确认失败只走全局 toast；弹窗里的 error 留给提交前的逐行校验
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>

<style scoped>
.lines {
  margin: 16px 0;
}

.hint {
  color: var(--scm-text-secondary);
  font-size: 12px;
}

.lot-rows {
  margin-bottom: 12px;
}

.lot-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
</style>
