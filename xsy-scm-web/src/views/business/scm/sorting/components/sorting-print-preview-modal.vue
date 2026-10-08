<template>
  <a-modal :open="open" title="分拣单打印预览" :width="1000" :footer="null" @cancel="close">
    <div class="print-toolbar">
      <a-space>
        <a-button :disabled="printLoading || !print" @click="emit('open-ticket', printTaskId)"
                  v-privilege="'scm:sorting:task:query'">选择模板并打印小票</a-button>
        <a-button :disabled="printLoading || !print" @click="emit('reload')">重新预览</a-button>
        <a-button
            type="primary"
            v-privilege="'scm:sorting:task:print'"
            :disabled="!print || printing || printVersion == null"
            :loading="printing"
            @click="emit('record')"
        >登记打印
        </a-button>
      </a-space>
    </div>
    <a-spin :spinning="printLoading">
      <div v-if="print" class="sorting-print">
        <section class="print-ticket">
          <h1>分拣单</h1>
          <p>单号：{{ print.taskNo }}</p>
          <p>仓库：{{ print.warehouseNameSnapshot }}　分拣员：{{ print.assigneeName || '未指派' }}</p>
          <p>状态：{{ statusDesc(print.status) }}　行数：{{ print.items.length }}　已打印：{{ print.printCount || '—' }}</p>
          <p>生成时间：{{ datetime(print.generatedAt) }}</p>
          <table>
            <thead>
            <tr>
              <th>订单号</th>
              <th>客户</th>
              <th>商品 / 规格</th>
              <th>单位</th>
              <th class="numeric">计划量</th>
              <th class="numeric">分拣量</th>
              <th>结果</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="item in print.items" :key="item.id">
              <td>{{ item.orderNoSnapshot }}</td>
              <td>{{ item.customerNameSnapshot }}</td>
              <td>
                {{ item.productNameSnapshot }} {{ item.specNameSnapshot }}
                <a-tag v-if="isGiftRow(item)" color="purple">赠品</a-tag>
              </td>
              <td>{{ item.saleUnitSnapshot }}</td>
              <td class="numeric">{{ quantityText(item.plannedQuantitySnapshot) }}</td>
              <td class="numeric">{{ quantityText(item.sortedQuantity) }}</td>
              <td>{{ isGiftRow(item) ? '—' : resultDesc(item.result) }}</td>
            </tr>
            </tbody>
          </table>
          <p class="print-note">本单为分拣作业凭据；未录入的分拣量显示“—”。分拣不回写订单、不改库存。</p>
        </section>
        <section class="print-labels">
          <h2>商品标签（逐行）</h2>
          <div v-for="item in print.items" :key="`label-${item.id}`" class="label">
            <strong>
              {{ item.productNameSnapshot }}
              <a-tag v-if="isGiftRow(item)" color="purple">赠品</a-tag>
            </strong>
            <p>{{ item.specNameSnapshot || '—' }} / {{ item.saleUnitSnapshot }}</p>
            <p class="label-qty">
              计划 {{ quantityText(item.plannedQuantitySnapshot) }}　实分 {{ quantityText(item.sortedQuantity) }}
            </p>
            <p>{{ item.customerNameSnapshot }}</p>
            <p class="label-no">{{ item.orderNoSnapshot }} · {{ print.taskNo }}</p>
          </div>
        </section>
      </div>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import {datetime} from '../../common/scm-display';
import {quantityText} from '../sorting-types';
import type {Id, SortingPrint, SortingTaskItem} from '../sorting-types';

defineProps<{
  open: boolean;
  printTaskId?: Id;
  printVersion?: number;
  printLoading: boolean;
  printing: boolean;
  print?: SortingPrint;
  statusDesc: (status?: string | null) => string;
  resultDesc: (result?: string | null) => string;
  isGiftRow: (record: SortingTaskItem) => boolean;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
  'open-ticket': [taskId?: Id];
  reload: [];
  record: [];
}>();

function close() {
  emit('update:open', false);
}
</script>

<style scoped>
.print-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin: 12px 0;
}

.sorting-print {
  background: #fff;
  color: #1f2329;
  padding: 24px;
}

.sorting-print h1 {
  font-size: 24px;
  margin: 0 0 12px;
}

.sorting-print h2 {
  font-size: 18px;
  margin: 24px 0 12px;
}

.sorting-print table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 12px;
}

.sorting-print th,
.sorting-print td {
  border: 1px solid #e5e6eb;
  padding: 8px;
  text-align: left;
}

.sorting-print .numeric {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.print-note {
  margin-top: 16px;
}

.print-labels {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.print-labels h2 {
  width: 100%;
}

.label {
  width: 220px;
  border: 1px dashed #86909c;
  padding: 12px;
  break-inside: avoid;
}

.label strong {
  font-size: 15px;
}

.label p {
  margin: 4px 0;
}

.label-qty {
  font-variant-numeric: tabular-nums;
}

.label-no {
  font-size: 11px;
  color: #4e5969;
}
</style>
