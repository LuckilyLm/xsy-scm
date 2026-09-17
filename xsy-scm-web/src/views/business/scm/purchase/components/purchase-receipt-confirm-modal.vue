<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/purchase/purchase-receive-list.vue（C 无确认弹窗，只有「入库确认」按钮）
复制日期：2026-09-16。Copy First + Adapt。仿 W4 `order-detail.vue` 的实重录入弹窗结构。
剪枝：`confirmInbound` 的库存入库语义（W5 不做库存）、C 的 `receiveFlag` 枚举（A4）。
适配：**A25 非标品必须录入实重**（`effectiveQuantity` 取实重）、
      **A26 容差提示**（超收容差是服务端配置，弹窗只给「剩余可收」这个确定事实）、
      P24 对账量逐行展示（`remaining` / `overReceiptQuantity` / `receiptDifference`）、
      必须覆盖**全部**收货行（40998）、右对齐 + 等宽（A17）、`null` → `—`（A18）、
      loading/error/retry（A27）。
验收：W5 单测、TS 棘轮与 Playwright。 -->
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
      <template #action><a-button @click="load">重试</a-button></template>
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
        :scroll="{ x: 1150 }"
      >
        <template #bodyCell="{ record, column }">
          <template v-if="column.dataIndex === 'plannedQuantity'">
            <span class="num">{{ quantity(record.plannedQuantity) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'cumulativeReceivedQuantity'">
            <span class="num">{{ quantity(record.cumulativeReceivedQuantity) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'remainingQuantity'">
            <span class="num">{{ quantity(record.remainingQuantity) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'overReceiptQuantity'">
            <span class="num">{{ quantity(record.overReceiptQuantity) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'receiptDifference'">
            <span class="num">{{ quantity(record.receiptDifference) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'receivedQuantity'">
            <a-input-number
              string-mode
              :precision="4"
              :min="'0'"
              v-model:value="lineOf(record.id).receivedQuantity"
              aria-label="本次声明数量"
            />
          </template>
          <template v-else-if="column.dataIndex === 'actualWeight'">
            <!-- A25：非标品的有效数量取实重，必须录入 -->
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

      <a-alert
        class="hint-block"
        type="info"
        show-icon
        message="超收容差"
        description="超出剩余可收量的比例由服务端「采购超收容差」配置决定；本次可收上限超出容差时整笔确认会被拒绝（40989），不会部分入库。"
      />
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import { purchaseReceiptApi } from '/@/api/business/scm/purchase-receipt-api';
import type { Id, Receipt, ReceiptConfirmItemPayload, ReceiptItem } from '../purchase-types';
import {
  confirmPayload,
  isNonStandard,
  newConfirmLines,
  quantity,
  validateConfirm,
} from '../purchase-form-model';
import { purchaseError } from '../purchase-errors';

const emit = defineEmits<{ saved: [] }>();

const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const receipt = ref<Receipt>();
const lines = ref<ReceiptConfirmItemPayload[]>([]);
let requestId = 0;

const columns: TableColumnsType<ReceiptItem> = [
  { title: '商品', dataIndex: 'skuName', width: 150 },
  { title: '采购单位', dataIndex: 'purchaseUnit', width: 90 },
  { title: '计划数量', dataIndex: 'plannedQuantity', align: 'right', width: 105 },
  { title: '累计已收', dataIndex: 'cumulativeReceivedQuantity', align: 'right', width: 105 },
  { title: '剩余可收', dataIndex: 'remainingQuantity', align: 'right', width: 105 },
  { title: '超收', dataIndex: 'overReceiptQuantity', align: 'right', width: 95 },
  { title: '差异', dataIndex: 'receiptDifference', align: 'right', width: 105 },
  { title: '本次声明数量', dataIndex: 'receivedQuantity', align: 'right', width: 155 },
  { title: '实重', dataIndex: 'actualWeight', align: 'right', width: 145 },
  { title: '修正原因', dataIndex: 'correctionReason', width: 170 },
];

/** 按收货行 id 取（并缓存）对应的确认行 —— 输入框双向绑定需要稳定对象。 */
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
    const r = await purchaseReceiptApi.detail(id);
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
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({ open });
</script>

<style scoped>
.lines {
  margin: 16px 0;
}
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.hint {
  color: var(--ant-color-text-secondary);
  font-size: 12px;
}
.hint-block {
  margin-top: 8px;
}
</style>
