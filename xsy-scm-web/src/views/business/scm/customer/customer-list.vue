<!--
  客户档案列表。
  - 不做批量删除：后端没有 `batchDelete` 端点，只有单条删除；
  - `requestId` 请求序号用于丢弃过期响应，否则快速翻页时旧响应会覆盖新响应。
-->
<template>
  <section aria-label="客户档案">
    <a-form class="smart-query-form" layout="inline">
      <a-row class="smart-query-form-row">
        <a-form-item label="关键字" class="smart-query-form-item">
          <a-input v-model:value="filters.keyword" allow-clear placeholder="编码 / 名称 / 联系人 / 电话" style="width: 240px" />
        </a-form-item>
        <a-form-item label="客户类型" class="smart-query-form-item">
          <CustomerTypeSelect v-model:value="filters.customerTypeId" width="180px" />
        </a-form-item>
        <a-form-item label="状态" class="smart-query-form-item">
          <SmartEnumSelect v-model:value="filters.status" enum-name="CUSTOMER_STATUS_ENUM" width="130px" />
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-space>
            <a-button type="primary" @click="search">查询</a-button>
            <a-button @click="reset">重置</a-button>
            <a-button type="link" @click="advanced = !advanced">{{ advanced ? '收起筛选' : '高级筛选' }}</a-button>
          </a-space>
        </a-form-item>
      </a-row>
      <a-row v-if="advanced" class="smart-query-form-row">
        <a-form-item label="结算方式" class="smart-query-form-item">
          <SmartEnumSelect v-model:value="filters.settleMode" enum-name="SETTLE_MODE_ENUM" width="140px"/>
        </a-form-item>
        <a-form-item label="上级集团" class="smart-query-form-item">
          <CustomerSelect v-model:value="filters.parentCustomerId" type-code="GROUP" width="220px"
                          placeholder="仅集团客户"/>
        </a-form-item>
      </a-row>
    </a-form>

    <a-card size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <a-button v-privilege="'scm:customer:add'" type="primary" @click="drawer?.open()">新增客户</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_CUSTOMER" :refresh="load"/>
      </a-row>
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
        <template #action>
          <a-button size="small" @click="load">重新加载</a-button>
        </template>
      </a-alert>
      <a-table
          class="customer-table"
          :data-source="rows"
          :columns="columns"
          row-key="customerId"
          :loading="loading"
          :pagination="false"
          :locale="{ emptyText }"
          size="small"
          bordered
          :scroll="{ x: 1090 }"
          @change="sortChanged"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'name'">
            <div class="scm-cell-stack">
              <a-button type="link" size="small" class="scm-cell-link" @click="detail(record.customerId)">
                {{ record.name }}
              </a-button>
              <span class="scm-cell-stack__sub scm-mono">{{ record.customerCode }}</span>
            </div>
          </template>
          <span v-else-if="column.dataIndex === 'customerTypeName'">{{ record.customerTypeName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'sellerName'">{{ record.sellerName || '—' }}</span>
          <template v-else-if="column.dataIndex === 'contact'">
            <div v-if="record.contactName || record.contactPhone" class="scm-cell-stack">
              <span v-if="record.contactName" class="scm-cell-stack__main">{{ record.contactName }}</span>
              <span v-if="record.contactPhone" class="scm-cell-stack__sub scm-cell-stack__sub--num">
                {{ record.contactPhone }}
              </span>
            </div>
            <span v-else>—</span>
          </template>
          <span v-else-if="column.dataIndex === 'settleMode'">{{ settleModeText(record.settleMode) }}</span>
          <span v-else-if="column.dataIndex === 'creditLimit'" class="scm-money">{{ formatAmountOrDash(record.creditLimit) }}</span>
          <ScmStatusTag v-else-if="column.dataIndex === 'status'" :color="statusColor(record.status)"
                        :label="statusText(record.status)"/>
          <a-space v-else-if="column.dataIndex === 'action'" :size="0" class="smart-table-operate scm-table-actions">
            <a-button v-privilege="'scm:customer:update'" type="link" size="small"
                      @click="drawer?.open(record.customerId)">编辑
            </a-button>
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

    <CustomerDrawer ref="drawer" @saved="load"/>

    <a-modal
        v-model:open="reassignVisible"
        title="改派业务员"
        :confirm-loading="reassignSaving"
        :ok-button-props="{ disabled: reassignSaving }"
        @ok="submitReassign"
    >
      <a-alert v-if="reassignError" type="error" :message="reassignError" show-icon class="smart-margin-bottom10"/>
      <p>客户：<strong>{{ reassignTarget?.name }}</strong>（{{ reassignTarget?.customerCode }}）</p>
      <p class="reassign-current">当前负责人：{{ reassignTarget?.sellerName || '未分配' }}</p>
      <a-form layout="vertical">
        <a-form-item label="新负责人">
          <EmployeeSelect v-model:value="reassignSeller" placeholder="留空即收回为未分配" width="100%"/>
          <div class="ant-form-item-extra">留空表示收回为未分配，未分配客户仅持分配权或全量范围者可见。</div>
        </a-form-item>
      </a-form>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
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
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import CustomerTypeSelect from '/@/components/business/scm/customer-type-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import CustomerDrawer from './components/customer-form-drawer.vue';
import {customerError} from './customer-errors';
import {hasPermission} from '../common/scm-permission';
import {formatAmountOrDash} from '/@/utils/scm-amount';
import {useQueryFilterMemory} from '/@/lib/query-filter-memory';

const router = useRouter();
const filters = reactive<CustomerQuery>({pageNum: 1, pageSize: 20});
const rows = ref<CustomerRow[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const advanced = ref(false);
const drawer = ref<InstanceType<typeof CustomerDrawer>>();
// 查询条件按「登录用户 + 本页」本地记忆；仅存浏览器，不落业务表。
const queryMemory = useQueryFilterMemory<CustomerQuery>('scm:customer:list');

/** 改派归属：独立动作、独立权限（scm:customer:assign），带乐观锁 version。 */
const reassignVisible = ref(false);
const reassignSaving = ref(false);
const reassignError = ref('');
const reassignTarget = ref<CustomerRow>();
/** EmployeeSelect 的 value prop 不接受 null（声明 [Number, Array]），用 undefined 桥接「收回为未分配」。 */
const reassignSeller = ref<number | undefined>(undefined);

/** 无全量客户范围权限时，空表可能是「授权范围内确实没有」而非「系统没有数据」，文案要能区分。 */
const canSeeAllCustomers = computed(() => hasPermission('scm:customer:scope:all:query'));
const emptyText = computed(() =>
  canSeeAllCustomers.value
    ? '暂无数据'
    : '当前仅显示您授权范围内的客户；若无数据，可能是尚未分配业务员或授权范围未配置，请联系管理员确认。'
);

function openReassign(row: CustomerRow) {
  reassignTarget.value = row;
  reassignSeller.value = row.sellerId ?? undefined;
  reassignError.value = '';
  reassignVisible.value = true;
}

async function submitReassign() {
  const row = reassignTarget.value;
  if (!row) {
    return;
  }
  reassignSaving.value = true;
  reassignError.value = '';
  try {
    await customerApi.reassignSeller({
      customerId: row.customerId,
      sellerId: reassignSeller.value ?? null,
      version: row.version,
    });
    message.success('归属已改派');
    reassignVisible.value = false;
    await load();
  } catch (e) {
    // 版本冲突（40921）走 customerError 的同一句话，提示刷新后重试而不是静默覆盖。
    reassignError.value = customerError(e);
  } finally {
    reassignSaving.value = false;
  }
}

/** 状态下拉的可选项：直接展开 SmartEnum，避免手写一份会和后端漂移的文案表。 */
const statusOptions = Object.values(CUSTOMER_STATUS_ENUM);

const statusText = (value: CustomerStatus): string => CUSTOMER_STATUS_ENUM[value]?.desc || value;
const settleModeText = (value: string): string => SETTLE_MODE_ENUM[value]?.desc || value;
const statusColor = (value: CustomerStatus): string => {
  if (value === 'COOPERATING') return 'green';
  if (value === 'SUSPENDED') return 'orange';
  if (value === 'BLACKLIST') return 'red';
  return 'default';
};

// 主列表只放「快速识别 + 状态判断 + 高频操作」用得上的列。
// 客户编码折成名称下方的次要文字；上级集团、更新时间仍在搜索、详情、编辑与导出里，不默认摊在列表上。
// 联系人与联系电话合并成一列，避免两个半空列挤占业务字段。
const columns = ref<TableColumnsType<CustomerRow>>([
  {title: '客户名称', dataIndex: 'name', width: 205, sorter: true},
  {title: '客户类型', dataIndex: 'customerTypeName', width: 105},
  {title: '业务员', dataIndex: 'sellerName', width: 105},
  {title: '联系方式', dataIndex: 'contact', width: 175},
  {title: '结算方式', dataIndex: 'settleMode', width: 110, align: 'center'},
  {title: '授信额度', dataIndex: 'creditLimit', width: 150, align: 'right'},
  {title: '状态', dataIndex: 'status', width: 90, align: 'center', sorter: true},
  {title: '操作', dataIndex: 'action', width: 150, align: 'center', fixed: 'right'},
]);

let requestId = 0;

async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const response = await customerApi.query({...filters});
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
    status: undefined,
    settleMode: undefined,
    parentCustomerId: undefined,
    sortItemList: undefined,
  });
  queryMemory.clear();
  void load();
}

