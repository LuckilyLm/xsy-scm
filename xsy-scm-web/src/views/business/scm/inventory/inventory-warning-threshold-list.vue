<!--
  预警阈值配置。

  一个 (仓库, SKU) 只允许一条配置：两条会让「按哪条判断」变得没有答案，
  而预警是给人看的，含糊的预警等于没有预警。

  本页不改变库存：阈值是配置，余额是派生状态，两者的写路径完全分开 ——
  配置路径不会、也不应该创建余额行（否则就会造出「没有任何流水支撑的余额行」）。
  因此没有「确认 / 审批」这类动作，改配置立即生效（预警是读时计算的，天然实时）。
-->
<template>
  <a-form class="scm-filter-bar" layout="inline" @submit.prevent="onSearch">
    <div class="scm-filter-fields">
      <a-form-item label="仓库">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="200px"/>
      </a-form-item>
      <a-form-item label="商品规格编码">
        <a-input v-model:value="queryForm.skuCode" placeholder="商品规格编码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
    </div>
    <div class="scm-filter-actions">
      <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:threshold:query'">查询</a-button>
      <a-button @click="resetQuery">重置</a-button>
    </div>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block scm-table-toolbar">
      <div class="smart-table-operate-block">
        <a-button type="primary" @click="openCreate" v-privilege="'scm:inventory:threshold:add'">
          新建阈值配置
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_WARNING_THRESHOLD"
            :refresh="queryData"
        />
      </div>
    </a-row>

    <a-table
        :id="SCM_INVENTORY_TABLE_ID.WARNING_THRESHOLD"
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText: '暂无阈值配置' }"
        :scroll="{ x: 1315 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'warehouseCode'">
          <span class="scm-mono">{{ record.warehouseCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'skuName'">
          {{ skuMainText(record.specValues, record.skuName) }}
        </template>
        <template v-else-if="column.dataIndex === 'skuCode'">
          <span class="scm-mono">{{ record.skuCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'productName'">
          {{ record.productName || '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'warnMin'">
          <span class="scm-quantity">{{ quantityText(record.warnMin) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'warnMax'">
          <span class="scm-quantity">{{ quantityText(record.warnMax) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button
                type="link"
                size="small"
                @click="openEdit(record)"
                v-privilege="'scm:inventory:threshold:update'"
            >
              编辑
            </a-button>
            <a-button
                type="link"
                size="small"
                danger
                @click="onDelete(record)"
                v-privilege="'scm:inventory:threshold:delete'"
            >
              删除
            </a-button>
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

  <!-- 新建 / 编辑 -->
  <a-drawer
      :open="drawerOpen"
      :title="form.id ? '编辑阈值配置' : '新建阈值配置'"
      :width="scmDrawerWidth('s')"
      @close="closeDrawer"
  >
    <a-alert
        type="info"
        show-icon
        style="margin-bottom: 12px"
        message="预警按可用量（现有量 − 预留量）判定。"
    />
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical">
      <a-form-item label="仓库" name="warehouseId">
        <WarehouseSelect v-model:value="form.warehouseId" :options="warehouses" width="260px"/>
      </a-form-item>
      <a-form-item label="商品规格" name="skuId">
        <SkuSelect
            :value="form.skuId"
            :disabled-statuses="[]"
            width="260px"
            @update:value="(v) => (form.skuId = Array.isArray(v) ? v[0] : v)"
        />
      </a-form-item>
      <a-form-item label="预警下限" name="warnMin">
        <a-input-number
            v-model:value="form.warnMin"
            :min="0"
            :precision="4"
            :step="1"
            placeholder="留空表示不设下限"
            style="width: 200px"
        />
      </a-form-item>
      <a-form-item label="预警上限" name="warnMax">
        <a-input-number
            v-model:value="form.warnMax"
            :min="0"
            :precision="4"
            :step="1"
            placeholder="留空表示不设上限"
            style="width: 200px"
        />
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="form.remark" :rows="2" :maxlength="500" show-count/>
      </a-form-item>
    </a-form>
    <template #footer>
      <a-space>
        <a-button @click="closeDrawer">取消</a-button>
        <a-button type="primary" :loading="saving" @click="onSubmit">保存</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {inventoryWarningThresholdApi} from '/@/api/business/scm/inventory-warning-threshold-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_INVENTORY_TABLE_ID} from '/@/constants/business/scm/inventory-const';
import type {
  InventoryWarningThreshold,
  InventoryWarningThresholdAdd,
  InventoryWarningThresholdQuery,
} from './inventory-types';
import type {Warehouse} from '../purchase/purchase-types';
import {fixed4} from '../common/scm-fixed';
import {quantityText, singleWarehouseDefault, skuMainText} from './inventory-model';
import {inventoryError} from './inventory-errors';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const queryForm = reactive<InventoryWarningThresholdQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<InventoryWarningThreshold[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const warehouses = ref<Warehouse[]>([]);
let requestId = 0;

// 配置页将仓库、商品、规格和编码分列；更新时间不上列。
const columns = ref<TableColumnsType<InventoryWarningThreshold>>([
  {title: '仓库', dataIndex: 'warehouseName', width: 150, ellipsis: true},
  {title: '仓库编码', dataIndex: 'warehouseCode', width: 120},
  {title: '商品', dataIndex: 'productName', width: 160, ellipsis: true},
  {title: '商品规格', dataIndex: 'skuName', width: 170, ellipsis: true},
  {title: '规格编码', dataIndex: 'skuCode', width: 135},
  {title: '预警下限', dataIndex: 'warnMin', align: 'right', width: 120},
  {title: '预警上限', dataIndex: 'warnMax', align: 'right', width: 120},
  {title: '备注', dataIndex: 'remark', width: 200, ellipsis: true},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 140},
]);

// ------------------------------------------------------------------ 查询

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await inventoryWarningThresholdApi.query({...queryForm});
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
  queryForm.warehouseId = undefined;
  queryForm.skuCode = undefined;
  onSearch();
}

// ------------------------------------------------------------------ 表单

const drawerOpen = ref(false);
const saving = ref(false);
const formRef = ref();

const form = reactive<{
  id?: string | number;
  warehouseId?: string | number;
  skuId?: string | number;
  /**
   * 上下限在表单里是 `number`（InputNumber 只接受数字），提交时才转成后端要求的
   * 4 位定点字符串。`null` = 不设该边界，与 `0` 是两件事。
   */
  warnMin?: number | null;
  warnMax?: number | null;
  remark?: string;
}>({});

async function validateAtLeastOneThreshold() {
  if (form.warnMin != null || form.warnMax != null) {
    return;
  }
  throw new Error('预警下限和上限至少填写一个');
}

const formRules = {
  warehouseId: [{required: true, message: '请选择仓库'}],
  skuId: [{required: true, message: '请选择商品规格'}],
  warnMin: [{validator: validateAtLeastOneThreshold}],
};

function openCreate() {
  form.id = undefined;
  form.warehouseId = queryForm.warehouseId ?? singleWarehouseDefault(warehouses.value);
  form.skuId = undefined;
  form.warnMin = null;
  form.warnMax = null;
  form.remark = undefined;
  drawerOpen.value = true;
}

function openEdit(record: InventoryWarningThreshold) {
  form.id = record.id;
  form.warehouseId = record.warehouseId;
  form.skuId = record.skuId;
  // 后端给的是 4 位定点字符串，InputNumber 要 number；空值保持 null（= 不设该边界）
  form.warnMin = record.warnMin == null ? null : Number(record.warnMin);
  form.warnMax = record.warnMax == null ? null : Number(record.warnMax);
  form.remark = record.remark;
  drawerOpen.value = true;
}

function closeDrawer() {
  drawerOpen.value = false;
}

/**
 * 提交前校验两条判据：至少填一个、下限不高于上限。
 * 非负与小数位数已由 InputNumber（`:min="0"` + `:precision="4"`）结构性保证，
 * 后端会再判一次（41051）并给出可读错误。
 */
function buildPayload(): InventoryWarningThresholdAdd | null {
  const min = fixed4(form.warnMin);
  const max = fixed4(form.warnMax);
  if (min === undefined && max === undefined) {
    message.warning('预警下限和上限至少填写一个');
    return null;
  }
  if (form.warnMin != null && form.warnMax != null && form.warnMin > form.warnMax) {
    message.warning('预警下限不得大于上限');
    return null;
  }
  return {
    warehouseId: form.warehouseId as string | number,
    skuId: form.skuId as string | number,
    // 定点字符串提交：后端拒绝 JSON 数字（ScmStrictDecimalStringDeserializer）
    warnMin: min ?? null,
    warnMax: max ?? null,
    remark: form.remark,
  };
}

async function onSubmit() {
  await formRef.value.validate();
  const payload = buildPayload();
  if (!payload) return;
  saving.value = true;
  try {
    if (form.id) {
      await inventoryWarningThresholdApi.update(form.id, payload);
      message.success('已保存');
    } else {
      await inventoryWarningThresholdApi.create(payload);
      message.success('已创建');
    }
    drawerOpen.value = false;
    queryData();
  } catch (e) {
    message.error(inventoryError(e));
  } finally {
    saving.value = false;
  }
}

function onDelete(record: InventoryWarningThreshold) {
  Modal.confirm({
    title: '删除阈值配置',
    content: `确认删除「${record.warehouseName || ''} / ${record.skuCode || ''}」的预警阈值？删除后该仓库与商品规格不再产生预警。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '返回',
    onOk: async () => {
      try {
        await inventoryWarningThresholdApi.delete(record.id);
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
