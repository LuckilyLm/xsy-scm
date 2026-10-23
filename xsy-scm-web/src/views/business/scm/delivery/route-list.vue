<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent="search">
    <a-row class="smart-query-form-row">
      <a-form-item label="配送日期">
        <a-date-picker v-model:value="query.deliveryDate" value-format="YYYY-MM-DD" />
      </a-form-item>
      <a-form-item label="线路">
        <a-input v-model:value="query.keyword" placeholder="线路名称 / 编号" allow-clear @pressEnter="search" />
      </a-form-item>
      <a-form-item label="状态">
        <a-select v-model:value="query.status" :options="statusOptions" allow-clear class="filter-select" />
      </a-form-item>
      <a-form-item label="仓库">
        <a-select
          v-model:value="query.warehouseId"
          :options="warehouses.map((w) => ({value: w.id, label: w.name}))"
          allow-clear
          class="filter-select"
        />
      </a-form-item>
      <a-form-item label="司机">
        <a-select
          v-model:value="query.driverId"
          :options="drivers.map((d) => ({value: d.id, label: d.driverName}))"
          allow-clear
          class="filter-select"
        />
      </a-form-item>
      <a-form-item label="车辆">
        <a-select
          v-model:value="query.vehicleId"
          :options="vehicles.map((v) => ({value: v.id, label: v.vehicleNo}))"
          allow-clear
          class="filter-select"
        />
      </a-form-item>
      <a-form-item label="省市区">
        <AreaCascader v-model:value="area" type="province_city_district" @change="changeArea" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-space>
          <a-button type="primary" @click="search" v-privilege="'scm:delivery:route:query'">查询 </a-button>
          <a-button @click="reset">重置</a-button>
        </a-space>
      </a-form-item>
    </a-row>
  </a-form>
  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="load">重试</a-button>
    </template>
  </a-alert>
  <a-alert v-if="optionsError" :message="optionsError" type="warning" show-icon>
    <template #action>
      <a-button @click="loadOptions">重载筛选项</a-button>
    </template>
  </a-alert>
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
      :locale="{emptyText}"
      :scroll="{x: tableScrollX}"
    >
      <template #bodyCell="{column, record}">
        <template v-if="column.dataIndex === 'routeName'">{{ record.routeName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="ROUTE_STATUS_TONE[record.status as RouteStatus]" :label="routeStatuses[record.status as RouteStatus].label" />
        </template>
        <template v-else-if="column.dataIndex === 'coverage'">
          <!-- 定位覆盖率是质量信息：图标 + Tooltip 足够，不需要一列文字 -->
          <a-tooltip :title="coverageText(record)">
            <CheckCircleOutlined v-if="coverageOk(record)" class="coverage coverage--ok" />
            <ExclamationCircleOutlined v-else class="coverage coverage--warn" />
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'totalAmount'">{{ money(record.totalAmount) }}</template>
        <template v-else-if="column.dataIndex === 'driverNameSnapshot'">{{ record.driverNameSnapshot || '未分配' }} </template>
        <template v-else-if="column.dataIndex === 'vehicleNoSnapshot'">{{ record.vehicleNoSnapshot || '未分配' }} </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" @click="openDetail(record.id, 'base')">详情</a-button>
            <a-button type="link" size="small" @click="openDetail(record.id, 'map')">路线</a-button>
            <ScmActionMore :actions="rowActions(record)" @select="onRowAction($event, record)" />
          </a-space>
        </template>
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
        @change="applyQuery"
      />
    </div>
  </a-card>
  <RouteFormDrawer ref="formDrawer" @saved="created" />
  <RoutePrint ref="printer" />
</template>
<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {useRoute, useRouter} from 'vue-router';
import {CheckCircleOutlined, ExclamationCircleOutlined} from '@ant-design/icons-vue';
import {deliveryApi} from '/@/api/business/scm/delivery-api';
import AreaCascader from '/@/components/framework/area-cascader/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import type {AreaNode} from '/@/types/business/scm/area';
import {areaColumnsOf, areaNodesOf} from '../common/scm-area';
import {deepLinkFilters} from '/@/lib/query-deep-link';
import type {Warehouse} from '../purchase/purchase-types';
import {deliveryError, routeStatuses, type DeliveryRoute, type Driver, type Vehicle, type Query, type Id, type RouteStatus} from './delivery-types';
import {money} from './delivery-display';
import {DELIVERY_PERM, useDeliveryPermission} from './use-delivery-permission';
import RouteFormDrawer from './components/route-form-drawer.vue';
import RoutePrint from './route-print.vue';

const query = reactive<Query>({pageNum: 1, pageSize: 20});
const {canViewAmount, hasPerm} = useDeliveryPermission();
// 没有全量范围权限时，空表可能是没排给自己，提示里要说清
const emptyText = computed(() =>
  hasPerm(DELIVERY_PERM.SCOPE_ALL_QUERY) ? '暂无数据' : '当前仅显示您负责的线路；若无数据，可能是司机未绑定员工或线路未排到您名下，请联系调度确认。'
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
  printer = ref<InstanceType<typeof RoutePrint>>();
const statusOptions = Object.entries(routeStatuses).map(([value, state]) => ({value, label: state.label}));
// 金额列按权限显示；编辑和打印是低频动作，收进「更多」。
const columns = computed<TableColumnsType>(() => [
  {title: '配送日期', dataIndex: 'deliveryDate', width: 120},
  {title: '线路名称', dataIndex: 'routeName', width: 180},
  {title: '线路编号', dataIndex: 'routeNo', width: 130},
  {title: '仓库', dataIndex: 'warehouseNameSnapshot', width: 150},
  {title: '司机', dataIndex: 'driverNameSnapshot', width: 110},
  {title: '车辆', dataIndex: 'vehicleNoSnapshot', width: 120},
  {title: '停靠点', dataIndex: 'stopCount' as const, width: 80},
  {title: '订单数', dataIndex: 'orderCount' as const, width: 80},
  ...(canViewAmount.value ? [{title: '订单金额', dataIndex: 'totalAmount' as const, width: 140}] : []),
  {title: '停靠点定位', dataIndex: 'coverage' as const, width: 90},
  {title: '状态', dataIndex: 'status', align: 'center' as const, width: 100},
  {title: '操作', dataIndex: 'action', fixed: 'right' as const, align: 'center' as const, width: 160},
]);
const tableScrollX = computed(() => columns.value.reduce((width, column) => width + Number(column.width ?? 0), 0));

/** 状态视觉：草稿 = 待处理（橙），已规划 / 已发车 = 处理中（蓝），已完成 = 绿，已取消 = 灰。 */
const ROUTE_STATUS_TONE: Record<RouteStatus, ScmStatusTone> = {
  DRAFT: 'warning',
  PLANNED: 'processing',
  DISPATCHED: 'processing',
  COMPLETED: 'success',
  CANCELLED: 'neutral',
};

/** 所有停靠点和起点都有坐标，才算定位完整。 */
function coverageOk(record: DeliveryRoute): boolean {
  return record.stopCount > 0 && record.locatedCount === record.stopCount && !!record.startGeomCrs;
}

function coverageText(record: DeliveryRoute): string {
  if (record.stopCount === 0) {
    return '本条线路没有停靠点';
  }
  return coverageOk(record)
    ? `全部 ${record.stopCount} 个停靠点已定位`
    : `已定位 ${record.locatedCount} / ${record.stopCount} 个停靠点，未定位的订单无法参与路线规划`;
}

/** 「更多」菜单挂不上 v-privilege，改用 hasPerm 判断；打印沿用线路查询权限。 */
function rowActions(record: DeliveryRoute): ScmActionItem[] {
  return [
    {
      key: 'edit',
      label: '编辑',
      hidden: !(record.status === 'DRAFT' && hasPerm(DELIVERY_PERM.ROUTE_UPDATE)),
    },
    {
      key: 'print',
      label: '打印',
      hidden: !(['PLANNED', 'DISPATCHED', 'COMPLETED'].includes(record.status) && hasPerm(DELIVERY_PERM.ROUTE_QUERY)),
    },
  ];
}

function onRowAction(key: string, record: DeliveryRoute) {
  if (key === 'edit') {
    formDrawer?.value?.open(record);
  } else if (key === 'print') {
    printer?.value?.open(record.id);
  }
}

/** 筛选转成 URL 参数，只带非空项；键取自 ROUTE_DEEP_LINK，带出和还原用同一份清单。 */
function listFilterParams(): Record<string, string> {
  const params: Record<string, string> = {};
  for (const key of Object.keys(ROUTE_DEEP_LINK)) {
    const value = (query as unknown as Record<string, unknown>)[key];
    if (value === undefined || value === null || value === '') continue;
    if (key === 'pageNum' && value === DEFAULT_QUERY.pageNum) continue;
    if (key === 'pageSize' && value === DEFAULT_QUERY.pageSize) continue;
    params[key] = String(value);
  }
  return params;
}

/** 路由 query 与目标参数是否等价；本页不产生多值键，遇到数组一律按不等处理。 */
function sameQuery(current: Record<string, unknown>, next: Record<string, string>): boolean {
  const keys = new Set([...Object.keys(current), ...Object.keys(next)]);
  for (const key of keys) {
    const left = current[key];
    if (Array.isArray(left)) return false;
    if ((left ?? '') !== (next[key] ?? '')) return false;
  }
  return true;
}

/** 把筛选写进 URL，由下面的 watcher 统一查询；URL 没变化时直接查一次。 */
function applyQuery() {
  const params = listFilterParams();
  if (sameQuery(route.query, params)) {
    load();
    return;
  }
  void router.replace({path: route.path, query: params});
}

/** 详情和路线都进同一个详情页，只是默认 Tab 不同；筛选一并带过去，返回时原样带回。 */
function openDetail(id: Id, targetTab: 'base' | 'map' | 'orders') {
  void router.push({path: `/delivery/routes/${id}`, query: {tab: targetTab, ...listFilterParams()}});
}

let generation = 0;

async function load() {
  const current = ++generation;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头 Alert 提示并可重试
    const result = await deliveryApi.routes(query, {suppressGlobalErrorMessage: true});
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
    const [w, d, v] = await Promise.all([
      deliveryApi.warehouses({suppressGlobalErrorMessage: true}),
      deliveryApi.drivers({suppressGlobalErrorMessage: true}),
      deliveryApi.vehicles({suppressGlobalErrorMessage: true}),
    ]);
    warehouses.value = w.data;
    drivers.value = d.data;
    vehicles.value = v.data;
  } catch (e) {
    optionsError.value = deliveryError(e);
  }
}

function search() {
  query.pageNum = 1;
  applyQuery();
}

function changeArea(_value: unknown, nodes: AreaNode[]) {
  Object.assign(query, areaColumnsOf(nodes));
}

/** 页面默认查询条件：手动重置、deep-link 进入与 URL 省略默认分页共用同一份定义。 */
const DEFAULT_QUERY = {pageNum: 1, pageSize: 20};

function clearQuery() {
  Object.keys(query).forEach((key) => delete (query as unknown as Record<string, unknown>)[key]);
  Object.assign(query, {...DEFAULT_QUERY});
  area.value = [];
}

function reset() {
  clearQuery();
  applyQuery();
}

function created(id: Id) {
  load();
  openDetail(id, 'orders');
}

// 可带到 URL 的筛选键。待办卡片进来要看到同一批数据；详情页返回也复用这份清单。
// 枚举键会校验取值，文本键只做 trim 和空值处理。
const ROUTE_DEEP_LINK = {
  status: Object.keys(routeStatuses),
  keyword: null,
  deliveryDate: null,
  warehouseId: null,
  driverId: null,
  vehicleId: null,
  provinceCode: null,
  provinceName: null,
  cityCode: null,
  cityName: null,
  districtCode: null,
  districtName: null,
  pageNum: null,
  pageSize: null,
};

const route = useRoute();
const router = useRouter();
const routesRouteName = route.name;

/** 省市区编码在 URL 里是字符串，回填查询条件必须是数字：级联控件按数字比对。 */
const areaCode = (value?: string) => (value && /^\d+$/.test(value) ? Number(value) : null);

watch(
  [() => route.name, () => route.query],
  ([name, incomingQuery]) => {
    // 组件被 keep-alive 缓存时，跳往其他页面不能触发本页查询。
    if (name !== routesRouteName) return;
    const filters = deepLinkFilters(incomingQuery, ROUTE_DEEP_LINK);
    clearQuery();
    query.status = filters.status;
    query.keyword = filters.keyword;
    query.deliveryDate = filters.deliveryDate;
    query.warehouseId = filters.warehouseId;
    query.driverId = filters.driverId;
    query.vehicleId = filters.vehicleId;
    query.provinceCode = areaCode(filters.provinceCode);
    query.provinceName = filters.provinceName || null;
    query.cityCode = areaCode(filters.cityCode);
    query.cityName = filters.cityName || null;
    query.districtCode = areaCode(filters.districtCode);
    query.districtName = filters.districtName || null;
    const pageNum = Number(filters.pageNum);
    const pageSize = Number(filters.pageSize);
    if (Number.isInteger(pageNum) && pageNum > 0) query.pageNum = pageNum;
    if (Number.isInteger(pageSize) && pageSize > 0) query.pageSize = pageSize;
    // 级联控件要求层级连续，缺层级时按未筛选处理
    area.value = areaNodesOf(query);
    load();
    const id = incomingQuery.routeId;
    if (typeof id === 'string' && /^[1-9]\d{0,18}$/.test(id)) {
      openDetail(id, 'orders');
    }
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

/* 定位覆盖率：只用一个图标，颜色承担全部语义 */
.coverage {
  font-size: 16px;
}

.coverage--ok {
  color: var(--scm-success, #52c41a);
}

.coverage--warn {
  color: var(--scm-warning, #faad14);
}
</style>
