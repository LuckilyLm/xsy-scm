<template>
  <a-card size="small" :bordered="false">
    <a-form :model="queryForm" layout="inline" class="daily-filters" @finish="search">
      <a-form-item label="清单日期">
        <a-date-picker v-model:value="queryForm.reportDate" value-format="YYYY-MM-DD" :allow-clear="false" :disabled-date="disabledDate" />
      </a-form-item>
      <a-form-item label="仓库">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouseOptions" width="180px" />
      </a-form-item>
      <a-form-item label="商品">
        <a-input v-model:value="queryForm.keyword" placeholder="商品名称 / 商品规格" allow-clear :maxlength="100" />
      </a-form-item>
      <a-form-item>
        <a-space wrap>
          <a-button type="primary" :loading="loading" @click="search">查询</a-button>
          <a-button @click="reset">重置</a-button>
          <a-button :disabled="!report?.generatedAt || loading || !!error" @click="printReport">打印</a-button>
          <a-button v-privilege="PERM.EXPORT" :loading="exporting" :disabled="!report?.generatedAt || loading || !!error" @click="exportReport"
            >导出清单</a-button
          >
        </a-space>
      </a-form-item>
    </a-form>

    <p class="daily-description">
      清单每天定时生成，后续单据变化不会影响已生成的清单。
      <router-link v-privilege="'support:job:query'" to="/job/list">配置执行时间</router-link>
    </p>

    <a-alert v-if="error" type="error" show-icon :message="error" class="smart-margin-bottom10">
      <template #action><a-button @click="search">重试</a-button></template>
    </a-alert>
    <p v-if="report?.generatedAt" class="daily-meta">
      <strong>{{ report.reportDate }} 采购商品清单</strong>
      <span>生成时间：{{ datetime(report.generatedAt) }}</span>
      <span>符合条件的商品规格：{{ report.products.total }} 项</span>
    </p>
    <a-table
      size="small"
      bordered
      :columns="columns"
      :data-source="report?.products.list ?? []"
      :row-key="rowKey"
      :loading="loading"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
      :locale="{emptyText}"
    >
      <template #bodyCell="{column, record}">
        <template v-if="column.dataIndex === 'plannedQuantity'"
          ><span class="scm-quantity">{{ quantityText(record.plannedQuantity) }}</span></template
        >
        <template v-else-if="column.dataIndex === 'orderAmount'"
          ><span class="scm-money">{{ moneyText(record.orderAmount) }}</span></template
        >
      </template>
    </a-table>
    <div class="daily-pagination">
      <a-pagination
        v-model:current="pageNum"
        v-model:pageSize="pageSize"
        :total="report?.products.total ?? 0"
        :show-size-changer="true"
        :page-size-options="SCM_PAGE_SIZE_OPTIONS_WIDE"
        :disabled="loading"
        @change="changePage"
      />
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, onBeforeUnmount, onMounted, reactive, ref} from 'vue';
import dayjs from 'dayjs';
import type {Dayjs} from 'dayjs';
import type {TableColumnsType} from 'ant-design-vue';
import {message} from 'ant-design-vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import type {Warehouse} from '/@/views/business/scm/purchase/purchase-types';
import {purchaseDailyReportApi} from '/@/api/business/scm/purchase-daily-report-api';
import type {PurchaseDailyProduct, PurchaseDailyQuery, PurchaseDailyReport} from '/@/api/business/scm/purchase-daily-report-api';
import {SCM_REPORT_PERMISSION as PERM} from '/@/constants/business/scm/report-const';
import {quantityText, moneyText} from '../../inventory/inventory-model';
import {datetime} from '../../common/scm-display';
import {SCM_PAGE_SIZE_OPTIONS_WIDE} from '/@/constants/business/scm/pagination-const';

function todayInShanghai() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const value = (type: string) => parts.find((part) => part.type === type)?.value;
  return dayjs(`${value('year')}-${value('month')}-${value('day')}`);
}

const yesterday = () => todayInShanghai().subtract(1, 'day').format('YYYY-MM-DD');
const queryForm = reactive<{reportDate: string; warehouseId: number | string | undefined; keyword: string}>({
  reportDate: yesterday(),
  warehouseId: undefined,
  keyword: '',
});
const pageNum = ref(1);
const pageSize = ref(20);
const warehouseOptions = ref<Warehouse[]>([]);
const report = ref<PurchaseDailyReport>();
const loading = ref(false);
const exporting = ref(false);
const error = ref('');
let requestSequence = 0;
let appliedQuery: PurchaseDailyQuery | undefined;

const columns: TableColumnsType<PurchaseDailyProduct> = [
  {title: '商品名称', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'skuName', width: 160},
  {title: '采购单位', dataIndex: 'purchaseUnit', width: 76},
  {title: '采购单数', dataIndex: 'orderCount', width: 92},
  {title: '采购数量', dataIndex: 'plannedQuantity', width: 130},
  {title: '采购金额（元）', dataIndex: 'orderAmount', width: 140},
];
const emptyText = computed(() => {
  if (loading.value) return '正在读取清单';
  if (error.value) return '清单读取失败，请重试';
  return report.value?.generatedAt
    ? '清单已生成，当前授权范围和筛选条件下没有采购商品'
    : '该日期清单尚未生成或无访问范围，可联系管理员查看任务记录或补生成';
});
const rowKey = (row: PurchaseDailyProduct) => JSON.stringify([row.skuId, row.spuCode, row.productName, row.skuCode, row.skuName, row.purchaseUnit]);
const disabledDate = (date: Dayjs) => !date.isBefore(todayInShanghai(), 'day');

