<!--
  供应商档案列表。
  - 不做批量删除：后端没有 `batchDelete` 端点；
  - 「关联商品」走 `supplier_sku` 的整表替换写入口；
  - 状态变更收敛成单条「启用 / 停用」二次确认。
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
          :scroll="{ x: 990 }"
          @change="sortChanged"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'name'">
            <a-button type="link" size="small" class="scm-cell-link" @click="detail(record.supplierId)">{{ record.name }}</a-button>
          </template>
          <template v-else-if="column.dataIndex === 'supplierCode'">
            <span class="scm-mono">{{ record.supplierCode || '—' }}</span>
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
          <ScmStatusTag v-else-if="column.dataIndex === 'status'" :tone="statusTone(record.status)"
                        :label="statusText(record.status)"/>
          <a-space v-else-if="column.dataIndex === 'action'" :size="0"
                   class="smart-table-operate scm-table-actions">
            <a-button v-privilege="'scm:supplier:update'" type="link" size="small"
                      @click="drawer?.open(record.supplierId)">编辑
            </a-button>
            <a-button v-privilege="'scm:supplier:sku:update'" type="link" size="small" @click="openSku(record)">
              关联商品
            </a-button>
            <ScmActionMore :actions="rowActions()" @select="onRowAction($event, record)"/>
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
import SupplierDrawer from './components/supplier-form-drawer.vue';
import SupplierSkuDrawer from './components/supplier-sku-drawer.vue';
import {supplierError} from './supplier-errors';
import {hasPermission} from '../common/scm-permission';

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

// 名称、编码、联系人和电话可独立查看、比较；更新时间仍由搜索、详情、编辑与导出承载。
const columns = ref<TableColumnsType<SupplierRow>>([
  {title: '供应商名称', dataIndex: 'name', width: 190, sorter: true, ellipsis: true},
  {title: '供应商编码', dataIndex: 'supplierCode', width: 140},
  {title: '联系人', dataIndex: 'contactName', width: 130, ellipsis: true},
  {title: '联系电话', dataIndex: 'contactPhone', width: 140},
  {title: '关联商品数', dataIndex: 'skuCount', width: 120, align: 'center'},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center', sorter: true},
  {title: '操作', dataIndex: 'action', width: 170, align: 'center', fixed: 'right'},
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

/** 「更多」里的菜单项挂不上 `v-privilege` 指令，改用同一口径的 hasPermission 裁剪。 */
const canUpdateStatus = computed(() => hasPermission('scm:supplier:status'));
const canDelete = computed(() => hasPermission('scm:supplier:delete'));

/** 操作列常驻「编辑 + 关联商品」，其余低频与危险动作收进「更多」，把宽度留给业务字段。 */
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
    // 菜单项挂不上 a-popconfirm，二次确认改由 Modal 承担，语义与原 popconfirm 一致。
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
