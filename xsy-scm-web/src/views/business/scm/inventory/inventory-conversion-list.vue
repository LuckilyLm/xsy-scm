<!--
  库存规格转换。

  状态机：PENDING → COMPLETED | REJECTED，两个终态都不可回退（流水 append-only）。
  创建即提交待审核：转换会把两个 SKU 的余额同时改掉，而折算率是人工声明的，
  没有审批等于录单人可以单方面决定「一箱等于多少 kg」。

  跨 SKU、同仓库：源规格 → 目标规格（整件 → 散装）。跨仓搬运是「调拨」，不是转换。

  页面上要讲清楚的一件事：两个单位都由单据声明，后端会与各自 SKU 的余额记账单位比对，
  不一致直接失败（41059 / 41060）—— 库存不做自动换算。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="转换单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.conversionNo" placeholder="转换单号" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="190px"/>
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
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button type="primary" @click="openCreate" v-privilege="'scm:inventory:conversion:add'">
          新建转换单
        </a-button>
        <a-typography-text type="secondary" style="margin-left: 12px">
          审批通过才调库存，流水不可删除
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
        :scroll="{ x: 1140 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'convertType'">
          <ScmStatusTag tone="processing" :label="record.convertTypeDesc || record.convertType"/>
        </template>
        <template v-else-if="column.dataIndex === 'warehouse'">
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main">{{ record.warehouseName || '—' }}</span>
            <span v-if="record.warehouseCode" class="scm-cell-stack__sub">{{ record.warehouseCode }}</span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="statusTone(record.status)" :label="record.statusDesc || record.status"/>
        </template>
        <template v-else-if="column.dataIndex === 'auditedAt'">
          <!-- 审核的「谁」和「何时」是同一件事的两面，合成一格 -->
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main">{{ datetime(record.auditedAt) }}</span>
            <span v-if="record.auditor" class="scm-cell-stack__sub">{{ record.auditor }}</span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 行内常驻「详情」与待审核态的「审批」；驳回 / 编辑 / 删除收进「更多」 -->
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button
                v-if="record.status === 'PENDING'"
                type="link"
                size="small"
                @click="openAudit(record, 'approve')"
                v-privilege="'scm:inventory:conversion:approve'"
            >
              审批
            </a-button>
            <ScmActionMore :actions="rowActions(record)" @select="onRowAction($event, record)"/>
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
      :width="scmDrawerWidth('xl')"
      @close="closeDrawer"
  >
    <a-alert
        type="info"
        show-icon
        style="margin-bottom: 12px"
        message="折算率由本单声明，两个单位须与商品规格的库存记账单位一致。"
    />
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical">
      <a-form-item label="仓库" name="warehouseId">
        <WarehouseSelect v-model:value="form.warehouseId" :options="warehouses" width="260px"/>
      </a-form-item>
      <a-form-item label="转换类型" name="convertType">
        <a-radio-group v-model:value="form.convertType" button-style="solid">
          <a-radio-button value="SPLIT">整件拆零（1 件 → N 散装）</a-radio-button>
          <a-radio-button value="COMBINE">组合拆分（N 散装 → 1 件）</a-radio-button>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="原因" name="reason">
        <a-input v-model:value="form.reason" :maxlength="200" show-count placeholder="如：客户要散装 / 整件拆零上架"/>
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="form.remark" :rows="2" :maxlength="500" show-count/>
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
              <a-input-number
                  v-model:value="record.sourceQuantity"
                  :min="0.0001"
                  :precision="4"
                  :step="1"
                  placeholder="0.0000"
                  style="width: 120px"
              />
            </template>
            <template v-else-if="column.dataIndex === 'sourceUnit'">
              <a-input v-model:value="record.sourceUnit" placeholder="如 箱" style="width: 80px"/>
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
              <a-input-number
                  v-model:value="record.targetQuantity"
                  :min="0.0001"
                  :precision="4"
                  :step="1"
                  placeholder="0.0000"
                  style="width: 120px"
              />
            </template>
            <template v-else-if="column.dataIndex === 'targetUnit'">
              <a-input v-model:value="record.targetUnit" placeholder="如 kg" style="width: 80px"/>
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <a-button type="link" size="small" danger @click="removeItem(index)">删除</a-button>
            </template>
          </template>
        </a-table>
        <a-button type="dashed" block style="margin-top: 8px" @click="addItem">+ 添加明细</a-button>
        <a-typography-text type="secondary" style="display: block; margin-top: 8px">
          同一商品规格可以在多行里出现（既是某行的源、又是另一行的目标，用于链式转换），
          但同一行的源与目标不能是同一个商品规格。
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

  <InventoryConversionDetailDrawer v-model:open="detailOpen" :detail="detail" />

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
        ? '审批通过会立即按本单明细改动两个商品规格的库存（源出 / 目标入）并生成不可删除的流水，此操作不可撤销。'
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
import {computed, onMounted, reactive, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import InventoryConversionDetailDrawer from './components/inventory-conversion-detail-drawer.vue';
import {inventoryConversionApi} from '/@/api/business/scm/inventory-conversion-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
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
import type {Warehouse} from '../purchase/purchase-types';
import {fixed4} from '../common/scm-fixed';
import {singleWarehouseDefault} from './inventory-model';
import {hasPermission} from '../common/scm-permission';
import {inventoryError} from './inventory-errors';
import {datetime} from '../common/scm-display';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const queryForm = reactive<InventoryConversionQuery>({pageNum: 1, pageSize: 20});
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

// 列表按「哪张单 / 哪个仓 / 什么类型 / 什么状态 / 为什么」排列。创建时间是技术字段，
// 转换单的业务时刻是审核时间，不上列；审核人与审核时间合成一格。
const columns = ref<TableColumnsType<InventoryConversion>>([
  {title: '转换单号', dataIndex: 'conversionNo', width: 200},
  {title: '仓库', dataIndex: 'warehouse', width: 150},
  {title: '类型', dataIndex: 'convertType', align: 'center', width: 110},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '原因', dataIndex: 'reason', width: 220, ellipsis: true},
  {title: '审核', dataIndex: 'auditedAt', width: 180},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 160},
]);

