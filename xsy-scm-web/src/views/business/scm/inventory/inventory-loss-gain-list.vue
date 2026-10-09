<!--
  库存报损报溢单。

  状态机：PENDING → COMPLETED | REJECTED，两个终态都不可回退（流水 append-only）。
  与出库单 / 盘点单不同，这里没有草稿态：创建即提交待审核。
  原因是「报损」= 把货从账上抹掉，录单与审批应由不同的人完成；草稿态会让这道制衡
  变成「同一个人先后点两次」。

  审批会真实调整库存并写不可逆流水，因此：
  1. 审批与驳回各占一个按钮 + 二次确认，不与「编辑」混在一起；
  2. 审批请求必须带上打开单据时读到的 `version` —— 若单据在审批期间被改过，
     后端以 40921 拒绝并要求刷新，避免「批准了一个自己没看过的数量」。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="单据号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.lossGainNo" placeholder="单据号" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="200px"/>
      </a-form-item>
      <a-form-item label="类型" class="smart-query-form-item">
        <a-select
            v-model:value="queryForm.adjustType"
            :options="typeOptions"
            placeholder="全部"
            allow-clear
            style="width: 120px"
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
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:loss-gain:query'">查询</a-button>
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
        <a-button type="primary" @click="openCreate" v-privilege="'scm:inventory:loss-gain:add'">
          新建报损报溢单
        </a-button>
        <a-typography-text type="secondary" style="margin-left: 12px">
          创建后进入待审核；审批通过才调整库存并生成不可删除的流水。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_LOSS_GAIN"
            :refresh="queryData"
        />
      </div>
    </a-row>

    <a-table
        :id="SCM_INVENTORY_TABLE_ID.LOSS_GAIN"
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText: '暂无报损报溢单' }"
        :scroll="{ x: scrollX }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'adjustType'">
          <ScmStatusTag :tone="typeTone(record.adjustType)" :label="record.adjustTypeDesc || record.adjustType"/>
        </template>
        <template v-else-if="column.dataIndex === 'warehouseName'">{{ record.warehouseName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'warehouseCode'">
          <span class="scm-mono">{{ record.warehouseCode || '—' }}</span>
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
                v-privilege="'scm:inventory:loss-gain:approve'"
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
      :title="form.id ? `编辑报损报溢单 ${form.lossGainNo}` : '新建报损报溢单'"
      :width="scmDrawerWidth('l')"
      @close="closeDrawer"
  >
    <a-alert
        type="info"
        show-icon
        style="margin-bottom: 12px"
        message="报损减库存、报溢加库存；原因必填。"
    />
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical">
      <a-form-item label="调整类型" name="adjustType">
        <a-radio-group v-model:value="form.adjustType" button-style="solid">
          <a-radio-button value="LOSS">报损（减少库存）</a-radio-button>
          <a-radio-button value="OVERFLOW">报溢（增加库存）</a-radio-button>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="仓库" name="warehouseId">
        <WarehouseSelect v-model:value="form.warehouseId" :options="warehouses" width="260px"/>
      </a-form-item>
      <a-form-item label="原因" name="reason">
        <a-input v-model:value="form.reason" :maxlength="200" show-count
                 placeholder="如：到货变质 / 运输破损 / 盘点外多出"/>
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="form.remark" :rows="2" :maxlength="500" show-count/>
      </a-form-item>
      <a-form-item label="明细" required>
        <a-table
            size="small"
            :data-source="form.items"
            :columns="itemColumns"
            row-key="_key"
            bordered
            :pagination="false"
        >
          <template #bodyCell="{ record, column, index }">
            <template v-if="column.dataIndex === 'skuId'">
              <SkuSelect
                  :value="record.skuId"
                  :disabled-statuses="[]"
                  width="260px"
                  @update:value="(v) => (record.skuId = Array.isArray(v) ? v[0] : v)"
              />
            </template>
            <template v-else-if="column.dataIndex === 'quantity'">
              <a-input-number
                  v-model:value="record.quantity"
                  :min="0.0001"
                  :precision="4"
                  :step="1"
                  placeholder="0.0000"
                  style="width: 140px"
              />
            </template>
            <template v-else-if="column.dataIndex === 'remark'">
              <a-input v-model:value="record.remark" :maxlength="500"/>
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <a-button type="link" size="small" danger @click="removeItem(index)">删除</a-button>
            </template>
          </template>
        </a-table>
        <a-button type="dashed" block style="margin-top: 8px" @click="addItem">+ 添加明细</a-button>
        <a-typography-text type="secondary" style="display: block; margin-top: 8px">
          同一个商品规格只能出现一次 —— 重复行会让同一份数量被调整两次。
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

  <InventoryLossGainDetailDrawer v-model:open="detailOpen" :detail="detail" />

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
        ? '审批通过会立即按本单明细调整库存并生成不可删除的流水，此操作不可撤销。'
        : '驳回不产生任何库存影响；单据将变为终态，不可再修改或删除。'"
    />
    <a-descriptions :column="1" bordered size="small" style="margin-bottom: 12px">
      <a-descriptions-item label="单据号">{{ auditRecord.lossGainNo }}</a-descriptions-item>
      <a-descriptions-item label="类型">
        {{ auditRecord.adjustTypeDesc || auditRecord.adjustType }}
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
      提交时会带上打开本单时读到的版本号；若期间单据已被修改，系统会要求你刷新后重新审批。
    </a-typography-text>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {useRoute} from 'vue-router';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import InventoryLossGainDetailDrawer from './components/inventory-loss-gain-detail-drawer.vue';
import {inventoryLossGainApi} from '/@/api/business/scm/inventory-loss-gain-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {deepLinkFilters, deepLinkId} from '/@/lib/query-deep-link';
import {
  SCM_INVENTORY_LOSS_GAIN_STATUS_ENUM,
  SCM_INVENTORY_LOSS_GAIN_TYPE_ENUM,
  SCM_INVENTORY_TABLE_ID,
} from '/@/constants/business/scm/inventory-const';
import type {
  Id,
  InventoryLossGain,
  InventoryLossGainAdd,
  InventoryLossGainQuery,
} from './inventory-types';
import type {Warehouse} from '../purchase/purchase-types';
import {fixed4} from '../common/scm-fixed';
import {singleWarehouseDefault} from './inventory-model';
import {hasPermission} from '../common/scm-permission';
import {inventoryError} from './inventory-errors';
import {datetime} from '../common/scm-display';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const queryForm = reactive<InventoryLossGainQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<InventoryLossGain[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const warehouses = ref<Warehouse[]>([]);
let requestId = 0;

const typeOptions = Object.values(SCM_INVENTORY_LOSS_GAIN_TYPE_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

const statusOptions = Object.values(SCM_INVENTORY_LOSS_GAIN_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

// 列表按「哪张单 / 什么类型 / 哪个仓 / 什么状态 / 为什么」排列。创建时间是技术字段，
// 报损报溢的业务时刻是审核时间，不上列。
type InventoryLossGainColumn = TableColumnsType<InventoryLossGain>[number] & {showFlag?: boolean};
const columns = ref<InventoryLossGainColumn[]>([
  {title: '单据号', dataIndex: 'lossGainNo', width: 200},
  {title: '类型', dataIndex: 'adjustType', align: 'center', width: 100},
  {title: '仓库', dataIndex: 'warehouseName', width: 140},
  {title: '仓库编码', dataIndex: 'warehouseCode', width: 120, showFlag: false},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '原因', dataIndex: 'reason', width: 240, ellipsis: true},
  {title: '审核', dataIndex: 'auditedAt', width: 180},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 150},
]);
const scrollX = computed(() => columns.value.reduce((width, column) => width + Number(column.width ?? 0), 0));

const itemColumns: TableColumnsType = [
  {title: '商品规格', dataIndex: 'skuId', width: 290},
  {title: '数量', dataIndex: 'quantity', width: 160, align: 'right'},
  {title: '备注', dataIndex: 'remark'},
  {title: '操作', dataIndex: 'action', width: 80, align: 'center'},
];

/** 报损 = 库存减少（红），报溢 = 库存增加（绿）—— 方向在列表里必须一眼可见。 */
const TYPE_TONE: Record<string, ScmStatusTone> = {
  LOSS: 'error',
  OVERFLOW: 'success',
};
const typeTone = (adjustType?: string | null): ScmStatusTone => TYPE_TONE[adjustType ?? ''] ?? 'neutral';

/** 待审核 = 待处理（橙），已完成 = 通过（绿），已驳回 = 拒绝（红）。 */
const STATUS_TONE: Record<string, ScmStatusTone> = {
  PENDING: 'warning',
  COMPLETED: 'success',
  REJECTED: 'error',
};
const statusTone = (status?: string | null): ScmStatusTone => STATUS_TONE[status ?? ''] ?? 'neutral';

/** 「更多」里的菜单项挂不上 `v-privilege` 指令，改用同一口径的 hasPermission 裁剪。 */
const canUpdate = computed(() => hasPermission('scm:inventory:loss-gain:update'));
const canReject = computed(() => hasPermission('scm:inventory:loss-gain:reject'));
const canDelete = computed(() => hasPermission('scm:inventory:loss-gain:delete'));

/** 只有待审核可写；动作集合与原行内按钮一一对应，只按频率重新分组。 */
function rowActions(row: InventoryLossGain): ScmActionItem[] {
  const pending = row.status === 'PENDING';
  return [
    {key: 'reject', label: '驳回', danger: true, hidden: !(pending && canReject.value)},
    {key: 'edit', label: '编辑', hidden: !(pending && canUpdate.value)},
    {key: 'delete', label: '删除', danger: true, hidden: !(pending && canDelete.value)},
  ];
}

function onRowAction(key: string, row: InventoryLossGain) {
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
    const r = await inventoryLossGainApi.query({...queryForm});
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
  queryForm.lossGainNo = undefined;
  queryForm.warehouseId = undefined;
  queryForm.adjustType = undefined;
  queryForm.status = undefined;
  onSearch();
}

// ------------------------------------------------------------------ 表单

interface EditableItem {
  _key: number;
  skuId?: string | number;
  /**
   * 数量在表单里是 `number`（InputNumber 只接受数字），提交时才转成后端要求的
   * 4 位定点字符串。`null` = 还没填。
   */
  quantity?: number | null;
  remark?: string;
}

const drawerOpen = ref(false);
const saving = ref(false);
const formRef = ref();
let keySeq = 0;

const form = reactive<{
  id?: string | number;
  lossGainNo?: string;
  adjustType?: string;
  warehouseId?: string | number;
  reason?: string;
  remark?: string;
  items: EditableItem[];
}>({items: []});

const formRules = {
  adjustType: [{required: true, message: '请选择调整类型'}],
  warehouseId: [{required: true, message: '请选择仓库'}],
  reason: [{required: true, message: '请填写原因（审批人据此判断）'}],
};

function addItem() {
  form.items.push({_key: ++keySeq, quantity: null});
}

function removeItem(index: number) {
  form.items.splice(index, 1);
}

function openCreate() {
  form.id = undefined;
  form.lossGainNo = undefined;
  form.adjustType = 'LOSS';
  form.warehouseId = queryForm.warehouseId ?? singleWarehouseDefault(warehouses.value);
  form.reason = undefined;
  form.remark = undefined;
  form.items = [];
  addItem();
  drawerOpen.value = true;
}

async function openEdit(record: InventoryLossGain) {
  const r = await inventoryLossGainApi.detail(record.id);
  const d = r.data;
  form.id = d.id;
  form.lossGainNo = d.lossGainNo;
  form.adjustType = d.adjustType;
  form.warehouseId = d.warehouseId;
  form.reason = d.reason;
  form.remark = d.remark;
  form.items = (d.items ?? []).map((i) => ({
    _key: ++keySeq,
    skuId: i.skuId,
    // 后端以 4 位定点字符串返回数量，InputNumber 要 number；空值保持 null
    quantity: i.quantity == null ? null : Number(i.quantity),
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
function buildPayload(): InventoryLossGainAdd | null {
  const items = form.items.filter((i) => i.skuId !== undefined && i.skuId !== null);
  if (items.length === 0) {
    message.warning('请至少添加一条明细');
    return null;
  }
  const seen = new Set<string>();
  for (let i = 0; i < items.length; i++) {
    const skuKey = String(items[i].skuId);
    if (seen.has(skuKey)) {
      message.warning(`第 ${i + 1} 行：同一商品规格只能出现一次，请合并重复行`);
      return null;
    }
    seen.add(skuKey);
    // 数量恒为正：方向由单据类型表达，不接受 0 或负数
    // （大于 0 与最多 4 位小数已由 InputNumber 的 :min="0.0001" + :precision="4" 保证）
    if (fixed4(items[i].quantity) === undefined) {
      message.warning(`第 ${i + 1} 行：请填写数量`);
      return null;
    }
  }
  return {
    adjustType: form.adjustType as string,
    warehouseId: form.warehouseId as string | number,
    reason: (form.reason ?? '').trim(),
    remark: form.remark,
    // 数量以定点字符串提交：后端拒绝 JSON 数字（ScmStrictDecimalStringDeserializer）
    items: items.map((i) => ({
      skuId: i.skuId as string | number,
      quantity: fixed4(i.quantity) as string,
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
      await inventoryLossGainApi.update(form.id, payload);
      message.success('已保存');
    } else {
      await inventoryLossGainApi.create(payload);
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
const detail = ref<Partial<InventoryLossGain>>({});

async function openDetailById(id: Id) {
  try {
    const r = await inventoryLossGainApi.detail(id);
    detail.value = r.data;
    detailOpen.value = true;
  } catch (e) {
    message.error(inventoryError(e));
  }
}

function openDetail(record: InventoryLossGain) {
  return openDetailById(record.id);
}

// ------------------------------------------------------------------ 审批 / 驳回

const auditOpen = ref(false);
const auditSaving = ref(false);
const auditMode = ref<'approve' | 'reject'>('approve');
const auditRecord = ref<Partial<InventoryLossGain>>({});
const auditOpinion = ref('');

/**
 * 打开审批弹窗时把 `version` 一起记下来 —— 提交时原样回传。
 *
 * 不重新拉取单据：审批人应当批准自己看到的那一版；
 * 重新拉取会让乐观锁失效（拿到最新版就等于「总是批准最新的」，那道防线就没了）。
 */
function openAudit(record: InventoryLossGain, mode: 'approve' | 'reject') {
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
      await inventoryLossGainApi.approve(record.id, payload);
      message.success('审批通过，库存已按本单调整');
    } else {
      await inventoryLossGainApi.reject(record.id, payload);
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

function onDelete(record: InventoryLossGain) {
  Modal.confirm({
    title: '删除报损报溢单',
    content: `确认删除「${record.lossGainNo}」？只有待审核的单据可删除，已审核的单据必须留痕。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '返回',
    onOk: async () => {
      try {
        await inventoryLossGainApi.delete(record.id);
        message.success('已删除');
        queryData();
      } catch (e) {
        message.error(inventoryError(e));
      }
    },
  });
}

// 待办卡片带 `?status=PENDING`，驳回消息带 `?id=<单据>`：两者都必须真正落到页面条件上，
// 否则「待办 5 条」点进来看到的不是那 5 条。取值过状态字典白名单，非法值按未提供处理。
const LOSS_GAIN_DEEP_LINK = {
  lossGainNo: null,
  status: Object.values(SCM_INVENTORY_LOSS_GAIN_STATUS_ENUM).map((i) => i.value),
};

const route = useRoute();
const lossGainRouteName = route.name;

/** 进入页面（首次挂载与 keep-alive 复用同一条路径）：URL 条件 → 页面默认 → 查询 → 可选详情。 */
async function enterPage() {
  const filters = deepLinkFilters(route.query, LOSS_GAIN_DEEP_LINK);
  queryForm.lossGainNo = filters.lossGainNo;
  queryForm.adjustType = undefined;
  queryForm.status = filters.status;
  queryForm.warehouseId = undefined;
  queryForm.pageNum = 1;
  await applySingleWarehouseDefault();
  await queryData();
  // 详情深链优先直进目标单据：详情接口自带 `scm:inventory:loss-gain:detail` 校验，
  // 失去权限时这里得到 403 提示，跳转本身不绕过任何权限。
  const id = deepLinkId(route.query);
  if (id) {
    await openDetailById(id);
  }
}

watch(
    [() => route.name, () => route.query],
    ([name]) => {
      // 组件被缓存时，跳往其他页面不应触发本页查询；回到本页才重新套用来源链接。
      if (name !== lossGainRouteName) return;
      void enterPage();
    },
    {immediate: true}
);
</script>
