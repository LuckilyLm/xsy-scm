<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/order/order-refund-list.vue
复制日期：2026-09-16。Copy First + Adapt。
剪枝：履约/支付/裸ID/独立明细写入口/列拖拽。
适配：四状态、API、权限、四位定点、NULL、version、幂等、错误重试。
验收：W4 单测、TS 棘轮与 Playwright。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.keyword" @pressEnter="onSearch" allow-clear/>
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_ORDER_RETURN_STATUS_ENUM" v-model:value="queryForm.status" width="160px"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:order:return:query'">查询</a-button>
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
      <div class="smart-table-operate-block">退货</div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="603" :refresh="queryData"/>
      </div>
    </a-row>
    <a-table id="order-return-table" size="small" :data-source="tableData" :columns="columns" row-key="returnId"
             :loading="loading" bordered :pagination="false" :scroll="{x:1100}">
      <template #bodyCell="{record,column,text}">
        <template v-if="column.dataIndex==='status'">{{ SCM_ORDER_RETURN_STATUS_ENUM[text]?.desc }}</template>
        <template v-else-if="['approvedAmount','refundAmount'].includes(column.dataIndex)">{{ amount(text) }}</template>
        <template v-else-if="column.dataIndex==='action'">
          <div class="smart-table-operate">
            <a-button type="link" v-privilege="'scm:order:return:query'" @click="showDetail(record.returnId)">详情</a-button>
            <a-button type="link" v-privilege="'scm:order:return:approve'" v-if="record.status==='PENDING'"
                      @click="edit(record,'approve')">批准
            </a-button>
            <a-button type="link" v-privilege="'scm:order:return:receive'" v-if="record.status==='APPROVED'"
                      @click="edit(record,'receive')">实物接收
            </a-button>
            <a-button type="link" v-privilege="'scm:order:return:reject'" v-if="record.status==='PENDING'"
                      @click="edit(record,'reject')">驳回
            </a-button>
            <a-button type="link" danger v-privilege="'scm:order:return:cancel'" v-if="record.status==='PENDING'"
                      @click="edit(record,'cancel')">取消
            </a-button>
          </div>
        </template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination show-size-changer show-quick-jumper v-model:current="queryForm.pageNum"
                    v-model:page-size="queryForm.pageSize" :total="total" @change="queryData"
                    :show-total="(n:number)=>`共${n}条`"/>
    </div>
  </a-card>
  <a-drawer v-model:open="detailOpen" title="退货单详情" width="min(850px,96vw)">
    <a-alert v-if="detailError" type="error" show-icon :message="detailError">
      <template #action><a-button @click="showDetail(detailId)">重试</a-button></template>
    </a-alert>
    <a-spin :spinning="detailLoading">
      <template v-if="detail">
        <a-descriptions bordered :column="1" size="small">
          <a-descriptions-item label="退货单号">{{ detail.returnNo }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ SCM_ORDER_RETURN_STATUS_ENUM[detail.status]?.desc ?? detail.status }}</a-descriptions-item>
          <a-descriptions-item label="申请原因">{{ detail.reason || '—' }}</a-descriptions-item>
          <a-descriptions-item label="处理原因">{{ detail.decisionReason || '—' }}</a-descriptions-item>
        </a-descriptions>
        <a-table :data-source="detail.items" row-key="orderItemId" :pagination="false" :scroll="{x: 600}"
                 :columns="[{title:'商品',dataIndex:'productName'},{title:'单位',dataIndex:'unit'},
                   {title:'申请数量',dataIndex:'requestedQuantity'},{title:'批准数量',dataIndex:'approvedQuantity'},
                   {title:'已接收数量',dataIndex:'receivedQuantity'}]"/>
      </template>
    </a-spin>
  </a-drawer>
  <a-modal :open="visible" :title="action==='approve'?'审核退货':action==='receive'?'退货实物接收':action==='reject'?'驳回退货':'取消退货'" width="min(800px,96vw)"
           :confirm-loading="saving" @ok="save" @cancel="visible=false">
    <template v-if="active">
      <a-alert v-if="error" :message="error" type="error"/>
      <a-table v-if="action==='approve'" :data-source="active.items"
               :columns="[{title:'申请数量',dataIndex:'requestedQuantity'},{title:'锁定单价',dataIndex:'lockedUnitPrice'},{title:'批准数量',dataIndex:'approvedQuantity'}]"
               row-key="orderItemId" :pagination="false">
        <template #bodyCell="{record,column}">
          <a-input-number v-if="column.dataIndex==='approvedQuantity'" v-model:value="record.approvedQuantity"
                          string-mode :precision="4" :min="'0'" :max="record.requestedQuantity" aria-label="批准数量"/>
        </template>
      </a-table>
      <template v-else-if="action==='receive'">
        <a-alert message="仅接收本次实际验收的数量；报损不会增加可售库存。" type="info" show-icon/>
        <a-form-item label="接收仓库" required><WarehouseSelect v-model:value="warehouseId"/></a-form-item>
        <a-table :data-source="active.items" :scroll="{x: 650}" :columns="[{title:'商品',dataIndex:'productName',width:150},{title:'单位',dataIndex:'unit',width:65},{title:'批准数量',dataIndex:'approvedQuantity'},{title:'已接收',dataIndex:'receivedQuantity'},{title:'本次接收',dataIndex:'receiptQuantity'},{title:'处置',dataIndex:'disposition'}]" row-key="returnItemId" :pagination="false">
          <template #bodyCell="{record,column}">
            <a-input-number v-if="column.dataIndex==='receiptQuantity'" v-model:value="record.receiptQuantity" string-mode :precision="4" :min="'0'" :max="remainingQuantity(record)" aria-label="本次接收数量"/>
            <a-select v-else-if="column.dataIndex==='disposition'" v-model:value="record.disposition" aria-label="实物处置方式" :options="[{value:'RETURN_TO_STOCK',label:'可售回库'},{value:'DAMAGE',label:'报损'}]"/>
          </template>
        </a-table>
      </template>
      <a-form-item v-else label="处理原因" required>
        <a-input v-model:value="decisionReason" maxlength="500"/>
      </a-form-item>
    </template>
  </a-modal>
