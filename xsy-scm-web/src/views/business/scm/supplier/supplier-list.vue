<!--
  * 供应商档案列表
  *
  * 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/purchase/supplier-list.vue
  * （Copy First + Adapt，328 行 → 剪枝 + 类型补全）。
  *
  * 剪枝（W2 范围外）：
  * - 删除「供应商ID」列（把主键当业务字段展示，没有意义）；
  * - 删除「状态」查询字段以外的状态编辑入口 —— 状态变更收敛成单条「启用 / 停用」二次确认；
  * - 删除**批量删除**（Target Design Q14：后端没有 batchDelete 端点）；
  * - 删除 `resizable: true` / `@resizeColumn` / `handleResizeColumn`（V2 无 `TableHeaderCell`）；
  * - 删除供应商协同列（账号 / 报品 / 对账，W3+）。
  *
  * 适配 / 新增：
  * - 「关联商品」操作 → 打开 `SupplierSkuDrawer`，走 `supplier_sku` 的**整表替换**写入口；
  * - 新增「关联商品数」列（`skuCount`，后端 `SupplierVO` 已返回，只计活动关联）；
  * - 权限码 → `scm:supplier:*` / `scm:supplier:sku:*`；
  * - 删除改为 `POST /scm/supplier/delete` + `{ supplierId, version }`。
