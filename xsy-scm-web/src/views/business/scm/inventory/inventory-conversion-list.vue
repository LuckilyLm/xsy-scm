<!--
  库存规格转换（规格转换波次新增）。

  状态机：PENDING → COMPLETED | REJECTED，两个终态都不可回退（流水 append-only）。
  创建即提交待审核：转换会把**两个 SKU** 的余额同时改掉，而折算率是人工声明的，
  没有审批等于录单人可以单方面决定「一箱等于多少 kg」。

  **跨 SKU、同仓库**：源规格 → 目标规格（整件 → 散装）。跨仓搬运是「调拨」，不是转换。

  页面上要讲清楚的一件事：**两个单位都由单据声明，后端会与各自 SKU 的余额记账单位比对**，
  不一致直接失败（41059 / 41060）—— 库存不做自动换算。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="转换单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.conversionNo" placeholder="转换单号" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="190px" />
      </a-form-item>
      <a-form-item label="类型" class="smart-query-form-item">
        <a-select
          v-model:value="queryForm.convertType"
          :options="typeOptions"
          placeholder="全部"
          allow-clear
          style="width: 140px"
        />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select
          v-model:value="queryForm.status"
          :options="statusOptions"
          placeholder="全部"
          allow-clear
          style="width: 130px"
        />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:conversion:query'">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action><a-button @click="queryData">重试</a-button></template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button type="primary" @click="openCreate" v-privilege="'scm:inventory:conversion:add'">
          新建转换单
        </a-button>
        <a-typography-text type="secondary" style="margin-left: 12px">
          创建后进入待审核；审批通过才调整库存，并生成不可删除的两条流水（源出 / 目标入）。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
          v-model="columns"
          :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_CONVERSION"
          :refresh="queryData"
        />
      </div>
    </a-row>

    <a-table
      :id="SCM_INVENTORY_TABLE_ID.CONVERSION"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{ emptyText: '暂无规格转换单' }"
      :scroll="{ x: 1400 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'convertType'">
          <a-tag color="blue">{{ record.convertTypeDesc || record.convertType }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="statusColor(record.status)">{{ record.statusDesc || record.status }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'auditedAt'">{{ datetime(record.auditedAt) }}</template>
        <template v-else-if="column.dataIndex === 'createdAt'">{{ datetime(record.createdAt) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button
              v-if="record.status === 'PENDING'"
              type="link"
              size="small"
              @click="openEdit(record)"
              v-privilege="'scm:inventory:conversion:update'"
            >
              编辑
            </a-button>
            <a-button
              v-if="record.status === 'PENDING'"
              type="link"
              size="small"
              @click="openAudit(record, 'approve')"
              v-privilege="'scm:inventory:conversion:approve'"
            >
              审批
            </a-button>
            <a-button
              v-if="record.status === 'PENDING'"
              type="link"
              size="small"
              danger
              @click="openAudit(record, 'reject')"
              v-privilege="'scm:inventory:conversion:reject'"
            >
              驳回
            </a-button>
            <a-button
              v-if="record.status === 'PENDING'"
              type="link"
              size="small"
              danger
              @click="onDelete(record)"
              v-privilege="'scm:inventory:conversion:delete'"
            >
              删除
            </a-button>
          </a-space>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
        show-size-changer
        show-quick-jumper
        v-model:current="queryForm.pageNum"
        v-model:page-size="queryForm.pageSize"
        :total="total"
        @change="queryData"
        :show-total="(n: number) => `共${n}条`"
      />
    </div>
  </a-card>

  <!-- 新建 / 编辑待审核 -->
  <a-drawer
    :open="drawerOpen"
    :title="form.id ? `编辑转换单 ${form.conversionNo}` : '新建转换单'"
    width="1080"
    @close="closeDrawer"
  >
    <a-alert
      type="info"
      show-icon
      style="margin-bottom: 12px"
      message="折算关系由你显式声明（如 1 箱 = 10 kg），系统不推断。两个单位会与各自 SKU 的库存记账单位比对，不一致会被拒绝。"
    />
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical">
      <a-form-item label="仓库" name="warehouseId">
        <WarehouseSelect v-model:value="form.warehouseId" :options="warehouses" width="260px" />
      </a-form-item>
      <a-form-item label="转换类型" name="convertType">
        <a-radio-group v-model:value="form.convertType" button-style="solid">
          <a-radio-button value="SPLIT">整件拆零（1 件 → N 散装）</a-radio-button>
          <a-radio-button value="COMBINE">组合拆分（N 散装 → 1 件）</a-radio-button>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="原因" name="reason">
        <a-input v-model:value="form.reason" :maxlength="200" show-count placeholder="如：客户要散装 / 整件拆零上架" />
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="form.remark" :rows="2" :maxlength="500" show-count />
      </a-form-item>
      <a-form-item label="转换明细" required>
        <a-table
          size="small"
          :data-source="form.items"
          :columns="itemColumns"
          row-key="_key"
          bordered
          :pagination="false"
          :scroll="{ x: 900 }"
        >
          <template #bodyCell="{ record, column, index }">
            <template v-if="column.dataIndex === 'sourceSkuId'">
              <SkuSelect
                :value="record.sourceSkuId"
                :disabled-statuses="[]"
                width="200px"
                @update:value="(v) => (record.sourceSkuId = Array.isArray(v) ? v[0] : v)"
              />
            </template>
            <template v-else-if="column.dataIndex === 'sourceQuantity'">
              <a-input v-model:value="record.sourceQuantity" placeholder="0.0000" style="width: 110px" />
            </template>
            <template v-else-if="column.dataIndex === 'sourceUnit'">
              <a-input v-model:value="record.sourceUnit" placeholder="如 箱" style="width: 80px" />
            </template>
            <template v-else-if="column.dataIndex === 'targetSkuId'">
              <SkuSelect
                :value="record.targetSkuId"
                :disabled-statuses="[]"
                width="200px"
                @update:value="(v) => (record.targetSkuId = Array.isArray(v) ? v[0] : v)"
              />
            </template>
            <template v-else-if="column.dataIndex === 'targetQuantity'">
              <a-input v-model:value="record.targetQuantity" placeholder="0.0000" style="width: 110px" />
            </template>
            <template v-else-if="column.dataIndex === 'targetUnit'">
              <a-input v-model:value="record.targetUnit" placeholder="如 kg" style="width: 80px" />
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <a-button type="link" size="small" danger @click="removeItem(index)">删除</a-button>
            </template>
          </template>
        </a-table>
        <a-button type="dashed" block style="margin-top: 8px" @click="addItem">+ 添加明细</a-button>
        <a-typography-text type="secondary" style="display: block; margin-top: 8px">
          同一 SKU 可以在多行里出现（既是某行的源、又是另一行的目标，用于链式转换），
          但**同一行的源与目标不能是同一个 SKU**。
        </a-typography-text>
      </a-form-item>
    </a-form>
    <template #footer>
      <a-space>
        <a-button @click="closeDrawer">取消</a-button>
        <a-button type="primary" :loading="saving" @click="onSubmit">保存</a-button>
      </a-space>
    </template>
  </a-drawer>

  <!-- 详情 -->
  <a-drawer :open="detailOpen" title="转换单详情" width="1000" @close="detailOpen = false">
    <a-descriptions :column="2" bordered size="small">
      <a-descriptions-item label="转换单号">{{ detail.conversionNo }}</a-descriptions-item>
      <a-descriptions-item label="类型">
        <a-tag color="blue">{{ detail.convertTypeDesc || detail.convertType }}</a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="状态">
        <a-tag :color="statusColor(detail.status)">{{ detail.statusDesc || detail.status }}</a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="仓库">{{ detail.warehouseName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="原因" :span="2">{{ detail.reason || '—' }}</a-descriptions-item>
      <a-descriptions-item label="审核人">{{ detail.auditor || '—' }}</a-descriptions-item>
      <a-descriptions-item label="审核时间">{{ datetime(detail.auditedAt) }}</a-descriptions-item>
      <a-descriptions-item label="审核意见" :span="2">{{ detail.auditOpinion || '—' }}</a-descriptions-item>
      <a-descriptions-item label="创建时间">{{ datetime(detail.createdAt) }}</a-descriptions-item>
      <a-descriptions-item label="备注">{{ detail.remark || '—' }}</a-descriptions-item>
    </a-descriptions>
    <a-table
      style="margin-top: 12px"
      size="small"
      :data-source="detail.items || []"
      :columns="detailItemColumns"
      row-key="id"
      bordered
      :pagination="false"
      :scroll="{ x: 1100 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'sourceQuantity'">
          <span class="num">{{ quantityText(record.sourceQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'targetQuantity'">
          <span class="num">{{ quantityText(record.targetQuantity) }}</span>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <a-typography-text v-if="detail.status === 'COMPLETED'" type="secondary" style="display: block; margin-top: 8px">
      审批已生成两条流水：源 SKU「转换出」、目标 SKU「转换入」。若要冲销，请新建一张反向转换单
      —— 流水不可修改、不可删除。
    </a-typography-text>
  </a-drawer>

  <!-- 审批 / 驳回 -->
  <a-modal
    :open="auditOpen"
    :title="auditMode === 'approve' ? '审批通过' : '驳回'"
    :confirm-loading="auditSaving"
    :ok-text="auditMode === 'approve' ? '确认审批' : '确认驳回'"
    :ok-type="auditMode === 'approve' ? 'primary' : 'danger'"
    cancel-text="取消"
    @ok="onAuditSubmit"
    @cancel="auditOpen = false"
  >
    <a-alert
      :type="auditMode === 'approve' ? 'warning' : 'info'"
      show-icon
      style="margin-bottom: 12px"
      :message="auditMode === 'approve'
        ? '审批通过会立即按本单明细改动两个 SKU 的库存（源出 / 目标入）并生成不可删除的流水，此操作不可撤销。'
        : '驳回不产生任何库存影响；单据将变为终态，不可再修改或删除。'"
    />
    <a-descriptions :column="1" bordered size="small" style="margin-bottom: 12px">
      <a-descriptions-item label="转换单号">{{ auditRecord.conversionNo }}</a-descriptions-item>
      <a-descriptions-item label="类型">
        {{ auditRecord.convertTypeDesc || auditRecord.convertType }}
      </a-descriptions-item>
      <a-descriptions-item label="原因">{{ auditRecord.reason || '—' }}</a-descriptions-item>
    </a-descriptions>
    <a-form layout="vertical">
      <a-form-item :label="auditMode === 'approve' ? '审核意见（可选）' : '审核意见（必填）'">
        <a-textarea
          v-model:value="auditOpinion"
          :rows="3"
          :maxlength="500"
          show-count
          :placeholder="auditMode === 'approve' ? '可留空' : '请说明驳回原因，录单人据此修改'"
        />
      </a-form-item>
    </a-form>
    <a-typography-text type="secondary">
      提交时会带上打开本单时读到的版本号；若期间折算关系已被修改，系统会要求你刷新后重新审批。
    </a-typography-text>
  </a-modal>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { message, Modal } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import { inventoryConversionApi } from '/@/api/business/scm/inventory-conversion-api';
import { warehouseApi } from '/@/api/business/scm/warehouse-api';
import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
import {
  SCM_INVENTORY_CONVERSION_STATUS_ENUM,
  SCM_INVENTORY_CONVERSION_TYPE_ENUM,
  SCM_INVENTORY_TABLE_ID,
} from '/@/constants/business/scm/inventory-const';
import type {
  InventoryConversion,
  InventoryConversionAdd,
  InventoryConversionQuery,
} from './inventory-types';
import type { Warehouse } from '../purchase/purchase-types';
import { quantityText, singleWarehouseDefault } from './inventory-model';
import { inventoryError } from './inventory-errors';
import { datetime } from '../common/scm-display';

const queryForm = reactive<InventoryConversionQuery>({ pageNum: 1, pageSize: 20 });
const tableData = ref<InventoryConversion[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const warehouses = ref<Warehouse[]>([]);
let requestId = 0;

const typeOptions = Object.values(SCM_INVENTORY_CONVERSION_TYPE_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

const statusOptions = Object.values(SCM_INVENTORY_CONVERSION_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

const columns = ref<TableColumnsType<InventoryConversion>>([
  { title: '转换单号', dataIndex: 'conversionNo', width: 200 },
  { title: '仓库', dataIndex: 'warehouseName', width: 150 },
  { title: '类型', dataIndex: 'convertType', align: 'center', width: 120 },
  { title: '状态', dataIndex: 'status', align: 'center', width: 100 },
  { title: '原因', dataIndex: 'reason', width: 220, ellipsis: true },
  { title: '审核人', dataIndex: 'auditor', width: 130 },
  { title: '审核时间', dataIndex: 'auditedAt', width: 170 },
  { title: '创建时间', dataIndex: 'createdAt', width: 170 },
  { title: '操作', dataIndex: 'action', width: 280, fixed: 'right' },
]);

const itemColumns: TableColumnsType = [
  { title: '源 SKU（转出）', dataIndex: 'sourceSkuId', width: 220 },
  { title: '源数量', dataIndex: 'sourceQuantity', width: 120 },
  { title: '源单位', dataIndex: 'sourceUnit', width: 90 },
  { title: '目标 SKU（转入）', dataIndex: 'targetSkuId', width: 220 },
  { title: '目标数量', dataIndex: 'targetQuantity', width: 120 },
  { title: '目标单位', dataIndex: 'targetUnit', width: 90 },
  { title: '操作', dataIndex: 'action', width: 70 },
];

const detailItemColumns: TableColumnsType = [
  { title: '源 SKU', dataIndex: 'sourceSkuCode', width: 150 },
  { title: '源商品', dataIndex: 'sourceProductName', width: 140 },
  { title: '源数量', dataIndex: 'sourceQuantity', align: 'right', width: 110 },
  { title: '源单位', dataIndex: 'sourceUnit', align: 'center', width: 90 },
  { title: '目标 SKU', dataIndex: 'targetSkuCode', width: 150 },
  { title: '目标商品', dataIndex: 'targetProductName', width: 140 },
  { title: '目标数量', dataIndex: 'targetQuantity', align: 'right', width: 110 },
  { title: '目标单位', dataIndex: 'targetUnit', align: 'center', width: 90 },
];

function statusColor(status?: string) {
  if (status === 'COMPLETED') return 'green';
  if (status === 'REJECTED') return 'red';
  return 'orange';
}

// ------------------------------------------------------------------ 查询

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await inventoryConversionApi.query({ ...queryForm });
    if (id === requestId) {
      tableData.value = r.data.list;
      total.value = r.data.total;
    }
  } catch (e) {
    if (id === requestId) {
      error.value = inventoryError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

async function applySingleWarehouseDefault() {
  try {
    const r = await warehouseApi.list();
    warehouses.value = r.data ?? [];
    const fallback = singleWarehouseDefault(warehouses.value);
    if (fallback !== undefined) {
      queryForm.warehouseId = fallback;
    }
  } catch {
    warehouses.value = [];
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.conversionNo = undefined;
  queryForm.warehouseId = undefined;
  queryForm.convertType = undefined;
  queryForm.status = undefined;
  onSearch();
}

// ------------------------------------------------------------------ 表单

interface EditableItem {
  _key: number;
  sourceSkuId?: string | number;
  sourceQuantity: string;
  sourceUnit: string;
  targetSkuId?: string | number;
  targetQuantity: string;
  targetUnit: string;
  remark?: string;
}

const drawerOpen = ref(false);
const saving = ref(false);
const formRef = ref();
let keySeq = 0;

const form = reactive<{
  id?: string | number;
  conversionNo?: string;
  warehouseId?: string | number;
  convertType?: string;
  reason?: string;
  remark?: string;
  items: EditableItem[];
}>({ items: [] });

const formRules = {
  warehouseId: [{ required: true, message: '请选择仓库' }],
  convertType: [{ required: true, message: '请选择转换类型' }],
};

function addItem() {
  form.items.push({
    _key: ++keySeq,
    sourceQuantity: '',
    sourceUnit: '',
    targetQuantity: '',
    targetUnit: '',
  });
}

function removeItem(index: number) {
  form.items.splice(index, 1);
}

function openCreate() {
  form.id = undefined;
  form.conversionNo = undefined;
  form.warehouseId = queryForm.warehouseId ?? singleWarehouseDefault(warehouses.value);
  form.convertType = 'SPLIT';
  form.reason = undefined;
  form.remark = undefined;
  form.items = [];
  addItem();
  drawerOpen.value = true;
}

async function openEdit(record: InventoryConversion) {
  const r = await inventoryConversionApi.detail(record.id);
  const d = r.data;
  form.id = d.id;
  form.conversionNo = d.conversionNo;
  form.warehouseId = d.warehouseId;
  form.convertType = d.convertType;
  form.reason = d.reason;
  form.remark = d.remark;
  form.items = (d.items ?? []).map((i) => ({
    _key: ++keySeq,
    sourceSkuId: i.sourceSkuId,
    // 定点字符串直接回填，不转 number（避免精度与类型问题）
    sourceQuantity: i.sourceQuantity ?? '',
    sourceUnit: i.sourceUnit ?? '',
    targetSkuId: i.targetSkuId,
    targetQuantity: i.targetQuantity ?? '',
    targetUnit: i.targetUnit ?? '',
    remark: i.remark,
  }));
  if (form.items.length === 0) {
    addItem();
  }
  drawerOpen.value = true;
}

function closeDrawer() {
  drawerOpen.value = false;
}

/** 明细校验在提交前做：逐行给出「第几行缺什么」，比一条笼统的「参数不合法」有用得多。 */
function buildPayload(): InventoryConversionAdd | null {
  const items = form.items.filter(
    (i) => i.sourceSkuId !== undefined && i.sourceSkuId !== null && i.targetSkuId !== undefined && i.targetSkuId !== null
  );
  if (items.length === 0) {
    message.warning('请至少添加一条转换明细（源 SKU 与目标 SKU 都要选）');
    return null;
  }
  for (let i = 0; i < items.length; i++) {
    const row = items[i];
    if (String(row.sourceSkuId) === String(row.targetSkuId)) {
      message.warning(`第 ${i + 1} 行：源 SKU 与目标 SKU 不能相同`);
      return null;
    }
    for (const [label, value] of [
      ['源数量', row.sourceQuantity],
      ['目标数量', row.targetQuantity],
    ] as const) {
      const q = (value ?? '').trim();
      // 数量恒为正：方向由「转出 / 转入」决定，不接受 0 或负数
      if (!/^\d+(\.\d{1,4})?$/.test(q) || Number(q) <= 0) {
        message.warning(`第 ${i + 1} 行：${label}必须为大于 0 的数字，最多 4 位小数`);
        return null;
      }
    }
    if (!(row.sourceUnit ?? '').trim() || !(row.targetUnit ?? '').trim()) {
      message.warning(`第 ${i + 1} 行：源单位与目标单位都要填（折算关系含单位）`);
      return null;
    }
  }
  return {
    warehouseId: form.warehouseId as string | number,
    convertType: form.convertType as string,
    reason: form.reason,
    remark: form.remark,
    // 数量以字符串提交：后端拒绝 JSON 数字（ScmStrictDecimalStringDeserializer）
    items: items.map((i) => ({
      sourceSkuId: i.sourceSkuId as string | number,
      sourceQuantity: i.sourceQuantity.trim(),
      sourceUnit: i.sourceUnit.trim(),
      targetSkuId: i.targetSkuId as string | number,
      targetQuantity: i.targetQuantity.trim(),
      targetUnit: i.targetUnit.trim(),
      remark: i.remark,
    })),
  };
}

async function onSubmit() {
  await formRef.value.validate();
  const payload = buildPayload();
  if (!payload) return;
  saving.value = true;
  try {
    if (form.id) {
      await inventoryConversionApi.update(form.id, payload);
      message.success('已保存');
    } else {
      await inventoryConversionApi.create(payload);
      message.success('已创建，等待审批');
    }
    drawerOpen.value = false;
    queryData();
  } catch (e) {
    message.error(inventoryError(e));
  } finally {
    saving.value = false;
  }
}

// ------------------------------------------------------------------ 详情与动作

const detailOpen = ref(false);
// Partial：初始为空对象（还没选中任何单据），打开详情时整体替换为后端返回的 VO
const detail = ref<Partial<InventoryConversion>>({});

async function openDetail(record: InventoryConversion) {
  try {
    const r = await inventoryConversionApi.detail(record.id);
    detail.value = r.data;
    detailOpen.value = true;
  } catch (e) {
    message.error(inventoryError(e));
  }
}

// ------------------------------------------------------------------ 审批 / 驳回

const auditOpen = ref(false);
const auditSaving = ref(false);
const auditMode = ref<'approve' | 'reject'>('approve');
const auditRecord = ref<Partial<InventoryConversion>>({});
const auditOpinion = ref('');

/**
 * 打开审批弹窗时把 `version` 一起记下来 —— 提交时原样回传。
 * 不重新拉取单据：审批人应当批准自己**看到的那一版**；重新拉取会让乐观锁失效。
 */
function openAudit(record: InventoryConversion, mode: 'approve' | 'reject') {
  auditRecord.value = { ...record };
  auditMode.value = mode;
  auditOpinion.value = '';
  auditOpen.value = true;
}

async function onAuditSubmit() {
  const record = auditRecord.value;
  if (record.id === undefined) return;
  if (auditMode.value === 'reject' && !auditOpinion.value.trim()) {
    message.warning('驳回时必须填写审核意见，说明驳回原因');
    return;
  }
  auditSaving.value = true;
  try {
    const payload = {
      // version 必须原样回传：后端用它确认「审批的就是看到的那一版」
      version: record.version ?? 0,
      auditOpinion: auditOpinion.value.trim() || undefined,
    };
    if (auditMode.value === 'approve') {
      await inventoryConversionApi.approve(record.id, payload);
      message.success('审批通过，库存已按本单调整');
    } else {
      await inventoryConversionApi.reject(record.id, payload);
      message.success('已驳回');
    }
    auditOpen.value = false;
    queryData();
  } catch (e) {
    // 40921（单据在审批期间被改过）会走到这里；库存未受影响，刷新后重新审批即可
    message.error(inventoryError(e));
    if (auditMode.value === 'approve') {
      queryData();
    }
  } finally {
    auditSaving.value = false;
  }
}

function onDelete(record: InventoryConversion) {
  Modal.confirm({
    title: '删除转换单',
    content: `确认删除「${record.conversionNo}」？只有待审核的单据可删除，已审核的单据必须留痕。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '返回',
    onOk: async () => {
      try {
        await inventoryConversionApi.delete(record.id);
        message.success('已删除');
        queryData();
      } catch (e) {
        message.error(inventoryError(e));
      }
    },
  });
}

onMounted(async () => {
  await applySingleWarehouseDefault();
  await queryData();
});
</script>

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
