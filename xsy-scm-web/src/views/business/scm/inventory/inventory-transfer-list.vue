<!-- 库存调拨单：草稿 → 在途 → 已收货。在途货物不在任何仓库余额中，全仓总库存会暂时减少。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="调拨单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.transferNo" placeholder="调拨单号" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="源仓" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.fromWarehouseId" :options="warehouses" width="190px" />
      </a-form-item>
      <a-form-item label="目标仓" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.toWarehouseId" :options="warehouses" width="190px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select v-model:value="queryForm.status" :options="statusOptions" placeholder="全部" allow-clear style="width: 130px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:transfer:query'">查询</a-button>
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
        <a-button type="primary" @click="openCreate" v-privilege="'scm:inventory:transfer:add'"> 新建调拨单 </a-button>
        <a-button style="margin-left: 8px" @click="openInTransit" v-privilege="'scm:inventory:transfer:query'"> 在途库存 </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_TRANSFER" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      :id="SCM_INVENTORY_TABLE_ID.TRANSFER"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{emptyText: '暂无调拨单'}"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'direction'">
          <span class="transfer-direction">
            <span>{{ record.fromWarehouseName || '—' }}</span>
            <span class="transfer-direction__arrow">→</span>
            <span>{{ record.toWarehouseName || '—' }}</span>
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="statusTone(record.status)" :label="record.statusDesc || record.status" />
        </template>
        <template v-else-if="column.dataIndex === 'shippedAt'">{{ datetime(record.shippedAt) }}</template>
        <template v-else-if="column.dataIndex === 'shippedBy'">{{ record.shippedBy || '—' }}</template>
        <template v-else-if="column.dataIndex === 'receivedAt'">{{ datetime(record.receivedAt) }}</template>
        <template v-else-if="column.dataIndex === 'receivedBy'">{{ record.receivedBy || '—' }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 详情和当前可执行的推进动作常驻，其他操作收进“更多” -->
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button v-if="record.status === 'DRAFT'" type="link" size="small" @click="onShip(record)" v-privilege="'scm:inventory:transfer:ship'">
              发出
            </a-button>
            <a-button
              v-else-if="record.status === 'SHIPPED'"
              type="link"
              size="small"
              @click="onReceive(record)"
              v-privilege="'scm:inventory:transfer:receive'"
            >
              收货
            </a-button>
            <ScmActionMore :actions="rowActions(record)" @select="onRowAction($event, record)" />
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

  <!-- 新建 / 编辑草稿 -->
  <a-drawer :open="drawerOpen" :title="form.id ? `编辑调拨单 ${form.transferNo}` : '新建调拨单'" :width="scmDrawerWidth('l')" @close="closeDrawer">
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical" class="app-drawer-form">
      <a-form-item label="源仓库（转出）" name="fromWarehouseId">
        <WarehouseSelect v-model:value="form.fromWarehouseId" :options="warehouses" width="260px" />
      </a-form-item>
      <a-form-item label="目标仓库（转入）" name="toWarehouseId">
        <WarehouseSelect v-model:value="form.toWarehouseId" :options="warehouses" width="260px" />
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="form.remark" :rows="2" :maxlength="500" show-count />
      </a-form-item>
      <a-form-item label="调拨明细" required>
        <a-table size="small" :data-source="form.items" :columns="itemColumns" row-key="_key" bordered :pagination="false">
          <template #bodyCell="{record, column, index}">
            <template v-if="column.dataIndex === 'skuId'">
              <SkuSelect
                :value="record.skuId"
                :disabled-statuses="[]"
                width="260px"
                @update:value="(v) => (record.skuId = Array.isArray(v) ? v[0] : v)"
              />
            </template>
            <template v-else-if="column.dataIndex === 'quantity'">
              <a-input-number v-model:value="record.quantity" :min="0.0001" :precision="4" :step="1" placeholder="0.0000" style="width: 140px" />
            </template>
            <template v-else-if="column.dataIndex === 'remark'">
              <a-input v-model:value="record.remark" :maxlength="500" />
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <a-button type="link" size="small" danger @click="removeItem(index)">删除</a-button>
            </template>
          </template>
        </a-table>
        <a-button type="dashed" block style="margin-top: 8px" @click="addItem">+ 添加明细</a-button>
      </a-form-item>
    </a-form>
    <template #footer>
      <a-space>
        <a-button @click="closeDrawer">取消</a-button>
        <a-button type="primary" :loading="saving" @click="onSubmit">保存草稿</a-button>
      </a-space>
    </template>
  </a-drawer>

  <InventoryTransferDetailDrawer v-model:open="detailOpen" :detail="detail" />
  <!-- 在途库存报表（只读） -->
  <a-modal :open="inTransitOpen" title="在途库存" width="1000" :footer="null" @cancel="inTransitOpen = false">
    <p class="scm-note scm-note--block">在途量不在任何仓库的余额里，对账时需单独计入。</p>
    <a-table
      size="small"
      :data-source="inTransitRows"
      :columns="inTransitColumns"
      row-key="rowKey"
      bordered
      :loading="inTransitLoading"
      :pagination="false"
      :locale="{emptyText: '当前没有在途调拨'}"
      :scroll="{x: scmColumnsWidth(inTransitColumns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'direction'">
          <span class="transfer-direction">
            <span>{{ record.fromWarehouseName || '—' }}</span>
            <span class="transfer-direction__arrow">→</span>
            <span>{{ record.toWarehouseName || '—' }}</span>
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'sku'">{{ record.skuName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
  </a-modal>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, onMounted, reactive, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType, FormInstance} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import InventoryTransferDetailDrawer from './components/inventory-transfer-detail-drawer.vue';
