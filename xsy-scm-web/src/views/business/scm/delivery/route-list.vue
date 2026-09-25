<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent="search">
    <a-form-item label="配送日期">
      <a-date-picker v-model:value="query.deliveryDate" value-format="YYYY-MM-DD"/>
    </a-form-item>
    <a-form-item label="线路">
      <a-input v-model:value="query.keyword" placeholder="线路名称 / 编号" allow-clear @pressEnter="search"/>
    </a-form-item>
    <a-form-item label="状态">
      <a-select v-model:value="query.status" :options="statusOptions" allow-clear class="filter-select"/>
    </a-form-item>
    <a-form-item label="仓库"
    >
      <a-select
          v-model:value="query.warehouseId"
          :options="warehouses.map((w) => ({ value: w.id, label: w.name }))"
          allow-clear
          class="filter-select"
      />
    </a-form-item>
    <a-form-item label="司机"
    >
      <a-select
          v-model:value="query.driverId"
          :options="drivers.map((d) => ({ value: d.id, label: d.driverName }))"
          allow-clear
          class="filter-select"
      />
    </a-form-item>
    <a-form-item label="车辆"
    >
      <a-select
          v-model:value="query.vehicleId"
          :options="vehicles.map((v) => ({ value: v.id, label: v.vehicleNo }))"
          allow-clear
          class="filter-select"
      />
    </a-form-item>
    <a-form-item label="省市区">
      <AreaCascader v-model:value="area" type="province_city_district" @change="changeArea"/>
    </a-form-item>
    <a-form-item
    >
      <a-space
      >
        <a-button type="primary" @click="search" v-privilege="'scm:delivery:route:query'">查询
        </a-button
        >
        <a-button @click="reset">重置</a-button>
      </a-space
      >
    </a-form-item
    >
  </a-form>
  <a-alert v-if="error" :message="error" type="error" show-icon
  >
    <template #action>
      <a-button @click="load">重试</a-button>
    </template>
  </a-alert
  >
  <a-alert v-if="optionsError" :message="optionsError" type="warning" show-icon
  >
    <template #action>
      <a-button @click="loadOptions">重载筛选项</a-button>
    </template>
  </a-alert
  >
  <a-card size="small" :bordered="false">
    <div class="smart-table-btn-block">
      <a-button type="primary" v-privilege="'scm:delivery:route:add'" @click="formDrawer?.open()">新建线路</a-button>
    </div>
    <a-table
        id="scm-delivery-route-table"
        :columns="columns"
        :data-source="rows"
        row-key="id"
        size="small"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText }"
        :scroll="{ x: 1660 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'"
        >
          <a-tag :color="routeStatuses[record.status as RouteStatus].color">
            {{ routeStatuses[record.status as RouteStatus].label }}
          </a-tag>
        </template
        >
        <template v-else-if="column.dataIndex === 'coverage'"
        >
          <a-tag
              :color="record.locatedCount === record.stopCount && record.startGeomCrs && record.stopCount > 0 ? 'green' : 'orange'"
          >{{ record.locatedCount }} / {{ record.stopCount }}
          </a-tag
          >
        </template
        >
        <template v-else-if="column.dataIndex === 'totalAmount'">{{ money(record.totalAmount) }}</template>
        <template v-else-if="column.dataIndex === 'driverNameSnapshot'">{{
            record.driverNameSnapshot || '未分配'
          }}
        </template>
        <template v-else-if="column.dataIndex === 'vehicleNoSnapshot'">{{
            record.vehicleNoSnapshot || '未分配'
          }}
        </template>
        <template v-else-if="column.dataIndex === 'action'"
        >
          <a-space :size="0">
            <a-button type="link" @click="details?.open(record.id)">详情</a-button>
            <a-button type="link" @click="details?.open(record.id, 'map')">路线</a-button>
            <a-button v-if="record.status === 'DRAFT'" type="link" v-privilege="'scm:delivery:route:update'"
                      @click="formDrawer?.open(record)"
            >编辑
            </a-button
            >
            <a-button
                v-if="['PLANNED', 'DISPATCHED', 'COMPLETED'].includes(record.status)"
                type="link"
                v-privilege="'scm:delivery:route:print'"
                @click="printer?.open(record.id)"
            >打印
            </a-button
            >
          </a-space>
        </template
        >
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
          v-model:current="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          show-size-changer
          show-quick-jumper
          :show-total="(n: number) => `共 ${n} 条线路`"
          @change="load"
      />
    </div>
  </a-card>
  <RouteFormDrawer ref="formDrawer" @saved="created"/>
  <RouteDetail ref="details" @changed="load"/>
  <RoutePrint ref="printer"/>
</template>
<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {useRoute} from 'vue-router';
import {deliveryApi} from '/@/api/business/scm/delivery-api';
import AreaCascader from '/@/components/framework/area-cascader/index.vue';
import type {AreaNode} from '/@/types/business/scm/area';
import {areaColumnsOf} from '../common/scm-area';
import {deepLinkFilters} from '/@/lib/query-deep-link';
import type {Warehouse} from '../purchase/purchase-types';
import {
  deliveryError,
  routeStatuses,
  type DeliveryRoute,
  type Driver,
  type Vehicle,
  type Query,
  type Id,
  type RouteStatus,
} from './delivery-types';
import {money} from './delivery-display';
import {DELIVERY_PERM, useDeliveryPermission} from './use-delivery-permission';
import RouteFormDrawer from './components/route-form-drawer.vue';
import RouteDetail from './route-detail.vue';
import RoutePrint from './route-print.vue';

