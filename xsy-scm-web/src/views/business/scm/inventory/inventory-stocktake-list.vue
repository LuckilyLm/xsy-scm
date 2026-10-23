<!-- 库存盘点单：确认时把“实盘量 − 账面量”的差异计入库存。确认时的账面量可能与保存草稿时不同。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="盘点单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.stocktakeNo" placeholder="盘点单号" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="220px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select v-model:value="queryForm.status" :options="statusOptions" placeholder="全部" allow-clear style="width: 140px" />
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
        <a-button type="primary" @click="openCreate" v-privilege="'scm:inventory:stocktake:add'"> 新建盘点单 </a-button>
        <a-button style="margin-left: 8px" :loading="templateLoading" @click="onDownloadTemplate" v-privilege="'scm:inventory:stocktake:import'">
          导出盘点模板
        </a-button>
        <a-upload :show-upload-list="false" :custom-request="onUploadImport" accept=".xlsx" style="display: inline-block; margin-left: 8px">
          <a-button :loading="importing" v-privilege="'scm:inventory:stocktake:import'">导入盘点</a-button>
        </a-upload>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_STOCKTAKE" :refresh="queryData" />
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
      :locale="{emptyText: '暂无盘点单'}"
      :scroll="{x: scrollX}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'warehouseName'">{{ record.warehouseName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'warehouseCode'">
          <span class="scm-mono">{{ record.warehouseCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="statusTone(record.status)" :label="record.statusDesc || record.status" />
        </template>
        <template v-else-if="column.dataIndex === 'confirmedAt'">{{ datetime(record.confirmedAt) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 详情和确认盘点常驻，其他操作收进“更多” -->
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button
              v-if="record.status === 'DRAFT'"
              type="link"
              size="small"
              @click="onConfirm(record)"
              v-privilege="'scm:inventory:stocktake:confirm'"
            >
              确认盘点
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
  <a-drawer :open="drawerOpen" :title="form.id ? `编辑盘点单 ${form.stocktakeNo}` : '新建盘点单'" :width="scmDrawerWidth('l')" @close="closeDrawer">
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical" class="app-drawer-form">
      <a-form-item label="盘点仓库" name="warehouseId">
        <WarehouseSelect v-model:value="form.warehouseId" :options="warehouses" width="260px" />
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="form.remark" :rows="2" :maxlength="500" show-count />
      </a-form-item>
      <a-form-item label="盘点明细" required>
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
            <template v-else-if="column.dataIndex === 'unit'">
              <a-typography-text type="secondary">{{ record.unit || '—' }}</a-typography-text>
            </template>
            <template v-else-if="column.dataIndex === 'actualQuantity'">
              <a-input-number v-model:value="record.actualQuantity" :min="0" :precision="4" :step="1" placeholder="0.0000" style="width: 140px" />
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

  <InventoryStocktakeDetailDrawer v-model:open="detailOpen" :detail="detail" />

  <!-- 导入结果：整批校验，任一错误行都不会创建盘点单 -->
  <a-modal :open="importResultOpen" title="盘点导入结果" :closable="false" width="720" @cancel="importResultOpen = false">
    <a-alert
      v-if="importResult && importResult.stocktakeId"
      type="success"
      show-icon
      :message="`已创建草稿（${importResult.importedItems} 条明细）`"
    />
    <a-alert
      v-else-if="importResult"
      type="error"
      show-icon
      :message="`整批未导入：${importResult.totalErrors} / ${importResult.totalRows} 行存在问题，未生成任何草稿`"
      description="来源行按导出时的盘点清单锁定，不能增删或替换；实盘量不能为空；清单过期或账面已变动时请重新导出。"
    />
    <a-table
      v-if="importResult && importResult.errors.length"
      style="margin-top: 12px"
      size="small"
      :data-source="importResult.errors"
      :columns="importErrorColumns"
      :row-key="(_r: unknown, i: number) => i"
      :pagination="false"
      :scroll="{y: 320}"
    />
    <template #footer>
      <a-button type="primary" @click="importResultOpen = false">知道了</a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType, UploadProps, FormInstance} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import InventoryStocktakeDetailDrawer from './components/inventory-stocktake-detail-drawer.vue';
import {inventoryStocktakeApi} from '/@/api/business/scm/inventory-stocktake-api';
import {inventoryBalanceApi} from '/@/api/business/scm/inventory-balance-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_INVENTORY_STOCKTAKE_STATUS_ENUM, SCM_INVENTORY_TABLE_ID} from '/@/constants/business/scm/inventory-const';
import type {InventoryStocktake, InventoryStocktakeAdd, InventoryStocktakeQuery, StocktakeImportResult} from './inventory-types';
import type {Warehouse} from '../purchase/purchase-types';
import {fixed4} from '../common/scm-fixed';
import {resolveStocktakeCopyUnits, singleWarehouseDefault} from './inventory-model';
import {hasPermission} from '../common/scm-permission';
import {inventoryError} from './inventory-errors';
import {datetime} from '../common/scm-display';
import {scmColumnsWidth, type ScmListColumn} from '../common/scm-column';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

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

// 创建时间不上列，业务时间用确认时间。
type InventoryStocktakeColumn = ScmListColumn;
const columns = ref<InventoryStocktakeColumn[]>([
  {title: '盘点单号', dataIndex: 'stocktakeNo', width: 200},
  {title: '仓库', dataIndex: 'warehouseName', width: 140},
  {title: '仓库编码', dataIndex: 'warehouseCode', width: 120, showFlag: false},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '确认人', dataIndex: 'operator', width: 130},
  {title: '确认时间', dataIndex: 'confirmedAt', width: 170},
  {title: '备注', dataIndex: 'remark', width: 200, ellipsis: true},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 150},
]);
const scrollX = computed(() => scmColumnsWidth(columns.value));

const itemColumns: TableColumnsType = [
  {title: '商品规格', dataIndex: 'skuId', width: 290},
  {title: '记账单位', dataIndex: 'unit', width: 76},
  {title: '实盘量', dataIndex: 'actualQuantity', width: 160},
  {title: '备注', dataIndex: 'remark'},
  {title: '操作', dataIndex: 'action', width: 80, align: 'center'},
];

/** 草稿 = 盘点进行中（橙），已确认 = 已完成（绿），已取消 = 失效（灰）。 */
const STATUS_TONE: Record<string, ScmStatusTone> = {
  DRAFT: 'warning',
  CONFIRMED: 'success',
  CANCELLED: 'neutral',
};
const statusTone = (status?: string | null): ScmStatusTone => STATUS_TONE[status ?? ''] ?? 'neutral';

/** 更多菜单无法使用 `v-privilege`，改用 `hasPermission` 判断。 */
const canAdd = computed(() => hasPermission('scm:inventory:stocktake:add'));
const canUpdate = computed(() => hasPermission('scm:inventory:stocktake:update'));
const canDelete = computed(() => hasPermission('scm:inventory:stocktake:delete'));

/** 复制到新建对所有状态可用，其余动作只在草稿态。 */
function rowActions(row: InventoryStocktake): ScmActionItem[] {
  const draft = row.status === 'DRAFT';
  return [
    {key: 'copy', label: '复制到新建', hidden: !canAdd.value},
    {key: 'edit', label: '编辑', hidden: !(draft && canUpdate.value)},
    {key: 'cancel', label: '取消单据', hidden: !(draft && canUpdate.value)},
    {key: 'delete', label: '删除', danger: true, hidden: !(draft && canDelete.value)},
  ];
}

function onRowAction(key: string, row: InventoryStocktake) {
  if (key === 'copy') {
    void openCopy(row);
  } else if (key === 'edit') {
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
    const r = await inventoryStocktakeApi.query({...queryForm}, {suppressGlobalErrorMessage: true});
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
  /** 记账单位，仅复制到新建时带入。 */
  unit?: string;
  /** 表单里用 number，提交时转成 4 位定点字符串；null 表示未清点，0 表示确实没有。 */
  actualQuantity?: number | null;
  remark?: string;
}

const drawerOpen = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
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
  form.items.push({_key: ++keySeq, actualQuantity: null});
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
    // 后端以 4 位定点字符串返回实盘量，InputNumber 要 number；空值保持 null
    actualQuantity: i.actualQuantity == null ? null : Number(i.actualQuantity),
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

// 复制历史盘点：只填表单，不写库

/**
 * 复制历史盘点的仓库、商品规格和当前单位到未保存的新建表单。
 * 实盘量一律留空；任一规格已无余额时整单不复制。
 */
async function openCopy(record: InventoryStocktake) {
  try {
    const r = await inventoryStocktakeApi.detail(record.id);
    const d = r.data;
    const sourceItems = d.items ?? [];
    if (sourceItems.length === 0) {
      message.warning('来源盘点单没有明细，无可复制内容');
      return;
    }
    const whId = d.warehouseId as string | number;
    // 读取当前余额只为获取记账单位并确认规格仍有库存
    const {unitBySku} = await resolveStocktakeCopyUnits(
      sourceItems.map((i) => i.skuId),
      async (pageNum, pageSize) => {
        const bal = await inventoryBalanceApi.query({warehouseId: whId, pageNum, pageSize});
        return bal.data.list;
      }
    );
    const items: EditableItem[] = [];
    for (const i of sourceItems) {
      const key = String(i.skuId);
      if (!unitBySku.has(key)) {
        message.error(`商品规格「${i.skuCode ?? i.skuName ?? key}」在仓库「${d.warehouseName ?? whId}」已无库存余额，无法复制，请改用新建`);
        return;
      }
      items.push({
        _key: ++keySeq,
        skuId: i.skuId,
        unit: unitBySku.get(key),
        actualQuantity: null,
        remark: undefined,
      });
    }
    form.id = undefined;
    form.stocktakeNo = undefined;
    form.warehouseId = whId;
    form.remark = undefined;
    form.items = items;
    drawerOpen.value = true;
    message.info('已复制到新建表单（未保存）：请重新清点并逐行填写实盘量');
  } catch (e) {
    message.error(inventoryError(e));
  }
}

// ------------------------------------------------------------------ Excel 导入

const templateLoading = ref(false);
const importing = ref(false);
const importResultOpen = ref(false);
const importResult = ref<StocktakeImportResult | null>(null);

const importErrorColumns: TableColumnsType = [
  {title: '行', dataIndex: 'row', width: 60},
  {title: '商品规格编码', dataIndex: 'skuCode', width: 140},
  {title: '列', dataIndex: 'column', width: 120},
  {title: '原因', dataIndex: 'message'},
];

/** 导出模板使用查询条件里的仓库；未选仓库时不导出。 */
async function onDownloadTemplate() {
  if (queryForm.warehouseId === undefined || queryForm.warehouseId === null || queryForm.warehouseId === '') {
    message.warning('请先在上方查询条件选择仓库，再导出盘点模板');
    return;
  }
  templateLoading.value = true;
  try {
    await inventoryStocktakeApi.downloadImportTemplate(queryForm.warehouseId);
  } catch (e) {
    message.error(inventoryError(e));
  } finally {
    templateLoading.value = false;
  }
}

/** 接管上传，走带 Idempotency-Key 的导入接口；整批校验失败时不创建盘点单。 */
const onUploadImport: UploadProps['customRequest'] = async (options) => {
  const file = options.file as File;
  importing.value = true;
  try {
    const r = await inventoryStocktakeApi.importStocktake(file);
    if (r.code !== 0) {
      message.error(r.msg || '导入失败');
    } else {
      importResult.value = r.data;
      importResultOpen.value = true;
      if (r.data.stocktakeId) {
        queryData();
      }
    }
    options.onSuccess?.(r);
  } catch (e) {
    message.error(inventoryError(e));
    options.onError?.(e as Error);
  } finally {
    importing.value = false;
  }
};

/** 提交前逐行校验；实盘量 0 是有效值，未填才报错。 */
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
      message.warning(`第 ${i + 1} 行：同一商品规格只能出现一次，请合并重复行`);
      return null;
    }
    seen.add(skuKey);
    if (fixed4(items[i].actualQuantity) === undefined) {
      message.warning(`第 ${i + 1} 行：请填写实盘量（一件不剩请填 0）`);
      return null;
    }
  }
  return {
    warehouseId: form.warehouseId as string | number,
    remark: form.remark,
    // 数量以定点字符串提交，后端不接受 JSON 数字
    items: items.map((i) => ({
      skuId: i.skuId as string | number,
      actualQuantity: fixed4(i.actualQuantity) as string,
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
      await inventoryStocktakeApi.update(form.id, payload);
      message.success('已保存草稿（账面量已更新）');
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
// 初始为空对象，打开详情时替换为后端数据
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
        // 负库存、低于预留等业务失败会走到这里，整单已回滚
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