import {inventoryTransferApi} from '/@/api/business/scm/inventory-transfer-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_INVENTORY_TABLE_ID, SCM_INVENTORY_TRANSFER_STATUS_ENUM} from '/@/constants/business/scm/inventory-const';
import type {InventoryInTransit, InventoryTransfer, InventoryTransferAdd, InventoryTransferQuery} from './inventory-types';
import type {Warehouse} from '../purchase/purchase-types';
import {fixed4} from '../common/scm-fixed';
import {quantityText, singleWarehouseDefault} from './inventory-model';
import {hasPermission} from '../common/scm-permission';
import {inventoryError} from './inventory-errors';
import {datetime} from '../common/scm-display';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const queryForm = reactive<InventoryTransferQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<InventoryTransfer[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const warehouses = ref<Warehouse[]>([]);
let requestId = 0;

const statusOptions = Object.values(SCM_INVENTORY_TRANSFER_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

// 方向合成一格（源仓 → 目标仓）；创建时间不上列。
const columns = ref<TableColumnsType<InventoryTransfer>>([
  {title: '调拨单号', dataIndex: 'transferNo', width: 190},
  {title: '调拨方向', dataIndex: 'direction', width: 260, align: 'center'},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '发出时间', dataIndex: 'shippedAt', width: 170},
  {title: '发出人', dataIndex: 'shippedBy', width: 110},
  {title: '收货时间', dataIndex: 'receivedAt', width: 170},
  {title: '收货人', dataIndex: 'receivedBy', width: 110},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 180},
]);

const itemColumns: TableColumnsType = [
  {title: '商品规格', dataIndex: 'skuId', width: 290},
  {title: '调拨数量', dataIndex: 'quantity', width: 160},
  {title: '备注', dataIndex: 'remark'},
  {title: '操作', dataIndex: 'action', width: 80, align: 'center'},
];

/** 在途用橙色，避免被误认为货物丢失。 */
const STATUS_TONE: Record<string, ScmStatusTone> = {
  DRAFT: 'processing',
  SHIPPED: 'warning',
  RECEIVED: 'success',
  CANCELLED: 'neutral',
};
const statusTone = (status?: string | null): ScmStatusTone => STATUS_TONE[status ?? ''] ?? 'neutral';

/** 更多菜单无法使用 `v-privilege`，改用 `hasPermission` 判断。 */
const canUpdate = computed(() => hasPermission('scm:inventory:transfer:update'));
const canDelete = computed(() => hasPermission('scm:inventory:transfer:delete'));

