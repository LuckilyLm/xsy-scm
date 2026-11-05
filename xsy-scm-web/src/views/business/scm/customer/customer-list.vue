<!-- 客户档案列表：只有单条删除；翻页时用请求序号丢弃过期响应。 -->
<template>
  <section aria-label="客户档案">
    <div class="xsy-query-layout">
      <a-form class="xsy-query-form" layout="inline">
        <a-row class="xsy-query-form-row">
          <a-form-item label="关键字" class="xsy-query-form-item">
            <a-input v-model:value="filters.keyword" allow-clear placeholder="客户名称 / 联系人 / 电话" style="width: 200px" />
          </a-form-item>
          <a-form-item label="客户分类" class="xsy-query-form-item">
            <BusinessCategoryTreeSelect v-model:value="filters.businessCategoryId" scope="CUSTOMER" style="width: 150px" />
          </a-form-item>
          <a-form-item label="客户分组" class="xsy-query-form-item">
            <CustomerGroupSelect v-model:value="filters.customerGroupId" width="150px" />
          </a-form-item>
          <a-form-item label="状态" class="xsy-query-form-item">
            <XsyEnumSelect v-model:value="filters.status" enum-name="CUSTOMER_STATUS_ENUM" width="120px" />
          </a-form-item>
          <a-form-item class="xsy-query-form-item">
            <a-space :size="10" wrap>
              <a-button type="primary" @click="search">查询</a-button>
              <a-button @click="reset">重置</a-button>
              <a-button type="link" @click="advanced = !advanced">{{ advanced ? '收起筛选' : '高级筛选' }}</a-button>
            </a-space>
          </a-form-item>
        </a-row>
        <a-row v-if="advanced" class="xsy-query-form-row">
          <a-form-item label="结算方式" class="xsy-query-form-item">
            <XsyEnumSelect v-model:value="filters.settleMode" enum-name="SETTLE_MODE_ENUM" width="140px" />
          </a-form-item>
          <a-form-item label="上级客户" class="xsy-query-form-item">
            <CustomerSelect v-model:value="filters.parentCustomerId" width="220px" placeholder="请选择上级客户" />
          </a-form-item>
        </a-row>
      </a-form>
      <a-row data-toolbar-layout="query" class="xsy-table-btn-block" justify="space-between" align="middle">
        <a-button v-privilege="'scm:customer:add'" type="primary" @click="drawer?.open()">新增客户</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_CUSTOMER" :refresh="load" />
      </a-row>
    </div>

    <a-card size="small" :bordered="false">
      <a-alert v-if="error" :message="error" type="error" show-icon class="xsy-margin-bottom10">
        <template #action>
          <a-button size="small" @click="load">重新加载</a-button>
        </template>
      </a-alert>
      <a-table
        ref="tableRef"
        class="customer-table"
        :data-source="rows"
        :columns="displayColumns"
        row-key="customerId"
        :loading="loading"
        :pagination="false"
        :locale="{emptyText}"
        size="small"
        bordered
        :scroll="{x: scrollX}"
        @change="sortChanged"
      >
        <template #bodyCell="{column, record}">
          <template v-if="column.dataIndex === 'name'">
            <a-button type="link" size="small" class="scm-cell-link" @click="detail(record.customerId)">
              {{ record.name }}
            </a-button>
          </template>
          <span v-else-if="column.dataIndex === 'businessCategoryName'">{{ record.businessCategoryName || '未分类' }}</span>
          <span v-else-if="column.dataIndex === 'customerGroupName'">{{ record.customerGroupName || '未分组' }}</span>
          <span v-else-if="column.dataIndex === 'sellerName'">{{ record.sellerName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'contactName'">{{ record.contactName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'contactPhone'" class="scm-mono">{{ record.contactPhone || '—' }}</span>
          <span v-else-if="column.dataIndex === 'settleMode'">{{ settleModeText(record.settleMode) }}</span>
          <span v-else-if="column.dataIndex === 'creditLimit'" class="scm-money">{{ formatAmountOrDash(record.creditLimit) }}</span>
          <ScmStatusTag v-else-if="column.dataIndex === 'status'" :color="statusColor(record.status)" :label="statusText(record.status)" />
          <a-space v-else-if="column.dataIndex === 'action'" :size="4" class="xsy-table-operate scm-table-actions">
            <a-button v-privilege="'scm:customer:update'" type="link" size="small" @click="drawer?.open(record.customerId)">编辑 </a-button>
            <ScmActionMore :actions="rowActions()" @select="onRowAction($event, record)" />
          </a-space>
        </template>
      </a-table>
      <div class="xsy-query-table-page">
        <a-pagination
          show-quick-jumper
          show-less-items
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

    <CustomerDrawer ref="drawer" @saved="load" />
    <CustomerPromotionModal ref="promotionModal" />
  </section>
</template>

<script setup lang="ts">
import {useScmTableFill} from '/@/views/business/scm/common/use-scm-table-fill';
import {onMounted, reactive, ref, computed} from 'vue';
import {useRouter} from 'vue-router';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType, TableProps} from 'ant-design-vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import type {CustomerQuery, CustomerRow, CustomerStatus, ScmId} from '/@/types/business/scm/customer';
import {CUSTOMER_STATUS_ENUM, SETTLE_MODE_ENUM} from '/@/constants/business/scm/customer-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import XsyEnumSelect from '/@/components/framework/xsy-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import CustomerPromotionModal from '/@/views/business/scm/customer/components/customer-promotion-modal.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import BusinessCategoryTreeSelect from '/@/components/business/scm/business-category-tree-select/index.vue';
import CustomerGroupSelect from '/@/components/business/scm/customer-group-select/index.vue';
import CustomerDrawer from './components/customer-form-drawer.vue';
import {customerError} from './customer-errors';
import {hasPermission} from '../common/scm-permission';
import {formatAmountOrDash} from '/@/utils/scm-amount';
import {useQueryFilterMemory} from '/@/lib/query-filter-memory';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';

const router = useRouter();
const filters = reactive<CustomerQuery>({pageNum: 1, pageSize: 20});
const rows = ref<CustomerRow[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const advanced = ref(false);
const drawer = ref<InstanceType<typeof CustomerDrawer>>();
// 查询条件记在浏览器本地，不写业务表。
const queryMemory = useQueryFilterMemory<CustomerQuery>('scm:customer:list');

/** 没有全量权限时空表要说明，可能只是授权范围内没有数据。 */
const canSeeAllCustomers = computed(() => hasPermission('scm:customer:scope:all:query'));
const emptyText = computed(() =>
  canSeeAllCustomers.value ? '暂无数据' : '当前仅显示您授权范围内的客户；若无数据，可能是尚未分配业务员或授权范围未配置，请联系管理员确认。'
);

const statusText = (value: CustomerStatus): string => CUSTOMER_STATUS_ENUM[value]?.desc || value;
const settleModeText = (value: string): string => SETTLE_MODE_ENUM[value]?.desc || value;
const statusColor = (value: CustomerStatus): string => {
  if (value === 'COOPERATING') return 'green';
  if (value === 'SUSPENDED') return 'orange';
  if (value === 'BLACKLIST') return 'red';
  return 'default';
};

// 客户编码属于内部标识，不上列表。
type CustomerListColumn = TableColumnsType<CustomerRow>[number] & {showFlag?: boolean};
const columns = ref<CustomerListColumn[]>([
  {title: '客户名称', dataIndex: 'name', width: 200, sorter: true, ellipsis: true},
  {title: '客户分类', dataIndex: 'businessCategoryName', width: 120, align: 'center'},
  {title: '客户分组', dataIndex: 'customerGroupName', width: 140},
  {title: '业务员', dataIndex: 'sellerName', width: 110},
  {title: '联系人', dataIndex: 'contactName', width: 120},
  {title: '联系电话', dataIndex: 'contactPhone', width: 140},
  {title: '结算方式', dataIndex: 'settleMode', width: 112, align: 'center'},
  {title: '赊欠额度', dataIndex: 'creditLimit', width: 130},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center', sorter: true},
  {title: '操作', dataIndex: 'action', width: 150, align: 'center', fixed: 'right'},
]);
// 客户名称撑满剩余宽度；用户改过的列宽不动。
const {tableRef, displayColumns, scrollX} = useScmTableFill(columns);

let requestId = 0;

async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头 Alert 提示，不再弹全局 toast
    const response = await customerApi.query({...filters}, {suppressGlobalErrorMessage: true});
    if (request === requestId) {
      rows.value = response.data.list;
      total.value = Number(response.data.total);
    }
  } catch (e) {
    if (request === requestId) error.value = customerError(e);
  } finally {
    if (request === requestId) loading.value = false;
  }
}