async function load(query: PurchaseDailyQuery) {
  const sequence = ++requestSequence;
  loading.value = true;
  error.value = '';
  report.value = undefined;
  try {
    const response = await purchaseDailyReportApi.query(query);
    if (sequence !== requestSequence) return;
    report.value = response.data;
    appliedQuery = {...query};
  } catch {
    if (sequence === requestSequence) error.value = '采购清单读取失败，请重试。';
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

function search() {
  if (!queryForm.reportDate) {
    message.warning('请选择清单日期');
    return;
  }
  pageNum.value = 1;
  void load({
    reportDate: queryForm.reportDate,
    warehouseId: queryForm.warehouseId,
    keyword: queryForm.keyword.trim() || undefined,
    pageNum: 1,
    pageSize: pageSize.value,
  });
}

function changePage(current: number, size: number) {
  if (!appliedQuery) return;
  pageNum.value = size === appliedQuery.pageSize ? current : 1;
  pageSize.value = size;
  void load({...appliedQuery, pageNum: pageNum.value, pageSize: size});
}

function reset() {
  queryForm.reportDate = yesterday();
  queryForm.warehouseId = undefined;
  queryForm.keyword = '';
  search();
}

async function exportReport() {
  if (!appliedQuery || !report.value?.generatedAt || exporting.value || loading.value) return;
  exporting.value = true;
  try {
    await purchaseDailyReportApi.export({...appliedQuery});
  } catch {
    message.error('采购清单导出失败，请重试。');
  } finally {
    exporting.value = false;
  }
}

function warehouseLabel(warehouseId?: number | string) {
  if (warehouseId === undefined || warehouseId === null) return '全部仓库';
  return warehouseOptions.value.find((item) => String(item.id) === String(warehouseId))?.name ?? `仓库 ${warehouseId}`;
}

/** 浏览器打印当前清单页：表头固定打印当前查询条件，明细使用当前页已加载的数据。 */
function printReport() {
  const applied = appliedQuery;
  const current = report.value;
  if (!applied || !current?.generatedAt) return;
  const rows = current.products.list ?? [];
  const total = current.products.total ?? 0;
  const frame = document.createElement('iframe');
  frame.style.cssText = 'position:fixed;width:0;height:0;border:0;';
  document.body.appendChild(frame);
  const doc = frame.contentDocument;
  if (!doc || !frame.contentWindow) {
    frame.remove();
    return;
  }
  doc.open();
  doc.write(
    '<!doctype html><html><head><meta charset="utf-8"><style>body{font:12px Arial,sans-serif;color:#222;padding:20px}h2{text-align:center;margin:0 0 8px}table{border-collapse:collapse;width:100%;margin-top:10px}td,th{border:1px solid #aaa;padding:5px;text-align:left}th{background:#f5f5f5}.r{text-align:right;font-variant-numeric:tabular-nums}.meta{color:#555}</style></head><body></body></html>'
  );
  doc.close();

  const title = doc.createElement('h2');
  title.textContent = `${applied.reportDate} 采购商品清单`;
  doc.body.appendChild(title);

  const pageCount = Math.max(1, Math.ceil(total / pageSize.value));
  const meta = doc.createElement('p');
  meta.className = 'meta';
  meta.textContent = [
    `生成时间：${datetime(current.generatedAt)}`,
    `仓库：${warehouseLabel(applied.warehouseId)}`,
    applied.keyword ? `商品：${applied.keyword}` : '',
    `本页 ${rows.length} 项，共 ${total} 项`,
    `第 ${pageNum.value} 页 / 共 ${pageCount} 页`,
  ]
    .filter(Boolean)
    .join('　');
  doc.body.appendChild(meta);

  const table = doc.createElement('table');
  const header = table.insertRow();
  columns.forEach((column) => {
    const th = doc.createElement('th');
    th.textContent = String(column.title ?? '');
    header.appendChild(th);
  });
  rows.forEach((row) => {
    const values = [
      row.productName,
      row.skuName,
      row.purchaseUnit,
      String(row.orderCount ?? ''),
      quantityText(row.plannedQuantity),
      moneyText(row.orderAmount),
    ];
    const tr = table.insertRow();
    values.forEach((value, index) => {
      const td = tr.insertCell();
      td.textContent = value;
      if (index >= 4) td.className = 'r';
    });
  });
  doc.body.appendChild(table);

  frame.contentWindow.focus();
  frame.contentWindow.print();
  window.setTimeout(() => frame.remove(), 1000);
}

onMounted(() => {
  // 仓库名称只用于打印表头；与清单查询并行，无权限时仍可查询和打印，按编号回退
  void (async () => {
    try {
      warehouseOptions.value = (await warehouseApi.list({suppressGlobalErrorMessage: true})).data ?? [];
    } catch {
      // 保留空选项，不影响清单本身
    }
  })();
  search();
});
onBeforeUnmount(() => {
  requestSequence += 1;
});
</script>

<style scoped>
.daily-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 0;
}
.daily-description {
  color: var(--scm-text-secondary, #666);
  margin: 16px 0;
}
.daily-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  margin: 16px 0;
}
.daily-pagination {
  display: flex;
  justify-content: flex-end;
  overflow-x: auto;
  margin-top: 16px;
}
@media (max-width: 600px) {
  .daily-filters :deep(.ant-form-item) {
    width: 100%;
    margin-inline-end: 0;
  }
  .daily-meta {
    flex-direction: column;
  }
  .daily-pagination {
    justify-content: flex-start;
  }
}
</style>
