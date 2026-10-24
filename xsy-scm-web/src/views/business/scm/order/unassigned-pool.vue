<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="订单号 / 客户" class="smart-query-form-item">
        <a-input v-model:value="queryForm.keyword" placeholder="订单号或客户名称" allow-clear @pressEnter="search" />
      </a-form-item>
      <a-form-item label="订单状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_ORDER_STATUS_ENUM" v-model:value="queryForm.status" width="140px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="search">查询</a-button>
          <a-button @click="reset">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action><a-button @click="load">重试</a-button></template>
  </a-alert>
  <a-card size="small" :bordered="false">
    <a-table
      size="small"
      :data-source="rows"
      :columns="columns"
      row-key="orderId"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'orderNo'">
          <span class="scm-mono">{{ record.orderNo }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="statusTone(record.status)" :label="SCM_ORDER_STATUS_ENUM[record.status]?.desc || record.status" />
        </template>
        <template v-else-if="column.dataIndex === 'orderedTotalAmount'">
          <span class="scm-money">{{ amount(record.orderedTotalAmount, true) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'createdAt'">{{
          record.createdAt ? dayjs(record.createdAt).format('YYYY-MM-DD HH:mm') : '—'
        }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button v-if="canClaim" type="link" size="small" @click="claim(record)">接单</a-button>
            <a-button type="link" size="small" @click="openAssignment(record)">指派</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
        show-size-changer
        :page-size-options="SCM_PAGE_SIZE_OPTIONS"
        show-quick-jumper
        v-model:current="queryForm.pageNum"
        v-model:page-size="queryForm.pageSize"
        :total="total"
        @change="load"
        :show-total="(count: number) => `共${count}条`"
      />
    </div>
  </a-card>

  <a-modal v-model:open="assignmentOpen" title="指派订单业务员" :confirm-loading="assigning" ok-text="确认指派" @ok="assignSelected">
    <p>订单 {{ selectedOrder?.orderNo }} · {{ selectedOrder?.customerNameSnapshot }}</p>
    <a-alert v-if="assigneeError" :message="assigneeError" type="error" show-icon>
      <template #action><a-button @click="loadAssignees">重试</a-button></template>
    </a-alert>
    <a-form-item label="业务员" required>
      <a-select v-model:value="selectedSellerId" show-search option-filter-prop="label" placeholder="选择可处理订单的在职员工" style="width: 100%">
        <a-select-option v-for="employee in assignees" :key="employee.employeeId" :value="employee.employeeId" :label="employee.actualName">
          {{ employee.actualName }}
        </a-select-option>
      </a-select>
    </a-form-item>
    <a-alert v-if="assignError" :message="assignError" type="error" show-icon />
  </a-modal>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, onMounted, reactive, ref} from 'vue';
import dayjs from 'dayjs';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {orderApi} from '/@/api/business/scm/order-api';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {SCM_ORDER_STATUS_ENUM} from '/@/constants/business/scm/order-const';
import type {OrderPoolAssignee, OrderPoolQuery, OrderPoolRow} from './order-types';
import {amount} from './order-form-model';
import {orderError} from './order-errors';
import {useUserStore} from '/@/store/modules/system/user';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';

const queryForm = reactive<OrderPoolQuery>({pageNum: 1, pageSize: 20});
const rows = ref<OrderPoolRow[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const assignees = ref<OrderPoolAssignee[]>([]);
const assigneeError = ref('');
const assignmentOpen = ref(false);
const selectedOrder = ref<OrderPoolRow | null>(null);
const selectedSellerId = ref<number>();
const assigning = ref(false);
const assignError = ref('');
const userStore = useUserStore();
const canClaim = computed(() => assignees.value.some((employee) => String(employee.employeeId) === String(userStore.employeeId)));
let requestId = 0;

const columns: TableColumnsType<OrderPoolRow> = [
  {title: '订单号', dataIndex: 'orderNo', width: 190},
  {title: '客户名称', dataIndex: 'customerNameSnapshot', width: 210},
  {title: '订单状态', dataIndex: 'status', align: 'center', width: 96},
  {title: '预计订单金额', dataIndex: 'orderedTotalAmount', width: 148},
  {title: '下单时间', dataIndex: 'createdAt', width: 180},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 150},
];

function statusTone(status: string): ScmStatusTone {
  return status === 'CONFIRMED' ? 'processing' : 'warning';
}

async function load() {
  const current = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const response = await orderApi.unassignedPool(queryForm, {suppressGlobalErrorMessage: true});
    if (current === requestId) {
      rows.value = response.data.list;
      total.value = response.data.total;
    }
  } catch (failure) {
    if (current === requestId) error.value = orderError(failure);
  } finally {
    if (current === requestId) loading.value = false;
  }
}

async function loadAssignees() {
  assigneeError.value = '';
  try {
    const response = await orderApi.poolAssignees({suppressGlobalErrorMessage: true});
    assignees.value = response.data;
  } catch (failure) {
    assigneeError.value = orderError(failure);
    assignees.value = [];
  }
}

function search() {
  queryForm.pageNum = 1;
  load();
}

function reset() {
  queryForm.keyword = undefined;
  queryForm.status = undefined;
  search();
}

function claim(order: OrderPoolRow) {
  Modal.confirm({
    title: '接下这张订单？',
    content: `订单 ${order.orderNo} 将进入你的个人订单范围。`,
    okText: '确认接单',
    onOk: async () => {
      await orderApi.claimPoolOrder({orderId: order.orderId, version: order.version});
      message.success('已接单');
      await load();
    },
  });
}

function openAssignment(order: OrderPoolRow) {
  selectedOrder.value = order;
  selectedSellerId.value = undefined;
  assignError.value = '';
  assignmentOpen.value = true;
  if (!assignees.value.length) loadAssignees();
}

async function assignSelected() {
  if (!selectedOrder.value || selectedSellerId.value == null) {
    assignError.value = '请选择业务员';
    return;
  }
  assigning.value = true;
  assignError.value = '';
  try {
    await orderApi.assignPoolOrder({
      orderId: selectedOrder.value.orderId,
      version: selectedOrder.value.version,
      sellerId: selectedSellerId.value,
    });
    assignmentOpen.value = false;
    message.success('已指派');
    await load();
  } catch (failure) {
    assignError.value = orderError(failure);
  } finally {
    assigning.value = false;
  }
}

onMounted(() => {
  load();
  loadAssignees();
});
</script>
