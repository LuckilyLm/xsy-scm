<!-- C 无仓库页（新增文件）。仿 W2 `supplier-list.vue` 的列表骨架。
适配：`/scm/warehouse/**`（5 个端点）、`version`（A8）、`scm:warehouse:*`（A22）、
      `scm-warehouse-table`（A23）、`purchase-errors`（A24）、loading/empty/error/retry（A27）、
      `v-privilege`（A30）。
**已知缺口 G1**：`WarehouseAddForm` / `WarehouseUpdateForm` **不含 `status`**，且没有独立的启停端点
——因此本页只做「新建 / 编辑基础信息」，不提供启用停用按钮（停用需要后端补写入路径）。
验收：W5 单测、TS 棘轮与 Playwright。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="仓库编码" class="smart-query-form-item">
        <a-input v-model:value="queryForm.warehouseCode" placeholder="仓库编码" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="仓库名称" class="smart-query-form-item">
        <a-input v-model:value="queryForm.name" placeholder="仓库名称" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_WAREHOUSE_STATUS_ENUM" v-model:value="queryForm.status" width="140px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:warehouse:query'">查询</a-button>
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
        <a-button type="primary" v-privilege="'scm:warehouse:add'" @click="open()">新建仓库</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_WAREHOUSE" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      :id="SCM_PURCHASE_TABLE_ID.WAREHOUSE"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{ x: 1200 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="record.status === 'ENABLED' ? 'green' : 'default'">
            {{ SCM_WAREHOUSE_STATUS_ENUM[record.status]?.desc || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'address'">{{ record.address || '—' }}</template>
        <template v-else-if="column.dataIndex === 'remark'">{{ record.remark || '—' }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button type="link" v-privilege="'scm:warehouse:update'" @click="open(record)">编辑</a-button>
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

  <a-modal
    :open="visible"
    :title="form.id ? '编辑仓库' : '新建仓库'"
    :confirm-loading="saving"
    @ok="save"
    @cancel="visible = false"
  >
    <a-alert v-if="formError" :message="formError" type="error" show-icon />
    <a-form :model="form" layout="vertical">
      <a-form-item label="仓库编码" name="warehouseCode" required>
        <a-input v-model:value="form.warehouseCode" maxlength="64" :disabled="!!form.id" />
      </a-form-item>
      <a-form-item label="仓库名称" name="name" required>
        <a-input v-model:value="form.name" maxlength="150" />
      </a-form-item>
      <a-form-item label="地址" name="address">
        <a-input v-model:value="form.address" maxlength="255" />
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-input v-model:value="form.remark" maxlength="500" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import { warehouseApi } from '/@/api/business/scm/warehouse-api';
import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
import { SCM_PURCHASE_TABLE_ID, SCM_WAREHOUSE_STATUS_ENUM } from '/@/constants/business/scm/purchase-const';
import type { Warehouse, WarehousePayload, WarehouseQuery } from './purchase-types';
import { purchaseError } from './purchase-errors';
import { datetime } from '../common/scm-display';

const queryForm = reactive<WarehouseQuery>({ pageNum: 1, pageSize: 20 });
const tableData = ref<Warehouse[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const visible = ref(false);
const saving = ref(false);
const formError = ref('');
const form = ref<WarehousePayload>({ warehouseCode: '', name: '' });
let requestId = 0;

const columns = ref<TableColumnsType<Warehouse>>([
  { title: '仓库编码', dataIndex: 'warehouseCode', width: 160 },
  { title: '仓库名称', dataIndex: 'name', width: 200 },
  { title: '状态', dataIndex: 'status', align: 'center', width: 110 },
  { title: '地址', dataIndex: 'address', width: 260 },
  { title: '备注', dataIndex: 'remark', width: 200 },
  { title: '创建时间', dataIndex: 'createdAt', width: 190, customRender: ({ text }) => datetime(text) },
  { title: '操作', dataIndex: 'action', align: 'right', fixed: 'right', width: 100 },
]);

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await warehouseApi.query(queryForm);
    if (id === requestId) {
      tableData.value = r.data.list;
      total.value = r.data.total;
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
  queryForm.warehouseCode = undefined;
  queryForm.name = undefined;
  queryForm.status = undefined;
  onSearch();
}

function open(row?: Warehouse) {
  formError.value = '';
  form.value = row
    ? {
        id: row.id,
        version: row.version,
        warehouseCode: row.warehouseCode ?? '',
        name: row.name ?? '',
        address: row.address ?? null,
        remark: row.remark ?? null,
      }
    : { warehouseCode: '', name: '' };
  visible.value = true;
}

async function save() {
  formError.value = '';
  if (!form.value.warehouseCode.trim()) {
    formError.value = '请填写仓库编码';
    return;
  }
  if (!form.value.name.trim()) {
    formError.value = '请填写仓库名称';
    return;
  }
  saving.value = true;
  try {
    if (form.value.id === undefined) {
      await warehouseApi.create(form.value);
      message.success('仓库已创建');
    } else {
      await warehouseApi.update(form.value);
      message.success('仓库已更新');
    }
    visible.value = false;
    await queryData();
  } catch (e) {
    formError.value = purchaseError(e);
  } finally {
    saving.value = false;
  }
}

onMounted(queryData);
</script>
