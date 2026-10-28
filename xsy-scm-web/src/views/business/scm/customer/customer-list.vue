<!-- 客户档案列表：只有单条删除；翻页时用请求序号丢弃过期响应。 -->
<template>
  <section aria-label="客户档案">
    <a-form class="smart-query-form" layout="inline">
      <a-row class="smart-query-form-row">
        <a-form-item label="关键字" class="smart-query-form-item">
          <a-input v-model:value="filters.keyword" allow-clear placeholder="客户名称 / 联系人 / 电话" style="width: 240px" />
        </a-form-item>
        <a-form-item label="客户类型" class="smart-query-form-item">
          <CustomerTypeSelect v-model:value="filters.customerTypeId" width="180px" />
        </a-form-item>
        <a-form-item label="客户分类" class="smart-query-form-item">
          <BusinessCategoryTreeSelect v-model:value="filters.businessCategoryId" scope="CUSTOMER" style="width: 160px" />
        </a-form-item>
        <a-form-item label="客户分组" class="smart-query-form-item">
          <CustomerGroupSelect v-model:value="filters.customerGroupId" width="160px" />
        </a-form-item>
        <a-form-item label="状态" class="smart-query-form-item">
          <SmartEnumSelect v-model:value="filters.status" enum-name="CUSTOMER_STATUS_ENUM" width="130px" />
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-space :size="10" wrap>
            <a-button type="primary" @click="search">查询</a-button>
            <a-button @click="reset">重置</a-button>
            <a-button type="link" @click="advanced = !advanced">{{ advanced ? '收起筛选' : '高级筛选' }}</a-button>
          </a-space>
        </a-form-item>
      </a-row>
      <a-row v-if="advanced" class="smart-query-form-row">
        <a-form-item label="结算方式" class="smart-query-form-item">
          <SmartEnumSelect v-model:value="filters.settleMode" enum-name="SETTLE_MODE_ENUM" width="140px" />
        </a-form-item>
        <a-form-item label="上级集团" class="smart-query-form-item">
          <CustomerSelect v-model:value="filters.parentCustomerId" type-code="GROUP" width="220px" placeholder="仅集团客户" />
        </a-form-item>
      </a-row>
    </a-form>

    <a-card size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <a-button v-privilege="'scm:customer:add'" type="primary" @click="drawer?.open()">新增客户</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_CUSTOMER" :refresh="load" />
      </a-row>
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
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
          <span v-else-if="column.dataIndex === 'customerTypeName'">{{ record.customerTypeName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'businessCategoryName'">{{ record.businessCategoryName || '未分类' }}</span>
          <span v-else-if="column.dataIndex === 'customerGroupName'">{{ record.customerGroupName || '未分组' }}</span>
          <span v-else-if="column.dataIndex === 'sellerName'">{{ record.sellerName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'contactName'">{{ record.contactName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'contactPhone'" class="scm-mono">{{ record.contactPhone || '—' }}</span>
          <span v-else-if="column.dataIndex === 'settleMode'">{{ settleModeText(record.settleMode) }}</span>
          <span v-else-if="column.dataIndex === 'creditLimit'" class="scm-money">{{ formatAmountOrDash(record.creditLimit) }}</span>
          <ScmStatusTag v-else-if="column.dataIndex === 'status'" :color="statusColor(record.status)" :label="statusText(record.status)" />
          <a-space v-else-if="column.dataIndex === 'action'" :size="4" class="smart-table-operate scm-table-actions">
            <a-button v-privilege="'scm:customer:update'" type="link" size="small" @click="drawer?.open(record.customerId)">编辑 </a-button>
            <a-dropdown>
              <a-button v-privilege="'scm:customer:status'" type="link" size="small">状态</a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item
                    v-for="item in statusOptions"
                    :key="item.value"
                    :disabled="item.value === record.status"
                    @click="changeStatus(record, item.value)"
                  >
                    {{ item.desc }}
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
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

    <CustomerDrawer ref="drawer" @saved="load" />
    <CustomerPromotionModal ref="promotionModal" />

    <a-modal
      v-model:open="reassignVisible"
      title="改派业务员"
      :confirm-loading="reassignSaving"
      :ok-button-props="{disabled: reassignSaving}"
      @ok="submitReassign"
    >
      <p>
        客户：<strong>{{ reassignTarget?.name }}</strong>
      </p>
      <p class="reassign-current">当前负责人：{{ reassignTarget?.sellerName || '未分配' }}</p>
      <a-form layout="vertical">
        <a-form-item>
          <template #label>
            新负责人
            <ScmFieldHelp label="新负责人" text="留空则取消当前业务员的负责关系；只有有权分配客户的人员才能重新分配" />
          </template>
          <EmployeeSelect v-model:value="reassignSeller" placeholder="留空即收回为未分配" width="100%" />
        </a-form-item>
      </a-form>
    </a-modal>
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
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import CustomerPromotionModal from '/@/views/business/scm/customer/components/customer-promotion-modal.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import CustomerTypeSelect from '/@/components/business/scm/customer-type-select/index.vue';
import BusinessCategoryTreeSelect from '/@/components/business/scm/business-category-tree-select/index.vue';
import CustomerGroupSelect from '/@/components/business/scm/customer-group-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import CustomerDrawer from './components/customer-form-drawer.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
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

/** 改派业务员：独立权限，带乐观锁版本。 */
const reassignVisible = ref(false);
const reassignSaving = ref(false);
const reassignTarget = ref<CustomerRow>();
/** 收回负责关系时用 undefined；EmployeeSelect 不接受 null。 */
const reassignSeller = ref<number | undefined>(undefined);

/** 没有全量权限时空表要说明，可能只是授权范围内没有数据。 */
const canSeeAllCustomers = computed(() => hasPermission('scm:customer:scope:all:query'));
const emptyText = computed(() =>
  canSeeAllCustomers.value ? '暂无数据' : '当前仅显示您授权范围内的客户；若无数据，可能是尚未分配业务员或授权范围未配置，请联系管理员确认。'
);

function openReassign(row: CustomerRow) {
  reassignTarget.value = row;
  reassignSeller.value = row.sellerId ?? undefined;
  reassignVisible.value = true;
}

async function submitReassign() {
  const row = reassignTarget.value;
  if (!row) {
    return;
  }
  reassignSaving.value = true;
  try {
    await customerApi.reassignSeller({
      customerId: row.customerId,
      sellerId: reassignSeller.value ?? null,
      version: row.version,
    });
    message.success('归属已改派');
    reassignVisible.value = false;
    await load();
  } catch {
    // 改派失败由全局 toast 提示
  } finally {
    reassignSaving.value = false;
  }
}

/** 状态选项直接来自枚举，不另写文案表。 */
const statusOptions = Object.values(CUSTOMER_STATUS_ENUM);

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
  {title: '客户类型', dataIndex: 'customerTypeName', width: 120},
  {title: '客户分类', dataIndex: 'businessCategoryName', width: 120, align: 'center'},
  {title: '客户分组', dataIndex: 'customerGroupName', width: 140},
  {title: '业务员', dataIndex: 'sellerName', width: 110},
  {title: '联系人', dataIndex: 'contactName', width: 120},
  {title: '联系电话', dataIndex: 'contactPhone', width: 140},
  {title: '结算方式', dataIndex: 'settleMode', width: 112, align: 'center'},
  {title: '授信额度', dataIndex: 'creditLimit', width: 130},
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
    customerTypeId: undefined,
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

/** 值来自枚举，必然是后端枚举码。 */
async function changeStatus(row: CustomerRow, status: string) {
  try {
    await customerApi.updateStatus({
      customerId: row.customerId,
      version: row.version,
      status: status as CustomerStatus,
    });
    message.success('客户状态已更新');
    await load();
  } catch {
    // 状态更新失败由全局 toast 提示
  }
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
const canAssign = computed(() => hasPermission('scm:customer:assign'));
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
    {key: 'reassign', label: '改派业务员', hidden: !canAssign.value},
    {key: 'delete', label: '删除', danger: true, hidden: !canDelete.value},
  ];
}

function onRowAction(key: string, row: CustomerRow) {
  if (key === 'detail') {
    detail(row.customerId);
  } else if (key === 'promotion') {
    promotionModal.value?.openFor(row.customerId, row.name);
  } else if (key === 'reassign') {
    openReassign(row);
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