const query = reactive<Query>({pageNum: 1, pageSize: 20});
const {canViewAmount, hasPerm} = useDeliveryPermission();
// 无全量配送范围权限（调度岗）时，普通司机看到空表可能是「线路没排到自己」而非「没有线路」，
// 文案要提示是授权/绑定问题，而不是一句「暂无数据」把配置缺口盖掉。
const emptyText = computed(() =>
  hasPerm(DELIVERY_PERM.SCOPE_ALL_QUERY)
    ? '暂无数据'
    : '当前仅显示您负责的线路；若无数据，可能是司机未绑定员工或线路未排到您名下，请联系调度确认。'
);
const rows = ref<DeliveryRoute[]>([]),
    total = ref(0),
    loading = ref(false),
    error = ref(''),
    optionsError = ref('');
const warehouses = ref<Warehouse[]>([]),
    drivers = ref<Driver[]>([]),
    vehicles = ref<Vehicle[]>([]),
    area = ref<AreaNode[]>([]);
const formDrawer = ref<InstanceType<typeof RouteFormDrawer>>(),
    details = ref<InstanceType<typeof RouteDetail>>(),
    printer = ref<InstanceType<typeof RoutePrint>>();
const statusOptions = Object.entries(routeStatuses).map(([value, state]) => ({value, label: state.label}));
// 金额列按权限出现：服务端已把无权限的 totalAmount 抹成 null，这里决定要不要留这一格。
const columns = computed<TableColumnsType>(() => [
  {title: '配送日期', dataIndex: 'deliveryDate', width: 120},
  {title: '线路编号', dataIndex: 'routeNo', width: 180},
  {title: '线路名称', dataIndex: 'routeName', width: 180},
  {title: '仓库', dataIndex: 'warehouseNameSnapshot', width: 150},
  {title: '司机', dataIndex: 'driverNameSnapshot', width: 110},
  {title: '车辆', dataIndex: 'vehicleNoSnapshot', width: 120},
  {title: '停靠点', dataIndex: 'stopCount', align: 'right' as const, width: 80},
  {title: '订单数', dataIndex: 'orderCount', align: 'right' as const, width: 80},
  ...(canViewAmount.value
      ? [{title: '订单金额', dataIndex: 'totalAmount', align: 'right' as const, width: 140}]
      : []),
  {title: '停靠点定位', dataIndex: 'coverage', align: 'center' as const, width: 110},
  {title: '状态', dataIndex: 'status', align: 'center' as const, width: 100},
  {title: '操作', dataIndex: 'action', fixed: 'right' as const, align: 'right' as const, width: 220},
]);
let generation = 0;

async function load() {
  const current = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const result = await deliveryApi.routes(query);
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

async function loadOptions() {
  optionsError.value = '';
  try {
    const [w, d, v] = await Promise.all([deliveryApi.warehouses(), deliveryApi.drivers(), deliveryApi.vehicles()]);
    warehouses.value = w.data;
    drivers.value = d.data;
    vehicles.value = v.data;
  } catch (e) {
    optionsError.value = deliveryError(e);
  }
}

function search() {
  query.pageNum = 1;
  load();
}

function changeArea(_value: unknown, nodes: AreaNode[]) {
  Object.assign(query, areaColumnsOf(nodes));
}

/** 页面默认查询条件：手动重置与 deep-link 进入共用，避免两处各自维护一份「清空」。 */
function clearQuery() {
  Object.keys(query).forEach((key) => delete (query as unknown as Record<string, unknown>)[key]);
  Object.assign(query, {pageNum: 1, pageSize: 20});
  area.value = [];
}

function reset() {
  clearQuery();
  load();
}

function created(id: Id) {
  load();
  details.value?.open(id, 'orders');
}

// 待办卡片带 `?status=DRAFT`（待排线线路）：点进来必须看到同一批数据。
// 进入时先回落到页面默认再落 URL 条件，因此从普通菜单进入不会残留上次 deep-link 的筛选；
// status 取值过线路状态字典白名单，URL 里写别的值按未筛选处理。
const ROUTE_DEEP_LINK = {status: Object.keys(routeStatuses)};

const route = useRoute();
const routesRouteName = route.name;

watch(
    [() => route.name, () => route.query],
    ([name, incomingQuery]) => {
      // 组件被 keep-alive 缓存时，跳往其他页面不能触发本页查询。
      if (name !== routesRouteName) return;
      const filters = deepLinkFilters(incomingQuery, ROUTE_DEEP_LINK);
      clearQuery();
      query.status = filters.status;
      load();
    },
    {immediate: true}
);

onMounted(loadOptions);
</script>
<style scoped>
.filter-select {
  min-width: 140px;
}

.smart-query-form {
  gap: 12px 0;
}
</style>
