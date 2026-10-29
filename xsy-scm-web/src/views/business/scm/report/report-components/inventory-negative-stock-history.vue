<template>
  <a-modal v-model:open="visible" title="负库存月度留存" :width="1160" :footer="null" @cancel="listRequestId++">
    <a-form class="smart-query-form" layout="inline" :model="filters" @submit.prevent="search">
      <a-row class="smart-query-form-row">
        <a-form-item label="月份"
          ><a-date-picker v-model:value="filters.month" picker="month" value-format="YYYY-MM" :allow-clear="false"
        /></a-form-item>
        <a-form-item label="仓库"><WarehouseSelect v-model:value="filters.warehouseId" width="180px" /></a-form-item>
        <a-form-item
          ><a-space
            ><a-button type="primary" :loading="loading" @click="search">查询</a-button
            ><a-button v-privilege="PERM.INVENTORY_SNAPSHOT_CREATE" @click="openCreate">留存当前数据</a-button></a-space
          ></a-form-item
        >
      </a-row>
    </a-form>
    <p class="scm-note">留存保留实际取数时间，同月可多次保存。历史月份只查看已保存记录，不能用当前库存补录历史月末数据。</p>
    <a-alert v-if="error" type="error" show-icon :message="error"
      ><template #action><a-button @click="load">重试</a-button></template></a-alert
    >
    <a-table
      :columns="columns"
      :data-source="rows"
      row-key="id"
      size="small"
      :loading="loading"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{column, record}">
        <template v-if="column.dataIndex === 'capturedAt'">{{ datetime(record.capturedAt) }}</template>
        <span v-else-if="column.dataIndex === 'estimatedAmount'" class="scm-money">{{ moneyText(record.estimatedAmount) }}</span>
        <template v-else-if="column.dataIndex === 'action'"
          ><a-space :size="4"
            ><a-button type="link" size="small" @click="showDetail(record)">查看</a-button
            ><a-button v-privilege="PERM.EXPORT" type="link" size="small" :disabled="exporting" @click="exportSnapshot(record)"
              >导出</a-button
            ></a-space
          ></template
        >
      </template>
    </a-table>
    <div class="smart-query-table-page"><a-pagination v-model:current="pageNum" :page-size="20" :total="total" @change="load" /></div>
  </a-modal>
  <a-modal
    v-model:open="createOpen"
    title="留存当前负库存"
    :confirm-loading="creating"
    :closable="!creating"
    :mask-closable="!creating"
    :cancel-button-props="{disabled: creating}"
    @ok="create"
  >
    <a-form ref="createFormRef" :model="createForm" layout="vertical" :disabled="creating">
      <a-form-item name="warehouseId" label="仓库" :rules="[{required: true, message: '请选择留存仓库'}]"
        ><WarehouseSelect v-model:value="createForm.warehouseId" :disabled="creating"
      /></a-form-item>
      <a-form-item name="remark" label="备注"><a-textarea v-model:value="createForm.remark" :maxlength="500" :rows="3" /></a-form-item>
    </a-form>
    <p class="scm-note">保存该仓库当前完整负库存清单，不生成报损或库存调整。之后补录采购不会改变本次留存。</p>
  </a-modal>
  <a-modal v-model:open="detailOpen" :title="selected?.snapshotNo || '留存明细'" :width="1120" :footer="null" @cancel="detailRequestId++">
    <a-alert v-if="detailError" type="error" show-icon :message="detailError" />
    <a-spin :spinning="detailLoading">
      <template v-if="selected">
        <p class="scm-note">{{ selected.warehouseName }} · 留存时间 {{ datetime(selected.capturedAt) }} · {{ selected.remark || '月度核对' }}</p>
        <a-alert
          v-if="selected.zeroCostRowCount > 0"
          type="warning"
          show-icon
          :message="'有 ' + selected.zeroCostRowCount + ' 行成本待确认，估算金额不代表完整损耗。'"
        />
        <a-table
          :columns="detailColumns"
          :data-source="selected.rows || []"
          row-key="balanceId"
          size="small"
          :pagination="{pageSize: 20}"
          :scroll="{x: scmColumnsWidth(detailColumns)}"
        >
          <template #bodyCell="{column, record}">
            <span v-if="column.dataIndex === 'quantity'" class="scm-quantity">{{ quantityText(record.quantity) }}</span>
            <span v-else-if="['avgCost', 'estimatedAmount'].includes(column.dataIndex)" class="scm-money">{{
              moneyText(record[column.dataIndex])
            }}</span>
          </template>
        </a-table>
      </template>
    </a-spin>
  </a-modal>
</template>
<script setup lang="ts">
import {onBeforeUnmount, reactive, ref} from 'vue';
import dayjs from 'dayjs';
import {message, type FormInstance, type TableColumnsType} from 'ant-design-vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {negativeStockSnapshotApi, type NegativeStockSnapshot} from '/@/api/business/scm/negative-stock-snapshot-api';
import {SCM_REPORT_PERMISSION as PERM} from '/@/constants/business/scm/report-const';
import type {ReportId, InventoryNegativeStockRow} from '../report-types';
import {scmColumnsWidth} from '../../common/scm-column';
import {datetime} from '../../common/scm-display';
import {moneyText} from '../../finance/finance-form-model';
import {quantityText} from '../../inventory/inventory-model';
import {reportError} from '../report-errors';

