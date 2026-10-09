<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="订单号 / 客户" class="smart-query-form-item">
        <a-input v-model:value="queryForm.keyword" @pressEnter="onSearch" placeholder="名称或订单号" allow-clear/>
      </a-form-item>
      <a-form-item label="订单来源" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_ORDER_SOURCE_ENUM" v-model:value="queryForm.orderSource" width="140px"/>
      </a-form-item>
      <a-form-item label="订单状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_ORDER_STATUS_ENUM" v-model:value="queryForm.status" width="140px"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:order:query'">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>
  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button type="primary" v-privilege="'scm:order:add'" @click="drawer?.open()">新建订单</a-button>
        <a-button v-privilege="'scm:order:import'" @click="importModal?.open()">导入订单</a-button>
        <a-button danger v-privilege="'scm:order:delete'" :disabled="!selected.length" @click="batchDelete">
          批量删除草稿
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="602" :refresh="queryData"/>
      </div>
    </a-row>
    <a-table id="order-table" size="small" :data-source="tableData" :columns="columns" row-key="orderId" bordered
             :loading="loading" :pagination="false" :scroll="{x:1190}"
             :row-selection="{selectedRowKeys:selected,onChange:(keys:(string|number)[])=>selected=keys,getCheckboxProps:(r:Order)=>({disabled:r.status!=='DRAFT'})}">
      <template #bodyCell="{record,column}">
        <template v-if="column.dataIndex==='orderNo'"><a @click="detail?.open(record.orderId)">{{ record.orderNo }}</a>
        </template>
        <template v-else-if="column.dataIndex==='customerNameSnapshot'">{{ record.customerNameSnapshot || '—' }}</template>
        <template v-else-if="column.dataIndex==='customerCodeSnapshot'">
          <span class="scm-mono">{{ record.customerCodeSnapshot || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex==='status'">
          <ScmStatusTag :tone="statusTone(record.status)"
                        :label="SCM_ORDER_STATUS_ENUM[record.status]?.desc"/>
        </template>
        <template v-else-if="column.dataIndex==='orderSource'">{{ SCM_ORDER_SOURCE_ENUM[record.orderSource]?.desc }}
        </template>
        <template v-else-if="column.dataIndex==='orderedTotalAmount'">
          <span class="scm-money">{{ amount(record.orderedTotalAmount, true) }}</span>
        </template>
        <template v-else-if="column.dataIndex==='settlementTotalAmount'">
          <span class="scm-money">{{ amount(record.settlementTotalAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex==='action'">
          <!-- 行内只留当前状态最高频的动作，其余收进「更多」；动作集合与原行内按钮一一对应 -->
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <template v-if="record.status==='DRAFT'">
              <a-button type="link" size="small" v-privilege="'scm:order:update'"
                        @click="drawer?.open(record.orderId)">编辑
              </a-button>
              <a-button type="link" size="small" v-privilege="'scm:order:submit'"
                        @click="submit(record)">提交
              </a-button>
            </template>
            <template v-else-if="record.status==='CONFIRMED'">
              <a-button type="link" size="small" @click="detail?.open(record.orderId)">详情</a-button>
              <a-button type="link" size="small" v-privilege="'scm:order:reserve-stock'"
                        @click="reserveStock(record)">预留库存
              </a-button>
            </template>
            <template v-else>
              <a-button type="link" size="small" @click="detail?.open(record.orderId)">详情</a-button>
            </template>
            <ScmActionMore :actions="rowActions(record)" @select="onRowAction($event, record)"/>
          </a-space>
        </template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination show-size-changer show-quick-jumper v-model:current="queryForm.pageNum"
                    v-model:page-size="queryForm.pageSize" :total="total" @change="queryData"
                    :show-total="(n:number)=>`共${n}条`"/>
    </div>
  </a-card>
  <OrderForm ref="drawer" @saved="queryData"/>
  <OrderImportModal ref="importModal" @saved="queryData"/>
  <OrderDetail ref="detail" @saved="queryData"/>
</template>
<script setup lang="ts">
import {computed, nextTick, onMounted, reactive, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {orderApi} from '/@/api/business/scm/order-api';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import {SCM_ORDER_STATUS_ENUM, SCM_ORDER_SOURCE_ENUM} from '/@/constants/business/scm/order-const';
import type {Order, Query} from './order-types';
import {amount} from './order-form-model';
import {orderError} from './order-errors';
import {hasPermission} from '../common/scm-permission';
import OrderForm from './components/order-form-drawer.vue';
import OrderImportModal from './components/order-import-modal.vue';
import OrderDetail from './order-detail.vue';

const queryForm = reactive<Query>({pageNum: 1, pageSize: 20}), tableData = ref<Order[]>([]), total = ref(0),
    loading = ref(false), error = ref(''), selected = ref<(string | number)[]>([]);
const drawer = ref<InstanceType<typeof OrderForm>>(), importModal = ref<InstanceType<typeof OrderImportModal>>(),
    detail = ref<InstanceType<typeof OrderDetail>>();
const route = useRoute();
const router = useRouter();

// 消费一次快捷入口动作，避免刷新或返回列表时再次打开新建表单。
watch(() => route.query.action, async (action) => {
  if (route.path !== '/order/order-list' || action !== 'create') return;
  const query = {...route.query};
  delete query.action;
  await router.replace({path: route.path, query});
  await nextTick();
  if (hasPermission('scm:order:query') && hasPermission('scm:order:add')) {
    drawer.value?.open();
  }
}, {immediate: true, flush: 'post'});
let requestId = 0;
type OrderListColumn = TableColumnsType<Order>[number] & {showFlag?: boolean};
const columns = ref<OrderListColumn[]>([
  {title: '订单号', dataIndex: 'orderNo', width: 190},
  {title: '客户名称', dataIndex: 'customerNameSnapshot', width: 200},
  {title: '客户编码', dataIndex: 'customerCodeSnapshot', width: 125, showFlag: false},
  {title: '来源', dataIndex: 'orderSource', align: 'center', width: 100},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '下单金额', dataIndex: 'orderedTotalAmount', align: 'right', width: 130},
  {title: '结算金额', dataIndex: 'settlementTotalAmount', align: 'right', width: 130},
  {title: '期望配送时间', dataIndex: 'expectDeliveryTime', width: 180},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 160},
]);

/** 草稿/待确认=待处理，已确认=处理中，已取消=失效。 */
const STATUS_TONE: Record<string, ScmStatusTone> = {
  DRAFT: 'warning',
  PENDING: 'warning',
  CONFIRMED: 'processing',
  CANCELLED: 'neutral',
};
const statusTone = (value?: string | null): ScmStatusTone => STATUS_TONE[value ?? ''] ?? 'neutral';

/** 「更多」里的菜单项挂不上 `v-privilege` 指令，改用同一口径的 hasPermission 裁剪。 */
const canAdd = computed(() => hasPermission('scm:order:add'));
const canDelete = computed(() => hasPermission('scm:order:delete'));

/**
 * 行内常驻当前状态最高频的动作（草稿：编辑/提交；已确认：详情/预留库存），其余收进「更多」。
 * 动作集合与原行内按钮一一对应，只按频率重新分组，不新增也不隐藏业务动作。
 */
function rowActions(row: Order): ScmActionItem[] {
  const draft = row.status === 'DRAFT';
  const confirmed = row.status === 'CONFIRMED';
  return [
    {key: 'detail', label: '详情', hidden: draft || confirmed},
    {key: 'reuse', label: '复用为新单', hidden: !(confirmed && canAdd.value)},
    {key: 'delete', label: '删除', danger: true, hidden: !(draft && canDelete.value)},
  ];
}

function onRowAction(key: string, row: Order) {
  // `Order.orderId` 在 VO 里可选，打开详情 / 复用为新单都要求确定的 Id，这里先判空。
  if (row.orderId != null && key === 'detail') {
    detail?.value?.open(row.orderId);
  } else if (row.orderId != null && key === 'reuse') {
    drawer?.value?.openFromHistory(row.orderId);
  } else if (key === 'delete') {
    remove(row);
  }
}

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await orderApi.query(queryForm);
    if (id === requestId) {
      tableData.value = r.data.list;
      total.value = r.data.total;
      selected.value = [];
    }
  } catch (e) {
    if (id === requestId) error.value = orderError(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.keyword = undefined;
  queryForm.status = undefined;
  queryForm.orderSource = undefined;
  onSearch();
}

function submit(r: Order) {
  Modal.confirm({
    title: '提交订单并锁定价格？', onOk: async () => {
      try {
        await orderApi.submit({orderId: r.orderId, version: r.version});
        await queryData();
      } catch (e) {
        error.value = orderError(e);
        throw e;
      }
    }
  });
}

function remove(r: Order) {
  Modal.confirm({
    title: '删除这张草稿订单？', okType: 'danger', onOk: async () => {
      try {
        await orderApi.delete({orderId: r.orderId, version: r.version});
        await queryData();
      } catch (e) {
        error.value = orderError(e);
        throw e;
      }
    }
  });
}

/**
 * 预留库存。
 *
 * 显式操作而非确认时自动预留：本业务的库存在订单确认之后才产生，
 * 挂在确认上会让「先接单 → 再采购」链路无法运转（见 docs/decisions.md）。
 * 货到之后由业务人员对本单执行；任一行可用量不足则整体失败（41011）。
 */
function reserveStock(r: Order) {
  Modal.confirm({
    title: '为该订单预留库存？',
    content: '将按订单明细的实数量占用可用量（可用量 = 现有量 − 预留量）。任一行不足则整体失败，不会只占一半。',
    okText: '预留',
    onOk: async () => {
      try {
        await orderApi.reserveStock(r.orderId!);
        await queryData();
      } catch (e) {
        error.value = orderError(e);
        throw e;
      }
    }
  });
}

function batchDelete() {
  Modal.confirm({
    title: '删除所选草稿？', okType: 'danger', onOk: async () => {
      try {
        await orderApi.batchDelete(tableData.value.filter(o => selected.value.includes(o.orderId!)).map(o => ({
          orderId: o.orderId,
          version: o.version
        })));
        await queryData();
      } catch (e) {
        error.value = orderError(e);
        throw e;
      }
    }
  });
}

onMounted(queryData);
</script>
