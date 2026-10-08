<!--
  库存出库单。

  状态机：DRAFT → CONFIRMED，草稿可 CANCELLED。已确认不可回退（流水 append-only）。
  「确认出库」会真实扣减库存并写不可逆流水，因此单独一个按钮 + 二次确认，
  不与「编辑」混在一起。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="出库单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.outboundNo" placeholder="出库单号" allow-clear @pressEnter="onSearch"/>
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
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:outbound:query'">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button type="primary" @click="openCreate" v-privilege="'scm:inventory:outbound:add'">
          新建出库单
        </a-button>
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
        :scroll="{ x: 1250 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'outboundNo'">
          <span class="scm-mono">{{ record.outboundNo || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'warehouseName'">{{ record.warehouseName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'warehouseCode'">
          <span class="scm-mono">{{ record.warehouseCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="statusTone(record.status)" :label="record.statusDesc || record.status"/>
        </template>
        <template v-else-if="column.dataIndex === 'confirmedAt'">{{ datetime(record.confirmedAt) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 行内常驻「详情」与当前状态最高频的「确认出库」，其余收进「更多」；
               确认出库不可撤销，故不与编辑 / 取消 / 删除混在一排 -->
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button
                v-if="record.status === 'DRAFT'"
                type="link"
                size="small"
                @click="onConfirm(record)"
                v-privilege="'scm:inventory:outbound:confirm'"
            >
              确认出库
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

  <!-- 新建 / 编辑草稿 -->
  <a-drawer
      :open="drawerOpen"
      :title="form.id ? `编辑出库单 ${form.outboundNo}` : '新建出库单'"
      :width="scmDrawerWidth('l')"
      @close="closeDrawer"
  >
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical">
      <a-form-item label="出库仓库" name="warehouseId">
        <WarehouseSelect v-model:value="form.warehouseId" :options="warehouses" width="260px"/>
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="form.remark" :rows="2" :maxlength="500" show-count/>
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
  <a-drawer :open="detailOpen" title="出库单详情" :width="scmDrawerWidth('l')" @close="detailOpen = false">
    <a-descriptions :column="2" bordered size="small">
      <a-descriptions-item label="出库单号">{{ detail.outboundNo }}</a-descriptions-item>
      <a-descriptions-item label="状态">
        <ScmStatusTag :tone="statusTone(detail.status)" :label="detail.statusDesc || detail.status"/>
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
        <template v-if="column.dataIndex === 'sku'">{{ skuMainText(record.specValues, record.skuName) }}</template>
        <template v-else-if="column.dataIndex === 'skuCode'">
          <span class="scm-mono">{{ record.skuCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
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
import {computed, onMounted, reactive, ref, watch} from 'vue';
import {useRoute} from 'vue-router';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {inventoryOutboundApi} from '/@/api/business/scm/inventory-outbound-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {deepLinkFilters, type DeepLinkQuery} from '/@/lib/query-deep-link';
import {
  SCM_INVENTORY_OUTBOUND_STATUS_ENUM,
  SCM_INVENTORY_TABLE_ID,
} from '/@/constants/business/scm/inventory-const';
import type {
  InventoryOutbound,
  InventoryOutboundAdd,
  InventoryOutboundQuery,
} from './inventory-types';
import type {Warehouse} from '../purchase/purchase-types';
import {fixed4} from '../common/scm-fixed';
import {quantityText, singleWarehouseDefault, skuMainText} from './inventory-model';
import {hasPermission} from '../common/scm-permission';
import {inventoryError} from './inventory-errors';
import {datetime} from '../common/scm-display';
import {useScmErrorToast} from '../common/scm-error-toast';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const queryForm = reactive<InventoryOutboundQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<InventoryOutbound[]>([]);
const total = ref(0);
const loading = ref(false);
const error = useScmErrorToast();
const warehouses = ref<Warehouse[]>([]);
let requestId = 0;

const statusOptions = Object.values(SCM_INVENTORY_OUTBOUND_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

// 列表按「哪张单 / 哪个仓 / 什么状态 / 谁在什么时候出的」排列，仓库名与仓库编码各自成列。
// 创建时间是技术字段：出库单的业务时刻是确认时间，草稿态的创建时间对使用者没有决策价值，不上列。
const columns = ref<TableColumnsType<InventoryOutbound>>([
  {title: '出库单号', dataIndex: 'outboundNo', width: 190},
  {title: '仓库', dataIndex: 'warehouseName', width: 150},
  {title: '仓库编码', dataIndex: 'warehouseCode', width: 130},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '确认人', dataIndex: 'operator', width: 120},
  {title: '出库时间', dataIndex: 'confirmedAt', width: 170},
  {title: '备注', dataIndex: 'remark', width: 200, ellipsis: true},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 150},
]);

/** 草稿 = 待处理（橙），已确认 = 已完成（绿），已取消 = 失效（灰）。 */
const STATUS_TONE: Record<string, ScmStatusTone> = {
  DRAFT: 'warning',
  CONFIRMED: 'success',
  CANCELLED: 'neutral',
};
const statusTone = (status?: string | null): ScmStatusTone => STATUS_TONE[status ?? ''] ?? 'neutral';

const itemColumns: TableColumnsType = [
  {title: '商品规格', dataIndex: 'skuId', width: 290},
  {title: '出库数量', dataIndex: 'quantity', width: 160},
  {title: '备注', dataIndex: 'remark'},
  {title: '操作', dataIndex: 'action', width: 80, align: 'center'},
];

// 明细的名称与编码各自成列（与列表页同一口径）。
const detailItemColumns: TableColumnsType = [
  {title: '商品', dataIndex: 'productName', width: 150},
  {title: '商品规格', dataIndex: 'sku', width: 170},
  {title: '商品规格编码', dataIndex: 'skuCode', width: 150},
  {title: '数量', dataIndex: 'quantity', align: 'right', width: 110},
  {title: '单位', dataIndex: 'unitSnapshot', align: 'center', width: 110},
];

/** 「更多」里的菜单项挂不上 `v-privilege` 指令，改用同一口径的 hasPermission 裁剪。 */
const canUpdate = computed(() => hasPermission('scm:inventory:outbound:update'));
const canDelete = computed(() => hasPermission('scm:inventory:outbound:delete'));

/** 只有草稿可写；动作集合与原行内按钮一一对应，只按频率重新分组。 */
function rowActions(row: InventoryOutbound): ScmActionItem[] {
  const draft = row.status === 'DRAFT';
  return [
    {key: 'edit', label: '编辑', hidden: !(draft && canUpdate.value)},
    {key: 'cancel', label: '取消单据', hidden: !(draft && canUpdate.value)},
    {key: 'delete', label: '删除', danger: true, hidden: !(draft && canDelete.value)},
  ];
}

function onRowAction(key: string, row: InventoryOutbound) {
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
    const r = await inventoryOutboundApi.query({...queryForm});
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

/**
 * URL 带入的筛选条件：配送线路详情页的「跳出库单」带 `?outboundNo=`。
 *
 * 只把值填进已有筛选项，不新增任何后端能力；出库单号是自由文本（`null` 白名单），
 * 但仍经 `deepLinkFilters` 做 trim 与空值收口，避免 `?outboundNo=` 空串被当成有效条件。
 * 用 `route.query` 而不是 props：菜单路由不传 props，hash 路由下 query 是唯一稳定通道。
 */
const OUTBOUND_DEEP_LINK = {outboundNo: null};

const route = useRoute();
const outboundRouteName = route.name;

function applyDeepLink(incomingQuery: DeepLinkQuery) {
  queryForm.outboundNo = deepLinkFilters(incomingQuery, OUTBOUND_DEEP_LINK).outboundNo;
}

watch(
    () => route.query,
    (incomingQuery) => {
      // 组件被 keep-alive 缓存时，跳往其他页面不能把别人的 URL 当成本页条件。
      if (route.name !== outboundRouteName) return;
      applyDeepLink(incomingQuery);
      onSearch();
    }
);

// ------------------------------------------------------------------ 表单

interface EditableItem {
  _key: number;
  skuId?: string | number;
  /**
   * 明细数量在表单里是 `number`（InputNumber 只接受数字），提交时才转成后端要求的
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
  outboundNo?: string;
  warehouseId?: string | number;
  remark?: string;
  items: EditableItem[];
}>({items: []});

const formRules = {
  warehouseId: [{required: true, message: '请选择出库仓库'}],
};

function addItem() {
  form.items.push({_key: ++keySeq, quantity: null});
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
    // 后端以 4 位定点字符串返回明细数量，InputNumber 要 number；空值保持 null
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

/**
 * 明细校验在提交前做：逐行给出「第几行缺什么」，比一条笼统的「参数不合法」有用得多。
 *
 * 数量大于 0 与最多 4 位小数已由 InputNumber（`:min="0.0001"` + `:precision="4"`）
 * 结构性保证，这里只需拦住「整行没填数量」。
 */
function buildPayload(): InventoryOutboundAdd | null {
  const items = form.items.filter((i) => i.skuId !== undefined && i.skuId !== null);
  if (items.length === 0) {
    message.warning('请至少添加一条出库明细');
    return null;
  }
  for (let i = 0; i < items.length; i++) {
    if (fixed4(items[i].quantity) === undefined) {
      message.warning(`第 ${i + 1} 行：请填写出库数量`);
      return null;
    }
  }
  return {
    warehouseId: form.warehouseId as string | number,
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
  // 首屏先落 URL 条件再查，否则从配送页跳进来会先闪一次全量列表。
  applyDeepLink(route.query);
  await applySingleWarehouseDefault();
  await queryData();
});
</script>