const visible = ref(false),
  loading = ref(false),
  error = ref(''),
  exporting = ref(false);
const filters = reactive({month: dayjs().format('YYYY-MM'), warehouseId: undefined as ReportId | undefined});
const rows = ref<NegativeStockSnapshot[]>([]),
  pageNum = ref(1),
  total = ref(0);
let listRequestId = 0,
  detailRequestId = 0;
const columns: TableColumnsType<NegativeStockSnapshot> = [
  {title: '留存编号', dataIndex: 'snapshotNo', width: 190},
  {title: '仓库', dataIndex: 'warehouseName', width: 160},
  {title: '留存时间', dataIndex: 'capturedAt', width: 180},
  {title: '规格数', dataIndex: 'rowCount', width: 90, align: 'right'},
  {title: '待确认成本数', dataIndex: 'zeroCostRowCount', width: 110, align: 'right'},
  {title: '估算金额', dataIndex: 'estimatedAmount', width: 148, align: 'right'},
  {title: '操作', dataIndex: 'action', width: 120, align: 'center', fixed: 'right'},
];
const detailColumns: TableColumnsType<InventoryNegativeStockRow> = [
  {title: '商品名称', dataIndex: 'productName', width: 200},
  {title: '规格', dataIndex: 'specName', width: 160},
  {title: '单位', dataIndex: 'unit', width: 76, align: 'center'},
  {title: '负数量', dataIndex: 'quantity', width: 132, align: 'right'},
  {title: '余额均价', dataIndex: 'avgCost', width: 144, align: 'right'},
  {title: '估算金额', dataIndex: 'estimatedAmount', width: 148, align: 'right'},
];
function open() {
  visible.value = true;
  search();
}
function search() {
  pageNum.value = 1;
  void load();
}
async function load() {
  const id = ++listRequestId;
  loading.value = true;
  error.value = '';
  try {
    const result = await negativeStockSnapshotApi.query(
      {month: filters.month + '-01', warehouseId: filters.warehouseId, pageNum: pageNum.value, pageSize: 20},
      {suppressGlobalErrorMessage: true}
    );
    if (id !== listRequestId) return;
    rows.value = result.data.list;
    total.value = result.data.total;
  } catch (e) {
    if (id === listRequestId) {
      error.value = reportError(e);
      rows.value = [];
      total.value = 0;
    }
  } finally {
    if (id === listRequestId) loading.value = false;
  }
}
const createOpen = ref(false),
  creating = ref(false),
  createFormRef = ref<FormInstance>();
const createForm = reactive({warehouseId: undefined as ReportId | undefined, remark: ''});
function openCreate() {
  createForm.warehouseId = filters.warehouseId;
  createForm.remark = '';
  createFormRef.value?.clearValidate();
  createOpen.value = true;
}
async function create() {
  if (creating.value) return;
  try {
    await createFormRef.value?.validate();
  } catch {
    return;
  }
  if (createForm.warehouseId == null) return;
  creating.value = true;
  try {
    const result = await negativeStockSnapshotApi.create({warehouseId: createForm.warehouseId, remark: createForm.remark.trim()});
    message.success('当前负库存已留存');
    createOpen.value = false;
    filters.month = result.data.snapshotMonth.slice(0, 7);
    filters.warehouseId = result.data.warehouseId;
    detailRequestId++;
    detailLoading.value = false;
    selected.value = result.data;
    detailOpen.value = true;
    detailError.value = '';
    search();
  } catch {
    /* 全局提示 */
  } finally {
    creating.value = false;
  }
}
const detailOpen = ref(false),
  detailLoading = ref(false),
  detailError = ref(''),
  selected = ref<NegativeStockSnapshot>();
async function showDetail(row: NegativeStockSnapshot) {
  const id = ++detailRequestId;
  detailOpen.value = true;
  detailLoading.value = true;
  detailError.value = '';
  selected.value = undefined;
  try {
    const result = await negativeStockSnapshotApi.detail(row.id, {suppressGlobalErrorMessage: true});
    if (id === detailRequestId) selected.value = result.data;
  } catch (e) {
    if (id === detailRequestId) detailError.value = reportError(e);
  } finally {
    if (id === detailRequestId) detailLoading.value = false;
  }
}
async function exportSnapshot(row: NegativeStockSnapshot) {
  if (exporting.value) return;
  exporting.value = true;
  try {
    await negativeStockSnapshotApi.export(row.id);
  } catch {
    /* 全局提示 */
  } finally {
    exporting.value = false;
  }
}
onBeforeUnmount(() => {
  listRequestId++;
  detailRequestId++;
});
defineExpose({open});
</script>
