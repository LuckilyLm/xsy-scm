<!--
  库存盘点单（盘点波次新增）。

  状态机：DRAFT → CONFIRMED，草稿可 CANCELLED。已确认不可回退（流水 append-only）。
  「确认盘点」会真实调整库存并写不可逆流水，因此单独一个按钮 + 二次确认，不与「编辑」混在一起。

  页面上要讲清楚的一件事：**差异施加到「确认瞬间的账面量」，不是把账面直接改写成实盘数**。
  保存草稿到确认之间若发生了收货 / 出库，确认后的账面就不等于实盘数 ——
  这是正确行为（那笔收货没有被抹掉），但看起来像 bug，所以在确认弹窗与详情页都写明。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="盘点单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.stocktakeNo" placeholder="盘点单号" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="220px"/>
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
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:stocktake:query'">查询</a-button>
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
        <a-button type="primary" @click="openCreate" v-privilege="'scm:inventory:stocktake:add'">
          新建盘点单
        </a-button>
        <a-typography-text type="secondary" style="margin-left: 12px">
          确认盘点会把差异转成不可删除的盘盈 / 盘亏流水。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_STOCKTAKE"
            :refresh="queryData"
        />
      </div>
    </a-row>

    <a-table
        :id="SCM_INVENTORY_TABLE_ID.STOCKTAKE"
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText: '暂无盘点单' }"
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
                v-privilege="'scm:inventory:stocktake:update'"
            >
              编辑
            </a-button>
            <a-button
                v-if="record.status === 'DRAFT'"
                type="link"
                size="small"
                @click="onConfirm(record)"
                v-privilege="'scm:inventory:stocktake:confirm'"
            >
              确认盘点
            </a-button>
            <a-button
                v-if="record.status === 'DRAFT'"
                type="link"
                size="small"
                danger
                @click="onCancel(record)"
                v-privilege="'scm:inventory:stocktake:update'"
            >
              取消
            </a-button>
            <a-button
                v-if="record.status === 'DRAFT'"
                type="link"
                size="small"
                danger
                @click="onDelete(record)"
                v-privilege="'scm:inventory:stocktake:delete'"
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
      :title="form.id ? `编辑盘点单 ${form.stocktakeNo}` : '新建盘点单'"
      width="900"
      @close="closeDrawer"
  >
    <a-alert
        type="info"
        show-icon
        style="margin-bottom: 12px"
        message="账面量由系统在保存时自动快照，无需手工填写。实盘量填 0 表示确实一件不剩。"
    />
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical">
      <a-form-item label="盘点仓库" name="warehouseId">
        <WarehouseSelect v-model:value="form.warehouseId" :options="warehouses" width="260px"/>
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="form.remark" :rows="2" :maxlength="500" show-count/>
      </a-form-item>
      <a-form-item label="盘点明细" required>
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
            <template v-else-if="column.dataIndex === 'actualQuantity'">
              <a-input v-model:value="record.actualQuantity" placeholder="0.0000" style="width: 130px"/>
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
          同一个 SKU 只能出现一次 —— 重复行会让同一份差异被调整两次。
        </a-typography-text>
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
  <a-drawer :open="detailOpen" title="盘点单详情" width="860" @close="detailOpen = false">
    <a-descriptions :column="2" bordered size="small">
      <a-descriptions-item label="盘点单号">{{ detail.stocktakeNo }}</a-descriptions-item>
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
        :scroll="{ x: 900 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'bookQuantity'">
          <span class="num">{{ quantityText(record.bookQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'actualQuantity'">
          <span class="num">{{ quantityText(record.actualQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'deltaQuantity'">
          <span class="num" :class="deltaClass(record.deltaQuantity)">{{ deltaText(record.deltaQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unitSnapshot'">
          {{ record.unitSnapshot || '（草稿未确认）' }}
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <a-typography-text v-if="detail.status === 'CONFIRMED'" type="secondary" style="display: block; margin-top: 8px">
      差异 = 实盘量 − 账面量快照。确认时系统把差异施加到**确认瞬间的账面量**上，
      因此若在保存草稿之后发生过收货或出库，确认后的账面不会等于实盘量 —— 那笔变动被保留了。
    </a-typography-text>
  </a-drawer>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {inventoryStocktakeApi} from '/@/api/business/scm/inventory-stocktake-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {
  SCM_INVENTORY_STOCKTAKE_STATUS_ENUM,
  SCM_INVENTORY_TABLE_ID,
} from '/@/constants/business/scm/inventory-const';
import type {
  InventoryStocktake,
  InventoryStocktakeAdd,
  InventoryStocktakeQuery,
} from './inventory-types';
import type {Warehouse} from '../purchase/purchase-types';
import {quantityText, singleWarehouseDefault} from './inventory-model';
import {inventoryError} from './inventory-errors';
import {datetime} from '../common/scm-display';

const queryForm = reactive<InventoryStocktakeQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<InventoryStocktake[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const warehouses = ref<Warehouse[]>([]);
let requestId = 0;

const statusOptions = Object.values(SCM_INVENTORY_STOCKTAKE_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

const columns = ref<TableColumnsType<InventoryStocktake>>([
  {title: '盘点单号', dataIndex: 'stocktakeNo', width: 200},
  {title: '仓库', dataIndex: 'warehouseName', width: 160},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '确认人', dataIndex: 'operator', width: 140},
  {title: '确认时间', dataIndex: 'confirmedAt', width: 180},
  {title: '备注', dataIndex: 'remark', width: 200, ellipsis: true},
  {title: '创建时间', dataIndex: 'createdAt', width: 180},
  {title: '操作', dataIndex: 'action', width: 260, fixed: 'right'},
]);

const itemColumns: TableColumnsType = [
  {title: 'SKU', dataIndex: 'skuId', width: 290},
  {title: '实盘量', dataIndex: 'actualQuantity', width: 150},
  {title: '备注', dataIndex: 'remark'},
  {title: '操作', dataIndex: 'action', width: 80},
];

const detailItemColumns: TableColumnsType = [
  {title: 'SKU 编码', dataIndex: 'skuCode', width: 150},
  {title: 'SKU 名称', dataIndex: 'skuName', width: 130},
  {title: '商品名称', dataIndex: 'productName', width: 130},
  {title: '账面量', dataIndex: 'bookQuantity', align: 'right', width: 110},
  {title: '实盘量', dataIndex: 'actualQuantity', align: 'right', width: 110},
  {title: '差异', dataIndex: 'deltaQuantity', align: 'right', width: 110},
  {title: '单位', dataIndex: 'unitSnapshot', align: 'center', width: 110},
];

function statusColor(status?: string) {
  if (status === 'CONFIRMED') return 'green';
  if (status === 'CANCELLED') return 'default';
  return 'orange';
}

/** 差异带符号展示：盘盈 `+`、盘亏 `−`。后端已给 4 位定点字符串，这里只补符号。 */
function deltaText(value?: string | null) {
  if (value === undefined || value === null || value === '') return '—';
  const n = Number(value);
  if (!Number.isFinite(n)) return value;
  if (n === 0) return '0.0000';
  return n > 0 ? `+${value}` : `−${value.replace('-', '')}`;
}

function deltaClass(value?: string | null) {
  const n = Number(value);
  if (!Number.isFinite(n) || n === 0) return '';
  return n > 0 ? 'delta-up' : 'delta-down';
}

// ------------------------------------------------------------------ 查询

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await inventoryStocktakeApi.query({...queryForm});
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
  queryForm.stocktakeNo = undefined;
  queryForm.warehouseId = undefined;
  queryForm.status = undefined;
  onSearch();
}

// ------------------------------------------------------------------ 表单

interface EditableItem {
  _key: number;
  skuId?: string | number;
  actualQuantity: string;
  remark?: string;
}

const drawerOpen = ref(false);
const saving = ref(false);
const formRef = ref();
let keySeq = 0;

const form = reactive<{
  id?: string | number;
  stocktakeNo?: string;
  warehouseId?: string | number;
  remark?: string;
  items: EditableItem[];
}>({items: []});

const formRules = {
  warehouseId: [{required: true, message: '请选择盘点仓库'}],
};

function addItem() {
  form.items.push({_key: ++keySeq, actualQuantity: ''});
}

function removeItem(index: number) {
  form.items.splice(index, 1);
}

function openCreate() {
  form.id = undefined;
  form.stocktakeNo = undefined;
  form.warehouseId = queryForm.warehouseId ?? singleWarehouseDefault(warehouses.value);
  form.remark = undefined;
  form.items = [];
  addItem();
  drawerOpen.value = true;
}

async function openEdit(record: InventoryStocktake) {
  const r = await inventoryStocktakeApi.detail(record.id);
  const d = r.data;
  form.id = d.id;
  form.stocktakeNo = d.stocktakeNo;
  form.warehouseId = d.warehouseId;
  form.remark = d.remark;
  form.items = (d.items ?? []).map((i) => ({
    _key: ++keySeq,
    skuId: i.skuId,
    // 实盘量后端以 4 位定点字符串返回，直接回填，不转 number（避免精度与类型问题）
    actualQuantity: i.actualQuantity ?? '',
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
function buildPayload(): InventoryStocktakeAdd | null {
  const items = form.items.filter((i) => i.skuId !== undefined && i.skuId !== null);
  if (items.length === 0) {
    message.warning('请至少添加一条盘点明细');
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
    const q = (items[i].actualQuantity ?? '').trim();
    // 实盘量允许 0（确实一件不剩），但不允许负数 —— 后端同样拒绝负数
    if (!/^\d+(\.\d{1,4})?$/.test(q)) {
      message.warning(`第 ${i + 1} 行：实盘量必须为不小于 0 的数字，最多 4 位小数`);
      return null;
    }
  }
  return {
    warehouseId: form.warehouseId as string | number,
    remark: form.remark,
    // 数量以字符串提交：后端拒绝 JSON 数字（ScmStrictDecimalStringDeserializer）
    items: items.map((i) => ({
      skuId: i.skuId as string | number,
      actualQuantity: i.actualQuantity.trim(),
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
      await inventoryStocktakeApi.update(form.id, payload);
      message.success('已保存草稿（账面量已重新快照）');
    } else {
      await inventoryStocktakeApi.create(payload);
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
const detail = ref<Partial<InventoryStocktake>>({});

async function openDetail(record: InventoryStocktake) {
  try {
    const r = await inventoryStocktakeApi.detail(record.id);
    detail.value = r.data;
    detailOpen.value = true;
  } catch (e) {
    message.error(inventoryError(e));
  }
}

function onConfirm(record: InventoryStocktake) {
  Modal.confirm({
    title: '确认盘点',
    width: 520,
    content: `确认后将把「${record.stocktakeNo}」各行「实盘量 − 账面量」的差异转成不可删除的盘盈 / 盘亏流水，并调整库存。此操作不可撤销。`,
    okText: '确认盘点',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await inventoryStocktakeApi.confirm(record.id);
        message.success('盘点完成，库存已按差异调整');
        queryData();
      } catch (e) {
        // 负库存（41024）/ 低于预留（41025）等业务失败会走到这里，整单已回滚
        message.error(inventoryError(e));
      }
    },
  });
}

function onCancel(record: InventoryStocktake) {
  Modal.confirm({
    title: '取消盘点单',
    content: `确认取消「${record.stocktakeNo}」？取消不产生任何库存影响。`,
    okText: '取消单据',
    cancelText: '返回',
    onOk: async () => {
      try {
        await inventoryStocktakeApi.cancel(record.id);
        message.success('已取消');
        queryData();
      } catch (e) {
        message.error(inventoryError(e));
      }
    },
  });
}

function onDelete(record: InventoryStocktake) {
  Modal.confirm({
    title: '删除盘点单',
    content: `确认删除草稿「${record.stocktakeNo}」？已确认的单据不可删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '返回',
    onOk: async () => {
      try {
        await inventoryStocktakeApi.delete(record.id);
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

.delta-up {
  color: #389e0d;
}

.delta-down {
  color: #cf1322;
}
</style>
