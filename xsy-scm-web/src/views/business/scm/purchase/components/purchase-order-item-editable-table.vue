<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/purchase/purchase-item-list.vue（**降级**）
复制日期：2026-09-16。Copy First + Adapt。
剪枝：C 的独立明细 CRUD 页（A-D1：明细只能随采购单整体提交）、`resizable`（A1）、
      C 的 `unitPrice`/`receiveFlag` 列（W5 用 `purchasePrice` + 派生对账量）。
适配：`purchase-item-list.vue` → **表单内可编辑表格**（A19，修 C 的 H13/H29）；
      **A31 采购行多需求分配编辑器**（`allocations[]` 可增 / 删 / 改，不是单个 `demandId`）；
      **A32 需求单位 ≠ 采购单位时禁止加入分配**；右对齐 + 等宽（A17）；`null` → `—`（A18）；
      `scm:purchase:*`（A22）；`v-privilege`（A30）。
验收：W5 单测、TS 棘轮与 Playwright。 -->
<template>
  <a-table
      id="purchase-order-item-table"
      size="small"
      bordered
      :data-source="items"
      :columns="columns"
      :pagination="false"
      :scroll="{ x: 1250 }"
      :row-key="(_row: OrderItem, index?: number) => index ?? 0"
      :expanded-row-keys="items.map((_row, index) => index)"
  >
    <template #bodyCell="{ record, column, index }">
      <template v-if="column.dataIndex === 'skuId'">
        <SkuSelect
            :value="record.skuId"
            width="270px"
            :disabled-statuses="[]"
            @update:value="(v: Id | Id[] | undefined) => onSkuChange(record, v)"
        />
      </template>
      <template v-else-if="column.dataIndex === 'plannedQuantity'">
        <a-input-number
            string-mode
            :precision="4"
            :min="'0.0001'"
            v-model:value="record.plannedQuantity"
            :aria-label="'采购数量 ' + (index + 1)"
            @blur="normalizeNumber(record, 'plannedQuantity')"
        />
      </template>
      <template v-else-if="column.dataIndex === 'purchasePrice'">
        <a-input-number
            string-mode
            :precision="4"
            :min="'0'"
            :allow-clear="true"
            v-model:value="record.purchasePrice"
            :aria-label="'采购单价 ' + (index + 1)"
            @blur="normalizeNumber(record, 'purchasePrice')"
        />
      </template>
      <template v-else-if="column.dataIndex === 'allocated'">
        <span class="num">{{ allocatedOnItem(record) }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'allocationCount'">
        <a-tag>{{ (record.allocations ?? []).length }} 条需求</a-tag>
      </template>
      <template v-else-if="column.dataIndex === 'action'">
        <a-button danger type="link" @click="items.splice(index, 1)">移除</a-button>
      </template>
    </template>

    <!-- A31：一行的分配是**集合**，用展开行承载「多需求分配」编辑 -->
    <template #expandedRowRender="slot">
      <div class="alloc-block">
        <a-table
            size="small"
            :data-source="asItem(slot.record).allocations ?? []"
            :columns="allocationColumns"
            :pagination="false"
            :row-key="(row: Allocation) => String(row.demandId)"
        >
          <template #bodyCell="{ record: alloc, column, index: allocIndex }">
            <template v-if="column.dataIndex === 'salesOrderNo'">{{ alloc.salesOrderNo || '—' }}</template>
            <template v-else-if="column.dataIndex === 'demandUnit'">{{ alloc.demandUnit || '—' }}</template>
            <template v-else-if="column.dataIndex === 'quantity'">
              <a-input-number
                  string-mode
                  :precision="4"
                  :min="'0.0001'"
                  v-model:value="alloc.quantity"
                  aria-label="分配数量"
                  @blur="normalizeNumber(alloc, 'quantity')"
              />
            </template>
            <template v-else-if="column.dataIndex === 'demandStatus'">
              <a-tag>{{ SCM_DEMAND_STATUS_ENUM[alloc.demandStatus ?? '']?.desc || '—' }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <a-button danger type="link" @click="removeAllocation(asItem(slot.record), allocIndex)">移除</a-button>
            </template>
          </template>
        </a-table>

        <a-space class="alloc-actions">
          <a-button size="small" v-privilege="'scm:purchase:demand:allocate'"
                    @click="openDemandPicker(asItem(slot.record))">
            添加需求分配
          </a-button>
          <span v-if="!asItem(slot.record).skuId" class="hint">请先选择采购商品</span>
          <span v-else-if="!(asItem(slot.record).allocations ?? []).length" class="hint">
            该行没有需求来源：可以只按采购数量下单（采购量 ≠ 需求量）
          </span>
        </a-space>
      </div>
    </template>
  </a-table>

  <a-button class="add-line" @click="items.push(newOrderItem())">添加采购行</a-button>

  <!-- 需求选择：只列**同一 SKU**且仍有可分配余量的需求（跨 SKU → 40995） -->
  <a-modal :open="picker.open" title="选择采购需求" width="900px" :footer="null" @cancel="picker.open = false">
    <a-alert v-if="picker.error" :message="picker.error" type="error" show-icon/>
    <a-spin :spinning="picker.loading">
      <a-table size="small" :data-source="picker.rows" :columns="pickerColumns" :pagination="false" row-key="id">
        <template #bodyCell="{ record: demand, column }">
          <template v-if="column.dataIndex === 'unallocatedQuantity'">
            <span class="num">{{ quantity(demand.unallocatedQuantity) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'demandUnit'">{{ demand.demandUnit || '—' }}</template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-button type="link" @click="pick(demand)">加入</a-button>
          </template>
        </template>
      </a-table>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import {reactive} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {purchaseDemandApi} from '/@/api/business/scm/purchase-demand-api';
import {SCM_DEMAND_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import type {Allocation, Demand, Id, OrderItem} from '../purchase-types';
import {
  allocatedOnItem,
  hasAllocation,
  newAllocation,
  newOrderItem,
  normalizeTyped,
  quantity,
  unitMismatch,
} from '../purchase-form-model';
import {purchaseError} from '../purchase-errors';

defineProps<{ items: OrderItem[] }>();

const columns: TableColumnsType<OrderItem> = [
  {title: '商品 / 规格 / SKU', dataIndex: 'skuId', width: 300},
  {title: '采购数量', dataIndex: 'plannedQuantity', align: 'right', width: 150},
  {title: '采购单价', dataIndex: 'purchasePrice', align: 'right', width: 150},
  {title: '已分配合计', dataIndex: 'allocated', align: 'right', width: 140},
  {title: '需求来源', dataIndex: 'allocationCount', align: 'center', width: 120},
  {title: '操作', dataIndex: 'action', align: 'right', width: 90},
];

const allocationColumns: TableColumnsType<Allocation> = [
  {title: '来源销售单', dataIndex: 'salesOrderNo', width: 200},
  {title: '需求单位', dataIndex: 'demandUnit', width: 100},
  {title: '本次分配数量', dataIndex: 'quantity', align: 'right', width: 170},
  {title: '需求状态', dataIndex: 'demandStatus', align: 'center', width: 120},
  {title: '操作', dataIndex: 'action', align: 'right', width: 90},
];

const pickerColumns: TableColumnsType<Demand> = [
  {title: '来源销售单', dataIndex: 'salesOrderNoSnapshot', width: 210},
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '需求单位', dataIndex: 'demandUnit', width: 100},
  {title: '剩余可分配', dataIndex: 'unallocatedQuantity', align: 'right', width: 140},
  {title: '需求日期', dataIndex: 'demandDate', width: 120},
  {title: '操作', dataIndex: 'action', align: 'right', width: 90},
];

const picker = reactive({
  open: false,
  loading: false,
  error: '',
  rows: [] as Demand[],
  item: undefined as OrderItem | undefined,
});

/**
 * `expandedRowRender` 的 slot props 在 antd 里是 `any`，直接解构会让 `record.allocations`
 * 变成隐式 any 访问。这里用一次显式收窄把类型边界钉住，而不是给整块模板加 `@ts-ignore`。
 */
function asItem(record: unknown): OrderItem {
  return record as OrderItem;
}

function removeAllocation(item: OrderItem, index: number) {
  item.allocations = (item.allocations ?? []).filter((_row, i) => i !== index);
}

/**
 * 失焦时把输入框里的裸数显示成四位定点（`"2"` → `"2.0000"`）。
 *
 * `a-input-number` 只在 blur 之后才按 `precision` 归一（读的是组件内部 `inputValue`），
 * 而 `props.value` 的 watch 会因为「新值等于当前解析值」而**跳过**回写，
 * 于是显示值会一直停在 `"2"`。这里直接写模型：不等值才赋值，避免多余渲染。
 */
function normalizeNumber(target: object, key: 'plannedQuantity' | 'purchasePrice' | 'quantity') {
  const self = target as Record<string, string | null | undefined>;
  const normalized = normalizeTyped(self[key]);
  if (normalized !== self[key]) {
    self[key] = normalized;
  }
}

function onSkuChange(item: OrderItem, value: Id | Id[] | undefined) {
  item.skuId = Array.isArray(value) ? value[0] : value;
  // 换 SKU 后原有的分配必然不匹配（40995），直接清空比留着一堆无效行更安全。
  item.allocations = [];
}

async function openDemandPicker(item: OrderItem) {
  if (!item.skuId) {
    message.warning('请先选择采购商品');
    return;
  }
  picker.open = true;
  picker.item = item;
  picker.error = '';
  picker.loading = true;
  picker.rows = [];
  try {
    const r = await purchaseDemandApi.query({pageNum: 1, pageSize: 50, skuId: item.skuId});
    // 只保留还有余量的需求（`unallocated = 0` 的加进去必然 40082）
    picker.rows = r.data.list.filter((d) => Number(d.unallocatedQuantity ?? '0') > 0);
    if (!picker.rows.length) {
      picker.error = '该 SKU 没有可分配的采购需求，请先在「采购需求」页生成需求';
    }
  } catch (e) {
    picker.error = purchaseError(e);
  } finally {
    picker.loading = false;
  }
}

function pick(demand: Demand) {
  const item = picker.item!;
  if (hasAllocation(item, demand.id)) {
    message.warning('该需求已经在这一行上，请直接修改它的分配数量');
    return;
  }
  // A32 / Q17：单位不一致时**拒绝**，不猜换算系数、不换单位字符串。
  if (unitMismatch(item, demand)) {
    picker.error = `需求单位 ${demand.demandUnit} 与采购单位 ${item.purchaseUnit} 不一致：W5 不做自动换算，请改用与需求单位一致的供应商采购配置`;
    return;
  }
  item.allocations = [...(item.allocations ?? []), newAllocation(demand)];
  picker.open = false;
  picker.error = '';
}

defineExpose({openDemandPicker});
</script>

<style scoped>
.alloc-block {
  padding: 4px 0 8px;
}

.alloc-actions {
  margin-top: 8px;
}

.add-line {
  margin-top: 12px;
}

.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.hint {
  color: var(--ant-color-text-secondary);
  font-size: 12px;
}
</style>
