<!-- 库存预警列表只读，按可用量（现有量 − 预留量）判断。写操作只有“检查并发送预警通知”。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="200px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select v-model:value="queryForm.status" :options="statusOptions" style="width: 150px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:warning:query'">查询</a-button>
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
    <a-alert v-if="anchorThresholdId" class="anchor" type="info" show-icon :message="'已定位到相关的库存预警配置'" />
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="'scm:inventory:warning:scan'" :loading="scanning" @click="scan"> 检查并发送预警通知 </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_WARNING" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      :id="SCM_INVENTORY_TABLE_ID.WARNING"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="thresholdId"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{emptyText: '没有需要处理的库存预警'}"
      :scroll="{x: scrollX}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'warehouseName'">{{ record.warehouseName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'skuName'">{{ skuMainText(record.specValues, record.skuName) }}</template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'reservedQuantity'">
          <span class="scm-quantity">{{ quantityText(record.reservedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'availableQuantity'">
          <!-- 判定依据，加粗显示 -->
          <span class="scm-quantity available">{{ quantityText(record.availableQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'warnMin'">
          <span class="scm-quantity">{{ quantityText(record.warnMin) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'warnMax'">
          <span class="scm-quantity">{{ quantityText(record.warnMax) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="statusTone(record.status)" :label="record.statusDesc || record.status" />
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
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {message} from 'ant-design-vue';
import {useRoute} from 'vue-router';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {inventoryWarningApi} from '/@/api/business/scm/inventory-warning-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {deepLinkId} from '/@/lib/query-deep-link';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_INVENTORY_TABLE_ID, SCM_INVENTORY_WARNING_STATUS_ENUM} from '/@/constants/business/scm/inventory-const';
import type {Id, InventoryWarning, InventoryWarningQuery} from './inventory-types';
import type {Warehouse} from '../purchase/purchase-types';
import {quantityText, singleWarehouseDefault, skuMainText} from './inventory-model';
import {inventoryError} from './inventory-errors';
import {scmColumnsWidth, type ScmListColumn} from '../common/scm-column';

const queryForm = reactive<InventoryWarningQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<InventoryWarning[]>([]);
const total = ref(0);
const loading = ref(false);
const scanning = ref(false);
const error = ref('');
const warehouses = ref<Warehouse[]>([]);
/** 从站内信进入时的阈值配置 ID。 */
const anchorThresholdId = ref<Id | undefined>(undefined);
let requestId = 0;

const route = useRoute();
const warningRouteName = route.name;

/** 第一项是“仅异常”；后端 status 为空时也只返回异常项。 */
const statusOptions = [
  {value: undefined, label: '仅异常'},
  ...Object.values(SCM_INVENTORY_WARNING_STATUS_ENUM).map((i) => ({
    value: i.value,
    label: i.desc,
  })),
];

// 三个数量都保留，便于解释预警为什么触发；编码属于内部标识，不上列表。
type InventoryWarningColumn = ScmListColumn;
const columns = ref<InventoryWarningColumn[]>([
  {title: '仓库', dataIndex: 'warehouseName', width: 150},
  {title: '商品', dataIndex: 'productName', width: 150},
  {title: '商品规格', dataIndex: 'skuName', width: 150},
  {title: '单位', dataIndex: 'unit', width: 80},
  {title: '现有量', dataIndex: 'quantity', width: 100},
  {title: '已预留', dataIndex: 'reservedQuantity', width: 100},
  {title: '可用量', dataIndex: 'availableQuantity', width: 110},
  {title: '预警下限', dataIndex: 'warnMin', width: 100},
  {title: '预警上限', dataIndex: 'warnMax', width: 100},
  {title: '状态', dataIndex: 'status', align: 'center', width: 96},
]);
const scrollX = computed(() => scmColumnsWidth(columns.value));

/** 低于下限是断货风险（红），高于上限是积压（橙），正常不强调。 */
const STATUS_TONE: Record<string, ScmStatusTone> = {
  LOW: 'error',
  HIGH: 'warning',
  NORMAL: 'neutral',
};
const statusTone = (status?: string | null): ScmStatusTone => STATUS_TONE[status ?? ''] ?? 'neutral';

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头 Alert 承担，不再让全局 toast 重复说一遍
    const r = await inventoryWarningApi.query({...queryForm}, {suppressGlobalErrorMessage: true});
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

/** 检查阈值并发送通知。只扫描有权限的仓库；同一次预警不会重复发送。 */
async function scan() {
  scanning.value = true;
  error.value = '';
  try {
    const r = await inventoryWarningApi.scan();
    const {scannedCount, sentCount} = r.data;
    if (sentCount > 0) {
      message.success(`已检查 ${scannedCount} 条阈值配置，投递 ${sentCount} 条预警通知`);
    } else {
      message.info(`已检查 ${scannedCount} 条阈值配置，没有新的预警跃迁`);
    }
    await queryData();
  } catch {
    // 扫描失败只走全局 toast
  } finally {
    scanning.value = false;
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.warehouseId = undefined;
  queryForm.thresholdId = undefined;
  // 重置回“仅异常”
  queryForm.status = undefined;
  anchorThresholdId.value = undefined;
  onSearch();
}

/** 进入页面时按站内信的 thresholdId 定位；普通菜单进入不筛选。 */
async function enterPage() {
  const anchor = deepLinkId(route.query, 'thresholdId');
  anchorThresholdId.value = anchor;
  queryForm.thresholdId = anchor;
  queryForm.status = undefined;
  queryForm.pageNum = 1;
  // 有锚点时不再默认选中单仓，否则会把锚点所在的仓筛掉
  if (anchor === undefined) {
    queryForm.warehouseId = undefined;
    await applySingleWarehouseDefault();
  }
  await queryData();
}

watch(
  [() => route.name, () => route.query],
  ([name]) => {
    // 组件被缓存时，跳往其他页面不应触发本页查询；回到本页才重新套用来源链接。
    if (name !== warningRouteName) return;
    void enterPage();
  },
  {immediate: true}
);
</script>

<style scoped>
/* 可用量是判定基准，加粗以区别于现有量 */
.available {
  font-weight: 600;
}

.anchor {
  margin-bottom: 12px;
}
</style>