const itemColumns: TableColumnsType = [
  {title: '源商品规格（转出）', dataIndex: 'sourceSkuId', width: 220},
  {title: '源数量', dataIndex: 'sourceQuantity', width: 120},
  {title: '源单位', dataIndex: 'sourceUnit', width: 90},
  {title: '目标商品规格（转入）', dataIndex: 'targetSkuId', width: 220},
  {title: '目标数量', dataIndex: 'targetQuantity', width: 120},
  {title: '目标单位', dataIndex: 'targetUnit', width: 90},
  {title: '操作', dataIndex: 'action', width: 70, align: 'center'},
];

/** 待审核 = 待处理（橙），已完成 = 通过（绿），已驳回 = 拒绝（红）。 */
const STATUS_TONE: Record<string, ScmStatusTone> = {
  PENDING: 'warning',
  COMPLETED: 'success',
  REJECTED: 'error',
};
const statusTone = (status?: string | null): ScmStatusTone => STATUS_TONE[status ?? ''] ?? 'neutral';

/** 「更多」里的菜单项挂不上 `v-privilege` 指令，改用同一口径的 hasPermission 裁剪。 */
const canUpdate = computed(() => hasPermission('scm:inventory:conversion:update'));
const canReject = computed(() => hasPermission('scm:inventory:conversion:reject'));
const canDelete = computed(() => hasPermission('scm:inventory:conversion:delete'));

/** 只有待审核可写；动作集合与原行内按钮一一对应，只按频率重新分组。 */
function rowActions(row: InventoryConversion): ScmActionItem[] {
  const pending = row.status === 'PENDING';
  return [
    {key: 'reject', label: '驳回', danger: true, hidden: !(pending && canReject.value)},
    {key: 'edit', label: '编辑', hidden: !(pending && canUpdate.value)},
    {key: 'delete', label: '删除', danger: true, hidden: !(pending && canDelete.value)},
  ];
}

function onRowAction(key: string, row: InventoryConversion) {
  if (key === 'reject') {
    openAudit(row, 'reject');
  } else if (key === 'edit') {
    void openEdit(row);
  } else if (key === 'delete') {
    onDelete(row);
  }
}

// ------------------------------------------------------------------ 查询

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await inventoryConversionApi.query({...queryForm});
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
  /**
   * 数量在表单里是 `number`（InputNumber 只接受数字），提交时才转成后端要求的
   * 4 位定点字符串。`null` = 还没填。
   */
  sourceQuantity?: number | null;
  /** 单位是单据声明（折算关系的一部分），仍是自由文本，不做单位换算。 */
  sourceUnit: string;
  targetSkuId?: string | number;
  targetQuantity?: number | null;
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
}>({items: []});

const formRules = {
  warehouseId: [{required: true, message: '请选择仓库'}],
  convertType: [{required: true, message: '请选择转换类型'}],
};

function addItem() {
  form.items.push({
    _key: ++keySeq,
    sourceQuantity: null,
    sourceUnit: '',
    targetQuantity: null,
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
    // 后端以 4 位定点字符串返回数量，InputNumber 要 number；空值保持 null
    sourceQuantity: i.sourceQuantity == null ? null : Number(i.sourceQuantity),
    sourceUnit: i.sourceUnit ?? '',
    targetSkuId: i.targetSkuId,
    targetQuantity: i.targetQuantity == null ? null : Number(i.targetQuantity),
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
    message.warning('请至少添加一条转换明细（源商品规格与目标商品规格都要选）');
    return null;
  }
  for (let i = 0; i < items.length; i++) {
    const row = items[i];
    if (String(row.sourceSkuId) === String(row.targetSkuId)) {
      message.warning(`第 ${i + 1} 行：源商品规格与目标商品规格不能相同`);
      return null;
    }
    for (const [label, value] of [
      ['源数量', row.sourceQuantity],
      ['目标数量', row.targetQuantity],
    ] as const) {
      // 数量恒为正：方向由「转出 / 转入」决定，不接受 0 或负数
      // （大于 0 与最多 4 位小数已由 InputNumber 的 :min="0.0001" + :precision="4" 保证）
      if (fixed4(value) === undefined) {
        message.warning(`第 ${i + 1} 行：请填写${label}`);
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
    // 数量以定点字符串提交：后端拒绝 JSON 数字（ScmStrictDecimalStringDeserializer）
    items: items.map((i) => ({
      sourceSkuId: i.sourceSkuId as string | number,
      sourceQuantity: fixed4(i.sourceQuantity) as string,
      sourceUnit: i.sourceUnit.trim(),
      targetSkuId: i.targetSkuId as string | number,
      targetQuantity: fixed4(i.targetQuantity) as string,
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
 * 不重新拉取单据：审批人应当批准自己看到的那一版；重新拉取会让乐观锁失效。
 */
function openAudit(record: InventoryConversion, mode: 'approve' | 'reject') {
  auditRecord.value = {...record};
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