// 排序白名单：只映射后端 CustomerQueryService.SORTABLE 允许的四列。
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

/** `item.value` 来自 SmartEnum 展开，运行时必然是后端枚举码之一。 */
async function changeStatus(row: CustomerRow, status: string) {
  try {
    await customerApi.updateStatus({
      customerId: row.customerId,
      version: row.version,
      status: status as CustomerStatus
    });
    message.success('客户状态已更新');
    await load();
  } catch (e) {
    error.value = customerError(e);
  }
}

async function remove(row: CustomerRow) {
  try {
    await customerApi.delete({customerId: row.customerId, version: row.version});
    message.success('客户已删除');
    await load();
  } catch (e) {
    error.value = customerError(e);
  }
}

/** 「更多」里的菜单项挂不上 `v-privilege` 指令，改用同一口径的 hasPermission 裁剪。 */
const canAssign = computed(() => hasPermission('scm:customer:assign'));
const canDelete = computed(() => hasPermission('scm:customer:delete'));

/** 操作列常驻「编辑 + 状态」，其余低频与危险动作收进「更多」，把宽度留给业务字段。 */
function rowActions(): ScmActionItem[] {
  return [
    {key: 'detail', label: '详情'},
    {key: 'reassign', label: '改派业务员', hidden: !canAssign.value},
    {key: 'delete', label: '删除', danger: true, hidden: !canDelete.value},
  ];
}