</template>
<script setup lang="ts">
import {onMounted, reactive, ref, watch} from 'vue';
import {useRoute} from 'vue-router';
import Decimal from 'decimal.js';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {orderReturnApi as api} from '/@/api/business/scm/order-return-api';
import {SCM_ORDER_RETURN_STATUS_ENUM} from '/@/constants/business/scm/order-const';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import type {ReturnRow, Query, ReturnItem, Id} from './order-types';
type ReturnItemWithDisposition = ReturnItem & { disposition: 'RETURN_TO_STOCK' | 'DAMAGE'; receiptQuantity: string };
import {amount, fixed} from './order-form-model';
import {orderError} from './order-errors';

const queryForm = reactive<Query>({pageNum: 1, pageSize: 20}), tableData = ref<ReturnRow[]>([]), total = ref(0),
    loading = ref(false), error = ref(''), visible = ref(false), saving = ref(false), active = ref<ReturnRow>();
let requestId = 0;
const columns = ref<TableColumnsType<ReturnRow>>([{
  title: '退货单号',
  dataIndex: 'returnNo',
  width: 220
}, {title: '退货原因', dataIndex: 'reason', width: 200}, {
  title: '状态',
  dataIndex: 'status',
  width: 120
}, {title: '批准金额', dataIndex: 'approvedAmount', align: 'right', width: 140}, {
  title: '操作',
  dataIndex: 'action',
  align: 'right',
  fixed: 'right',
  width: 240
}]);

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await api.query(queryForm);
    if (id === requestId) {
      tableData.value = r.data.list;
      total.value = r.data.total;
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
  queryForm.operationType = undefined;
  onSearch();
}

const action = ref<'approve' | 'receive' | 'reject' | 'cancel'>('approve'), decisionReason = ref(''), warehouseId = ref<Id>();

async function edit(row: ReturnRow, mode: 'approve' | 'receive' | 'reject' | 'cancel') {
  try {
    active.value = (await api.detail(row.returnId)).data;
    active.value.items.forEach(i => {
      if (mode === 'approve') i.approvedQuantity = i.requestedQuantity;
      (i as ReturnItemWithDisposition).receiptQuantity = remainingQuantity(i);
      (i as ReturnItemWithDisposition).disposition = 'RETURN_TO_STOCK';
    });
    warehouseId.value = undefined;
    action.value = mode;
    decisionReason.value = '';
    error.value = '';
    visible.value = true;
  } catch (e) {
    error.value = orderError(e);
  }
}

function remainingQuantity(item: ReturnItem): string {
  return Decimal.max(0, new Decimal(item.approvedQuantity ?? 0).minus(item.receivedQuantity ?? 0)).toFixed(4);
}

async function save() {
  if (!active.value || saving.value) return;
  if (['reject', 'cancel'].includes(action.value) && !decisionReason.value.trim()) {
    message.error('请填写处理原因');
    return;
  }
  saving.value = true;
  try {
    const r = active.value;
    if (action.value === 'receive') {
      if (!warehouseId.value) throw new Error('请选择接收仓库');
      const items = (r.items as ReturnItemWithDisposition[]).filter(i => new Decimal(i.receiptQuantity ?? 0).gt(0));
      if (!items.length) throw new Error('请填写至少一行本次接收数量');
      if (items.some(i => new Decimal(i.receiptQuantity).gt(remainingQuantity(i)))) {
        throw new Error('本次接收数量不能超过剩余可接收数量');
      }
      await api.receive({returnId: r.returnId, version: r.version, warehouseId: warehouseId.value, items: items.map(i => ({
        returnItemId: i.returnItemId, quantity: fixed(i.receiptQuantity), disposition: i.disposition
      }))});
    } else {
      await api[action.value]({
        returnId: r.returnId,
        version: r.version, ...(action.value === 'approve' ? {
          items: r.items.map(i => ({
            orderItemId: i.orderItemId,
            approvedQuantity: fixed(i.approvedQuantity ?? '0')
          }))
        } : {decisionReason: decisionReason.value})
      });
    }
    visible.value = false;
    await queryData();
  } catch (e) {
    error.value = orderError(e);
  } finally {
    saving.value = false;
  }
}

const detailOpen = ref(false), detailLoading = ref(false), detailError = ref('');
const detail = ref<ReturnRow>(), detailId = ref<Id>();
let detailRequestId = 0;
async function showDetail(id?: Id) {
  if (id === undefined) return;
  const generation = ++detailRequestId;
  detailId.value = id;
  detailOpen.value = true;
  detailLoading.value = true;
  detailError.value = '';
  detail.value = undefined;
  try {
    const response = await api.detail(id);
    if (generation === detailRequestId) detail.value = response.data;
  } catch (e) { if (generation === detailRequestId) detailError.value = orderError(e); }
  finally { if (generation === detailRequestId) detailLoading.value = false; }
}
const route = useRoute();
const returnRouteName = route.name;
watch([() => route.name, () => route.query.returnId], ([name, id]) => {
  if (name === returnRouteName && typeof id === 'string' && /^[1-9]\d{0,18}$/.test(id)) void showDetail(id);
}, {immediate: true});

onMounted(queryData);
</script>
<style scoped>pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}</style>
