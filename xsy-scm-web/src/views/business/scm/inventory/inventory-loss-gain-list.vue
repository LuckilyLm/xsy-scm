<!--
  库存报损报溢单（报损报溢波次新增）。

  状态机：PENDING → COMPLETED | REJECTED，两个终态都不可回退（流水 append-only）。
  与出库单 / 盘点单不同，这里**没有草稿态**：创建即提交待审核。
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
        :scroll="{ x: 1450 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'adjustType'">
          <a-tag :color="typeColor(record.adjustType)">{{ record.adjustTypeDesc || record.adjustType }}</a-tag>
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
                v-privilege="'scm:inventory:loss-gain:update'"
            >
              编辑
            </a-button>
            <a-button
                v-if="record.status === 'PENDING'"
                type="link"
                size="small"
                @click="openAudit(record, 'approve')"
                v-privilege="'scm:inventory:loss-gain:approve'"
            >
              审批
            </a-button>
            <a-button
                v-if="record.status === 'PENDING'"
                type="link"
                size="small"
                danger
                @click="openAudit(record, 'reject')"
                v-privilege="'scm:inventory:loss-gain:reject'"
            >
              驳回
            </a-button>
            <a-button
                v-if="record.status === 'PENDING'"
                type="link"
                size="small"
                danger
                @click="onDelete(record)"
                v-privilege="'scm:inventory:loss-gain:delete'"
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
      :title="form.id ? `编辑报损报溢单 ${form.lossGainNo}` : '新建报损报溢单'"
      width="900"
      @close="closeDrawer"
  >
    <a-alert
        type="info"
        show-icon
        style="margin-bottom: 12px"
        message="报损减少库存、报溢增加库存。原因必填 —— 它是审批人唯一的判断依据。"
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
              <a-input v-model:value="record.quantity" placeholder="0.0000" style="width: 130px"/>
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
          同一个 SKU 只能出现一次 —— 重复行会让同一份数量被调整两次。
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
  <a-drawer :open="detailOpen" title="报损报溢单详情" width="860" @close="detailOpen = false">
    <a-descriptions :column="2" bordered size="small">
      <a-descriptions-item label="单据号">{{ detail.lossGainNo }}</a-descriptions-item>
      <a-descriptions-item label="类型">
        <a-tag :color="typeColor(detail.adjustType)">{{ detail.adjustTypeDesc || detail.adjustType }}</a-tag>
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
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'quantity'">
          <span class="num">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unitSnapshot'">
          {{ record.unitSnapshot || '（待审核未审批）' }}
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
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
import {reactive, ref, watch} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {useRoute} from 'vue-router';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
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
import {quantityText, singleWarehouseDefault} from './inventory-model';
import {inventoryError} from './inventory-errors';
import {datetime} from '../common/scm-display';

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

const columns = ref<TableColumnsType<InventoryLossGain>>([
  {title: '单据号', dataIndex: 'lossGainNo', width: 200},
  {title: '类型', dataIndex: 'adjustType', align: 'center', width: 90},
  {title: '仓库', dataIndex: 'warehouseName', width: 150},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '原因', dataIndex: 'reason', width: 220, ellipsis: true},
  {title: '审核人', dataIndex: 'auditor', width: 130},
  {title: '审核时间', dataIndex: 'auditedAt', width: 170},
  {title: '创建时间', dataIndex: 'createdAt', width: 170},
  {title: '操作', dataIndex: 'action', width: 280, fixed: 'right'},
]);

const itemColumns: TableColumnsType = [
  {title: 'SKU', dataIndex: 'skuId', width: 290},
  {title: '数量', dataIndex: 'quantity', width: 150},
  {title: '备注', dataIndex: 'remark'},
  {title: '操作', dataIndex: 'action', width: 80},
];

const detailItemColumns: TableColumnsType = [
  {title: 'SKU 编码', dataIndex: 'skuCode', width: 160},
  {title: 'SKU 名称', dataIndex: 'skuName', width: 150},
  {title: '商品名称', dataIndex: 'productName', width: 150},
  {title: '数量', dataIndex: 'quantity', align: 'right', width: 110},
  {title: '单位', dataIndex: 'unitSnapshot', align: 'center', width: 130},
];

/** 报损是减少（红），报溢是增加（绿）—— 方向在列表里必须一眼可见。 */
function typeColor(adjustType?: string) {
  if (adjustType === 'LOSS') return 'red';
  if (adjustType === 'OVERFLOW') return 'green';
  return 'default';
}

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
  quantity: string;
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
  form.items.push({_key: ++keySeq, quantity: ''});
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
    // 数量后端以 4 位定点字符串返回，直接回填，不转 number（避免精度与类型问题）
    quantity: i.quantity ?? '',
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
      message.warning(`第 ${i + 1} 行：同一 SKU 只能出现一次，请合并重复行`);
      return null;
    }
    seen.add(skuKey);
    const q = (items[i].quantity ?? '').trim();
    // 数量恒为正：方向由单据类型表达，不接受 0 或负数
    if (!/^\d+(\.\d{1,4})?$/.test(q) || Number(q) <= 0) {
      message.warning(`第 ${i + 1} 行：数量必须为大于 0 的数字，最多 4 位小数`);
      return null;
    }
  }
  return {
    adjustType: form.adjustType as string,
    warehouseId: form.warehouseId as string | number,
    reason: (form.reason ?? '').trim(),
    remark: form.remark,
    // 数量以字符串提交：后端拒绝 JSON 数字（ScmStrictDecimalStringDeserializer）
    items: items.map((i) => ({
      skuId: i.skuId as string | number,
      quantity: i.quantity.trim(),
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
 * 不重新拉取单据：审批人应当批准自己**看到的那一版**；
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

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