-->
<template>
  <section aria-label="供应商档案">
    <a-form class="smart-query-form" layout="inline">
      <a-row class="smart-query-form-row">
        <a-form-item label="关键字" class="smart-query-form-item">
          <a-input v-model:value="filters.keyword" allow-clear placeholder="编码 / 名称 / 联系人 / 电话" style="width: 240px" />
        </a-form-item>
        <a-form-item label="状态" class="smart-query-form-item">
          <SmartEnumSelect v-model:value="filters.status" enum-name="SUPPLIER_STATUS_ENUM" width="130px" />
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-space>
            <a-button type="primary" @click="search">查询</a-button>
            <a-button @click="reset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-row>
    </a-form>

    <a-card size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <a-button v-privilege="'scm:supplier:add'" type="primary" @click="drawer?.open()">新增供应商</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_SUPPLIER" :refresh="load"/>
      </a-row>
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
        <template #action>
          <a-button size="small" @click="load">重新加载</a-button>
        </template>
      </a-alert>
      <a-table
          :data-source="rows"
          :columns="columns"
          row-key="supplierId"
          :loading="loading"
          :pagination="false"
          size="small"
          bordered
          :scroll="{ x: 1250 }"
          @change="sortChanged"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'name'">
            <a-button type="link" @click="detail(record.supplierId)">{{ record.name }}</a-button>
          </template>
          <span v-else-if="column.dataIndex === 'contactName'">{{ record.contactName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'contactPhone'">{{ record.contactPhone || '—' }}</span>
          <a-button
              v-else-if="column.dataIndex === 'skuCount'"
              v-privilege="'scm:supplier:sku:query'"
              type="link"
              size="small"
              @click="openSku(record)"
          >
            {{ record.skuCount ?? 0 }}
          </a-button>
          <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 'ENABLED' ? 'green' : 'default'">
            {{ statusText(record.status) }}
          </a-tag>
          <a-space v-else-if="column.dataIndex === 'action'" :size="0" class="smart-table-operate">
            <a-button type="link" size="small" @click="detail(record.supplierId)">详情</a-button>
            <a-button v-privilege="'scm:supplier:update'" type="link" size="small"
                      @click="drawer?.open(record.supplierId)">编辑
            </a-button>
            <a-button v-privilege="'scm:supplier:sku:update'" type="link" size="small" @click="openSku(record)">
              关联商品
            </a-button>
            <a-popconfirm
                :title="`确认${record.status === 'ENABLED' ? '停用' : '启用'}此供应商？`"
                @confirm="toggleStatus(record)"
            >
              <a-button v-privilege="'scm:supplier:status'" type="link" size="small">
                {{ record.status === 'ENABLED' ? '停用' : '启用' }}
              </a-button>
            </a-popconfirm>
            <a-popconfirm title="确认删除此供应商？" @confirm="remove(record)">
              <a-button v-privilege="'scm:supplier:delete'" type="link" danger size="small">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </a-table>
      <div class="smart-query-table-page">
        <a-pagination
            v-model:current="filters.pageNum"
            v-model:page-size="filters.pageSize"
            :total="total"
            show-size-changer
            :show-total="(n: number) => `共 ${n} 条`"
            @change="load"
        />
      </div>
    </a-card>

    <SupplierDrawer ref="drawer" @saved="load"/>
    <SupplierSkuDrawer ref="skuDrawer" @saved="load"/>
  </section>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {message} from 'ant-design-vue';
import type {TableColumnsType, TableProps} from 'ant-design-vue';
import {supplierApi} from '/@/api/business/scm/supplier-api';
import type {EnableStatus, ScmId, SupplierQuery, SupplierRow} from '/@/types/business/scm/supplier';
import {SUPPLIER_STATUS_ENUM} from '/@/constants/business/scm/supplier-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import SupplierDrawer from './components/supplier-form-drawer.vue';
import SupplierSkuDrawer from './components/supplier-sku-drawer.vue';
import {supplierError} from './supplier-errors';
import {datetime} from '../common/scm-display';

const router = useRouter();
const filters = reactive<SupplierQuery>({pageNum: 1, pageSize: 20});
const rows = ref<SupplierRow[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const drawer = ref<InstanceType<typeof SupplierDrawer>>();
const skuDrawer = ref<InstanceType<typeof SupplierSkuDrawer>>();

const statusText = (value: EnableStatus): string => SUPPLIER_STATUS_ENUM[value]?.desc || value;

const columns = ref<TableColumnsType<SupplierRow>>([
  {title: '供应商编码', dataIndex: 'supplierCode', width: 160, sorter: true},
  {title: '供应商名称', dataIndex: 'name', width: 220, sorter: true},
  {title: '联系人', dataIndex: 'contactName', width: 120},
  {title: '联系电话', dataIndex: 'contactPhone', width: 150},
  {title: '关联商品数', dataIndex: 'skuCount', width: 120, align: 'center'},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center', sorter: true},
  {title: '更新时间', dataIndex: 'updatedAt', width: 190, sorter: true, customRender: ({text}) => datetime(text)},
  {title: '操作', dataIndex: 'action', width: 300, align: 'right', fixed: 'right'},
]);

let requestId = 0;

async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const response = await supplierApi.query({...filters});
    if (request === requestId) {
      rows.value = response.data.list;
      total.value = Number(response.data.total);
    }
  } catch (e) {
    if (request === requestId) error.value = supplierError(e);
  } finally {
    if (request === requestId) loading.value = false;
  }
}

function search() {
  filters.pageNum = 1;
  void load();
}

function reset() {
  Object.assign(filters, {pageNum: 1, keyword: undefined, status: undefined, sortItemList: undefined});
  void load();
}

// 排序白名单：只映射后端 SupplierQueryService.SORTABLE 允许的四列。
const sortChanged: TableProps<SupplierRow>['onChange'] = (_page, _filters, sort) => {
  const item = Array.isArray(sort) ? sort[0] : sort;
  const names: Record<string, string> = {
    supplierCode: 'supplier_code',
    name: 'name',
    status: 'status',
    updatedAt: 'updated_at',
  };
  const column = names[String(item.field)];
  filters.sortItemList = item.order && column ? [{column, isAsc: item.order === 'ascend'}] : undefined;
  search();
};

function detail(id: ScmId) {
  void router.push({path: '/supplier/supplier-detail', query: {supplierId: String(id)}});
}

/** 打开「关联商品」抽屉，传入供应商行以便回显编码 / 名称。 */
function openSku(row: SupplierRow) {
  void skuDrawer.value?.open(row);
}

async function toggleStatus(row: SupplierRow) {
  const next: EnableStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
  try {
    await supplierApi.updateStatus({supplierId: row.supplierId, version: row.version, status: next});
    message.success('供应商状态已更新');
    await load();
  } catch (e) {
    error.value = supplierError(e);
  }
}

async function remove(row: SupplierRow) {
  try {
    await supplierApi.delete({supplierId: row.supplierId, version: row.version});
    message.success('供应商已删除');
    await load();
  } catch (e) {
    error.value = supplierError(e);
  }
}

onMounted(load);
</script>
