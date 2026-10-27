<!-- 供应商列表：只有单条删除；关联商品走独立的整表替换接口。 -->
<template>
  <section aria-label="供应商档案">
    <a-form class="smart-query-form" layout="inline">
      <a-row class="smart-query-form-row">
        <a-form-item label="关键字" class="smart-query-form-item">
          <a-input v-model:value="filters.keyword" allow-clear placeholder="编码 / 名称 / 联系人 / 电话" style="width: 240px" />
        </a-form-item>
        <a-form-item label="供应商分类" class="smart-query-form-item">
          <BusinessCategoryTreeSelect v-model:value="filters.businessCategoryId" scope="SUPPLIER" style="width: 160px" />
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
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_SUPPLIER" :refresh="load" />
      </a-row>
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
        <template #action>
          <a-button size="small" @click="load">重新加载</a-button>
        </template>
      </a-alert>
      <a-table
        ref="tableRef"
        :data-source="rows"
        :columns="displayColumns"
        row-key="supplierId"
        :loading="loading"
        :pagination="false"
        size="small"
        bordered
        :scroll="{x: scrollX}"
        @change="sortChanged"
      >
        <template #bodyCell="{column, record}">
          <template v-if="column.dataIndex === 'name'">
            <a-button type="link" size="small" class="scm-cell-link" @click="detail(record.supplierId)">{{ record.name }}</a-button>
          </template>
          <template v-else-if="column.dataIndex === 'businessCategoryName'">
            {{ record.businessCategoryName || '未分类' }}
          </template>
          <template v-else-if="column.dataIndex === 'contactName'">
            {{ record.contactName || '—' }}
          </template>
          <template v-else-if="column.dataIndex === 'contactPhone'">
            <span class="scm-mono">{{ record.contactPhone || '—' }}</span>
          </template>
          <a-button
            v-else-if="column.dataIndex === 'skuCount'"
            v-privilege="'scm:supplier:sku:query'"
            type="link"
            size="small"
            @click="openSku(record)"
          >
            {{ record.skuCount ?? 0 }}
          </a-button>
          <ScmStatusTag v-else-if="column.dataIndex === 'status'" :tone="statusTone(record.status)" :label="statusText(record.status)" />
          <a-space v-else-if="column.dataIndex === 'action'" :size="0" class="smart-table-operate scm-table-actions">
            <a-button v-privilege="'scm:supplier:update'" type="link" size="small" @click="drawer?.open(record.supplierId)">编辑 </a-button>
            <a-button v-privilege="'scm:supplier:sku:update'" type="link" size="small" @click="openSku(record)"> 关联商品 </a-button>
            <ScmActionMore :actions="rowActions()" @select="onRowAction($event, record)" />
          </a-space>
        </template>
      </a-table>
      <div class="smart-query-table-page">
        <a-pagination
          v-model:current="filters.pageNum"
          v-model:page-size="filters.pageSize"
          :total="total"
          show-size-changer
          :page-size-options="SCM_PAGE_SIZE_OPTIONS"
          :show-total="(n: number) => `共 ${n} 条`"
          @change="load"
        />
      </div>
    </a-card>

    <SupplierDrawer ref="drawer" @saved="load" />
    <SupplierSkuDrawer ref="skuDrawer" @saved="load" />
  </section>
</template>

<script setup lang="ts">
import {useScmTableFill} from '/@/views/business/scm/common/use-scm-table-fill';
import {onMounted, computed, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType, TableProps} from 'ant-design-vue';
import {supplierApi} from '/@/api/business/scm/supplier-api';
import type {EnableStatus, ScmId, SupplierQuery, SupplierRow} from '/@/types/business/scm/supplier';
import {SUPPLIER_STATUS_ENUM} from '/@/constants/business/scm/supplier-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import BusinessCategoryTreeSelect from '/@/components/business/scm/business-category-tree-select/index.vue';
import SupplierDrawer from './components/supplier-form-drawer.vue';
import SupplierSkuDrawer from './components/supplier-sku-drawer.vue';
import {supplierError} from './supplier-errors';
import {hasPermission} from '../common/scm-permission';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';