function search() {
  filters.pageNum = 1;
  queryMemory.save({...filters});
  void load();
}

function reset() {
  Object.assign(filters, {
    pageNum: 1,
    pageSize: 20,
    keyword: undefined,
    businessCategoryId: undefined,
    customerGroupId: undefined,
    status: undefined,
    settleMode: undefined,
    parentCustomerId: undefined,
    sortItemList: undefined,
  });
  queryMemory.clear();
  void load();
}

// 排序只映射后端允许的四列。
const sortChanged: TableProps<CustomerRow>['onChange'] = (_page, _filters, sort) => {
  const item = Array.isArray(sort) ? sort[0] : sort;
  const names: Record<string, string> = {
    customerCode: 'customer_code',
    name: 'name',
    status: 'status',
    updatedAt: 'updated_at',
  };
  const column = names[String(item.field)];
  filters.sortItemList = item.order && column ? [{column, isAsc: item.order === 'ascend'}] : undefined;
  search();
};

function detail(id: ScmId) {
  void router.push({path: '/customer/customer-detail', query: {customerId: String(id)}});
}

async function remove(row: CustomerRow) {
  try {
    await customerApi.delete({customerId: row.customerId, version: row.version});
    message.success('客户已删除');
    await load();
  } catch {
    // 删除失败由全局 toast 提示
  }
}