/** 只有草稿可写。 */
function rowActions(row: InventoryTransfer): ScmActionItem[] {
  const draft = row.status === 'DRAFT';
  return [
    {key: 'edit', label: '编辑', hidden: !(draft && canUpdate.value)},
    {key: 'cancel', label: '取消单据', hidden: !(draft && canUpdate.value)},
    {key: 'delete', label: '删除', danger: true, hidden: !(draft && canDelete.value)},
  ];
}

function onRowAction(key: string, row: InventoryTransfer) {
  if (key === 'edit') {
    void openEdit(row);
  } else if (key === 'cancel') {
    onCancel(row);
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
    // 加载失败由页头 Alert 承担，不再让全局 toast 重复说一遍
    const r = await inventoryTransferApi.query({...queryForm}, {suppressGlobalErrorMessage: true});
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
    const r = await warehouseApi.list({suppressGlobalErrorMessage: true});
    warehouses.value = r.data ?? [];
    const fallback = singleWarehouseDefault(warehouses.value);
    if (fallback !== undefined) {
      queryForm.fromWarehouseId = fallback;
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
  queryForm.transferNo = undefined;
  queryForm.fromWarehouseId = undefined;
  queryForm.toWarehouseId = undefined;
  queryForm.status = undefined;
  onSearch();
}

// ------------------------------------------------------------------ 表单

interface EditableItem {
  _key: number;
  skuId?: string | number;
  /** 表单里用 number，提交时转成 4 位定点字符串；null 表示未填。 */
  quantity?: number | null;
  remark?: string;
}

const drawerOpen = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
let keySeq = 0;

const form = reactive<{
  id?: string | number;
  transferNo?: string;
  fromWarehouseId?: string | number;
  toWarehouseId?: string | number;
  remark?: string;
  items: EditableItem[];
}>({items: []});

const formRules = {
  fromWarehouseId: [{required: true, message: '请选择源仓库'}],
  toWarehouseId: [{required: true, message: '请选择目标仓库'}],
};

function addItem() {
  form.items.push({_key: ++keySeq, quantity: null});
}

function removeItem(index: number) {
  form.items.splice(index, 1);
}

function openCreate() {
  form.id = undefined;
  form.transferNo = undefined;
  form.fromWarehouseId = queryForm.fromWarehouseId ?? singleWarehouseDefault(warehouses.value);
  form.toWarehouseId = undefined;
  form.remark = undefined;
  form.items = [];
  addItem();
  drawerOpen.value = true;
}

async function openEdit(record: InventoryTransfer) {
  const r = await inventoryTransferApi.detail(record.id);
  const d = r.data;
  form.id = d.id;
  form.transferNo = d.transferNo;
  form.fromWarehouseId = d.fromWarehouseId;
  form.toWarehouseId = d.toWarehouseId;
  form.remark = d.remark;
  form.items = (d.items ?? []).map((i) => ({
    _key: ++keySeq,
    skuId: i.skuId,
    // 后端返回定点字符串，InputNumber 需要 number；空值保持 null
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

/** 提交前逐行校验，并指出第几行缺什么。 */
function buildPayload(): InventoryTransferAdd | null {
  if (String(form.fromWarehouseId) === String(form.toWarehouseId)) {
    message.warning('源仓库与目标仓库不能相同');
    return null;
  }
  const items = form.items.filter((i) => i.skuId !== undefined && i.skuId !== null);
  if (items.length === 0) {
    message.warning('请至少添加一条调拨明细');
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
    // 数量必须大于 0，方向由发出/收货动作决定
    if (fixed4(items[i].quantity) === undefined) {
      message.warning(`第 ${i + 1} 行：请填写调拨数量`);
      return null;
    }
  }
  return {
    fromWarehouseId: form.fromWarehouseId as string | number,
    toWarehouseId: form.toWarehouseId as string | number,
    remark: form.remark,
    // 数量以定点字符串提交，后端不接受 JSON 数字
    items: items.map((i) => ({
      skuId: i.skuId as string | number,
      quantity: fixed4(i.quantity) as string,
      remark: i.remark,
    })),
  };
}

async function onSubmit() {
  await formRef.value?.validate();
  const payload = buildPayload();
  if (!payload) return;
  saving.value = true;
  try {
    if (form.id) {
      await inventoryTransferApi.update(form.id, payload);
      message.success('已保存草稿');
    } else {
      await inventoryTransferApi.create(payload);
      message.success('已创建草稿');
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
// 初始为空对象，打开详情时替换为后端数据
const detail = ref<Partial<InventoryTransfer>>({});

async function openDetail(record: InventoryTransfer) {
  try {
    const r = await inventoryTransferApi.detail(record.id);
    detail.value = r.data;
    detailOpen.value = true;
  } catch (e) {
    message.error(inventoryError(e));
  }
}

function onShip(record: InventoryTransfer) {
  Modal.confirm({
    title: '发出调拨',
    content: `确认从「${record.fromWarehouseName || '源仓'}」发出「${
      record.transferNo
    }」的明细数量？发出后源仓库存立即扣减，单据进入在途，等待目标仓收货。在途期间不可取消。`,
    okText: '确认发出',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await inventoryTransferApi.ship(record.id);
        message.success('已发出，单据进入在途');
        queryData();
      } catch (e) {
        // 源仓可用量不足等业务失败会走到这里，整单已回滚
        message.error(inventoryError(e));
      }
    },
  });
}

function onReceive(record: InventoryTransfer) {
  Modal.confirm({
    title: '收货入库',
    content: `确认「${record.toWarehouseName || '目标仓'}」已收到「${
      record.transferNo
    }」的明细数量？收货后目标仓库存增加，单据完成。目标仓的记账单位必须与调拨单位一致。`,
    okText: '确认收货',
    cancelText: '取消',
    onOk: async () => {
      try {
        await inventoryTransferApi.receive(record.id);
        message.success('已收货，调拨完成');
        queryData();
      } catch (e) {
        // 单位不一致等失败会走到这里；单据停在在途，处理后重试
        message.error(inventoryError(e));
      }
    },
  });
}

function onCancel(record: InventoryTransfer) {
  Modal.confirm({
    title: '取消调拨单',
    content: `确认取消「${record.transferNo}」？取消不产生任何库存影响。`,
    okText: '取消单据',
    cancelText: '返回',
    onOk: async () => {
      try {
        await inventoryTransferApi.cancel(record.id);
        message.success('已取消');
        queryData();
      } catch (e) {
        message.error(inventoryError(e));
      }
    },
  });
}

function onDelete(record: InventoryTransfer) {
  Modal.confirm({
    title: '删除调拨单',
    content: `确认删除草稿「${record.transferNo}」？已发出或已收货的单据不可删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '返回',
    onOk: async () => {
      try {
        await inventoryTransferApi.delete(record.id);
        message.success('已删除');
        queryData();
      } catch (e) {
        message.error(inventoryError(e));
      }
    },
  });
}

// ------------------------------------------------------------------ 在途库存报表

const inTransitOpen = ref(false);
const inTransitLoading = ref(false);
/** 同一单号和 SKU 可能重复，加序号保证 rowKey 唯一。 */
const inTransitRows = ref<Array<InventoryInTransit & {rowKey: string}>>([]);

const inTransitColumns: TableColumnsType = [
  {title: '调拨单号', dataIndex: 'transferNo', width: 190},
  {title: '调拨方向', dataIndex: 'direction', width: 240, align: 'center'},
  {title: '商品规格', dataIndex: 'sku', width: 200},
  {title: '在途数量', dataIndex: 'quantity', width: 120},
  {title: '单位', dataIndex: 'unit', width: 76},
];

/** 打开在途库存报表，每次重新拉取，不缓存。 */
async function openInTransit() {
  inTransitOpen.value = true;
  inTransitLoading.value = true;
  try {
    const r = await inventoryTransferApi.inTransit();
    inTransitRows.value = (r.data ?? []).map((row, index) => ({
      ...row,
      rowKey: `${row.transferNo}-${row.skuId}-${index}`,
    }));
  } catch (e) {
    message.error(inventoryError(e));
  } finally {
    inTransitLoading.value = false;
  }
}

onMounted(async () => {
  await applySingleWarehouseDefault();
  await queryData();
});
</script>

<style scoped>
/* 箭头弱化，仓名不换行 */
.transfer-direction {
  display: flex;
  align-items: center;
  min-width: 0;
}

.transfer-direction > span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.transfer-direction__arrow {
  flex: none;
  margin: 0 6px;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}
</style>