const router = useRouter();
const filters = reactive<SupplierQuery>({pageNum: 1, pageSize: 20});
const rows = ref<SupplierRow[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const drawer = ref<InstanceType<typeof SupplierDrawer>>();
const skuDrawer = ref<InstanceType<typeof SupplierSkuDrawer>>();

const statusText = (value: EnableStatus): string => SUPPLIER_STATUS_ENUM[value]?.desc || value;
/** 启用=正常（绿），停用=失效（灰）。 */
const statusTone = (value: EnableStatus): ScmStatusTone => (value === 'ENABLED' ? 'success' : 'neutral');

// 更新时间不上列表，可在详情里查看。
const columns = ref<TableColumnsType<SupplierRow>>([
  {title: '供应商名称', dataIndex: 'name', width: 190, sorter: true, ellipsis: true},
  {title: '供应商分类', dataIndex: 'businessCategoryName', width: 120, align: 'center'},
  {title: '联系人', dataIndex: 'contactName', width: 130, ellipsis: true},
  {title: '联系电话', dataIndex: 'contactPhone', width: 140},
  {title: '关联商品数', dataIndex: 'skuCount', width: 92},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center', sorter: true},
  {title: '操作', dataIndex: 'action', width: 170, align: 'center', fixed: 'right'},
]);
// 文本列撑满剩余宽度；用户改过的列宽不动。
const {tableRef, displayColumns, scrollX} = useScmTableFill(columns);

let requestId = 0;

async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头 Alert 提示，不再弹全局 toast
    const response = await supplierApi.query({...filters}, {suppressGlobalErrorMessage: true});
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
  Object.assign(filters, {pageNum: 1, keyword: undefined, businessCategoryId: undefined, status: undefined, sortItemList: undefined});
  void load();
}

// 排序只映射后端允许的四列。
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

/** 传入供应商行，用于回显编码和名称。 */
function openSku(row: SupplierRow) {
  void skuDrawer.value?.open(row);
}

async function toggleStatus(row: SupplierRow) {
  const next: EnableStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
  try {
    await supplierApi.updateStatus({supplierId: row.supplierId, version: row.version, status: next});
    message.success('供应商状态已更新');
    await load();
  } catch {
    // 状态更新失败由全局 toast 提示
  }
}

async function remove(row: SupplierRow) {
  try {
    await supplierApi.delete({supplierId: row.supplierId, version: row.version});
    message.success('供应商已删除');
    await load();
  } catch {
    // 删除失败由全局 toast 提示
  }
}

/** 菜单项不能挂 v-privilege，用 hasPermission 判断。 */
const canUpdateStatus = computed(() => hasPermission('scm:supplier:status'));
const canDelete = computed(() => hasPermission('scm:supplier:delete'));

/** 低频和危险动作收进「更多」。 */
function rowActions(): ScmActionItem[] {
  return [
    {key: 'detail', label: '详情'},
    {key: 'toggle', label: '启用 / 停用', hidden: !canUpdateStatus.value},
    {key: 'delete', label: '删除', danger: true, hidden: !canDelete.value},
  ];
}

function onRowAction(key: string, row: SupplierRow) {
  if (key === 'detail') {
    detail(row.supplierId);
  } else if (key === 'toggle') {
    const next = row.status === 'ENABLED' ? '停用' : '启用';
    // 菜单项不能挂 a-popconfirm，改用 Modal 确认。
    Modal.confirm({
      title: `${next}供应商`,
      content: `确认${next}「${row.name}」？`,
      okText: next,
      okType: row.status === 'ENABLED' ? 'danger' : 'primary',
      cancelText: '取消',
      onOk: () => toggleStatus(row),
    });
  } else if (key === 'delete') {
    Modal.confirm({
      title: '删除供应商',
      content: `确认删除「${row.name}」（${row.supplierCode}）？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: () => remove(row),
    });
  }
}

onMounted(load);
</script>
