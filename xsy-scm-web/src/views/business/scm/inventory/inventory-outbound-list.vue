<!--
  库存出库单（出库波次新增）。

  状态机：DRAFT → CONFIRMED，草稿可 CANCELLED。已确认不可回退（流水 append-only）。
  「确认出库」会真实扣减库存并写不可逆流水，因此单独一个按钮 + 二次确认，
  不与「编辑」混在一起。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="出库单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.outboundNo" placeholder="出库单号" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="220px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select
          v-model:value="queryForm.status"
          :options="statusOptions"
          placeholder="全部"
          allow-clear
          style="width: 140px"
        />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:outbound:query'">查询</a-button>
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
        <a-button type="primary" @click="openCreate" v-privilege="'scm:inventory:outbound:add'">
          新建出库单
        </a-button>
        <a-typography-text type="secondary" style="margin-left: 12px">
          确认出库会扣减库存并生成不可删除的 SALES_OUT 流水。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
          v-model="columns"
          :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_OUTBOUND"
          :refresh="queryData"
        />
      </div>
    </a-row>

    <a-table
      :id="SCM_INVENTORY_TABLE_ID.OUTBOUND"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{ emptyText: '暂无出库单' }"
      :scroll="{ x: 1300 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="statusColor(record.status)">{{ record.statusDesc || record.status }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'confirmedAt'">{{ datetime(record.confirmedAt) }}</template>
        <template v-else-if="column.dataIndex === 'createdAt'">{{ datetime(record.createdAt) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button
              v-if="record.status === 'DRAFT'"
              type="link"
              size="small"
              @click="openEdit(record)"
              v-privilege="'scm:inventory:outbound:update'"
            >
              编辑
            </a-button>
            <a-button
              v-if="record.status === 'DRAFT'"
              type="link"
              size="small"
              @click="onConfirm(record)"
              v-privilege="'scm:inventory:outbound:confirm'"
            >
              确认出库
            </a-button>
            <a-button
              v-if="record.status === 'DRAFT'"
              type="link"
              size="small"
              danger
              @click="onCancel(record)"
              v-privilege="'scm:inventory:outbound:update'"
            >
              取消
            </a-button>
            <a-button
              v-if="record.status === 'DRAFT'"
              type="link"
              size="small"
              danger
              @click="onDelete(record)"
              v-privilege="'scm:inventory:outbound:delete'"
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

  <!-- 新建 / 编辑草稿 -->
  <a-drawer
    :open="drawerOpen"
    :title="form.id ? `编辑出库单 ${form.outboundNo}` : '新建出库单'"
    width="900"
    @close="closeDrawer"
  >
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical">
      <a-form-item label="出库仓库" name="warehouseId">
        <WarehouseSelect v-model:value="form.warehouseId" :options="warehouses" width="260px" />
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="form.remark" :rows="2" :maxlength="500" show-count />
      </a-form-item>
      <a-form-item label="出库明细" required>
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
              <a-input v-model:value="record.quantity" placeholder="0.0000" style="width: 130px" />
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

  <!-- 详情 -->
  <a-drawer :open="detailOpen" title="出库单详情" width="760" @close="detailOpen = false">
    <a-descriptions :column="2" bordered size="small">
      <a-descriptions-item label="出库单号">{{ detail.outboundNo }}</a-descriptions-item>
      <a-descriptions-item label="状态">
        <a-tag :color="statusColor(detail.status)">{{ detail.statusDesc || detail.status }}</a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="仓库">{{ detail.warehouseName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="确认人">{{ detail.operator || '—' }}</a-descriptions-item>
      <a-descriptions-item label="确认时间">{{ datetime(detail.confirmedAt) }}</a-descriptions-item>
      <a-descriptions-item label="创建时间">{{ datetime(detail.createdAt) }}</a-descriptions-item>
      <a-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</a-descriptions-item>
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
          {{ record.unitSnapshot || '（草稿未确认）' }}
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
  </a-drawer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { message, Modal } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import { inventoryOutboundApi } from '/@/api/business/scm/inventory-outbound-api';
import { warehouseApi } from '/@/api/business/scm/warehouse-api';
import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
import {
  SCM_INVENTORY_OUTBOUND_STATUS_ENUM,
  SCM_INVENTORY_TABLE_ID,
} from '/@/constants/business/scm/inventory-const';
import type {
  InventoryOutbound,
  InventoryOutboundAdd,
  InventoryOutboundQuery,
} from './inventory-types';
import type { Warehouse } from '../purchase/purchase-types';
import { quantityText, singleWarehouseDefault } from './inventory-model';
import { inventoryError } from './inventory-errors';
import { datetime } from '../common/scm-display';

const queryForm = reactive<InventoryOutboundQuery>({ pageNum: 1, pageSize: 20 });
const tableData = ref<InventoryOutbound[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const warehouses = ref<Warehouse[]>([]);
let requestId = 0;

const statusOptions = Object.values(SCM_INVENTORY_OUTBOUND_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

const columns = ref<TableColumnsType<InventoryOutbound>>([
  { title: '出库单号', dataIndex: 'outboundNo', width: 200 },
  { title: '仓库', dataIndex: 'warehouseName', width: 160 },
  { title: '状态', dataIndex: 'status', align: 'center', width: 100 },
  { title: '确认人', dataIndex: 'operator', width: 140 },
  { title: '确认时间', dataIndex: 'confirmedAt', width: 180 },
  { title: '备注', dataIndex: 'remark', width: 200, ellipsis: true },
  { title: '创建时间', dataIndex: 'createdAt', width: 180 },
  { title: '操作', dataIndex: 'action', width: 260, fixed: 'right' },
]);

const itemColumns: TableColumnsType = [
  { title: 'SKU', dataIndex: 'skuId', width: 290 },
  { title: '出库数量', dataIndex: 'quantity', width: 150 },
  { title: '备注', dataIndex: 'remark' },
  { title: '操作', dataIndex: 'action', width: 80 },
];

const detailItemColumns: TableColumnsType = [
  { title: 'SKU 编码', dataIndex: 'skuCode', width: 160 },
  { title: 'SKU 名称', dataIndex: 'skuName', width: 150 },
  { title: '商品名称', dataIndex: 'productName', width: 150 },
  { title: '数量', dataIndex: 'quantity', align: 'right', width: 110 },
  { title: '单位', dataIndex: 'unitSnapshot', align: 'center', width: 120 },
];

function statusColor(status?: string) {
  if (status === 'CONFIRMED') return 'green';
  if (status === 'CANCELLED') return 'default';
  return 'orange';
}

// ------------------------------------------------------------------ 查询

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await inventoryOutboundApi.query({ ...queryForm });
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
  queryForm.outboundNo = undefined;
  queryForm.warehouseId = undefined;
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
  outboundNo?: string;
  warehouseId?: string | number;
  remark?: string;
  items: EditableItem[];
}>({ items: [] });

const formRules = {
  warehouseId: [{ required: true, message: '请选择出库仓库' }],
};

function addItem() {
  form.items.push({ _key: ++keySeq, quantity: '' });
}

function removeItem(index: number) {
  form.items.splice(index, 1);
}

function openCreate() {
  form.id = undefined;
  form.outboundNo = undefined;
  form.warehouseId = queryForm.warehouseId ?? singleWarehouseDefault(warehouses.value);
  form.remark = undefined;
  form.items = [];
  addItem();
  drawerOpen.value = true;
}

async function openEdit(record: InventoryOutbound) {
  const r = await inventoryOutboundApi.detail(record.id);
  const d = r.data;
  form.id = d.id;
  form.outboundNo = d.outboundNo;
  form.warehouseId = d.warehouseId;
  form.remark = d.remark;
  form.items = (d.items ?? []).map((i) => ({
    _key: ++keySeq,
    skuId: i.skuId,
    // 明细数量后端以 4 位定点字符串返回，直接回填，不转 number（避免精度与类型问题）
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
function buildPayload(): InventoryOutboundAdd | null {
  const items = form.items.filter((i) => i.skuId !== undefined && i.skuId !== null);
  if (items.length === 0) {
    message.warning('请至少添加一条出库明细');
    return null;
  }
  for (let i = 0; i < items.length; i++) {
    const q = (items[i].quantity ?? '').trim();
    if (!/^\d+(\.\d{1,4})?$/.test(q) || Number(q) <= 0) {
      message.warning(`第 ${i + 1} 行：出库数量必须为大于 0 的数字，最多 4 位小数`);
      return null;
    }
  }
  return {
    warehouseId: form.warehouseId as string | number,
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
      await inventoryOutboundApi.update(form.id, payload);
      message.success('已保存草稿');
    } else {
      await inventoryOutboundApi.create(payload);
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
// Partial：初始为空对象（还没选中任何单据），打开详情时整体替换为后端返回的 VO
const detail = ref<Partial<InventoryOutbound>>({});

async function openDetail(record: InventoryOutbound) {
  try {
    const r = await inventoryOutboundApi.detail(record.id);
    detail.value = r.data;
    detailOpen.value = true;
  } catch (e) {
    message.error(inventoryError(e));
  }
}

function onConfirm(record: InventoryOutbound) {
  Modal.confirm({
    title: '确认出库',
    content: `确认后将从库存中扣减「${record.outboundNo}」的明细数量，并生成不可删除的出库流水。此操作不可撤销。`,
    okText: '确认出库',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await inventoryOutboundApi.confirm(record.id);
        message.success('出库完成，库存已扣减');
        queryData();
      } catch (e) {
        // 可用量不足（41011）等业务失败会走到这里，整单已回滚
        message.error(inventoryError(e));
      }
    },
  });
}

function onCancel(record: InventoryOutbound) {
  Modal.confirm({
    title: '取消出库单',
    content: `确认取消「${record.outboundNo}」？取消不产生任何库存影响。`,
    okText: '取消单据',
    cancelText: '返回',
    onOk: async () => {
      try {
        await inventoryOutboundApi.cancel(record.id);
        message.success('已取消');
        queryData();
      } catch (e) {
        message.error(inventoryError(e));
      }
    },
  });
}

function onDelete(record: InventoryOutbound) {
  Modal.confirm({
    title: '删除出库单',
    content: `确认删除草稿「${record.outboundNo}」？已确认的单据不可删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '返回',
    onOk: async () => {
      try {
        await inventoryOutboundApi.delete(record.id);
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
