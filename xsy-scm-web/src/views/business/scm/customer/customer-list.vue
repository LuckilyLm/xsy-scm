<!--
  * 客户档案列表
  *
  * 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/customer/customer-list.vue
  * （Copy First + Adapt，376 行 → 剪枝 + 类型补全）。
  *
  * 剪枝（W2 范围外）：
  * - 删除「账户余额」列与相关展示（财务域，W2 不做）；
  * - 删除**批量删除**（Target Design Q14：W2 只做单条删除，后端也没有 batchDelete 端点）；
  * - 删除 `:row-selection` / `onSelectChange` / `selectedRowKeyList`（随批量删除一起移除）；
  * - 删除客户等级、区域、二维码、可见性等列。
  *
  * 适配：
  * - 删除全部 `resizable: true` / `@resizeColumn` / `handleResizeColumn` —— V2 没有 `TableHeaderCell`，
  *   全库 0 命中（Target Design §5.2）；
  * - 权限码 `customer:xxx` → `scm:customer:xxx`；
  * - 状态变更从 C 的 `Modal.confirm` 改为 `a-dropdown` + 二次确认，
  *   因为 V2 有 4 个状态（潜在 / 合作中 / 暂停合作 / 黑名单），不是 C 的「启用 / 停用」二态；
  * - `columns` 显式标注 `TableColumnsType<CustomerRow>`（C 是裸数组，V2 严格模式下是 TS2322）；
  * - 补 `requestId` 请求序号，避免快速翻页时旧响应覆盖新响应。
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
          :data-source="rows"
          :columns="columns"
          row-key="customerId"
          :loading="loading"
          :pagination="false"
          :locale="{ emptyText }"
          size="small"
          bordered
          :scroll="{ x: 1860 }"
          @change="sortChanged"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'name'">
            <a-button type="link" @click="detail(record.customerId)">{{ record.name }}</a-button>
          </template>
          <span v-else-if="column.dataIndex === 'parentCustomerName'">{{ record.parentCustomerName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'sellerName'">{{ record.sellerName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'contactName'">{{ record.contactName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'contactPhone'">{{ record.contactPhone || '—' }}</span>
          <span v-else-if="column.dataIndex === 'settleMode'">{{ settleModeText(record.settleMode) }}</span>
          <span v-else-if="column.dataIndex === 'creditLimit'" class="amount">{{ record.creditLimit ?? '—' }}</span>
          <a-tag v-else-if="column.dataIndex === 'status'" :color="statusColor(record.status)">
            {{ statusText(record.status) }}
          </a-tag>
          <a-space v-else-if="column.dataIndex === 'action'" :size="0" class="smart-table-operate">
            <a-button type="link" size="small" @click="detail(record.customerId)">详情</a-button>
            <a-button v-privilege="'scm:customer:update'" type="link" size="small"
                      @click="drawer?.open(record.customerId)">编辑
            </a-button>
            <a-button v-privilege="'scm:customer:assign'" type="link" size="small" @click="openReassign(record)">
              改派
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
            <a-popconfirm title="确认删除此客户？" @confirm="remove(record)">
              <a-button v-privilege="'scm:customer:delete'" type="link" danger size="small">删除</a-button>
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
import {message} from 'ant-design-vue';
import type {TableColumnsType, TableProps} from 'ant-design-vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import type {CustomerQuery, CustomerRow, CustomerStatus, ScmId} from '/@/types/business/scm/customer';
import {CUSTOMER_STATUS_ENUM, SETTLE_MODE_ENUM} from '/@/constants/business/scm/customer-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import CustomerTypeSelect from '/@/components/business/scm/customer-type-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import CustomerDrawer from './components/customer-form-drawer.vue';
import {customerError} from './customer-errors';
import {hasPermission} from '../common/scm-permission';
import {datetime} from '../common/scm-display';
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

const columns = ref<TableColumnsType<CustomerRow>>([
  {title: '客户编码', dataIndex: 'customerCode', width: 150, sorter: true},
  {title: '客户名称', dataIndex: 'name', width: 200, sorter: true},
  {title: '客户类型', dataIndex: 'customerTypeName', width: 110},
  {title: '上级集团', dataIndex: 'parentCustomerName', width: 160},
  {title: '业务员', dataIndex: 'sellerName', width: 110},
  {title: '联系人', dataIndex: 'contactName', width: 110},
  {title: '联系电话', dataIndex: 'contactPhone', width: 140},
  {title: '结算方式', dataIndex: 'settleMode', width: 110, align: 'center'},
  {title: '授信额度', dataIndex: 'creditLimit', width: 140, align: 'right'},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center', sorter: true},
  {title: '更新时间', dataIndex: 'updatedAt', width: 190, sorter: true, customRender: ({text}) => datetime(text)},
  {title: '操作', dataIndex: 'action', width: 280, align: 'right', fixed: 'right'},
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

onMounted(() => {
  // 恢复上次筛选条件与分页大小，但强制回到第 1 页：记忆是便利，不该把用户带回深处的旧页码。
  Object.assign(filters, queryMemory.load(), {pageNum: 1});
  void load();
});
</script>

<style scoped>
.amount {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
