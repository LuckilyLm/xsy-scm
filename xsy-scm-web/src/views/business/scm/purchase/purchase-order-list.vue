<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/purchase/purchase-list.vue
复制日期：2026-09-16。Copy First + Adapt。
剪枝：`resizable`/`@resizeColumn`/`handleResizeColumn`（A1）、`actualAmount` 列（A12）、
      `accept` 接单动作、C 的 `TABLE_ID_CONST.BUSINESS.PURCHASE.*`（A28）、C 的行内抽屉表单（拆为独立组件）。
适配：6 值字符串状态枚举（A2）、`/scm/purchase/**`（A6）、`version`（A8）、
      供应商/仓库/员工选择器（A9/A10/A11）、submit/cancel/short-close/delete（A13/A14）、
      右对齐 + 等宽（A17）、`null` → `—`（A18）、`scm:purchase:*`（A22）、
      `scm-purchase-order-table`（A23）、`purchase-errors`（A24）、
      loading/empty/error/retry/409 重载（A27）、`v-privilege`（A30）。
验收：W5 单测、TS 棘轮与 Playwright。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="采购单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.orderNo" placeholder="采购单号" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="供应商" class="smart-query-form-item">
        <SupplierSelect v-model:value="queryForm.supplierId" width="200px"/>
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_PURCHASE_STATUS_ENUM" v-model:value="queryForm.status" width="150px"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:purchase:query'">查询</a-button>
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
        <a-button type="primary" v-privilege="'scm:purchase:add'" @click="drawer?.open()">新建采购单</a-button>
        <a-button danger v-privilege="'scm:purchase:delete'" :disabled="!draftSelected.length" @click="batchDelete">
          批量删除草稿
        </a-button>
        <a-button danger v-privilege="'scm:purchase:short-close'" :disabled="!partialSelected.length" @click="batchShortClose">
          批量少收关单
        </a-button>
        <a-button :disabled="!selected.length" @click="batchPrint">批量打印</a-button>
        <a-button v-privilege="'scm:purchase:query'" @click="onExport">导出</a-button>
        <a-button v-privilege="'scm:purchase:query'" @click="exportSettingsOpen = true">导出设置</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PURCHASE_ORDER" :refresh="queryData"/>
      </div>
    </a-row>

    <a-table
        :id="SCM_PURCHASE_TABLE_ID.ORDER"
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1500 }"
        :row-selection="{
        selectedRowKeys: selected,
        onChange: (keys: (string | number)[]) => (selected = keys),
        getCheckboxProps: (row: Order) => ({ disabled: !['DRAFT', 'PARTIALLY_RECEIVED'].includes(row.status ?? '') }),
      }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'orderNo'">
          <a @click="detail?.open(record.id)">{{ record.orderNo }}</a>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag>{{ SCM_PURCHASE_STATUS_ENUM[record.status]?.desc }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'totalAmount'">
          <span class="num">{{ amount(record.totalAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'receivedProgress'">
          <span class="num">{{ progress(record.receivedProgress) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'createdAt'">
          <span class="num">{{ datetime(record.createdAt) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button type="link" @click="detail?.open(record.id)">详情</a-button>
            <a-button type="link" @click="printRow(record)">打印</a-button>
            <a-button v-if="record.status === 'DRAFT'" type="link" v-privilege="'scm:purchase:update'"
                      @click="drawer?.open(record.id)">
              编辑
            </a-button>
            <a-button v-if="record.status === 'DRAFT'" type="link" v-privilege="'scm:purchase:submit'"
                      @click="submit(record)">
              提交
            </a-button>
            <a-button
                v-if="['DRAFT', 'SUBMITTED'].includes(record.status)"
                type="link"
                v-privilege="'scm:purchase:cancel'"
                @click="cancel(record)"
            >
              取消
            </a-button>
            <a-button
                v-if="record.status === 'PARTIALLY_RECEIVED'"
                type="link"
                v-privilege="'scm:purchase:short-close'"
                @click="shortClose(record)"
            >
              少收关单
            </a-button>
            <a-button v-if="record.status === 'DRAFT'" danger type="link" v-privilege="'scm:purchase:delete'"
                      @click="remove(record)">
              删除
            </a-button>
          </div>
        </template>
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

  <PurchaseOrderForm ref="drawer" @saved="queryData"/>
  <PurchaseOrderDetail ref="detail" @saved="queryData"/>

  <a-modal v-model:open="exportSettingsOpen" title="导出列设置" width="520px" @ok="saveExportSettings">
    <p class="export-hint">勾选需要导出的列；设置按当前登录用户本地记忆，刷新后仍保留。不勾选任何列时后端导出全部目录。</p>
    <a-checkbox-group v-model:value="exportColumns">
      <a-row>
        <a-col v-for="col in SCM_PURCHASE_EXPORT_COLUMNS" :key="col.key" :span="12">
          <a-checkbox :value="col.key">{{ col.title }}</a-checkbox>
        </a-col>
      </a-row>
    </a-checkbox-group>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, h, onMounted, reactive, ref} from 'vue';
import {Input, message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {purchaseOrderApi} from '/@/api/business/scm/purchase-order-api';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {useUserStore} from '/@/store/modules/system/user';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {
  SCM_PURCHASE_EXPORT_COLUMNS,
  SCM_PURCHASE_STATUS_ENUM,
  SCM_PURCHASE_TABLE_ID,
} from '/@/constants/business/scm/purchase-const';
import type {Order, OrderQuery} from './purchase-types';
import {amount, progress} from './purchase-form-model';
import {datetime} from '../common/scm-display';
import {purchaseError} from './purchase-errors';
import {printPurchaseOrders} from './purchase-order-print';
import PurchaseOrderForm from './components/purchase-order-form-drawer.vue';
import PurchaseOrderDetail from './components/purchase-order-detail-drawer.vue';

const queryForm = reactive<OrderQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<Order[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const selected = ref<(string | number)[]>([]);
const drawer = ref<InstanceType<typeof PurchaseOrderForm>>();
const detail = ref<InstanceType<typeof PurchaseOrderDetail>>();

const userStore = useUserStore();
/** 导出列偏好按「登录用户 + 场景」本地记忆（Wave 2B §6.5），不建后端表；缺失 / 损坏回落到整目录。 */
const EXPORT_SCENE = 'purchase-order-list';
const ALL_EXPORT_KEYS = SCM_PURCHASE_EXPORT_COLUMNS.map((c) => c.key);
function exportSettingKey() {
  return `xsy-scm:export-columns:${userStore.employeeId}:${EXPORT_SCENE}`;
}
function loadExportColumns(): string[] {
  try {
    const raw = localStorage.getItem(exportSettingKey());
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        const valid = new Set(ALL_EXPORT_KEYS);
        const kept = parsed.filter((k: string) => valid.has(k));
        if (kept.length) {
          return kept;
        }
      }
    }
  } catch {
    // 本地偏好损坏时静默回落整目录——偏好只是便利，坏了不能挡住导出。
  }
  return ALL_EXPORT_KEYS;
}
const exportSettingsOpen = ref(false);
const exportColumns = ref<string[]>(loadExportColumns());
/** 竞态保护：慢的旧请求不得覆盖新结果。 */
let requestId = 0;

/** 行勾选同时服务两类批量：删除只取草稿、少收关单只取部分收货，各自按状态取子集。 */
const selectedOrders = computed(() => tableData.value.filter((o) => selected.value.includes(o.id!)));
const draftSelected = computed(() => selectedOrders.value.filter((o) => o.status === 'DRAFT'));
const partialSelected = computed(() => selectedOrders.value.filter((o) => o.status === 'PARTIALLY_RECEIVED'));

const columns = ref<TableColumnsType<Order>>([
  {title: '采购单号', dataIndex: 'orderNo', width: 210},
  {title: '供应商', dataIndex: 'supplierName', width: 180},
  {title: '采购员', dataIndex: 'purchaserName', width: 120},
  {title: '收货仓库', dataIndex: 'warehouseName', width: 140},
  {title: '状态', dataIndex: 'status', align: 'center', width: 110},
  {title: '计划到货', dataIndex: 'plannedArrivalDate', width: 120},
  {title: '采购金额', dataIndex: 'totalAmount', align: 'right', width: 140},
  {title: '收货进度', dataIndex: 'receivedProgress', align: 'right', width: 110},
  {title: '创建时间', dataIndex: 'createdAt', width: 180},
  {title: '操作', dataIndex: 'action', align: 'right', fixed: 'right', width: 360},
]);

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await purchaseOrderApi.query(queryForm);
    if (id === requestId) {
      tableData.value = r.data.list;
      total.value = r.data.total;
      selected.value = [];
    }
  } catch (e) {
    if (id === requestId) {
      error.value = purchaseError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.orderNo = undefined;
  queryForm.supplierId = undefined;
  queryForm.status = undefined;
  onSearch();
}

/** 状态类命令：成功后重载，失败把错误码翻成中文（A24）并把错误留给用户重试。 */
function submit(row: Order) {
  Modal.confirm({
    title: '提交这张采购单？提交后不可再改行与分配。',
    onOk: async () => {
      try {
        await purchaseOrderApi.submit({id: row.id!, version: row.version!});
        await queryData();
      } catch (e) {
        error.value = purchaseError(e);
        throw e;
      }
    },
  });
}

function cancel(row: Order) {
  let reason = '';
  Modal.confirm({
    title: '取消这张采购单？已占用的采购需求会被释放。',
    content: () =>
        h('div', [
          h('p', '取消原因（必填）：'),
          h(Input.TextArea, {
            rows: 3,
            maxlength: 500,
            'onUpdate:value': (v: string) => (reason = v),
          }),
        ]),
    onOk: async () => {
      if (!reason.trim()) {
        error.value = '请填写取消原因';
        throw new Error('cancel reason required');
      }
      try {
        await purchaseOrderApi.cancel({id: row.id!, version: row.version!, cancelReason: reason});
        await queryData();
      } catch (e) {
        error.value = purchaseError(e);
        throw e;
      }
    },
  });
}

function shortClose(row: Order) {
  let reason = '';
  Modal.confirm({
    title: '少收关单？剩余未收数量将不再补收，采购单进入终态。',
    content: () =>
        h('div', [
          h('p', '少收原因（必填）：'),
          h(Input.TextArea, {
            rows: 3,
            maxlength: 500,
            'onUpdate:value': (v: string) => (reason = v),
          }),
        ]),
    onOk: async () => {
      if (!reason.trim()) {
        error.value = '请填写少收关单原因';
        throw new Error('short close reason required');
      }
      try {
        await purchaseOrderApi.shortClose({id: row.id!, version: row.version!, shortCloseReason: reason});
        await queryData();
      } catch (e) {
        error.value = purchaseError(e);
        throw e;
      }
    },
  });
}

function remove(row: Order) {
  Modal.confirm({
    title: '删除这张草稿采购单？',
    okType: 'danger',
    onOk: async () => {
      try {
        await purchaseOrderApi.delete({id: row.id!, version: row.version!});
        await queryData();
      } catch (e) {
        error.value = purchaseError(e);
        throw e;
      }
    },
  });
}

function batchDelete() {
  const targets = draftSelected.value;
  if (!targets.length) {
    error.value = '请勾选至少一张「草稿」采购单';
    return;
  }
  Modal.confirm({
    title: `删除所选 ${targets.length} 张草稿采购单？`,
    okType: 'danger',
    onOk: async () => {
      try {
        await purchaseOrderApi.batchDelete(targets.map((o) => ({id: o.id!, version: o.version!})));
        await queryData();
      } catch (e) {
        error.value = purchaseError(e);
        throw e;
      }
    },
  });
}

/** 批量少收关单（Wave 2B §6.3）：只作用于「部分收货」勾选，整批共享原因、后端同事务逐单校验。 */
function batchShortClose() {
  const targets = partialSelected.value;
  if (!targets.length) {
    error.value = '请勾选至少一张「部分收货」的采购单';
    return;
  }
  let reason = '';
  Modal.confirm({
    title: `对 ${targets.length} 张「部分收货」采购单批量少收关单？`,
    content: () =>
        h('div', [
          h('p', '整批共享一个原因，在同一事务内逐单校验；任一单状态非法或被他人改过版本，整批都会失败回滚。'),
          h('p', '少收关单原因（必填）：'),
          h(Input.TextArea, {
            rows: 3,
            maxlength: 500,
            'onUpdate:value': (v: string) => (reason = v),
          }),
        ]),
    okType: 'danger',
    onOk: async () => {
      if (!reason.trim()) {
        error.value = '请填写少收关单原因';
        throw new Error('batch short close reason required');
      }
      try {
        await purchaseOrderApi.batchShortClose({
          orders: targets.map((o) => ({id: o.id!, version: o.version!})),
          shortCloseReason: reason,
        });
        await queryData();
      } catch (e) {
        error.value = purchaseError(e);
        throw e;
      }
    },
  });
}

/** 导出走**当前筛选**（后端强制第 1 页 + 上限行，分页参数会被覆盖），只回传勾选列 key。 */
function onExport() {
  purchaseOrderApi.export({...queryForm, exportColumns: exportColumns.value});
}

function saveExportSettings() {
  localStorage.setItem(exportSettingKey(), JSON.stringify(exportColumns.value));
  exportSettingsOpen.value = false;
  message.success('导出设置已保存');
}

/** 单张打印：拉详情后交隐藏 iframe 渲染，纯读取、不改采购状态（§6.8）。 */
function printRow(row: Order) {
  purchaseOrderApi
      .detail(row.id!)
      .then((r) => printPurchaseOrders([r.data]))
      .catch((e) => (error.value = purchaseError(e)));
}

async function batchPrint() {
  const rows = selectedOrders.value;
  if (!rows.length) {
    error.value = '请先勾选要打印的采购单';
    return;
  }
  const orders: Order[] = [];
  for (const row of rows) {
    try {
      orders.push((await purchaseOrderApi.detail(row.id!)).data);
    } catch (e) {
      error.value = purchaseError(e);
      return;
    }
  }
  printPurchaseOrders(orders);
}

onMounted(queryData);
</script>

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.export-hint {
  color: var(--ant-color-text-secondary);
  font-size: 12px;
  margin-bottom: 12px;
}
</style>
