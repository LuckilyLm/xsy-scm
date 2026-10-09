<!--
  库存盘点单。

  状态机：DRAFT → CONFIRMED，草稿可 CANCELLED。已确认不可回退（流水 append-only）。
  「确认盘点」会真实调整库存并写不可逆流水，因此单独一个按钮 + 二次确认，不与「编辑」混在一起。

  页面上要讲清楚的一件事：差异施加到「确认瞬间的账面量」，不是把账面直接改写成实盘数。
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
        <a-button
            style="margin-left: 8px"
            :loading="templateLoading"
            @click="onDownloadTemplate"
            v-privilege="'scm:inventory:stocktake:import'"
        >
          导出快照模板
        </a-button>
        <a-upload
            :show-upload-list="false"
            :custom-request="onUploadImport"
            accept=".xlsx"
            style="display: inline-block; margin-left: 8px"
        >
          <a-button :loading="importing" v-privilege="'scm:inventory:stocktake:import'">导入盘点</a-button>
        </a-upload>
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
        :scroll="{ x: scrollX }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'warehouseName'">{{ record.warehouseName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'warehouseCode'">
          <span class="scm-mono">{{ record.warehouseCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="statusTone(record.status)" :label="record.statusDesc || record.status"/>
        </template>
        <template v-else-if="column.dataIndex === 'confirmedAt'">{{ datetime(record.confirmedAt) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 行内常驻「详情」与草稿态的「确认盘点」（会真实调整库存，故单独留在行内），
               复制 / 编辑 / 取消 / 删除收进「更多」 -->
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
      :title="form.id ? `编辑盘点单 ${form.stocktakeNo}` : '新建盘点单'"
      :width="scmDrawerWidth('l')"
      @close="closeDrawer"
  >
    <a-alert
        type="info"
        show-icon
        style="margin-bottom: 12px"
        message="保存草稿前不产生单据；账面量保存时自动快照。"
    />
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical" class="app-drawer-form">
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
            <template v-else-if="column.dataIndex === 'unit'">
              <a-typography-text type="secondary">{{ record.unit || '—' }}</a-typography-text>
            </template>
            <template v-else-if="column.dataIndex === 'actualQuantity'">
              <a-input-number
                  v-model:value="record.actualQuantity"
                  :min="0"
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
          同一个商品规格只能出现一次 —— 重复行会让同一份差异被调整两次。
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

  <InventoryStocktakeDetailDrawer v-model:open="detailOpen" :detail="detail" />

  <!-- 导入结果：整批校验，任一错误行都不落库（后端返回 code=0 且 totalErrors>0，故按数据判定，不看信封码） -->
  <a-modal
      :open="importResultOpen"
      title="盘点导入结果"
      :closable="false"
      width="720"
      @cancel="importResultOpen = false"
  >
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
        description="来源行由导出快照锁定，不能增删 / 替换；实盘量不能为空；快照过期或账面版本已变动需重新导出并核对。"
    />
    <a-table
        v-if="importResult && importResult.errors.length"
        style="margin-top: 12px"
        size="small"
        :data-source="importResult.errors"
        :columns="importErrorColumns"
        :row-key="(_r: unknown, i: number) => i"
        :pagination="false"
        :scroll="{ y: 320 }"
    />
    <template #footer>
      <a-button type="primary" @click="importResultOpen = false">知道了</a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType, UploadProps} from 'ant-design-vue';
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
import {
  SCM_INVENTORY_STOCKTAKE_STATUS_ENUM,
  SCM_INVENTORY_TABLE_ID,
} from '/@/constants/business/scm/inventory-const';
import type {
  InventoryStocktake,
  InventoryStocktakeAdd,
  InventoryStocktakeQuery,
  StocktakeImportResult,
} from './inventory-types';
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

// 列表按「哪张单 / 哪个仓 / 什么状态 / 谁在什么时候盘的」排列。创建时间是技术字段，
// 盘点单的业务时刻是确认时间，不上列。
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
  {title: '记账单位', dataIndex: 'unit', align: 'center', width: 100},
  {title: '实盘量', dataIndex: 'actualQuantity', width: 160, align: 'right'},
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

/** 「更多」里的菜单项挂不上 `v-privilege` 指令，改用同一口径的 hasPermission 裁剪。 */
const canAdd = computed(() => hasPermission('scm:inventory:stocktake:add'));
const canUpdate = computed(() => hasPermission('scm:inventory:stocktake:update'));
const canDelete = computed(() => hasPermission('scm:inventory:stocktake:delete'));

/** 复制到新建对所有状态可用（它是「照这张单再盘一次」）；其余动作只在草稿态。 */
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
  // 记账单位：仅「复制到新建」时带入，供核对当前余额单位；普通新建为空。
  unit?: string;
  /**
   * 实盘量在表单里是 `number`（InputNumber 只接受数字），提交时才转成后端要求的
   * 4 位定点字符串。`null` = 还没清点；`0` = 确实一件不剩，两者是不同的结论。
   */
  actualQuantity?: number | null;
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

// ------------------------------------------------------------------ 复制历史盘点（纯前端，不落库）

/**
 * 把历史盘点的「仓库 + SKU 集合 + 当前余额记账单位」复制进未保存的新建表单。
 *
 * 刻意不复制历史账面量 / 历史实盘量 / 历史差异 / version：那些是另一时点的事实，
 * 冒充实盘会让确认阶段把陈旧数字当成本次清点结果。实盘量一律留空，由用户重新填写。
 * 本动作不创建任何 DRAFT —— 只有用户填完实盘量点「保存草稿」才走既有 create。
 * 任一 SKU 在当前仓库已无余额时显性报错、整单不复制，绝不悄悄丢弃行。
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
    // 读当前余额：只为取「当前记账单位」并校验 SKU 仍存在；账面量绝不作为实盘量填入。
    // 分页口径在 inventory-model 的 resolveStocktakeCopyUnits 里（后端 @Max(100) 会拒掉更大的
    // pageSize），因此这里不放宽通用查询上限来喂本页面。
    const {unitBySku} = await resolveStocktakeCopyUnits(sourceItems.map((i) => i.skuId), async (pageNum, pageSize) => {
      const bal = await inventoryBalanceApi.query({warehouseId: whId, pageNum, pageSize});
      return bal.data.list;
    });
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

// ------------------------------------------------------------------ Excel 快照导入

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

/** 导出快照模板绑定查询条件里的仓库；未选仓库时不导出，避免导错范围。 */
async function onDownloadTemplate() {
  if (queryForm.warehouseId === undefined || queryForm.warehouseId === null || queryForm.warehouseId === '') {
    message.warning('请先在上方查询条件选择仓库，再导出快照模板');
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

/**
 * customRequest 接管上传：走带 Idempotency-Key 的导入命令，而不是组件默认上传。
 * 后端整批校验：信封 code=0 但 totalErrors>0 表示「一行都没落库」，据此决定成功还是展示错误表。
 */
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

/**
 * 明细校验在提交前做：逐行给出「第几行缺什么」，比一条笼统的「参数不合法」有用得多。
 *
 * 非负与最多 4 位小数已由 InputNumber（`:min="0"` + `:precision="4"`）结构性保证，
 * 这里只需拦住「整行没清点」。实盘量填 0 是合法结论（确实一件不剩），不能与「没填」合并。
 */
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
    // 数量以定点字符串提交：后端拒绝 JSON 数字（ScmStrictDecimalStringDeserializer）
    items: items.map((i) => ({
      skuId: i.skuId as string | number,
      actualQuantity: fixed4(i.actualQuantity) as string,
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
