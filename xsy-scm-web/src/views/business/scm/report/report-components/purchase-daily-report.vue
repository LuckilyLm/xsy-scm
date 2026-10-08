<template>
  <a-card size="small" :bordered="false">
    <a-form :model="queryForm" layout="inline" class="daily-filters" @finish="search">
      <a-form-item label="清单日期">
        <a-date-picker v-model:value="queryForm.reportDate" value-format="YYYY-MM-DD" :allow-clear="false"
                       :disabled-date="disabledDate"/>
      </a-form-item>
      <a-form-item label="仓库">
        <WarehouseSelect v-model:value="queryForm.warehouseId" width="180px"/>
      </a-form-item>
      <a-form-item label="商品">
        <a-input v-model:value="queryForm.keyword" placeholder="商品名称 / 商品编码 / 商品规格编码" allow-clear :maxlength="100"/>
      </a-form-item>
      <a-form-item>
        <a-space wrap>
          <a-button type="primary" :loading="loading" @click="search">查询</a-button>
          <a-button @click="reset">重置</a-button>
          <a-button v-privilege="PERM.EXPORT" :loading="exporting"
                    :disabled="!report?.generatedAt || loading || !!error" @click="exportReport">导出清单</a-button>
        </a-space>
      </a-form-item>
    </a-form>

    <p class="daily-description">
      每日生成，保留生成时的快照。
      <router-link v-privilege="'support:job:query'" to="/job/list">配置执行时间</router-link>
    </p>

    <p v-if="report?.generatedAt" class="daily-meta">
      <strong>{{ report.reportDate }} 采购商品清单</strong>
      <span>生成时间：{{ datetime(report.generatedAt) }}</span>
      <span>当前范围 {{ report.products.total }} 项</span>
    </p>
    <a-table size="small" bordered :columns="columns" :data-source="report?.products.list ?? []"
             :row-key="rowKey" :loading="loading" :pagination="false" :scroll="{ x: 800 }"
             :locale="{ emptyText }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'plannedQuantity'"><span class="scm-quantity">{{ quantityText(record.plannedQuantity) }}</span></template>
        <template v-else-if="column.dataIndex === 'orderAmount'"><span class="scm-money">{{ moneyText(record.orderAmount) }}</span></template>
      </template>
    </a-table>
    <div class="daily-pagination">
      <a-pagination v-model:current="pageNum" v-model:pageSize="pageSize" :total="report?.products.total ?? 0"
                    :show-size-changer="true" :page-size-options="['10', '20', '50', '100']"
                    :disabled="loading" @change="changePage"/>
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, reactive, ref} from 'vue';
import dayjs from 'dayjs';
import type {Dayjs} from 'dayjs';
import type {TableColumnsType} from 'ant-design-vue';
import {message} from 'ant-design-vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {purchaseDailyReportApi} from '/@/api/business/scm/purchase-daily-report-api';
import type {PurchaseDailyProduct, PurchaseDailyQuery, PurchaseDailyReport} from '/@/api/business/scm/purchase-daily-report-api';
import {SCM_REPORT_PERMISSION as PERM} from '/@/constants/business/scm/report-const';
import {quantityText, moneyText} from '../../inventory/inventory-model';
import {datetime} from '../../common/scm-display';
import {useScmErrorToast} from '../../common/scm-error-toast';

function todayInShanghai() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date());
  const value = (type: string) => parts.find(part => part.type === type)?.value;
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
const report = ref<PurchaseDailyReport>();
const loading = ref(false);
const exporting = ref(false);
const error = useScmErrorToast();
let requestSequence = 0;
let appliedQuery: PurchaseDailyQuery | undefined;

const columns: TableColumnsType<PurchaseDailyProduct> = [
  {title: '商品名称', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'skuName', width: 160},
  {title: '采购单位', dataIndex: 'purchaseUnit', width: 90},
  {title: '采购单数', dataIndex: 'orderCount', width: 100, align: 'right'},
  {title: '采购数量', dataIndex: 'plannedQuantity', width: 130, align: 'right'},
  {title: '采购金额（元）', dataIndex: 'orderAmount', width: 140, align: 'right'},
];
const emptyText = computed(() => {
  if (loading.value) return '正在读取清单';
  if (error.value) return '清单读取失败，请重试';
  return report.value?.generatedAt
    ? '清单已生成，当前授权范围和筛选条件下没有采购商品'
    : '该日期清单尚未生成或无访问范围，可联系管理员查看任务记录或补生成';
});
const rowKey = (row: PurchaseDailyProduct) => JSON.stringify([
  row.skuId, row.spuCode, row.productName, row.skuCode, row.skuName, row.purchaseUnit,
]);
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
  void load({reportDate: queryForm.reportDate, warehouseId: queryForm.warehouseId,
    keyword: queryForm.keyword.trim() || undefined, pageNum: 1, pageSize: pageSize.value});
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

onMounted(search);
onBeforeUnmount(() => { requestSequence += 1; });
</script>

<style scoped>
.daily-filters { display: flex; flex-wrap: wrap; gap: 12px 0; }
.daily-description { color: var(--scm-text-secondary, #666); margin: 16px 0; }
.daily-meta { display: flex; flex-wrap: wrap; gap: 8px 24px; margin: 16px 0; }
.daily-pagination { display: flex; justify-content: flex-end; overflow-x: auto; margin-top: 16px; }
@media (max-width: 600px) {
  .daily-filters :deep(.ant-form-item) { width: 100%; margin-inline-end: 0; }
  .daily-meta { flex-direction: column; }
  .daily-pagination { justify-content: flex-start; }
}
</style>