function onRowAction(key: string, row: CustomerRow) {
  if (key === 'detail') {
    detail(row.customerId);
  } else if (key === 'reassign') {
    openReassign(row);
  } else if (key === 'delete') {
    // 菜单项挂不上 a-popconfirm，二次确认改由 Modal 承担，语义与原 popconfirm 一致。
    Modal.confirm({
      title: '删除客户',
      content: `确认删除「${row.name}」（${row.customerCode}）？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: () => remove(row),
    });
  }
}

onMounted(() => {
  // 恢复上次筛选条件与分页大小，但强制回到第 1 页：记忆是便利，不该把用户带回深处的旧页码。
  Object.assign(filters, queryMemory.load(), {pageNum: 1});
  void load();
});
</script>

<style scoped>
/* 行高收到 48~52px：本表有两处双行复合单元（名称+编码、联系人+电话），
   size="small" 默认内边距（8px）叠上两行内容会到 55px+，整屏能看到的客户数变少。
   两行的字号 / 字重 / 行距由公共类 .scm-cell-stack 统一（14px/500/20px 与 12px/16px），
   这里只收紧单元格内边距，不重写行内排版 —— 否则客户、供应商两个列表会长得不一样。

   名称入口的样式走公共类 .scm-cell-link：它同时保证名称是主行那一档的深色文字
   （而不是 antd 默认的链接绿），且悬停才给链接反馈。 */
.customer-table :deep(.ant-table-tbody > tr > td) {
  padding: 6px 12px;
}
</style>
