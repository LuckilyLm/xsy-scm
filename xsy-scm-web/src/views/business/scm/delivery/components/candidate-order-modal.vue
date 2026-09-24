<template>
  <a-modal
      v-model:open="visible"
      title="选择待配送订单"
      :width="1200"
      :confirm-loading="saving"
      :ok-button-props="{ disabled: !selected.length || loading }"
      :ok-text="`加入线路（${selected.length}）`"
      @ok="save"
  >
    <a-alert v-if="error" type="error" :message="error" show-icon/>
    <a-form layout="inline" class="candidate-filters" @submit.prevent="search">
      <a-form-item label="配送日期">
        <a-date-picker v-model:value="query.deliveryDate" value-format="YYYY-MM-DD"/>
      </a-form-item>
      <a-form-item label="订单 / 客户 / 地址">
        <a-input v-model:value="query.keyword" allow-clear @pressEnter="search"/>
      </a-form-item>
      <a-form-item label="省市区">
        <AreaCascader v-model:value="area" type="province_city_district" @change="changeArea"/>
      </a-form-item>
      <a-form-item label="期望配送时间"
      >
        <a-range-picker v-model:value="timeRange" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" @change="changeTime"
        />
      </a-form-item>
      <a-form-item v-if="canViewAmount" label="金额"
      >
        <a-input-number v-model:value="query.minAmount" string-mode :min="0" placeholder="最低"/>
        <span>至</span
        >
        <a-input-number v-model:value="query.maxAmount" string-mode :min="0" placeholder="最高"
        />
      </a-form-item>
      <a-form-item label="商品行数"
      >
        <a-input-number v-model:value="query.minItemCount" :min="0" :precision="0" placeholder="最少"/>
        <span>至</span
        >
        <a-input-number v-model:value="query.maxItemCount" :min="0" :precision="0" placeholder="最多"
        />
      </a-form-item>
      <a-form-item>
        <a-checkbox v-model:checked="query.locatedOnly">仅已定位</a-checkbox>
      </a-form-item>
      <a-form-item
      >
        <a-space>
          <a-button type="primary" @click="search">查询</a-button>
          <a-button @click="reset()">重置</a-button>
        </a-space>
      </a-form-item
      >
    </a-form>
    <a-table
        size="small"
        :columns="columns"
        :data-source="rows"
        row-key="orderId"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1050, y: 360 }"
        :row-selection="{ selectedRowKeys: selected, onChange: select, preserveSelectedRowKeys: true }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'location'"
        >
          <a-tag :color="isLocated(record) ? 'green' : 'default'">{{ isLocated(record) ? '已定位' : '未定位' }}</a-tag>
        </template
        >
        <template v-else-if="column.dataIndex === 'orderAmount'">{{ money(record.orderAmount) }}</template>
        <template v-else-if="column.dataIndex === 'expectDeliveryTime'">{{
            datetime(record.expectDeliveryTime)
          }}
        </template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination v-model:current="query.pageNum" v-model:page-size="query.pageSize" :total="total" show-size-changer
                    @change="load"/>
    </div>
    <a-form layout="vertical"
    >
      <a-form-item label="组单原因" required>
        <a-input v-model:value="reason" :maxlength="500" placeholder="例如：本次城区配送安排"/>
      </a-form-item
      >
    </a-form>
    <p>同一客户、相同地址的订单会合并为一个停靠点；仅展示已确认且尚未分配的订单。</p>
  </a-modal>
</template>
<script setup lang="ts">
import {computed, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {deliveryApi} from '/@/api/business/scm/delivery-api';
import AreaCascader from '/@/components/framework/area-cascader/index.vue';
import type {AreaNode} from '/@/types/business/scm/area';
import {areaColumnsOf} from '../../common/scm-area';
import {isLocated} from '/@/components/business/scm/map/types';
import {datetime} from '../../common/scm-display';
import {money} from '../delivery-display';
import {useDeliveryPermission} from '../use-delivery-permission';
import {deliveryError, type CandidateOrder, type DeliveryRoute, type Id, type Query} from '../delivery-types';

const emit = defineEmits<{ saved: [] }>();
const {canViewAmount} = useDeliveryPermission();
const visible = ref(false),
    loading = ref(false),
    saving = ref(false),
    error = ref('');
const query = reactive<Query>({pageNum: 1, pageSize: 20});
const rows = ref<CandidateOrder[]>([]),
    selected = ref<Id[]>([]),
    total = ref(0),
    area = ref<AreaNode[]>([]),
    timeRange = ref<[string, string]>();
const route = ref<DeliveryRoute>(),
    reason = ref('');
let generation = 0;
// 候选池是调度能力，金额列仍按调用者的金额权限出现（服务端已把无权时的值抹成 null）。
const columns = computed<TableColumnsType>(() => [
  {title: '订单号', dataIndex: 'orderNo', width: 170},
  {title: '客户', dataIndex: 'customerName', width: 160},
  {title: '配送地址', dataIndex: 'address', width: 230},
  {title: '期望配送', dataIndex: 'expectDeliveryTime', width: 170},
  ...(canViewAmount.value ? [{title: '金额', dataIndex: 'orderAmount', align: 'right' as const, width: 120}] : []),
  {title: '商品行数', dataIndex: 'itemCount', align: 'right' as const, width: 90},
  {title: '定位', dataIndex: 'location', width: 90},
]);

function select(keys: (string | number)[]) {
  selected.value = keys;
}

function changeArea(_value: unknown, nodes: AreaNode[]) {
  Object.assign(query, areaColumnsOf(nodes));
}

function changeTime() {
  query.deliveryTimeFrom = timeRange.value?.[0];
  query.deliveryTimeTo = timeRange.value?.[1];
}

function search() {
  query.pageNum = 1;
  selected.value = [];
  load();
}

function reset(reload = true) {
  Object.keys(query).forEach((key) => delete (query as unknown as Record<string, unknown>)[key]);
  Object.assign(query, {pageNum: 1, pageSize: 20});
  area.value = [];
  timeRange.value = undefined;
  selected.value = [];
  if (reload) search();
}

function open(value: DeliveryRoute) {
  route.value = value;
  visible.value = true;
  selected.value = [];
  reason.value = '';
  reset(false);
  query.deliveryDate = value.deliveryDate;
  load();
}

async function load() {
  const current = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const result = await deliveryApi.candidates(query);
    if (current === generation) {
      rows.value = result.data.list;
      total.value = result.data.total;
    }
  } catch (e) {
    if (current === generation) error.value = deliveryError(e);
  } finally {
    if (current === generation) loading.value = false;
  }
}

async function save() {
  if (!route.value || !selected.value.length || !reason.value.trim()) {
    error.value = '请选择订单并填写组单原因';
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    await deliveryApi.addOrders(route.value.id, route.value.version, selected.value, reason.value);
    visible.value = false;
    emit('saved');
  } catch (e) {
    error.value = deliveryError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
<style scoped>
.candidate-filters {
  gap: 12px 0;
  margin: 16px 0;
}

.candidate-filters :deep(.ant-input-number) {
  width: 110px;
}
</style>
