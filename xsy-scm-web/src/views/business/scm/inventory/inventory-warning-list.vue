<!--
  库存预警列表。

  列表本身只读：预警不是可以「标记已读」的状态，它只是 (阈值, 可用量) 的当前计算结果。
  引入「已读 / 已忽略」会让预警与真实库存脱钩。唯一的写动作是「检查并发送预警通知」，
  它不改变任何预警，只把当前已经发生的阈值跃迁投递成站内信，单独授权。

  判定基准是可用量（现有量 − 预留量），不是现有量 —— 货已被订走就不算有货，
  因此三个数量都展示出来，用户能自己看懂预警为什么触发。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="200px"/>
      </a-form-item>
      <a-form-item label="商品规格编码" class="smart-query-form-item">
        <a-input v-model:value="queryForm.skuCode" placeholder="商品规格编码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select
            v-model:value="queryForm.status"
            :options="statusOptions"
            style="width: 150px"
        />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:warning:query'">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-card size="small" :bordered="false">
    <a-alert
        v-if="anchorThresholdId"
        class="anchor"
        type="info"
        show-icon
        :message="`已按消息定位到阈值配置 ${anchorThresholdId}`"
    />
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button
            v-privilege="'scm:inventory:warning:scan'"
            :loading="scanning"
            @click="scan"
        >
          检查并发送预警通知
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_WARNING"
            :refresh="queryData"
        />
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
        :locale="{ emptyText: '没有需要处理的库存预警' }"
        :scroll="{ x: 1450 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'warehouseName'">{{ record.warehouseName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'warehouseCode'">
          <span class="scm-mono">{{ record.warehouseCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'sku'">{{ skuMainText(record.specValues, record.skuName) }}</template>
        <template v-else-if="column.dataIndex === 'skuCode'">
          <span class="scm-mono">{{ record.skuCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unit'">{{ record.unit || '—' }}</template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'reservedQuantity'">
          <span class="scm-quantity">{{ quantityText(record.reservedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'availableQuantity'">
          <!-- 判定基准，加粗以便与「现有量」一眼区分 -->
          <span class="scm-quantity available">{{ quantityText(record.availableQuantity) }}</span>
        </template>
        <!-- 上下限各自可空：不设该边界时显示破折号 -->
        <template v-else-if="column.dataIndex === 'warnMin'">
          <span class="scm-quantity">{{ quantityText(record.warnMin) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'warnMax'">
          <span class="scm-quantity">{{ quantityText(record.warnMax) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="statusTone(record.status)" :label="record.statusDesc || record.status"/>
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
import {reactive, ref, watch} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {useRoute} from 'vue-router';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {inventoryWarningApi} from '/@/api/business/scm/inventory-warning-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {deepLinkId} from '/@/lib/query-deep-link';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {
  SCM_INVENTORY_TABLE_ID,
  SCM_INVENTORY_WARNING_STATUS_ENUM,
} from '/@/constants/business/scm/inventory-const';
import type {Id, InventoryWarning, InventoryWarningQuery} from './inventory-types';
import type {Warehouse} from '../purchase/purchase-types';
import {quantityText, singleWarehouseDefault, skuMainText} from './inventory-model';
import {inventoryError} from './inventory-errors';
import {useScmErrorToast} from '../common/scm-error-toast';

const queryForm = reactive<InventoryWarningQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<InventoryWarning[]>([]);
const total = ref(0);
const loading = ref(false);
const scanning = ref(false);
const error = useScmErrorToast();
const warehouses = ref<Warehouse[]>([]);
/** 来自站内信的阈值配置锚点；非空时页面顶部提示「已定位到某一条」。 */
const anchorThresholdId = ref<Id | undefined>(undefined);
let requestId = 0;

const route = useRoute();
const warningRouteName = route.name;

/**
 * 第一项是「仅异常」而不是「全部」—— 后端 status 为空时的语义就是只看异常。
 * 标成「全部」会与事实不符，用户选了它却看不到正常项会以为系统漏数据。
 */
const statusOptions = [
  {value: undefined, label: '仅异常'},
  ...Object.values(SCM_INVENTORY_WARNING_STATUS_ENUM).map((i) => ({
    value: i.value,
    label: i.desc,
  })),
];

// 列按「哪个仓 / 什么货 / 还够不够发 / 阈值是多少」排列，一格一个值。三个数量都保留：
// 判定基准是可用量，只给一个数字会让用户看不懂预警为什么触发。
const columns = ref<TableColumnsType<InventoryWarning>>([
  {title: '仓库', dataIndex: 'warehouseName', width: 140},
  {title: '仓库编码', dataIndex: 'warehouseCode', width: 120},
  {title: '商品', dataIndex: 'productName', width: 140},
  {title: '商品规格', dataIndex: 'sku', width: 160},
  {title: '商品规格编码', dataIndex: 'skuCode', width: 150},
  {title: '单位', dataIndex: 'unit', align: 'center', width: 80},
  {title: '现有量', dataIndex: 'quantity', align: 'right', width: 100},
  {title: '已预留', dataIndex: 'reservedQuantity', align: 'right', width: 100},
  {title: '可用量', dataIndex: 'availableQuantity', align: 'right', width: 110},
  {title: '预警下限', dataIndex: 'warnMin', align: 'right', width: 100},
  {title: '预警上限', dataIndex: 'warnMax', align: 'right', width: 100},
  {title: '状态', dataIndex: 'status', align: 'center', width: 110},
]);

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
    const r = await inventoryWarningApi.query({...queryForm});
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

/**
 * 立即检查阈值跃迁并投递通知。
 *
 * 只扫描调用者有授权的仓库（范围由服务端解析，前端不传）；重复点击不会重复发信 ——
 * 同一次跃迁的 event_key 是稳定的，第二次起会被去重表挡掉，因此这里不需要额外的防抖。
 */
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
  } catch (e) {
    error.value = inventoryError(e);
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
  queryForm.skuCode = undefined;
  queryForm.thresholdId = undefined;
  // 重置回「仅异常」而不是「全部」：这是本页的默认语义
  queryForm.status = undefined;
  anchorThresholdId.value = undefined;
  onSearch();
}

/**
 * 进入页面（首次挂载与 keep-alive 复用同一条路径）。
 *
 * 站内信跳转带 `thresholdId`（消息的 dataId 就是阈值配置 id），按它精确定位那一条；
 * 普通菜单进入没有 query，于是锚点回落 `undefined`，即页面默认（未筛选）。
 */
async function enterPage() {
  const anchor = deepLinkId(route.query, 'thresholdId');
  anchorThresholdId.value = anchor;
  queryForm.thresholdId = anchor;
  queryForm.skuCode = undefined;
  queryForm.status = undefined;
  queryForm.pageNum = 1;
  // 带锚点时不再套用「只有一个仓库就默认选中」：那会把锚点所在的仓筛掉，看起来像没数据
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