/** 菜单项不能挂 v-privilege，用 hasPermission 判断。 */
const canPromotion = computed(() => hasPermission('scm:customer:promotion:manage'));
const promotionModal = ref<InstanceType<typeof CustomerPromotionModal>>();
const canDelete = computed(() => hasPermission('scm:customer:delete'));

/** 客户名称列被列设置隐藏时，详情需要另留一个入口。 */
const entryHidden = computed(() => !columns.value.some((column) => (column as {dataIndex?: string}).dataIndex === 'name'));

/** 低频和危险动作收进「更多」；详情默认走第一列，只有第一列被隐藏时才补回菜单项。 */
function rowActions(): ScmActionItem[] {
  return [
    {key: 'detail', label: '详情', hidden: !entryHidden.value},
    {key: 'promotion', label: '推广二维码', hidden: !canPromotion.value},
    {key: 'delete', label: '删除', danger: true, hidden: !canDelete.value},
  ];
}

function onRowAction(key: string, row: CustomerRow) {
  if (key === 'detail') {
    detail(row.customerId);
  } else if (key === 'promotion') {
    promotionModal.value?.openFor(row.customerId, row.name);
  } else if (key === 'delete') {
    // 菜单项不能挂 a-popconfirm，改用 Modal 确认。
    Modal.confirm({
      title: '删除客户',
      content: `确认删除「${row.name}」？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: () => remove(row),
    });
  }
}

onMounted(() => {
  // 恢复筛选条件，但回到第 1 页。
  Object.assign(filters, queryMemory.load(), {pageNum: 1});
  void load();
});
</script>

<style scoped>
/* 单元格内边距稍紧，保持单行密度 */
.customer-table :deep(.ant-table-tbody > tr > td) {
  padding: 6px 12px;
}
</style>
