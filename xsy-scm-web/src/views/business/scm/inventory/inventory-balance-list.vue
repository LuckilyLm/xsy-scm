<!--
  W6 库存余额（新增文件）。仿 W5 `warehouse-list.vue` 的列表骨架。
  适配：`/scm/inventory/balance/**`（2 个只读端点）、`scm:inventory:balance:query`（菜单 811）、
        `scm-inventory-balance-table`、`inventory-errors`、loading/empty/error/retry、`v-privilege`。

  **本页全只读**：库存余额不是可以被直接赋值的状态，它只能是流水的净和。
  唯一的写入路径是「收货确认 → 采购入库」，发生在采购侧的同事务内，因此这里
  没有任何新建/编辑/删除按钮 —— 一个不存在的入口比一个会报错的入口更诚实。

  **Q12（默认仓库）**：系统恰好只有 1 个启用仓库时，加载时默认带出该仓库；
  启用仓库数量 != 1 时**不自动选任何一个**（只提供普通筛选）。理由：多仓下随便选一个仓
  会让用户误以为自己在看全部仓库的库存，而实际上只看到一个仓 —— 这种误解比多一次点击昂贵得多。
  判定放在前端（后端不做隐式默认），见 `applySingleWarehouseDefault`。

  **筛选为什么用编码/名称文本而不是 SKU 选择器**：`/scm/product/sku/option-list` 需要
  `scm:product:sku:query`。把它放到库存页会让「只有库存权限的人打不开库存页的筛选」——
  一个只读页不该依赖另一个域的权限。后端仍支持 `skuId` 精确筛选（见 `inventory-types.ts`）。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="220px" />
      </a-form-item>
      <a-form-item label="SKU 编码" class="smart-query-form-item">
        <a-input v-model:value="queryForm.skuCode" placeholder="SKU 编码" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="商品名称" class="smart-query-form-item">
        <a-input v-model:value="queryForm.productName" placeholder="商品名称" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:balance:query'">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action><a-button @click="queryData">重试</a-button></template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-typography-text type="secondary">
          余额由「收货确认 → 采购入库」的流水累加而来，本页不提供修改入口。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
          v-model="columns"
          :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_BALANCE"
          :refresh="queryData"
        />
      </div>
    </a-row>

    <a-table
      :id="SCM_INVENTORY_TABLE_ID.BALANCE"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{ emptyText: '暂无库存余额' }"
      :scroll="{ x: 1400 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'specValues'">{{ specText(record.specValues) }}</template>
        <template v-else-if="column.dataIndex === 'unit'">{{ record.unit || '—' }}</template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="num">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'updatedAt'">{{ datetime(record.updatedAt) }}</template>
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
import { onMounted, reactive, ref } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import { inventoryBalanceApi } from '/@/api/business/scm/inventory-balance-api';
import { warehouseApi } from '/@/api/business/scm/warehouse-api';
import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
import { SCM_INVENTORY_TABLE_ID } from '/@/constants/business/scm/inventory-const';
import type { InventoryBalance, InventoryBalanceQuery } from './inventory-types';
import type { Warehouse } from '../purchase/purchase-types';
import { quantityText, singleWarehouseDefault, specText } from './inventory-model';
import { inventoryError } from './inventory-errors';
import { datetime } from '../common/scm-display';

const queryForm = reactive<InventoryBalanceQuery>({ pageNum: 1, pageSize: 20 });
const tableData = ref<InventoryBalance[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
/** 仓库选项由本页持有并传给 `WarehouseSelect`：Q12 的判定本来就要拉一次，避免同一端点被请求两次。 */
const warehouses = ref<Warehouse[]>([]);
let requestId = 0;

const columns = ref<TableColumnsType<InventoryBalance>>([
  { title: '仓库编码', dataIndex: 'warehouseCode', width: 130 },
  { title: '仓库名称', dataIndex: 'warehouseName', width: 160 },
  { title: 'SKU 编码', dataIndex: 'skuCode', width: 170 },
  { title: 'SKU 名称', dataIndex: 'skuName', width: 150 },
  { title: '商品名称', dataIndex: 'productName', width: 180 },
  { title: '规格', dataIndex: 'specValues', width: 160 },
  { title: '单位', dataIndex: 'unit', align: 'center', width: 90 },
  { title: '库存数量', dataIndex: 'quantity', align: 'right', width: 130 },
  { title: '更新时间', dataIndex: 'updatedAt', width: 190 },
]);

/** `specValues` 是 `{"规格":"散装"}` 形状的 JSONB；无值显示破折号。见 `inventory-model.ts`。 */

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await inventoryBalanceApi.query({ ...queryForm });
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

/**
 * Q12：系统恰好只有 1 个启用仓库时默认带出该仓库；否则不自动选择（规则见
 * `inventory-model.ts` 的 `singleWarehouseDefault`，那里有单测）。
 *
 * 拉不到仓库列表（例如当前账号没有 `scm:warehouse:query`）时**静默跳过**：
 * 默认仓库只是便利，不该让整个只读页打不开。
 */
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
  // 重置时**清空**仓库，而不是重新套用 Q12 默认值：用户点「重置」的意图是「回到无筛选」。
  // 多仓下重新套一个默认值会让人以为筛选没清掉。
  queryForm.warehouseId = undefined;
  queryForm.skuCode = undefined;
  queryForm.productName = undefined;
  onSearch();
}

onMounted(async () => {
  // 顺序固定：先定默认仓库，再查第一屏数据 —— 否则首屏会先显示「全部仓库」再跳成「默认仓库」，
  // 看起来像页面闪了一下。
  await applySingleWarehouseDefault();
  await queryData();
});
</script>

<style scoped>
/* 数量一律等宽右对齐：4 位定点数在比例字体下会参差不齐，扫描一列数字时很费眼 */
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
